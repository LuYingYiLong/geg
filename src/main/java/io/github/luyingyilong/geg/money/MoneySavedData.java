package io.github.luyingyilong.geg.money;

import io.github.luyingyilong.geg.market.MarketState;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;


public final class MoneySavedData extends SavedData {
    private static final String DATA_NAME = "geg_money";
    private static final String WALLETS_KEY = "wallets";

    private final Map<UUID, MoneyBalance> wallets = new HashMap<>();
    private MarketState market = new MarketState();
    private final List<CoinAuditEntry> audit = new ArrayList<>();
    private long nextAuditSequence = 1L;
    private boolean auditInitialized;
    private long questActivationTime;
    private final Map<String, QuestCompletion> questCompletions = new HashMap<>();
    private final Map<String, QuestCredit> questCredits = new HashMap<>();


    public static MoneySavedData get(MinecraftServer server) {
        MoneySavedData data = server.overworld().getDataStorage().computeIfAbsent(
                MoneySavedData::load,
                MoneySavedData::new,
                DATA_NAME
        );
        data.initializeAudit();
        return data;
    }


    static MoneySavedData load(CompoundTag root) {
        MoneySavedData data = new MoneySavedData();
        ListTag entries = root.getList(WALLETS_KEY, Tag.TAG_COMPOUND);

        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = entries.getCompound(index);
            if (!entry.hasUUID("uuid")) {
                continue;
            }

            long coinMinorUnits = entry.getLong("coinMinorUnits");
            long gem = entry.getLong("gem");
            if (coinMinorUnits < 0L || gem < 0L) {
                continue;
            }

            data.wallets.put(entry.getUUID("uuid"), new MoneyBalance(coinMinorUnits, gem));
        }

        if (root.contains("market", Tag.TAG_COMPOUND)) {
            data.market = MarketState.load(root.getCompound("market"));
        }

        data.auditInitialized = root.getBoolean("auditInitialized");
        data.nextAuditSequence = Math.max(1L, root.getLong("nextAuditSequence"));
        for (Tag raw : root.getList("audit", Tag.TAG_COMPOUND)) {
            CoinAuditEntry entry = CoinAuditEntry.load((CompoundTag) raw);
            if (entry.sequence() > 0 && entry.amount() > 0) {
                data.audit.add(entry);
                if (entry.sequence() < Long.MAX_VALUE) {
                    data.nextAuditSequence = Math.max(data.nextAuditSequence, entry.sequence() + 1);
                }
            }
        }
        data.questActivationTime = root.getLong("questActivationTime");
        for (Tag raw : root.getList("questCompletions", Tag.TAG_COMPOUND)) {
            QuestCompletion completion = QuestCompletion.load((CompoundTag) raw);
            data.questCompletions.put(completion.key(), completion);
        }
        for (Tag raw : root.getList("questCredits", Tag.TAG_COMPOUND)) {
            QuestCredit credit = QuestCredit.load((CompoundTag) raw);
            data.questCredits.put(credit.key(), credit);
        }

        return data;
    }


    public MoneyBalance balance(UUID playerId) {
        return wallets.getOrDefault(playerId, MoneyBalance.ZERO);
    }


    public void put(UUID playerId, MoneyBalance balance) {
        if (balance.equals(MoneyBalance.ZERO)) {
            wallets.remove(playerId);
        } else {
            wallets.put(playerId, balance);
        }
        setDirty();
    }


    public MarketState market() {
        return market;
    }


    public void markChanged() {
        setDirty();
    }

    public List<CoinAuditEntry> audit() {
        return Collections.unmodifiableList(audit);
    }

    public void record(String reason, MoneyCurrency currency, String from, String to,
                       long amount, String reference) {
        if (amount <= 0 || nextAuditSequence == Long.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid audit amount or exhausted sequence");
        }
        audit.add(new CoinAuditEntry(nextAuditSequence++, System.currentTimeMillis(), reason,
                currency.id(), from, to, amount, reference));
        setDirty();
    }

    public long questActivationTime() {
        return questActivationTime;
    }

    public void activateQuests(long now) {
        if (questActivationTime == 0L) {
            questActivationTime = now;
            setDirty();
        }
    }

    public Map<String, QuestCompletion> questCompletions() {
        return questCompletions;
    }

    public Map<String, QuestCredit> questCredits() {
        return questCredits;
    }

    private void initializeAudit() {
        if (auditInitialized) return;
        auditInitialized = true;
        wallets.forEach((player, balance) -> {
            if (balance.coinMinorUnits() > 0) {
                record("opening", MoneyCurrency.COIN, "system:opening", "wallet:" + player,
                        balance.coinMinorUnits(), "legacy");
            }
            if (balance.gem() > 0) {
                record("opening", MoneyCurrency.GEM, "system:opening", "wallet:" + player,
                        balance.gem(), "legacy");
            }
        });
        market.forEachCoinHolding((account, amount) -> record("opening", MoneyCurrency.COIN,
                "system:opening", account, amount, "legacy"));
        questCredits.values().forEach(credit -> {
            if (credit.pending() && credit.amount() > 0L) {
                record("opening", MoneyCurrency.COIN, "system:opening",
                        MoneyService.questPending(credit.playerId(), credit.key()), credit.amount(), "legacy");
            }
        });
        setDirty();
    }


    @Override
    public CompoundTag save(CompoundTag root) {
        ListTag entries = new ListTag();
        wallets.forEach((playerId, balance) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("uuid", playerId);
            entry.putLong("coinMinorUnits", balance.coinMinorUnits());
            entry.putLong("gem", balance.gem());
            entries.add(entry);
        });
        root.put(WALLETS_KEY, entries);
        root.put("market", market.save());
        root.putBoolean("auditInitialized", auditInitialized);
        root.putLong("nextAuditSequence", nextAuditSequence);
        ListTag auditTags = new ListTag();
        audit.forEach(entry -> auditTags.add(entry.save()));
        root.put("audit", auditTags);
        root.putLong("questActivationTime", questActivationTime);
        ListTag completionTags = new ListTag();
        questCompletions.values().forEach(entry -> completionTags.add(entry.save()));
        root.put("questCompletions", completionTags);
        ListTag creditTags = new ListTag();
        questCredits.values().forEach(entry -> creditTags.add(entry.save()));
        root.put("questCredits", creditTags);
        return root;
    }
}
