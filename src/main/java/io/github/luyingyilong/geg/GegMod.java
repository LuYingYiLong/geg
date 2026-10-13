package io.github.luyingyilong.geg;

import io.github.luyingyilong.geg.money.MoneyCommands;
import io.github.luyingyilong.geg.money.MoneyEvents;
import io.github.luyingyilong.geg.money.MoneyNetwork;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;


@Mod(GegMod.MODID)
public final class GegMod {
    public static final String MODID = "geg";


    public GegMod() {
        MoneyNetwork.register();
        MinecraftForge.EVENT_BUS.addListener(MoneyCommands::register);
        MinecraftForge.EVENT_BUS.addListener(MoneyEvents::onPlayerLogin);
    }
}
