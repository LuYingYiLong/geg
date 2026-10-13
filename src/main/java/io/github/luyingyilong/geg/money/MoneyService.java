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
        requireServerThread(server);
        if (amount < 0L) {
            throw new IllegalArgumentException("Balance cannot be negative");
        }

        MoneySavedData data = MoneySavedData.get(server);
        MoneyBalance updated = data.balance(playerId).with(currency, amount);
        data.put(playerId, updated);
        syncIfOnline(server, playerId, updated);
        return updated;
    }


    public static MoneyBalance add(MinecraftServer server, UUID playerId, MoneyCurrency currency, long amount) {
        requireServerThread(server);
        requirePositive(amount);
        MoneyBalance current = balance(server, playerId);
        long updatedAmount = Math.addExact(current.amount(currency), amount);
        return set(server, playerId, currency, updatedAmount);
    }


    public static boolean take(MinecraftServer server, UUID playerId, MoneyCurrency currency, long amount) {
        requireServerThread(server);
        requirePositive(amount);
        MoneyBalance current = balance(server, playerId);
        if (current.amount(currency) < amount) {
            return false;
        }

        set(server, playerId, currency, current.amount(currency) - amount);
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
