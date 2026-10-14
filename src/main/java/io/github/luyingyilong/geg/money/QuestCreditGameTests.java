package io.github.luyingyilong.geg.money;

import io.github.luyingyilong.geg.GegMod;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(GegMod.MODID)
@PrefixGameTestTemplate(false)
public final class QuestCreditGameTests {
    private QuestCreditGameTests() {
    }

    @GameTest(template = "empty")
    public static void offlineCreditOverflowAndIdempotency(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        MoneySavedData data = MoneySavedData.get(server);
        data.activateQuests(System.currentTimeMillis() - 1000L);
        UUID team = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID lateJoiner = UUID.randomUUID();
        long time = Math.max(System.currentTimeMillis(), data.questActivationTime()) + 1000L;
        QuestCompletion completion = completion(data, team, 0x71L, time, member);
        data.put(member, new MoneyBalance(Long.MAX_VALUE - 50L, 0L));
        helper.assertTrue(QuestCreditService.award(server, completion, member, 100L, false, id -> false)
                        == QuestCreditService.AwardResult.GRANTED,
                "Offline eligible member did not receive an entitlement");
        helper.assertTrue(data.balance(member).coinMinorUnits() == Long.MAX_VALUE - 50L
                        && data.questCredits().get(completion.key() + ":" + member).pending(),
                "Overflowing quest reward was lost or forced into the wallet");
        helper.assertTrue(QuestCreditService.award(server, completion, member, 100L, false, id -> false)
                        == QuestCreditService.AwardResult.DUPLICATE,
                "Quest reward was issued twice");
        helper.assertTrue(QuestCreditService.award(server, completion, lateJoiner, 100L, false, id -> false)
                        == QuestCreditService.AwardResult.INELIGIBLE,
                "A player who joined after completion received coin");
        data.put(member, new MoneyBalance(Long.MAX_VALUE - 200L, 0L));
        QuestCreditService.settlePending(server, member);
        helper.assertTrue(data.balance(member).coinMinorUnits() == Long.MAX_VALUE - 100L
                        && !data.questCredits().get(completion.key() + ":" + member).pending(),
                "Pending quest coin was not settled exactly once");
        helper.assertTrue(data.audit().stream().filter(entry -> entry.reason().equals("quest_reward")
                        && entry.reference().equals(completion.key() + ":" + member)).count() == 1,
                "Quest mint audit entry was missing or duplicated");
        helper.assertTrue(data.audit().stream().filter(entry -> entry.reason().equals("quest_claim")
                        && entry.reference().equals(completion.key() + ":" + member)).count() == 1,
                "Quest pending-to-wallet transfer was missing or duplicated");

        QuestCompletion old = completion(data, team, 0x72L, data.questActivationTime() - 1L, member);
        helper.assertTrue(QuestCreditService.award(server, old, member, 100L, false, id -> false)
                        == QuestCreditService.AwardResult.INELIGIBLE,
                "A pre-activation quest was paid retroactively");
        MoneySavedData restored = MoneySavedData.load(data.save(new CompoundTag()));
        helper.assertTrue(restored.questCredits().containsKey(completion.key() + ":" + member)
                        && restored.audit().stream().anyMatch(entry -> entry.reference()
                        .equals(completion.key() + ":" + member)),
                "Quest entitlement or audit did not survive serialization");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void repeatableDailyLimit(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        MoneySavedData data = MoneySavedData.get(server);
        data.activateQuests(System.currentTimeMillis() - 1000L);
        UUID member = UUID.randomUUID();
        UUID team = UUID.randomUUID();
        long time = Math.max(System.currentTimeMillis(), data.questActivationTime()) + 1000L;
        for (int index = 0; index < 4; index++) {
            QuestCompletion completion = completion(data, team, 0x80L + index, time + index, member);
            helper.assertTrue(QuestCreditService.award(server, completion, member, 500L, true, id -> true)
                            == QuestCreditService.AwardResult.GRANTED,
                    "One of the four approved daily repeatable rewards was rejected");
        }
        QuestCompletion fifth = completion(data, team, 0x84L, time + 4L, member);
        helper.assertTrue(QuestCreditService.award(server, fifth, member, 500L, true, id -> true)
                        == QuestCreditService.AwardResult.DAILY_LIMIT,
                "More than 20 coin was issued in one day");
        QuestCompletion nextDay = completion(data, team, 0x84L, time + 86_400_000L, member);
        helper.assertTrue(QuestCreditService.award(server, nextDay, member, 500L, true, id -> true)
                        == QuestCreditService.AwardResult.GRANTED,
                "Daily quest cap did not reset on the next Shanghai calendar day");
        helper.succeed();
    }

    private static QuestCompletion completion(MoneySavedData data, UUID team, long questId,
                                              long time, UUID member) {
        String key = team + ":" + Long.toUnsignedString(questId, 16) + ":" + time;
        QuestCompletion value = new QuestCompletion(key, questId, team, time, Set.of(member));
        data.questCompletions().put(key, value);
        data.markChanged();
        return value;
    }
}
