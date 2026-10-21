package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.layout.SizeFlags;
import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.signal.Signal;
import io.github.luyingyilong.ore.signal.SignalSource;
import io.github.luyingyilong.ore.theme.*;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;

/**
 * Determinate progress; clips a full-size foreground rather than squeezing its texture.
 */
public final class ProgressBar extends Control {
    public static final ThemeKey<StyleBox> BACKGROUND = ThemeKey.of("ProgressBar", "background", StyleBox.class,
            ThemeKey.Kind.STYLE, new StyleBoxFlat(0xFF1E1E1F, ThemeInsets.ZERO, 0, ThemeInsets.ZERO, ThemeInsets.ZERO), true);
    public static final ThemeKey<StyleBox> FILL = ThemeKey.of("ProgressBar", "fill", StyleBox.class,
            ThemeKey.Kind.STYLE, new StyleBoxFlat(0xFF6CC349, ThemeInsets.ZERO, 0, ThemeInsets.ZERO, ThemeInsets.ZERO), false);
    public static final ThemeKey<Integer> FONT_COLOR = ThemeKey.of("ProgressBar", "font_color", Integer.class, ThemeKey.Kind.COLOR, 0xFFFFFFFF, false);
    public static final ThemeKey<Float> FONT_SIZE = new ThemeKey<>("ProgressBar", "font_size", Float.class, ThemeKey.Kind.FLOAT, 8.0F, true, v -> Float.isFinite(v) && v > 0);
    private static final ThemePadding PADDING = ThemePadding.of("ProgressBar", "", 1, 1, 1, 1);
    public static final ThemeKey<Integer> PADDING_TOP = PADDING.top(), PADDING_RIGHT = PADDING.right(),
            PADDING_BOTTOM = PADDING.bottom(), PADDING_LEFT = PADDING.left();
    private final SignalSource<Double> changed = new SignalSource<>(this, "valueChanged", "Double");
    private final Label percentage = ownPart("percentage", new Label().setOpticalCenter(true)
            .setAlignment(Label.Horizontal.CENTER, Label.Vertical.CENTER));
    private final ProgressValueModel values = new ProgressValueModel();
    private Direction direction = Direction.LEFT_TO_RIGHT;
    private boolean showPercentage;
    public ProgressBar() {
        updateChildren(List.of(percentage));
        percentage.setVisible(false);
        setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        setPreferred(128, 8);
        refresh();
    }

    public static void registerTheme(ThemeDB db) {
        OreTheme.registerType(db, "ProgressBar");
        db.register(BACKGROUND);
        db.register(FILL);
        db.register(FONT_COLOR);
        db.register(FONT_SIZE);
        PADDING.register(db);
    }

    @Override
    protected boolean requiresAxisAlignedClip() {
        return true;
    }

    @Override
    protected void validateChildren(List<Control> children) {
        if (!isUpdatingChildren()) throw new IllegalStateException("ProgressBar has a fixed skeleton");
    }

    public double getMin() {
        return values.getMin();
    }

    public ProgressBar setMin(double v) {
        return setLimits(v, getMax());
    }

    public double getMax() {
        return values.getMax();
    }

    public ProgressBar setMax(double v) {
        return setLimits(getMin(), v);
    }

    public double getValue() {
        return values.getValue();
    }

    public ProgressBar setValue(double v) {
        double old = getValue();
        setValueNoSignal(v);
        if (old != getValue()) changed.emit(getValue());
        return this;
    }

    public double getFraction() {
        return values.getFraction();
    }

    public Signal<Double> valueChanged() {
        return changed.signal();
    }

    public ProgressBar setMinNoSignal(double v) {
        return setLimitsNoSignal(v, getMax());
    }

    public ProgressBar setMaxNoSignal(double v) {
        return setLimitsNoSignal(getMin(), v);
    }

    public ProgressBar setLimits(double a, double b) {
        double old = getValue();
        setLimitsNoSignal(a, b);
        if (old != getValue()) changed.emit(getValue());
        return this;
    }

    public ProgressBar setLimitsNoSignal(double a, double b) {
        values.setLimits(a, b);
        refresh();
        return this;
    }

    public ProgressBar setValueNoSignal(double v) {
        values.setValue(v);
        refresh();
        return this;
    }

