package io.github.luyingyilong.ore.window;

import brachy.modularui.utils.Point;
import brachy.modularui.widgets.textfield.TextFieldHandler;
import io.github.luyingyilong.ore.control.base.*;
import io.github.luyingyilong.ore.control.input.*;
import io.github.luyingyilong.ore.control.layout.Scroll;
import io.github.luyingyilong.ore.control.navigation.Tabs;
import io.github.luyingyilong.ore.event.*;
import io.github.luyingyilong.ore.gui.*;
import io.github.luyingyilong.ore.gui.dev.UiSourceWatcher;
import io.github.luyingyilong.ore.render.*;
import io.github.luyingyilong.ore.signal.SignalScope;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import java.io.StringReader;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Opt-in real client/GLFW test. Runs exclusively from an isolated test world. */
@Mod.EventBusSubscriber(modid = "geg", value = Dist.CLIENT)
public final class XmlUiClientTest {
    private static int ticks, phase, mode, fieldIndex, commits, submissions;
    private static long until, expectedVersion;
    private static Window root;
    private static UiDocument document;
    private static SignalScope owner;
    private static Tabs tabs;
    private static List<InputControl> fields;
    private static Scroll scroll;
    private static Snapshot expected;
    private static String before;
    private static ImeSpy ime;
    private static Canvas canvas;
    private static ScreenWindowHost externalHost;
    private static UiSourceWatcher sourceWatcher;
    private static Path sourceFile;
    private static final AtomicReference<String> XML = new AtomicReference<>();
    private static final String INITIAL = """
            <Box id="root" gap="4">
              <Tabs id="tabs" sizeFlagsVertical="SIZE_EXPAND_FILL">
                <item key="edit" text="Editor"><Scroll id="editScroll"><slot name="content"><Form id="edit">
                  <FormItem id="inputField" name="name" label="Name"><Input id="input" text="Retained 中文" placeholder="Hint"/><validate rule="required"/></FormItem>
                  <FormItem id="areaField" name="body" label="Body"><TextArea id="area" text="First line&#10;Second line 中文" rows="3"/></FormItem>
                  <FormItem id="numberField" name="amount" label="Amount"><NumberInput id="number" text="12" decimalPlaces="2" minimumValue="0.01" maximumValue="100"/></FormItem>
                </Form></slot></Scroll></item>
                <item key="scroll" text="Scroll"><Scroll id="scroll" horizontal="AUTO" vertical="AUTO"><slot name="content"><Control minimum="1000,1000"/></slot></Scroll></item>
              </Tabs>
              <Button id="send" text="Submit" sizeFlagsVertical="SIZE_SHRINK_BEGIN"><connect signal="pressed" handler="submit"/></Button>
            </Box>
            """;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("ore.xml.smoke") || event.phase != TickEvent.Phase.END || phase == 99) return;
        Minecraft mc = Minecraft.getInstance(); if (mc.player == null) return;
        try {
            if (++ticks > 3000) throw new AssertionError("XML timeout mode=" + mode + " phase=" + phase + " status=" + (document == null ? "none" : document.getStatus()));
            if (System.nanoTime() < until) return;
            if (phase == 0) {
                // An isolated unattended client can open PauseScreen before the first test tick.
                // Release only that startup pause; never dismiss unrelated screens silently.
                if (mode == 0) {
                    mc.options.pauseOnLostFocus = false;
                    GLFW.glfwFocusWindow(mc.getWindow().getWindow());
                    if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
                    if (mc.screen != null || mc.getOverlay() != null || ticks < 40) {
                        if (ticks % 200 == 0) System.out.println("ORE_XML_WAIT screen=" + mc.screen + " overlay=" + mc.getOverlay());
                        return;
                    }
                }
                XML.set(INITIAL); owner = new SignalScope();
                UiLoader loader = new UiLoader(UiTypeRegistry.createDefault(), id -> new StringReader(XML.get()));
                Path sourceRoot = mc.gameDirectory.toPath().resolve("build/tmp/xml-sources/host-" + mode);
                sourceFile = sourceRoot.resolve("assets/test/gui/client.xml");
                Files.createDirectories(sourceFile.getParent()); Files.writeString(sourceFile, INITIAL);
                sourceWatcher = new UiSourceWatcher(sourceRoot, loader);
                document = loader.open(ResourceLocation.parse("test:gui/client.xml"), new UiBindings().action("submit", () -> submissions++), owner);
                check(UiSourceWatcher.getSourceStatus(document.getResource()).running(), "Live fixture reads watched source XML");
                tabs = document.getRoot().findControlFromId("tabs", Tabs.class);
                fields = List.of("input", "area", "number").stream().map(id -> document.getRoot().findControlFromId(id, InputControl.class)).toList();
                scroll = document.getRoot().findControlFromId("scroll", Scroll.class);
                if (mode == 0) { root = Window.embedded().setContent(document.getRoot()); root.open(); GLFW.glfwFocusWindow(mc.getWindow().getWindow()); }
                else if (mode == 1) { root = Window.nativeWindow(new WindowOptions("Ore XML test", 512, 480, 256, 320, 2)).setContent(document.getRoot()); root.open(); root.requestFocus(); }
                else { GLFW.glfwFocusWindow(mc.getWindow().getWindow()); mc.setScreen(new External()); }
                fieldIndex = commits = submissions = 0;
                ((NumberInput) fields.get(2).getWidget()).valueCommitted().connect(owner, ignored -> commits++);
                next(1);
            } else if (phase == 1) {
                if (!fields.get(0).getWidget().isValid() || !tabs.isReady() || !tabs.getWindowContext().getFocused()) return;
                root = tabs.getWindowContext().getOwner();
                tabs.setSelectedKeyNoSignal("scroll");
                if (mode == 2) externalHost.prepareInput(); else root.layoutViewport(240, 200, mc.font);
                scroll.setScroll(11, 70);
                check(scroll.getScrollX() == 11 && scroll.getScrollY() == 70, "Host measured scroll before initializing the fixture");
                tabs.setSelectedKeyNoSignal("edit");
                next(2);
            } else if (phase == 2) {
                root.getFocusManager().requestFocus(fields.get(fieldIndex),root.getContext().getMui());
                click(fields.get(fieldIndex)); next(21);
            } else if (phase == 21) {
                Input input = fields.get(fieldIndex).getWidget();
                check(input.isFocused(), "Raw GLFW press focuses actual editor");
                before = input.getText();
                if (input instanceof NumberInput) tap(GLFW.GLFW_KEY_A, GLFW.GLFW_MOD_CONTROL);
                WindowInputs.text(root, input instanceof NumberInput ? "12+" : "!", 0, root.getContext().getMui());
                TextFieldHandler editor = editor(input); editor.setCursor(new Point(3, 0), true); editor.setOffsetCursor(new Point(1, 0));
                expected = snapshot(input); ime = input instanceof NumberInput ? null : spy(input);
                expectedVersion = document.getVersion() + 1;
                XML.set(INITIAL.replace("gap=\"4\"", "gap=\"" + (fieldIndex + 5) + "\"").replace("placeholder=\"Hint\"", "placeholder=\"Updated hint\""));
                Files.writeString(sourceFile, XML.get()); next(3);
            } else if (phase == 3) {
                if (ime != null) {
                    if (document.getStatus() != UiDocument.Status.WAITING_FOR_IME) return;
                    check(document.getVersion() == expectedVersion - 1 && snapshot(fields.get(fieldIndex).getWidget()).equals(expected), "Active preedit defers commit without changing document");
                    ime.composing = false;
                }
                next(4);
            } else if (phase == 4) {
                check(document.getStatus() != UiDocument.Status.ERROR, document.getError());
                if (document.getVersion() != expectedVersion) return;
                InputControl field = fields.get(fieldIndex); Input input = field.getWidget();
                check(document.getRoot().findControlFromId(List.of("input", "area", "number").get(fieldIndex)) == field, "Stable XML IDs keep actual MUI editor instances");
                check(input.isValid() && input.isFocused() && root.getFocusManager().getFocused() == field, "Native and logical focus restored together");
                check(snapshot(input).equals(expected), "Draft and both selection endpoints retained");
                boolean composingBeforeUndo = input.getComposing();
                tap(GLFW.GLFW_KEY_Z, GLFW.GLFW_MOD_CONTROL);
                check(input.getText().equals(before), "Real undo survives XML commit: expected=" + before
                        + " actual=" + input.getText() + " composing=" + composingBeforeUndo);
                tap(GLFW.GLFW_KEY_Y, GLFW.GLFW_MOD_CONTROL); check(input.getText().equals(expected.text()), "Real redo survives XML commit");
                check(commits == 0, "Reload does not commit NumberInput");
                tabs.setSelectedKeyNoSignal("scroll"); next(5);
            } else if (phase == 5) {
                check(scroll.getScrollX() == 11 && scroll.getScrollY() == 70, "Hidden Scroll retains offsets across reload: " + scroll.getScrollX() + "," + scroll.getScrollY());
                tabs.setSelectedKeyNoSignal("edit");
                if (++fieldIndex < fields.size()) next(2);
                else {
                    root.getContext().getMui().removeFocus();
                    var send = document.getRoot().findControlFromId("send");
                    Rect point = EventGeometry.displayed(send); WindowInputs.press(root, point.x() + 2, point.y() + 2, root.getContext().getMui());
                    expectedVersion = document.getVersion() + 1;
                    Files.writeString(sourceFile, XML.get() + "\n"); next(6);
                }
            } else if (phase == 6) {
                if (document.getVersion() != expectedVersion) return;
                var send = document.getRoot().findControlFromId("send"); Rect point = EventGeometry.displayed(send);
                WindowInputs.release(root, point.x() + 2, point.y() + 2); check(submissions == 0, "Old release cannot activate newly rebound XML button");
                click(send); next(7);
            } else if (phase == 7) {
                check(submissions == 1, "One valid action produces one XML handler call");
                capture(mc);
                if (canvas != null) { canvas.close(); canvas = null; }
                sourceWatcher.close(); sourceWatcher = null;
                document.close(); owner.close(); root.close(); root.queueFree();
                if (mode == 2) mc.setScreen(null);
                mode++; if (mode < 3) next(0); else { System.out.println("ORE_XML_SOURCE_CLIENT_PASS"); System.out.println("ORE_XML_CLIENT_PASS"); phase = 99; mc.stop(); }
            }
        } catch (Throwable failure) {
            failure.printStackTrace(); System.out.println("ORE_XML_CLIENT_FAIL mode=" + mode + " phase=" + phase);
            if (sourceWatcher != null) sourceWatcher.close();
            phase = 99; if (mode == 2) mc.setScreen(null); if (document != null) document.close(); if (root != null) root.close(); mc.stop();
        }
    }
    private static void next(int value) { phase = value; until = System.nanoTime() + 250_000_000L; System.out.println("ORE_XML_STEP mode=" + mode + " field=" + fieldIndex + " phase=" + phase); }
    private static void tap(int key, int mods) { WindowInputs.keyDown(root, key, mods, root.getContext().getMui()); WindowInputs.keyUp(root, key, mods, root.getContext().getMui()); }
    private static void click(Control target) {
        movePointer(target);
        long handle = root.getContext().getHandle();
        var button = GLFW.glfwSetMouseButtonCallback(handle, null); GLFW.glfwSetMouseButtonCallback(handle, button);
        button.invoke(handle, 0, GLFW.GLFW_PRESS, 0); button.invoke(handle, 0, GLFW.GLFW_RELEASE, 0);
    }
    private static void movePointer(Control target) {
        Rect area = EventGeometry.displayed(target);
        var context = root.getContext(); long handle = context.getHandle();
        double x, y;
        if (context.getNativeWindow()) { x = context.clientX(area.x() + 4); y = context.clientY(area.y() + 6); }
        else {
            var main = Minecraft.getInstance().getWindow();
            x = (area.x() + 4) * main.getScreenWidth() / (double) main.getGuiScaledWidth();
            y = (area.y() + 6) * main.getScreenHeight() / (double) main.getGuiScaledHeight();
        }
        var move = GLFW.glfwSetCursorPosCallback(handle, null); GLFW.glfwSetCursorPosCallback(handle, move);
        GLFW.glfwSetCursorPos(handle, x, y); move.invoke(handle, x, y);
    }
    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static TextFieldHandler editor(Input input) throws ReflectiveOperationException {
        for (Class<?> type = input.getClass(); type != null; type = type.getSuperclass())
            try { return (TextFieldHandler) field(type, "handler").get(input); } catch (NoSuchFieldException ignored) { }
        throw new AssertionError("Missing MUI handler");
    }
    private record Snapshot(String text, int x, int y, int anchorX, int anchorY) { }
    private static Snapshot snapshot(Input input) throws ReflectiveOperationException {
        var editor = editor(input); var cursor = editor.getMainCursor(); var anchor = editor.getOffsetCursor();
        return new Snapshot(input.getText(), cursor.x, cursor.y, anchor.x, anchor.y);
    }
    private static final class ImeSpy { int cancels, closes; boolean composing = true; }
    private static ImeSpy spy(Input input) throws ReflectiveOperationException {
        Object state = ((Map<?, ?>) field(OreInputMethod.class, "STATES").get(null)).get(input.getWindowContext());
        check(state != null, "Real host establishes an IME session");
        Object session = field(state.getClass(), "session").get(state);
        Field connection = field(session.getClass(), "connection"); Object original = connection.get(session);
        check(original != null, "IME connection is active");
        Class<?> api = Class.forName("io.github.luyingyilong.ore.control.input.InputMethodSession$Connection"); ImeSpy spy = new ImeSpy();
        connection.set(session, Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[]{api}, (proxy, method, args) -> {
            if (method.getName().equals("preedit")) return spy.composing ? new InputPreedit("pinyin", 6) : InputPreedit.EMPTY;
            if (method.getName().equals("composing")) return spy.composing;
            if (method.getName().equals("inlinePreedit")) return true;
            if (method.getName().equals("cancelComposition")) { spy.cancels++; spy.composing = false; }
            if (method.getName().equals("close")) spy.closes++;
            method.setAccessible(true);
            try { return method.invoke(original, args); } catch (InvocationTargetException error) { throw error.getCause(); }
        }));
        return spy;
    }
    private static void capture(Minecraft mc) throws Exception {
        var file = mc.gameDirectory.toPath().resolve("screenshots/xml-host-" + mode + ".png"); java.nio.file.Files.createDirectories(file.getParent());
        var target = mode == 1 ? ((NativeWindowHost) root.getHost()).renderTarget() : mc.getMainRenderTarget();
        try (var image = net.minecraft.client.Screenshot.takeScreenshot(target)) { image.writeToFile(file); }
    }
    private static final class External extends Screen {
        External() { super(Component.literal("External XML test")); canvas = new Canvas(document.getRoot()); }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void render(GuiGraphics graphics, int x, int y, float partial) {
            externalHost = ScreenWindowHost.getOrCreate(this); canvas.attach(externalHost);
            canvas.setViewport(new Rect(0, 0, 240, 200)).setTransform(new CanvasTransform(6, 18, 1.25)); canvas.render(graphics, x, y, partial);
        }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
