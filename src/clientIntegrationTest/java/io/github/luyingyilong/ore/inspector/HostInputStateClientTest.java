package io.github.luyingyilong.ore.inspector;

import brachy.modularui.utils.Point;
import brachy.modularui.widgets.textfield.TextFieldHandler;
import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.control.input.*;
import io.github.luyingyilong.ore.control.layout.Box;
import io.github.luyingyilong.ore.event.WindowInputs;
import io.github.luyingyilong.ore.window.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

/** Opt-in isolated client: real GLFW hosts, document/selection preservation and instrumented IMM cleanup. */
@Mod.EventBusSubscriber(modid = "geg", value = Dist.CLIENT)
public final class HostInputStateClientTest {
    private static int phase, ticks, fieldIndex, commits, changes, actions;
    private static long waitUntil;
    private static Window main, inspector;
    private static NativeWindowHost nativeHost;
    private static InputControl nativeField;
    private static List<InputControl> fields;
    private static Button action;
    private static boolean originalFullscreen;
    private static Snapshot expected;
    private static ImeSpy ime;
    private static Snapshot nativeExpected;

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("ore.host.input.smoke") || event.phase != TickEvent.Phase.END || phase == 99) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        try {
            if (++ticks > 2400) throw new AssertionError("Host input timeout phase " + phase);
            if (phase == 0) {
                originalFullscreen = mc.getWindow().isFullscreen();
                Input input = new Input(); input.setTextNoSignal("Main draft 中文 / selection");
                TextArea area = new TextArea(0); area.setRows(3); area.setTextNoSignal("First line 中文\nSecond line 😀\nThird line");
                NumberInput number = new NumberInput(); number.setTextNoSignal("12+");
                fields = List.of(new InputControl(input), new InputControl(area), new InputControl(number));
                Box body = new Box(); fields.forEach(body::addChild);
                action = new Button(Component.literal("Host test action")); body.addChild(action);
                main = Window.embedded().setContent(body); main.setDebugName("Host input integration target");
                action.pressed().connect(main, () -> actions++);
                fields.forEach(field -> field.getWidget().textChanged().connect(main, value -> changes++));
                number.valueCommitted().connect(main, value -> commits++);
                main.open(); OreInspector.open(); next(1);
            } else if (phase == 1 && ready() && OreInspector.view() != null) {
                inspector = OreInspector.view().window(); nativeHost = (NativeWindowHost) inspector.getHost();
                nativeField = (InputControl) find(inspector, control -> control instanceof InputControl);
                nativeHost.requestFocus(); next(2);
            } else if (phase == 2 && ready() && inspector.getContext().getFocused()) {
                inspector.getFocusManager().requestFocus(nativeField, inspector.getContext().getMui());
                nativeField.getWidget().setTextNoSignal("Inspector draft / selection");
                pressNative(); next(3);
            } else if (phase == 3 && ready() && inspector.getPointerCaptured(0)) {
                select(nativeField.getWidget()); expected = snapshot(nativeField.getWidget());
                ime = spy(nativeField.getWidget());
                nativeFocus(false); nativeFocus(false); nativeChars('X'); next(4);
            } else if (phase == 4 && ready() && !inspector.getContext().getFocused()) {
                checkInactive(inspector, nativeField, expected, ime);
                nativeFocus(true); next(5);
            } else if (phase == 5 && ready() && inspector.getContext().getFocused()) {
                checkRestored(inspector, nativeField, expected);
                nativeHost.requestFocus(); next(6);
            } else if (phase == 6 && ready()) {
                ime = spy(nativeField.getWidget());
                GLFW.glfwIconifyWindow(nativeHost.nativeHandle()); next(7);
            } else if (phase == 7 && ready() && GLFW.glfwGetWindowAttrib(nativeHost.nativeHandle(), GLFW.GLFW_ICONIFIED) != 0) {
                checkInactive(inspector, nativeField, expected, ime);
                nativeHost.requestFocus(); next(8);
            } else if (phase == 8 && ready() && inspector.getContext().getFocused()) {
                checkRestored(inspector, nativeField, expected);
                pressNative(); next(9);
            } else if (phase == 9 && ready() && inspector.getPointerCaptured(0)) {
                select(nativeField.getWidget()); expected = snapshot(nativeField.getWidget());
                nativeExpected = expected;
                GLFW.glfwSetWindowSize(nativeHost.nativeHandle(), 1001, 701); next(10);
            } else if (phase == 10 && ready()) {
                checkRestored(inspector, nativeField, expected);
                check(!inspector.getPointerCaptured(0) && !inspector.getContext().mouseDown(0), "Native resize cancels capture");
                nativeKey(GLFW.GLFW_KEY_A, GLFW.GLFW_REPEAT); nativeChars('a'); next(18);
            } else if (phase == 18 && ready()) {
                checkRestored(inspector, nativeField, expected);
                nativeKey(GLFW.GLFW_KEY_A, GLFW.GLFW_RELEASE);
                focusMain(mc); next(11);
            } else if (phase == 11 && ready() && mc.isWindowActive() && main.getContext().getFocused()) {
                InputControl field = fields.get(fieldIndex);
                main.getFocusManager().requestFocus(field, main.getContext().getMui());
                select(field.getWidget()); expected = snapshot(field.getWidget());
                ime = field.getWidget() instanceof NumberInput ? null : spy(field.getWidget());
                GLFW.glfwIconifyWindow(mc.getWindow().getWindow()); nativeHost.requestFocus(); next(12);
            } else if (phase == 12 && ready() && GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(), GLFW.GLFW_ICONIFIED) != 0) {
                checkInactive(main, fields.get(fieldIndex), expected, ime);
                focusMain(mc); next(13);
            } else if (phase == 13 && ready() && mc.isWindowActive()) {
                checkRestored(main, fields.get(fieldIndex), expected);
                check(commits == 0 && changes == 0, "Host suspension cannot commit NumberInput or mutate any draft");
                if (fields.get(fieldIndex).getWidget() instanceof NumberInput number)
                    check(number.getError() == null, "Host suspension must not validate an unfinished number expression");
                if (++fieldIndex < fields.size()) next(11);
                else {
                    main.getFocusManager().requestFocus(fields.get(0), main.getContext().getMui());
                    pressMain(mc, fields.get(0)); select(fields.get(0).getWidget()); expected = snapshot(fields.get(0).getWidget());
                    ime = spy(fields.get(0).getWidget());
                    mc.getWindow().toggleFullScreen(); next(14);
                }
            } else if (phase == 14 && ready()) {
                checkRestored(main, fields.get(0), expected);
                check(!main.getPointerCaptured(0) && !main.getContext().mouseDown(0), "Fullscreen invalidates the old pointer gesture");
                check(ime.cancels > 0, "Fullscreen cancels the old composition");
                mc.getWindow().toggleFullScreen(); next(15);
            } else if (phase == 15 && ready()) {
                checkRestored(main, fields.get(0), expected);
                check(snapshot(nativeField.getWidget()).equals(nativeExpected), "Main fullscreen changes cannot mutate the Inspector draft or selection");
                main.getFocusManager().requestFocus(action, main.getContext().getMui());
                mainKey(mc, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_PRESS);
                GLFW.glfwIconifyWindow(mc.getWindow().getWindow()); nativeHost.requestFocus(); next(16);
            } else if (phase == 16 && ready()) {
                check(!main.getContext().keyDown(GLFW.GLFW_KEY_ENTER), "Loss clears held activation keys");
                focusMain(mc); next(17);
            } else if (phase == 17 && ready() && mc.isWindowActive()) {
                mainKey(mc, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_REPEAT);
                mainKey(mc, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_RELEASE);
                check(actions == 0, "Old repeat/release cannot fire a business action after restoration");
                mainKey(mc, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_PRESS);
                mainKey(mc, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_RELEASE);
                check(actions == 1, "Fresh key activation still works exactly once");
                check(commits == 0 && changes == 0, "No transition rewrites or commits drafts");
                System.out.println("ORE_HOST_INPUT_STATE_CLIENT_PASS"); finish(mc);
            }
        } catch (Throwable failure) {
            failure.printStackTrace(); System.out.println("ORE_HOST_INPUT_STATE_CLIENT_FAIL phase=" + phase); finish(mc);
        }
    }

    private static void next(int value) { phase = value; waitUntil = System.nanoTime() + 350_000_000L; }
    private static boolean ready() { return System.nanoTime() >= waitUntil; }
    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static TextFieldHandler editor(Input input) throws ReflectiveOperationException {
        for (Class<?> type = input.getClass(); type != null; type = type.getSuperclass())
            try { return (TextFieldHandler) field(type, "handler").get(input); }
            catch (NoSuchFieldException ignored) { }
        throw new AssertionError("Missing editor fixture");
    }
    private static void select(Input input) throws ReflectiveOperationException {
        TextFieldHandler editor = editor(input);
        Point caret = input instanceof TextArea ? new Point(3, 1) : new Point(Math.min(8, input.getText().length()), 0);
        editor.setCursor(caret, true); editor.setOffsetCursor(new Point(1, 0));
    }
    private record Snapshot(String text, int caretX, int caretY, int anchorX, int anchorY) { }
    private static Snapshot snapshot(Input input) throws ReflectiveOperationException {
        TextFieldHandler editor = editor(input); Point caret = editor.getMainCursor(), anchor = editor.getOffsetCursor();
        return new Snapshot(input.getText(), caret.x, caret.y, anchor.x, anchor.y);
    }
    private static void checkInactive(Window window, InputControl field, Snapshot before, ImeSpy spy) throws ReflectiveOperationException {
        check(!field.getWidget().isFocused() && !imeOwns(field.getWidget()), "Inactive host releases MUI focus and IME");
        check(!window.getPointerCaptured(0) && !window.getContext().mouseDown(0), "Inactive host cancels pointer capture and held buttons");
        if (spy != null) check(spy.cancels > 0 && spy.closes == 1, "The instrumented real IMM connection cancels and closes once");
        check(snapshot(field.getWidget()).equals(before), "Blur preserves text and both selection endpoints: " + before + " -> " + snapshot(field.getWidget()));
    }
    private static void checkRestored(Window window, InputControl field, Snapshot before) throws ReflectiveOperationException {
        boolean expectedIme = !(field.getWidget() instanceof NumberInput);
        check(window.getFocusManager().getFocused() == field && field.getWidget().isFocused() && imeOwns(field.getWidget()) == expectedIme,
                "Logical focus, MUI focus and IME ownership restore together");
        check(snapshot(field.getWidget()).equals(before), "Restoration preserves the draft and selection: " + before + " -> " + snapshot(field.getWidget()));
    }
    private static Object session(Input input) throws ReflectiveOperationException {
        Object state = ((Map<?, ?>) field(OreInputMethod.class, "STATES").get(null)).get(input.getWindowContext());
        return state == null ? null : field(state.getClass(), "session").get(state);
    }
    private static boolean imeOwns(Input input) throws ReflectiveOperationException {
        Object session = session(input); return session != null && field(session.getClass(), "owner").get(session) == input;
    }
    private static final class ImeSpy { int cancels, closes; boolean composing = true; }
    private static ImeSpy spy(Input input) throws ReflectiveOperationException {
        Object session = session(input); check(session != null && imeOwns(input), "The client must establish a real IME session: field=" + fieldIndex
                + " focused=" + input.isFocused() + " host=" + input.getWindowContext().getFocused()
                + " valid=" + input.isValid() + " text=" + input.getText());
        Field connection = field(session.getClass(), "connection"); Object original = connection.get(session);
        Class<?> api = Class.forName("io.github.luyingyilong.ore.control.input.InputMethodSession$Connection");
        ImeSpy spy = new ImeSpy();
        connection.set(session, Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[]{api}, (proxy, method, arguments) -> {
            if (method.getName().equals("preedit")) return spy.composing ? new InputPreedit("pinyin", 6) : InputPreedit.EMPTY;
            if (method.getName().equals("composing")) return spy.composing;
            if (method.getName().equals("inlinePreedit")) return true;
            if (method.getName().equals("cancelComposition")) { spy.cancels++; spy.composing = false; }
            if (method.getName().equals("close")) spy.closes++;
            method.setAccessible(true);
            try { return method.invoke(original, arguments); }
            catch (InvocationTargetException failure) { throw failure.getCause(); }
        }));
        return spy;
    }
    private static void nativeFocus(boolean focused) {
        var callback = GLFW.glfwSetWindowFocusCallback(nativeHost.nativeHandle(), null);
        try { callback.invoke(nativeHost.nativeHandle(), focused); }
        finally { GLFW.glfwSetWindowFocusCallback(nativeHost.nativeHandle(), callback); }
    }
    private static void nativeChars(char value) {
        var callback = GLFW.glfwSetCharCallback(nativeHost.nativeHandle(), null);
        try { callback.invoke(nativeHost.nativeHandle(), value); }
        finally { GLFW.glfwSetCharCallback(nativeHost.nativeHandle(), callback); }
    }
    private static void nativeKey(int key, int action) {
        var callback = GLFW.glfwSetKeyCallback(nativeHost.nativeHandle(), null);
        try { callback.invoke(nativeHost.nativeHandle(), key, 0, action, 0); }
        finally { GLFW.glfwSetKeyCallback(nativeHost.nativeHandle(), callback); }
    }
    private static void mainKey(Minecraft mc, int key, int action) {
        long handle = mc.getWindow().getWindow();
        var callback = GLFW.glfwSetKeyCallback(handle, null);
        try { callback.invoke(handle, key, 0, action, 0); }
        finally { GLFW.glfwSetKeyCallback(handle, callback); }
    }
    private static void pressNative() {
        Rect bounds = nativeField.getDisplayedBounds();
        var move = GLFW.glfwSetCursorPosCallback(nativeHost.nativeHandle(), null);
        try { move.invoke(nativeHost.nativeHandle(), inspector.getContext().clientX(bounds.x() + 4), inspector.getContext().clientY(bounds.y() + 5)); }
        finally { GLFW.glfwSetCursorPosCallback(nativeHost.nativeHandle(), move); }
        var press = GLFW.glfwSetMouseButtonCallback(nativeHost.nativeHandle(), null);
        try { press.invoke(nativeHost.nativeHandle(), 0, GLFW.GLFW_PRESS, 0); }
        finally { GLFW.glfwSetMouseButtonCallback(nativeHost.nativeHandle(), press); }
    }
    private static void pressMain(Minecraft mc, InputControl field) {
        Rect bounds = field.getDisplayedBounds(); var main = mc.getWindow();
        GLFW.glfwSetCursorPos(main.getWindow(), (bounds.x() + 4) * main.getScreenWidth() / (double) main.getGuiScaledWidth(),
                (bounds.y() + 5) * main.getScreenHeight() / (double) main.getGuiScaledHeight());
        WindowInputs.press(HostInputStateClientTest.main, bounds.x() + 4, bounds.y() + 5, HostInputStateClientTest.main.getContext().getMui());
    }
    private static void focusMain(Minecraft mc) { GLFW.glfwRestoreWindow(mc.getWindow().getWindow()); GLFW.glfwFocusWindow(mc.getWindow().getWindow()); }
    private static Control find(Control root, java.util.function.Predicate<Control> predicate) {
        if (predicate.test(root)) return root;
        for (Control child : root.getChildren()) { Control found = find(child, predicate); if (found != null) return found; }
        return null;
    }
    private static void finish(Minecraft mc) {
        phase = 99;
        try { if (mc.getWindow().isFullscreen() != originalFullscreen) mc.getWindow().toggleFullScreen(); }
        finally { focusMain(mc); OreInspector.stop(); if (main != null) main.close(); mc.stop(); }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
