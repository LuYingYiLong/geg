package io.github.luyingyilong.ore.control.overlay;

import io.github.luyingyilong.ore.control.base.Container;
import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.control.base.Size;
import io.github.luyingyilong.ore.control.base.SizeFlags;
import io.github.luyingyilong.ore.control.display.Label;
import io.github.luyingyilong.ore.control.display.Tag;

import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.theme.OreSounds;
import io.github.luyingyilong.ore.theme.OreTheme;
import io.github.luyingyilong.ore.theme.ThemeKey;
import io.github.luyingyilong.ore.theme.ThemePadding;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * A non-interactive transient message, usable in Ore trees or through OreToasts.show.
 */
public final class Toast extends Container {
    public static final long DEFAULT_DURATION = 3000L;
    public static final long ENTER_DURATION = 180L;
    public static final long EXIT_DURATION = 160L;
    private static final ThemePadding PADDING = ThemePadding.of("Toast", "", 2, 4, 2, 4);
    public static final ThemeKey<Integer> PADDING_TOP = PADDING.top();
    public static final ThemeKey<Integer> PADDING_RIGHT = PADDING.right();
    public static final ThemeKey<Integer> PADDING_BOTTOM = PADDING.bottom();
    public static final ThemeKey<Integer> PADDING_LEFT = PADDING.left();
    private final Label label = ownPart("label", new Label().setWrap(Label.Wrap.WORD).setMaxLines(3).setOverflow(Label.Overflow.ELLIPSIS)
            .setOpticalCenter(true).setAlignment(Label.Horizontal.CENTER, Label.Vertical.CENTER));
    private final LongSupplier clock;
    private final Consumer<OreSounds.Cue> sound;
    private Type type = Type.NEUTRAL;
    private long duration = DEFAULT_DURATION;
    private long openedAt;
    private long closingAt;
    private double closingFrom;
    private boolean started;
    private boolean closing;

    public Toast() {
        this(() -> System.nanoTime() / 1_000_000L, OreSounds::play);
    }

    public Toast(Component text) {
        this();
        setText(text);
    }

