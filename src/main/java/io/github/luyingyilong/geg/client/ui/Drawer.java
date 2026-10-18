package io.github.luyingyilong.geg.client.ui;

import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetTheme;
import brachy.modularui.theme.WidgetThemeEntry;
import io.github.luyingyilong.geg.client.ui.layout.Box;
import io.github.luyingyilong.geg.client.ui.layout.Container;
import io.github.luyingyilong.geg.client.ui.layout.Control;
import io.github.luyingyilong.geg.client.ui.layout.Padding;
import io.github.luyingyilong.geg.client.ui.layout.Panel;
import io.github.luyingyilong.geg.client.ui.layout.Rect;
import io.github.luyingyilong.geg.client.ui.layout.Scroll;
import io.github.luyingyilong.geg.client.ui.layout.SizeFlags;
import io.github.luyingyilong.geg.client.ui.theme.OreIcons;
import io.github.luyingyilong.geg.client.ui.theme.OreSounds;
import io.github.luyingyilong.geg.client.ui.theme.OreTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * An edge overlay, initially closed. Add it to Window's Overlay, then call open().
 * The panel slides in from and returns to its selected edge; the mask fades with it.
 * onClose runs after the exit animation, so the owner can safely remove the layer.
 * <pre>{@code
 * Drawer drawer = new Drawer(title).fontSize(12).width(240).content(form);
 * overlay.add(drawer);
 * drawer.open();
 * }</pre>
 */
public final class Drawer extends Container {
    public enum Placement { LEFT, RIGHT }
    public static final int PADDING = 4;
    private static final int CLOSE_SIZE = 20;
    private static final int CLOSE_ICON_SIZE = 7;

    private final LongSupplier clock;
    private final Consumer<OreSounds.Cue> soundPlayer;
    private final Label title = new Label().hero(true).opticalCenter(true).overflow(Label.Overflow.ELLIPSIS)
            .align(Label.Horizontal.LEFT, Label.Vertical.CENTER).color(OreTheme.TEXT_WHITE);
    private final Box header = new Box().orientation(Box.Orientation.HORIZONTAL).gap(4);
    private final Button closeButton;
    private final Box footer = new Box().gap(2);
    private final Scroll body = new Scroll();
    private final Box column = new Box().gap(4);
    private final Padding frame = new Padding(Padding.Insets.all(PADDING)).child(column);
    private Panel background;
    private int titleColor = OreTheme.TEXT_WHITE;
    private Runnable onClose = () -> { };
    private int requestedWidth;
    private Placement placement = Placement.RIGHT;
    private boolean closable = true;
    private float depth = 200.0F;
    private long durationMs = 200L;
    private long transitionStart;
    private double transitionFrom;
    private double progress;
    private boolean targetOpen;
    private boolean animating;

    public Drawer(Component title) {
        this(title, () -> System.nanoTime() / 1_000_000L);
    }

    /** Injectable monotonic clock keeps transition and interaction tests deterministic. */
    Drawer(Component title, LongSupplier clock) {
        this(title, clock, OreSounds::play);
    }

