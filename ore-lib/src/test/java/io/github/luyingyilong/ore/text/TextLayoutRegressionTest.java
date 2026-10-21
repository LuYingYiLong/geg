package io.github.luyingyilong.ore.text;

import io.github.luyingyilong.ore.theme.OreFonts;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import java.nio.file.Path;
import java.util.*;

public final class TextLayoutRegressionTest {
    private static TextLayoutEngine engine;
    public static void main(String[] args) {
        try (var resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(
                new PathPackResources("test", Path.of(System.getProperty("ore.resourceRoot")), false)))) {
            engine = new TextLayoutEngine(new FontRepository(resources, 10), TextBackend.builtin());
            for (var font : List.of(OreFonts.BODY, OreFonts.HERO)) {
                for (String text : List.of("Minecraft 中文（半角）Test", "你好", "Hello", "e\u0301", "abc אבג 123", "مرحبا", "नमस्ते", "ภาษาไทย", "👩🏽‍💻🇨🇳")) {
                    var layout = layout(text, font, 128, TextLayoutEngine.Wrap.WORD);
                    check(layout.getWidth() > 0 && !layout.getLines().isEmpty(), "layout " + text);
                    var legal = Arrays.stream(TextBoundaries.graphemes(text)).boxed().toList();
                    for (var line : layout.getLines()) for (var stop : line.stops()) check(legal.contains(stop.offset()), "caret splits grapheme " + text);
                    for (float x = -1; x <= layout.getWidth() + 1; x += .5F) check(legal.contains(layout.hitTest(x, 0).offset()), "hit boundary");
                    var same = layout(text, font, 128, TextLayoutEngine.Wrap.WORD);
                    check(same == layout, "bounded layout reuse");
                }
                var a = layout("Hello", font, 128, TextLayoutEngine.Wrap.WORD);
                var b = layout("中文", font, 128, TextLayoutEngine.Wrap.WORD);
                check(a.getMetrics().equals(b.getMetrics()), "stable family baseline");
                for (int width : new int[]{0, 1, 127, 128, 129}) {
                    String text = "一二三四五六七八九十\n\nWord hello world 👩🏽‍💻";
                    var l = layout(text, font, width, TextLayoutEngine.Wrap.WORD);
                    for (var row : l.getLines()) {
                        check(row.width() <= width || TextBoundaries.graphemes(row.text().text()).length == 2 || row.text().text().isEmpty(), "wrap or single grapheme overflow");
                        for (var cell : row.cells()) check(cell.end() > cell.start() && cell.right() >= cell.left(), "valid cells");
                    }
                }
            }
            for (String emoji : List.of("👩🏽‍💻", "🇨🇳", "😀", "1️⃣")) {
                var l = layout(emoji, OreFonts.BODY, 100, TextLayoutEngine.Wrap.WORD);
                check(l.getLines().get(0).glyphs().size() == 1, "emoji cluster");
                check(l.getLines().get(0).glyphs().get(0).bitmap().colored(), "offline colored emoji");
            }
            var cjk = layout("（", OreFonts.BODY, 100, TextLayoutEngine.Wrap.NONE);
            var glyph = cjk.getLines().get(0).glyphs().get(0);
            var nativeVector = engine.getBackend().shape(glyph.font(), "（".toCharArray(), 0, 1, false);
            check(Math.abs(cjk.getWidth() - nativeVector.getGlyphPosition(nativeVector.getNumGlyphs()).getX()) < .001, "fullwidth advance unchanged");
            var source = Component.literal("link").withStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL,"https://example.com")));
            var rich = engine.layout(new TextLayoutEngine.Request(StyledText.of(source, OreFonts.HERO),OreFonts.HERO,12,100,TextLayoutEngine.Wrap.NONE,1,false,FontMetricsPolicy.DEFAULT,Locale.ROOT),null);
            check(rich.getStyleAt(2, 1).getClickEvent() != null, "component interaction styles");
            check(TextBoundaries.graphemes("e\u0301👩🏽‍💻🇨🇳").length == 4, "ICU grapheme sequences");
            var style = FontMetricsPolicy.parse(com.google.gson.JsonParser.parseString("{\"match_mode\":\"NATIVE\",\"line_spacing\":1.2,\"faces\":{\"ore:minecraft_ten.ttf\":{\"scale\":1.1,\"baseline_shift\":0.1}}}").getAsJsonObject());
            check(style.matchMode() == FontMetricsPolicy.MatchMode.NATIVE, "typed theme metrics");
        }
    }
    private static TextLayout layout(String value, ResourceLocation font, float width, TextLayoutEngine.Wrap wrap) {
        return engine.layout(new TextLayoutEngine.Request(StyledText.of(Component.literal(value), font),font,12,width,wrap,Integer.MAX_VALUE,false,FontMetricsPolicy.DEFAULT,Locale.ROOT),null);
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
