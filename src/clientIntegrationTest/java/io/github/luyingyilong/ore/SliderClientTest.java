package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.event.EventGeometry;
import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.render.Canvas;
import io.github.luyingyilong.ore.render.CanvasTransform;
import io.github.luyingyilong.ore.window.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.util.Map;

/** Isolated GLFW smoke across embedded, native and scaled external-Screen hosts. */
@Mod.EventBusSubscriber(modid = "geg", value = Dist.CLIENT)
public final class SliderClientTest {
    private static Window root;
    private static Slider scalar, interval;
    private static int mode, stage = -1, ticks, changed, rangeCompleted, scalarCompleted;
    private static boolean done;
    private static Canvas canvas;

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("ore.slider.smoke") || event.phase != TickEvent.Phase.END || done) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        try {
            if (++ticks > 1200) throw new AssertionError("Slider timeout mode=" + mode + " stage=" + stage);
            if (ticks < changed + 6) return;
            if (stage == -1) {
                rangeCompleted = scalarCompleted = 0;
                Control content = content();
                if (mode == 2) mc.setScreen(new CanvasScreen(content));
                else {
                    root = (mode == 0 ? Window.embedded() : Window.nativeWindow(new WindowOptions("Ore Slider smoke", 640, 400, 320, 200, 2)))
                            .setContent(content).open();
                    root.requestFocus();
                }
                stage = 0; changed = ticks; return;
            }
            if (root == null || !interval.isReady()) return;
            System.out.println("ORE_SLIDER_STEP mode=" + mode + " stage=" + stage);
            switch (stage++) {
                case 0 -> {
                    var diagnostics = io.github.luyingyilong.ore.theme.ThemeDB.getDefault().getDiagnostics();
                    check(diagnostics.stream().noneMatch(item -> item.property().startsWith("Slider:")), "Slider theme resources loaded");
                    move(interval, 0.25); mouse(GLFW.GLFW_PRESS);
                }
                case 1 -> {
                    System.out.println("ORE_SLIDER_CAPTURE " + interval.getThemeState() + " captured=" + root.getPointerCaptured(0)
                            + " origin=" + interval.getAbsoluteBounds() + " window=" + root.getContext().getGuiWidth());
                    move(interval, 0.9);
                }
                case 2 -> {
                    check(Math.abs(interval.getRangeValue().upper() - 90) <= 1 && interval.getRangeValue().lower() == 75,
                            "GLFW crossing keeps physical thumb: " + interval.getThemeState() + " capture=" + root.getPointerCaptured(0));
                    mouse(GLFW.GLFW_RELEASE);
                }
                case 3 -> {
                    check(rangeCompleted == 1, "GLFW release completes once");
                    key(GLFW.GLFW_KEY_HOME, GLFW.GLFW_PRESS); key(GLFW.GLFW_KEY_HOME, GLFW.GLFW_RELEASE);
                }
                case 4 -> {
                    check(interval.getRangeValue().equals(new Slider.RangeValue(0, 75)) && rangeCompleted == 2, "native keyboard uses active thumb");
                    key(GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_PRESS); key(GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_REPEAT);
                    key(GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_RELEASE);
                }
                case 5 -> {
                    check(interval.getRangeValue().equals(new Slider.RangeValue(2, 75)) && rangeCompleted == 3, "repeat group completes once");
                    move(scalar, 0.66); mouse(GLFW.GLFW_PRESS); mouse(GLFW.GLFW_RELEASE);
                }
                case 6 -> {
                    check(Math.abs(scalar.getValue() - 66) <= 1 && scalarCompleted == 1, "track click edits through host input only once");
                    check(interval.getFocusBounds().width() == 14 && interval.getFocusBounds().height() == 15, "host scaling does not stretch GUI thumb");
                    var modal = new Modal(Component.literal("Slider input scope")).setSize(140, 70).setContent(new Label(Component.literal("Focus isolation")));
                    modal.closeRequested().connect(modal, modal::queueFree);
                    root.getPopupOverlay().addChild(modal);
                }
                case 7 -> {
                    key(GLFW.GLFW_KEY_HOME, GLFW.GLFW_PRESS); key(GLFW.GLFW_KEY_HOME, GLFW.GLFW_RELEASE);
                    key(GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_PRESS); key(GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_RELEASE);
                }
                case 8 -> {
                    check(Math.abs(scalar.getValue() - 66) <= 1 && scalarCompleted == 1, "higher Modal blocks slider keyboard");
                    if (mode != 1) {
                        var path = mc.gameDirectory.toPath().resolve("screenshots/ore-slider-" + mode + ".png");
                        Files.createDirectories(path.getParent());
                        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(path); }
                    }
                    System.out.println("ORE_SLIDER_HOST_PASS " + mode);
                    if (mode == 2) {
                        canvas.close(); mc.setScreen(null);
                        done = true; System.out.println("ORE_SLIDER_CLIENT_PASS"); mc.stop();
                    } else {
                        root.close(); root.queueFree(); root = null;
                        mode++; stage = -1;
                        GLFW.glfwFocusWindow(mc.getWindow().getWindow());
                    }
                }
                default -> throw new AssertionError("Unexpected stage");
            }
            changed = ticks;
        } catch (Throwable error) {
            error.printStackTrace(); System.out.println("ORE_SLIDER_CLIENT_FAIL mode=" + mode + " stage=" + stage);
            done = true; mc.stop();
        }
    }

    private static Control content() {
        Box box = new Box().setGap(8);
        box.addChild(new Label(Component.literal("Slider / 滑动输入条")));
        scalar = new Slider().setValueNoSignal(35).setShowTicks(true).setStepNoSignal(1);
        interval = new Slider().setRangeNoSignal(true).setRangeValueNoSignal(new Slider.RangeValue(25, 75))
                .setMarks(Map.of(0.0, Component.literal("0"), 50.0, Component.literal("50"), 100.0, Component.literal("100")));
        scalar.changeCompleted().connect(scalar, ignored -> scalarCompleted++);
        interval.changeCompleted().connect(interval, ignored -> rangeCompleted++);
        box.addChild(scalar); box.addChild(interval);
        box.addChild(new Slider().setValueNoSignal(60).setDisabled(true));
        return new Panel().setChild(new Padding(Padding.Insets.all(8)).setChild(box));
    }

    private static final class CanvasScreen extends Screen {
        CanvasScreen(Control content) { super(Component.literal("Slider Canvas")); canvas = new Canvas(content); }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void render(GuiGraphics graphics, int x, int y, float partial) {
            var host = ScreenWindowHost.getOrCreate(this); canvas.attach(host); root = host.getWindow();
            canvas.setViewport(new Rect(0, 0, 240, 150)).setTransform(new CanvasTransform(12, 18, 0.75));
            canvas.render(graphics, x, y, partial);
        }
    }
    private static long handle() { return root.getContext().getHandle(); }
    private static void move(Slider slider, double fraction) {
        Rect rail = slider.getRailBounds(), art = slider.getArtworkBounds();
        var point = EventGeometry.matrix(slider).transform(rail.x() + fraction * rail.width(), art.y() + art.height() / 2.0);
        var mc = Minecraft.getInstance();
        double x = root.getContext().getNativeWindow() ? root.getContext().clientX(point.x())
                : point.x() * mc.getWindow().getScreenWidth() / mc.screen.width;
        double y = root.getContext().getNativeWindow() ? root.getContext().clientY(point.y())
                : point.y() * mc.getWindow().getScreenHeight() / mc.screen.height;
        var callback = GLFW.glfwSetCursorPosCallback(handle(), null); GLFW.glfwSetCursorPosCallback(handle(), callback);
        GLFW.glfwSetCursorPos(handle(), x, y); callback.invoke(handle(), x, y);
    }
    private static void mouse(int action) {
        var callback = GLFW.glfwSetMouseButtonCallback(handle(), null); GLFW.glfwSetMouseButtonCallback(handle(), callback);
        callback.invoke(handle(), 0, action, 0);
    }
    private static void key(int key, int action) {
        var callback = GLFW.glfwSetKeyCallback(handle(), null); GLFW.glfwSetKeyCallback(handle(), callback);
        callback.invoke(handle(), key, GLFW.glfwGetKeyScancode(key), action, 0);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
