package io.github.luyingyilong.geg;

import io.github.luyingyilong.geg.client.GegClient;
import io.github.luyingyilong.geg.market.MarketBlocks;
import io.github.luyingyilong.geg.market.MarketEvents;
import io.github.luyingyilong.geg.market.MarketNetwork;
import io.github.luyingyilong.geg.market.MarketRules;
import io.github.luyingyilong.geg.money.MoneyCommands;
import io.github.luyingyilong.geg.money.MoneyEvents;
import io.github.luyingyilong.geg.money.MoneyNetwork;
import io.github.luyingyilong.geg.money.MoneyRules;
import io.github.luyingyilong.geg.organization.OrganizationEvents;
import io.github.luyingyilong.geg.organization.OrganizationNetwork;
import io.github.luyingyilong.geg.sound.GegSounds;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(GegMod.MODID)
public final class GegMod {
    public static final String MODID = "geg";

    public GegMod(FMLJavaModLoadingContext context) {
        MarketRules.register();
        MoneyRules.register();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> GegClient.initialize(context.getModEventBus()));
        MarketBlocks.register(context.getModEventBus());
        GegSounds.register(context.getModEventBus());
        MoneyNetwork.register();
        MarketNetwork.register();
        OrganizationNetwork.register();
        io.github.luyingyilong.geg.chat.ChatNetwork.register();
        MinecraftForge.EVENT_BUS.addListener(io.github.luyingyilong.geg.chat.ChatCommands::register);
        io.github.luyingyilong.geg.mail.MailNetwork.register();
        MinecraftForge.EVENT_BUS.addListener(io.github.luyingyilong.geg.mail.MailService::onStarted);
        MinecraftForge.EVENT_BUS.addListener(io.github.luyingyilong.geg.mail.MailService::onStopped);
        MinecraftForge.EVENT_BUS.addListener(io.github.luyingyilong.geg.mail.MailService::onLogin);
        MinecraftForge.EVENT_BUS.addListener(io.github.luyingyilong.geg.mail.MailService::onLogout);
        MinecraftForge.EVENT_BUS.addListener(io.github.luyingyilong.geg.mail.MailService::onTick);
        MinecraftForge.EVENT_BUS.addListener(MoneyCommands::register);
        MinecraftForge.EVENT_BUS.addListener(MoneyEvents::onPlayerLogin);
        MinecraftForge.EVENT_BUS.addListener(OrganizationEvents::onPlayerLogin);
        MinecraftForge.EVENT_BUS.addListener(MoneyEvents::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(MoneyEvents::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(MarketEvents::onPlayerLogout);
        if (ModList.get().isLoaded("ftbquests")) {
            io.github.luyingyilong.geg.money.integration.FtbCoinIntegration.register();
        }
    }
}
