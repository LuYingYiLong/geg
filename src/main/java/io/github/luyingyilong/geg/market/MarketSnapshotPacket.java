package io.github.luyingyilong.geg.market;

import io.github.luyingyilong.geg.market.client.ClientMarketState;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;


public record MarketSnapshotPacket(CompoundTag payload) {
    public static void encode(MarketSnapshotPacket packet, FriendlyByteBuf buffer) {
        buffer.writeNbt(packet.payload);
    }


    public static MarketSnapshotPacket decode(FriendlyByteBuf buffer) {
        CompoundTag payload = buffer.readNbt();
        return new MarketSnapshotPacket(payload == null ? new CompoundTag() : payload);
    }


    public static void handle(MarketSnapshotPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT, () -> () -> ClientMarketState.accept(packet.payload)
        ));
        context.setPacketHandled(true);
    }
}
