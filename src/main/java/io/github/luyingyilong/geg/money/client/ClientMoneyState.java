package io.github.luyingyilong.geg.money.client;

import io.github.luyingyilong.geg.GegMod;
import io.github.luyingyilong.geg.money.MoneyBalance;
import io.github.luyingyilong.geg.money.MoneySyncPacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;


@Mod.EventBusSubscriber(modid = GegMod.MODID, value = Dist.CLIENT)
public final class ClientMoneyState {
    private static volatile MoneyBalance balance = MoneyBalance.ZERO;


    private ClientMoneyState() {
    }


    public static MoneyBalance balance() {
        return balance;
    }


    public static void update(MoneySyncPacket packet) {
        if (packet.coinMinorUnits() >= 0L && packet.gem() >= 0L) {
            balance = new MoneyBalance(packet.coinMinorUnits(), packet.gem());
        }
    }


    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        balance = MoneyBalance.ZERO;
    }
}
