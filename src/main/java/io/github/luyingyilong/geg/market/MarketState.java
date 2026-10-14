package io.github.luyingyilong.geg.market;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;


public final class MarketState {
    final Map<Long, MarketListing> listings = new LinkedHashMap<>();
    final Map<UUID, List<MarketDelivery>> deliveries = new HashMap<>();
    final Map<Long, MarketBuyOrder> buyOrders = new LinkedHashMap<>();
    final Map<UUID, List<MarketCoinCredit>> coinCredits = new HashMap<>();
    final Map<UUID, List<MarketPurchase>> history = new HashMap<>();
    final Map<UUID, List<MarketFavorite>> favorites = new HashMap<>();

    long nextId = 1L;
    long revision = 1L;


    public long revision() {
        return revision;
    }

    public void forEachCoinHolding(BiConsumer<String, Long> consumer) {
        buyOrders.values().forEach(order -> {
            if (order.escrow() > 0) consumer.accept("escrow:" + order.buyer() + ":" + order.id(), order.escrow());
        });
        coinCredits.forEach((player, credits) -> credits.forEach(credit -> {
            if (credit.amount() > 0) {
                consumer.accept("pending:" + player + ":" + credit.id(), credit.amount());
            }
        }));
    }


    public static MarketState load(CompoundTag root) {
        MarketState state = new MarketState();
        state.nextId = Math.max(1L, root.getLong("nextId"));
        state.revision = Math.max(1L, root.getLong("revision"));

        for (Tag raw : root.getList("listings", Tag.TAG_COMPOUND)) {
            MarketListing listing = MarketListing.load((CompoundTag) raw);
            if (listing != null) {
                state.listings.put(listing.id(), listing);
                if (listing.id() < Long.MAX_VALUE) {
                    state.nextId = Math.max(state.nextId, listing.id() + 1L);
                }
            }
        }

        for (Tag raw : root.getList("buyOrders", Tag.TAG_COMPOUND)) {
            MarketBuyOrder order = MarketBuyOrder.load((CompoundTag) raw);
            if (order != null && !state.listings.containsKey(order.id())) {
                state.buyOrders.put(order.id(), order);
                if (order.id() < Long.MAX_VALUE) {
                    state.nextId = Math.max(state.nextId, order.id() + 1L);
                }
            }
        }

        for (Tag raw : root.getList("deliveries", Tag.TAG_COMPOUND)) {
            CompoundTag account = (CompoundTag) raw;
            if (!account.hasUUID("player")) {
                continue;
            }
            List<MarketDelivery> values = new ArrayList<>();
            for (Tag rawDelivery : account.getList("entries", Tag.TAG_COMPOUND)) {
                MarketDelivery delivery = MarketDelivery.load((CompoundTag) rawDelivery);
                if (delivery != null) {
                    values.add(delivery);
                    if (delivery.id() < Long.MAX_VALUE) {
                        state.nextId = Math.max(state.nextId, delivery.id() + 1L);
                    }
                }
            }
            state.deliveries.put(account.getUUID("player"), values);
        }

        for (Tag raw : root.getList("coinCredits", Tag.TAG_COMPOUND)) {
            CompoundTag account = (CompoundTag) raw;
            if (!account.hasUUID("player")) continue;
            List<MarketCoinCredit> values = new ArrayList<>();
            for (Tag entry : account.getList("entries", Tag.TAG_COMPOUND)) {
                MarketCoinCredit credit = MarketCoinCredit.load((CompoundTag) entry);
                if (credit != null) {
                    values.add(credit);
                    if (credit.id() < Long.MAX_VALUE) state.nextId = Math.max(state.nextId, credit.id() + 1L);
                }
            }
            state.coinCredits.put(account.getUUID("player"), values);
        }
        // Old saves stored one aggregate amount per player. Convert it to a claimable record.
        for (Tag raw : root.getList("pendingCoin", Tag.TAG_COMPOUND)) {
            CompoundTag account = (CompoundTag) raw;
            if (account.hasUUID("player") && account.getLong("amount") > 0L && state.nextId < Long.MAX_VALUE
                    && !state.coinCredits.containsKey(account.getUUID("player"))) {
                state.coinCredits.computeIfAbsent(account.getUUID("player"), ignored -> new ArrayList<>())
                        .add(new MarketCoinCredit(state.nextId++, account.getLong("amount")));
            }
        }

        for (Tag raw : root.getList("history", Tag.TAG_COMPOUND)) {
            CompoundTag account = (CompoundTag) raw;
            if (!account.hasUUID("player")) {
                continue;
            }
            List<MarketPurchase> values = new ArrayList<>();
            for (Tag rawPurchase : account.getList("entries", Tag.TAG_COMPOUND)) {
                MarketPurchase purchase = MarketPurchase.load((CompoundTag) rawPurchase);
                if (purchase != null && values.size() < 100) {
                    values.add(purchase);
                }
            }
            state.history.put(account.getUUID("player"), values);
        }

        for (Tag raw : root.getList("orderFavorites", Tag.TAG_COMPOUND)) {
            CompoundTag account = (CompoundTag) raw;
            if (!account.hasUUID("player")) {
                continue;
            }
            List<MarketFavorite> values = new ArrayList<>();
            for (Tag rawItem : account.getList("entries", Tag.TAG_COMPOUND)) {
                MarketFavorite favorite = MarketFavorite.load((CompoundTag) rawItem);
                if (favorite != null) values.add(favorite);
            }
            state.favorites.put(account.getUUID("player"), values);
        }
        for (Tag raw : root.getList("favorites", Tag.TAG_COMPOUND)) {
            CompoundTag account = (CompoundTag) raw;
            if (!account.hasUUID("player") || state.favorites.containsKey(account.getUUID("player"))) continue;
            List<MarketFavorite> values = new ArrayList<>();
            for (Tag entry : account.getList("entries", Tag.TAG_COMPOUND)) {
                ItemStack item = ItemStack.of((CompoundTag) entry);
                if (!item.isEmpty()) values.add(new MarketFavorite(0L, false, item, "", 0, 0L, true));
            }
            state.favorites.put(account.getUUID("player"), values);
        }
        return state;
    }


