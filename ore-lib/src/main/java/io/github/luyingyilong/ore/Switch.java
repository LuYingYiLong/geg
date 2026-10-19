package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.signal.*;

import brachy.modularui.screen.viewport.ModularGuiContext;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.luyingyilong.ore.layout.Container;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.layout.SizeFlags;
import io.github.luyingyilong.ore.theme.OreIcons;
import io.github.luyingyilong.ore.theme.OreSounds;
import io.github.luyingyilong.ore.util.ReleaseGesture;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Pixel-sized toggle. State changes on release; only the thumb moves and receives the focus outline. */
public final class Switch extends Container {
    public static final int WIDTH = 28;
    public static final int HEIGHT = 15;
    private static final int BASE_WIDTH = 27;
    private static final int BASE_HEIGHT = 13;
    private static final int THUMB_WIDTH = 14;
    private static final int TRAVEL = 14;

    private final ReleaseGesture<Boolean> gesture = new ReleaseGesture<>();
    private final LongSupplier clock;
    private final Consumer<OreSounds.Cue> soundPlayer;
    private final Spin spinner = ownPart("spinner", new Spin());
    private final SignalSource<Boolean> toggled = new SignalSource<>(this, "toggled", "Boolean");
    private final SignalSource<Boolean> activated = new SignalSource<>(this, "activated", "Boolean");
    private boolean checked;
    private boolean disabled;
    private boolean loading;
    private long animationDuration = 240L;
    private long animationStart;
    private double animationFrom;

    public Switch() {
        this(false);
    }

    /** Initial checked state is placed immediately, without an entrance animation. */
    public Switch(boolean defaultChecked) {
        this(defaultChecked, () -> System.nanoTime() / 1_000_000L, OreSounds::play);
    }

