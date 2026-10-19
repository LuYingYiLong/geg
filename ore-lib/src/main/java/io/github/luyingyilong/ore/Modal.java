package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.signal.*;

import io.github.luyingyilong.ore.debug.DebugBridge;

import io.github.luyingyilong.ore.render.DrawContext;
import brachy.modularui.theme.WidgetTheme;
import brachy.modularui.theme.WidgetThemeEntry;
import brachy.modularui.theme.WidgetThemeKey;
import io.github.luyingyilong.ore.layout.Box;
import io.github.luyingyilong.ore.layout.Container;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.Padding;
import io.github.luyingyilong.ore.layout.Panel;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.layout.Size;
import io.github.luyingyilong.ore.layout.SizeFlags;
import io.github.luyingyilong.ore.theme.OreIcons;
import io.github.luyingyilong.ore.theme.OreSounds;
import io.github.luyingyilong.ore.theme.OreTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A positioned overlay with a one-pixel frame and themed header and footer. */
public final class Modal extends Container {
    public static final int HEADER_HEIGHT = 22;
    private static final int FRAME_INSET = 1;
    private static final int CLOSE_HIT_WIDTH = 20;
    private static final int CLOSE_ICON_SIZE = 7;
    private static final int BUTTON_GAP = 2;
    private static final int BODY_INSET = 8;
    private static final int FOOTER_INSET = 8;

    private final Panel background;
    private final Box column = new Box();
    private final Panel header;
    private final Box headerRow = new Box().setOrientation(Box.Orientation.HORIZONTAL).setGap(4);
    private final Control headerSpacer = new Control();
    private final Label titleLabel = new Label().setOpticalCenter(true).setOverflow(Label.Overflow.ELLIPSIS)
            .setAlignment(Label.Horizontal.CENTER, Label.Vertical.CENTER);
    private final Button close = new Button().setType(Button.Type.TEXT)
            .setIcon(OreIcons.CLOSE, CLOSE_ICON_SIZE, CLOSE_ICON_SIZE);
    private final Padding bodyInset = new Padding(Padding.Insets.all(BODY_INSET));
    private final Panel footer;
    private final Box footerButtons = new Box().setGap(BUTTON_GAP);
    private final Padding footerInset = new Padding(Padding.Insets.all(FOOTER_INSET)).setChild(footerButtons);
    private final List<Button> actions = new ArrayList<>();
    private Control content;
    private final SignalSource0 closeRequested = new SignalSource0(this, "closeRequested");
    private int requestedWidth;
    private int requestedHeight;
    private Integer customX;
    private Integer customY;
    private float depth = 200.0F;

    public Modal(Component title) {
        this(title, OreTheme.FLOATING_BACKGROUND, OreTheme.FLOATING_HEADER, OreTheme.BAR);
    }

