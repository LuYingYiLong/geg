package io.github.luyingyilong.ore.theme;

import io.github.luyingyilong.ore.Button;
import io.github.luyingyilong.ore.Label;
import io.github.luyingyilong.ore.input.Input;
import io.github.luyingyilong.ore.input.InputControl;
import io.github.luyingyilong.ore.input.TextArea;
import io.github.luyingyilong.ore.layout.Box;
import io.github.luyingyilong.ore.layout.Padding;
import io.github.luyingyilong.ore.layout.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.font.providers.TrueTypeGlyphProviderDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Files;

/** Opt-in, isolated client proof that the actual font loader uses the bearing fix. */
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
                var definition = new TrueTypeGlyphProviderDefinition(
                        ResourceLocation.fromNamespaceAndPath("ore", "noto_sans_cjk_sc_medium.otf"),
                        12, 4, new TrueTypeGlyphProviderDefinition.Shift(0, 0.1F), "");
                try (var provider = definition.unpack().left().orElseThrow().load(mc.getResourceManager())) {
                    check(provider instanceof OreTrueTypeGlyphProvider, "Resource loading uses the corrected Ore provider");
                    var glyph = provider.getGlyph('（');
                    glyph.bake(sheet -> {
                        check(sheet.getRight() <= glyph.getAdvance() + 0.25F, "Actual loaded bracket stays inside its advance");
                        return null;
                    });
                }
                Box content = new Box().gap(6);
                content.add(new Label(Component.literal("Ore punctuation / 全角与半角符号")).hero(true));
                content.add(new Label(Component.literal(SAMPLE)));
                content.add(new Label(Component.literal("【中文】「中文」，。；：！？ABC + 123")));
                content.add(new Label(Component.literal(SAMPLE)).fontSize(12));
                content.add(new Button(Component.literal("组织（2） / Group (2)")));
                input = new Input(); input.setTextNoSignal("中文（English）中文");
                area = new TextArea(0).rows(3); area.setTextNoSignal(SAMPLE + "\n【多行文本】（括号）");
                content.add(new InputControl(input)).add(new InputControl(area));
                root = Window.embedded().content(new Padding(Padding.Insets.all(8)).child(content)).open();
            } else if (ticks >= 25) {
                check(input.getText().equals("中文（English）中文") && area.getText().contains("（括号）"),
                        "Fullwidth characters are retained in the editor document");
                var path = mc.gameDirectory.toPath().resolve("screenshots/ore-glyph-bearing.png");
                Files.createDirectories(path.getParent());
                try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(path); }
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
