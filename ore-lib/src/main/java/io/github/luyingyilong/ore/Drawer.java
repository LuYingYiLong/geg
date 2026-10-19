package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.signal.*;

import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.debug.Inspectable;
import io.github.luyingyilong.ore.debug.DebugProperty;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetTheme;
import brachy.modularui.theme.WidgetThemeEntry;
import io.github.luyingyilong.ore.layout.Box;
import io.github.luyingyilong.ore.layout.Container;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.Padding;
import io.github.luyingyilong.ore.layout.Panel;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.layout.Scroll;
import io.github.luyingyilong.ore.layout.SizeFlags;
import io.github.luyingyilong.ore.theme.OreIcons;
import io.github.luyingyilong.ore.theme.OreSounds;
import io.github.luyingyilong.ore.theme.OreTheme;
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
 * The closed signal runs after the exit animation, so the owner can safely remove the layer.
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
    private final SignalSource0 closed = new SignalSource0(this, "closed");
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
        closeButton = Signals.configure(new Button().type(Button.Type.TEXT)
                .icon(OreIcons.CLOSE, CLOSE_ICON_SIZE, CLOSE_ICON_SIZE), connected -> connected.pressed().connect(connected, this::close));
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
        Objects.requireNonNull(placement);
        if (DebugBridge.previewWrite(this, "placement", placement)) return this;
        this.placement = placement;
        requestLayout();
        return this;
    }
    @Inspectable
    public Placement placement() {
        Placement placement = DebugBridge.value(this, "placement", this.placement);
        return placement;
    }

    /** Zero (the default) uses half the GUI viewport; explicit widths are viewport-limited. */
    @Inspectable(setter = "width", min = 0, max = 100000)
    public int requestedWidth() { return DebugBridge.value(this, "requestedWidth", requestedWidth); }

    public Drawer width(int width) {
        if (width < 0) throw new IllegalArgumentException("Drawer width cannot be negative");
        if (DebugBridge.previewWrite(this, "requestedWidth", width)) return this;
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

    public Drawer addAction(Button button) {
        Objects.requireNonNull(button);
        button.sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        footer.addChild(button);
        footer.minimum(0, footer.children().size() * Button.HEIGHT
                + Math.max(0, footer.children().size() - 1) * 2);
        footer.visible(true);
        return this;
    }

    public Drawer clearActions() {
        for (Control child : footer.children()) { if (child.parent() != null) child.parent().removeChild(child); child.queueFree(); }
        footer.minimum(0, 0);
        footer.visible(false);
        return this;
    }

    public Signal0 closed() { return closed.signal(); }
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
                closed.emit();
            }
        }
    }

    int maskColor() {
        int alpha = (int) Math.round((OreTheme.OVERLAY_COLOR >>> 24) * progress);
        return (alpha << 24) | (OreTheme.OVERLAY_COLOR & 0x00FFFFFF);
    }

    @Override public void layout(Rect viewport, Font font) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            super.layout(viewport, font);
            layoutPanel(font);
        }
    }

    private void layoutPanel(Font font) {
        Placement placement = DebugBridge.value(this, "placement", this.placement);
        int requestedWidth = DebugBridge.value(this, "requestedWidth", this.requestedWidth);
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
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            if (!isAlive()) return;
            DebugBridge.backend().paint(this, context, screenX, screenY);
            if (!visible()) return;
            layoutPanel(context.getFont());
            if (!visible()) return;
            GuiGraphics graphics = context.getGraphics();
            Rect viewport = screenBounds(screenX, screenY);
            graphics.flush();
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, depth);
            graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
            DebugBridge.backend().clip(viewport);
            try {
                graphics.fill(viewport.x(), viewport.y(), viewport.right(), viewport.bottom(), maskColor());
                if (background == null) background = ownPart("background", new Panel(OreTheme.FLOATING_BACKGROUND));
                WidgetThemeEntry<WidgetTheme> entry = context.getTheme().getWidgetTheme(OreTheme.FLOATING_BACKGROUND);
                WidgetTheme style = entry == null ? context.getTheme().getPanelTheme().theme() : entry.theme();
                title.color(titleColor).shadow(style.isTextShadow());
                background.drawAt(context, frame.screenBounds(screenX, screenY));
                frame.draw(context, screenX, screenY);
                graphics.flush();
            } finally {
                graphics.disableScissor(); DebugBridge.backend().unclip();
                graphics.pose().popPose();
            }
        }
    }

    @Override public Control hitTop(int rootX, int rootY) {
        if (!hit(rootX, rootY)) return null;
        Control child = targetOpen && frame.hit(rootX, rootY) ? frame.hitTop(rootX, rootY) : null;
        return child == null ? this : child;
    }

    @Override protected boolean pointerDown(int x, int y) {
        if (!hit(x,y)) return false;
        if (targetOpen && !frame.hit(x,y)) close();
        return true;
    }
    @Override protected boolean wheel(int x,int y,double delta,boolean horizontal) {
        return hit(x,y);
    }

    @Override public boolean onFocusKey(int keyCode, int modifiers) {
        if (!targetOpen) return visible();
        if (keyCode != GLFW.GLFW_KEY_ESCAPE) return false;
        close();
        return true;
    }


    @Override public void debugProperties(java.util.List<DebugProperty> out) {
        super.debugProperties(out);
        out.add(DebugProperty.number(title,"fontSize","Text",title::fontSize,0.0001,256));
        java.util.List<DebugProperty> paddingProperties = new java.util.ArrayList<>();
        frame.debugProperties(paddingProperties);
        paddingProperties.stream().filter(p -> java.util.Set.of("top","right","bottom","left").contains(p.key())).forEach(out::add);
    }

}
