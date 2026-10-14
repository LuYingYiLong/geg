package io.github.luyingyilong.geg;

import io.github.luyingyilong.geg.market.MarketBlocks;
import io.github.luyingyilong.geg.market.MarketEvents;
import io.github.luyingyilong.geg.market.MarketNetwork;
import io.github.luyingyilong.geg.money.MoneyCommands;
import io.github.luyingyilong.geg.money.MoneyEvents;
import io.github.luyingyilong.geg.money.MoneyNetwork;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(GegMod.MODID)
public final class GegMod {
    public static final String MODID = "geg";

    public GegMod() {
        MarketBlocks.register(FMLJavaModLoadingContext.get().getModEventBus());
        MoneyNetwork.register();
        MarketNetwork.register();
        MinecraftForge.EVENT_BUS.addListener(MoneyCommands::register);
        MinecraftForge.EVENT_BUS.addListener(MoneyEvents::onPlayerLogin);
        MinecraftForge.EVENT_BUS.addListener(MarketEvents::onPlayerLogout);
    }
}
