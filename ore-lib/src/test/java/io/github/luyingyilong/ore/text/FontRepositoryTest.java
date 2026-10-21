package io.github.luyingyilong.ore.text;

import io.github.luyingyilong.ore.control.base.LifecycleScheduler;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.control.layout.Box;
import io.github.luyingyilong.ore.theme.OreTheme;
import io.github.luyingyilong.ore.theme.ThemeDB;

import io.github.luyingyilong.ore.control.display.Label;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class FontRepositoryTest {
    public static void main(String[] args) throws Exception {
        Path pack=Path.of("build/tmp/font-repository");
        Path fonts=pack.resolve("assets/test/font"); Files.createDirectories(fonts);
        try(var zip=new ZipOutputStream(Files.newOutputStream(fonts.resolve("sample.zip")))) {
            zip.putNextEntry(new ZipEntry("sample.hex"));
            zip.write(("0041:"+"FF".repeat(16)+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();
        }
        Files.writeString(fonts.resolve("hex.json"),"""
                {"providers":[{"type":"unihex","hex_file":"test:font/sample.zip",
                  "size_overrides":[{"from":"A","to":"A","left":0,"right":15}]}]}
                """);
        Files.writeString(fonts.resolve("main.json"),"""
                {"providers":[{"type":"reference","id":"test:hex"}]}
                """);
        Files.copy(Path.of(System.getProperty("ore.resourceRoot"), "assets/ore/font/minecraft_ten.ttf"),
                fonts.resolve("sample.ttf"), StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(fonts.resolve("outline.json"),"""
                {"providers":[{"type":"ttf","file":"test:sample.ttf","skip":["AB","C"]}]}
                """);
        try(var resources=new MultiPackResourceManager(PackType.CLIENT_RESOURCES,List.of(new PathPackResources("test",pack,false)))) {
            var repo=new FontRepository(resources,2);
            var id=ResourceLocation.parse("test:main");
            var face=repo.resolve(id,"A");var glyph=face.getBitmap('A');
            check(glyph!=null&&glyph.image().getWidth()==16&&glyph.advance()==9,"unihex override expands source cell");
            check(glyph.image().getRGB(7,4)==-1&&glyph.image().getRGB(8,4)==0,"padded columns stay transparent");
            var outline = repo.getFamily(ResourceLocation.parse("test:outline")).get(0);
            check(outline.isOutline() && outline.supports("D") && !outline.supports("A") && !outline.supports("C"),
                    "TTF skip arrays preserve the provider and combine every entry");
            var engine=new TextLayoutEngine(repo,TextBackend.builtin());
            var bold = StyledText.of(Component.literal("AA").withStyle(net.minecraft.ChatFormatting.BOLD, net.minecraft.ChatFormatting.ITALIC), id);
            var boldLayout = engine.layout(new TextLayoutEngine.Request(bold,id,8,100,TextLayoutEngine.Wrap.NONE,1,false,FontMetricsPolicy.DEFAULT,Locale.ROOT),null);
            check(boldLayout.getWidth() == 20 && boldLayout.getLines().get(0).glyphs().stream().allMatch(g -> g.style().isItalic()),
                    "bitmap bold advance and italic presentation share source styles");
            var source=StyledText.of(Component.literal("AA\nAAAA"),id);
            var result=engine.layout(new TextLayoutEngine.Request(source,id,8,15,TextLayoutEngine.Wrap.NONE,2,true,FontMetricsPolicy.DEFAULT,Locale.ROOT),null);
            var second=result.getLines().get(1);
            check(second.start()==3&&second.glyphs().get(0).source()>=3,"ellipsis retains document mapping on second line");
        }
        var theme=ThemeDB.getDefault().getTheme(ResourceLocation.parse("test:metrics"));
        var policy=new FontMetricsPolicy(FontMetricsPolicy.MatchMode.AUTO,1.5F,1,Map.of());
        theme.set(OreTheme.FONT_METRICS,policy);
        Box root=new Box();root.setTheme(theme);
        Label label=new Label(Component.literal("sample"));root.addChild(label);
        check(label.getThemeItem(OreTheme.FONT_METRICS).equals(policy),"inherited typed font metrics");
        root.layout(new Rect(0,0,200,80),null);
        label.setThemeOverride(OreTheme.FONT_METRICS,FontMetricsPolicy.DEFAULT);
        check(label.getThemeItem(OreTheme.FONT_METRICS).equals(FontMetricsPolicy.DEFAULT),"local metrics override");
        label.removeThemeOverride(OreTheme.FONT_METRICS);
        check(label.getThemeItem(OreTheme.FONT_METRICS).equals(policy),"metrics restore inherited value");
        root.queueFree();LifecycleScheduler.flush();
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
