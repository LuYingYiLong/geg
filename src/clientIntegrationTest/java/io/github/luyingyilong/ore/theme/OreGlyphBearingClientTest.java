package io.github.luyingyilong.ore.theme;

import io.github.luyingyilong.ore.control.input.Button;
import io.github.luyingyilong.ore.control.display.Label;
import io.github.luyingyilong.ore.control.input.Input;
import io.github.luyingyilong.ore.control.input.InputControl;
import io.github.luyingyilong.ore.control.input.TextArea;
import io.github.luyingyilong.ore.control.layout.Box;
import io.github.luyingyilong.ore.control.layout.Padding;
import io.github.luyingyilong.ore.window.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Files;

/** Opt-in isolated client coverage of shared layout, input and premultiplied alpha. */
@Mod.EventBusSubscriber(modid = "geg", value = Dist.CLIENT)
public final class OreGlyphBearingClientTest {
    private static int ticks;
    private static Window root;
    private static Input input;
    private static TextArea area;
    private static boolean done;
    private static final String SAMPLE = "中文（English 123）中文 / 中文(English 123)中文";

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("geg.glyph.smoke") || event.phase != TickEvent.Phase.END || done) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        try {
            if (++ticks == 1) {
                var layout = io.github.luyingyilong.ore.text.OreText.layout(Component.literal(SAMPLE), OreFonts.BODY,
                        12, 400, io.github.luyingyilong.ore.text.TextLayoutEngine.Wrap.WORD, Integer.MAX_VALUE, false,
                        io.github.luyingyilong.ore.text.FontMetricsPolicy.DEFAULT, mc.font);
                check(layout.getGeneration() > 0 && layout.getWidth() > 0, "Unified font repository loaded");
                System.out.println("ORE_TEXT_BACKEND " + layout.getDiagnostics());
                Box content = new Box().setGap(6);
                content.addChild(new Label(Component.literal("Ore punctuation / 全角与半角符号")).setHero(true));
                content.addChild(new Label(Component.literal(SAMPLE)));
                content.addChild(new Label(Component.literal("Ten + 中文（English）é 👩🏽‍💻 🇨🇳")).setHero(true));
                var alphaRows = new Box().setGap(4);
                for (int color : new int[]{0xFFFFFFFF,0x80FFFFFF,0x40FFFFFF,0x10FFFFFF}) {
                    var line = new Label(Component.literal("抗锯齿 Alpha / Smooth edges / é"));
                    line.setThemeOverride(Label.FONT_COLOR,color); alphaRows.addChild(line);
                }
                var background = new io.github.luyingyilong.ore.control.layout.Panel().setChild(alphaRows);
                background.setThemeOverride(OreTheme.PANEL,new StyleBoxFlat(0xFFFFFFFF,ThemeInsets.ZERO,0,ThemeInsets.ZERO,ThemeInsets.ZERO));
                content.addChild(background);
                content.addChild(new Label(Component.literal("【中文】「中文」，。；：！？ABC + 123")));
                Label large = new Label(Component.literal(SAMPLE));
                large.setThemeOverride(Label.FONT_SIZE, 12.0F);
                content.addChild(large);
                content.addChild(new Button(Component.literal("组织（2） / Group (2)")));
                input = new Input(); input.setTextNoSignal("中文（English）中文");
                area = new TextArea(0).setRows(3); area.setTextNoSignal(SAMPLE + "\n【多行文本】（括号）");
                content.addChild(new InputControl(input));
                content.addChild(new InputControl(area));
                root = Window.embedded().setContent(new Padding(Padding.Insets.all(8)).setChild(content)).open();
            } else if (ticks >= 25) {
                io.github.luyingyilong.ore.control.input.InputGeometryProbe.verifyUnicodeLayout(input, area, root);
                check(input.getText().equals("中文（English）中文") && area.getText().contains("（括号）"),
                        "Fullwidth characters are retained in the editor document");
                var path = mc.gameDirectory.toPath().resolve("screenshots/ore-glyph-bearing.png");
                Files.createDirectories(path.getParent());
                try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(path); }
                io.github.luyingyilong.ore.window.TextRasterClientTest.verify();
                if (io.github.luyingyilong.ore.text.OreText.getEngine().getBackend().getId().startsWith("modern")) {
                    io.github.luyingyilong.ore.text.OreText.setBackendMode(io.github.luyingyilong.ore.text.OreText.BackendMode.BUILTIN);
                    io.github.luyingyilong.ore.window.TextRasterClientTest.verify();
                    io.github.luyingyilong.ore.text.OreText.setBackendMode(io.github.luyingyilong.ore.text.OreText.BackendMode.AUTO);
                    check(io.github.luyingyilong.ore.text.OreText.getEngine().getBackend().getId().startsWith("modern"), "AUTO restores compatible backend");
                    System.out.println("ORE_BACKEND_SWITCH_CLIENT_PASS");
                }
                System.out.println("ORE_GLYPH_BEARING_CLIENT_PASS");
                root.queueFree(); done = true; mc.stop();
            }
        } catch (Throwable error) {
            error.printStackTrace(); System.out.println("ORE_GLYPH_BEARING_CLIENT_FAIL");
            done = true; mc.stop();
        }
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
