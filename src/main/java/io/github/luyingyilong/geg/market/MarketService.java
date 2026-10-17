package io.github.luyingyilong.geg.market;

import io.github.luyingyilong.geg.money.MoneyBalance;
import io.github.luyingyilong.geg.money.MoneyNetwork;
import io.github.luyingyilong.geg.money.MoneySavedData;
import io.github.luyingyilong.geg.money.MoneyCurrency;
import io.github.luyingyilong.geg.money.MoneyService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;


public final class MarketService {
    public static final int MAX_ACTIVE_LISTINGS = 128;
    public static final int MAX_ITEM_DATA_CHARS = 32768;


    private MarketService() {
    }


    public static MarketState state(MinecraftServer server) {
        requireServerThread(server);
        return MoneySavedData.get(server).market();
    }


    public static List<MarketListing> listings(MinecraftServer server) {
        return List.copyOf(state(server).listings.values());
    }


    public static List<MarketBuyOrder> buyOrders(MinecraftServer server) {
        return List.copyOf(state(server).buyOrders.values());
    }


    public static List<MarketPurchase> history(MinecraftServer server, UUID playerId) {
        return List.copyOf(state(server).history.getOrDefault(playerId, List.of()));
    }


    public static List<MarketFavorite> favorites(MinecraftServer server, UUID playerId) {
        return List.copyOf(state(server).favorites.getOrDefault(playerId, List.of()));
    }


    public static MarketResult list(ServerPlayer seller, int slot, int quantity,
                                    long unitPrice, ItemStack expectedItem) {
        MinecraftServer server = seller.server;
        if (!MarketRules.enabled(server)) return MarketResult.DISABLED;
        MarketState state = state(server);
        if (slot < 0 || slot >= 36 || quantity <= 0 || unitPrice <= 0L || state.nextId == Long.MAX_VALUE) {
            return MarketResult.INVALID;
        }
        ItemStack inventoryStack = seller.getInventory().getItem(slot);
        if (inventoryStack.isEmpty() || inventoryStack.getCount() < quantity || expectedItem.isEmpty()
                || !MarketKey.of(inventoryStack).equals(MarketKey.of(expectedItem))) {
            return MarketResult.STALE;
        }
        ItemStack item = inventoryStack.copyWithCount(1);
        if (item.save(new net.minecraft.nbt.CompoundTag()).toString().length() > MAX_ITEM_DATA_CHARS) {
            return MarketResult.LIMIT;
        }
        long ownListings = state.listings.values().stream().filter(value -> value.seller().equals(seller.getUUID())).count();
        if (ownListings >= MAX_ACTIVE_LISTINGS) {
            return MarketResult.LIMIT;
        }

        MarketListing listing = new MarketListing(state.nextId, seller.getUUID(),
                seller.getGameProfile().getName(), item, quantity, unitPrice, 1L, System.currentTimeMillis());
        seller.getInventory().removeItem(slot, quantity);
        seller.getInventory().setChanged();
        seller.inventoryMenu.broadcastChanges();
        state.nextId++;
        state.listings.put(listing.id(), listing);
        changed(server, listing.item());
        MarketNetwork.sync(seller);
        return MarketResult.LISTED;
    }


