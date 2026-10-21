package io.github.luyingyilong.ore.text;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.window.WindowContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RegisterShadersEvent;
import org.lwjgl.opengl.*;
import java.util.*;

/** Own material: no vanilla text alpha discard, no changes to global Minecraft shaders. */
public final class TextRenderer {
    private static final int PAGE_SIZE = 1024;
    private static ShaderInstance shader;
    private static final List<Page> pages = new ArrayList<>();
    private static final Map<Key, Sprite> glyphs = new HashMap<>();
    private static boolean invalid = true;
    private static int serial;
    private static final FontRepository.BitmapGlyph WHITE;
    static {
        var pixel = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        pixel.setRGB(0, 0, -1);
        WHITE = new FontRepository.BitmapGlyph(pixel, 1, 0, 1);
    }
    private record Key(java.awt.Font font, int glyph, FontRepository.BitmapGlyph bitmap, float bitmapScale, float density) { }
    private record Sprite(Page page, int x, int y, int width, int height, GlyphRasterizer.Image image) { }
    private record Draw(int texture, boolean straightMask, float x, float y, float width, float height,
                        float u0, float v0, float u1, float v1, int color, float shearTop, float shearBottom) {
        Draw(int texture, boolean straightMask, float x, float y, float width, float height,
             float u0, float v0, float u1, float v1, int color) {
            this(texture, straightMask, x, y, width, height, u0, v0, u1, v1, color, 0, 0);
        }
    }
    private static final class Page {
        final NativeImage image = new NativeImage(PAGE_SIZE, PAGE_SIZE, true);
        final DynamicTexture texture = new DynamicTexture(image);
        final ResourceLocation id = ResourceLocation.parse("ore:text_atlas/" + serial++);
        final boolean smooth;
        int x = 2, y = 2, rowHeight;
        boolean dirty;
        Page(boolean smooth) {
            this.smooth = smooth;
            Minecraft.getInstance().getTextureManager().register(id, texture);
            texture.setFilter(smooth, false);
        }
        boolean fits(int width, int height) {
            return width + 4 <= PAGE_SIZE && height + 4 <= PAGE_SIZE
                    && (x + width + 2 <= PAGE_SIZE ? y + height + 2 : y + rowHeight + 4 + height + 2) <= PAGE_SIZE;
        }
        Sprite add(GlyphRasterizer.Image glyph) {
            var source = glyph.pixels(); int width = source.getWidth(), height = source.getHeight();
            if (x + width + 2 > PAGE_SIZE) { x = 2; y += rowHeight + 4; rowHeight = 0; }
            Sprite result = new Sprite(this, x, y, width, height, glyph);
            for (int yy = 0; yy < height; yy++) for (int xx = 0; xx < width; xx++) {
                // BufferedImage.getRGB returns straight ARGB, even for an ARGB_PRE raster.
                int color = TextAlpha.premultiply(source.getRGB(xx, yy));
                image.setPixelRGBA(x + xx, y + yy, color & 0xFF00FF00 | (color & 255) << 16 | (color >>> 16 & 255));
            }
            x += width + 4; rowHeight = Math.max(rowHeight, height); dirty = true;
            return result;
        }
    }
    private TextRenderer() { }
    public static void registerShaders(RegisterShadersEvent event) {
        try { event.registerShader(new ShaderInstance(event.getResourceProvider(), ResourceLocation.parse("ore:text"), DefaultVertexFormat.POSITION_TEX_COLOR), value -> shader = value); }
        catch (java.io.IOException error) { throw new IllegalStateException("Cannot load Ore text material", error); }
    }
    public static void endFrame() {
        if (invalid || pages.size() > 8 || glyphs.size() > 16384) clear();
    }
    public static void invalidate() { invalid = true; }
    /** Called at a client render safe point; no layout retains atlas entries. */
    public static void clear() {
        for (Page page : pages) Minecraft.getInstance().getTextureManager().release(page.id);
        pages.clear(); glyphs.clear(); invalid = false;
    }
    private static Sprite sprite(TextLayout.Glyph glyph, float density) {
        Key key = new Key(glyph.font(), glyph.glyphId(), glyph.bitmap(), glyph.bitmapScale(), glyph.bitmap() == null ? density : 1);
        if (glyphs.containsKey(key)) return glyphs.get(key);
        GlyphRasterizer.Image image = GlyphRasterizer.rasterize(glyph, density);
        if (image == null) { glyphs.put(key, null); return null; }
        float rasterDensity = density;
        while (image.pixels().getWidth() + 4 > PAGE_SIZE || image.pixels().getHeight() + 4 > PAGE_SIZE) {
            if (glyph.bitmap() != null || rasterDensity <= .03125F) return null;
            rasterDensity /= 2;
            image = GlyphRasterizer.rasterize(glyph, rasterDensity);
            if (image == null) return null;
        }
        Page target = null;
        for (Page page : pages) if (page.smooth == image.smooth() && page.fits(image.pixels().getWidth(), image.pixels().getHeight())) { target = page; break; }
        if (target == null) { target = new Page(image.smooth()); pages.add(target); }
        Sprite sprite = target.add(image); glyphs.put(key, sprite); return sprite;
    }
    public static void draw(DrawContext context, TextLayout layout, float x, float y, int color, boolean shadow) {
        draw(context.getGraphics(), layout, x, y, color, shadow);
    }
    public static void draw(GuiGraphics graphics, TextLayout layout, float x, float y, int color, boolean shadow) {
        if ((color >>> 24) == 0 || shader == null) return;
        graphics.flush();
        float[] previousColor = RenderSystem.getShaderColor().clone();
        var oldShader = RenderSystem.getShader(); int oldTexture = RenderSystem.getShaderTexture(0);
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0); int boundTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GlStateManager._activeTexture(active);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        int sr = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dr = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int sa = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), da = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        try {
            if (invalid || pages.size() > 8 || glyphs.size() > 16384) clear();
            var matrix = graphics.pose().last().pose();
            float poseScale = (float)Math.sqrt(matrix.m00() * matrix.m00() + matrix.m01() * matrix.m01());
            float density = Math.max(1, (float)Math.ceil(WindowContext.getCurrent().getGuiScale() * poseScale));
            List<Draw> draws = new ArrayList<>();
            for (var line : layout.getLines()) for (var glyph : line.glyphs()) layout.getTextBackend().getGlyphImage(glyph, density);
            for (int i = 0; i < layout.getLines().size(); i++) for (var glyph : layout.getLines().get(i).glyphs()) {
                int rgb = glyph.bitmap() != null && glyph.bitmap().colored() ? 0xFFFFFF
                        : glyph.style().getColor() == null ? color & 0xFFFFFF : glyph.style().getColor().getValue();
                float left = x + glyph.x(), baseline = y + i * layout.getMetrics().lineHeight() + layout.getMetrics().ascent() + glyph.baselineShift();
                var external = layout.getTextBackend().getGlyphImage(glyph, density);
                if (external != null) {
                    draws.add(new Draw(external.texture(), true, left + external.left(), baseline + external.top(),
                            external.width(), external.height(), external.u0(),external.v0(),external.u1(),external.v1(),color & 0xFF000000 | rgb));
                } else {
                    Sprite s = sprite(glyph, density); if (s == null) continue;
                    var image = s.image;
                    boolean bitmapStyle = glyph.bitmap() != null && !glyph.bitmap().colored();
                    float shearTop = bitmapStyle && glyph.style().isItalic() ? -.25F * image.top() : 0;
                    float shearBottom = bitmapStyle && glyph.style().isItalic() ? -.25F * (image.top() + image.height()) : 0;
                    for (int copy = 0; copy < (bitmapStyle && glyph.style().isBold() ? 2 : 1); copy++)
                        draws.add(new Draw(s.page.texture.getId(), false, left + image.left() + copy * glyph.bitmapScale(),
                                baseline + image.top(), image.width(),image.height(), s.x/(float)PAGE_SIZE,s.y/(float)PAGE_SIZE,
                                (s.x+s.width)/(float)PAGE_SIZE,(s.y+s.height)/(float)PAGE_SIZE,color & 0xFF000000 | rgb,shearTop,shearBottom));
                }
            }
            // Decorations use the same alpha material and source ranges as the glyphs.
            for (int row = 0; row < layout.getLines().size(); row++) for (var cell : layout.getLines().get(row).cells()) {
                if (!cell.style().isUnderlined() && !cell.style().isStrikethrough()) continue;
                Sprite white = sprite(new TextLayout.Glyph(null, null, 0, WHITE, 0, 0, 1, 0, net.minecraft.network.chat.Style.EMPTY), 1);
                int rgb = cell.style().getColor() == null ? color & 0xFFFFFF : cell.style().getColor().getValue();
                float baseline = y + row * layout.getMetrics().lineHeight() + layout.getMetrics().ascent();
                for (boolean underline : new boolean[]{true, false}) {
                    if (underline ? !cell.style().isUnderlined() : !cell.style().isStrikethrough()) continue;
                    float top = baseline + (underline ? 1 : layout.getMetrics().opticalTop() * .45F);
                    draws.add(new Draw(white.page.texture.getId(), false, x + cell.left(), top, cell.right()-cell.left(),1,
                            (white.x+.5F)/PAGE_SIZE,(white.y+.5F)/PAGE_SIZE,(white.x+.5F)/PAGE_SIZE,(white.y+.5F)/PAGE_SIZE,color & 0xFF000000 | rgb));
                }
            }
            for (Page page : pages) if (page.dirty) { page.texture.upload(); page.texture.setFilter(page.smooth, false); page.dirty = false; }
            RenderSystem.enableBlend(); RenderSystem.disableDepthTest();
            RenderSystem.blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            RenderSystem.setShader(() -> shader);
            if (shadow) batch(graphics, draws, true);
            batch(graphics, draws, false);
        } catch (TextBackend.Unavailable error) {
            // Do not paint a layout with a different backend. The next frame remeasures it.
            OreText.backendFailed(layout.getTextBackend(), error);
        } finally {
            RenderSystem.setShaderColor(previousColor[0], previousColor[1], previousColor[2], previousColor[3]);
            RenderSystem.setShaderTexture(0, oldTexture); RenderSystem.setShader(() -> oldShader);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0); GlStateManager._bindTexture(boundTexture);
            GlStateManager._activeTexture(active); GlStateManager._glUseProgram(program);
            RenderSystem.blendFuncSeparate(sr, dr, sa, da);
            if (!blend) RenderSystem.disableBlend(); if (depth) RenderSystem.enableDepthTest();
        }
    }
    private static void batch(GuiGraphics graphics, List<Draw> draws, boolean shadow) {
        var matrix = graphics.pose().last().pose();
        // Preserve glyph order; overlapping combining marks must not be reordered by atlas page.
        for (int at = 0; at < draws.size();) {
            Draw first = draws.get(at);
            RenderSystem.setShaderTexture(0, first.texture);
            shader.safeGetUniform("StraightMask").set(first.straightMask ? 1 : 0);
            var buffer = Tesselator.getInstance().getBuilder(); buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            do {
                Draw d = draws.get(at++);
                float left = d.x + (shadow ? 1 : 0), top = d.y + (shadow ? 1 : 0);
                int color = shadow ? d.color & 0xFF000000 | (d.color & 0xFCFCFC) >> 2 : d.color;
                buffer.vertex(matrix, left + d.shearBottom, top + d.height, 0).uv(d.u0, d.v1).color(color).endVertex();
                buffer.vertex(matrix, left + d.width + d.shearBottom, top + d.height, 0).uv(d.u1, d.v1).color(color).endVertex();
                buffer.vertex(matrix, left + d.width + d.shearTop, top, 0).uv(d.u1, d.v0).color(color).endVertex();
                buffer.vertex(matrix, left + d.shearTop, top, 0).uv(d.u0, d.v0).color(color).endVertex();
            } while (at < draws.size() && draws.get(at).texture == first.texture && draws.get(at).straightMask == first.straightMask);
            BufferUploader.drawWithShader(buffer.end());
        }
    }
}
