package io.github.luyingyilong.ore.window;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import io.github.luyingyilong.ore.TextureProgressBar;
import io.github.luyingyilong.ore.gallery.*;
import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.render.*;
import io.github.luyingyilong.ore.theme.IconTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;
import java.nio.file.Files;
import java.util.*;

/** Opt-in real GPU/readback and all three hosts, using only the isolated smoke world. */
@Mod.EventBusSubscriber(modid = "geg", value = Dist.CLIENT)
public final class TextureProgressBarClientTest {
    private static boolean ran, gpuPassed, failed;
    private static int phase, ticks, at, widthIndex;
    private static Window nativeWindow;
    private static Control sample;
    private static TextureScreen external;
    private static TextureProgressBar arrow() {
        return new TextureProgressBar().setUnderTexture(TextureSource.themed(IconTheme.BURN_PROGRESS_UNDER))
                .setProgressTexture(TextureSource.themed(IconTheme.BURN_PROGRESS)).setValueNoSignal(65);
    }
    @SubscribeEvent public static void render(RenderGuiEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (ran || !Boolean.getBoolean("ore.texture.progress.smoke") || mc.player == null) return;
        ran = true;
        try { verifyGpu(mc); gpuPassed = true; System.out.println("ORE_TEXTURE_PROGRESS_GPU_PASS"); }
        catch (Throwable error) { fail(error); }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("ore.texture.progress.smoke") || event.phase != TickEvent.Phase.END || failed || !gpuPassed || phase == 99) return;
        var mc = Minecraft.getInstance();
        try {
            if (++ticks > 500) throw new AssertionError("Texture progress timeout phase " + phase);
            if (phase == 0) { OreGallery.open(); phase = 1; at = ticks; }
            else if (phase == 1 && ticks > at + 5 && OreGallery.getView() != null) {
                OreGallery.getView().select("texture_progress"); sample = OreGallery.getView().getSample(); phase = 2; at = ticks;
            } else if (phase == 2 && ticks > at + 4) {
                check(sample == OreGallery.getView().getSample() && count(sample) == 19, "Gallery retains every example");
                if (widthIndex < 4) { OreGallery.getView().setPreviewWidth(new int[]{127, 128, 129, 0}[widthIndex++]); at = ticks; }
                else {
                    capture("texture-progress-gallery.png");
                    Box body = new Box().setGap(8); body.addChild(arrow());
                    body.addChild(arrow().setFillMode(TextureProgressBar.FillMode.CLOCKWISE).setRadialInitialAngle(330).setOpacity(.5F));
                    nativeWindow = Window.nativeWindow(new WindowOptions("Ore texture progress", 480, 320, 240, 160, 2)).setContent(body);
                    nativeWindow.open(); phase = 3; at = ticks;
                }
            } else if (phase == 3 && ((NativeWindowHost) nativeWindow.getHost()).framesRendered() >= 3) {
                var path = mc.gameDirectory.toPath().resolve("screenshots/texture-progress-native.png"); Files.createDirectories(path.getParent());
                try (var image = net.minecraft.client.Screenshot.takeScreenshot(((NativeWindowHost) nativeWindow.getHost()).renderTarget())) { image.writeToFile(path); }
                nativeWindow.close(); phase = 4; at = ticks;
            } else if (phase == 4 && ticks > at + 4 && WindowManager.nativeWindows() == 0) {
                OreGallery.stop(); external = new TextureScreen(); mc.setScreen(external); phase = 5; at = ticks;
            } else if (phase == 5 && external.frames > 3) {
                check(external.canvas.getHost() == ScreenWindowHost.get(external), "external Canvas uses its unique Screen host");
                capture("texture-progress-canvas.png"); mc.setScreen(null); phase = 6; at = ticks;
            } else if (phase == 6 && ticks > at + 3) {
                check(sample.isDisposed() && nativeWindow.isDisposed() && external.content.isDisposed(), "all host trees clean up");
                phase = 99; System.out.println("ORE_TEXTURE_PROGRESS_CLIENT_PASS"); mc.stop();
            }
        } catch (Throwable error) { fail(error); }
    }
    private static int count(Control c) { return (c instanceof TextureProgressBar ? 1 : 0) + c.getChildren().stream().mapToInt(TextureProgressBarClientTest::count).sum(); }
    private static void capture(String name) {
        var mc = Minecraft.getInstance();
        net.minecraft.client.Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> System.out.println(message.getString()));
    }
    private static final class TextureScreen extends Screen {
        Canvas canvas; Control content; int frames;
        TextureScreen() { super(Component.literal("Texture progress external Canvas")); }
        @Override protected void init() {
            Box box = new Box().setGap(12); box.addChild(arrow().setProgressOffset(new Offset(3, -2)));
            var radial = arrow().setFillMode(TextureProgressBar.FillMode.BIDIRECTIONAL_RADIAL);
            radial.setRenderTransform(Transform2D.rotation(45)); box.addChild(radial); content = box;
            canvas = new Canvas(box).setViewport(new Rect(0, 0, 160, 90)).setTransform(new CanvasTransform(20, 30, 1.25));
            canvas.attach(ScreenWindowHost.getOrCreate(this));
        }
        @Override public void render(GuiGraphics graphics, int x, int y, float partial) { canvas.render(graphics, x, y, partial); frames++; }
        @Override public void removed() { canvas.close(); canvas.getHost().close(); }
        @Override public boolean isPauseScreen() { return false; }
    }
    private static void verifyGpu(Minecraft mc) {
        List<ResourceLocation> textures = new ArrayList<>();
        List<TextureProgressBar> bars = new ArrayList<>();
        try (var outer = new WindowRenderState(false)) {
            NativeImage image = new NativeImage(4, 4, false);
            int[] alphas = {4, 64, 128, 0};
            for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) image.setPixelRGBA(x, y, (alphas[x] << 24) | 0xFFFFFF);
            var white = register(mc, image, textures);
            var red = solid(mc, 0x800000FF, textures);
            var green = solid(mc, 0x8000FF00, textures);
            var blue = solid(mc, 0x80FF0000, textures);
            var bar = new TextureProgressBar().setProgressTexture(white).setValueNoSignal(100).setStretchMode(TextureProgressBar.StretchMode.SCALE);
            bars.add(bar); bar.layout(new Rect(0, 0, 8, 8), mc.font);
            try (var state = new WindowRenderState()) {
                TextureTarget target = new TextureTarget(16, 16, true, Minecraft.ON_OSX);
                try {
                    target.setClearColor(0, 0, 0, 0); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
                    RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, 16, 16, 0, -1, 1), VertexSorting.ORTHOGRAPHIC_Z);
                    RenderSystem.disableDepthTest(); RenderSystem.enableCull(); RenderSystem.setShaderColor(1, 1, 1, 1);
                    RenderSystem.enableScissor(0, 0, 16, 16); RenderSystem.disableBlend();
                    var graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
                    try (var context = new DrawContext(graphics, mc.font, new WindowContext(false), new Rect(0, 0, 16, 16), 0, 0, 0)) {
                        graphics.flush();
                        var shader = RenderSystem.getShader(); var color = RenderSystem.getShaderColor().clone();
                        int equation = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB), equationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
                        GL20.glBlendEquationSeparate(GL14.GL_FUNC_REVERSE_SUBTRACT, GL14.GL_FUNC_SUBTRACT);
                        bar.draw(context, 0, 0);
                        check(RenderSystem.getShader() == shader && Arrays.equals(color, RenderSystem.getShaderColor()) && !GL11.glIsEnabled(GL11.GL_BLEND)
                                && GL11.glIsEnabled(GL11.GL_CULL_FACE) && GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)
                                && GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB) == GL14.GL_FUNC_REVERSE_SUBTRACT
                                && GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA) == GL14.GL_FUNC_SUBTRACT, "mesh drawing restores render state");
                        GL20.glBlendEquationSeparate(equation, equationAlpha);
                        near(pixel(0, 1), new int[]{4, 4, 4, 4}, "low coverage is not thresholded");
                        near(pixel(2, 1), new int[]{64, 64, 64, 64}, "default semi-transparent pixels");
                        near(pixel(6, 1), new int[]{0, 0, 0, 0}, "transparent holes");
                        target.clear(Minecraft.ON_OSX); target.bindWrite(true);
                        bar.setOpacity(.5F).setThemeOverride(TextureProgressBar.PROGRESS_TINT, 0x80FFFFFF);
                        RenderSystem.setShaderColor(1, 1, 1, .5F); bar.draw(context, 0, 0);
                        near(pixel(4, 1), new int[]{16, 16, 16, 16}, "pixel, tint, control and context alpha multiply once");
                        RenderSystem.setShaderColor(1, 1, 1, 1);
                        target.clear(Minecraft.ON_OSX); target.bindWrite(true);
                        bar.setOpacity(1).setThemeOverride(TextureProgressBar.PROGRESS_TINT, 0xFFFFFFFF);
                        bar.setUnderTexture(red).setProgressTexture(green).setOverTexture(blue); bar.draw(context, 0, 0);
                        near(pixel(1, 1), new int[]{32, 64, 128, 224}, "three layers accumulate source-over alpha");
                        target.clear(Minecraft.ON_OSX); target.bindWrite(true);
                        bar.setUnderTexture(null).setOverTexture(null).setValueNoSignal(25); bar.draw(context, 0, 0);
                        near(pixel(0, 1), new int[]{0, 128, 0, 128}, "one-pixel source stretches to a partial fill");
                        near(pixel(3, 1), new int[]{0, 0, 0, 0}, "partial fill does not squeeze the whole source");
                        target.clear(Minecraft.ON_OSX); target.bindWrite(true);
                        bar.setUnderTexture(null).setOverTexture(null).setProgressTexture(green).setFillMode(TextureProgressBar.FillMode.CLOCKWISE).setValueNoSignal(75);
                        bar.draw(context, 0, 0);
                        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                            int alpha = pixel(x, y)[3];
                            check(alpha == 0 || Math.abs(alpha - 128) <= 1, "radial triangles have no double-alpha seams");
                        }
                    }
                } finally { target.destroyBuffers(); }
            }
        } finally { bars.forEach(Control::queueFree); textures.forEach(mc.getTextureManager()::release); }
    }
    private static TextureSource solid(Minecraft mc, int color, List<ResourceLocation> ids) {
        var image = new NativeImage(1, 1, false); image.setPixelRGBA(0, 0, color); return register(mc, image, ids);
    }
    private static TextureSource register(Minecraft mc, NativeImage image, List<ResourceLocation> ids) {
        var id = ResourceLocation.fromNamespaceAndPath("geg", "test/texture_progress_" + ids.size());
        mc.getTextureManager().register(id, new DynamicTexture(image)); ids.add(id);
        return TextureSource.region(TextureRegion.full(id, image.getWidth(), image.getHeight()));
    }
    private static int[] pixel(int x, int y) {
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            var bytes = stack.malloc(4); GL11.glReadPixels(x, 15 - y, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, bytes);
            return new int[]{bytes.get(0) & 255, bytes.get(1) & 255, bytes.get(2) & 255, bytes.get(3) & 255};
        }
    }
    private static void near(int[] actual, int[] expected, String message) {
        for (int i = 0; i < 4; i++) check(Math.abs(actual[i] - expected[i]) <= 2, message + ": " + Arrays.toString(actual));
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void fail(Throwable error) { failed = true; error.printStackTrace(); System.out.println("ORE_TEXTURE_PROGRESS_CLIENT_FAIL"); Minecraft.getInstance().stop(); }
}