    public static MarketResult buy(ServerPlayer buyer, long listingId, long expectedVersion, long expectedPrice, int quantity) {
        MinecraftServer server = buyer.server;
        if (!MarketRules.enabled(server)) return MarketResult.DISABLED;
        MarketState state = state(server);
        MarketListing listing = state.listings.get(listingId);
        if (listing == null || listing.version() != expectedVersion || listing.unitPrice() != expectedPrice
                || quantity <= 0 || quantity > listing.quantity()) {
            return MarketResult.STALE;
        }
        if (listing.seller().equals(buyer.getUUID())) {
            return MarketResult.SELF_BUY;
        }

        long total;
        try {
            total = Math.multiplyExact(listing.unitPrice(), quantity);
        } catch (ArithmeticException exception) {
            return MarketResult.OVERFLOW;
        }
        long fee = fee(total, MoneySavedData.get(server).marketFeeBasisPoints());
        long proceeds = total - fee;

        MoneySavedData data = MoneySavedData.get(server);
        MoneyBalance balance = data.balance(buyer.getUUID());
        if (balance.coinMinorUnits() < total) {
            return MarketResult.INSUFFICIENT;
        }
        if (state.nextId > Long.MAX_VALUE - (proceeds > 0L ? 2L : 1L)) {
            return MarketResult.LIMIT;
        }

        data.requireAuditCapacity(3);
        MarketDelivery delivery = new MarketDelivery(state.nextId++, listing.item(), quantity);
        MarketCoinCredit credit = proceeds > 0L ? new MarketCoinCredit(state.nextId++, proceeds) : null;
        MarketPurchase purchase = new MarketPurchase(listing.item(), quantity, listing.unitPrice(),
                listing.sellerName(), System.currentTimeMillis());
        if (credit != null) data.record("market_sale", MoneyCurrency.COIN,
                MoneyService.wallet(buyer.getUUID()), MoneyService.pending(listing.seller(), credit.id()),
                proceeds, "listing:" + listingId);
        if (fee > 0L) data.record("market_fee", MoneyCurrency.COIN,
                MoneyService.wallet(buyer.getUUID()), "system:sink", fee, "listing:" + listingId);
        data.put(buyer.getUUID(), balance.with(io.github.luyingyilong.geg.money.MoneyCurrency.COIN,
                balance.coinMinorUnits() - total));
        MoneyNetwork.sync(buyer, data.balance(buyer.getUUID()));
        if (credit != null) state.coinCredits.computeIfAbsent(listing.seller(), ignored -> new ArrayList<>()).add(credit);
        state.deliveries.computeIfAbsent(buyer.getUUID(), ignored -> new ArrayList<>()).add(delivery);
        List<MarketPurchase> records = state.history.computeIfAbsent(buyer.getUUID(), ignored -> new ArrayList<>());
        records.add(0, purchase);
        if (records.size() > 100) {
            records.subList(100, records.size()).clear();
        }
        if (quantity == listing.quantity()) {
            state.listings.remove(listingId);
        } else {
            state.listings.put(listingId, listing.withQuantity(listing.quantity() - quantity));
        }
        updateFavorites(state, listingId);
        changed(server, listing.item());
        MarketNetwork.sync(buyer);
        ServerPlayer onlineSeller = server.getPlayerList().getPlayer(listing.seller());
        if (onlineSeller != null) {
            MarketNetwork.sync(onlineSeller);
        }
        return MarketResult.BOUGHT;
    }


    public static MarketResult reprice(ServerPlayer seller, long listingId, long expectedVersion, long unitPrice) {
        MinecraftServer server = seller.server;
        if (!MarketRules.enabled(server)) return MarketResult.DISABLED;
        MarketState state = state(server);
        MarketListing listing = state.listings.get(listingId);
        if (listing == null || listing.version() != expectedVersion) {
            return MarketResult.STALE;
        }
        if (!listing.seller().equals(seller.getUUID()) || unitPrice <= 0L) {
            return MarketResult.INVALID;
        }
        state.listings.put(listingId, listing.withPrice(unitPrice));
        updateFavorites(state, listingId);
        changed(server, listing.item());
        MarketNetwork.sync(seller);
        return MarketResult.REPRICED;
    }


    public static MarketResult cancel(ServerPlayer seller, long listingId, long expectedVersion) {
        MinecraftServer server = seller.server;
        MarketState state = state(server);
        MarketListing listing = state.listings.get(listingId);
        if (listing == null || listing.version() != expectedVersion) {
            return MarketResult.STALE;
        }
        if (!listing.seller().equals(seller.getUUID()) || state.nextId == Long.MAX_VALUE) {
            return MarketResult.INVALID;
        }
        state.deliveries.computeIfAbsent(seller.getUUID(), ignored -> new ArrayList<>())
                .add(new MarketDelivery(state.nextId++, listing.item(), listing.quantity()));
        state.listings.remove(listingId);
        updateFavorites(state, listingId);
        changed(server, listing.item());
        MarketNetwork.sync(seller);
        return MarketResult.CANCELLED;
    }


