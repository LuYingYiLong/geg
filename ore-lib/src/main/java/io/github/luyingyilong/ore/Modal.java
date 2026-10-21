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
 * A positioned overlay with a one-pixel frame and themed header and footer.
 */
public final class Modal extends Container {

    public static final ThemeKey<TextureRegion> CLOSE_ICON = IconTheme.item("Modal", "close_icon", IconTheme.CLOSE);
    public static final ThemeKey<Integer> PADDING_TOP = paddingKey("padding_top", 1);
    public static final ThemeKey<Integer> PADDING_RIGHT = paddingKey("padding_right", 1);
    public static final ThemeKey<Integer> PADDING_BOTTOM = paddingKey("padding_bottom", 1);
    public static final ThemeKey<Integer> PADDING_LEFT = paddingKey("padding_left", 1);
    public static final ThemeKey<Integer> HEADER_PADDING_TOP = paddingKey("header_padding_top", 0);
    public static final ThemeKey<Integer> HEADER_PADDING_RIGHT = paddingKey("header_padding_right", 1);
    public static final ThemeKey<Integer> HEADER_PADDING_BOTTOM = paddingKey("header_padding_bottom", 0);
    public static final ThemeKey<Integer> HEADER_PADDING_LEFT = paddingKey("header_padding_left", 1);
    public static final ThemeKey<Integer> BODY_PADDING_TOP = paddingKey("body_padding_top", 8);
    public static final ThemeKey<Integer> BODY_PADDING_RIGHT = paddingKey("body_padding_right", 8);
    public static final ThemeKey<Integer> BODY_PADDING_BOTTOM = paddingKey("body_padding_bottom", 8);
    public static final ThemeKey<Integer> BODY_PADDING_LEFT = paddingKey("body_padding_left", 8);
    public static final ThemeKey<Integer> FOOTER_PADDING_TOP = paddingKey("footer_padding_top", 8);
    public static final ThemeKey<Integer> FOOTER_PADDING_RIGHT = paddingKey("footer_padding_right", 8);
    public static final ThemeKey<Integer> FOOTER_PADDING_BOTTOM = paddingKey("footer_padding_bottom", 8);
    public static final ThemeKey<Integer> FOOTER_PADDING_LEFT = paddingKey("footer_padding_left", 8);
    public static final int HEADER_HEIGHT = 22;
    private static final int CLOSE_HIT_WIDTH = 20;
    private static final int CLOSE_ICON_SIZE = 7;
    private static final int BUTTON_GAP = 2;
    private final Panel background;
    private final Box column = new Box();
    private final Panel header;
    private final Box headerRow = new Box().setOrientation(Box.Orientation.HORIZONTAL).setGap(4);
    private final Padding headerInset = new Padding(Padding.Insets.all(0)).setChild(headerRow);
    private final Control headerSpacer = new Control();
    private final Label titleLabel = new Label().setThemeTypeVariation("LabelHero").setOpticalCenter(true).setOverflow(Label.Overflow.ELLIPSIS)
            .setAlignment(Label.Horizontal.CENTER, Label.Vertical.CENTER);
    private final Button close = new Button().setThemeTypeVariation("TextButton")
            .setIcon(TextureSource.themed(CLOSE_ICON));
    private final Padding bodyInset = new Padding(Padding.Insets.all(0));
    private final Panel footer;
    private final Box footerButtons = new Box().setGap(BUTTON_GAP);
    private final Padding footerInset = new Padding(Padding.Insets.all(0)).setChild(footerButtons);
    private final Padding frameInset = new Padding(Padding.Insets.all(0)).setChild(column);
    private final List<Button> actions = new ArrayList<>();
    private final SignalSource0 closeRequested = new SignalSource0(this, "closeRequested");
    private Control content;
    private int requestedWidth;
    private int requestedHeight;
    private int appliedBodyMinimum = -1;
    private int appliedFooterMinimum = -1;
    private Integer customX;
    private Integer customY;
    private float depth = 200.0F;
    public Modal(Component title) {
        this(title, OreTheme.MODAL_PANEL, OreTheme.FLOATING_HEADER, OreTheme.MODAL_FOOTER);
    }

