package io.github.luyingyilong.geg.money;

import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;


public final class MoneyService {
    private MoneyService() {
    }


    public static MoneyBalance balance(MinecraftServer server, UUID playerId) {
        requireServerThread(server);
        return MoneySavedData.get(server).balance(playerId);
    }


    public static MoneyBalance set(MinecraftServer server, UUID playerId, MoneyCurrency currency, long amount) {
        return setWithReason(server, playerId, currency, amount, "admin_set", "");
    }

    public static MoneyBalance setWithReason(MinecraftServer server, UUID playerId,
                                             MoneyCurrency currency, long amount, String reason, String reference) {
        requireServerThread(server);
        if (amount < 0L) {
            throw new IllegalArgumentException("Balance cannot be negative");
        }

        MoneySavedData data = MoneySavedData.get(server);
        long oldAmount = data.balance(playerId).amount(currency);
        MoneyBalance updated = data.balance(playerId).with(currency, amount);
        if (amount > oldAmount) {
            data.record(reason, currency, "system:mint", wallet(playerId), amount - oldAmount, reference);
        } else if (amount < oldAmount) {
            data.record(reason, currency, wallet(playerId), "system:sink", oldAmount - amount, reference);
        }
        data.put(playerId, updated);
        syncIfOnline(server, playerId, updated);
        return updated;
    }


    public static MoneyBalance add(MinecraftServer server, UUID playerId, MoneyCurrency currency, long amount) {
        return addWithReason(server, playerId, currency, amount, "admin_add", "");
    }

    public static MoneyBalance addWithReason(MinecraftServer server, UUID playerId,
                                             MoneyCurrency currency, long amount, String reason, String reference) {
        requireServerThread(server);
        requirePositive(amount);
        MoneyBalance current = balance(server, playerId);
        long updatedAmount = Math.addExact(current.amount(currency), amount);
        return setWithReason(server, playerId, currency, updatedAmount, reason, reference);
    }


    public static boolean take(MinecraftServer server, UUID playerId, MoneyCurrency currency, long amount) {
        requireServerThread(server);
        requirePositive(amount);
        MoneyBalance current = balance(server, playerId);
        if (current.amount(currency) < amount) {
            return false;
        }

        setWithReason(server, playerId, currency, current.amount(currency) - amount, "admin_take", "");
        return true;
    }


    public static boolean transfer(
            MinecraftServer server,
            UUID fromPlayerId,
            UUID toPlayerId,
            MoneyCurrency currency,
            long amount
    ) {
        requireServerThread(server);
        requirePositive(amount);
        if (fromPlayerId.equals(toPlayerId)) {
            throw new IllegalArgumentException("Cannot transfer money to the same account");
        }

        MoneySavedData data = MoneySavedData.get(server);
        MoneyBalance source = data.balance(fromPlayerId);
        MoneyBalance destination = data.balance(toPlayerId);
        if (source.amount(currency) < amount) {
            return false;
        }

        long destinationAmount = Math.addExact(destination.amount(currency), amount);
        MoneyBalance updatedSource = source.with(currency, source.amount(currency) - amount);
        MoneyBalance updatedDestination = destination.with(currency, destinationAmount);

        data.record("player_pay", currency, wallet(fromPlayerId), wallet(toPlayerId), amount, "");
        data.put(fromPlayerId, updatedSource);
        data.put(toPlayerId, updatedDestination);
        syncIfOnline(server, fromPlayerId, updatedSource);
        syncIfOnline(server, toPlayerId, updatedDestination);
        return true;
    }


    private static void requirePositive(long amount) {
        if (amount <= 0L) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }

    public static String wallet(UUID playerId) {
        return "wallet:" + playerId;
    }

    public static String escrow(UUID playerId, long orderId) {
        return "escrow:" + playerId + ":" + orderId;
    }

    public static String pending(UUID playerId, long creditId) {
        return "pending:" + playerId + ":" + creditId;
    }

    public static String questPending(UUID playerId, String creditKey) {
        return "quest_pending:" + playerId + ":" + creditKey;
    }


    private static void requireServerThread(MinecraftServer server) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Money operations must run on the server thread");
        }
    }


    private static void syncIfOnline(MinecraftServer server, UUID playerId, MoneyBalance balance) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            MoneyNetwork.sync(player, balance);
        }
    }
}
