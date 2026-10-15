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
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A tree-owned overlay measured and laid out as header, content, and footer. */
public final class Modal extends Container {
    public static final int PADDING = 8;
    private static final int COMPACT_VERTICAL_PADDING = 2;
    private static final int SECTION_GAP = 4;
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
    private final Box column = new Box().gap(SECTION_GAP);
    private final Padding inset = new Padding(Padding.Insets.all(PADDING)).child(column);
    private final Box header = new Box().orientation(Box.Orientation.HORIZONTAL).gap(HEADER_GAP);
    private final Label titleLabel = new Label().overflow(Label.Overflow.CLIP)
            .align(Label.Horizontal.LEFT, Label.Vertical.CENTER);
    private final CloseControl close = new CloseControl();
    private final Box footer = new Box().orientation(Box.Orientation.VERTICAL).gap(BUTTON_GAP);
    private final List<Button> actions = new ArrayList<>();
    private Control content;
    private Runnable onClose = () -> { };
    private float depth = 200.0F;

    public Modal(Component title) {
        this(0, 0, 0, title);
    }

    public Modal(int width, int height, int verticalOffset, Component title) {
        preferredWidth = Math.max(0, width);
        preferredHeight = Math.max(0, height);
        this.verticalOffset = verticalOffset;
        title(title);
        titleLabel.policy(SizePolicy.EXPAND, SizePolicy.FILL);
        close.minimum(CLOSE_SIZE, CLOSE_SIZE);
        header.minimum(0, CLOSE_SIZE).policy(SizePolicy.FILL, SizePolicy.CONTENT);
        header.add(titleLabel).add(close);
        column.add(header);
        footer.policy(SizePolicy.FILL, SizePolicy.CONTENT);
        footer.visible(false);
        column.add(footer);
        panel.child(inset);
        addChild(panel);
    }

    @Override public Modal minimum(int width, int height) {
        super.minimum(width, height);
        return this;
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
        if (this.content != null) column.removeChild(this.content);
        this.content = Objects.requireNonNull(content);
        content.minimum(content.minimumWidth(), Math.max(content.minimumHeight(), CONTENT_MIN_HEIGHT));
        content.policy(SizePolicy.FILL, SizePolicy.EXPAND);
        column.removeChild(footer);
        column.add(content).add(footer);
        return this;
    }

    public Modal clearActions() {
        for (Button action : actions) footer.removeChild(action);
        actions.clear();
        footer.minimum(0, 0);
        footer.visible(false);
        return this;
    }

    public Modal action(Component label, Runnable onClick) {
        return action(label, onClick, Button.Type.SECONDARY);
    }

    public Modal action(Component label, Runnable onClick, Button.Type type) {
        Button button = new Button(label).type(type).onPress(onClick);
        button.policy(Control.SizePolicy.FILL, Control.SizePolicy.CONTENT);
        actions.add(button);
        footer.addChild(button);
        footer.minimum(0, actionHeight(actions.size()));
        footer.visible(true);
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
        inset.insets(viewport.height() < 110
                ? new Padding.Insets(COMPACT_VERTICAL_PADDING, PADDING, COMPACT_VERTICAL_PADDING, PADDING)
                : Padding.Insets.all(PADDING));
        super.layout(viewport, font);
        Size measured = panel.measureSize(font, new Size(viewport.width(), viewport.height()));
        int width = Math.max(Math.max(preferredWidth, minimumWidth()), measured.width());
        int height = Math.max(Math.max(preferredHeight, minimumHeight()), measured.height());
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

    private static int actionHeight(int count) {
        return count == 0 ? 0 : count * Button.Size.MEDIUM.height() + (count - 1) * BUTTON_GAP;
    }

    private static final class CloseControl extends Control {
        @Override protected void render(ModularGuiContext context, int screenX, int screenY) {
            Rect area = screenBounds(screenX, screenY);
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