    Modal(Component title, io.github.luyingyilong.ore.theme.ThemeKey<io.github.luyingyilong.ore.theme.StyleBox> backgroundTheme,
          io.github.luyingyilong.ore.theme.ThemeKey<io.github.luyingyilong.ore.theme.StyleBox> headerTheme, io.github.luyingyilong.ore.theme.ThemeKey<io.github.luyingyilong.ore.theme.StyleBox> footerTheme) {
        background = new Panel(backgroundTheme);
        header = new Panel(headerTheme);
        footer = new Panel(footerTheme);
        background.setThemeParent(this);
        header.setThemeParent(this);
        footer.setThemeParent(this);
        setTitle(title);
        titleLabel.setThemeParent(this);
        titleLabel.setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).setSizeFlagsVertical(SizeFlags.SIZE_FILL);
        headerSpacer.setMinimum(CLOSE_HIT_WIDTH, CLOSE_HIT_WIDTH);
        close.setMinimum(CLOSE_HIT_WIDTH, CLOSE_HIT_WIDTH);
        close.setThemeParent(this);
        close.setSizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_CENTER).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        close.pressed().connect(close, () -> closeRequested.emit());
        headerRow.addChild(headerSpacer);
        headerRow.addChild(titleLabel);
        headerRow.addChild(close);
        header.setChild(headerInset);
        header.setMinimum(0, HEADER_HEIGHT).setPreferred(0, HEADER_HEIGHT)
                .setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        bodyInset.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        footerButtons.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        footer.setChild(footerInset);
        footer.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        footer.setVisible(false);
        column.addChild(header);
        column.addChild(bodyInset);
        column.addChild(footer);
        background.setChild(frameInset);
        updateChildren(List.of(background));
    }

    public static void registerTheme(ThemeDB db) {
        OreTheme.registerType(db, "Modal");
        IconTheme.registerTheme(db);
        db.register(CLOSE_ICON);
        for (var key : List.of(PADDING_TOP, PADDING_RIGHT, PADDING_BOTTOM, PADDING_LEFT,
                HEADER_PADDING_TOP, HEADER_PADDING_RIGHT, HEADER_PADDING_BOTTOM, HEADER_PADDING_LEFT,
                BODY_PADDING_TOP, BODY_PADDING_RIGHT, BODY_PADDING_BOTTOM, BODY_PADDING_LEFT,
                FOOTER_PADDING_TOP, FOOTER_PADDING_RIGHT, FOOTER_PADDING_BOTTOM, FOOTER_PADDING_LEFT))
            db.register(key);
        db.registerFallback(CLOSE_ICON, IconTheme.CLOSE);
    }

    private static ThemeKey<Integer> paddingKey(String name, int value) {
        return new ThemeKey<>("Modal", name, Integer.class, ThemeKey.Kind.INTEGER, value, true,
                padding -> padding >= 0 && padding <= Integer.MAX_VALUE / 2);
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

    private void syncPadding() {
        frameInset.setInsets(new Padding.Insets(getThemeItem(PADDING_TOP), getThemeItem(PADDING_RIGHT),
                getThemeItem(PADDING_BOTTOM), getThemeItem(PADDING_LEFT)));
        headerInset.setInsets(new Padding.Insets(getThemeItem(HEADER_PADDING_TOP), getThemeItem(HEADER_PADDING_RIGHT),
                getThemeItem(HEADER_PADDING_BOTTOM), getThemeItem(HEADER_PADDING_LEFT)));
        bodyInset.setInsets(new Padding.Insets(getThemeItem(BODY_PADDING_TOP), getThemeItem(BODY_PADDING_RIGHT),
                getThemeItem(BODY_PADDING_BOTTOM), getThemeItem(BODY_PADDING_LEFT)));
        footerInset.setInsets(new Padding.Insets(getThemeItem(FOOTER_PADDING_TOP), getThemeItem(FOOTER_PADDING_RIGHT),
                getThemeItem(FOOTER_PADDING_BOTTOM), getThemeItem(FOOTER_PADDING_LEFT)));
        int bodyMinimum = bodyInset.getInsets().top() + bodyInset.getInsets().bottom();
        if (appliedBodyMinimum != bodyMinimum) {
            appliedBodyMinimum = bodyMinimum;
            bodyInset.setMinimum(0, bodyMinimum);
        }
        updateFooterMinimum();
    }

    private void updateFooterMinimum() {
        int padding = footerInset.getInsets().top() + footerInset.getInsets().bottom()
                + footer.getStyleBox().getContentMargin().getVertical();
        int minimum = actions.isEmpty() ? 0
                : padding + actions.size() * Button.HEIGHT + (actions.size() - 1) * BUTTON_GAP;
        if (appliedFooterMinimum != minimum) {
            appliedFooterMinimum = minimum;
            footer.setMinimum(0, minimum);
        }
    }

    @Override
    protected void validateChildren(List<Control> proposed) {
        if (!isUpdatingChildren())
            throw new IllegalStateException("Modal has a fixed skeleton; use setContent/addAction");
    }

    @Override
    protected void childrenChanged() {
        content = bodyInset.getChildCount() == 0 ? null : bodyInset.getChild(0);
        actions.clear();
        for (Control child : footerButtons.getChildren())
            if (child instanceof Button button && button.isAlive()) actions.add(button);
        updateFooterMinimum();
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

    /**
     * Coordinates are relative to the GUI viewport, not the operating-system window.
     */
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

    public Signal0 closeRequested() {
        return closeRequested.signal();
    }

    @Override
    public float getContentDepth() {
        return depth;
    }

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
        for (Button action : List.copyOf(actions)) {
            if (action.getParent() != null) action.getParent().removeChild(action);
            action.queueFree();
        }
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
        updateFooterMinimum();
        return this;
    }

    public Rect getPanelBounds() {
        return background.getAbsoluteBounds();
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

    public Rect getContentBounds() {
        return content == null ? new Rect(0, 0, 0, 0) : content.getAbsoluteBounds();
    }

    @Override
    public void layout(Rect viewport, Font font) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            syncPadding();
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

    @Override
    protected void render(DrawContext context, int screenX, int screenY) {
        Rect area = getScreenBounds(screenX, screenY);
        context.getGraphics().fill(area.x(), area.y(), area.right(), area.bottom(), getThemeItem(OreTheme.OVERLAY));

    }

    @Override
    protected void drawContent(DrawContext context, int screenX, int screenY) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
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
     * The topmost modal consumes clicks both inside and outside its panel.
     */
    @Override
    protected boolean pointerDown(int rootX, int rootY) {
        if (!getVisible()) return false;
        if (!background.hit(rootX, rootY)) {
            OreSounds.play(OreSounds.Cue.CLICK);
            closeRequested.emit();
            return true;
        }
        return true;
    }


}
