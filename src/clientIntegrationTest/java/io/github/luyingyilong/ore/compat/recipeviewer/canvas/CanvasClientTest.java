package io.github.luyingyilong.ore.compat.recipeviewer.canvas;

import io.github.luyingyilong.ore.Button;
import io.github.luyingyilong.ore.compat.recipeviewer.RecipeViewerClientTest;
import io.github.luyingyilong.ore.event.EventGeometry;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.render.Canvas;
import io.github.luyingyilong.ore.render.CanvasTransform;
import io.github.luyingyilong.ore.window.ScreenWindowHost;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Real GLFW inputs, actual viewer recipe layouts, and an ordinary container Screen host. */
@Mod.EventBusSubscriber(modid = "geg", value = Dist.CLIENT)
public final class CanvasClientTest {
    private static int ticks, phase, step, changed;
    private static boolean done;
    private static CanvasDemo demo;
    private static CanvasDemo genericDemo;
    private static final List<Runnable> steps = new ArrayList<>();
    private static String clipboard;

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        String viewer = System.getProperty("ore.canvas.smoke", "");
        if (viewer.isEmpty() || event.phase != TickEvent.Phase.END || done) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        try {
            if (++ticks > 1800) throw new AssertionError("Canvas timeout phase=" + phase + " step=" + step);
            if (phase == 0) {
                if (!RecipeViewerClientTest.viewersReady(viewer)) return;
                GLFW.glfwFocusWindow(handle());
                mc.setScreen(new DemoScreen()); phase = 1; changed = ticks; return;
            }
            if (ticks < changed + 4) return;
            if (phase == 1 || phase == 3) {
                demo = CanvasDemo.INSTANCES.stream().filter(instance -> instance.content.isAlive()
                        && instance.content.getWindowContext().getOwner() != null
                        && ScreenWindowHost.get(mc.screen) != null
                        && instance.content.getWindowContext() == ScreenWindowHost.get(mc.screen).getContext()).skip(phase == 1 ? 1 : 0).findFirst().orElse(null);
                if (demo == null || !demo.input.isValid()) return;
                if (phase == 1) {
                    check(ScreenWindowHost.get(mc.screen).getExclusionAreas().size() == 2, "Two surfaces share one Screen host");
                    genericDemo = demo;
                }
                buildSteps(); step = 0; phase++; changed = ticks; return;
            }
            if (step < steps.size()) {
                System.out.println("ORE_CANVAS_STEP phase=" + phase + " step=" + step);
                steps.get(step++).run(); changed = ticks; return;
            }
            if (phase == 2 && !viewer.equals("none")) {
                mc.setScreen(null);
                String plugin = viewer.contains("emi") ? "EmiCanvasDemoPlugin" : viewer.equals("rei") ? "ReiCanvasDemoPlugin" : "JeiCanvasDemoPlugin";
                Class.forName(CanvasClientTest.class.getPackageName() + "." + plugin).getMethod("open").invoke(null);
                phase = 3; changed = ticks; return;
            }
            done = true;
            if (mc.screen instanceof DemoScreen) check(genericDemo.content.isAlive()
                    && genericDemo.input.getText().equals("r中"), "Viewer Back restores retained Canvas instances and drafts");
            if (clipboard != null) GLFW.glfwSetClipboardString(handle(), clipboard);
            System.out.println("ORE_CANVAS_" + viewer.replace('+', '_').toUpperCase(java.util.Locale.ROOT) + "_CLIENT_PASS");
            mc.setScreen(null); mc.stop();
        } catch (Throwable error) {
            done = true; error.printStackTrace();
            System.out.println("ORE_CANVAS_CLIENT_FAIL phase=" + phase + " step=" + step);
            mc.stop();
        }
    }

    private static void buildSteps() {
        steps.clear();
        steps.add(() -> click(demo.inputNode));
        steps.add(() -> {
            try { demo.lastFrame.getFont(); throw new AssertionError("DrawContext escaped its frame"); }
            catch (IllegalStateException expected) { }
            check(demo.input.isFocused(), "External Input receives native focus");
            checkImBlockerFocus(true);
            demo.input.setTextNoSignal(""); demo.textChanges = 0;
            key(GLFW.GLFW_KEY_R, GLFW.GLFW_PRESS, 0); text("r中"); key(GLFW.GLFW_KEY_R, GLFW.GLFW_RELEASE, 0);
            check(demo.input.getText().equals("r中") && demo.textChanges == 2, "No viewer interception or duplicate editing");
        });
        steps.add(() -> click(demo.numberNode));
        steps.add(() -> {
            check(demo.number.isFocused(), "NumberInput receives native focus");
            demo.number.setTextNoSignal("123456");
        });
        steps.add(() -> {
            Rect b = EventGeometry.displayed(demo.numberNode);
            move(b.x() + 5, b.y() + b.height() / 2); mouse(GLFW.GLFW_PRESS); mouse(GLFW.GLFW_RELEASE); text("9");
            check(demo.number.getText().equals("9123456"), "Mouse positions numeric caret at the start, including scaled Canvas");
        });
        steps.add(() -> click(demo.areaNode));
        steps.add(() -> {
            demo.area.setTextNoSignal(""); text("中"); tap(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_MOD_SHIFT); text("文");
            check(demo.area.getText().equals("中\n文"), "Multiline input");
            if (clipboard == null) clipboard = GLFW.glfwGetClipboardString(handle());
            GLFW.glfwSetClipboardString(handle(), "复制\n粘贴"); tap(GLFW.GLFW_KEY_A, GLFW.GLFW_MOD_CONTROL); tap(GLFW.GLFW_KEY_V, GLFW.GLFW_MOD_CONTROL);
            check(demo.area.getText().equals("复制\n粘贴"), "Paste");
            tap(GLFW.GLFW_KEY_Z, GLFW.GLFW_MOD_CONTROL); check(demo.area.getText().equals("中\n文"), "Undo");
            tap(GLFW.GLFW_KEY_Y, GLFW.GLFW_MOD_CONTROL); check(demo.area.getText().equals("复制\n粘贴"), "Redo");
            tap(GLFW.GLFW_KEY_ESCAPE, 0); check(!demo.area.isFocused(), "Esc blurs before navigating the viewer");
            checkImBlockerFocus(false);
        });
        steps.add(() -> click(demo.button));
        steps.add(() -> check(demo.presses == 1, "Release activates Button once"));
        steps.add(() -> click(demo.dropdown));
        steps.add(() -> {
            check(demo.dropdown.isOpen(), "Dropdown in external layout opens");
            var root = demo.content.getWindowContext().getOwner();
            List<Button> options = buttons(root.getFocusManager().getInputScope());
            check(options.size() == 2, "Popup owns real menu buttons"); click(options.get(1));
        });
        steps.add(() -> check(demo.dropdown.getSelected().equals("b") && !demo.dropdown.isOpen(), "Overlay option receives one click"));
        steps.add(() -> click(demo.overlayButton));
        steps.add(() -> {
            check(demo.lastModal != null && demo.lastModal.getVisible(), "Screen-wide Modal opens above recipe contents");
            check(demo.lastModal.getPanelBounds().width() > EventGeometry.displayed(demo.content).width(), "Modal uses host viewport, not recipe viewport");
            tap(GLFW.GLFW_KEY_ESCAPE, 0);
        });
        steps.add(() -> { check(demo.lastModal.isDisposed(), "Modal cleans up at frame boundary"); click(demo.overlayButton); });
        steps.add(() -> {
            check(demo.lastDrawer != null && demo.lastDrawer.isOpen(), "Drawer opens from a recipe");
            click(demo.drawerInput);
        });
        steps.add(() -> {
            text("抽屉"); check(demo.drawerInput.getWidget().getText().equals("抽屉"), "Drawer uses shared native input host");
            tap(GLFW.GLFW_KEY_ESCAPE, 0); check(demo.lastDrawer.isOpen(), "First Esc only blurs Input");
        });
        steps.add(() -> tap(GLFW.GLFW_KEY_ESCAPE, 0));
        steps.add(() -> check(demo.lastDrawer.isDisposed(), "Drawer closes before native Screen navigation"));
        steps.add(() -> {
            var before = Minecraft.getInstance().screen;
            tap(GLFW.GLFW_KEY_ESCAPE, 0);
            check(Minecraft.getInstance().screen != before, "Unhandled Esc is returned to native host navigation");
        });
    }

    private static final class DemoScreen extends InventoryScreen {
        private final Canvas first, second;
        DemoScreen() {
            super(Minecraft.getInstance().player);
            first = new Canvas(new CanvasDemo(1).content);
            second = new Canvas(new CanvasDemo(2).content);
        }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
            super.render(graphics, mouseX, mouseY, partial);
            var host = ScreenWindowHost.getOrCreate(this);
            first.attach(host); second.attach(host);
            first.setViewport(new Rect(0, 0, 170, 156)).setTransform(new CanvasTransform(4, 32, 1));
            second.setViewport(new Rect(0, 0, 170, 156)).setTransform(new CanvasTransform(230, 32, 0.75));
            var before = new org.joml.Matrix4f(graphics.pose().last().pose());
            float[] color = com.mojang.blaze3d.systems.RenderSystem.getShaderColor().clone();
            first.render(graphics, mouseX, mouseY, partial); second.render(graphics, mouseX, mouseY, partial);
            check(before.equals(graphics.pose().last().pose()), "Borrowed pose is restored");
            check(java.util.Arrays.equals(color, com.mojang.blaze3d.systems.RenderSystem.getShaderColor()), "Borrowed shader tint is restored");
        }
    }

    private static List<Button> buttons(Control root) {
        List<Button> out = new ArrayList<>();
        if (root == null) return out;
        if (root instanceof Button button) out.add(button);
        for (Control child : root.getChildren()) out.addAll(buttons(child)); return out;
    }
    private static void click(Control node) { Rect b = EventGeometry.displayed(node); move(b.x() + 6, b.y() + b.height() / 2); mouse(GLFW.GLFW_PRESS); mouse(GLFW.GLFW_RELEASE); }
    private static void move(int x, int y) {
        var mc = Minecraft.getInstance(); var w = mc.getWindow();
        double cx = (double) x * w.getScreenWidth() / mc.screen.width, cy = (double) y * w.getScreenHeight() / mc.screen.height;
        var callback = GLFW.glfwSetCursorPosCallback(handle(), null); GLFW.glfwSetCursorPosCallback(handle(), callback);
        GLFW.glfwSetCursorPos(handle(), cx, cy); callback.invoke(handle(), cx, cy);
    }
    private static void mouse(int action) { var c = GLFW.glfwSetMouseButtonCallback(handle(), null); GLFW.glfwSetMouseButtonCallback(handle(), c); c.invoke(handle(), 0, action, 0); }
    private static void key(int key, int action, int mods) { var c = GLFW.glfwSetKeyCallback(handle(), null); GLFW.glfwSetKeyCallback(handle(), c); c.invoke(handle(), key, GLFW.glfwGetKeyScancode(key), action, mods); }
    private static void tap(int key, int mods) { key(key, GLFW.GLFW_PRESS, mods); key(key, GLFW.GLFW_RELEASE, mods); }
    private static void text(String value) { var c = GLFW.glfwSetCharModsCallback(handle(), null); GLFW.glfwSetCharModsCallback(handle(), c); value.codePoints().forEach(cp -> c.invoke(handle(), cp, 0)); }
    private static long handle() { return Minecraft.getInstance().getWindow().getWindow(); }
    private static void checkImBlockerFocus(boolean expected) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("imblocker")) return;
        try {
            Object owner = Class.forName("io.github.reserveword.imblocker.common.gui.FocusManager")
                    .getMethod("getFocusOwner").invoke(null);
            check((owner != null && "Ore Input IME focus".equals(owner.toString())) == expected,
                    "IMBlocker focus ownership follows Ore Input without silently falling back");
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("IMBlocker focus API unavailable", error);
        }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
