package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.signal.*;

import io.github.luyingyilong.ore.debug.DebugBridge;

import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetTheme;
import brachy.modularui.theme.WidgetThemeEntry;
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

/** A tree-owned overlay measured and laid out as header, content, and footer. */
public final class Dialog extends Container {
    public static final int PADDING = 8;
    private static final int BODY_HORIZONTAL_INSET = PADDING;
    private static final int BODY_BOTTOM_INSET = 5;
    private static final int COMPACT_VERTICAL_PADDING = 2;
    private static final int SECTION_SPACING = 4;
    private static final int BUTTON_GAP = 2;
    private static final int HEADER_GAP = 4;
    private static final int CONTENT_MIN_HEIGHT = 24;
    private static final int CLOSE_SIZE = 18;
    private static final int CLOSE_ICON_SIZE = 8;

    private final int preferredWidth;
    private final int preferredHeight;
    private final int verticalOffset;
    private final Panel panel = new Panel(OreTheme.MODAL);
    private final Box column = new Box();
    private final Box header = new Box().setOrientation(Box.Orientation.HORIZONTAL).setGap(HEADER_GAP);
    private final Padding headerInset = new Padding(new Padding.Insets(PADDING, PADDING, SECTION_SPACING, PADDING))
            .setChild(header);
    private final Label titleLabel = new Label().setHero(true).setOpticalCenter(true).setOverflow(Label.Overflow.CLIP)
            .setAlignment(Label.Horizontal.LEFT, Label.Vertical.CENTER);
    private final Button close = new Button().setSize(Button.Size.SMALL).setType(Button.Type.TEXT)
            .setIcon(OreIcons.SMALL_CLOSE, CLOSE_ICON_SIZE, CLOSE_ICON_SIZE);
    private final Box footer = new Box().setOrientation(Box.Orientation.VERTICAL).setGap(BUTTON_GAP);
    private final Padding footerInset = new Padding(new Padding.Insets(SECTION_SPACING, PADDING, PADDING, PADDING))
            .setChild(footer);
    private final Padding bodyInset = new Padding(new Padding.Insets(PADDING, BODY_HORIZONTAL_INSET,
            PADDING, BODY_HORIZONTAL_INSET));
    private final List<Button> actions = new ArrayList<>();
    private Control content;
    private final SignalSource0 closeRequested = new SignalSource0(this, "closeRequested");
    private float depth = 200.0F;

    public Dialog(Component title) {
        this(0, 0, 0, title);
    }

