package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.theme.ThemeKey;
import io.github.luyingyilong.ore.theme.ThemePadding;
import io.github.luyingyilong.ore.layout.Container;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.layout.Size;
import io.github.luyingyilong.ore.layout.SizeFlags;
import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.theme.OreTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/**
 * Compact, non-interactive status text with a solid semantic background.
 */
public final class Tag extends Container {
    private static final ThemePadding PADDING = ThemePadding.of("Tag", "", 2, 4, 2, 4);
    public static final ThemeKey<Integer> PADDING_TOP = PADDING.top();
    public static final ThemeKey<Integer> PADDING_RIGHT = PADDING.right();
    public static final ThemeKey<Integer> PADDING_BOTTOM = PADDING.bottom();
    public static final ThemeKey<Integer> PADDING_LEFT = PADDING.left();

    private final Label label = ownPart("label", new Label().setOpticalCenter(true).setAlignment(Label.Horizontal.CENTER, Label.Vertical.CENTER)
            .setOverflow(Label.Overflow.ELLIPSIS));
    private boolean outlined;
    private Type type = Type.NEUTRAL;
    public Tag() {
        updateChildren(java.util.List.of(label));
        setSizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_BEGIN).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
    }

    public Tag(Component text) {
        this();
        setText(text);
    }

    public static void registerTheme(io.github.luyingyilong.ore.theme.ThemeDB db) {
        io.github.luyingyilong.ore.theme.OreTheme.registerType(db, "Tag");
        PADDING.register(db);
    }

    @Override
    protected void validateChildren(java.util.List<io.github.luyingyilong.ore.layout.Control> proposed) {
        if (!isUpdatingChildren())
            throw new IllegalStateException("Tag has a fixed skeleton; configure its properties");
    }

    public Component getText() {
        return label.getText();
    }

    public Tag setText(Component text) {
        label.setText(Objects.requireNonNull(text));
        return this;
    }

    public boolean getOutlined() {
        return outlined;
    }

    public Tag setOutlined(boolean outlined) {
        if (this.outlined != outlined) {
            this.outlined = outlined;
            requestLayout();
        }
        return this;
    }

    public Type getType() {
        return type;
    }

    public Tag setType(Type type) {
        this.type = Objects.requireNonNull(type);
        label.setThemeTypeVariation(type.textColor == OreTheme.TEXT_DARK ? "LabelDark" : "");
        return this;
    }

    private int border() {
        return outlined ? 1 : 0;
    }

    @Override
    public Size measureSize(Font font, Size available) {
        if (!isAlive()) return new Size(0, 0);
        var padding = PADDING.getInsets(this);
        int horizontal = padding.getHorizontal() + 2 * border(), vertical = padding.getVertical() + 2 * border();
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
            var padding = PADDING.getInsets(this);
            int x = padding.left() + border(), y = padding.top() + border();
            fitChild(label, new Rect(x, y, Math.max(0, bounds.width() - padding.getHorizontal() - 2 * border()),
                    Math.max(0, bounds.height() - padding.getVertical() - 2 * border())), font);
        }
    }

    @Override
    protected void render(DrawContext context, int screenX, int screenY) {
        Rect area = getScreenBounds(screenX, screenY);
        if (area.width() <= 0 || area.height() <= 0) return;
        var graphics = context.getGraphics();
        if (outlined)
            graphics.fill(area.x(), area.y(), area.right(), area.bottom(), getThemeItem(OreTheme.TAG_OUTLINE_KEY));
        int inset = border();
        if (area.width() > inset * 2 && area.height() > inset * 2)
            graphics.fill(area.x() + inset, area.y() + inset, area.right() - inset, area.bottom() - inset, type.getColor(this));
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

        public int getColor(io.github.luyingyilong.ore.layout.Control owner) {
            return owner.getThemeItem(switch (this) {
                case PRIMARY -> OreTheme.TAG_PRIMARY_KEY;
                case NEUTRAL -> OreTheme.TAG_NEUTRAL_KEY;
                case SECONDARY -> OreTheme.TAG_SECONDARY_KEY;
                case INFORMATIVE -> OreTheme.TAG_INFORMATIVE_KEY;
                case NOTICE -> OreTheme.TAG_NOTICE_KEY;
                case WARNING -> OreTheme.TAG_WARNING_KEY;
                case REALMS_INFORMATIVE -> OreTheme.TAG_REALMS_INFORMATIVE_KEY;
            });
        }

        public int getColor() {
            return color;
        }

        int textColor() {
            return textColor;
        }
    }
}
