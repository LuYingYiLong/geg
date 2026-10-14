package io.github.luyingyilong.geg.market;

import com.mojang.authlib.GameProfile;
import io.github.luyingyilong.geg.GegMod;
import io.github.luyingyilong.geg.money.MoneyBalance;
import io.github.luyingyilong.geg.money.MoneySavedData;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;


@GameTestHolder(GegMod.MODID)
@PrefixGameTestTemplate(false)
public final class MarketGameTests {
    private MarketGameTests() {
    }


    @GameTest(template = "empty")
    public static void distinctItemDataAndPersistence(GameTestHelper helper) {
        helper.assertTrue(helper.getLevel().getRecipeManager()
                        .byKey(new ResourceLocation(GegMod.MODID, "market_terminal")).isPresent(),
                "Market terminal recipe was not loaded");
        ItemStack first = new ItemStack(Items.PAPER);
        first.getOrCreateTag().putString("variant", "first");
        ItemStack second = first.copy();
        second.getOrCreateTag().putString("variant", "second");
        helper.assertFalse(MarketKey.of(first).equals(MarketKey.of(second)), "Different NBT was merged");
        helper.assertTrue(MarketKey.of(first).equals(MarketKey.of(first.copyWithCount(8))),
                "Stack count changed the market key");

        UUID owner = UUID.randomUUID();
        MarketState original = new MarketState();
        original.listings.put(1L, new MarketListing(1L, owner, "seller", first, 4, 125L, 2L, 1L));
        original.listings.put(3L, new MarketListing(3L, owner, "seller", first, 2, 100L, 1L, 2L));
        original.listings.put(4L, new MarketListing(4L, owner, "seller", second, 1, 90L, 1L, 3L));
        original.deliveries.put(owner, new ArrayList<>(List.of(new MarketDelivery(2L, second, 3))));
        original.coinCredits.put(owner, new ArrayList<>(List.of(new MarketCoinCredit(5L, 625L))));
        original.favorites.put(owner, new ArrayList<>(List.of(
                new MarketFavorite(1L, false, first, "seller", 4, 125L, false))));
        List<MarketPurchase> purchases = new ArrayList<>();
        for (int index = 0; index < 101; index++) {
            purchases.add(new MarketPurchase(first, 1, 125L, "seller", index));
        }
        original.history.put(owner, purchases);

        MarketState restored = MarketState.load(original.save());
        List<MarketSummary> summaries = MarketNetwork.summaries(restored);
        helper.assertTrue(summaries.size() == 2, "Different item NBT merged into one card");
        MarketSummary firstSummary = summaries.stream()
                .filter(value -> MarketKey.of(value.item()).equals(MarketKey.of(first)))
                .findFirst().orElseThrow();
        helper.assertTrue(firstSummary.quantity() == 6 && firstSummary.minPrice() == 100L,
                "Summary quantity or lowest unit price is incorrect");
        helper.assertTrue(restored.listings.get(1L).item().getTag().equals(first.getTag()),
                "Listing item data did not survive save/load");
        helper.assertTrue(restored.deliveries.get(owner).get(0).item().getTag().equals(second.getTag()),
                "Claim item data did not survive save/load");
        helper.assertTrue(restored.coinCredits.get(owner).get(0).amount() == 625L,
                "Pending coin did not survive save/load");
        helper.assertTrue(restored.favorites.get(owner).size() == 1, "Favorites did not survive save/load");
        helper.assertTrue(restored.history.get(owner).size() == 100, "Purchase history exceeded 100 entries");
        MarketState manyItems = new MarketState();
        for (int index = 0; index < 300; index++) {
            ItemStack variant = new ItemStack(Items.PAPER);
            variant.getOrCreateTag().putInt("variant", index);
            long id = index + 1L;
            manyItems.listings.put(id, new MarketListing(id, owner, "seller", variant, 1, 100L, 1L, id));
        }
        helper.assertTrue(MarketNetwork.summaries(manyItems).size() == 300,
                "Hundreds of distinct cards were merged or lost");
        helper.succeed();
    }


