package io.github.luyingyilong.ore.text;

import java.awt.Font;
import java.awt.font.GlyphVector;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

/** Only public optional APIs are linked; absent Modern UI never loads its classes on the server. */
final class TextBackends {
    private TextBackends() { }
    static TextBackend select(OreText.BackendMode mode) {
        if (mode == OreText.BackendMode.BUILTIN) return TextBackend.builtin();
        try {
            Class<?> mod = Class.forName("icyllis.modernui.mc.ModernUIMod");
            if (!Boolean.TRUE.equals(mod.getMethod("isTextEngineEnabled").invoke(null))) {
                System.getLogger(TextBackends.class.getName()).log(System.Logger.Level.INFO,
                        "Modern UI text engine is disabled; using built-in text");
                return TextBackend.builtin();
            }
            Class<?> type = Class.forName("icyllis.modernui.mc.text.GlyphManager");
            var lookup = MethodHandles.publicLookup();
            Object manager = type.getMethod("getInstance").invoke(null);
            MethodHandle shape = lookup.unreflect(type.getMethod("layoutGlyphVector", Font.class, char[].class, int.class, int.class, boolean.class)).bindTo(manager);
            if (!type.getField("sAntiAliasing").getBoolean(null)) {
                System.getLogger(TextBackends.class.getName()).log(System.Logger.Level.INFO,
                        "Modern UI antialiasing is disabled; using built-in coverage renderer");
                return TextBackend.builtin();
            }
            Class<?> outline = Class.forName("icyllis.modernui.graphics.text.OutlineFont");
            Class<?> fontInterface = Class.forName("icyllis.modernui.graphics.text.Font");
            Class<?> baked = Class.forName("icyllis.modernui.mc.text.GLBakedGlyph");
            var constructor = outline.getConstructor(Font.class);
            MethodHandle glyphLookup = lookup.unreflect(type.getMethod("lookupGlyph", fontInterface, int.class, int.class)).bindTo(manager);
            MethodHandle texture = lookup.unreflect(type.getMethod("getFontTexture")).bindTo(manager);
            var gx = baked.getField("x"); var gy = baked.getField("y"); var gw = baked.getField("width"); var gh = baked.getField("height");
            var u0 = baked.getField("u1"); var v0 = baked.getField("v1"); var u1 = baked.getField("u2"); var v1 = baked.getField("v2");
            return new TextBackend() {
                private final java.util.Map<Font, Object> fonts = new java.util.LinkedHashMap<>(32, .75F, true) {
                    @Override protected boolean removeEldestEntry(java.util.Map.Entry<Font, Object> entry) { return size() > 256; }
                };
                public String getId() { return "modern-ui-java2d"; }
                public GlyphVector shape(Font font, char[] text, int start, int end, boolean rtl) {
                    try { return (GlyphVector)shape.invoke(font, text, start, end, rtl); }
                    catch (VirtualMachineError | ThreadDeath error) { throw error; }
                    catch (Throwable error) { throw new TextBackend.Unavailable("Modern UI text adapter failed", error); }
                }
                public GpuGlyph getGlyphImage(TextLayout.Glyph glyph, float density) {
                    if (glyph.font() == null) return null;
                    try {
                        Object face = fonts.get(glyph.font());
                        if (face == null) { face = constructor.newInstance(glyph.font()); fonts.put(glyph.font(), face); }
                        int pixels = Math.max(1, (int)Math.ceil(glyph.font().getSize2D() * density));
                        Object image = glyphLookup.invoke(face, pixels, glyph.glyphId());
                        if (image == null) return null;
                        float unit = glyph.font().getSize2D() / pixels;
                        return new GpuGlyph((int)texture.invoke(), gx.getInt(image)*unit, gy.getInt(image)*unit,
                                gw.getShort(image)*unit, gh.getShort(image)*unit, u0.getFloat(image),v0.getFloat(image),u1.getFloat(image),v1.getFloat(image));
                    } catch (VirtualMachineError | ThreadDeath error) { throw error; }
                    catch (Throwable error) { throw new TextBackend.Unavailable("Modern UI glyph cache adapter failed", error); }
                }
            };
        } catch (ReflectiveOperationException | LinkageError error) {
            if (mode == OreText.BackendMode.MODERN_UI || !(error instanceof ClassNotFoundException))
                System.getLogger(TextBackends.class.getName()).log(System.Logger.Level.WARNING, "Modern UI backend unavailable; using built-in text", error);
            return TextBackend.builtin();
        }
    }
}
