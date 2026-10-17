package io.github.luyingyilong.geg.market.client;

import io.github.luyingyilong.geg.GegMod;
import io.github.luyingyilong.geg.market.MarketActionPacket;
import io.github.luyingyilong.geg.market.MarketBuyOrder;
import io.github.luyingyilong.geg.market.MarketDetail;
import io.github.luyingyilong.geg.market.MarketFavorite;
import io.github.luyingyilong.geg.market.MarketKey;
import io.github.luyingyilong.geg.market.MarketListing;
import io.github.luyingyilong.geg.market.MarketNetwork;
import io.github.luyingyilong.geg.market.MarketPurchase;
import io.github.luyingyilong.geg.market.MarketResult;
import io.github.luyingyilong.geg.market.MarketSummary;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;


@Mod.EventBusSubscriber(modid = GegMod.MODID, value = Dist.CLIENT)
public final class ClientMarketState {
    public record InventoryEntry(int slot, ItemStack item) {
    }

    private static final List<MarketSummary> SUMMARIES = new ArrayList<>();
    private static final List<MarketListing> MINE = new ArrayList<>();
    private static final List<MarketBuyOrder> MINE_REQUESTS = new ArrayList<>();
    private static final List<MarketDetail> DETAILS = new ArrayList<>();
    private static final List<MarketPurchase> HISTORY = new ArrayList<>();
    private static final List<MarketFavorite> FAVORITES = new ArrayList<>();
    private static final List<InventoryEntry> INVENTORY = new ArrayList<>();

    private static long revision;
    private static boolean marketEnabled = true;
    private static int feeBasisPoints;
    private static long detailRequestedId;
    private static ItemStack detailItem = ItemStack.EMPTY;
    private static boolean detailLoading;
    private static boolean ready;
    private static boolean loading;


    private ClientMarketState() {
    }


    public static void accept(CompoundTag payload) {
        switch (payload.getString("kind")) {
            case "result" -> MarketFeedback.show(MarketResult.valueOf(payload.getString("result")));
            case "start" -> {
                SUMMARIES.clear();
                MINE.clear();
                MINE_REQUESTS.clear();
                DETAILS.clear();
                detailLoading = !detailItem.isEmpty();
                HISTORY.clear();
                FAVORITES.clear();
                INVENTORY.clear();
                revision = payload.getLong("revision");
                marketEnabled = !payload.contains("enabled") || payload.getBoolean("enabled");
                feeBasisPoints = payload.getInt("feeBasisPoints");
                ready = false;
                loading = true;
            }
            case "summaries" -> {
                for (Tag raw : payload.getList("entries", Tag.TAG_COMPOUND)) {
                    MarketSummary summary = MarketSummary.load((CompoundTag) raw);
                    if (summary != null) {
                        SUMMARIES.add(summary);
                    }
                }
            }
            case "mine" -> {
                for (Tag raw : payload.getList("entries", Tag.TAG_COMPOUND)) {
                    MarketListing listing = MarketListing.load((CompoundTag) raw);
                    if (listing != null) {
                        MINE.add(listing);
                    }
                }
            }
            case "mineRequests" -> {
                for (Tag raw : payload.getList("entries", Tag.TAG_COMPOUND)) {
                    MarketBuyOrder order = MarketBuyOrder.load((CompoundTag) raw);
                    if (order != null) MINE_REQUESTS.add(order);
                }
            }
            case "history" -> {
                for (Tag raw : payload.getList("entries", Tag.TAG_COMPOUND)) {
                    MarketPurchase purchase = MarketPurchase.load((CompoundTag) raw);
                    if (purchase != null) {
                        HISTORY.add(purchase);
                    }
                }
            }
            case "favorites" -> {
                for (Tag raw : payload.getList("entries", Tag.TAG_COMPOUND)) {
                    MarketFavorite favorite = MarketFavorite.load((CompoundTag) raw);
                    if (favorite != null) FAVORITES.add(favorite);
                }
            }
            case "inventory" -> {
                for (Tag raw : payload.getList("entries", Tag.TAG_COMPOUND)) {
                    CompoundTag entry = (CompoundTag) raw;
                    ItemStack item = ItemStack.of(entry.getCompound("item"));
                    if (!item.isEmpty()) {
                        INVENTORY.add(new InventoryEntry(entry.getInt("slot"), item));
                    }
                }
            }
            case "end" -> {
                MarketSearchIndex.clear();
                SUMMARIES.sort(Comparator.comparingLong(MarketSummary::minPrice)
                        .thenComparingLong(MarketSummary::representativeId));
                ready = true;
                loading = false;
                requeryDetails();
                ClientMarketScreen.refresh();
            }
            case "detailsStart" -> {
                if (payload.getLong("requestId") != detailRequestedId) {
                    break;
                }
                DETAILS.clear();
                ItemStack item = ItemStack.of(payload.getCompound("item"));
                if (!item.isEmpty() && MarketKey.of(item).equals(MarketKey.of(detailItem))) {
                    detailItem = item.copyWithCount(1);
                }
            }
            case "details" -> {
                if (payload.getLong("requestId") != detailRequestedId) {
                    break;
                }
                for (Tag raw : payload.getList("entries", Tag.TAG_COMPOUND)) {
                    MarketDetail offer = MarketDetail.load((CompoundTag) raw);
                    if (offer != null) {
                        DETAILS.add(offer);
                    }
                }
            }
            case "detailsEnd" -> {
                if (payload.getLong("requestId") != detailRequestedId) {
                    break;
                }
                DETAILS.sort(Comparator.comparing(MarketDetail::buyOrder).reversed()
                        .thenComparing((left, right) -> left.buyOrder()
                                ? Long.compare(right.unitPrice(), left.unitPrice())
                                : Long.compare(left.unitPrice(), right.unitPrice()))
                        .thenComparingLong(MarketDetail::id));
                detailLoading = false;
                ClientMarketScreen.refresh();
            }
            case "listingChange" -> {
                long nextRevision = payload.getLong("revision");
                if (nextRevision <= revision) {
                    break;
                }
                if (loading) {
                    break;
                }
                if (nextRevision != revision + 1L) {
                    loading = true;
                    MarketNetwork.sendAction(MarketActionPacket.request());
                    break;
                }
                ItemStack changedItem = ItemStack.of(payload.getCompound("item"));
                if (changedItem.isEmpty()) {
                    loading = true;
                    MarketNetwork.sendAction(MarketActionPacket.request());
                    break;
                }
                MarketKey changedKey = MarketKey.of(changedItem);
                SUMMARIES.removeIf(summary -> MarketKey.of(summary.item()).equals(changedKey));
                if (payload.contains("summary", Tag.TAG_COMPOUND)) {
                    MarketSummary summary = MarketSummary.load(payload.getCompound("summary"));
                    if (summary != null) {
                        SUMMARIES.add(summary);
                    }
                }
                SUMMARIES.sort(Comparator.comparingLong(MarketSummary::minPrice)
                        .thenComparingLong(MarketSummary::representativeId));
                revision = nextRevision;
                MarketSearchIndex.clear();
                if (!detailItem.isEmpty() && MarketKey.of(detailItem).equals(changedKey)) {
                    requeryDetails();
                }
                ClientMarketScreen.refresh();
            }
            case "invalidate" -> {
                if (payload.getLong("revision") > revision && !loading && ClientMarketScreen.isOpen()) {
                    loading = true;
                    MarketNetwork.sendAction(MarketActionPacket.request());
                }
            }
            default -> {
            }
        }
    }


