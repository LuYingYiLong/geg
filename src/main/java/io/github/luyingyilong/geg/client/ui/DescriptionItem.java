package io.github.luyingyilong.geg.client.ui;

import io.github.luyingyilong.geg.client.ui.layout.Box;
import io.github.luyingyilong.geg.client.ui.layout.Container;
import io.github.luyingyilong.geg.client.ui.layout.Rect;
import io.github.luyingyilong.geg.client.ui.layout.Size;
import io.github.luyingyilong.geg.client.ui.layout.SizeFlags;
import io.github.luyingyilong.geg.client.ui.theme.OreTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/** A retained, read-only label/value pair. Descriptions owns its layout and shared text defaults. */
public final class DescriptionItem extends Container {
    private final String key;
    private final Box box = new Box().orientation(Box.Orientation.HORIZONTAL).gap(4);
    private final Label label = new Label().wrap(Label.Wrap.CHARACTER).color(OreTheme.TEXT_SECONDARY);
    private final Label content = new Label().wrap(Label.Wrap.CHARACTER).color(OreTheme.TEXT_WHITE);
    private Component labelText;
    private Descriptions.Layout layout = Descriptions.Layout.HORIZONTAL;
    private boolean colon = true;
    private int gap = 4;

    public DescriptionItem(String key, Component label, Component content) {
        if (Objects.requireNonNull(key).isBlank()) throw new IllegalArgumentException("Description key is blank");
        this.key = key;
        this.labelText = Objects.requireNonNull(label).copy();
        this.content.text(Objects.requireNonNull(content));
        this.label.sizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
        this.content.sizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
        this.content.sizeFlagsStretchRatio(2.0F);
        box.add(this.label).add(this.content);
        super.addChild(box);
        updateLabel();
        sizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
    }

    public String key() { return key; }
    public Component label() { return labelText.copy(); }
    public Component content() { return content.text(); }
    public Label labelNode() { return label; }
    public Label contentNode() { return content; }
    public DescriptionItem label(Component text) { labelText = Objects.requireNonNull(text).copy(); updateLabel(); return this; }
    public DescriptionItem content(Component text) { content.text(Objects.requireNonNull(text)); return this; }

    void configure(Descriptions.Layout layout, boolean colon, int gap) {
        if (this.layout != layout || this.gap != gap) {
            this.layout = layout;
            this.gap = gap;
            box.orientation(layout == Descriptions.Layout.HORIZONTAL ? Box.Orientation.HORIZONTAL : Box.Orientation.VERTICAL).gap(gap);
            label.sizeFlagsHorizontal(layout == Descriptions.Layout.HORIZONTAL ? SizeFlags.SIZE_EXPAND_FILL : SizeFlags.SIZE_FILL);
            content.sizeFlagsHorizontal(layout == Descriptions.Layout.HORIZONTAL ? SizeFlags.SIZE_EXPAND_FILL : SizeFlags.SIZE_FILL);
        }
        if (this.colon != colon) { this.colon = colon; updateLabel(); }
    }

    private void updateLabel() {
        label.text(colon && !labelText.getString().isEmpty() ? labelText.copy().append(":") : labelText.copy());
    }

    @Override public Size measureSize(Font font, Size available) {
        int width = Math.max(minimumWidth(), available.width());
        int height;
        if (layout == Descriptions.Layout.VERTICAL) {
            height = label.measureHeight(font, width) + gap + content.measureHeight(font, width);
        } else {
            int space = Math.max(0, width - gap);
            int captionWidth = (int) Math.ceil(space / 3.0);
            if (captionWidth < label.minimumWidth()) captionWidth = label.minimumWidth();
            else if (space - captionWidth < content.minimumWidth()) captionWidth = Math.max(0, space - content.minimumWidth());
            height = Math.max(label.measureHeight(font, captionWidth),
                    content.measureHeight(font, Math.max(content.minimumWidth(), space - captionWidth)));
        }
        return new Size(width, Math.max(minimumHeight(), height));
    }

    @Override public int measureWidth(Font font, int available) { return Math.max(minimumWidth(), available); }
    @Override public int measureHeight(Font font, int available) { return measureSize(font, new Size(available, Integer.MAX_VALUE)).height(); }

    @Override public void layout(Rect bounds, Font font) {
        super.layout(bounds, font);
        fitChild(box, new Rect(0, 0, bounds.width(), bounds.height()), font);
    }
}
