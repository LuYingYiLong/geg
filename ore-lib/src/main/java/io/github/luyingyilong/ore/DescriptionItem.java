package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.layout.*;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/**
 * A retained, read-only label/value pair. Descriptions owns its layout and shared text defaults.
 */
public final class DescriptionItem extends Container {
    private final String key;
    private final Box box = new Box().setOrientation(Box.Orientation.HORIZONTAL).setGap(4);
    private final Label label = new Label().setWrap(Label.Wrap.CHARACTER).setThemeTypeVariation("LabelSecondary");
    private final Label content = new Label().setWrap(Label.Wrap.CHARACTER);
    private Component labelText;
    private Descriptions.Layout layout = Descriptions.Layout.HORIZONTAL;
    private boolean colon = true;
    private int gap = 4;
    public DescriptionItem(String key, Component label, Component content) {
        if (Objects.requireNonNull(key).isBlank()) throw new IllegalArgumentException("Description key is blank");
        this.key = key;
        this.labelText = Objects.requireNonNull(label).copy();
        this.content.setText(Objects.requireNonNull(content));
        this.label.setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
        this.content.setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
        this.content.setSizeFlagsStretchRatio(2.0F);
        box.addChild(this.label);
        box.addChild(this.content);
        updateChildren(java.util.List.of(box));
        updateLabel();
        setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
    }

    public static void registerTheme(io.github.luyingyilong.ore.theme.ThemeDB db) {
        io.github.luyingyilong.ore.theme.OreTheme.registerType(db, "DescriptionItem");
    }

    @Override
    protected void validateChildren(java.util.List<io.github.luyingyilong.ore.layout.Control> proposed) {
        if (!isUpdatingChildren()) throw new IllegalStateException("DescriptionItem has a fixed field skeleton");
    }

    public String getKey() {
        return key;
    }

    public Component getLabel() {
        return labelText.copy();
    }

    public DescriptionItem setLabel(Component text) {
        labelText = Objects.requireNonNull(text).copy();
        updateLabel();
        return this;
    }

    public Component getContent() {
        return content.getText();
    }

    public DescriptionItem setContent(Component text) {
        content.setText(Objects.requireNonNull(text));
        return this;
    }

    public Label getLabelNode() {
        return label;
    }

    public Label getContentNode() {
        return content;
    }

    void configure(Descriptions.Layout layout, boolean colon, int gap) {
        if (this.layout != layout || this.gap != gap) {
            this.layout = layout;
            this.gap = gap;
            box.setOrientation(layout == Descriptions.Layout.HORIZONTAL ? Box.Orientation.HORIZONTAL : Box.Orientation.VERTICAL).setGap(gap);
            label.setSizeFlagsHorizontal(layout == Descriptions.Layout.HORIZONTAL ? SizeFlags.SIZE_EXPAND_FILL : SizeFlags.SIZE_FILL);
            content.setSizeFlagsHorizontal(layout == Descriptions.Layout.HORIZONTAL ? SizeFlags.SIZE_EXPAND_FILL : SizeFlags.SIZE_FILL);
        }
        if (this.colon != colon) {
            this.colon = colon;
            updateLabel();
        }
    }

    private void updateLabel() {
        label.setText(colon && !labelText.getString().isEmpty() ? labelText.copy().append(":") : labelText.copy());
    }

    @Override
    public Size measureSize(Font font, Size available) {
        if (!isAlive()) return new Size(0, 0);
        int width = Math.max(getMinimumWidth(), available.width());
        int height;
        if (layout == Descriptions.Layout.VERTICAL) {
            height = label.measureHeight(font, width) + gap + content.measureHeight(font, width);
        } else {
            int space = Math.max(0, width - gap);
            int captionWidth = (int) Math.ceil(space / 3.0);
            if (captionWidth < label.getMinimumWidth()) captionWidth = label.getMinimumWidth();
            else if (space - captionWidth < content.getMinimumWidth())
                captionWidth = Math.max(0, space - content.getMinimumWidth());
            height = Math.max(label.measureHeight(font, captionWidth),
                    content.measureHeight(font, Math.max(content.getMinimumWidth(), space - captionWidth)));
        }
        return new Size(width, Math.max(getMinimumHeight(), height));
    }

    @Override
    public int measureWidth(Font font, int available) {
        if (!isAlive()) return 0;
        return Math.max(getMinimumWidth(), available);
    }

    @Override
    public int measureHeight(Font font, int available) {
        if (!isAlive()) return 0;
        return measureSize(font, new Size(available, Integer.MAX_VALUE)).height();
    }

    @Override
    public void layout(Rect bounds, Font font) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            super.layout(bounds, font);
            fitChild(box, new Rect(0, 0, bounds.width(), bounds.height()), font);
        }
    }
}
