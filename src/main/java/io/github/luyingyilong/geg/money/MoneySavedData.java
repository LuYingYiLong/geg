package io.github.luyingyilong.geg.money;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;


final class MoneySavedData extends SavedData {
    private static final String DATA_NAME = "geg_money";
    private static final String WALLETS_KEY = "wallets";

    private final Map<UUID, MoneyBalance> wallets = new HashMap<>();


    static MoneySavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                MoneySavedData::load,
                MoneySavedData::new,
                DATA_NAME
        );
    }


    private static MoneySavedData load(CompoundTag root) {
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

        return data;
    }


    MoneyBalance balance(UUID playerId) {
        return wallets.getOrDefault(playerId, MoneyBalance.ZERO);
    }


    void put(UUID playerId, MoneyBalance balance) {
        if (balance.equals(MoneyBalance.ZERO)) {
            wallets.remove(playerId);
        } else {
            wallets.put(playerId, balance);
        }
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
        return root;
    }
}
