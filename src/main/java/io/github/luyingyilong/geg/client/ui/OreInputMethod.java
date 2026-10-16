package io.github.luyingyilong.geg.client.ui;

import com.mojang.logging.LogUtils;
import io.github.luyingyilong.geg.GegMod;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/** Native text input follows Ore focus, including fields disappearing or their screen closing. */
@Mod.EventBusSubscriber(modid = GegMod.MODID, value = Dist.CLIENT)
public final class OreInputMethod {
    private static final InputMethodSession<Input> SESSION = new InputMethodSession<>();
    private static Input focused;
    private static Screen screen;
    private static boolean failed;

    private OreInputMethod() { }

    static void focus(Input input) {
        if (failed || !input.supportsInputMethod()) return;
        try {
            SESSION.focus(input, () -> {
                if (ModList.get().isLoaded("imblocker")) return new ImBlockerInputMethod(input);
                if (Util.getPlatform() == Util.OS.WINDOWS) return new WindowsInputMethod(input);
                return () -> { };
            });
            focused = input;
            screen = Minecraft.getInstance().screen;
            SESSION.update(input, true);
        } catch (RuntimeException | LinkageError error) {
            fail(error);
        }
    }

    static void release(Input input) {
        input.clearPreeditDisplay();
        if (focused != input) return;
        focused = null;
        screen = null;
        try { SESSION.release(input); }
        catch (RuntimeException | LinkageError error) { fail(error); }
    }

    static boolean composing(Input input) {
        try { return SESSION.composing(input); }
        catch (RuntimeException | LinkageError error) { fail(error); return false; }
    }

    static InputPreedit preedit(Input input) {
        try { return SESSION.preedit(input); }
        catch (RuntimeException | LinkageError error) { fail(error); return InputPreedit.EMPTY; }
    }

    static boolean inlinePreedit(Input input) { return SESSION.inlinePreedit(input); }

    static void cancelComposition(Input input) {
        try { SESSION.cancelComposition(input); }
        catch (RuntimeException | LinkageError error) { fail(error); }
    }

    static void locateFocus(Input input) {
        try { SESSION.locateFocus(input); }
        catch (RuntimeException | LinkageError error) { fail(error); }
    }

    private static void fail(Throwable error) {
        failed = true;
        if (focused != null) focused.clearPreeditDisplay();
        focused = null;
        screen = null;
        try { SESSION.clear(); }
        catch (RuntimeException | LinkageError cleanup) { error.addSuppressed(cleanup); }
        LogUtils.getLogger().warn("Ore input method integration unavailable; using GLFW text input", error);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void screenClosing(ScreenEvent.Closing event) {
        if (focused != null && event.getScreen() == screen) release(focused);
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || focused == null) return;
        Input input = focused;
        if (Minecraft.getInstance().screen != screen || !input.isFocused()
                || !input.inputVisible() || !input.inputMethodInteractive()) {
            release(input);
            return;
        }
        try { SESSION.update(input, true); }
        catch (RuntimeException | LinkageError error) { fail(error); }
    }
}