    Toast(LongSupplier clock, Consumer<OreSounds.Cue> sound) {
        this.clock = Objects.requireNonNull(clock);
        this.sound = Objects.requireNonNull(sound);
        updateChildren(java.util.List.of(label));
        setType(type);
        setVisible(false);
        setSizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_CENTER).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
    }

    public static void registerTheme(io.github.luyingyilong.ore.theme.ThemeDB db) {
        io.github.luyingyilong.ore.theme.OreTheme.registerType(db, "Toast");
        PADDING.register(db);
    }

    private static double easeIn(double phase) {
        return Math.pow(Math.min(1.0D, Math.max(0.0D, phase)), 3.0D);
    }

    public static Rect shadowBounds(Rect area) {
        return area.translated(1, 1);
    }

    @Override
    protected void validateChildren(java.util.List<io.github.luyingyilong.ore.control.base.Control> proposed) {
        if (!isUpdatingChildren())
            throw new IllegalStateException("Toast has a fixed skeleton; configure its properties");
    }

    public Component getText() {
        return label.getText();
    }

    public Toast setText(Component text) {
        label.setText(Objects.requireNonNull(text));
        return this;
    }

    public long getDuration() {
        return duration;
    }

    /**
     * Milliseconds of fully displayed time; zero keeps the toast open until close().
     */
    public Toast setDuration(long milliseconds) {
        if (milliseconds < 0) throw new IllegalArgumentException("Negative toast duration");
        duration = milliseconds;
        return this;
    }

    public Type getType() {
        return type;
    }

    public Toast setType(Type type) {
        this.type = Objects.requireNonNull(type);
        label.setThemeTypeVariation(type.style.getTextColor() == OreTheme.TEXT_DARK ? "LabelDark" : "");
        return this;
    }

    public Toast open() {
        if (started && !getFinished()) return this;
        started = true;
        closing = false;
        openedAt = clock.getAsLong();
        setVisible(true);
        sound.accept(OreSounds.Cue.TOAST);
        return this;
    }

    /**
     * Keyed updates keep the existing toast and sound, and renew its displayed lifetime.
     */
    public Toast refresh() {
        if (!started || getFinished()) return open();
        openedAt = Math.max(openedAt, clock.getAsLong() - ENTER_DURATION);
        closing = false;
        return this;
    }

    public Toast close() {
        if (!started || closing || getFinished()) return this;
        closingFrom = getProgress();
        closingAt = clock.getAsLong();
        closing = true;
        return this;
    }

    public boolean getFinished() {
        if (!started) return true;
        long elapsed = Math.max(0L, clock.getAsLong() - (closing ? closingAt : openedAt));
        if (closing) return elapsed >= EXIT_DURATION;
        return duration > 0 && elapsed >= ENTER_DURATION && elapsed - ENTER_DURATION >= duration
                && elapsed - ENTER_DURATION - duration >= EXIT_DURATION;
    }

    /**
     * Visibility fraction used by the host's slide from above the GUI viewport.
     */
    public double getProgress() {
        if (!started) return 0.0D;
        long elapsed = Math.max(0L, clock.getAsLong() - (closing ? closingAt : openedAt));
        if (closing) return closingFrom * (1.0D - easeIn(elapsed / (double) EXIT_DURATION));
        if (elapsed < ENTER_DURATION) return 1.0D - Math.pow(1.0D - elapsed / (double) ENTER_DURATION, 3.0D);
        long held = elapsed - ENTER_DURATION;
        return duration == 0 || held < duration ? 1.0D : 1.0D - easeIn((held - duration) / (double) EXIT_DURATION);
    }

    @Override
    public Size measureSize(Font font, Size available) {
        if (!isAlive()) return new Size(0, 0);
        var padding = PADDING.getInsets(this);
        int width = Math.max(0, Math.min(available.width(), label.measureWidth(font,
                Math.max(0, available.width() - padding.getHorizontal())) + padding.getHorizontal()));
        int height = label.measureHeight(font, Math.max(1, width - padding.getHorizontal())) + padding.getVertical();
        return new Size(Math.max(getMinimumWidth(), width), Math.max(getMinimumHeight(), height));
    }

    @Override
    public void layout(Rect bounds, Font font) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            super.layout(bounds, font);
            fitChild(label, textBounds(new Rect(0, 0, bounds.width(), bounds.height())), font);
        }
    }

    Rect textBounds(Rect area) {
        return PADDING.getInsets(this).inset(area);
    }

    @Override
    protected void render(DrawContext context, int screenX, int screenY) {
        Rect area = getScreenBounds(screenX, screenY);
        if (!isAlive() || area.width() < 1 || area.height() < 1) return;
        var graphics = context.getGraphics();
        Rect shadow = shadowBounds(area);
        // Paint the shadow behind the opaque face, leaving only its 1px right/bottom offset visible.
        graphics.fill(shadow.x(), shadow.y(), shadow.right(), shadow.bottom(), getThemeItem(OreTheme.TOAST_SHADOW_KEY));
        graphics.fill(area.x(), area.y(), area.right(), area.bottom(), getThemeItem(OreTheme.TEXT_DARK_KEY));
        if (area.width() > 2 && area.height() > 2)
            graphics.fill(area.x() + 1, area.y() + 1, area.right() - 1, area.bottom() - 1, type.style.getColor(this));
    }

    @Override
    protected void drawContent(DrawContext context, int screenX, int screenY) {
        if (getFinished() || getSize().width() < 1 || getSize().height() < 1) return;
        super.drawContent(context, screenX, screenY);
    }

    @Override
    protected Control hitTestTree(int rootX, int rootY, int windowX, int windowY) {
        return null;
    }

    public enum Type {
        PRIMARY(Tag.Type.PRIMARY), NEUTRAL(Tag.Type.NEUTRAL), SECONDARY(Tag.Type.SECONDARY),
        INFORMATIVE(Tag.Type.INFORMATIVE), NOTICE(Tag.Type.NOTICE), WARNING(Tag.Type.WARNING),
        REALMS_INFORMATIVE(Tag.Type.REALMS_INFORMATIVE);
        private final Tag.Type style;

        Type(Tag.Type style) {
            this.style = style;
        }

        public int getColor() {
            return style.getColor();
        }
    }
}
