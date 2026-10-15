package io.github.luyingyilong.geg.market;

import com.mojang.logging.LogUtils;
import io.github.luyingyilong.geg.GegMod;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.slf4j.Logger;


public final class MarketNetwork {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PROTOCOL_VERSION = "4";
    private static final int CHUNK_SIZE = 8;
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(GegMod.MODID, "market"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );
    private static final Set<UUID> VIEWERS = new HashSet<>();


    private MarketNetwork() {
    }


    public static void register() {
        CHANNEL.registerMessage(0, MarketOpenPacket.class,
                MarketOpenPacket::encode, MarketOpenPacket::decode, MarketOpenPacket::handle);
        CHANNEL.registerMessage(1, MarketSnapshotPacket.class,
                MarketSnapshotPacket::encode, MarketSnapshotPacket::decode, MarketSnapshotPacket::handle);
        CHANNEL.registerMessage(2, MarketActionPacket.class,
                MarketActionPacket::encode, MarketActionPacket::decode, MarketActionPacket::handle);
    }


    public static void open(ServerPlayer player) {
        LOGGER.debug("Opening market for {}", player.getGameProfile().getName());
        if (!MarketRules.enabled(player.server)) {
            player.sendSystemMessage(Component.translatable(MarketResult.DISABLED.translationKey()));
        }
        VIEWERS.add(player.getUUID());
        send(player, new MarketOpenPacket());
        sync(player);
    }


    public static void close(ServerPlayer player) {
        VIEWERS.remove(player.getUUID());
    }


    public static boolean isViewer(ServerPlayer player) {
        return VIEWERS.contains(player.getUUID());
    }

    public static void syncViewers(MinecraftServer server) {
        for (UUID viewerId : Set.copyOf(VIEWERS)) {
            ServerPlayer viewer = server.getPlayerList().getPlayer(viewerId);
            if (viewer == null) VIEWERS.remove(viewerId);
            else sync(viewer);
        }
    }


    public static void sendAction(MarketActionPacket packet) {
        CHANNEL.sendToServer(packet);
    }


    public static void sync(ServerPlayer player) {
        if (!isViewer(player)) {
            return;
        }
        MinecraftServer server = player.server;
        MarketState state = MarketService.state(server);
        CompoundTag start = new CompoundTag();
        start.putString("kind", "start");
        start.putLong("revision", state.revision());
        start.putBoolean("enabled", MarketRules.enabled(server));
        start.putInt("feeBasisPoints", io.github.luyingyilong.geg.money.MoneySavedData.get(server)
                .marketFeeBasisPoints());
        send(player, new MarketSnapshotPacket(start));

        sendChunks(player, "summaries", summaries(state).stream().map(MarketSummary::save).toList());
        sendChunks(player, "mine", state.listings.values().stream()
                .filter(listing -> listing.seller().equals(player.getUUID()))
                .map(MarketListing::save).toList());
        sendChunks(player, "mineRequests", state.buyOrders.values().stream()
                .filter(order -> order.buyer().equals(player.getUUID()))
                .map(MarketBuyOrder::save).toList());
        sendChunks(player, "coinCredits", MarketService.coinCredits(server, player.getUUID()).stream()
                .map(MarketCoinCredit::save).toList());
        sendChunks(player, "deliveries", MarketService.deliveries(server, player.getUUID()).stream()
                .map(MarketDelivery::save).toList());
        sendChunks(player, "history", MarketService.history(server, player.getUUID()).stream()
                .map(MarketPurchase::save).toList());
        sendChunks(player, "favorites", MarketService.favorites(server, player.getUUID()).stream()
                .map(MarketFavorite::save).toList());

        List<CompoundTag> inventory = new ArrayList<>();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (!item.isEmpty()) {
                CompoundTag entry = new CompoundTag();
                entry.putInt("slot", slot);
                entry.put("item", item.save(new CompoundTag()));
                inventory.add(entry);
            }
        }
        sendChunks(player, "inventory", inventory);

