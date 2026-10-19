package io.github.luyingyilong.ore;

import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetTheme;
import brachy.modularui.theme.WidgetThemeEntry;
import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.debug.DebugProperty;
import io.github.luyingyilong.ore.debug.Inspectable;
import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.signal.Signal0;
import io.github.luyingyilong.ore.signal.SignalSource0;
import io.github.luyingyilong.ore.signal.Signals;
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
 * Drawer drawer = new Drawer(title).setFontSize(12).setWidth(240).setContent(form);
 * overlay.addChild(drawer);
 * drawer.open();
 * }</pre>
 */
public final class Drawer extends Container {
    public static final int PADDING = 4;
    private static final int CLOSE_SIZE = 20;
    private static final int CLOSE_ICON_SIZE = 7;
    private final LongSupplier clock;
    private final Consumer<OreSounds.Cue> soundPlayer;
    private final Label title = new Label().setHero(true).setOpticalCenter(true).setOverflow(Label.Overflow.ELLIPSIS)
            .setAlignment(Label.Horizontal.LEFT, Label.Vertical.CENTER).setColor(OreTheme.TEXT_WHITE);
    private final Box header = new Box().setOrientation(Box.Orientation.HORIZONTAL).setGap(4);
    private final Button closeButton;
    private final Box footer = new Box().setGap(2);
    private final Scroll body = new Scroll();
    private final Box column = new Box().setGap(4);
    private final Padding frame = new Padding(Padding.Insets.all(PADDING)).setChild(column);
    private final SignalSource0 closed = new SignalSource0(this, "closed");
    private Panel background;
    private int titleColor = OreTheme.TEXT_WHITE;
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

    /**
     * Injectable monotonic clock keeps transition and interaction tests deterministic.
     */
    Drawer(Component title, LongSupplier clock) {
        this(title, clock, OreSounds::play);
    }