    Switch(boolean defaultChecked, LongSupplier clock, Consumer<OreSounds.Cue> soundPlayer) {
        this.clock = Objects.requireNonNull(clock);
        this.soundPlayer = Objects.requireNonNull(soundPlayer);
        checked = defaultChecked;
        animationFrom = checked ? TRAVEL : 0;
        animationStart = clock.getAsLong();
        setMinimum(WIDTH, HEIGHT);
        setPreferred(WIDTH, HEIGHT);
        setSizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_BEGIN);
        setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        setFocusable(true);
        setFocusExpansion(1);
        spinner.setVisible(false);
        updateChildren(java.util.List.of(spinner));
    }

    @Override protected void validateChildren(java.util.List<io.github.luyingyilong.ore.layout.Control> proposed) {
        if (!isUpdatingChildren()) throw new IllegalStateException("Switch has a fixed skeleton; configure its properties");
    }

    public boolean getChecked() { return checked; }

    public Signal<Boolean> toggled() { return toggled.signal(); }
    public Signal<Boolean> activated() { return activated.signal(); }
    public Switch setChecked(boolean value) {
        boolean previous = checked;
        setCheckedNoSignal(value);
        if (previous != checked) toggled.emit(checked);
        return this;
    }

    /** Silent changes retain animation without playing a user-input sound. */
    public Switch setCheckedNoSignal(boolean value) {
        if (checked != value) {
            long now = clock.getAsLong();
            animationFrom = offsetAt(now);
            animationStart = now;
            checked = value;
        }
        return this;
    }

    public boolean getDisabled() { return disabled; }

    public Switch setDisabled(boolean value) {
        disabled = value;
        if (value) cancelInteraction();
        return this;
    }

    public boolean getLoading() { return loading; }

    /** Loading preserves the selected state and blocks further activation. */
    public Switch setLoading(boolean value) {
        loading = value;
        spinner.setVisible(value);
        if (value) cancelInteraction();
        return this;
    }


    public long getAnimationDuration() { return animationDuration; }

    /** Milliseconds; zero places the thumb immediately at its target. */
    public Switch setAnimationDuration(long milliseconds) {
        if (milliseconds < 0L) throw new IllegalArgumentException("Animation duration must not be negative");
        if (animationDuration == milliseconds) return this;
        long now = clock.getAsLong();
        animationFrom = offsetAt(now);
        animationStart = now;
        animationDuration = milliseconds;
        return this;
    }

    @Override public int measureWidth(Font font, int available) {
        if (!isAlive()) return 0; return Math.max(WIDTH, getMinimumWidth()); }
    @Override public int measureHeight(Font font, int available) {
        if (!isAlive()) return 0; return Math.max(HEIGHT, getMinimumHeight()); }

    @Override public void layout(Rect bounds, Font font) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            super.layout(bounds, font);
            layoutSpinner(thumbBounds(), font);
        }
    }

    /** Extra allocated space never stretches the textures or enlarges the clickable area. */
    Rect artworkBounds() {
        Rect area = getAbsoluteBounds();
        return new Rect(area.x() + Math.max(0, (area.width() - WIDTH) / 2),
                area.y() + Math.max(0, (area.height() - HEIGHT) / 2), WIDTH, HEIGHT);
    }

    Rect baseBounds() {
        Rect area = artworkBounds();
        return new Rect(area.x(), area.y() + HEIGHT - BASE_HEIGHT, BASE_WIDTH, BASE_HEIGHT);
    }

    Rect thumbBounds() {
        Rect area = artworkBounds();
        return new Rect(area.x() + (int) Math.round(offsetAt(clock.getAsLong())), area.y(), THUMB_WIDTH, HEIGHT);
    }

    @Override public Rect getFocusBounds() { return thumbBounds(); }
    @Override public boolean hit(int rootX, int rootY) { return getVisible() && artworkBounds().contains(rootX, rootY); }
    @Override public boolean acceptsFocus() { return available() && super.acceptsFocus(); }

    @Override protected void render(ModularGuiContext context, int screenX, int screenY) {
        Rect base = baseBounds().translated(screenX, screenY);
        Rect thumb = thumbBounds();
        layoutSpinner(thumb, null);
        thumb = thumb.translated(screenX, screenY);
        boolean hovered = artworkBounds().translated(screenX, screenY)
                .contains(context.getAbsMouseX(), context.getAbsMouseY());
        GuiGraphics graphics = context.getGraphics();
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        blit(graphics, disabled ? OreIcons.SWITCH_BASE_DISABLED : OreIcons.SWITCH_BASE, base);
        blit(graphics, buttonTexture(hovered), thumb);
        RenderSystem.disableBlend();
    }

    private static void blit(GuiGraphics graphics, ResourceLocation texture, Rect area) {
        graphics.blit(texture, area.x(), area.y(), 0.0F, 0.0F,
                area.width(), area.height(), area.width(), area.height());
    }

    private void layoutSpinner(Rect thumb, Font font) {
        Rect origin = getAbsoluteBounds();
        fitChild(spinner, new Rect(thumb.x() - origin.x() + (THUMB_WIDTH - 7) / 2,
                thumb.y() - origin.y() + (HEIGHT - 7) / 2, 7, 7), font);
    }

    ResourceLocation buttonTexture(boolean hovered) {
        if (disabled) return OreIcons.SWITCH_BUTTON_DISABLED;
        if (loading) return OreIcons.SWITCH_BUTTON_NORMAL;
        if (gesture.keyPressed(true) || hovered && gesture.pointerPending()) return OreIcons.SWITCH_BUTTON_PRESSED;
        return hovered ? OreIcons.SWITCH_BUTTON_HOVERED : OreIcons.SWITCH_BUTTON_NORMAL;
    }

    private boolean available() { return !disabled && !loading; }

    @Override protected boolean pointerDown(int rootX, int rootY) {
        if (!available() || !hit(rootX, rootY)) return false;

        gesture.pointerDown(true);
        return true;
    }

    @Override protected boolean pointerMove(int rootX, int rootY) { return gesture.pointerPending(); }

    @Override protected boolean pointerUp(boolean over) {
        var release = gesture.pointerUp(true, over && available() && getVisible());
        if (release.target() != null) activate();
        return release.handled();
    }

    @Override public boolean onActivationPressed(int keyCode) {
        if (!available() || !getVisible() || keyCode != GLFW.GLFW_KEY_ENTER && keyCode != GLFW.GLFW_KEY_SPACE) return false;
        gesture.keyDown(keyCode, true);
        return true;
    }

    @Override public boolean onActivationReleased(int keyCode) {
        var release = gesture.keyUp(keyCode, available() && getVisible() ? true : null);
        if (release.target() != null) activate();
        return release.handled();
    }

    @Override public void cancelInteraction() { gesture.cancel(); }
    @Override public void onFocusChanged(boolean focused) { if (!focused) cancelInteraction(); }

    @Override public boolean activate() {
        if (!available() || !getVisible()) return false;
        boolean next = !checked;
        soundPlayer.accept(OreSounds.Cue.CLICK);
        setChecked(next);
        activated.emit(next);
        return true;
    }

    double offsetAt(long milliseconds) {
        double target = checked ? TRAVEL : 0;
        if (animationFrom == target || animationDuration == 0L) return target;
        double progress = Math.max(0.0, Math.min(1.0, (double) (milliseconds - animationStart) / animationDuration));
        return animationFrom + (target - animationFrom) * bounceOut(progress);
    }

    /** Ease-out bounce stays inside the two endpoints, preserving the fixed pixel footprint. */
    private static double bounceOut(double progress) {
        if (progress < 1.0 / 2.75) return 7.5625 * progress * progress;
        if (progress < 2.0 / 2.75) {
            progress -= 1.5 / 2.75;
            return 7.5625 * progress * progress + 0.75;
        }
        if (progress < 2.5 / 2.75) {
            progress -= 2.25 / 2.75;
            return 7.5625 * progress * progress + 0.9375;
        }
        progress -= 2.625 / 2.75;
        return 7.5625 * progress * progress + 0.984375;
    }
}