    @GameTest(template = "empty")
    public static void transactionAndPartialClaim(GameTestHelper helper) {
        FakePlayer seller = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "market_seller"));
        FakePlayer buyer = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "market_buyer"));
        MoneySavedData data = MoneySavedData.get(helper.getLevel().getServer());
        data.put(buyer.getUUID(), new MoneyBalance(1000L, 0L));
        ItemStack item = new ItemStack(Items.EMERALD, 4);
        item.getOrCreateTag().putString("variant", "traded");
        seller.getInventory().setItem(0, item.copy());

        long listingId = data.market().nextId;
        helper.assertTrue(MarketService.list(seller, 0, 4, 125L, item) == MarketResult.LISTED,
                "Listing failed");
        helper.assertTrue(seller.getInventory().getItem(0).isEmpty(), "Listing did not escrow items");
        helper.assertTrue(MarketService.addFavorite(buyer, listingId) == MarketResult.FAVORITED,
                "Favorite failed");
        helper.assertTrue(MarketService.buy(buyer, listingId, 1L, 125L, 1) == MarketResult.BOUGHT,
                "Partial purchase failed");
        helper.assertTrue(data.balance(buyer.getUUID()).coinMinorUnits() == 875L,
                "Buyer was not charged the unit price");
        helper.assertTrue(MarketService.pendingCoin(helper.getLevel().getServer(), seller.getUUID()) == 125L,
                "Offline seller proceeds were not queued");
        helper.assertTrue(MarketService.buy(buyer, listingId, 1L, 125L, 1) == MarketResult.STALE,
                "Stale purchase succeeded");
        helper.assertTrue(MarketService.reprice(seller, listingId, 1L, 200L) == MarketResult.STALE,
                "Stale reprice succeeded");
        helper.assertTrue(MarketService.reprice(seller, listingId, 2L, 200L) == MarketResult.REPRICED,
                "Reprice failed");
        helper.assertTrue(MarketService.buy(buyer, listingId, 2L, 125L, 1) == MarketResult.STALE,
                "Old-price purchase succeeded");
        helper.assertTrue(MarketService.buy(buyer, listingId, 3L, 200L, 3) == MarketResult.BOUGHT,
                "Last items purchase failed");
        helper.assertTrue(MarketService.buy(buyer, listingId, 3L, 200L, 1) == MarketResult.STALE,
                "Second purchase of sold-out listing succeeded");
        helper.assertTrue(data.balance(buyer.getUUID()).coinMinorUnits() == 275L,
                "Total debit was incorrect");
        helper.assertTrue(MarketService.favorites(helper.getLevel().getServer(), buyer.getUUID()).size() == 1,
                "Favorite disappeared after sellout");
        helper.assertTrue(MarketService.claimCoin(seller) == MarketResult.CLAIMED,
                "Offline seller proceeds could not be claimed");
        helper.assertTrue(MarketService.claimCoin(seller) == MarketResult.CLAIMED,
                "Second sale proceeds could not be claimed");
        helper.assertTrue(data.balance(seller.getUUID()).coinMinorUnits() == 725L,
                "Seller proceeds were incorrect");
        data.put(seller.getUUID(), new MoneyBalance(Long.MAX_VALUE, 0L));
        data.market().coinCredits.put(seller.getUUID(), new ArrayList<>(List.of(
                new MarketCoinCredit(data.market().nextId++, 1L))));
        helper.assertTrue(MarketService.claimCoin(seller) == MarketResult.OVERFLOW,
                "Overflowing proceeds claim succeeded");
        helper.assertTrue(MarketService.pendingCoin(helper.getLevel().getServer(), seller.getUUID()) == 1L,
                "Overflowing claim lost pending proceeds");
        data.put(seller.getUUID(), new MoneyBalance(725L, 0L));
        data.market().coinCredits.remove(seller.getUUID());

        List<MarketDelivery> deliveries = MarketService.deliveries(helper.getLevel().getServer(), buyer.getUUID());
        for (int slot = 0; slot < 36; slot++) {
            buyer.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        }
        buyer.getInventory().setItem(0, item.copyWithCount(63));
        helper.assertTrue(MarketService.claimItems(buyer, deliveries.get(1).id()) == MarketResult.CLAIMED,
                "Partial inventory claim failed");
        helper.assertTrue(buyer.getInventory().getItem(0).getCount() == 64,
                "Partial claim did not fill the available slot");
        helper.assertTrue(MarketService.deliveries(helper.getLevel().getServer(), buyer.getUUID()).get(1).quantity() == 2,
                "Unclaimed remainder was lost");
        helper.assertTrue(MarketService.claimItems(buyer, deliveries.get(1).id()) == MarketResult.NO_ROOM,
                "Full inventory accepted a claim");
        buyer.getInventory().setItem(1, ItemStack.EMPTY);
        helper.assertTrue(MarketService.claimItems(buyer, deliveries.get(1).id()) == MarketResult.CLAIMED,
                "Remaining items could not be claimed");
        helper.assertTrue(buyer.getInventory().getItem(1).getCount() == 2,
                "Remaining claim quantity was incorrect");

        seller.getInventory().setItem(0, item.copyWithCount(2));
        long secondId = data.market().nextId;
        helper.assertTrue(MarketService.list(seller, 0, 2, Long.MAX_VALUE, item) == MarketResult.LISTED,
                "Large-price listing failed");
        helper.assertTrue(MarketService.buy(buyer, secondId, 1L, Long.MAX_VALUE, 2) == MarketResult.OVERFLOW,
                "Overflowing total was accepted");
        helper.assertTrue(MarketService.buy(seller, secondId, 1L, Long.MAX_VALUE, 1) == MarketResult.SELF_BUY,
                "Seller bought own listing");
        helper.assertTrue(MarketService.cancel(seller, secondId, 1L) == MarketResult.CANCELLED,
                "Cancellation failed");
        helper.assertTrue(MarketService.deliveries(helper.getLevel().getServer(), seller.getUUID()).size() == 1,
                "Cancelled items were not moved to Claim");
        seller.getInventory().setItem(0, item.copyWithCount(1));
        long thirdId = data.market().nextId;
        helper.assertTrue(MarketService.list(seller, 0, 1, 300L, item) == MarketResult.LISTED,
                "Insufficient-funds listing failed");
        helper.assertTrue(MarketService.buy(buyer, thirdId, 1L, 300L, 1) == MarketResult.INSUFFICIENT,
                "Purchase without enough coin succeeded");
        helper.assertTrue(MarketService.cancel(seller, thirdId, 1L) == MarketResult.CANCELLED,
                "Cleanup cancellation failed");
        helper.succeed();
    }


    @GameTest(template = "empty")
    public static void buyOrderEscrowDeliveryAndRefund(GameTestHelper helper) {
        FakePlayer buyer = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "request_buyer"));
        FakePlayer seller = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "request_seller"));
        FakePlayer rival = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "request_rival"));
        MoneySavedData data = MoneySavedData.get(helper.getLevel().getServer());
        data.put(buyer.getUUID(), new MoneyBalance(1000L, 0L));
        ItemStack sample = new ItemStack(Items.PAPER);
        sample.getOrCreateTag().putString("kind", "wanted");
        ItemStack wrong = sample.copy();
        wrong.getOrCreateTag().putString("kind", "other");
        buyer.getInventory().setItem(0, sample.copy());
        long id = data.market().nextId;
        helper.assertTrue(MarketService.createRequest(buyer, 0, 0L, 5, 100L, sample)
                == MarketResult.REQUESTED, "Buy order creation failed");
        helper.assertTrue(data.balance(buyer.getUUID()).coinMinorUnits() == 500L,
                "Buy order did not escrow all coin");
        helper.assertTrue(buyer.getInventory().getItem(0).getCount() == 1,
                "Buy order sample was removed from inventory");
        helper.assertTrue(MarketService.createRequest(buyer, 0, 0L, 1, Long.MAX_VALUE, sample)
                == MarketResult.INSUFFICIENT, "Unaffordable order was accepted");
        helper.assertTrue(MarketService.createRequest(buyer, 0, 0L, 2, Long.MAX_VALUE, sample)
                == MarketResult.OVERFLOW, "Overflowing escrow was accepted");
        helper.assertTrue(MarketService.addFavorite(rival, id) == MarketResult.FAVORITED,
                "Buy order could not be favorited");
        seller.getInventory().setItem(0, sample.copyWithCount(2));
        seller.getInventory().setItem(1, wrong.copyWithCount(9));
        seller.getInventory().setItem(2, sample.copyWithCount(2));
        helper.assertTrue(MarketService.fulfillRequest(seller, id, 1L, 100L, 4)
                == MarketResult.FULFILLED, "Delivery across matching stacks failed");
        helper.assertTrue(seller.getInventory().getItem(0).isEmpty()
                && seller.getInventory().getItem(2).isEmpty()
                && seller.getInventory().getItem(1).getCount() == 9,
                "Delivery removed items with different NBT");
        helper.assertTrue(data.market().buyOrders.get(id).quantity() == 1
                && data.market().buyOrders.get(id).escrow() == 100L,
                "Partial delivery left the wrong escrow or quantity");
        helper.assertTrue(MarketService.pendingCoin(helper.getLevel().getServer(), seller.getUUID()) == 400L,
                "Offline seller credit was lost");
        helper.assertTrue(MarketService.deliveries(helper.getLevel().getServer(), buyer.getUUID())
                .get(0).quantity() == 4, "Buyer delivery was not queued");
        helper.assertTrue(MarketService.fulfillRequest(rival, id, 1L, 100L, 1)
                == MarketResult.STALE, "Outdated delivery succeeded");
        helper.assertTrue(MarketService.repriceRequest(buyer, id, 2L, 200L)
                == MarketResult.REPRICED, "Buy order increase failed");
        helper.assertTrue(data.balance(buyer.getUUID()).coinMinorUnits() == 400L,
                "Price increase did not add escrow");
        helper.assertTrue(MarketService.fulfillRequest(seller, id, 2L, 100L, 1)
                == MarketResult.STALE, "Old-price delivery succeeded");
        helper.assertTrue(MarketService.repriceRequest(buyer, id, 3L, 50L)
                == MarketResult.REPRICED, "Buy order decrease failed");
        helper.assertTrue(MarketService.pendingCoin(helper.getLevel().getServer(), buyer.getUUID()) == 150L,
                "Price reduction refund was not recorded");
        helper.assertTrue(MarketService.cancelRequest(buyer, id, 4L) == MarketResult.CANCELLED,
                "Buy order cancellation failed");
        helper.assertTrue(MarketService.pendingCoin(helper.getLevel().getServer(), buyer.getUUID()) == 200L,
                "Cancellation refund was not recorded");
        helper.assertTrue(MarketService.favorites(helper.getLevel().getServer(), rival.getUUID())
                .get(0).ended(), "Finished order favorite was not marked ended");
        helper.assertTrue(MarketService.fulfillRequest(rival, id, 4L, 50L, 1)
                == MarketResult.STALE, "Cancelled order was fulfilled");

        MarketState restored = MarketState.load(data.market().save());
        helper.assertTrue(restored.buyOrders.isEmpty() || !restored.buyOrders.containsKey(id),
                "Cancelled order reappeared after reload");
        helper.assertTrue(restored.coinCredits.get(buyer.getUUID()).size() == 2,
                "Separate refunds were merged after reload");
        helper.assertTrue(restored.favorites.get(rival.getUUID()).get(0).ended(),
                "Ended favorite was not persisted");

        seller.getInventory().setItem(0, sample.copyWithCount(1));
        long listingId = data.market().nextId;
        helper.assertTrue(MarketService.list(seller, 0, 1, 90L, sample) == MarketResult.LISTED,
                "Listing to use as buy-order sample failed");
        long fromMarketId = data.market().nextId;
        helper.assertTrue(MarketService.createRequest(buyer, -1, listingId, 1, 50L, wrong)
                == MarketResult.STALE, "Forged market sample was accepted");
        helper.assertTrue(MarketService.createRequest(buyer, -1, listingId, 1, 50L, sample)
                == MarketResult.REQUESTED, "Market order sample was rejected");
        helper.assertTrue(MarketNetwork.summaries(data.market()).stream()
                .anyMatch(summary -> MarketKey.of(summary.item()).equals(MarketKey.of(sample))
                        && summary.quantity() == 1 && summary.buyQuantity() == 1),
                "Buy and sell orders were not merged into one market card");
        helper.assertTrue(MarketService.addFavorite(buyer, fromMarketId) == MarketResult.FAVORITED,
                "Active buy order could not be saved");
        long fromFavoriteId = data.market().nextId;
        helper.assertTrue(MarketService.createRequest(buyer, -2, 0L, 1, 25L, sample)
                == MarketResult.REQUESTED, "Favorite sample was rejected");
        helper.assertTrue(data.market().buyOrders.containsKey(fromFavoriteId),
                "Favorite-backed order was not created");
        seller.getInventory().setItem(0, sample.copyWithCount(1));
        rival.getInventory().setItem(0, sample.copyWithCount(1));
        helper.assertTrue(MarketService.fulfillRequest(seller, fromMarketId, 1L, 50L, 1)
                == MarketResult.FULFILLED, "Last unit was not delivered");
        helper.assertTrue(MarketService.fulfillRequest(rival, fromMarketId, 1L, 50L, 1)
                == MarketResult.STALE, "Rival delivered an already-filled last unit");
        helper.assertTrue(MarketService.cancelRequest(buyer, fromFavoriteId, 1L)
                == MarketResult.CANCELLED, "Favorite-backed order could not be cancelled");
        helper.assertTrue(MarketService.cancel(seller, listingId, 1L)
                == MarketResult.CANCELLED, "Sample listing cleanup failed");
        helper.succeed();
    }


    @GameTest(template = "empty")
    public static void oldPendingCoinMigrationAndPartialCoinClaim(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        CompoundTag old = new CompoundTag();
        ListTag accounts = new ListTag();
        CompoundTag account = new CompoundTag();
        account.putUUID("player", owner);
        account.putLong("amount", 250L);
        accounts.add(account);
        old.put("pendingCoin", accounts);
        MarketState migrated = MarketState.load(old);
        helper.assertTrue(migrated.coinCredits.get(owner).size() == 1
                && migrated.coinCredits.get(owner).get(0).amount() == 250L,
                "Old pendingCoin was not migrated");
        MarketState reloaded = MarketState.load(migrated.save());
        helper.assertTrue(reloaded.coinCredits.get(owner).size() == 1,
                "Migrated coin was duplicated or lost after save/reload");

        FakePlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "coin_claimant"));
        MoneySavedData data = MoneySavedData.get(helper.getLevel().getServer());
        data.market().coinCredits.put(player.getUUID(), new ArrayList<>(List.of(
                new MarketCoinCredit(data.market().nextId++, 10L))));
        data.put(player.getUUID(), new MoneyBalance(Long.MAX_VALUE - 3L, 0L));
        long creditId = data.market().coinCredits.get(player.getUUID()).get(0).id();
        helper.assertTrue(MarketService.claimCoin(player, creditId) == MarketResult.CLAIMED,
                "Partial coin claim failed");
        helper.assertTrue(data.balance(player.getUUID()).coinMinorUnits() == Long.MAX_VALUE
                && data.market().coinCredits.get(player.getUUID()).get(0).amount() == 7L,
                "Partial coin claim lost the remainder");
        helper.assertTrue(MarketService.claimCoin(player, creditId) == MarketResult.OVERFLOW,
                "Full wallet accepted more coin");
        helper.succeed();
    }
}