    private void refresh() {
        percentage.setText(Component.literal(Math.round(getFraction() * 100) + "%"));
    }

    public Direction getDirection() {
        return direction;
    }

    public ProgressBar setDirection(Direction d) {
        Objects.requireNonNull(d);
        if (direction == d) return this;
        boolean oldVertical = vertical();
        direction = d;
        if (oldVertical != vertical()) {
            setSizeFlagsHorizontal(vertical() ? SizeFlags.SIZE_SHRINK_CENTER : SizeFlags.SIZE_FILL);
            setSizeFlagsVertical(vertical() ? SizeFlags.SIZE_FILL : SizeFlags.SIZE_SHRINK_CENTER);
        }
        setPreferred(vertical() ? 8 : 128, vertical() ? 128 : 8);
        requestLayout();
        return this;
    }

    private boolean vertical() {
        return direction == Direction.TOP_TO_BOTTOM || direction == Direction.BOTTOM_TO_TOP;
    }

    public boolean getShowPercentage() {
        return showPercentage;
    }

    public ProgressBar setShowPercentage(boolean v) {
        showPercentage = v;
        percentage.setVisible(v);
        requestLayout();
        return this;
    }

    private void synchronizeTextTheme() {
        percentage.setThemeOverride(OreTheme.FONT_COLOR, getThemeItem(FONT_COLOR));
        percentage.setThemeOverride(OreTheme.FONT_SIZE, getThemeItem(FONT_SIZE));
    }

    @Override
    public int measureWidth(Font font, int available) {
        synchronizeTextTheme();
        int inset = PADDING.getInsets(this).getHorizontal() + getThemeItem(BACKGROUND).getContentMargin().getHorizontal();
        return Math.max(getMinimumWidth(), showPercentage ? percentage.measureWidth(font, available) + inset : inset + 1);
    }

    @Override
    public int measureHeight(Font font, int available) {
        synchronizeTextTheme();
        int inset = PADDING.getInsets(this).getVertical() + getThemeItem(BACKGROUND).getContentMargin().getVertical();
        return Math.max(getMinimumHeight(), showPercentage ? percentage.measureHeight(font, available) + inset : inset + 1);
    }

    @Override
    public void layout(Rect r, Font font) {
        super.layout(r, font);
        synchronizeTextTheme();
        percentage.layout(getInner(new Rect(0, 0, r.width(), r.height())), font);
    }

    @Override
    public int getMinimumWidth() {
        return Math.max(super.getMinimumWidth(), vertical() ? 8 : 16);
    }

    @Override
    public int getMinimumHeight() {
        return Math.max(super.getMinimumHeight(), vertical() ? 16 : 8);
    }

    private Rect getInner(Rect r) {
        return PADDING.getInsets(this).inset(getThemeItem(BACKGROUND).getContentRect(r));
    }

    public Rect getFillBounds(Rect inner) {
        int w = (int) Math.round(inner.width() * getFraction()), h = (int) Math.round(inner.height() * getFraction());
        return switch (direction) {
            case LEFT_TO_RIGHT -> new Rect(inner.x(), inner.y(), w, inner.height());
            case RIGHT_TO_LEFT -> new Rect(inner.right() - w, inner.y(), w, inner.height());
            case TOP_TO_BOTTOM -> new Rect(inner.x(), inner.y(), inner.width(), h);
            case BOTTOM_TO_TOP -> new Rect(inner.x(), inner.bottom() - h, inner.width(), h);
        };
    }

    @Override
    protected void render(DrawContext context, int x, int y) {
        Rect r = getScreenBounds(x, y);
        getThemeItem(BACKGROUND).draw(context, r);
        Rect inner = getInner(r), clip = getFillBounds(inner);
        if (clip.width() > 0 && clip.height() > 0) {
            context.enableScissor(clip.x(), clip.y(), clip.right(), clip.bottom());
            try {
                getThemeItem(FILL).draw(context, inner);
            } finally {
                context.disableScissor();
            }
        }
    }

    public enum Direction {LEFT_TO_RIGHT, RIGHT_TO_LEFT, TOP_TO_BOTTOM, BOTTOM_TO_TOP}
}
