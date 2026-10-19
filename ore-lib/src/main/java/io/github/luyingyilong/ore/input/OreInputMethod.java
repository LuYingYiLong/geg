package io.github.luyingyilong.ore.input;

import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;

/** Native text input follows Ore focus, including fields disappearing or their screen closing. */
public final class OreInputMethod {
    private static final java.util.Map<io.github.luyingyilong.ore.window.WindowContext, State> STATES = new java.util.WeakHashMap<>();
    private static final class State {
        final InputMethodSession<Input> session = new InputMethodSession<>();
        Input focused;
        Screen screen;
        boolean failed;
        InputPreedit lastPreedit=InputPreedit.EMPTY;
    }
    private static State state(Input input) { return STATES.computeIfAbsent(input.getWindowContext(), key -> new State()); }
    public static void releaseWindow(io.github.luyingyilong.ore.window.WindowContext context) {
        State state = STATES.remove(context);
        if (state != null) { if (state.focused != null) state.focused.clearPreeditDisplay(); state.session.clear(); }
    }

    private OreInputMethod() { }

    static void focus(Input input) {
        State state = state(input);
        if (state.failed || !input.supportsInputMethod() || !input.getWindowContext().getFocused()) return;
        try {
            state.session.focus(input, () -> {
                if (!input.getWindowContext().getNativeWindow() && ModList.get().isLoaded("imblocker")) return new ImBlockerInputMethod(input);
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
        State state = STATES.get(input.getWindowContext());
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
            if (!entry.getKey().getNativeWindow() && state.focused != null && event.getScreen() == state.screen) release(state.focused);
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (var entry : java.util.List.copyOf(STATES.entrySet())) {
            var context = entry.getKey(); State state = entry.getValue(); Input input = state.focused;
            if (input == null) continue;
            if (!context.getFocused() || !context.getNativeWindow() && Minecraft.getInstance().screen != state.screen
                    || !input.isFocused() || !input.inputVisible() || !input.inputMethodInteractive()) { release(input); continue; }
            try {
                state.session.update(input, true);
                InputPreedit preedit=state.session.inlinePreedit(input)?state.session.preedit(input):InputPreedit.EMPTY;
                if(!preedit.equals(state.lastPreedit)) {
                    state.lastPreedit=preedit;
                    if(context.getOwner()!=null)context.getOwner().dispatchInput(new io.github.luyingyilong.ore.event.CompositionEvent(
                            context,io.github.luyingyilong.ore.event.CompositionEvent.Action.UPDATE,preedit));
                }
            } catch (RuntimeException | LinkageError error) { fail(state, error); }
        }
    }
}