    Drawer(Component title, LongSupplier clock, Consumer<OreSounds.Cue> soundPlayer) {
        this.clock = Objects.requireNonNull(clock);
        this.soundPlayer = Objects.requireNonNull(soundPlayer);
        this.title.sizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).sizeFlagsVertical(SizeFlags.SIZE_FILL);
        closeButton = new Button().type(Button.Type.TEXT)
                .icon(OreIcons.CLOSE, CLOSE_ICON_SIZE, CLOSE_ICON_SIZE).onPress(this::close);
        closeButton.minimum(CLOSE_SIZE, CLOSE_SIZE);
        closeButton.sizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_CENTER).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        header.add(this.title).add(closeButton);
        header.minimum(0, CLOSE_SIZE).sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        body.sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        footer.sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        footer.visible(false);
        column.add(header).add(body).add(footer);
        super.addChild(frame);
        super.visible(false);
        title(title);
    }

    public Drawer title(Component text) {
        title.text(Objects.requireNonNull(text)); updateHeader(); return this;
    }
    /** Controls the close button; Escape and mask-click dismissal remain independent. */
    public Drawer closable(boolean closable) {
        this.closable = closable; updateHeader(); return this;
    }
    public boolean closable() { return closable; }
    private void updateHeader() {
        closeButton.visible(closable);
        header.visible(closable || !title.getText().getString().isBlank());
        requestLayout();
    }
    public Drawer titleColor(int color) { titleColor = color; title.color(color); return this; }
    public int titleColor() { return titleColor; }
    public Drawer fontSize(float fontSize) { title.fontSize(fontSize); return this; }
    public float fontSize() { return title.fontSize(); }

    /** Inner spacing around the header, body and footer; defaults to 4px on every side. */
    public Drawer padding(Padding.Insets insets) {
        frame.insets(Objects.requireNonNull(insets));
        return this;
    }
    public Drawer padding(int top, int right, int bottom, int left) {
        return padding(new Padding.Insets(top, right, bottom, left));
    }
    public Padding.Insets padding() { return frame.insets(); }

    public Drawer placement(Placement placement) {
        this.placement = Objects.requireNonNull(placement);
        requestLayout();
        return this;
    }
    public Placement placement() { return placement; }

    /** Zero (the default) uses half the GUI viewport; explicit widths are viewport-limited. */
    public Drawer width(int width) {
        if (width < 0) throw new IllegalArgumentException("Drawer width cannot be negative");
        requestedWidth = width;
        requestLayout();
        return this;
    }

    public Drawer content(Control content) {
        body.content(Objects.requireNonNull(content));
        return this;
    }

    /** Public children belong to the scrollable body, not the viewport-sized overlay. */
    @Override public Drawer addChild(Control content) { return content(content); }

    public Scroll bodyScroll() { return body; }

    public Drawer action(Component text, Runnable onPress) {
        return action(text, onPress, Button.Type.SECONDARY);
    }

    public Drawer action(Component text, Runnable onPress, Button.Type type) {
        Button button = new Button(text).type(type).onPress(onPress);
        button.sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        footer.addChild(button);
        footer.minimum(0, footer.children().size() * Button.HEIGHT
                + Math.max(0, footer.children().size() - 1) * 2);
        footer.visible(true);
        return this;
    }

    public Drawer clearActions() {
        for (Control child : footer.children()) footer.removeChild(child);
        footer.minimum(0, 0);
        footer.visible(false);
        return this;
    }

    public Drawer onClose(Runnable callback) { onClose = Objects.requireNonNull(callback); return this; }
    @Override public float contentDepth() { return depth; }

    public Drawer depth(float depth) { this.depth = depth; return this; }
    public float depth() { return depth; }

    public Drawer animationDuration(long milliseconds) {
        if (milliseconds < 0) throw new IllegalArgumentException("Duration cannot be negative");
        advanceAnimation();
        durationMs = milliseconds;
        transitionFrom = progress;
        transitionStart = clock.getAsLong();
        return this;
    }

    public Drawer open() { return open(true); }
    public Drawer close() { return open(false); }

    public Drawer open(boolean open) {
        advanceAnimation();
        if (targetOpen == open) return this;
        targetOpen = open;
        if (!open) frame.cancelInteractions();
        soundPlayer.accept(open ? OreSounds.Cue.DRAWER_OPEN : OreSounds.Cue.DRAWER_CLOSE);
        transitionFrom = progress;
        transitionStart = clock.getAsLong();
        animating = true;
        super.visible(true);
        requestLayout();
        advanceAnimation();
        return this;
    }

    /** Remains visible and intercepts input until the closing animation finishes. */
    @Override public void visible(boolean visible) { open(visible); }
    public boolean isOpen() { return targetOpen; }
    public boolean animating() { return animating; }
    public Rect panelBounds() { return frame.absoluteBounds(); }
    @Override public boolean focusScope() { return true; }

    private void advanceAnimation() {
        if (!animating) return;
        double target = targetOpen ? 1.0D : 0.0D;
        double duration = durationMs * Math.abs(target - transitionFrom);
        double elapsed = Math.max(0.0D, (double) clock.getAsLong() - transitionStart);
        double phase = duration == 0.0D ? 1.0D : Math.min(1.0D, elapsed / duration);
        // Smoothstep preserves the exact start and end positions, including reversed transitions.
        double eased = phase * phase * (3.0D - 2.0D * phase);
        progress = transitionFrom + (target - transitionFrom) * eased;
        if (phase == 1.0D) {
            animating = false;
            if (!targetOpen) {
                super.visible(false);
                onClose.run();
            }
        }
    }

    int maskColor() {
        int alpha = (int) Math.round((OreTheme.OVERLAY_COLOR >>> 24) * progress);
        return (alpha << 24) | (OreTheme.OVERLAY_COLOR & 0x00FFFFFF);
    }

    @Override public void layout(Rect viewport, Font font) {
        super.layout(viewport, font);
        layoutPanel(font);
    }

    private void layoutPanel(Font font) {
        advanceAnimation();
        int viewportWidth = Math.max(1, bounds().width());
        int width = Math.min(viewportWidth, Math.max(1,
                requestedWidth == 0 ? viewportWidth / 2 : requestedWidth));
        int visibleWidth = (int) Math.round(width * progress);
        int x = placement == Placement.LEFT ? visibleWidth - width : viewportWidth - visibleWidth;
        // Move the actual layout rectangle, so hit tests, focus and native inputs follow the slide.
        fitChild(frame, new Rect(x, 0, width, bounds().height()), font);
    }

    @Override public void draw(ModularGuiContext context, int screenX, int screenY) {
        if (!visible()) return;
        layoutPanel(context.getFont());
        if (!visible()) return;
        GuiGraphics graphics = context.getGraphics();
        Rect viewport = screenBounds(screenX, screenY);
        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, depth);
        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        try {
            graphics.fill(viewport.x(), viewport.y(), viewport.right(), viewport.bottom(), maskColor());
            if (background == null) background = new Panel(OreTheme.FLOATING_BACKGROUND);
            WidgetThemeEntry<WidgetTheme> entry = context.getTheme().getWidgetTheme(OreTheme.FLOATING_BACKGROUND);
            WidgetTheme style = entry == null ? context.getTheme().getPanelTheme().theme() : entry.theme();
            title.color(titleColor).shadow(style.isTextShadow());
            background.drawAt(context, frame.screenBounds(screenX, screenY));
            frame.draw(context, screenX, screenY);
            graphics.flush();
        } finally {
            graphics.disableScissor();
            graphics.pose().popPose();
        }
    }

    @Override public Control hitTop(int rootX, int rootY) {
        if (!hit(rootX, rootY)) return null;
        Control child = targetOpen && frame.hit(rootX, rootY) ? frame.hitTop(rootX, rootY) : null;
        return child == null ? this : child;
    }

    @Override public boolean dispatchPointer(int rootX, int rootY) {
        if (!hit(rootX, rootY)) return false;
        if (targetOpen) {
            if (frame.hit(rootX, rootY)) frame.dispatchPointer(rootX, rootY);
            else close();
        }
        return true;
    }

    @Override public boolean dispatchScroll(int rootX, int rootY, double delta, boolean horizontal) {
        if (!hit(rootX, rootY)) return false;
        if (targetOpen && frame.hit(rootX, rootY)) frame.dispatchScroll(rootX, rootY, delta, horizontal);
        return true;
    }

    @Override public boolean onFocusKey(int keyCode, int modifiers) {
        if (!targetOpen) return visible();
        if (keyCode != GLFW.GLFW_KEY_ESCAPE) return false;
        close();
        return true;
    }

}