    public static List<MarketSummary> summaries() {
        return SUMMARIES;
    }


    public static List<MarketListing> mine() {
        return MINE;
    }


    public static List<MarketBuyOrder> mineRequests() {
        return MINE_REQUESTS;
    }


    public static void requestDetails(long listingId, ItemStack item) {
        detailRequestedId = listingId;
        detailItem = item.copyWithCount(1);
        DETAILS.clear();
        detailLoading = true;
        MarketNetwork.sendAction(new MarketActionPacket(MarketActionPacket.Action.DETAIL,
                listingId, 0L, 0L, 0, 0, ItemStack.EMPTY));
    }


    public static void clearDetails() {
        DETAILS.clear();
        detailRequestedId = 0L;
        detailItem = ItemStack.EMPTY;
        detailLoading = false;
    }


    public static List<MarketDetail> detailsFor(ItemStack item) {
        if (detailLoading || detailItem.isEmpty() || !MarketKey.of(detailItem).equals(MarketKey.of(item))) {
            return List.of();
        }
        return DETAILS;
    }


    public static boolean isDetailLoading(ItemStack item) {
        return detailLoading && !detailItem.isEmpty() && MarketKey.of(detailItem).equals(MarketKey.of(item));
    }


    private static void requeryDetails() {
        if (detailItem.isEmpty()) {
            return;
        }
        MarketSummary target = SUMMARIES.stream()
                .filter(summary -> MarketKey.of(summary.item()).equals(MarketKey.of(detailItem)))
                .findFirst().orElse(null);
        if (target == null) {
            DETAILS.clear();
            detailLoading = false;
        } else {
            requestDetails(target.representativeId(), detailItem);
        }
    }


    public static List<MarketPurchase> history() {
        return HISTORY;
    }


    public static List<MarketFavorite> favorites() {
        return FAVORITES;
    }

    public static List<InventoryEntry> inventory() {
        return INVENTORY;
    }


    public static boolean ready() {
        return ready;
    }

    public static boolean marketEnabled() {
        return marketEnabled;
    }

    public static int feeBasisPoints() {
        return feeBasisPoints;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        SUMMARIES.clear();
        MINE.clear();
        MINE_REQUESTS.clear();
        DETAILS.clear();
        HISTORY.clear();
        FAVORITES.clear();
        INVENTORY.clear();
        revision = 0L;
        marketEnabled = true;
        feeBasisPoints = 0;
        detailRequestedId = 0L;
        detailItem = ItemStack.EMPTY;
        detailLoading = false;
        ready = false;
        loading = false;
    }
}