    public CompoundTag save() {
        CompoundTag root = new CompoundTag();
        root.putLong("nextId", nextId);
        root.putLong("revision", revision);

        ListTag listingTags = new ListTag();
        listings.values().forEach(listing -> listingTags.add(listing.save()));
        root.put("listings", listingTags);

        ListTag requestTags = new ListTag();
        buyOrders.values().forEach(order -> requestTags.add(order.save()));
        root.put("buyOrders", requestTags);

        ListTag deliveryAccounts = new ListTag();
        deliveries.forEach((playerId, values) -> {
            CompoundTag account = new CompoundTag();
            account.putUUID("player", playerId);
            ListTag entries = new ListTag();
            values.forEach(delivery -> entries.add(delivery.save()));
            account.put("entries", entries);
            deliveryAccounts.add(account);
        });
        root.put("deliveries", deliveryAccounts);

        ListTag coinAccounts = new ListTag();
        coinCredits.forEach((playerId, values) -> {
            CompoundTag account = new CompoundTag();
            account.putUUID("player", playerId);
            ListTag entries = new ListTag();
            values.forEach(credit -> entries.add(credit.save()));
            account.put("entries", entries);
            coinAccounts.add(account);
        });
        root.put("coinCredits", coinAccounts);

        ListTag historyAccounts = new ListTag();
        history.forEach((playerId, values) -> {
            CompoundTag account = new CompoundTag();
            account.putUUID("player", playerId);
            ListTag entries = new ListTag();
            values.forEach(purchase -> entries.add(purchase.save()));
            account.put("entries", entries);
            historyAccounts.add(account);
        });
        root.put("history", historyAccounts);

        ListTag favoriteAccounts = new ListTag();
        favorites.forEach((playerId, values) -> {
            CompoundTag account = new CompoundTag();
            account.putUUID("player", playerId);
            ListTag entries = new ListTag();
            values.forEach(favorite -> entries.add(favorite.save()));
            account.put("entries", entries);
            favoriteAccounts.add(account);
        });
        root.put("orderFavorites", favoriteAccounts);
        return root;
    }
}
