package io.github.luyingyilong.geg.client.ui;

import brachy.modularui.screen.viewport.ModularGuiContext;
import io.github.luyingyilong.geg.client.ui.layout.Container;
import io.github.luyingyilong.geg.client.ui.layout.Rect;
import io.github.luyingyilong.geg.client.ui.layout.Size;
import io.github.luyingyilong.geg.client.ui.layout.SizeFlags;
import io.github.luyingyilong.geg.client.ui.theme.OreTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/** Compact, non-interactive status text with a solid semantic background. */
public final class Tag extends Container {
    public enum Type {
        PRIMARY(OreTheme.TAG_PRIMARY, OreTheme.TEXT_DARK),
        NEUTRAL(OreTheme.TAG_NEUTRAL, OreTheme.TEXT_WHITE),
        SECONDARY(OreTheme.TAG_SECONDARY, OreTheme.TEXT_WHITE),
        INFORMATIVE(OreTheme.TAG_INFORMATIVE, OreTheme.TEXT_DARK),
        NOTICE(OreTheme.TAG_NOTICE, OreTheme.TEXT_DARK),
        WARNING(OreTheme.TAG_WARNING, OreTheme.TEXT_DARK),
        REALMS_INFORMATIVE(OreTheme.TAG_REALMS_INFORMATIVE, OreTheme.TEXT_DARK);

        private final int color;
        private final int textColor;
        Type(int color, int textColor) { this.color = color; this.textColor = textColor; }
        public int color() { return color; }
        int textColor() { return textColor; }
    }

    private static final int PADDING_X = 4;
    private static final int PADDING_Y = 2;
    private final Label label = new Label().opticalCenter(true).align(Label.Horizontal.CENTER, Label.Vertical.CENTER)
            .overflow(Label.Overflow.ELLIPSIS).color(OreTheme.TEXT_WHITE);
    private boolean outlined;
    private Type type = Type.NEUTRAL;

    public Tag() {
        addChild(label);
        sizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_BEGIN).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
    }
    public Tag(Component text) { this(); text(text); }

    public Tag text(Component text) { label.text(Objects.requireNonNull(text)); return this; }
    public Component text() { return label.text(); }
    public Tag outlined(boolean outlined) {
        if (this.outlined != outlined) { this.outlined = outlined; requestLayout(); }
        return this;
    }
    public boolean outlined() { return outlined; }
    public Tag type(Type type) {
        this.type = Objects.requireNonNull(type);
        label.color(type.textColor);
        return this;
    }
    public Type type() { return type; }

    private int border() { return outlined ? 1 : 0; }

    @Override public Size measureSize(Font font, Size available) {
        int horizontal = 2 * (PADDING_X + border()), vertical = 2 * (PADDING_Y + border());
        Size text = label.measureSize(font, new Size(Math.max(0, available.width() - horizontal),
                Math.max(0, available.height() - vertical)));
        return new Size(Math.max(super.measureWidth(font, available.width()), text.width() + horizontal),
                Math.max(super.measureHeight(font, available.height()), text.height() + vertical));
    }
    @Override public int measureWidth(Font font, int available) { return measureSize(font, new Size(available, available)).width(); }
    @Override public int measureHeight(Font font, int available) { return measureSize(font, new Size(available, available)).height(); }

    @Override public void layout(Rect bounds, Font font) {
        super.layout(bounds, font);
        int x = PADDING_X + border(), y = PADDING_Y + border();
        fitChild(label, new Rect(x, y, Math.max(0, bounds.width() - 2 * x), Math.max(0, bounds.height() - 2 * y)), font);
    }

    @Override protected void render(ModularGuiContext context, int screenX, int screenY) {
        Rect area = screenBounds(screenX, screenY);
        if (area.width() <= 0 || area.height() <= 0) return;
        var graphics = context.getGraphics();
        if (outlined) graphics.fill(area.x(), area.y(), area.right(), area.bottom(), OreTheme.TAG_OUTLINE);
        int inset = border();
        if (area.width() > inset * 2 && area.height() > inset * 2)
            graphics.fill(area.x() + inset, area.y() + inset, area.right() - inset, area.bottom() - inset, type.color);
    }
}
