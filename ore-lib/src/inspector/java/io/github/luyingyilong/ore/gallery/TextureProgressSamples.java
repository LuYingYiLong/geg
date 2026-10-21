package io.github.luyingyilong.ore.gallery;

import io.github.luyingyilong.ore.*;
import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.render.TextureSource;
import io.github.luyingyilong.ore.theme.IconTheme;
import net.minecraft.client.gui.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Local values only. Instances and subscriptions are retained until the sample is replaced. */
final class TextureProgressSamples {
    static TextureProgressBar flame(double value) {
        return new TextureProgressBar().setUnderTexture(TextureSource.themed(IconTheme.LIT_PROGRESS_UNDER))
                .setProgressTexture(TextureSource.themed(IconTheme.LIT_PROGRESS))
                .setFillMode(TextureProgressBar.FillMode.BOTTOM_TO_TOP).setValueNoSignal(value);
    }
    static TextureProgressBar arrow(double value) {
        return new TextureProgressBar().setUnderTexture(TextureSource.themed(IconTheme.BURN_PROGRESS_UNDER))
                .setProgressTexture(TextureSource.themed(IconTheme.BURN_PROGRESS)).setValueNoSignal(value);
    }
    private static Label text(String key) { return new Label(GalleryView.tr("sample.texture_progress_" + key)).setWrap(Label.Wrap.WORD); }
    static Control create() {
        Box root = new Box().setGap(8);
        List<TextureProgressBar> views = new ArrayList<>();
        Box furnace = new Box().setOrientation(Box.Orientation.HORIZONTAL).setGap(8);
        furnace.addChild(text("flame")); TextureProgressBar flame = flame(65); furnace.addChild(flame); views.add(flame);
        furnace.addChild(text("arrow")); TextureProgressBar arrow = arrow(65); furnace.addChild(arrow); views.add(arrow);
        root.addChild(furnace);
        Slider value = new Slider().setValueNoSignal(65);
        value.valueChanged().connect(root, v -> views.forEach(view -> view.setValueNoSignal(v)));
        root.addChild(value);

        Box modes = new Box().setOrientation(Box.Orientation.HORIZONTAL).setWrap(true).setGap(8);
        for (TextureProgressBar.FillMode mode : TextureProgressBar.FillMode.values()) {
            Box entry = new Box().setGap(4);
            entry.addChild(text(mode.name().toLowerCase(Locale.ROOT)));
            TextureProgressBar view = arrow(65).setFillMode(mode);
            if (mode.ordinal() >= TextureProgressBar.FillMode.CLOCKWISE.ordinal()) view.setRadialInitialAngle(330);
            entry.addChild(view); views.add(view); modes.addChild(entry);
        }
        root.addChild(modes);
        root.addChild(text("layers"));
        TextureProgressBar layers = arrow(65).setOverTexture(TextureSource.themed(IconTheme.BURN_PROGRESS_UNDER));
        layers.setThemeOverride(TextureProgressBar.OVER_TINT, 0x60FFFFFF);
        layers.setProgressOffset(new Offset(3, -2)); root.addChild(layers); views.add(layers);

        root.addChild(text("scale"));
        Box sizes = new Box().setOrientation(Box.Orientation.HORIZONTAL).setWrap(true).setGap(8);
        for (TextureProgressBar.StretchMode mode : TextureProgressBar.StretchMode.values()) {
            TextureProgressBar view = arrow(65).setStretchMode(mode).setExpandMode(TextureRect.ExpandMode.IGNORE_SIZE);
            sizes.addChild(new Backdrop(view, 0xFF313233)); views.add(view);
        }
        root.addChild(sizes);
        root.addChild(text("alpha"));
        Box alpha = new Box().setOrientation(Box.Orientation.HORIZONTAL).setWrap(true).setGap(8);
        for (int color : new int[]{0xFF1E1E1F, 0xFFF2F2F2, 0xFF295AA0, 0}) {
            TextureProgressBar view = arrow(65).setOpacity(0.5F).setStretchMode(TextureProgressBar.StretchMode.KEEP_ASPECT_CENTERED);
            view.setExpandMode(TextureRect.ExpandMode.IGNORE_SIZE);
            alpha.addChild(new Backdrop(view, color)); views.add(view);
        }
        root.addChild(alpha);
        return root;
    }
    private static final class Backdrop extends Container {
        final TextureProgressBar view;
        final int color;
        Backdrop(TextureProgressBar view, int color) { this.view = view; this.color = color; setMinimum(80, 48); addChild(view); }
        @Override public void layout(Rect bounds, Font font) {
            super.layout(bounds, font); view.layout(new Rect(4, 4, bounds.width() - 8, bounds.height() - 8), font);
        }
        @Override protected void render(DrawContext context, int x, int y) {
            Rect r = getScreenBounds(x, y);
            for (int yy = 0; yy < r.height(); yy += 4) for (int xx = 0; xx < r.width(); xx += 4) {
                int c = color != 0 ? color : ((xx / 4 + yy / 4) % 2 == 0 ? 0xFFEEEEEE : 0xFF888888);
                context.getGraphics().fill(r.x() + xx, r.y() + yy, r.x() + Math.min(r.width(), xx + 4), r.y() + Math.min(r.height(), yy + 4), c);
            }
        }
    }
}
