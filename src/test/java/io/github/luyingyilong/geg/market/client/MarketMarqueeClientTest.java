package io.github.luyingyilong.geg.market.client;

import io.github.luyingyilong.ore.Label;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.Grid;
import io.github.luyingyilong.geg.market.MarketSummary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in reproduction against the real market card and its hover coordinates. Never shipped. */
@Mod.EventBusSubscriber(modid = "geg", value = Dist.CLIENT)
public final class MarketMarqueeClientTest {
    private static int phase, waiting, ticks;
    private static Label name;
    private static long firstStart;

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("geg.market.marquee.smoke") || event.phase != TickEvent.Phase.END || phase == 99) return;
        Minecraft mc = Minecraft.getInstance();
        ticks++; waiting++;
        try {
            if (ticks > 1500) throw new AssertionError("Market marquee test timed out at " + phase);
            if (mc.player == null || mc.level == null) return;
            mc.options.pauseOnLostFocus = false;
            if (phase == 0) {
                var start = new CompoundTag(); start.putString("kind", "start"); ClientMarketState.accept(start);
                var summaries = new CompoundTag(); summaries.putString("kind", "summaries");
                var rows = new ListTag();
                for (int index = 0; index < 100; index++) {
                    ItemStack item = new ItemStack(Items.OAK_PLANKS);
                    item.setHoverName(Component.literal(index + " 超长物品名称 Long market item name ABCDEFGHIJKLMNOPQRSTUVWXYZ"));
                    rows.add(new MarketSummary(item, 123, 100, 0, 0, index + 1).save());
                }
                summaries.put("entries", rows); ClientMarketState.accept(summaries);
                var end = new CompoundTag(); end.putString("kind", "end"); ClientMarketState.accept(end);
                ClientMarketScreen.open(); next();
            } else if (phase == 1 && waiting > 30) {
                Object canvas = field(null, ClientMarketScreen.class, "canvas");
                Grid<?> grid = (Grid<?>) field(canvas, canvas.getClass(), "grid");
                Control card = grid.getChildren().get(0);
                name = (Label) field(card, card.getClass(), "name");
                var area = name.getAbsoluteBounds();
                System.out.println("MARQUEE_AREA " + area + " textWidth=" + mc.font.width(name.getStyledText()));
                var move = mc.mouseHandler.getClass().getDeclaredMethod("onMove", long.class, double.class, double.class);
                move.setAccessible(true);
                move.invoke(mc.mouseHandler, mc.getWindow().getWindow(),
                        (area.x() + area.width() / 2.0) * mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth(),
                        (area.y() + area.height() / 2.0) * mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight());
                next();
            } else if (phase == 2) {
                long start = (long) field(name, Label.class, "scrollStartMs");
                if (waiting % 10 == 0) System.out.println("MARQUEE_TIMER " + waiting + " start=" + start
                        + " mouse=" + mc.mouseHandler.xpos() + "," + mc.mouseHandler.ypos());
                if (waiting == 10) {
                    firstStart = start;
                    Screenshot.grab(mc.gameDirectory, "market-marquee-start.png", mc.getMainRenderTarget(), message -> { });
                }
                if (waiting > 60) {
                    if (start == Long.MIN_VALUE || start != firstStart)
                        throw new AssertionError("Market name hover timer is missing or resets each frame");
                    Screenshot.grab(mc.gameDirectory, "market-marquee-end.png", mc.getMainRenderTarget(), message -> { });
                    System.out.println("GEG_MARKET_MARQUEE_PASS"); next();
                }
            } else if (phase == 3 && waiting > 10) { phase = 99; mc.stop(); }
        } catch (Throwable failure) {
            System.err.println("GEG_MARKET_MARQUEE_FAIL phase=" + phase);
            failure.printStackTrace(); phase = 99; mc.stop();
        }
    }

    private static Object field(Object owner, Class<?> type, String name) throws ReflectiveOperationException {
        var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    private static void next() { phase++; waiting = 0; }
}