    public static MarketResult addFavorite(ServerPlayer player, long listingId) {
        MinecraftServer server = player.server;
        MarketState state = state(server);
        MarketListing listing = state.listings.get(listingId);
        MarketBuyOrder request = state.buyOrders.get(listingId);
        if (listing == null && request == null) return MarketResult.STALE;
        List<MarketFavorite> values = state.favorites.computeIfAbsent(player.getUUID(), ignored -> new ArrayList<>());
        if (values.stream().noneMatch(value -> value.orderId() == listingId)) {
            values.add(listing != null
                    ? new MarketFavorite(listing.id(), false, listing.item(), listing.sellerName(),
                    listing.quantity(), listing.unitPrice(), false)
                    : new MarketFavorite(request.id(), true, request.item(), request.buyerName(),
                    request.quantity(), request.unitPrice(), false));
            MoneySavedData.get(server).markChanged();
        }
        MarketNetwork.sync(player);
        return MarketResult.FAVORITED;
    }


    public static MarketResult removeFavorite(ServerPlayer player, int index) {
        MinecraftServer server = player.server;
        List<MarketFavorite> values = state(server).favorites.get(player.getUUID());
        if (values == null || index < 0 || index >= values.size()) {
            return MarketResult.STALE;
        }
        values.remove(index);
        MoneySavedData.get(server).markChanged();
        MarketNetwork.sync(player);
        return MarketResult.UNFAVORITED;
    }


    public static MarketResult createRequest(ServerPlayer buyer, int source, long sourceId,
                                             int quantity, long unitPrice, ItemStack expectedItem) {
        MinecraftServer server = buyer.server;
        if (!MarketRules.enabled(server)) return MarketResult.DISABLED;
        MarketState state = state(server);
        if (quantity <= 0 || unitPrice <= 0L || state.nextId == Long.MAX_VALUE) return MarketResult.INVALID;
        if (state.buyOrders.values().stream().filter(order -> order.buyer().equals(buyer.getUUID())).count()
                >= MAX_ACTIVE_LISTINGS) return MarketResult.LIMIT;
        ItemStack sample;
        if (source >= 0 && source < 36) sample = buyer.getInventory().getItem(source);
        else if (source == -1) {
            MarketListing listing = state.listings.get(sourceId);
            MarketBuyOrder order = state.buyOrders.get(sourceId);
            sample = listing != null ? listing.item() : order != null ? order.item() : ItemStack.EMPTY;
        } else if (source == -2) {
            List<MarketFavorite> favorites = state.favorites.getOrDefault(buyer.getUUID(), List.of());
            sample = sourceId >= 0 && sourceId < favorites.size() ? favorites.get((int) sourceId).item() : ItemStack.EMPTY;
        } else return MarketResult.INVALID;
        if (sample.isEmpty() || expectedItem.isEmpty() || !MarketKey.of(sample).equals(MarketKey.of(expectedItem)))
            return MarketResult.STALE;
        ItemStack item = sample.copyWithCount(1);
        if (item.save(new net.minecraft.nbt.CompoundTag()).toString().length() > MAX_ITEM_DATA_CHARS)
            return MarketResult.LIMIT;
        long escrow;
        try { escrow = Math.multiplyExact(unitPrice, quantity); }
        catch (ArithmeticException exception) { return MarketResult.OVERFLOW; }
        MoneySavedData data = MoneySavedData.get(server);
        MoneyBalance balance = data.balance(buyer.getUUID());
        if (balance.coinMinorUnits() < escrow) return MarketResult.INSUFFICIENT;
        data.requireAuditCapacity(1);
        MarketBuyOrder order = new MarketBuyOrder(state.nextId++, buyer.getUUID(),
                buyer.getGameProfile().getName(), item, quantity, unitPrice, escrow, 1L,
                System.currentTimeMillis());
        data.record("market_escrow", MoneyCurrency.COIN, MoneyService.wallet(buyer.getUUID()),
                MoneyService.escrow(buyer.getUUID(), order.id()), escrow, "order:" + order.id());
        data.put(buyer.getUUID(), balance.with(MoneyCurrency.COIN, balance.coinMinorUnits() - escrow));
        MoneyNetwork.sync(buyer, data.balance(buyer.getUUID()));
        state.buyOrders.put(order.id(), order);
        changed(server, item);
        MarketNetwork.sync(buyer);
        return MarketResult.REQUESTED;
    }


