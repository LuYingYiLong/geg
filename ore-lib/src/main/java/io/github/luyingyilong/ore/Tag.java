package io.github.luyingyilong.ore;

import brachy.modularui.screen.viewport.ModularGuiContext;
import io.github.luyingyilong.ore.layout.Container;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.layout.Size;
import io.github.luyingyilong.ore.layout.SizeFlags;
import io.github.luyingyilong.ore.theme.OreTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/**
 * Compact, non-interactive status text with a solid semantic background.
 */
public final class Tag extends Container {
    private static final int PADDING_X = 4;
    private static final int PADDING_Y = 2;
    private final Label label = ownPart("label", new Label().setOpticalCenter(true).setAlignment(Label.Horizontal.CENTER, Label.Vertical.CENTER)
            .setOverflow(Label.Overflow.ELLIPSIS).setColor(OreTheme.TEXT_WHITE));
    private boolean outlined;
    private Type type = Type.NEUTRAL;
    public Tag() {
        addChild(label);
        setSizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_BEGIN).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
    }

    public Tag(Component text) {
        this();
        setText(text);
    }

    public Tag setText(Component text) {
        label.setText(Objects.requireNonNull(text));
        return this;
    }

    public Component getText() {
        return label.getText();
    }

    public Tag setOutlined(boolean outlined) {
        if (this.outlined != outlined) {
            this.outlined = outlined;
            requestLayout();
        }
        return this;
    }

    public boolean getOutlined() {
        return outlined;
    }

    public Tag setType(Type type) {
        this.type = Objects.requireNonNull(type);
        label.setColor(type.textColor);
        return this;
    }

    public Type getType() {
        return type;
    }

    private int border() {
        return outlined ? 1 : 0;
    }

    @Override
    public Size measureSize(Font font, Size available) {
        if (!isAlive()) return new Size(0, 0);
        int horizontal = 2 * (PADDING_X + border()), vertical = 2 * (PADDING_Y + border());
        Size text = label.measureSize(font, new Size(Math.max(0, available.width() - horizontal),
                Math.max(0, available.height() - vertical)));
        return new Size(Math.max(super.measureWidth(font, available.width()), text.width() + horizontal),
                Math.max(super.measureHeight(font, available.height()), text.height() + vertical));
    }

    @Override
    public int measureWidth(Font font, int available) {
        if (!isAlive()) return 0;
        return measureSize(font, new Size(available, available)).width();
    }

    @Override
    public int measureHeight(Font font, int available) {
        if (!isAlive()) return 0;
        return measureSize(font, new Size(available, available)).height();
    }

    @Override
    public void layout(Rect bounds, Font font) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            super.layout(bounds, font);
            int x = PADDING_X + border(), y = PADDING_Y + border();
            fitChild(label, new Rect(x, y, Math.max(0, bounds.width() - 2 * x), Math.max(0, bounds.height() - 2 * y)), font);
        }
    }

    @Override
    protected void render(ModularGuiContext context, int screenX, int screenY) {
        Rect area = getScreenBounds(screenX, screenY);
        if (area.width() <= 0 || area.height() <= 0) return;
        var graphics = context.getGraphics();
        if (outlined) graphics.fill(area.x(), area.y(), area.right(), area.bottom(), OreTheme.TAG_OUTLINE);
        int inset = border();
        if (area.width() > inset * 2 && area.height() > inset * 2)
            graphics.fill(area.x() + inset, area.y() + inset, area.right() - inset, area.bottom() - inset, type.color);
    }

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

        Type(int color, int textColor) {
            this.color = color;
            this.textColor = textColor;
        }

        public int getColor() {
            return color;
        }

        int textColor() {
            return textColor;
        }
    }
}
