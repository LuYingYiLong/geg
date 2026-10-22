package io.github.luyingyilong.ore.window;

import brachy.modularui.utils.Point;
import brachy.modularui.widgets.textfield.TextFieldHandler;
import io.github.luyingyilong.ore.control.base.*;
import io.github.luyingyilong.ore.control.display.Label;
import io.github.luyingyilong.ore.control.input.*;
import io.github.luyingyilong.ore.control.layout.Box;
import io.github.luyingyilong.ore.control.layout.Scroll;
import io.github.luyingyilong.ore.control.navigation.TabItem;
import io.github.luyingyilong.ore.control.navigation.Tabs;
import io.github.luyingyilong.ore.event.EventGeometry;
import io.github.luyingyilong.ore.event.WindowInputs;
import io.github.luyingyilong.ore.render.Canvas;
import io.github.luyingyilong.ore.render.CanvasTransform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
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

/** Opt-in real GLFW regression, including a scaled external Canvas. Never use player saves. */
@Mod.EventBusSubscriber(modid = "geg", value = Dist.CLIENT)
public final class TabsClientTest {
    private static int ticks, phase, mode, fieldIndex, commits, editorPaints, previousPaints;
    private static long waitUntil;
    private static Tabs tabs;
    private static Window root;
    private static List<InputControl> fields;
    private static Scroll scroll;
    private static Canvas canvas;
    private static ScreenWindowHost externalHost;
    private static Snapshot expected;
    private static String beforeEdit;
    private static ImeSpy ime;
    private static Segmented segmented;

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("ore.tabs.smoke") || event.phase != TickEvent.Phase.END || phase == 99) return;
        Minecraft mc = Minecraft.getInstance(); if (mc.player == null) return;
        try {
            if (++ticks > 3000) throw new AssertionError("Tabs timeout mode=" + mode + " phase=" + phase);
            if (System.nanoTime() < waitUntil) return;
            if (phase == 0) {
                if (mode == 0 && (mc.screen != null || mc.getOverlay() != null || ticks < 40)) return;
                Input input = new Input(); input.setTextNoSignal("Retained draft 中文");
                TextArea area = new TextArea(0); area.setRows(3); area.setTextNoSignal("First line 中文\nSecond line 😀\nThird line");
                NumberInput number = new NumberInput(); number.setTextNoSignal("12+3");
                fields = List.of(new InputControl(input), new InputControl(area), new InputControl(number));
                var logo = io.github.luyingyilong.ore.render.TextureSource.region(io.github.luyingyilong.ore.render.TextureRegion.full(
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ore", "textures/icons/logo.png"), 128, 128));
                Box editors = new Box().setGap(4);
                editors.addChild(new Control() {
                    @Override protected void render(io.github.luyingyilong.ore.render.DrawContext context, int x, int y) {
                        editorPaints++;
                    }
                });
                fields.forEach(editors::addChild);
                segmented = new Segmented(List.of(Component.literal("选项 A"), Component.literal("Option B")))
                        .setIcon(0, logo).setIcon(1, logo);
                editors.addChild(segmented);
                Box rows = new Box().setGap(4);
                for (int i = 0; i < 50; i++) rows.addChild(new Label(Component.literal("Row " + i)));
                scroll = new Scroll().setContent(rows);
                tabs = new Tabs().addItem(new TabItem("edit", Component.literal("编辑"), editors, logo, false))
                        .addItem(new TabItem("scroll", Component.literal("记录"), scroll, logo, false))
                        .addItem(new TabItem("disabled", Component.literal("禁用"), new Control(), null, true));
                if (mode == 0) {
                    root = Window.embedded().setContent(tabs); root.open();
                    GLFW.glfwFocusWindow(mc.getWindow().getWindow());
                }
                else if (mode == 1) {
                    root = Window.nativeWindow(new WindowOptions("Ore Tabs test", 512, 480, 256, 320, 2)).setContent(tabs);
                    root.open(); root.requestFocus();
                } else {
                    GLFW.glfwFocusWindow(mc.getWindow().getWindow());
                    mc.setScreen(new External());
                }
                fieldIndex = 0; commits = 0;
                number.valueCommitted().connect(tabs, value -> commits++);
                next(1);
            } else if (phase == 1 && fields.get(0).getWidget().isValid() && tabs.isReady() && tabs.getWindowContext().getFocused()) {
                root = tabs.getWindowContext().getOwner();
                root.getFocusManager().requestFocus(fields.get(fieldIndex), root.getContext().getMui());
                next(2);
            } else if (phase == 2) {
                Input input = fields.get(fieldIndex).getWidget();
                check(input.isFocused(), "Editor has native focus");
                beforeEdit = input.getText();
                if (input instanceof NumberInput) tap(GLFW.GLFW_KEY_A, GLFW.GLFW_MOD_CONTROL);
                WindowInputs.text(root, input instanceof NumberInput ? "12+" : "!", 0, root.getContext().getMui());
                TextFieldHandler editor = editor(input);
                editor.setCursor(input instanceof TextArea ? new Point(3, 1) : new Point(3, 0), true);
                editor.setOffsetCursor(new Point(1, 0));
                expected = snapshot(input);
                ime = input instanceof NumberInput ? null : spy(input);
                click(tabBar().getChild(1)); next(3);
            } else if (phase == 3) {
                Input input = fields.get(fieldIndex).getWidget();
                check(tabs.getSelectedKey().equals("scroll") && !input.isFocused(), "Real GLFW click switches page and releases editor focus");
                check(snapshot(input).equals(expected), "Inactive page retains draft and selection: " + expected + " -> " + snapshot(input));
                if (ime != null) check(ime.cancels > 0 && ime.closes == 1, "Page switch cancels preedit and releases its IME connection once");
                if (input instanceof NumberInput number) check(number.getError() == null && commits == 0, "Switch does not commit unfinished numeric expression");
                check(!fields.get(fieldIndex).isInputAccessible() && input.isValid(), "Hidden editor stays initialized but cannot receive input");
                scroll.setScroll(0, 52);
                click(tabBar().getChild(0)); next(4);
            } else if (phase == 4) {
                Input input = fields.get(fieldIndex).getWidget();
                check(tabs.getSelectedKey().equals("edit") && snapshot(input).equals(expected), "Returning preserves the same document and selection");
                root.getFocusManager().requestFocus(fields.get(fieldIndex), root.getContext().getMui());
                check(snapshot(input).equals(expected), "Restoring editor focus preserves both selection endpoints");
                tap(GLFW.GLFW_KEY_Z, GLFW.GLFW_MOD_CONTROL);
                check(input.getText().equals(beforeEdit), "Undo history survives hidden page");
                tap(GLFW.GLFW_KEY_Y, GLFW.GLFW_MOD_CONTROL);
                check(input.getText().equals(expected.text), "Redo history survives hidden page");
                // Code-controlled switching exercises the non-pointer path too.
                tabs.setSelectedKeyNoSignal("scroll"); next(5);
            } else if (phase == 5) {
                check(scroll.getScrollY() == 52, "Page scroll position survives switching");
                tabs.setSelectedKeyNoSignal("edit");
                if (++fieldIndex < fields.size()) next(1);
                else {
                    for (int i = 0; i < 8; i++) tabs.addItem(new TabItem("long_" + i, Component.literal("非常长的页签标题需要省略 " + i), new Label(Component.literal("Page " + i))));
                    tabs.setSelectedKey("long_7"); next(6);
                    tabs.setMaxTabWidth(64);
                }
            } else if (phase == 6) {
                check(tabs.getHeaderScrollX() > 0, "Long headers scroll internally");
                root.getFocusManager().requestFocus(tabBar(), root.getContext().getMui());
                tap(GLFW.GLFW_KEY_HOME, 0); tap(GLFW.GLFW_KEY_ENTER, 0);
                movePointer(fields.get(0));
                previousPaints = editorPaints; next(7);
            } else if (phase == 7) {
                // Native hosts render on their own schedule; don't capture the previous page's framebuffer.
                if (editorPaints <= previousPaints) return;
                check(tabs.getSelectedKey().equals("edit"), "Keyboard activation works after horizontal scrolling");
                check(commits == 0 && fields.stream().allMatch(field -> field.getWidget().isValid()), "No input reinitialization or numeric commit during page changes");
                check(tabBar().getChild(1).getThemeState().equals("normal"), "Leaving the header clears hover on the next rendered frame");
                check(tabBar().getChild(0).getOwnedParts().get("label").getThemeItem(io.github.luyingyilong.ore.theme.OreTheme.FONT_COLOR) == 0xFFFFFFFF,
                        "Rendered tab captions resolve white theme text");
                check(fields.stream().allMatch(field -> field.getWidget().getPanel().getAlpha() == 1),
                        "Input adapter panel cannot fade retained editors to zero alpha");
                aligned(tabBar().getChild(0).getOwnedParts(), "label", "icon");
                aligned(segmented.getOwnedParts(), "option_0", "option_icon_0");
                check(tabBar().getChildren().stream().allMatch(caption -> caption.getBounds().width() <= 64), "Actual tab geometry obeys maximum width");
                Label longTitle = (Label) tabBar().getChild(3).getOwnedParts().get("label");
                StringBuilder shown = new StringBuilder();
                longTitle.displayedLines(mc.font, longTitle.getBounds().width(), longTitle.getBounds().height())
                        .forEach(line -> line.accept((index, style, codepoint) -> { shown.appendCodePoint(codepoint); return true; }));
                check(shown.toString().endsWith("…") || shown.toString().endsWith("..."), "Constrained titles actually render an ellipsis: " + shown);
                capture(mc);
                if (mode == 2) { canvas.close(); externalHost.close(); mc.setScreen(null); }
                else root.close();
                System.out.println("ORE_TABS_HOST_" + mode + "_PASS");
                if (++mode < 3) next(0);
                else { phase = 99; System.out.println("ORE_TABS_CLIENT_PASS"); mc.stop(); }
            }
            if (phase == 1 && ticks % 40 == 0)
                System.out.println("ORE_TABS_WAIT mode=" + mode + " valid=" + fields.get(0).getWidget().isValid()
                        + " ready=" + tabs.isReady() + " focus=" + tabs.getWindowContext().getFocused());
        } catch (Throwable failure) {
            failure.printStackTrace(); System.out.println("ORE_TABS_CLIENT_FAIL mode=" + mode + " phase=" + phase);
            phase = 99; if (root != null) root.close(); mc.stop();
        }
    }

    private static void next(int value) {
        phase = value; waitUntil = System.nanoTime() + 300_000_000L;
        System.out.println("ORE_TABS_STEP mode=" + mode + " field=" + fieldIndex + " phase=" + phase);
    }
    private static Control tabBar() { return ((Scroll) tabs.getChild(0)).getContent(); }
    private static void aligned(Map<String, Control> parts, String labelKey, String iconKey) {
        Rect label = parts.get(labelKey).getBounds(), icon = parts.get(iconKey).getBounds();
        check(icon.width() > 0 && icon.y() == label.y() + (label.height() - icon.height()) / 2,
                "Rendered icon shares caption's vertical center: " + labelKey);
    }
    private static void tap(int key, int mods) {
        WindowInputs.keyDown(root, key, mods, root.getContext().getMui());
        WindowInputs.keyUp(root, key, mods, root.getContext().getMui());
    }
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
        var path = mc.gameDirectory.toPath().resolve("screenshots/tabs-host-" + mode + ".png");
        java.nio.file.Files.createDirectories(path.getParent());
        var target = mode == 1 ? ((NativeWindowHost) root.getHost()).renderTarget() : mc.getMainRenderTarget();
        try (var image = net.minecraft.client.Screenshot.takeScreenshot(target)) {
            image.writeToFile(path);
            if (mode == 1) {
                for (InputControl field : fields) {
                    Rect area = field.getDisplayedBounds();
                    var context = root.getContext();
                    double sx = image.getWidth() / context.getProjectionWidth(), sy = image.getHeight() / context.getProjectionHeight();
                    int bright = 0;
                    for (int y = (int) (area.y() * sy); y < Math.min(image.getHeight(), area.bottom() * sy); y++)
                        for (int x = (int) (area.x() * sx); x < Math.min(image.getWidth(), area.right() * sx); x++) {
                            int pixel = image.getPixelRGBA(x, y);
                            if ((pixel & 255) > 180 && (pixel >> 8 & 255) > 180 && (pixel >> 16 & 255) > 180) bright++;
                        }
                    check(bright > 5, "Native editor text actually reaches its framebuffer: " + field.getWidget().getClass().getSimpleName());
                }
            }
        }
    }
    private static final class External extends Screen {
        External() { super(Component.literal("External Tabs test")); canvas = new Canvas(tabs); }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void render(GuiGraphics graphics, int x, int y, float partial) {
            externalHost = ScreenWindowHost.getOrCreate(this); canvas.attach(externalHost);
            canvas.setViewport(new Rect(0, 0, 128, 180)).setTransform(new CanvasTransform(6, 30, 1.25));
            canvas.render(graphics, x, y, partial);
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
