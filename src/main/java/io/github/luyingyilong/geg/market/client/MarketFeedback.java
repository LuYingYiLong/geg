package io.github.luyingyilong.geg.market.client;

import io.github.luyingyilong.ore.Toast;
import io.github.luyingyilong.ore.util.OreToasts;
import io.github.luyingyilong.geg.market.MarketResult;
import net.minecraft.network.chat.Component;

/** Shared presentation for authoritative market results and local validation failures. */
final class MarketFeedback {
    private MarketFeedback() { }

    static void show(MarketResult result) {
        OreToasts.show("geg:market:feedback", new Toast(Component.translatable(result.translationKey()))
                .type(result.success() ? Toast.Type.PRIMARY : Toast.Type.WARNING));
    }
}
