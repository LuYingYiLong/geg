package io.github.luyingyilong.ore.window;

import io.github.luyingyilong.ore.*;
import io.github.luyingyilong.ore.input.*;
import io.github.luyingyilong.ore.layout.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import java.nio.file.*;

/** Explicit opt-in, isolated-world integration test. Never included in ordinary runs or JARs. */
@Mod.EventBusSubscriber(modid = "geg", value = Dist.CLIENT)
public final class NativeWindowClientTest {
    private static int phase, ticks;
    private static Window first, second;
    private static Input input;
    private static TextArea text;
    private static int secondFrames, wait, beforeResize;
    private static boolean pause;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("geg.native.smoke") || event.phase != TickEvent.Phase.END || phase == 99) return;
        Minecraft mc = Minecraft.getInstance(); if (mc.player == null) return;
        try {
            if (++ticks > 2000) throw new AssertionError("Native test timeout phase " + phase);
            if (phase == 0) {
                input = new Input().placeholder(Component.literal("中文输入 / Input"));
                pause = mc.options.pauseOnLostFocus;
                text = new TextArea(0); text.placeholder(Component.literal("Multi-line / 自动换行"));
                Box body = new Box().gap(4).add(new Label(Component.literal("Native Ore / 独立窗口")).hero(true));
                body.add(new InputControl(input)); body.add(new InputControl(text));
                body.add(new Button(Component.literal("Button / 按钮")));
                Box rows = new Box().gap(2); for (int i = 0; i < 150; i++) rows.add(new Label(Component.literal("Scroll / 内容 " + i)));
                Scroll scroll = new Scroll().content(rows); scroll.sizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL); body.add(scroll);
                first = Window.nativeWindow(WindowOptions.defaults("Ore Native Test A")).content(body); first.open();
                second = Window.nativeWindow(new WindowOptions("Ore Native Test B", 600, 400, 300, 200, 2))
                        .content(new PixelProbe()); second.open(); phase = 1;
            } else if (phase == 1 && ((NativeWindowHost) first.host()).framesRendered() >= 5 && ((NativeWindowHost) second.host()).framesRendered() >= 3) {
                check(WindowManager.nativeWindows() == 2, "Two hosts"); first.requestFocus();
                checkPixels((NativeWindowHost) second.host());
                NativeWindowHost host = (NativeWindowHost) first.host();
                first.focusManager().requestFocus(WindowCanvas.inputs(first).get(0), first.context().mui());
                var callback = GLFW.glfwSetCharCallback(host.nativeHandle(), null);
                callback.invoke(host.nativeHandle(), '中'); callback.invoke(host.nativeHandle(), '文'); GLFW.glfwSetCharCallback(host.nativeHandle(), callback);
                phase = 2;
            } else if (phase == 2 && input.getText().equals("中文")) {
                var path = mc.gameDirectory.toPath().resolve("screenshots/native-ore.png"); Files.createDirectories(path.getParent());
                try (var image = Screenshot.takeScreenshot(((NativeWindowHost) first.host()).renderTarget())) { image.writeToFile(path); }
                var projection = new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix());
                int framebuffer = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING);
                try (var state = new WindowRenderState()) {
                    ((NativeWindowHost) first.host()).renderTarget().bindWrite(true);
                    com.mojang.blaze3d.systems.RenderSystem.enableScissor(3,4,5,6);
                    com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(new org.joml.Matrix4f(), com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);
                    throw new IllegalArgumentException("expected test exception");
                } catch (IllegalArgumentException expected) { }
                check(projection.equals(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix())
                        && framebuffer == org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING), "Restore rendering after exception");
                first.focusManager().requestFocus(WindowCanvas.inputs(first).get(1), first.context().mui());
                var chars = GLFW.glfwSetCharCallback(((NativeWindowHost) first.host()).nativeHandle(), null);
                chars.invoke(((NativeWindowHost) first.host()).nativeHandle(), 'A'); GLFW.glfwSetCharCallback(((NativeWindowHost) first.host()).nativeHandle(), chars);
                var keys = GLFW.glfwSetKeyCallback(((NativeWindowHost) first.host()).nativeHandle(), null);
                keys.invoke(((NativeWindowHost) first.host()).nativeHandle(), GLFW.GLFW_KEY_ENTER,0,GLFW.GLFW_PRESS,0);
                keys.invoke(((NativeWindowHost) first.host()).nativeHandle(), GLFW.GLFW_KEY_ENTER,0,GLFW.GLFW_RELEASE,0);
                GLFW.glfwSetKeyCallback(((NativeWindowHost) first.host()).nativeHandle(), keys);
                beforeResize=first.context().framebufferWidth();
                GLFW.glfwSetWindowSize(((NativeWindowHost) first.host()).nativeHandle(),901,601);
                secondFrames=((NativeWindowHost) second.host()).framesRendered(); GLFW.glfwIconifyWindow(((NativeWindowHost) second.host()).nativeHandle()); wait=ticks; phase=3;
            } else if (phase == 3 && ticks > wait + 8) {
                check(text.getText().equals("A\n"), "Multiline Enter stays in native field");
                check(((NativeWindowHost) second.host()).framesRendered() <= secondFrames + 1, "Minimized window skips drawing");
                check(first.context().framebufferWidth() != beforeResize, "Resize updates render target");
                GLFW.glfwRestoreWindow(((NativeWindowHost) second.host()).nativeHandle());
                GLFW.glfwSetWindowSize(((NativeWindowHost) second.host()).nativeHandle(),607,407);
                wait=ticks; phase=7;
            } else if (phase == 7 && ticks > wait + 8) {
                checkPixels((NativeWindowHost) second.host());
                second.close(); phase=8;
            } else if (phase == 8 && WindowManager.nativeWindows() == 1) {
                check(first.host().isOpen(), "Closing second leaves first alive"); first.close(); phase = 4;
            } else if (phase == 4 && WindowManager.nativeWindows() == 0) {
                check(mc.options.pauseOnLostFocus == pause, "Restore pause setting");
                System.out.println("GEG_NATIVE_WINDOW_CLIENT_PASS"); phase = 99; mc.stop();
            }
        } catch (Throwable error) { error.printStackTrace(); System.out.println("GEG_NATIVE_WINDOW_CLIENT_FAIL"); phase = 99; mc.stop(); }
    }
    public static void capture(Window window, java.nio.file.Path path) throws Exception {
        Files.createDirectories(path.getParent());
        try (var image = Screenshot.takeScreenshot(((NativeWindowHost)window.host()).renderTarget())) { image.writeToFile(path); }
    }
    private static final class PixelProbe extends Control {
        @Override protected void render(brachy.modularui.screen.viewport.ModularGuiContext context, int x, int y) {
            var area = screenBounds(x, y);
            for (int column=0; column<16; column++)
                context.getGraphics().fill(area.x()+7+column,area.y()+9,area.x()+8+column,area.y()+17,
                        column%2==0 ? 0xFFFFFFFF : 0xFF000000);
        }
    }
    private static void checkPixels(NativeWindowHost host) {
        int scale=(int)host.context().guiScale();
        check(scale==host.context().guiScale(), "Native UI scale is integral");
        try (var image=Screenshot.takeScreenshot(host.renderTarget())) {
            for(int x=0;x<16*scale;x++) for(int y=0;y<8*scale;y++) {
                int expected=x/scale%2==0 ? 0xFFFFFF : 0;
                check((image.getPixelRGBA(7*scale+x,9*scale+y)&0xFFFFFF)==expected,
                        "Uniform physical pixel blocks before presentation at "+x+","+y);
            }
        }
        long previous=GLFW.glfwGetCurrentContext();
        var capabilities=org.lwjgl.opengl.GL.getCapabilities();
        try {
            GLFW.glfwMakeContextCurrent(host.nativeHandle());
            org.lwjgl.opengl.GL.createCapabilities();
            try(var presentation=new NativePresentation()) {
                presentation.draw(host.renderTarget().getColorTextureId(),host.context().framebufferWidth(),host.context().framebufferHeight());
                var pixels=org.lwjgl.BufferUtils.createByteBuffer(16*scale*4);
                org.lwjgl.opengl.GL11.glReadPixels(7*scale,host.context().framebufferHeight()-9*scale-1,16*scale,1,
                        org.lwjgl.opengl.GL11.GL_RGBA,org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE,pixels);
                for(int x=0;x<16*scale;x++) for(int channel=0;channel<3;channel++)
                    check((pixels.get(x*4+channel)&255)==(x/scale%2==0 ? 255 : 0), "Presentation preserves exact source texels");
            }
        } finally {
            GLFW.glfwMakeContextCurrent(previous);
            org.lwjgl.opengl.GL.setCapabilities(capabilities);
        }
        System.out.println("GEG_NATIVE_PIXEL_GRID_PASS "+host.context().framebufferWidth()+"x"+host.context().framebufferHeight()+" scale="+scale);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
