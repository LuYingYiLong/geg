package io.github.luyingyilong.ore.text;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import java.nio.file.*;
import java.util.*;

/** Real outline fixtures; missing-glyph boxes cannot make these shaping assertions pass. */
public final class ComplexTextTest {
    public static void main(String[] args) throws Exception {
        Path pack = Path.of("build/tmp/complex-text");
        Path directory = pack.resolve("assets/test/font"); Files.createDirectories(directory);
        String[] families = {"Arabic", "Hebrew", "Devanagari", "Thai"};
        String[] texts = {"السَّلام", "שָׁלוֹם", "नमस्तेक्षि", "ภาษาไทยกำ"};
        for (String family : families) {
            String file = "NotoSans" + family + ".ttf";
            try (var stream = ComplexTextTest.class.getResourceAsStream("/text-fonts/" + file)) {
                Files.copy(Objects.requireNonNull(stream), directory.resolve(file.toLowerCase(Locale.ROOT)), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.writeString(directory.resolve(family.toLowerCase(Locale.ROOT)+".json"),
                    "{\"providers\":[{\"type\":\"ttf\",\"file\":\"test:"+file.toLowerCase(Locale.ROOT)+"\"}]}");
        }
        try (var resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES,
                List.of(new PathPackResources("shaping", pack, false)))) {
            var fonts = new FontRepository(resources, 1);
            var engine = new TextLayoutEngine(fonts, TextBackend.builtin());
            for (int i=0; i<families.length; i++) {
                var id=ResourceLocation.parse("test:"+families[i].toLowerCase(Locale.ROOT));
                String text=texts[i];
                var source=StyledText.of(Component.literal(text), id);
                var request=new TextLayoutEngine.Request(source,id,16,400,TextLayoutEngine.Wrap.NONE,1,false,FontMetricsPolicy.DEFAULT,Locale.ROOT);
                var layout=engine.layout(request,null); var line=layout.getLines().get(0);
                check(line.glyphs().stream().allMatch(g->g.font()!=null && g.glyphId()!=g.font().getMissingGlyphCode()),"real glyph coverage "+families[i]);
                var font=line.glyphs().get(0).font();
                var vector=engine.getBackend().shape(font,text.toCharArray(),0,text.length(),i<2);
                check(vector.getNumGlyphs()==line.glyphs().size(),"one contextual run "+families[i]);
                check(Math.abs(line.width()-vector.getGlyphPosition(vector.getNumGlyphs()).getX())<.001,"shaped advances");
                var legal=Arrays.stream(TextBoundaries.graphemes(text)).boxed().toList();
                for (var glyph:line.glyphs())check(legal.contains(glyph.source()),"glyph cluster source");
                for(int boundary:legal)check(line.stops().stream().anyMatch(stop->stop.offset()==boundary),"caret coverage "+families[i]+" "+boundary);
                if(i<2)check(line.getX(0,false)>line.getX(text.length(),false),"RTL visual order");
                check(!layout.getSelection(0,text.length()).isEmpty(),"selection geometry");
            }
            // A logical selection crossing a bidi run can consist of disjoint visual ranges.
            var id=ResourceLocation.parse("test:hebrew");
            var mixed=engine.layout(new TextLayoutEngine.Request(StyledText.of(Component.literal("אבג 123 דהו"),id),id,16,400,
                    TextLayoutEngine.Wrap.NONE,1,false,FontMetricsPolicy.DEFAULT,Locale.ROOT),null);
            check(mixed.getSelection(1,5).size()>1,"bidi selection has multiple rectangles");
            var row=mixed.getLines().get(0);
            for(var stop:row.stops()) {
                var caret=mixed.getCaret(stop.offset(),0,stop.x());
                check(Math.abs(caret.x()-stop.x())<.001,"bidi caret preserves visual affinity");
            }
        }
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
