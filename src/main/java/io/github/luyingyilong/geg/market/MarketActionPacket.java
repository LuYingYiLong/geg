package io.github.luyingyilong.geg.market;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;


public record MarketActionPacket(Action action, long id, long version, long price,
                                 int quantity, int slot, ItemStack expectedItem) {
    public enum Action {
        OPEN,
        REQUEST,
        DETAIL,
        CLOSE,
        LIST,
        BUY,
        REPRICE,
        CANCEL,
        FAVORITE_ADD,
        FAVORITE_REMOVE,
        CREATE_REQUEST,
        FULFILL_REQUEST,
        REPRICE_REQUEST,
        CANCEL_REQUEST,
    }


    public static MarketActionPacket request() {
        return new MarketActionPacket(Action.REQUEST, 0L, 0L, 0L, 0, 0, ItemStack.EMPTY);
    }


    public static MarketActionPacket open(BlockPos pos) {
        return new MarketActionPacket(Action.OPEN, pos.asLong(), 0L, 0L, 0, 0, ItemStack.EMPTY);
    }


    public static void encode(MarketActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.action);
        buffer.writeLong(packet.id);
        buffer.writeLong(packet.version);
        buffer.writeLong(packet.price);
        buffer.writeVarInt(packet.quantity);
        buffer.writeVarInt(packet.slot);
        buffer.writeItem(packet.expectedItem);
    }


    public static MarketActionPacket decode(FriendlyByteBuf buffer) {
        return new MarketActionPacket(buffer.readEnum(Action.class), buffer.readLong(), buffer.readLong(),
                buffer.readLong(), buffer.readVarInt(), buffer.readVarInt(), buffer.readItem());
    }


    public static void handle(MarketActionPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            if (packet.action == Action.OPEN) {
                BlockPos pos = BlockPos.of(packet.id);
                if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D
                        && player.level().getBlockState(pos).is(MarketBlocks.MARKET_TERMINAL.get())
                        && !MarketNetwork.isViewer(player)) {
                    MarketNetwork.open(player);
                }
                return;
            }
            if (!MarketNetwork.isViewer(player)) {
                return;
            }
            if (packet.action == Action.CLOSE) {
                MarketNetwork.close(player);
                return;
            }
            if (packet.action == Action.REQUEST) {
                MarketNetwork.sync(player);
                return;
            }
            if (packet.action == Action.DETAIL) {
                MarketNetwork.sendDetails(player, packet.id);
                return;
            }

            MarketResult result = switch (packet.action) {
                case LIST -> MarketService.list(player, packet.slot, packet.quantity, packet.price, packet.expectedItem);
                case BUY -> MarketService.buy(player, packet.id, packet.version, packet.price, packet.quantity);
                case REPRICE -> MarketService.reprice(player, packet.id, packet.version, packet.price);
                case CANCEL -> MarketService.cancel(player, packet.id, packet.version);
                case FAVORITE_ADD -> MarketService.addFavorite(player, packet.id);
                case FAVORITE_REMOVE -> MarketService.removeFavorite(player, packet.slot);
                case CREATE_REQUEST -> MarketService.createRequest(player, packet.slot, packet.id,
                        packet.quantity, packet.price, packet.expectedItem);
                case FULFILL_REQUEST -> MarketService.fulfillRequest(player, packet.id, packet.version,
                        packet.price, packet.quantity);
                case REPRICE_REQUEST -> MarketService.repriceRequest(player, packet.id, packet.version, packet.price);
                case CANCEL_REQUEST -> MarketService.cancelRequest(player, packet.id, packet.version);
                case OPEN, REQUEST, DETAIL, CLOSE -> MarketResult.INVALID;
            };
            MarketNetwork.feedback(player, result);
            if (result == MarketResult.STALE) {
                MarketNetwork.sync(player);
            }
        });
        context.setPacketHandled(true);
    }
}
