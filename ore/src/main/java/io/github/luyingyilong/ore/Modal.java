package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.debug.DebugBridge;

import brachy.modularui.screen.viewport.ModularGuiContext;
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
    private final Box headerRow = new Box().orientation(Box.Orientation.HORIZONTAL).gap(4);
    private final Control headerSpacer = new Control();
    private final Label titleLabel = new Label().opticalCenter(true).overflow(Label.Overflow.ELLIPSIS)
            .align(Label.Horizontal.CENTER, Label.Vertical.CENTER);
    private final Button close = new Button().type(Button.Type.TEXT)
            .icon(OreIcons.CLOSE, CLOSE_ICON_SIZE, CLOSE_ICON_SIZE);
    private final Padding bodyInset = new Padding(Padding.Insets.all(BODY_INSET));
    private final Panel footer;
    private final Box footerButtons = new Box().gap(BUTTON_GAP);
    private final Padding footerInset = new Padding(Padding.Insets.all(FOOTER_INSET)).child(footerButtons);
    private final List<Button> actions = new ArrayList<>();
    private Control content;
    private Runnable onClose = () -> { };
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
        title(title);
        titleLabel.sizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).sizeFlagsVertical(SizeFlags.SIZE_FILL);
        headerSpacer.minimum(CLOSE_HIT_WIDTH, CLOSE_HIT_WIDTH);
        close.minimum(CLOSE_HIT_WIDTH, CLOSE_HIT_WIDTH);
        close.sizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_CENTER).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        close.onPress(() -> onClose.run());
        headerRow.add(headerSpacer).add(titleLabel).add(close);
        header.child(new Padding(new Padding.Insets(0, 1, 0, 1)).child(headerRow));
        header.minimum(0, HEADER_HEIGHT).preferred(0, HEADER_HEIGHT)
                .sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        bodyInset.sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        bodyInset.minimum(0, BODY_INSET * 2);
        footerButtons.sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        footer.child(footerInset);
        footer.sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        footer.visible(false);
        column.add(header).add(bodyInset).add(footer);
        background.child(new Padding(Padding.Insets.all(FRAME_INSET)).child(column));
        addChild(background);
    }

    public Modal size(int width, int height) {
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
    public Modal position(int x, int y) {
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

    public Modal title(Component title) {
        titleLabel.text(Objects.requireNonNull(title));
        return this;
    }

    public Modal fontSize(float fontSize) { titleLabel.fontSize(fontSize); return this; }
    public float fontSize() { return titleLabel.fontSize(); }

    public Modal onClose(Runnable onClose) {
        this.onClose = Objects.requireNonNull(onClose);
        return this;
    }

    @Override public float contentDepth() { return depth; }

    public Modal depth(float depth) {
        this.depth = depth;
        return this;
    }

    public Modal content(Control content) {
        this.content = Objects.requireNonNull(content);
        content.sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        bodyInset.child(content);
        return this;
    }

    public Modal clearActions() {
        for (Button action : actions) footerButtons.removeChild(action);
        actions.clear();
        footer.visible(false);
        footer.minimum(0, 0);
        return this;
    }

    public Modal action(Component label, Runnable onClick) {
        return action(label, onClick, Button.Type.SECONDARY);
    }

    public Modal action(Component label, Runnable onClick, Button.Type type) {
        Button button = new Button(label).type(type).onPress(onClick);
        button.sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        actions.add(button);
        footerButtons.add(button);
        footer.visible(true);
        footer.minimum(0, FOOTER_INSET * 2 + actions.size() * Button.HEIGHT
                + (actions.size() - 1) * BUTTON_GAP);
        return this;
    }

    public Rect panelBounds() {
        return background.absoluteBounds();
    }

    @Override public boolean focusScope() { return true; }

    @Override public boolean onFocusKey(int keyCode, int modifiers) {
        if (keyCode != GLFW.GLFW_KEY_ESCAPE) return false;
        onClose.run();
        return true;
    }

    public Rect contentBounds() {
        return content == null ? new Rect(0, 0, 0, 0) : content.absoluteBounds();
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
        super.layout(viewport, font);
        int desiredWidth = requestedWidth > 0 ? requestedWidth
                : Math.max(minimumWidth(), background.measureSize(font,
                        new Size(viewport.width(), viewport.height())).width());
        int fittedWidth = Math.max(1, Math.min(desiredWidth, Math.max(1, viewport.width())));
        int desiredHeight = requestedHeight > 0 ? requestedHeight
                : Math.max(minimumHeight(), background.measureSize(font,
                        new Size(fittedWidth, viewport.height())).height());
        fitChild(background, fittedBounds(desiredWidth, desiredHeight, customX, customY,
                viewport.width(), viewport.height()), font);
    }

    @Override protected void render(ModularGuiContext context, int screenX, int screenY) {
        Rect area = screenBounds(screenX, screenY);
        context.getGraphics().fill(area.x(), area.y(), area.right(), area.bottom(), OreTheme.OVERLAY_COLOR);
        WidgetThemeEntry<WidgetTheme> entry = context.getTheme().getWidgetTheme(OreTheme.FLOATING_HEADER);
        WidgetTheme style = entry == null ? context.getTheme().getPanelTheme().theme() : entry.theme();
        titleLabel.color(style.getTextColor()).shadow(style.isTextShadow());
    }

    @Override public void draw(ModularGuiContext context, int screenX, int screenY) {
        DebugBridge.backend().paint(this, context, screenX, screenY);
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

    /** The topmost modal consumes clicks both inside and outside its panel. */
    public boolean clickAt(int rootX, int rootY) {
        if (!visible()) return false;
        if (!background.hit(rootX, rootY)) {
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

}
