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
                input = new Input().setPlaceholder(Component.literal("中文输入 / Input"));
                pause = mc.options.pauseOnLostFocus;
                text = new TextArea(0); text.setPlaceholder(Component.literal("Multi-line / 自动换行"));
                Box body = new Box().setGap(4);
                body.addChild(new Label(Component.literal("Native Ore / 独立窗口")).setHero(true));
                body.addChild(new InputControl(input)); body.addChild(new InputControl(text));
                body.addChild(new Button(Component.literal("Button / 按钮")));
                Box rows = new Box().setGap(2); for (int i = 0; i < 150; i++) { rows.addChild(new Label(Component.literal("Scroll / 内容 " + i))); }
                Scroll scroll = new Scroll().setContent(rows); scroll.setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL); body.addChild(scroll);
                first = Window.nativeWindow(WindowOptions.defaults("Ore Native Test A")).setContent(body); first.open();
                second = Window.nativeWindow(new WindowOptions("Ore Native Test B", 600, 400, 300, 200, 2))
                        .setContent(new PixelProbe()); second.open(); phase = 1;
            } else if (phase == 1 && ((NativeWindowHost) first.getHost()).framesRendered() >= 5 && ((NativeWindowHost) second.getHost()).framesRendered() >= 3) {
                check(WindowManager.nativeWindows() == 2, "Two hosts"); first.requestFocus();
                checkPixels((NativeWindowHost) second.getHost());
                NativeWindowHost host = (NativeWindowHost) first.getHost();
                var fieldBounds=WindowCanvas.inputs(first).get(0).getDisplayedBounds();
                var cursor=GLFW.glfwSetCursorPosCallback(host.nativeHandle(),null);
                cursor.invoke(host.nativeHandle(),first.getContext().clientX(fieldBounds.x()+5),first.getContext().clientY(fieldBounds.y()+7));
                GLFW.glfwSetCursorPosCallback(host.nativeHandle(),cursor);
                var mouse=GLFW.glfwSetMouseButtonCallback(host.nativeHandle(),null);
                mouse.invoke(host.nativeHandle(),0,GLFW.GLFW_PRESS,0);
                mouse.invoke(host.nativeHandle(),0,GLFW.GLFW_RELEASE,0);
                GLFW.glfwSetMouseButtonCallback(host.nativeHandle(),mouse);
                var callback = GLFW.glfwSetCharCallback(host.nativeHandle(), null);
                callback.invoke(host.nativeHandle(), '中'); callback.invoke(host.nativeHandle(), '文'); GLFW.glfwSetCharCallback(host.nativeHandle(), callback);
                phase = 2;
            } else if (phase == 2 && input.getText().equals("中文")) {
                var path = mc.gameDirectory.toPath().resolve("screenshots/native-ore.png"); Files.createDirectories(path.getParent());
                try (var image = Screenshot.takeScreenshot(((NativeWindowHost) first.getHost()).renderTarget())) { image.writeToFile(path); }
                var projection = new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix());
                int framebuffer = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING);
                try (var state = new WindowRenderState()) {
                    ((NativeWindowHost) first.getHost()).renderTarget().bindWrite(true);
                    com.mojang.blaze3d.systems.RenderSystem.enableScissor(3,4,5,6);
                    com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(new org.joml.Matrix4f(), com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);
                    throw new IllegalArgumentException("expected test exception");
                } catch (IllegalArgumentException expected) { }
                check(projection.equals(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix())
                        && framebuffer == org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING), "Restore rendering after exception");
                first.getFocusManager().requestFocus(WindowCanvas.inputs(first).get(1), first.getContext().getMui());
                var chars = GLFW.glfwSetCharCallback(((NativeWindowHost) first.getHost()).nativeHandle(), null);
                chars.invoke(((NativeWindowHost) first.getHost()).nativeHandle(), 'A'); GLFW.glfwSetCharCallback(((NativeWindowHost) first.getHost()).nativeHandle(), chars);
                var keys = GLFW.glfwSetKeyCallback(((NativeWindowHost) first.getHost()).nativeHandle(), null);
                keys.invoke(((NativeWindowHost) first.getHost()).nativeHandle(), GLFW.GLFW_KEY_ENTER,0,GLFW.GLFW_PRESS,0);
                keys.invoke(((NativeWindowHost) first.getHost()).nativeHandle(), GLFW.GLFW_KEY_ENTER,0,GLFW.GLFW_RELEASE,0);
                GLFW.glfwSetKeyCallback(((NativeWindowHost) first.getHost()).nativeHandle(), keys);
                beforeResize=first.getContext().getFramebufferWidth();
                GLFW.glfwSetWindowSize(((NativeWindowHost) first.getHost()).nativeHandle(),901,601);
                secondFrames=((NativeWindowHost) second.getHost()).framesRendered(); GLFW.glfwIconifyWindow(((NativeWindowHost) second.getHost()).nativeHandle()); wait=ticks; phase=3;
            } else if (phase == 3 && ticks > wait + 8) {
                check(text.getText().equals("A\n"), "Multiline Enter stays in native field");
                check(((NativeWindowHost) second.getHost()).framesRendered() <= secondFrames + 1, "Minimized window skips drawing");
                check(first.getContext().getFramebufferWidth() != beforeResize, "Resize updates render target");
                GLFW.glfwRestoreWindow(((NativeWindowHost) second.getHost()).nativeHandle());
                GLFW.glfwSetWindowSize(((NativeWindowHost) second.getHost()).nativeHandle(),607,407);
                wait=ticks; phase=7;
            } else if (phase == 7 && ticks > wait + 8) {
                checkPixels((NativeWindowHost) second.getHost());
                second.close(); phase=8;
            } else if (phase == 8 && WindowManager.nativeWindows() == 1) {
                check(first.getHost().isOpen(), "Closing second leaves first alive"); first.close(); phase = 4;
            } else if (phase == 4 && WindowManager.nativeWindows() == 0) {
                check(mc.options.pauseOnLostFocus == pause, "Restore pause setting");
                System.out.println("GEG_NATIVE_WINDOW_CLIENT_PASS"); phase = 99; mc.stop();
            }
        } catch (Throwable error) { error.printStackTrace(); System.out.println("GEG_NATIVE_WINDOW_CLIENT_FAIL"); phase = 99; mc.stop(); }
    }
    public static int frames(Window window) { return ((NativeWindowHost) window.getHost()).framesRendered(); }

    public static void capture(Window window, java.nio.file.Path path) throws Exception {
        Files.createDirectories(path.getParent());
        try (var image = Screenshot.takeScreenshot(((NativeWindowHost)window.getHost()).renderTarget())) { image.writeToFile(path); }
    }
    private static final class PixelProbe extends Control {
        @Override protected void render(io.github.luyingyilong.ore.render.DrawContext context, int x, int y) {
            var area = getScreenBounds(x, y);
            for (int column=0; column<16; column++)
                context.getGraphics().fill(area.x()+7+column,area.y()+9,area.x()+8+column,area.y()+17,
                        column%2==0 ? 0xFFFFFFFF : 0xFF000000);
        }
    }
    private static void checkPixels(NativeWindowHost host) {
        int scale=(int)host.getContext().getGuiScale();
        check(scale==host.getContext().getGuiScale(), "Native UI scale is integral");
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
                presentation.draw(host.renderTarget().getColorTextureId(),host.getContext().getFramebufferWidth(),host.getContext().getFramebufferHeight());
                var pixels=org.lwjgl.BufferUtils.createByteBuffer(16*scale*4);
                org.lwjgl.opengl.GL11.glReadPixels(7*scale,host.getContext().getFramebufferHeight()-9*scale-1,16*scale,1,
                        org.lwjgl.opengl.GL11.GL_RGBA,org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE,pixels);
                for(int x=0;x<16*scale;x++) for(int channel=0;channel<3;channel++)
                    check((pixels.get(x*4+channel)&255)==(x/scale%2==0 ? 255 : 0), "Presentation preserves exact source texels");
            }
        } finally {
            GLFW.glfwMakeContextCurrent(previous);
            org.lwjgl.opengl.GL.setCapabilities(capabilities);
        }
        System.out.println("GEG_NATIVE_PIXEL_GRID_PASS "+host.getContext().getFramebufferWidth()+"x"+host.getContext().getFramebufferHeight()+" scale="+scale);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
