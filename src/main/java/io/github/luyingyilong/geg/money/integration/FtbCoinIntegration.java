package io.github.luyingyilong.geg.money.integration;

import dev.architectury.event.EventResult;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftbquests.events.ObjectCompletedEvent;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import dev.ftb.mods.ftbquests.quest.reward.RewardTypes;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import io.github.luyingyilong.geg.money.MoneyCurrency;
import io.github.luyingyilong.geg.money.MoneySavedData;
import io.github.luyingyilong.geg.money.QuestCompletion;
import io.github.luyingyilong.geg.money.QuestCreditService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;

public final class FtbCoinIntegration {
    public static RewardType REWARD_TYPE;
    private static final Map<Long, Policy> POLICIES = createPolicies();

    private FtbCoinIntegration() {
    }

    public static void register() {
        REWARD_TYPE = RewardTypes.register(ResourceLocation.fromNamespaceAndPath("geg", "coin"),
                CoinQuestReward::new, () -> ItemIcon.getItemIcon(Items.GOLD_NUGGET));
        REWARD_TYPE.setDisplayName(Component.translatable("reward.geg.coin.type"));
        ObjectCompletedEvent.QUEST.register(event -> {
            completed(event);
            return EventResult.pass();
        });
        MinecraftForge.EVENT_BUS.addListener(FtbCoinIntegration::onServerStarted);
    }

    public static void onServerStarted(ServerStartedEvent event) {
        MoneySavedData.get(event.getServer()).activateQuests(System.currentTimeMillis());
    }

    private static void completed(ObjectCompletedEvent.QuestEvent event) {
        if (!event.getData().getFile().isServerSide()) return;
        long questId = event.getQuest().id;
        if (!POLICIES.containsKey(questId)) return;
        Policy policy = POLICIES.get(questId);
        boolean approvedReward = event.getQuest().getRewards().stream().anyMatch(reward ->
                reward instanceof CoinQuestReward coin && coin.configuredAmount() == policy.amount()
                        && !coin.isTeamReward());
        if (!approvedReward) return;
        MinecraftServer server = FTBTeamsAPI.api().getManager().getServer();
        MoneySavedData data = MoneySavedData.get(server);
        long completedAt = event.getTime().getTime();
        if (completedAt < data.questActivationTime()) return;
        UUID teamId = event.getData().getTeamId();
        Set<UUID> members = new HashSet<>();
        FTBTeamsAPI.api().getManager().getTeamByID(teamId).map(Team::getMembers).ifPresent(members::addAll);
        if (members.isEmpty()) {
            event.getOnlineMembers().forEach(player -> members.add(player.getUUID()));
        }
        String key = completionKey(teamId, questId, completedAt);
        data.questCompletions().putIfAbsent(key,
                new QuestCompletion(key, questId, teamId, completedAt, members));
        data.markChanged();
        for (UUID member : members) {
            grant(server, member, teamId, questId, completedAt, policy,
                    server.getPlayerList().getPlayer(member));
        }
    }

    public static void claim(ServerPlayer player, CoinQuestReward reward) {
        long questId = reward.getQuest().id;
        Policy policy = POLICIES.get(questId);
        TeamData teamData = TeamData.get(player);
        if (policy == null || policy.amount() != reward.configuredAmount() || reward.isTeamReward()
                || teamData == null || teamData.getRewardClaimTime(player.getUUID(), reward).isEmpty()) {
            player.sendSystemMessage(Component.translatable("reward.geg.coin.invalid"));
            return;
        }
        long completedAt = teamData.getCompletedTime(questId).map(java.util.Date::getTime).orElse(0L);
        grant(player.server, player.getUUID(), teamData.getTeamId(), questId, completedAt, policy, player);
    }

    public static void onLogin(ServerPlayer player) {
        MoneySavedData data = MoneySavedData.get(player.server);
        data.activateQuests(System.currentTimeMillis());
        ServerQuestFile file = ServerQuestFile.INSTANCE;
        if (file != null) {
            for (QuestCompletion completion : data.questCompletions().values()) {
                if (!completion.members().contains(player.getUUID())) continue;
                Policy policy = POLICIES.get(completion.questId());
                if (policy == null) continue;
                TeamData team = file.getNullableTeamData(completion.teamId());
                var quest = file.getQuest(completion.questId());
                if (team == null || quest == null || team.getCompletedTime(completion.questId())
                        .map(java.util.Date::getTime).orElse(0L) != completion.completedAt()) continue;
                boolean approvedReward = quest.getRewards().stream().anyMatch(reward ->
                        reward instanceof CoinQuestReward coin && coin.configuredAmount() == policy.amount());
                if (approvedReward) {
                    grant(player.server, player.getUUID(), completion.teamId(), completion.questId(),
                            completion.completedAt(), policy, player);
                }
            }
        }
        QuestCreditService.settlePending(player.server, player.getUUID());
    }

