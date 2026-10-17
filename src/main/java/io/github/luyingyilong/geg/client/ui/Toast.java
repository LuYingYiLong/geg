package io.github.luyingyilong.geg.client.ui;

import brachy.modularui.screen.viewport.ModularGuiContext;
import io.github.luyingyilong.geg.client.ui.layout.*;
import io.github.luyingyilong.geg.client.ui.theme.OreSounds;
import io.github.luyingyilong.geg.client.ui.theme.OreTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** A non-interactive transient message, usable in Ore trees or through OreToasts.show. */
public final class Toast extends Container {
    public enum Type {
        PRIMARY(Tag.Type.PRIMARY), NEUTRAL(Tag.Type.NEUTRAL), SECONDARY(Tag.Type.SECONDARY),
        INFORMATIVE(Tag.Type.INFORMATIVE), NOTICE(Tag.Type.NOTICE), WARNING(Tag.Type.WARNING),
        REALMS_INFORMATIVE(Tag.Type.REALMS_INFORMATIVE);
        private final Tag.Type style;
        Type(Tag.Type style) { this.style = style; }
        public int color() { return style.color(); }
    }

    public static final long DEFAULT_DURATION = 3000L;
    public static final long ENTER_DURATION = 180L;
    public static final long EXIT_DURATION = 160L;
    private static final int INSET_X = 6;
    private static final int INSET_Y = 4;
    private final Label label = new Label().wrap(Label.Wrap.WORD).maxLines(3).overflow(Label.Overflow.ELLIPSIS)
            .align(Label.Horizontal.CENTER, Label.Vertical.CENTER);
    private final LongSupplier clock;
    private final Consumer<OreSounds.Cue> sound;
    private Type type = Type.NEUTRAL;
    private long duration = DEFAULT_DURATION;
    private long openedAt;
    private long closingAt;
    private double closingFrom;
    private boolean started;
    private boolean closing;

    public Toast() { this(() -> System.nanoTime() / 1_000_000L, OreSounds::play); }
    public Toast(Component text) { this(); text(text); }
    Toast(LongSupplier clock, Consumer<OreSounds.Cue> sound) {
        this.clock = Objects.requireNonNull(clock); this.sound = Objects.requireNonNull(sound);
        addChild(label); type(type); visible(false);
        sizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_CENTER).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
    }

    public Toast text(Component text) { label.text(Objects.requireNonNull(text)); return this; }
    public Component text() { return label.text(); }
    /** Milliseconds of fully displayed time; zero keeps the toast open until close(). */
    public Toast duration(long milliseconds) {
        if (milliseconds < 0) throw new IllegalArgumentException("Negative toast duration");
        duration = milliseconds; return this;
    }
    public long duration() { return duration; }
    public Toast type(Type type) {
        this.type = Objects.requireNonNull(type); label.color(type.style.textColor()); return this;
    }
    public Type type() { return type; }

    public Toast open() {
        if (started && !finished()) return this;
        started = true; closing = false; openedAt = clock.getAsLong(); visible(true);
        sound.accept(OreSounds.Cue.TOAST); return this;
    }
    /** Keyed updates keep the existing toast and sound, and renew its displayed lifetime. */
    public Toast refresh() {
        if (!started || finished()) return open();
        openedAt = Math.max(openedAt, clock.getAsLong() - ENTER_DURATION); closing = false;
        return this;
    }
    public Toast close() {
        if (!started || closing || finished()) return this;
        closingFrom = progress(); closingAt = clock.getAsLong(); closing = true; return this;
    }
    public boolean finished() {
        if (!started) return true;
        long elapsed = Math.max(0L, clock.getAsLong() - (closing ? closingAt : openedAt));
        if (closing) return elapsed >= EXIT_DURATION;
        return duration > 0 && elapsed >= ENTER_DURATION && elapsed - ENTER_DURATION >= duration
                && elapsed - ENTER_DURATION - duration >= EXIT_DURATION;
    }
    /** Visibility fraction used by the host's slide from above the GUI viewport. */
    public double progress() {
        if (!started) return 0.0D;
        long elapsed = Math.max(0L, clock.getAsLong() - (closing ? closingAt : openedAt));
        if (closing) return closingFrom * (1.0D - easeIn(elapsed / (double) EXIT_DURATION));
        if (elapsed < ENTER_DURATION) return 1.0D - Math.pow(1.0D - elapsed / (double) ENTER_DURATION, 3.0D);
        long held = elapsed - ENTER_DURATION;
        return duration == 0 || held < duration ? 1.0D : 1.0D - easeIn((held - duration) / (double) EXIT_DURATION);
    }
    private static double easeIn(double phase) { return Math.pow(Math.min(1.0D, Math.max(0.0D, phase)), 3.0D); }

    @Override public Size measureSize(Font font, Size available) {
        int width = Math.max(0, Math.min(available.width(), label.measureWidth(font, available.width()) + INSET_X * 2));
        int height = label.measureHeight(font, Math.max(1, width - INSET_X * 2)) + INSET_Y * 2;
        return new Size(Math.max(minimumWidth(), width), Math.max(minimumHeight(), height));
    }
    @Override public void layout(Rect bounds, Font font) {
        super.layout(bounds, font);
        fitChild(label, new Rect(INSET_X, INSET_Y, Math.max(0, bounds.width() - INSET_X * 2),
                Math.max(0, bounds.height() - INSET_Y * 2)), font);
    }
    public static Rect shadowBounds(Rect area) { return area.translated(1, 1); }

    /** Global GUI adapter: the same Label painter, fonts and colors as other Ore controls. */
    public void drawGui(GuiGraphics graphics, Font font, Rect area) {
        if (!visible() || finished() || area.width() < 1 || area.height() < 1) return;
        Rect shadow = shadowBounds(area);
        // Paint the shadow behind the opaque face, leaving only its 1px right/bottom offset visible.
        graphics.fill(shadow.x(), shadow.y(), shadow.right(), shadow.bottom(), OreTheme.TOAST_SHADOW);
        graphics.fill(area.x(), area.y(), area.right(), area.bottom(), OreTheme.TEXT_DARK);
        if (area.width() > 2 && area.height() > 2)
            graphics.fill(area.x() + 1, area.y() + 1, area.right() - 1, area.bottom() - 1, type.color());
        label.paint(graphics, font, new Rect(area.x() + INSET_X, area.y() + INSET_Y,
                Math.max(0, area.width() - INSET_X * 2), Math.max(0, area.height() - INSET_Y * 2)), -1, -1);
    }
    @Override public void draw(ModularGuiContext context, int screenX, int screenY) {
        drawGui(context.getGraphics(), context.getFont(), screenBounds(screenX, screenY));
    }
    @Override public Control hitTop(int rootX, int rootY) { return null; }
}
