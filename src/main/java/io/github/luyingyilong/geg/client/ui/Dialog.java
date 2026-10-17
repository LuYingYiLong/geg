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
import io.github.luyingyilong.geg.client.ui.layout.Size;
import io.github.luyingyilong.geg.client.ui.layout.SizeFlags;
import io.github.luyingyilong.geg.client.ui.theme.OreIcons;
import io.github.luyingyilong.geg.client.ui.theme.OreSounds;
import io.github.luyingyilong.geg.client.ui.theme.OreTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

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
    private static final int OVERLAY_COLOR = 0xB8000000;

    private final int preferredWidth;
    private final int preferredHeight;
    private final int verticalOffset;
    private final Panel panel = new Panel(OreTheme.MODAL);
    private final Box column = new Box();
    private final Box header = new Box().orientation(Box.Orientation.HORIZONTAL).gap(HEADER_GAP);
    private final Padding headerInset = new Padding(new Padding.Insets(PADDING, PADDING, SECTION_SPACING, PADDING))
            .child(header);
    private final Label titleLabel = new Label().hero(true).overflow(Label.Overflow.CLIP)
            .align(Label.Horizontal.LEFT, Label.Vertical.CENTER);
    private final Button close = new Button().size(Button.Size.SMALL).type(Button.Type.TEXT)
            .icon(OreIcons.SMALL_CLOSE, CLOSE_ICON_SIZE, CLOSE_ICON_SIZE);
    private final Box footer = new Box().orientation(Box.Orientation.VERTICAL).gap(BUTTON_GAP);
    private final Padding footerInset = new Padding(new Padding.Insets(SECTION_SPACING, PADDING, PADDING, PADDING))
            .child(footer);
    private final Padding bodyInset = new Padding(new Padding.Insets(PADDING, BODY_HORIZONTAL_INSET,
            PADDING, BODY_HORIZONTAL_INSET));
    private final List<Button> actions = new ArrayList<>();
    private Control content;
    private Runnable onClose = () -> { };
    private float depth = 200.0F;

    public Dialog(Component title) {
        this(0, 0, 0, title);
    }

    public Dialog(int width, int height, int verticalOffset, Component title) {
        preferredWidth = Math.max(0, width);
        preferredHeight = Math.max(0, height);
        this.verticalOffset = verticalOffset;
        title(title);
        titleLabel.sizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).sizeFlagsVertical(SizeFlags.SIZE_FILL);
        close.minimum(CLOSE_SIZE, CLOSE_SIZE);
        close.sizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_CENTER).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        close.onPress(() -> onClose.run());
        header.minimum(0, CLOSE_SIZE).sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        header.add(titleLabel).add(close);
        headerInset.sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        column.add(headerInset);
        footer.sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        footerInset.sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        footerInset.visible(false);
        column.add(footerInset);
        panel.child(column);
        addChild(panel);
    }

    @Override public Dialog minimum(int width, int height) {
        super.minimum(width, height);
        return this;
    }

    public Dialog title(Component title) {
        titleLabel.text(Objects.requireNonNull(title));
        return this;
    }

    public Dialog fontSize(float fontSize) { titleLabel.fontSize(fontSize); return this; }
    public float fontSize() { return titleLabel.fontSize(); }

    public Dialog onClose(Runnable onClose) {
        this.onClose = Objects.requireNonNull(onClose);
        return this;
    }

    @Override public float contentDepth() { return depth; }

    public Dialog depth(float depth) {
        this.depth = depth;
        return this;
    }

    @Override public boolean focusScope() { return true; }

    public Dialog content(Control content) {
        this.content = Objects.requireNonNull(content);
        content.sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        bodyInset.child(content);
        if (bodyInset.parent() == null) {
            bodyInset.sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
            column.removeChild(footerInset);
            column.add(bodyInset).add(footerInset);
        }
        return this;
    }

    public Dialog clearActions() {
        for (Button action : actions) footer.removeChild(action);
        actions.clear();
        footer.minimum(0, 0);
        footerInset.visible(false);
        return this;
    }

    public Dialog action(Component label, Runnable onClick) {
        return action(label, onClick, Button.Type.SECONDARY);
    }

    public Dialog action(Component label, Runnable onClick, Button.Type type) {
        Button button = new Button(label).type(type).onPress(onClick);
        button.sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        actions.add(button);
        footer.addChild(button);
        footer.minimum(0, actionHeight(actions.size()));
        footerInset.visible(true);
        return this;
    }

    public Rect panelBounds() {
        return panel.absoluteBounds();
    }

    public Rect contentBounds() {
        return content == null ? new Rect(0, 0, 0, 0)
                : content.absoluteBounds();
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
        int verticalPadding = viewport.height() < 110 ? COMPACT_VERTICAL_PADDING : PADDING;
        headerInset.insets(new Padding.Insets(verticalPadding, PADDING, SECTION_SPACING, PADDING));
        footerInset.insets(new Padding.Insets(SECTION_SPACING, PADDING, verticalPadding, PADDING));
        super.layout(viewport, font);
        Size measured = panel.measureSize(font, new Size(viewport.width(), viewport.height()));
        int width = Math.max(Math.max(preferredWidth, minimumWidth()), measured.width());
        int intrinsicContentHeight = content == null ? 0
                : content.measureSize(font, new Size(viewport.width(), viewport.height())).height();
        int fallbackBodyHeight = content != null && intrinsicContentHeight == 0 ? CONTENT_MIN_HEIGHT : 0;
        int height = Math.max(Math.max(preferredHeight, minimumHeight()), measured.height() + fallbackBodyHeight);
        fitChild(panel, fittedBounds(width, height, verticalOffset,
                viewport.width(), viewport.height()), font);
    }

    @Override protected void render(ModularGuiContext context, int screenX, int screenY) {
        Rect area = screenBounds(screenX, screenY);
        context.getGraphics().fill(area.x(), area.y(), area.right(), area.bottom(), OVERLAY_COLOR);
        WidgetThemeEntry<WidgetTheme> entry = context.getTheme().getWidgetTheme(OreTheme.MODAL);
        WidgetTheme style = entry == null ? context.getTheme().getPanelTheme().theme() : entry.theme();
        titleLabel.color(style.getTextColor()).shadow(style.isTextShadow());
    }

    @Override public void draw(ModularGuiContext context, int screenX, int screenY) {
        if (!visible()) return;
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

    /** Returns true for all clicks while open; the topmost dialog owns its entire overlay. */
    public boolean clickAt(int rootX, int rootY) {
        if (!visible()) return false;
        if (!panel.hit(rootX, rootY)) {
            OreSounds.play(OreSounds.Cue.CLICK);
            onClose.run();
            return true;
        }
        if (close.pressAt(rootX, rootY)) return true;
        for (Button action : actions) if (action.pressAt(rootX, rootY)) return true;
        return true;
    }

    @Override protected boolean onPointerPressed(int rootX, int rootY) {
        return clickAt(rootX, rootY);
    }

    private static int actionHeight(int count) {
        return count == 0 ? 0 : count * Button.Size.MEDIUM.height() + (count - 1) * BUTTON_GAP;
    }

}
