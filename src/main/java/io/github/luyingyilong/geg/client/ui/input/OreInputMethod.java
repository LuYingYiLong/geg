package io.github.luyingyilong.geg.client.ui.input;

import com.mojang.logging.LogUtils;
import io.github.luyingyilong.geg.GegMod;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/** Native text input follows Ore focus, including fields disappearing or their screen closing. */
@Mod.EventBusSubscriber(modid = GegMod.MODID, value = Dist.CLIENT)
public final class OreInputMethod {
    private static final java.util.Map<io.github.luyingyilong.geg.client.ui.window.WindowContext, State> STATES = new java.util.WeakHashMap<>();
    private static final class State {
        final InputMethodSession<Input> session = new InputMethodSession<>();
        Input focused;
        Screen screen;
        boolean failed;
    }
    private static State state(Input input) { return STATES.computeIfAbsent(input.windowContext(), key -> new State()); }
    public static void releaseWindow(io.github.luyingyilong.geg.client.ui.window.WindowContext context) {
        State state = STATES.remove(context);
        if (state != null) { if (state.focused != null) state.focused.clearPreeditDisplay(); state.session.clear(); }
    }

    private OreInputMethod() { }

    static void focus(Input input) {
        State state = state(input);
        if (state.failed || !input.supportsInputMethod() || !input.windowContext().focused()) return;
        try {
            state.session.focus(input, () -> {
                if (!input.windowContext().nativeWindow() && ModList.get().isLoaded("imblocker")) return new ImBlockerInputMethod(input);
                if (Util.getPlatform() == Util.OS.WINDOWS) return new WindowsInputMethod(input);
                return () -> { };
            });
            state.focused = input;
            state.screen = Minecraft.getInstance().screen;
            state.session.update(input, true);
        } catch (RuntimeException | LinkageError error) {
            fail(state, error);
        }
    }

    static void release(Input input) {
        State state = STATES.get(input.windowContext());
        input.clearPreeditDisplay();
        if (state == null) return;
        if (state.focused != input) return;
        state.focused = null;
        state.screen = null;
        try { state.session.release(input); }
        catch (RuntimeException | LinkageError error) { fail(state, error); }
    }

    static boolean composing(Input input) {
        State state = state(input);
        try { return state.session.composing(input); }
        catch (RuntimeException | LinkageError error) { fail(state, error); return false; }
    }

    static InputPreedit preedit(Input input) {
        State state = state(input);
        try { return state.session.preedit(input); }
        catch (RuntimeException | LinkageError error) { fail(state, error); return InputPreedit.EMPTY; }
    }

    static boolean inlinePreedit(Input input) { return state(input).session.inlinePreedit(input); }

    static void cancelComposition(Input input) {
        State state = state(input);
        try { state.session.cancelComposition(input); }
        catch (RuntimeException | LinkageError error) { fail(state, error); }
    }

    static void locateFocus(Input input) {
        State state = state(input);
        try { state.session.locateFocus(input); }
        catch (RuntimeException | LinkageError error) { fail(state, error); }
    }

    private static void fail(State state, Throwable error) {
        state.failed = true;
        if (state.focused != null) state.focused.clearPreeditDisplay();
        state.focused = null; state.screen = null;
        try { state.session.clear(); } catch (RuntimeException | LinkageError cleanup) { error.addSuppressed(cleanup); }
        LogUtils.getLogger().warn("Ore window input method unavailable; using GLFW text input", error);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void screenClosing(ScreenEvent.Closing event) {
        for (var entry : java.util.List.copyOf(STATES.entrySet())) {
            State state = entry.getValue();
            if (!entry.getKey().nativeWindow() && state.focused != null && event.getScreen() == state.screen) release(state.focused);
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (var entry : java.util.List.copyOf(STATES.entrySet())) {
            var context = entry.getKey(); State state = entry.getValue(); Input input = state.focused;
            if (input == null) continue;
            if (!context.focused() || !context.nativeWindow() && Minecraft.getInstance().screen != state.screen
                    || !input.isFocused() || !input.inputVisible() || !input.inputMethodInteractive()) { release(input); continue; }
            try { state.session.update(input, true); } catch (RuntimeException | LinkageError error) { fail(state, error); }
        }
    }
}
