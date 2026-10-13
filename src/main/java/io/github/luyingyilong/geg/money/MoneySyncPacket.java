package io.github.luyingyilong.geg.money;

import io.github.luyingyilong.geg.money.client.ClientMoneyState;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;


public record MoneySyncPacket(long coinMinorUnits, long gem) {
    public static void encode(MoneySyncPacket packet, FriendlyByteBuf buffer) {
        buffer.writeLong(packet.coinMinorUnits);
        buffer.writeLong(packet.gem);
    }


    public static MoneySyncPacket decode(FriendlyByteBuf buffer) {
        return new MoneySyncPacket(buffer.readLong(), buffer.readLong());
    }


    public static void handle(MoneySyncPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> ClientMoneyState.update(packet)
        ));
        context.setPacketHandled(true);
    }
}
