package io.github.luyingyilong.geg.market.client;

import io.github.luyingyilong.geg.client.ui.Toast;
import io.github.luyingyilong.geg.client.ui.util.OreToasts;
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
