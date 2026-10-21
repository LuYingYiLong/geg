package io.github.luyingyilong.ore.gallery;

import io.github.luyingyilong.ore.*;
import io.github.luyingyilong.ore.input.*;
import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.theme.*;
import io.github.luyingyilong.ore.text.OreText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Shared layout samples with deliberate alpha/background contrasts, no per-frame UI creation. */
final class TextSamples {
    static Control create() {
        Box root = new Box().setGap(6);
        Dropdown<ResourceLocation> families = new Dropdown<>(OreFonts.BODY, 4);
        families.setOptions(List.of(new Dropdown.Option<>(OreFonts.BODY, Component.literal("Mojangles → Noto Sans")),
                new Dropdown.Option<>(OreFonts.HERO, Component.literal("Minecraft Ten → Noto Sans")),
                new Dropdown.Option<>(ResourceLocation.parse("ore:ore_fallback"), Component.literal("Noto Sans")),
                new Dropdown.Option<>(ResourceLocation.parse("ore_gallery:international"), GalleryView.tr("sample.text_multilingual"))));
        root.addChild(families);
        Dropdown<OreText.BackendMode> backends = new Dropdown<>(OreText.getBackendMode(), 3);
        backends.setOptions(Arrays.stream(OreText.BackendMode.values()).map(mode -> new Dropdown.Option<>(mode, Component.literal(mode.name()))).toList());
        backends.selectionChanged().connect(root, OreText::setBackendMode);
        root.addChild(backends);
        Checkbox shadow = new Checkbox(GalleryView.tr("sample.text_shadow")); root.addChild(shadow);
        List<Label> labels = new ArrayList<>();
        int[] backgrounds = {0xFF202020, 0xFFFFFFFF, 0xFF397A9E, 0};
        for (int background : backgrounds) {
            Box samples = new Box().setGap(4);
            for (int alpha : new int[]{255, 128, 64, 0}) {
                Label text = new Label(GalleryView.tr("sample.text_shaping")).setWrap(Label.Wrap.WORD);
                text.setThemeOverride(Label.FONT_COLOR, alpha << 24 | (background == -1 ? 0 : 0xFFFFFF));
                samples.addChild(text); labels.add(text);
            }
            // White on white detects premultiplication halos directly.
            if (background == -1) { Label white = new Label(Component.literal("White / 白色（50%）")); white.setThemeOverride(Label.FONT_COLOR, 0x80FFFFFF); samples.addChild(white); labels.add(white); }
            Container panel = new Container() {
                @Override public Size measureSize(net.minecraft.client.gui.Font font, Size available) { return getChild(0).desiredSize(font, available); }
                @Override public void layout(Rect bounds, net.minecraft.client.gui.Font font) {
                    super.layout(bounds, font);
                    fitChild(getChild(0), new Rect(0, 0, bounds.width(), bounds.height()), font);
                }
                @Override protected void render(DrawContext context, int x, int y) {
                    Rect area = getScreenBounds(x, y); var g = context.getGraphics();
                    if (background != 0) g.fill(area.x(),area.y(),area.right(),area.bottom(),background);
                    else for (int yy = 0; yy < area.height(); yy += 8) for (int xx = 0; xx < area.width(); xx += 8)
                        g.fill(area.x()+xx,area.y()+yy,Math.min(area.right(),area.x()+xx+8),Math.min(area.bottom(),area.y()+yy+8),
                                ((xx/8+yy/8)&1)==0 ? 0xFFE0E0E0 : 0xFF909090);
                }
            };
            panel.addChild(new Padding(Padding.Insets.all(4)).setChild(samples)); root.addChild(panel);
        }
        families.selectionChanged().connect(root, font -> labels.forEach(label -> label.setFont(font)));
        shadow.toggled().connect(root, enabled -> labels.forEach(label -> label.setThemeOverride(OreTheme.TEXT_SHADOW,enabled)));
        return root;
    }
}
