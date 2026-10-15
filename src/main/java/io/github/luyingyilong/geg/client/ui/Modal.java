package io.github.luyingyilong.geg.client.ui;

import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetTheme;
import brachy.modularui.theme.WidgetThemeEntry;
import io.github.luyingyilong.geg.client.ui.layout.Container;
import io.github.luyingyilong.geg.client.ui.layout.Control;
import io.github.luyingyilong.geg.client.ui.layout.Panel;
import io.github.luyingyilong.geg.client.ui.layout.Size;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A tree-owned overlay with a fixed header and footer and a replaceable content control. */
public final class Modal extends Container {
    public static final int PADDING = 8;
    private static final int HEADER_HEIGHT = 32;
    private static final int FOOTER_HEIGHT = 36;
    private static final int BUTTON_GAP = 8;
    private static final int CLOSE_SIZE = 18;
    private static final int CLOSE_ICON_SIZE = 8;
    private static final int OVERLAY_COLOR = 0xB8000000;

    private final int preferredWidth;
    private final int preferredHeight;
    private final int verticalOffset;
    private final Panel panel = new Panel(OreTheme.MODAL);
    private final Surface surface = new Surface();
    private final Label titleLabel = new Label().overflow(Label.Overflow.CLIP)
            .align(Label.Horizontal.LEFT, Label.Vertical.CENTER);
    private final CloseControl close = new CloseControl();
    private final List<Button> actions = new ArrayList<>();
    private Control content;
    private Runnable onClose = () -> { };
    private float depth = 200.0F;

    public Modal(int width, int height, int verticalOffset, Component title) {
        preferredWidth = Math.max(1, width);
        preferredHeight = Math.max(1, height);
        this.verticalOffset = verticalOffset;
        title(title);
        panel.child(surface);
        addChild(panel);
        surface.addChild(titleLabel);
        surface.addChild(close);
    }

    public Modal title(Component title) {
        titleLabel.text(Objects.requireNonNull(title));
        return this;
    }

    public Modal onClose(Runnable onClose) {
        this.onClose = Objects.requireNonNull(onClose);
        return this;
    }

    public Modal depth(float depth) {
        this.depth = depth;
        return this;
    }

    public Modal content(Control content) {
        if (this.content != null) surface.removeChild(this.content);
        this.content = Objects.requireNonNull(content);
        surface.addChild(content);
        requestLayout();
        return this;
    }

    public Modal clearActions() {
        for (Button action : actions) surface.removeChild(action);
        actions.clear();
        requestLayout();
        return this;
    }

    public Modal action(Component label, Runnable onClick) {
        Button button = new Button(label).onPress(onClick);
        actions.add(button);
        surface.addChild(button);
        requestLayout();
        return this;
    }

    public io.github.luyingyilong.geg.client.ui.layout.Rect panelBounds() {
        return panel.absoluteBounds();
    }

    public io.github.luyingyilong.geg.client.ui.layout.Rect contentBounds() {
        return content == null ? new io.github.luyingyilong.geg.client.ui.layout.Rect(0, 0, 0, 0)
                : content.absoluteBounds();
    }

    public static io.github.luyingyilong.geg.client.ui.layout.Rect fittedBounds(
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
        return new io.github.luyingyilong.geg.client.ui.layout.Rect(x, y, width, height);
    }

    public static int headerHeight(int panelHeight) {
        return panelHeight < 110 ? 20 : HEADER_HEIGHT;
    }

    public static int footerHeight(int panelHeight) {
        return panelHeight < 110 ? 22 : FOOTER_HEIGHT;
    }

    @Override public void layout(io.github.luyingyilong.geg.client.ui.layout.Rect viewport, Font font) {
        super.layout(viewport, font);
        fitChild(panel, fittedBounds(preferredWidth, preferredHeight, verticalOffset,
                viewport.width(), viewport.height()), font);
    }

    @Override protected void render(ModularGuiContext context, int screenX, int screenY) {
        io.github.luyingyilong.geg.client.ui.layout.Rect area = screenBounds(screenX, screenY);
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

    /** Returns true for all clicks while open; the topmost modal owns its entire overlay. */
    public boolean clickAt(int rootX, int rootY) {
        if (!visible()) return false;
        if (!panel.hit(rootX, rootY) || close.hit(rootX, rootY)) {
            onClose.run();
            return true;
        }
        for (Button action : actions) if (action.pressAt(rootX, rootY)) return true;
        return true;
    }

    @Override protected boolean onPointerPressed(int rootX, int rootY) {
        return clickAt(rootX, rootY);
    }

    private final class Surface extends Container {
        @Override public Size measureSize(Font font, Size available) {
            return available;
        }

        @Override public void layout(io.github.luyingyilong.geg.client.ui.layout.Rect bounds, Font font) {
            super.layout(bounds, font);
            int width = bounds.width();
            int height = bounds.height();
            int headerHeight = Modal.headerHeight(height);
            int footerHeight = Modal.footerHeight(height);
            int topInset = headerHeight < HEADER_HEIGHT ? 1 : PADDING;
            fitChild(titleLabel, new io.github.luyingyilong.geg.client.ui.layout.Rect(PADDING, topInset,
                    Math.max(0, width - 3 * PADDING - CLOSE_SIZE), CLOSE_SIZE), font);
            fitChild(close, new io.github.luyingyilong.geg.client.ui.layout.Rect(
                    Math.max(0, width - PADDING - CLOSE_SIZE), topInset, CLOSE_SIZE, CLOSE_SIZE), font);
            if (content != null) fitChild(content, new io.github.luyingyilong.geg.client.ui.layout.Rect(
                    PADDING, headerHeight, Math.max(1, width - 2 * PADDING),
                    Math.max(0, height - headerHeight - footerHeight)), font);
            int availableWidth = Math.max(1, width - 2 * PADDING);
            int count = actions.size();
            int gap = width < 160 ? 4 : BUTTON_GAP;
            int buttonWidth = count == 0 ? 0 : Math.max(1,
                    Math.min(132, (availableWidth - (count - 1) * gap) / count));
            int space = count <= 1 ? 0 : (availableWidth - count * buttonWidth) / (count - 1);
            int startX = count == 1 ? width - PADDING - buttonWidth : PADDING;
            int buttonY = Math.max(0, height - (footerHeight < FOOTER_HEIGHT ? 1 : PADDING)
                    - Button.Size.MEDIUM.height());
            for (int index = 0; index < count; index++)
                fitChild(actions.get(index), new io.github.luyingyilong.geg.client.ui.layout.Rect(
                        startX + index * (buttonWidth + space), buttonY,
                        buttonWidth, Button.Size.MEDIUM.height()), font);
        }
    }

    private static final class CloseControl extends Control {
        @Override protected void render(ModularGuiContext context, int screenX, int screenY) {
            io.github.luyingyilong.geg.client.ui.layout.Rect area = screenBounds(screenX, screenY);
            GuiGraphics graphics = context.getGraphics();
            if (area.contains(context.getAbsMouseX(), context.getAbsMouseY()))
                graphics.fill(area.x(), area.y(), area.right(), area.bottom(), 0x66303030);
            int x = area.x() + (area.width() - CLOSE_ICON_SIZE) / 2;
            int y = area.y() + (area.height() - CLOSE_ICON_SIZE) / 2;
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            graphics.blit(OreIcons.SMALL_CLOSE, x, y, 0.0F, 0.0F,
                    CLOSE_ICON_SIZE, CLOSE_ICON_SIZE, CLOSE_ICON_SIZE, CLOSE_ICON_SIZE);
        }
    }
}
