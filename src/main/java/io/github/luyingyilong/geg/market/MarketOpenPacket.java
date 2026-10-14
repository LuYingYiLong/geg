package io.github.luyingyilong.geg.market;

import com.mojang.logging.LogUtils;
import io.github.luyingyilong.geg.market.client.ClientMarketScreen;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;


public record MarketOpenPacket() {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void encode(MarketOpenPacket packet, FriendlyByteBuf buffer) {
    }


    public static MarketOpenPacket decode(FriendlyByteBuf buffer) {
        return new MarketOpenPacket();
    }


    public static void handle(MarketOpenPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            LOGGER.debug("Received market open packet");
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientMarketScreen::open);
        });
        context.setPacketHandled(true);
    }
}
