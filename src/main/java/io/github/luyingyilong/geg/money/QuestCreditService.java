package io.github.luyingyilong.geg.money;

import java.util.List;
import java.util.UUID;
import java.util.function.LongPredicate;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class QuestCreditService {
    private QuestCreditService() {
    }

    public static AwardResult award(MinecraftServer server, QuestCompletion completion, UUID playerId,
                                    long amount, boolean repeatable, LongPredicate isRepeatable) {
        MoneySavedData data = MoneySavedData.get(server);
        if (!server.isSameThread()) throw new IllegalStateException("Quest credits require the server thread");
        if (amount <= 0 || data.questActivationTime() == 0L
                || completion.completedAt() < data.questActivationTime()
                || !completion.members().contains(playerId)
                || !completion.equals(data.questCompletions().get(completion.key()))) {
            return AwardResult.INELIGIBLE;
        }
        String key = completion.key() + ":" + playerId;
        if (data.questCredits().containsKey(key)) return AwardResult.DUPLICATE;
        if (repeatable) {
            long today = data.questCredits().values().stream()
                    .filter(credit -> credit.playerId().equals(playerId)
                            && CoinAuditService.date(credit.completedAt())
                            .equals(CoinAuditService.date(completion.completedAt()))
                            && isRepeatable.test(credit.questId()))
                    .count();
            boolean sameQuest = data.questCredits().values().stream().anyMatch(credit ->
                    credit.playerId().equals(playerId) && credit.questId() == completion.questId()
                            && CoinAuditService.date(credit.completedAt())
                            .equals(CoinAuditService.date(completion.completedAt())));
            if (today >= 4 || sameQuest) return AwardResult.DAILY_LIMIT;
        }
        data.record("quest_reward", MoneyCurrency.COIN, "system:mint",
                MoneyService.questPending(playerId, key), amount, key);
        data.questCredits().put(key, new QuestCredit(key, playerId, completion.questId(),
                completion.completedAt(), amount, true));
        data.markChanged();
        settlePending(server, playerId);
        return AwardResult.GRANTED;
    }

    public static void settlePending(MinecraftServer server, UUID playerId) {
        if (!server.isSameThread()) throw new IllegalStateException("Quest credits require the server thread");
        MoneySavedData data = MoneySavedData.get(server);
        for (QuestCredit credit : List.copyOf(data.questCredits().values())) {
            if (!credit.pending() || !credit.playerId().equals(playerId)) continue;
            MoneyBalance balance = data.balance(playerId);
            if (Long.MAX_VALUE - balance.coinMinorUnits() < credit.amount()) continue;
            data.record("quest_claim", MoneyCurrency.COIN,
                    MoneyService.questPending(playerId, credit.key()), MoneyService.wallet(playerId),
                    credit.amount(), credit.key());
            data.put(playerId, balance.with(MoneyCurrency.COIN,
                    balance.coinMinorUnits() + credit.amount()));
            data.questCredits().put(credit.key(), credit.paid());
            data.markChanged();
            ServerPlayer online = server.getPlayerList().getPlayer(playerId);
            if (online != null) MoneyNetwork.sync(online, data.balance(playerId));
        }
    }

    public enum AwardResult {
        GRANTED,
        DUPLICATE,
        INELIGIBLE,
        DAILY_LIMIT
    }
}
