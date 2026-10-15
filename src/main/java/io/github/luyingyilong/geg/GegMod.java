package io.github.luyingyilong.geg;

import io.github.luyingyilong.geg.market.MarketBlocks;
import io.github.luyingyilong.geg.market.MarketEvents;
import io.github.luyingyilong.geg.market.MarketNetwork;
import io.github.luyingyilong.geg.market.MarketRules;
import io.github.luyingyilong.geg.client.ui.OreTheme;
import io.github.luyingyilong.geg.money.MoneyCommands;
import io.github.luyingyilong.geg.money.MoneyEvents;
import io.github.luyingyilong.geg.money.MoneyNetwork;
import io.github.luyingyilong.geg.money.MoneyRules;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.ModList;

@Mod(GegMod.MODID)
public final class GegMod {
    public static final String MODID = "geg";

    public GegMod(FMLJavaModLoadingContext context) {
        MarketRules.register();
        MoneyRules.register();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> OreTheme::register);
        MarketBlocks.register(context.getModEventBus());
        MoneyNetwork.register();
        MarketNetwork.register();
        MinecraftForge.EVENT_BUS.addListener(MoneyCommands::register);
        MinecraftForge.EVENT_BUS.addListener(MoneyEvents::onPlayerLogin);
        MinecraftForge.EVENT_BUS.addListener(MoneyEvents::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(MoneyEvents::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(MarketEvents::onPlayerLogout);
        if (ModList.get().isLoaded("ftbquests")) {
            io.github.luyingyilong.geg.money.integration.FtbCoinIntegration.register();
        }
    }
}