    public static boolean packQuestsPresent() {
        return ServerQuestFile.INSTANCE != null
                && ServerQuestFile.INSTANCE.getQuest(Long.parseUnsignedLong("7E6A4B31C9D80F22", 16)) != null;
    }

    public static int validateLoadedPackRewards() {
        ServerQuestFile file = ServerQuestFile.INSTANCE;
        int validated = 0;
        for (Map.Entry<Long, Policy> entry : POLICIES.entrySet()) {
            var quest = file.getQuest(entry.getKey());
            if (quest == null || quest.canBeRepeated() != entry.getValue().repeatable()
                    || quest.getRewards().stream().filter(reward -> reward instanceof CoinQuestReward coin
                    && coin.configuredAmount() == entry.getValue().amount() && !coin.isTeamReward()).count() != 1) {
                throw new IllegalStateException("Invalid GEG coin reward for quest "
                        + Long.toUnsignedString(entry.getKey(), 16));
            }
            validated++;
        }
        return validated;
    }

    private static void grant(MinecraftServer server, UUID playerId, UUID teamId, long questId,
                              long completedAt, Policy policy, ServerPlayer player) {
        MoneySavedData data = MoneySavedData.get(server);
        QuestCompletion completion = data.questCompletions().get(completionKey(teamId, questId, completedAt));
        if (completedAt < data.questActivationTime() || completion == null
                || !completion.members().contains(playerId)) {
            if (player != null) player.sendSystemMessage(Component.translatable("reward.geg.coin.ineligible"));
            return;
        }
        QuestCreditService.AwardResult result = QuestCreditService.award(server, completion, playerId,
                policy.amount(), policy.repeatable(), id -> POLICIES.getOrDefault(id, Policy.NONE).repeatable());
        if (player == null || result == QuestCreditService.AwardResult.DUPLICATE) return;
        if (result == QuestCreditService.AwardResult.GRANTED) {
            player.sendSystemMessage(Component.translatable("reward.geg.coin.granted",
                    MoneyCurrency.COIN.format(policy.amount())));
        } else if (result == QuestCreditService.AwardResult.DAILY_LIMIT) {
            player.sendSystemMessage(Component.translatable("reward.geg.coin.daily_limit"));
        } else {
            player.sendSystemMessage(Component.translatable("reward.geg.coin.ineligible"));
        }
    }

    private static String completionKey(UUID teamId, long questId, long completedAt) {
        return teamId + ":" + Long.toUnsignedString(questId, 16) + ":" + completedAt;
    }

    private static Map<Long, Policy> createPolicies() {
        Map<Long, Policy> result = new HashMap<>();
        add(result, "7E6A4B31C9D80F22", 2, false);
        add(result, "15562B5313DDE9A5", 5, false);
        add(result, "40B6536B8B1750BB", 5, false);
        add(result, "60A8BD339235D4C5", 20, false);
        add(result, "5DD1DFBC650A5E8D", 25, false);
        add(result, "2E234A7111AC8092", 50, false);
        add(result, "1AC858C2BE3027E6", 25, false);
        add(result, "102432CFE3159567", 30, false);
        add(result, "2D763D90223C9A51", 40, false);
        add(result, "7823AA372E7CF0B5", 80, false);
        add(result, "28D68A77A793C60D", 10, false);
        add(result, "26E61D8A06FAB278", 30, false);
        add(result, "4677ACC569C2F349", 100, false);
        add(result, "523EE9F005F26AB1", 5, true);
        add(result, "6B34AF93D6458C6A", 5, true);
        add(result, "074A9D09364D75A4", 5, true);
        add(result, "436B853DD5DA2904", 5, true);
        return Map.copyOf(result);
    }

    private static void add(Map<Long, Policy> result, String id, long coins, boolean repeatable) {
        result.put(Long.parseUnsignedLong(id, 16), new Policy(coins * 100L, repeatable));
    }

    private record Policy(long amount, boolean repeatable) {
        private static final Policy NONE = new Policy(0L, false);
    }
}