    Modal(Component title, WidgetThemeKey<WidgetTheme> backgroundTheme,
          WidgetThemeKey<WidgetTheme> headerTheme, WidgetThemeKey<WidgetTheme> footerTheme) {
        background = new Panel(backgroundTheme);
        header = new Panel(headerTheme);
        footer = new Panel(footerTheme);
        setTitle(title);
        titleLabel.setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).setSizeFlagsVertical(SizeFlags.SIZE_FILL);
        headerSpacer.setMinimum(CLOSE_HIT_WIDTH, CLOSE_HIT_WIDTH);
        close.setMinimum(CLOSE_HIT_WIDTH, CLOSE_HIT_WIDTH);
        close.setSizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_CENTER).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        close.pressed().connect(close, () -> closeRequested.emit());
        headerRow.addChild(headerSpacer);
        headerRow.addChild(titleLabel);
        headerRow.addChild(close);
        header.setChild(new Padding(new Padding.Insets(0, 1, 0, 1)).setChild(headerRow));
        header.setMinimum(0, HEADER_HEIGHT).setPreferred(0, HEADER_HEIGHT)
                .setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        bodyInset.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        bodyInset.setMinimum(0, BODY_INSET * 2);
        footerButtons.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        footer.setChild(footerInset);
        footer.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        footer.setVisible(false);
        column.addChild(header);
        column.addChild(bodyInset);
        column.addChild(footer);
        background.setChild(new Padding(Padding.Insets.all(FRAME_INSET)).setChild(column));
        updateChildren(List.of(background));
    }

    @Override protected void validateChildren(List<Control> proposed) {
        if (!isUpdatingChildren()) throw new IllegalStateException("Modal has a fixed skeleton; use setContent/addAction");
    }

    @Override protected void childrenChanged() {
        content = bodyInset.getChildCount() == 0 ? null : bodyInset.getChild(0);
        actions.clear();
        for (Control child : footerButtons.getChildren()) if (child instanceof Button button && button.isAlive()) actions.add(button);
        footer.setMinimum(0, actions.isEmpty() ? 0 : FOOTER_INSET * 2 + actions.size() * Button.HEIGHT + (actions.size() - 1) * BUTTON_GAP);
        footer.setVisible(!actions.isEmpty());
    }

    public Modal setSize(int width, int height) {
        int nextWidth = Math.max(0, width);
        int nextHeight = Math.max(0, height);
        if (requestedWidth != nextWidth || requestedHeight != nextHeight) {
            requestedWidth = nextWidth;
            requestedHeight = nextHeight;
            requestLayout();
        }
        return this;
    }

    /** Coordinates are relative to the GUI viewport, not the operating-system window. */
    public Modal setPosition(int x, int y) {
        if (!Objects.equals(customX, x) || !Objects.equals(customY, y)) {
            customX = x;
            customY = y;
            requestLayout();
        }
        return this;
    }

    public Modal centered() {
        if (customX != null || customY != null) {
            customX = null;
            customY = null;
            requestLayout();
        }
        return this;
    }

    public Modal setTitle(Component title) {
        titleLabel.setText(Objects.requireNonNull(title));
        return this;
    }

    public Modal setFontSize(float fontSize) { titleLabel.setFontSize(fontSize); return this; }
    public float getFontSize() { return titleLabel.getFontSize(); }

    public Signal0 closeRequested() { return closeRequested.signal(); }

    @Override public float getContentDepth() { return depth; }

    public Modal setDepth(float depth) {
        this.depth = depth;
        return this;
    }

    public Modal setContent(Control content) {
        if (this.content == content) return this;
        Objects.requireNonNull(content);
        bodyInset.setChild(content);
        content.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        return this;
    }

    public Modal clearActions() {
        for (Button action : List.copyOf(actions)) { if (action.getParent() != null) action.getParent().removeChild(action); action.queueFree(); }
        actions.clear();
        footer.setVisible(false);
        footer.setMinimum(0, 0);
        return this;
    }

    public Modal addAction(Button button) {
        Objects.requireNonNull(button);
        button.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        footerButtons.addChild(button);
        footer.setVisible(true);
        footer.setMinimum(0, FOOTER_INSET * 2 + actions.size() * Button.HEIGHT
                + (actions.size() - 1) * BUTTON_GAP);
        return this;
    }

    public Rect getPanelBounds() {
        return background.getAbsoluteBounds();
    }

    @Override public boolean getFocusScope() { return true; }

    @Override public boolean onFocusKey(int keyCode, int modifiers) {
        if (keyCode != GLFW.GLFW_KEY_ESCAPE) return false;
        closeRequested.emit();
        return true;
    }

    public Rect getContentBounds() {
        return content == null ? new Rect(0, 0, 0, 0) : content.getAbsoluteBounds();
    }

    public static Rect centeredBounds(int width, int height, int viewportWidth, int viewportHeight) {
        return fittedBounds(width, height, null, null, viewportWidth, viewportHeight);
    }

    public static Rect positionedBounds(int width, int height, int x, int y,
                                        int viewportWidth, int viewportHeight) {
        return fittedBounds(width, height, x, y, viewportWidth, viewportHeight);
    }

    private static Rect fittedBounds(int width, int height, Integer x, Integer y,
                                     int viewportWidth, int viewportHeight) {
        int fittedWidth = Math.max(1, Math.min(width, Math.max(1, viewportWidth)));
        int fittedHeight = Math.max(1, Math.min(height, Math.max(1, viewportHeight)));
        int left = x == null ? (viewportWidth - fittedWidth) / 2 : x;
        int top = y == null ? (viewportHeight - fittedHeight) / 2 : y;
        left = Math.max(0, Math.min(Math.max(0, viewportWidth - fittedWidth), left));
        top = Math.max(0, Math.min(Math.max(0, viewportHeight - fittedHeight), top));
        return new Rect(left, top, fittedWidth, fittedHeight);
    }

    @Override public void layout(Rect viewport, Font font) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            super.layout(viewport, font);
            int desiredWidth = requestedWidth > 0 ? requestedWidth
                    : Math.max(getMinimumWidth(), background.measureSize(font,
                            new Size(viewport.width(), viewport.height())).width());
            int fittedWidth = Math.max(1, Math.min(desiredWidth, Math.max(1, viewport.width())));
            int desiredHeight = requestedHeight > 0 ? requestedHeight
                    : Math.max(getMinimumHeight(), background.measureSize(font,
                            new Size(fittedWidth, viewport.height())).height());
            fitChild(background, fittedBounds(desiredWidth, desiredHeight, customX, customY,
                    viewport.width(), viewport.height()), font);
        }
    }

    @Override protected void render(DrawContext context, int screenX, int screenY) {
        Rect area = getScreenBounds(screenX, screenY);
        context.getGraphics().fill(area.x(), area.y(), area.right(), area.bottom(), OreTheme.OVERLAY_COLOR);
        WidgetThemeEntry<WidgetTheme> entry = context.getTheme().getWidgetTheme(OreTheme.FLOATING_HEADER);
        WidgetTheme style = entry == null ? context.getTheme().getPanelTheme().theme() : entry.theme();
        titleLabel.setColor(style.getTextColor()).setShadow(style.isTextShadow());
    }

    @Override public void draw(DrawContext context, int screenX, int screenY) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            DebugBridge.backend().paint(this, context, screenX, screenY);
            if (!getVisible()) return;
            GuiGraphics graphics = context.getGraphics();
            graphics.flush();
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, depth);
            try {
                super.draw(context, screenX, screenY);
                graphics.flush();
            } finally {
                graphics.pose().popPose();
            }
        }
    }

    /** The topmost modal consumes clicks both inside and outside its panel. */
    @Override protected boolean pointerDown(int rootX, int rootY) {
        if (!getVisible()) return false;
        if (!background.hit(rootX, rootY)) {
            OreSounds.play(OreSounds.Cue.CLICK);
            closeRequested.emit();
            return true;
        }
        return true;
    }



}
