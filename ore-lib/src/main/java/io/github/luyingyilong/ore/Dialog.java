package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.render.TextureRegion;
import io.github.luyingyilong.ore.render.TextureSource;
import io.github.luyingyilong.ore.signal.Signal0;
import io.github.luyingyilong.ore.signal.SignalSource0;
import io.github.luyingyilong.ore.theme.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A tree-owned overlay measured and laid out as header, content, and footer.
 */
public final class Dialog extends Container {

    public static final ThemeKey<TextureRegion> CLOSE_ICON = IconTheme.item("Dialog", "close_icon", IconTheme.SMALL_CLOSE);
    public static final ThemeKey<Integer> HEADER_PADDING_TOP = paddingKey("header_padding_top", 8);
    public static final ThemeKey<Integer> HEADER_PADDING_RIGHT = paddingKey("header_padding_right", 8);
    public static final ThemeKey<Integer> HEADER_PADDING_BOTTOM = paddingKey("header_padding_bottom", 4);
    public static final ThemeKey<Integer> HEADER_PADDING_LEFT = paddingKey("header_padding_left", 8);
    public static final ThemeKey<Integer> BODY_PADDING_TOP = paddingKey("body_padding_top", 8);
    public static final ThemeKey<Integer> BODY_PADDING_RIGHT = paddingKey("body_padding_right", 8);
    public static final ThemeKey<Integer> BODY_PADDING_BOTTOM = paddingKey("body_padding_bottom", 8);
    public static final ThemeKey<Integer> BODY_PADDING_LEFT = paddingKey("body_padding_left", 8);
    public static final ThemeKey<Integer> FOOTER_PADDING_TOP = paddingKey("footer_padding_top", 4);
    public static final ThemeKey<Integer> FOOTER_PADDING_RIGHT = paddingKey("footer_padding_right", 8);
    public static final ThemeKey<Integer> FOOTER_PADDING_BOTTOM = paddingKey("footer_padding_bottom", 8);
    public static final ThemeKey<Integer> FOOTER_PADDING_LEFT = paddingKey("footer_padding_left", 8);
    public static final ThemeKey<Integer> COMPACT_HEADER_PADDING_TOP = paddingKey("compact_header_padding_top", 2);
    public static final ThemeKey<Integer> COMPACT_FOOTER_PADDING_BOTTOM = paddingKey("compact_footer_padding_bottom", 2);
    private static final int BUTTON_GAP = 2;
    private static final int HEADER_GAP = 4;
    private static final int CONTENT_MIN_HEIGHT = 24;
    private static final int CLOSE_SIZE = 18;
    private static final int CLOSE_ICON_SIZE = 8;
    private final int preferredWidth;
    private final int preferredHeight;
    private final int verticalOffset;
    private final Panel panel = new Panel(OreTheme.DIALOG_PANEL);
    private final Box column = new Box();
    private final Box header = new Box().setOrientation(Box.Orientation.HORIZONTAL).setGap(HEADER_GAP);
    private final Padding headerInset = new Padding(Padding.Insets.all(0)).setChild(header);
    private final Label titleLabel = new Label().setThemeTypeVariation("LabelHero").setOpticalCenter(true).setOverflow(Label.Overflow.CLIP)
            .setAlignment(Label.Horizontal.LEFT, Label.Vertical.CENTER);
    private final Button close = new Button().setSize(Button.Size.SMALL).setThemeTypeVariation("TextButton")
            .setIcon(TextureSource.themed(CLOSE_ICON));
    private final Box footer = new Box().setOrientation(Box.Orientation.VERTICAL).setGap(BUTTON_GAP);
    private final Padding footerInset = new Padding(Padding.Insets.all(0)).setChild(footer);
    private final Padding bodyInset = new Padding(Padding.Insets.all(0));
    private final List<Button> actions = new ArrayList<>();
    private final SignalSource0 closeRequested = new SignalSource0(this, "closeRequested");
    private Control content;
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
        close.setThemeParent(this);
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
        panel.setThemeParent(this);
        updateChildren(List.of(panel));
    }

    public static void registerTheme(ThemeDB db) {
        OreTheme.registerType(db, "Dialog");
        IconTheme.registerTheme(db);
        db.register(CLOSE_ICON);
        for (var key : List.of(HEADER_PADDING_TOP, HEADER_PADDING_RIGHT, HEADER_PADDING_BOTTOM, HEADER_PADDING_LEFT,
                BODY_PADDING_TOP, BODY_PADDING_RIGHT, BODY_PADDING_BOTTOM, BODY_PADDING_LEFT,
                FOOTER_PADDING_TOP, FOOTER_PADDING_RIGHT, FOOTER_PADDING_BOTTOM, FOOTER_PADDING_LEFT,
                COMPACT_HEADER_PADDING_TOP, COMPACT_FOOTER_PADDING_BOTTOM)) db.register(key);
        db.registerFallback(COMPACT_HEADER_PADDING_TOP, HEADER_PADDING_TOP);
        db.registerFallback(COMPACT_FOOTER_PADDING_BOTTOM, FOOTER_PADDING_BOTTOM);
        db.registerFallback(CLOSE_ICON, IconTheme.SMALL_CLOSE);
    }

    private static ThemeKey<Integer> paddingKey(String name, int value) {
        return new ThemeKey<>("Dialog", name, Integer.class, ThemeKey.Kind.INTEGER, value, true,
                padding -> padding >= 0 && padding <= Integer.MAX_VALUE / 2);
    }

    private void syncPadding(int viewportHeight) {
        boolean compact = viewportHeight < 110;
        headerInset.setInsets(new Padding.Insets(getThemeItem(compact ? COMPACT_HEADER_PADDING_TOP : HEADER_PADDING_TOP),
                getThemeItem(HEADER_PADDING_RIGHT), getThemeItem(HEADER_PADDING_BOTTOM), getThemeItem(HEADER_PADDING_LEFT)));
        bodyInset.setInsets(new Padding.Insets(getThemeItem(BODY_PADDING_TOP), getThemeItem(BODY_PADDING_RIGHT),
                getThemeItem(BODY_PADDING_BOTTOM), getThemeItem(BODY_PADDING_LEFT)));
        footerInset.setInsets(new Padding.Insets(getThemeItem(FOOTER_PADDING_TOP), getThemeItem(FOOTER_PADDING_RIGHT),
                getThemeItem(compact ? COMPACT_FOOTER_PADDING_BOTTOM : FOOTER_PADDING_BOTTOM), getThemeItem(FOOTER_PADDING_LEFT)));
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

    private static int actionHeight(int count) {
        return count == 0 ? 0 : count * Button.Size.MEDIUM.getHeight() + (count - 1) * BUTTON_GAP;
    }

    @Override
    public Dialog setMinimum(int width, int height) {
        super.setMinimum(width, height);
        return this;
    }

    @Override
    protected void validateChildren(List<Control> proposed) {
        if (!isUpdatingChildren())
            throw new IllegalStateException("Dialog has a fixed skeleton; use setContent/addAction");
    }

    @Override
    protected void childrenChanged() {
        content = bodyInset.getChildCount() == 0 ? null : bodyInset.getChild(0);
        actions.clear();
        for (Control child : footer.getChildren())
            if (child instanceof Button button && button.isAlive()) actions.add(button);
        footer.setMinimum(0, actionHeight(actions.size()));
        footerInset.setVisible(!actions.isEmpty());
    }

    public Dialog setTitle(Component title) {
        titleLabel.setText(Objects.requireNonNull(title));
        return this;
    }

    public Dialog clearFontSize() {
        titleLabel.clearFontSize();
        requestLayout();
        return this;
    }

    public float getFontSize() {
        return titleLabel.getFontSize();
    }

    public Dialog setFontSize(float fontSize) {
        titleLabel.setFontSize(fontSize);
        return this;
    }

    public Signal0 closeRequested() {
        return closeRequested.signal();
    }

    @Override
    public float getContentDepth() {
        return depth;
    }

    public Dialog setDepth(float depth) {
        this.depth = depth;
        return this;
    }

    @Override
    public boolean getFocusScope() {
        return true;
    }

    @Override
    public boolean onFocusKey(int keyCode, int modifiers) {
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
        for (Button action : List.copyOf(actions)) {
            if (action.getParent() != null) action.getParent().removeChild(action);
            action.queueFree();
        }
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

    @Override
    public void layout(Rect viewport, Font font) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            syncPadding(viewport.height());
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

    @Override
    protected void render(DrawContext context, int screenX, int screenY) {
        Rect area = getScreenBounds(screenX, screenY);
        context.getGraphics().fill(area.x(), area.y(), area.right(), area.bottom(), getThemeItem(OreTheme.OVERLAY));

    }

    @Override
    protected void drawContent(DrawContext context, int screenX, int screenY) {
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
                super.drawContent(context, screenX, screenY);
                graphics.flush();
            } finally {
                graphics.pose().popPose();
            }
        }
    }

    /**
     * Returns true for all clicks while open; the topmost dialog owns its entire overlay.
     */
    @Override
    protected boolean pointerDown(int rootX, int rootY) {
        if (!getVisible()) return false;
        if (!panel.hit(rootX, rootY)) {
            OreSounds.play(OreSounds.Cue.CLICK);
            closeRequested.emit();
            return true;
        }
        return true;
    }

}