    public static MarketResult fulfillRequest(ServerPlayer seller, long orderId, long expectedVersion,
                                              long expectedPrice, int quantity) {
        MinecraftServer server = seller.server;
        if (!MarketRules.enabled(server)) return MarketResult.DISABLED;
        MarketState state = state(server);
        MarketBuyOrder order = state.buyOrders.get(orderId);
        if (order == null || order.version() != expectedVersion || order.unitPrice() != expectedPrice
                || quantity <= 0 || quantity > order.quantity()) return MarketResult.STALE;
        if (order.buyer().equals(seller.getUUID())) return MarketResult.SELF_BUY;
        int available = 0;
        MarketKey key = MarketKey.of(order.item());
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = seller.getInventory().getItem(slot);
            if (!stack.isEmpty() && MarketKey.of(stack).equals(key)) available += stack.getCount();
        }
        if (available < quantity) return MarketResult.INSUFFICIENT_ITEMS;
        long total = Math.multiplyExact(order.unitPrice(), quantity);
        if (order.escrow() < total) return MarketResult.STALE;
        MoneySavedData data = MoneySavedData.get(server);
        long fee = fee(total, data.marketFeeBasisPoints());
        long proceeds = total - fee;
        if (state.nextId > Long.MAX_VALUE - (proceeds > 0L ? 2L : 1L)) return MarketResult.LIMIT;
        data.requireAuditCapacity(3);
        long deliveryId = state.nextId++;
        long creditId = proceeds > 0L ? state.nextId++ : 0L;
        if (proceeds > 0L) data.record("market_fulfill", MoneyCurrency.COIN,
                MoneyService.escrow(order.buyer(), orderId), MoneyService.pending(seller.getUUID(), creditId),
                proceeds, "order:" + orderId);
        if (fee > 0L) data.record("market_fee", MoneyCurrency.COIN,
                MoneyService.escrow(order.buyer(), orderId), "system:sink", fee, "order:" + orderId);
        int remaining = quantity;
        for (int slot = 0; slot < 36 && remaining > 0; slot++) {
            ItemStack stack = seller.getInventory().getItem(slot);
            if (!stack.isEmpty() && MarketKey.of(stack).equals(key)) {
                int taken = Math.min(remaining, stack.getCount());
                seller.getInventory().removeItem(slot, taken);
                remaining -= taken;
            }
        }
        seller.getInventory().setChanged();
        seller.inventoryMenu.broadcastChanges();
        state.deliveries.computeIfAbsent(order.buyer(), ignored -> new ArrayList<>())
                .add(new MarketDelivery(deliveryId, order.item(), quantity));
        if (proceeds > 0L) state.coinCredits.computeIfAbsent(seller.getUUID(), ignored -> new ArrayList<>())
                .add(new MarketCoinCredit(creditId, proceeds));
        List<MarketPurchase> records = state.history.computeIfAbsent(order.buyer(), ignored -> new ArrayList<>());
        records.add(0, new MarketPurchase(order.item(), quantity, order.unitPrice(),
                seller.getGameProfile().getName(), System.currentTimeMillis()));
        if (records.size() > 100) records.subList(100, records.size()).clear();
        if (quantity == order.quantity()) state.buyOrders.remove(orderId);
        else state.buyOrders.put(orderId, order.withQuantity(order.quantity() - quantity));
        updateFavorites(state, orderId);
        changed(server, order.item());
        MarketNetwork.sync(seller);
        ServerPlayer onlineBuyer = server.getPlayerList().getPlayer(order.buyer());
        if (onlineBuyer != null) MarketNetwork.sync(onlineBuyer);
        return MarketResult.FULFILLED;
    }


    public static MarketResult repriceRequest(ServerPlayer buyer, long orderId, long version, long price) {
        MinecraftServer server = buyer.server;
        if (!MarketRules.enabled(server)) return MarketResult.DISABLED;
        MarketState state = state(server);
        MarketBuyOrder order = state.buyOrders.get(orderId);
        if (order == null || order.version() != version) return MarketResult.STALE;
        if (!order.buyer().equals(buyer.getUUID()) || price <= 0L) return MarketResult.INVALID;
        long newEscrow;
        try { newEscrow = Math.multiplyExact(price, order.quantity()); }
        catch (ArithmeticException exception) { return MarketResult.OVERFLOW; }
        MoneySavedData data = MoneySavedData.get(server);
        MoneyBalance balance = data.balance(buyer.getUUID());
        long delta = newEscrow - order.escrow();
        if (delta > 0 && balance.coinMinorUnits() < delta) return MarketResult.INSUFFICIENT;
        if (delta < 0 && state.nextId == Long.MAX_VALUE) return MarketResult.LIMIT;
        data.requireAuditCapacity(delta < 0 ? 2 : delta > 0 ? 1 : 0);
        if (delta > 0) {
            data.record("market_reprice", MoneyCurrency.COIN, MoneyService.wallet(buyer.getUUID()),
                    MoneyService.escrow(buyer.getUUID(), orderId), delta, "order:" + orderId);
            data.put(buyer.getUUID(), balance.with(MoneyCurrency.COIN, balance.coinMinorUnits() - delta));
            MoneyNetwork.sync(buyer, data.balance(buyer.getUUID()));
        } else if (delta < 0) {
            long creditId = state.nextId++;
            data.record("market_reprice_refund", MoneyCurrency.COIN, MoneyService.escrow(buyer.getUUID(), orderId),
                    MoneyService.pending(buyer.getUUID(), creditId), -delta, "order:" + orderId);
            state.coinCredits.computeIfAbsent(buyer.getUUID(), ignored -> new ArrayList<>())
                    .add(new MarketCoinCredit(creditId, -delta));
        }
        state.buyOrders.put(orderId, order.withPrice(price));
        updateFavorites(state, orderId);
        changed(server, order.item());
        MarketNetwork.sync(buyer);
        return MarketResult.REPRICED;
    }


    public static MarketResult cancelRequest(ServerPlayer buyer, long orderId, long version) {
        MinecraftServer server = buyer.server;
        MarketState state = state(server);
        MarketBuyOrder order = state.buyOrders.get(orderId);
        if (order == null || order.version() != version) return MarketResult.STALE;
        if (!order.buyer().equals(buyer.getUUID())) return MarketResult.INVALID;
        if (state.nextId == Long.MAX_VALUE) return MarketResult.LIMIT;
        MoneySavedData.get(server).requireAuditCapacity(2);
        long creditId = state.nextId++;
        MoneySavedData.get(server).record("market_cancel_refund", MoneyCurrency.COIN,
                MoneyService.escrow(buyer.getUUID(), orderId), MoneyService.pending(buyer.getUUID(), creditId),
                order.escrow(), "order:" + orderId);
        state.coinCredits.computeIfAbsent(buyer.getUUID(), ignored -> new ArrayList<>())
                .add(new MarketCoinCredit(creditId, order.escrow()));
        state.buyOrders.remove(orderId);
        updateFavorites(state, orderId);
        changed(server, order.item());
        MarketNetwork.sync(buyer);
        return MarketResult.CANCELLED;
    }


    private static void updateFavorites(MarketState state, long orderId) {
        MarketListing listing = state.listings.get(orderId);
        MarketBuyOrder order = state.buyOrders.get(orderId);
        for (List<MarketFavorite> entries : state.favorites.values()) {
            for (int index = 0; index < entries.size(); index++) {
                MarketFavorite old = entries.get(index);
                if (old.orderId() != orderId) continue;
                entries.set(index, listing != null
                        ? new MarketFavorite(orderId, false, listing.item(), listing.sellerName(),
                        listing.quantity(), listing.unitPrice(), false)
                        : order != null
                        ? new MarketFavorite(orderId, true, order.item(), order.buyerName(),
                        order.quantity(), order.unitPrice(), false) : old.markEnded());
            }
        }
    }


    private static void changed(MinecraftServer server, ItemStack item) {
        MoneySavedData data = MoneySavedData.get(server);
        for (UUID recipient : data.market().migrateClaims(data)) {
            ServerPlayer online = server.getPlayerList().getPlayer(recipient);
            if (online != null) io.github.luyingyilong.geg.mail.MailNetwork.status(online);
        }
        MarketState state = data.market();
        state.revision++;
        data.markChanged();
        MarketNetwork.broadcastRevision(server, state.revision(), item);
    }

    static long fee(long total, int basisPoints) {
        if (total < 0L || basisPoints < 0 || basisPoints > 10000)
            throw new IllegalArgumentException("Invalid market fee input");
        return (total / 10000L) * basisPoints + (total % 10000L) * basisPoints / 10000L;
    }


    private static void requireServerThread(MinecraftServer server) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Market operations must run on the server thread");
        }
    }
}
