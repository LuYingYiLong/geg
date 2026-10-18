package io.github.luyingyilong.ore.sample;

import io.github.luyingyilong.ore.*;
import io.github.luyingyilong.ore.input.Input;
import io.github.luyingyilong.ore.input.InputControl;
import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.window.WindowManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.lwjgl.glfw.GLFW;

/** A separate consumer, using only the single copied library directory and ModularUI. */
@Mod(OreSampleMod.ID)
public final class OreSampleMod {
    public static final String ID = "ore_sample";
    public OreSampleMod(FMLJavaModLoadingContext context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Client.initialize(context.getModEventBus()));
    }
    private static final class Client {
        private static int ticks, phase;
        private static Window root;
        static void initialize(IEventBus bus) {
            var cache = Minecraft.getInstance().gameDirectory.toPath().resolve(ID).resolve("avatars");
            OreClient.initialize(OreClientOptions.defaults(ID, cache), bus, MinecraftForge.EVENT_BUS);
            MinecraftForge.EVENT_BUS.addListener(Client::tick);
        }
        static void tick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END || phase == 99) return;
            var mc = Minecraft.getInstance();
            if (mc.getOverlay() != null || mc.screen == null) return;
            try {
                if (++ticks > 600) throw new AssertionError("Portability smoke timeout at phase " + phase);
                if (phase == 0) {
                    var input = new Input().placeholder(Component.literal("Portable input / 中文"));
                    var content = new Box().gap(4).add(new Label(Component.literal("Portable Ore")).hero(true))
                            .add(new InputControl(input)).add(new Button(Component.literal("Consumer button")));
                    root = Window.embedded().content(content); root.open(); phase = 1;
                } else if (phase == 1 && ticks > 15) {
                    if (!OreClient.options().hostModId().equals(ID)) throw new AssertionError("Wrong host identity");
                    var key = new ScreenEvent.KeyPressed.Pre(mc.screen, GLFW.GLFW_KEY_F8, 0, 0);
                    MinecraftForge.EVENT_BUS.post(key);
                    if (!key.isCanceled() || WindowManager.nativeWindows() != 1) throw new AssertionError("Inspector F8 was not installed");
                    phase = 2;
                } else if (phase == 2 && ticks > 40) {
                    if (WindowManager.nativeWindows() != 1) throw new AssertionError("Inspector failed to render");
                    for (String file : new String[]{"font/ore.json", "themes/ore.json", "textures/modularui/icons/arrow_down.png"})
                        if (mc.getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath("ore", file)).isEmpty())
                            throw new AssertionError("Missing library resource: " + file);
                    System.out.println("ORE_PORTABILITY_CLIENT_PASS host=" + ID);
                    phase = 99; WindowManager.shutdown(null); root.close(); mc.stop();
                }
            } catch (Throwable failure) {
                failure.printStackTrace(); System.out.println("ORE_PORTABILITY_CLIENT_FAIL");
                phase = 99; WindowManager.shutdown(null); mc.stop();
            }
        }
    }
}
