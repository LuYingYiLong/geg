package io.github.luyingyilong.ore.window;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.LifecycleScheduler;
import io.github.luyingyilong.ore.control.base.Offset;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.control.display.Label;
import io.github.luyingyilong.ore.control.overlay.Toast;
import io.github.luyingyilong.ore.control.overlay.toast.OreToasts;
import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.render.Transform2D;
import io.github.luyingyilong.ore.signal.DispatchGuard;
import io.github.luyingyilong.ore.theme.OreTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/** Real GL drawing and the global Screen adapter in the isolated smoke client. */
@Mod.EventBusSubscriber(modid = "geg", value = Dist.CLIENT)
public final class ToastDrawingClientTest {
    private static boolean ran;

    @SubscribeEvent public static void render(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (ran || !Boolean.getBoolean("geg.native.inspector.smoke") || mc.player == null) return;
        ran = true;
        try {
            verify(mc);
            System.out.println("ORE_TOAST_DRAWING_CLIENT_PASS");
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.out.println("ORE_TOAST_DRAWING_CLIENT_FAIL");
            mc.stop();
        }
    }

    private static void verify(Minecraft mc) {
        DebugBridge.Backend previous = DebugBridge.backend();
        Toast toast = new Toast(Component.literal("Toast")).setType(Toast.Type.INFORMATIVE).setDuration(0).open();
        Label label = (Label) toast.getChild(0);
        label.setThemeOverride(Label.FONT_COLOR, 0xFFFFFFFF);
        label.setThemeOverride(OreTheme.TEXT_SHADOW, false);
        toast.layout(new Rect(10, 10, 80, 26), mc.font);
        var window = new WindowContext(true);
        window.setDimensions(128, 80, 128, 80, 1);
        try (var state = new WindowRenderState(); var scope = window.enter()) {
            TextureTarget target = new TextureTarget(128, 80, true, Minecraft.ON_OSX);
            try {
                target.setClearColor(0, 0, 0, 0);
                target.clear(Minecraft.ON_OSX);
                target.bindWrite(true);
                RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, 128, 80, 0, -10000, 10000), VertexSorting.ORTHOGRAPHIC_Z);
                RenderSystem.disableDepthTest();
                RenderSystem.disableScissor();
                RenderSystem.setShaderColor(1, 1, 1, 1);
                GuiGraphics graphics = new WindowGraphics(mc, mc.renderBuffers().bufferSource(), window);
                try (var context = new DrawContext(graphics, mc.font, window, new Rect(0, 0, 128, 80), -1, -1, 0)) {
                    List<Control> painted = new ArrayList<>();
                    DebugBridge.install(new DebugBridge.Backend() {
                        @Override public void paint(Control node, DrawContext frame, int x, int y) {
                            check(DispatchGuard.active() && !LifecycleScheduler.flush(), "Paint remains inside the safe lifecycle phase");
                            painted.add(node);
                        }
                    });
                    toast.draw(context, 0, 0);
                    graphics.flush();
                    check(painted.equals(List.of(toast, label)), "Toast and its retained Label each use the standard pipeline once");
                    near(pixel(10, 10), 30, 30, 31, "Toast border");
                    near(pixel(11, 11), 140, 179, 255, "Toast face");
                    boolean textDrawn = false;
                    Rect text = label.getAbsoluteBounds();
                    for (int y = text.y(); y < text.bottom(); y++) for (int x = text.x(); x < text.right(); x++) {
                        int[] color = pixel(x, y);
                        textDrawn |= color[0] > 180 && color[1] > 200;
                    }
                    check(textDrawn, "The real Label renderer emits the toast text");

                    toast.setRenderTransform(new Transform2D(8, 4, 0, 1, .5, .5));
                    label.setOffset(new Offset(1, 1));
                    label.setRenderTransform(new Transform2D(3, 2, 0, 1, .5, .5));
                    Rect expected = label.getAbsoluteBounds().translated(11, 6);
                    DebugBridge.install(new DebugBridge.Backend() {
                        @Override public void paint(Control node, DrawContext frame, int x, int y) {
                            if (node == label) check(frame.toScreenRect(label.getScreenBounds(x, y)).equals(expected),
                                    "Label geometry includes parent transform, own transform and Offset once");
                        }
                    });
                    Matrix4f pose = new Matrix4f(graphics.pose().last().pose());
                    toast.draw(context, 0, 0);
                    check(pose.equals(graphics.pose().last().pose()), "Successful drawing restores transforms");
                    RuntimeException sentinel = new RuntimeException("expected toast drawing failure");
                    DebugBridge.install(new DebugBridge.Backend() {
                        @Override public void paint(Control node, DrawContext frame, int x, int y) {
                            if (node == label) throw sentinel;
                        }
                    });
                    try { toast.draw(context, 0, 0); throw new AssertionError("Expected draw failure"); }
                    catch (RuntimeException failure) { check(failure == sentinel, "Drawing preserves the original failure"); }
                    check(pose.equals(graphics.pose().last().pose()) && !DispatchGuard.active(), "Failed child drawing restores transforms and guards");
                    verifyGlobalAdapter(graphics, window);

                    painted.clear();
                    DebugBridge.install(new DebugBridge.Backend() {
                        @Override public void paint(Control node, DrawContext frame, int x, int y) { painted.add(node); }
                    });
                    toast.queueFree();
                    target.clear(Minecraft.ON_OSX);
                    target.bindWrite(true);
                    toast.draw(context, 0, 0);
                    label.draw(context, 0, 0);
                    graphics.flush();
                    check(painted.isEmpty() && pixel(20, 20)[3] == 0, "Pending toast and Label emit no geometry");
                }
            } finally { target.destroyBuffers(); }
        } finally {
            DebugBridge.install(previous);
            OreToasts.clear();
            toast.queueFree();
            LifecycleScheduler.flush();
        }
        check(toast.isDisposed() && label.isDisposed(), "Both controls are released after rendering");
    }

    private static void verifyGlobalAdapter(GuiGraphics graphics, WindowContext outer) {
        OreToasts.clear();
        Toast toast = OreToasts.show("draw-test", new Toast(Component.literal("Global message")).setDuration(0));
        Control label = toast.getChild(0);
        List<Control> paints = new ArrayList<>();
        DrawContext[] borrowed = {null};
        DebugBridge.install(new DebugBridge.Backend() {
            @Override public void paint(Control node, DrawContext frame, int x, int y) {
                paints.add(node); borrowed[0] = frame;
                check(WindowContext.getCurrent() == frame.getWindowContext() && frame.getWindowContext() != outer,
                        "Global host enters its own drawing context");
            }
        });
        Screen screen = new Screen(Component.literal("Toast test")) { };
        Matrix4f pose = new Matrix4f(graphics.pose().last().pose());
        OreToasts.screen(new ScreenEvent.Render.Post(screen, graphics, 0, 0, 0));
        check(paints.equals(List.of(toast, label)), "Global adapter draws the same retained tree exactly once");
        check(WindowContext.getCurrent() == outer && pose.equals(graphics.pose().last().pose()), "Global adapter restores host context and pose");
        closed(borrowed[0]);
        RuntimeException sentinel = new RuntimeException("expected global toast failure");
        DebugBridge.install(new DebugBridge.Backend() {
            @Override public void paint(Control node, DrawContext frame, int x, int y) {
                borrowed[0] = frame;
                if (node == label) throw sentinel;
            }
        });
        try { OreToasts.screen(new ScreenEvent.Render.Post(screen, graphics, 0, 0, 0)); throw new AssertionError("Expected adapter failure"); }
        catch (RuntimeException failure) { check(failure == sentinel, "Global adapter preserves failures"); }
        check(WindowContext.getCurrent() == outer && pose.equals(graphics.pose().last().pose()) && !DispatchGuard.active(),
                "Failed adapter releases its context, transforms and lifecycle phase");
        closed(borrowed[0]);
        OreToasts.clear();
    }

    private static void closed(DrawContext context) {
        check(context != null, "Global adapter provided a DrawContext");
        try { context.getFont(); throw new AssertionError("Global DrawContext escaped its frame"); }
        catch (IllegalStateException expected) { }
    }

    private static int[] pixel(int x, int y) {
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            var bytes = stack.malloc(4);
            GL11.glReadPixels(x, 79 - y, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, bytes);
            return new int[]{bytes.get(0) & 255, bytes.get(1) & 255, bytes.get(2) & 255, bytes.get(3) & 255};
        }
    }

    private static void near(int[] color, int r, int g, int b, String message) {
        check(Math.abs(color[0] - r) <= 2 && Math.abs(color[1] - g) <= 2 && Math.abs(color[2] - b) <= 2, message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