        CompoundTag end = new CompoundTag();
        end.putString("kind", "end");
        send(player, new MarketSnapshotPacket(end));
    }


    public static void sendDetails(ServerPlayer player, long listingId) {
        if (!isViewer(player)) {
            return;
        }
        MarketState state = MarketService.state(player.server);
        MarketListing target = state.listings.get(listingId);
        MarketBuyOrder buyTarget = state.buyOrders.get(listingId);
        CompoundTag start = new CompoundTag();
        start.putString("kind", "detailsStart");
        start.putLong("requestId", listingId);
        start.putLong("revision", state.revision());
        ItemStack targetItem = target != null ? target.item() : buyTarget != null ? buyTarget.item() : ItemStack.EMPTY;
        if (!targetItem.isEmpty()) {
            start.put("item", targetItem.save(new CompoundTag()));
        }
        send(player, new MarketSnapshotPacket(start));
        if (!targetItem.isEmpty()) {
            MarketKey key = MarketKey.of(targetItem);
            List<CompoundTag> offers = new ArrayList<>();
            state.buyOrders.values().stream().filter(order -> MarketKey.of(order.item()).equals(key))
                    .sorted(java.util.Comparator.comparingLong(MarketBuyOrder::unitPrice).reversed()
                            .thenComparingLong(MarketBuyOrder::id))
                    .map(order -> MarketDetail.of(order).save()).forEach(offers::add);
            state.listings.values().stream().filter(listing -> MarketKey.of(listing.item()).equals(key))
                    .sorted(java.util.Comparator.comparingLong(MarketListing::unitPrice)
                            .thenComparingLong(MarketListing::id))
                    .map(listing -> MarketDetail.of(listing).save()).forEach(offers::add);
            for (int index = 0; index < offers.size(); index += CHUNK_SIZE) {
                CompoundTag payload = new CompoundTag();
                payload.putString("kind", "details");
                payload.putLong("requestId", listingId);
                ListTag entries = new ListTag();
                offers.subList(index, Math.min(index + CHUNK_SIZE, offers.size())).forEach(entries::add);
                payload.put("entries", entries);
                send(player, new MarketSnapshotPacket(payload));
            }
        }
        CompoundTag end = new CompoundTag();
        end.putString("kind", "detailsEnd");
        end.putLong("requestId", listingId);
        send(player, new MarketSnapshotPacket(end));
        if (targetItem.isEmpty()) {
            sync(player);
        }
    }


    public static void broadcastRevision(MinecraftServer server, long revision, ItemStack changedItem) {
        CompoundTag payload = new CompoundTag();
        payload.putString("kind", "listingChange");
        payload.putLong("revision", revision);
        payload.put("item", changedItem.save(new CompoundTag()));
        MarketKey changedKey = MarketKey.of(changedItem);
        for (MarketSummary summary : summaries(MarketService.state(server))) {
            if (MarketKey.of(summary.item()).equals(changedKey)) {
                payload.put("summary", summary.save());
                break;
            }
        }
        for (UUID viewerId : Set.copyOf(VIEWERS)) {
            ServerPlayer player = server.getPlayerList().getPlayer(viewerId);
            if (player != null) {
                send(player, new MarketSnapshotPacket(payload));
                if (MarketService.state(server).favorites.getOrDefault(viewerId, List.of()).stream()
                        .anyMatch(favorite -> MarketKey.of(favorite.item()).equals(changedKey))) {
                    sync(player);
                }
            } else {
                VIEWERS.remove(viewerId);
            }
        }
    }


    static List<MarketSummary> summaries(MarketState state) {
        Map<MarketKey, MarketSummary> grouped = new LinkedHashMap<>();
        for (MarketListing listing : state.listings.values()) {
            MarketKey key = MarketKey.of(listing.item());
            MarketSummary current = grouped.get(key);
            grouped.put(key, current == null
                    ? new MarketSummary(listing.item(), listing.quantity(), listing.unitPrice(), 0L, 0L, listing.id())
                    : current.add(listing));
        }
        for (MarketBuyOrder order : state.buyOrders.values()) {
            MarketKey key = MarketKey.of(order.item());
            MarketSummary current = grouped.get(key);
            grouped.put(key, current == null
                    ? new MarketSummary(order.item(), 0L, 0L, order.quantity(), order.unitPrice(), order.id())
                    : current.add(order));
        }
        return List.copyOf(grouped.values());
    }


    private static void sendChunks(ServerPlayer player, String kind, List<CompoundTag> values) {
        for (int index = 0; index < values.size(); index += CHUNK_SIZE) {
            CompoundTag payload = new CompoundTag();
            payload.putString("kind", kind);
            ListTag entries = new ListTag();
            values.subList(index, Math.min(index + CHUNK_SIZE, values.size())).forEach(entries::add);
            payload.put("entries", entries);
            send(player, new MarketSnapshotPacket(payload));
        }
    }


    private static void send(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