    public Dialog(int width, int height, int verticalOffset, Component title) {
        preferredWidth = Math.max(0, width);
        preferredHeight = Math.max(0, height);
        this.verticalOffset = verticalOffset;
        setTitle(title);
        titleLabel.setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).setSizeFlagsVertical(SizeFlags.SIZE_FILL);
        close.setMinimum(CLOSE_SIZE, CLOSE_SIZE);
        close.setSizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_CENTER).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        close.pressed().connect(close, () -> closeRequested.emit());
        header.setMinimum(0, CLOSE_SIZE).setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        header.addChild(titleLabel);
        header.addChild(close);
        headerInset.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        column.addChild(headerInset);
        footer.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        footerInset.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        footerInset.setVisible(false);
        column.addChild(footerInset);
        panel.setChild(column);
        updateChildren(List.of(panel));
    }

    @Override public Dialog setMinimum(int width, int height) {
        super.setMinimum(width, height);
        return this;
    }

    @Override protected void validateChildren(List<Control> proposed) {
        if (!isUpdatingChildren()) throw new IllegalStateException("Dialog has a fixed skeleton; use setContent/addAction");
    }

    @Override protected void childrenChanged() {
        content = bodyInset.getChildCount() == 0 ? null : bodyInset.getChild(0);
        actions.clear();
        for (Control child : footer.getChildren()) if (child instanceof Button button && button.isAlive()) actions.add(button);
        footer.setMinimum(0, actionHeight(actions.size()));
        footerInset.setVisible(!actions.isEmpty());
    }

    public Dialog setTitle(Component title) {
        titleLabel.setText(Objects.requireNonNull(title));
        return this;
    }

    public Dialog setFontSize(float fontSize) { titleLabel.setFontSize(fontSize); return this; }
    public float getFontSize() { return titleLabel.getFontSize(); }

    public Signal0 closeRequested() { return closeRequested.signal(); }

    @Override public float getContentDepth() { return depth; }

    public Dialog setDepth(float depth) {
        this.depth = depth;
        return this;
    }

    @Override public boolean getFocusScope() { return true; }

    @Override public boolean onFocusKey(int keyCode, int modifiers) {
        if (keyCode != GLFW.GLFW_KEY_ESCAPE) return false;
        closeRequested.emit();
        return true;
    }

    public Dialog setContent(Control content) {
        if (this.content == content) return this;
        Objects.requireNonNull(content);
        bodyInset.setChild(content);
        content.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        if (bodyInset.getParent() == null) {
            bodyInset.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
            column.removeChild(footerInset);
            column.addChild(bodyInset);
            column.addChild(footerInset);
        }
        return this;
    }

    public Dialog clearActions() {
        for (Button action : List.copyOf(actions)) { if (action.getParent() != null) action.getParent().removeChild(action); action.queueFree(); }
        actions.clear();
        footer.setMinimum(0, 0);
        footerInset.setVisible(false);
        return this;
    }

    public Dialog addAction(Button button) {
        Objects.requireNonNull(button);
        button.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        footer.addChild(button);
        footer.setMinimum(0, actionHeight(actions.size()));
        footerInset.setVisible(true);
        return this;
    }

    public Rect getPanelBounds() {
        return panel.getAbsoluteBounds();
    }

    public Rect getContentBounds() {
        return content == null ? new Rect(0, 0, 0, 0)
                : content.getAbsoluteBounds();
    }

    public static Rect fittedBounds(
            int preferredWidth, int preferredHeight, int verticalOffset, int viewportWidth, int viewportHeight) {
        int horizontalMargin = Math.min(6, Math.max(1, viewportWidth / 32));
        int verticalMargin = viewportHeight < 110 ? 1 : 2;
        int width = Math.max(1, Math.min(preferredWidth,
                Math.max(1, viewportWidth - 2 * horizontalMargin)));
        int height = Math.max(1, Math.min(preferredHeight,
                Math.max(1, viewportHeight - 2 * verticalMargin)));
        int x = (viewportWidth - width) / 2;
        int y = (viewportHeight - height) / 2 + verticalOffset;
        y = Math.max(0, Math.min(Math.max(0, viewportHeight - height), y));
        return new Rect(x, y, width, height);
    }

    @Override public void layout(Rect viewport, Font font) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            int verticalPadding = viewport.height() < 110 ? COMPACT_VERTICAL_PADDING : PADDING;
            headerInset.setInsets(new Padding.Insets(verticalPadding, PADDING, SECTION_SPACING, PADDING));
            footerInset.setInsets(new Padding.Insets(SECTION_SPACING, PADDING, verticalPadding, PADDING));
            super.layout(viewport, font);
            Size measured = panel.measureSize(font, new Size(viewport.width(), viewport.height()));
            int width = Math.max(Math.max(preferredWidth, getMinimumWidth()), measured.width());
            int intrinsicContentHeight = content == null ? 0
                    : content.measureSize(font, new Size(viewport.width(), viewport.height())).height();
            int fallbackBodyHeight = content != null && intrinsicContentHeight == 0 ? CONTENT_MIN_HEIGHT : 0;
            int height = Math.max(Math.max(preferredHeight, getMinimumHeight()), measured.height() + fallbackBodyHeight);
            fitChild(panel, fittedBounds(width, height, verticalOffset,
                    viewport.width(), viewport.height()), font);
        }
    }

    @Override protected void render(ModularGuiContext context, int screenX, int screenY) {
        Rect area = getScreenBounds(screenX, screenY);
        context.getGraphics().fill(area.x(), area.y(), area.right(), area.bottom(), OreTheme.OVERLAY_COLOR);
        WidgetThemeEntry<WidgetTheme> entry = context.getTheme().getWidgetTheme(OreTheme.MODAL);
        WidgetTheme style = entry == null ? context.getTheme().getPanelTheme().theme() : entry.theme();
        titleLabel.setColor(style.getTextColor()).setShadow(style.isTextShadow());
    }

    @Override public void draw(ModularGuiContext context, int screenX, int screenY) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            if (!isAlive()) return;
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

    /** Returns true for all clicks while open; the topmost dialog owns its entire overlay. */
    @Override protected boolean pointerDown(int rootX, int rootY) {
        if (!getVisible()) return false;
        if (!panel.hit(rootX, rootY)) {
            OreSounds.play(OreSounds.Cue.CLICK);
            closeRequested.emit();
            return true;
        }
        return true;
    }



    private static int actionHeight(int count) {
        return count == 0 ? 0 : count * Button.Size.MEDIUM.getHeight() + (count - 1) * BUTTON_GAP;
    }

}