    Drawer(Component title, LongSupplier clock, Consumer<OreSounds.Cue> soundPlayer) {
        this.clock = Objects.requireNonNull(clock);
        this.soundPlayer = Objects.requireNonNull(soundPlayer);
        this.title.setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).setSizeFlagsVertical(SizeFlags.SIZE_FILL);
        closeButton = Signals.configure(new Button().setType(Button.Type.TEXT)
                .setIcon(OreIcons.CLOSE, CLOSE_ICON_SIZE, CLOSE_ICON_SIZE), connected -> connected.pressed().connect(connected, this::close));
        closeButton.setMinimum(CLOSE_SIZE, CLOSE_SIZE);
        closeButton.setSizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_CENTER).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        header.addChild(this.title);
        header.addChild(closeButton);
        header.setMinimum(0, CLOSE_SIZE).setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        body.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        footer.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        footer.setVisible(false);
        column.addChild(header);
        column.addChild(body);
        column.addChild(footer);
        updateChildren(java.util.List.of(frame));
        super.setVisible(false);
        setTitle(title);
    }

    @Override
    protected void validateChildren(java.util.List<Control> proposed) {
        if (!isUpdatingChildren())
            throw new IllegalStateException("Drawer has a fixed skeleton; use setContent/addAction");
    }

    @Override
    protected void childrenChanged() {
        int count = (int) footer.getChildren().stream().filter(child -> child.isAlive() && child.getVisible()).count();
        footer.setMinimum(0, count * Button.HEIGHT + Math.max(0, count - 1) * 2);
        footer.setVisible(count != 0);
    }

    public Drawer setTitle(Component text) {
        title.setText(Objects.requireNonNull(text));
        updateHeader();
        return this;
    }

    public boolean getClosable() {
        return closable;
    }

    /**
     * Controls the close button; Escape and mask-click dismissal remain independent.
     */
    public Drawer setClosable(boolean closable) {
        this.closable = closable;
        updateHeader();
        return this;
    }

    private void updateHeader() {
        closeButton.setVisible(closable);
        header.setVisible(closable || !title.getText().getString().isBlank());
        requestLayout();
    }

    public int getTitleColor() {
        return titleColor;
    }

    public Drawer setTitleColor(int color) {
        titleColor = color;
        title.setColor(color);
        return this;
    }

    public float getFontSize() {
        return title.getFontSize();
    }

    public Drawer setFontSize(float fontSize) {
        title.setFontSize(fontSize);
        return this;
    }

    public Drawer setPadding(int top, int right, int bottom, int left) {
        return setPadding(new Padding.Insets(top, right, bottom, left));
    }

    public Padding.Insets getPadding() {
        return frame.getInsets();
    }

    /**
     * Inner spacing around the header, body and footer; defaults to 4px on every side.
     */
    public Drawer setPadding(Padding.Insets insets) {
        frame.setInsets(Objects.requireNonNull(insets));
        return this;
    }

    @Inspectable
    public Placement getPlacement() {
        Placement placement = DebugBridge.value(this, "placement", this.placement);
        return placement;
    }

    public Drawer setPlacement(Placement placement) {
        Objects.requireNonNull(placement);
        if (DebugBridge.previewWrite(this, "placement", placement)) return this;
        this.placement = placement;
        requestLayout();
        return this;
    }

    /**
     * Zero (the default) uses half the GUI viewport; explicit widths are viewport-limited.
     */
    @Inspectable(setter = "setWidth", min = 0, max = 100000)
    public int getRequestedWidth() {
        return DebugBridge.value(this, "requestedWidth", requestedWidth);
    }

    public Drawer setWidth(int width) {
        if (width < 0) throw new IllegalArgumentException("Drawer width cannot be negative");
        if (DebugBridge.previewWrite(this, "requestedWidth", width)) return this;
        requestedWidth = width;
        requestLayout();
        return this;
    }

    public Drawer setContent(Control content) {
        body.setContent(Objects.requireNonNull(content));
        return this;
    }

    public Scroll getBodyScroll() {
        return body;
    }

    public Drawer addAction(Button button) {
        Objects.requireNonNull(button);
        button.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        footer.addChild(button);
        footer.setMinimum(0, footer.getChildren().size() * Button.HEIGHT
                + Math.max(0, footer.getChildren().size() - 1) * 2);
        footer.setVisible(true);
        return this;
    }

    public Drawer clearActions() {
        for (Control child : footer.getChildren()) {
            if (child.getParent() != null) child.getParent().removeChild(child);
            child.queueFree();
        }
        footer.setMinimum(0, 0);
        footer.setVisible(false);
        return this;
    }

    public Signal0 closed() {
        return closed.signal();
    }

    @Override
    public float getContentDepth() {
        return depth;
    }

    public float getDepth() {
        return depth;
    }

    public Drawer setDepth(float depth) {
        this.depth = depth;
        return this;
    }

    public Drawer setAnimationDuration(long milliseconds) {
        if (milliseconds < 0) throw new IllegalArgumentException("Duration cannot be negative");
        advanceAnimation();
        durationMs = milliseconds;
        transitionFrom = progress;
        transitionStart = clock.getAsLong();
        return this;
    }

    public Drawer open() {
        return open(true);
    }

    public Drawer close() {
        return open(false);
    }

    public Drawer open(boolean open) {
        advanceAnimation();
        if (targetOpen == open) return this;
        targetOpen = open;
        if (!open) frame.cancelInteractions();
        soundPlayer.accept(open ? OreSounds.Cue.DRAWER_OPEN : OreSounds.Cue.DRAWER_CLOSE);
        transitionFrom = progress;
        transitionStart = clock.getAsLong();
        animating = true;
        super.setVisible(true);
        requestLayout();
        advanceAnimation();
        return this;
    }

    /**
     * Remains visible and intercepts input until the closing animation finishes.
     */
    @Override
    public void setVisible(boolean visible) {
        open(visible);
    }

    public boolean isOpen() {
        return targetOpen;
    }

    public boolean getAnimating() {
        return animating;
    }

    public Rect getPanelBounds() {
        return frame.getAbsoluteBounds();
    }

    @Override
    public boolean getFocusScope() {
        return true;
    }

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
                super.setVisible(false);
                closed.emit();
            }
        }
    }

    int maskColor() {
        int alpha = (int) Math.round((OreTheme.OVERLAY_COLOR >>> 24) * progress);
        return (alpha << 24) | (OreTheme.OVERLAY_COLOR & 0x00FFFFFF);
    }

    @Override
    public void layout(Rect viewport, Font font) {
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
        int viewportWidth = Math.max(1, getBounds().width());
        int width = Math.min(viewportWidth, Math.max(1,
                requestedWidth == 0 ? viewportWidth / 2 : requestedWidth));
        int visibleWidth = (int) Math.round(width * progress);
        int x = placement == Placement.LEFT ? visibleWidth - width : viewportWidth - visibleWidth;
        // Move the actual layout rectangle, so hit tests, focus and native inputs follow the slide.
        fitChild(frame, new Rect(x, 0, width, getBounds().height()), font);
    }

    @Override
    public void draw(ModularGuiContext context, int screenX, int screenY) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            if (!isAlive()) return;
            DebugBridge.backend().paint(this, context, screenX, screenY);
            if (!getVisible()) return;
            layoutPanel(context.getFont());
            if (!getVisible()) return;
            GuiGraphics graphics = context.getGraphics();
            Rect viewport = getScreenBounds(screenX, screenY);
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
                title.setColor(titleColor).setShadow(style.isTextShadow());
                background.drawAt(context, frame.getScreenBounds(screenX, screenY));
                frame.draw(context, screenX, screenY);
                graphics.flush();
            } finally {
                graphics.disableScissor();
                DebugBridge.backend().unclip();
                graphics.pose().popPose();
            }
        }
    }

    @Override
    public Control hitTop(int rootX, int rootY) {
        if (!hit(rootX, rootY)) return null;
        Control child = targetOpen && frame.hit(rootX, rootY) ? frame.hitTop(rootX, rootY) : null;
        return child == null ? this : child;
    }

    @Override
    protected boolean pointerDown(int x, int y) {
        if (!hit(x, y)) return false;
        if (targetOpen && !frame.hit(x, y)) close();
        return true;
    }

    @Override
    protected boolean wheel(int x, int y, double delta, boolean horizontal) {
        return hit(x, y);
    }

    @Override
    public boolean onFocusKey(int keyCode, int modifiers) {
        if (!targetOpen) return getVisible();
        if (keyCode != GLFW.GLFW_KEY_ESCAPE) return false;
        close();
        return true;
    }

    @Override
    public void debugProperties(java.util.List<DebugProperty> out) {
        super.debugProperties(out);
        out.add(DebugProperty.number(title, "fontSize", "Text", title::getFontSize, 0.0001, 256));
        java.util.List<DebugProperty> paddingProperties = new java.util.ArrayList<>();
        frame.debugProperties(paddingProperties);
        paddingProperties.stream().filter(p -> java.util.Set.of("top", "right", "bottom", "left").contains(p.key())).forEach(out::add);
    }


    public enum Placement {LEFT, RIGHT}

}
