package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.event.CancelEvent;
import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.render.TextureRegion;
import io.github.luyingyilong.ore.render.TextureSource;
import io.github.luyingyilong.ore.signal.DispatchGuard;
import io.github.luyingyilong.ore.signal.Signal;
import io.github.luyingyilong.ore.signal.SignalSource;
import io.github.luyingyilong.ore.theme.*;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Retained single panel. The body is revealed by height, never scaled or recreated.
 */
public final class Collapse extends Container {
    public static final ThemeKey<StyleBox> PANEL = ThemeKey.of("Collapse", "panel", StyleBox.class, ThemeKey.Kind.STYLE,
            new StyleBoxTexture(((StyleBoxTexture) OreTheme.PANEL.defaultValue()).getTextureRegion(),
                    new ThemeInsets(1, 1, 1, 1), ThemeInsets.ZERO, ThemeInsets.ZERO,
                    StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1F), true);
    public static final ThemeKey<Integer> PADDING_TOP = paddingKey("padding_top", 4);
    public static final ThemeKey<Integer> PADDING_RIGHT = paddingKey("padding_right", 8);
    public static final ThemeKey<Integer> PADDING_BOTTOM = paddingKey("padding_bottom", 4);
    public static final ThemeKey<Integer> PADDING_LEFT = paddingKey("padding_left", 8);
    public static final ThemeKey<Integer> SECONDARY_COLOR = ThemeKey.of("Collapse", "secondary_color", Integer.class, ThemeKey.Kind.COLOR, OreTheme.TEXT_SECONDARY, false);
    public static final ThemeKey<TextureRegion> COLLAPSED_ARROW = IconTheme.item("Collapse", "collapsed_arrow", IconTheme.ARROW_DOWN_LIGHT);
    public static final ThemeKey<TextureRegion> DISABLED_COLLAPSED_ARROW = IconTheme.item("Collapse", "disabled_collapsed_arrow", IconTheme.ARROW_DOWN);
    public static final ThemeKey<TextureRegion> EXPANDED_ARROW = IconTheme.item("Collapse", "expanded_arrow", IconTheme.ARROW_UP_LIGHT);
    public static final ThemeKey<TextureRegion> DISABLED_EXPANDED_ARROW = IconTheme.item("Collapse", "disabled_expanded_arrow", IconTheme.ARROW_UP);
    public static final ThemeKey<Integer> ANIMATION_DURATION = new ThemeKey<>("Collapse", "animation_duration", Integer.class, ThemeKey.Kind.INTEGER, 100, false, v -> v >= 0);
    private final Button header = new Button().setThemeTypeVariation("DarkButton").setAlignment(Button.TextAlignment.LEFT);
    private final Panel panel = new Panel(PANEL);
    private final Padding contentPadding = new Padding(new Padding.Insets(4, 8, 4, 8));
    private final BodyViewport body = new BodyViewport();
    private final SignalSource<Boolean> expandedChanged = new SignalSource<>(this, "expandedChanged", "Boolean");
    private final LongSupplier clock;
    private Component text = Component.empty(), secondaryText = Component.empty(), displayedText;
    private boolean expanded, disabled, showArrow = true, initialized, animating;
    private Integer duration;
    private long started, frameTime;
    private double fullHeight, visibleHeight, fromHeight, toHeight;
    private int headerHeight;
    private int displayedSecondaryColor;

    public Collapse() {
        this(Component.empty());
    }

    public Collapse(Component text) {
        this(text, net.minecraft.Util::getMillis);
    }

    Collapse(Component text, LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock);
        this.text = Objects.requireNonNull(text);
        header.setThemeParent(this);
        panel.setThemeParent(this);
        panel.setChild(contentPadding);
        body.addChild(panel);
        body.setVisible(false);
        updateChildren(List.of(header, body));
        header.pressed().connect(this, () -> setExpanded(!expanded));
        setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
        updateHeader();
    }

    public static void registerTheme(ThemeDB db) {
        OreTheme.registerType(db, "Collapse");
        for (var key : List.of(PANEL, PADDING_TOP, PADDING_RIGHT, PADDING_BOTTOM, PADDING_LEFT, SECONDARY_COLOR,
                COLLAPSED_ARROW, DISABLED_COLLAPSED_ARROW, EXPANDED_ARROW, DISABLED_EXPANDED_ARROW, ANIMATION_DURATION))
            db.register(key);
        db.registerFallback(COLLAPSED_ARROW, IconTheme.ARROW_DOWN_LIGHT);
        db.registerFallback(DISABLED_COLLAPSED_ARROW, IconTheme.ARROW_DOWN);
        db.registerFallback(EXPANDED_ARROW, IconTheme.ARROW_UP_LIGHT);
        db.registerFallback(DISABLED_EXPANDED_ARROW, IconTheme.ARROW_UP);
    }

    private static ThemeKey<Integer> paddingKey(String name, int value) {
        return new ThemeKey<>("Collapse", name, Integer.class, ThemeKey.Kind.INTEGER, value, true, v -> v >= 0 && v <= Integer.MAX_VALUE / 2);
    }

    @Override
    protected void validateChildren(List<Control> proposed) {
        if (!isUpdatingChildren()) throw new IllegalStateException("Collapse has a fixed skeleton; use setContent");
    }

    public Component getText() {
        return text;
    }

    public Collapse setText(Component value) {
        requireAlive();
        text = Objects.requireNonNull(value);
        displayedText = null;
        updateHeader();
        requestLayout();
        return this;
    }

    public Component getSecondaryText() {
        return secondaryText;
    }

    public Collapse setSecondaryText(Component value) {
        requireAlive();
        secondaryText = Objects.requireNonNull(value);
        displayedText = null;
        updateHeader();
        requestLayout();
        return this;
    }

    public Control getContent() {
        return contentPadding.getChildCount() == 0 ? null : contentPadding.getChild(0);
    }

    public Collapse setContent(Control content) {
        requireAlive();
        contentPadding.setChild(content);
        requestLayout();
        return this;
    }

    public Padding.Insets getPadding() {
        syncPadding();
        return contentPadding.getInsets();
    }

    /**
     * Explicit body padding overrides the theme until clearPadding is called.
     */
    public Collapse setPadding(Padding.Insets insets) {
        requireAlive();
        Objects.requireNonNull(insets);
        PADDING_TOP.validate(insets.top());
        PADDING_RIGHT.validate(insets.right());
        PADDING_BOTTOM.validate(insets.bottom());
        PADDING_LEFT.validate(insets.left());
        setThemeProperty(PADDING_TOP, insets.top());
        setThemeProperty(PADDING_RIGHT, insets.right());
        setThemeProperty(PADDING_BOTTOM, insets.bottom());
        setThemeProperty(PADDING_LEFT, insets.left());
        syncPadding();
        return this;
    }

    public Collapse setPadding(int top, int right, int bottom, int left) {
        return setPadding(new Padding.Insets(top, right, bottom, left));
    }

    public Collapse clearPadding() {
        requireAlive();
        clearThemeProperty(PADDING_TOP);
        clearThemeProperty(PADDING_RIGHT);
        clearThemeProperty(PADDING_BOTTOM);
        clearThemeProperty(PADDING_LEFT);
        syncPadding();
        return this;
    }

    private void syncPadding() {
        contentPadding.setInsets(new Padding.Insets(getThemeItem(PADDING_TOP), getThemeItem(PADDING_RIGHT),
                getThemeItem(PADDING_BOTTOM), getThemeItem(PADDING_LEFT)));
    }

    public Button getHeaderButton() {
        return header;
    }

    public boolean getExpanded() {
        return expanded;
    }

    public Collapse setExpanded(boolean value) {
        return changeExpanded(value, true);
    }

    public Collapse setExpandedNoSignal(boolean value) {
        return changeExpanded(value, false);
    }

    public Signal<Boolean> expandedChanged() {
        return expandedChanged.signal();
    }

    public boolean getDisabled() {
        return disabled;
    }

    public Collapse setDisabled(boolean value) {
        requireAlive();
        disabled = value;
        header.setDisabled(value);
        displayedText = null;
        updateHeader();
        return this;
    }

    public boolean getShowArrow() {
        return showArrow;
    }

    public Collapse setShowArrow(boolean value) {
        requireAlive();
        showArrow = value;
        updateHeader();
        requestLayout();
        return this;
    }

    public int getAnimationDuration() {
        return duration == null ? getThemeItem(ANIMATION_DURATION) : duration;
    }

    public Collapse setAnimationDuration(int milliseconds) {
        requireAlive();
        if (milliseconds < 0) throw new IllegalArgumentException("Animation duration must not be negative");
        duration = milliseconds;
        advanceAnimation(clock.getAsLong());
        requestLayout();
        return this;
    }

    public Collapse clearAnimationDuration() {
        requireAlive();
        duration = null;
        requestLayout();
        return this;
    }

    public boolean getAnimating() {
        return animating;
    }

    public double getVisibleBodyHeight() {
        return visibleHeight;
    }

    private void requireAlive() {
        getSignalScope().checkThread();
        if (!isAlive()) throw new IllegalStateException("Collapse is no longer alive");
    }

    private Collapse changeExpanded(boolean value, boolean notify) {
        requireAlive();
        if (expanded == value) return this;
        frameTime = clock.getAsLong();
        advanceAnimation(frameTime);
        boolean bodyFocused = false;
        var window = getWindowContext().getOwner();
        if (!value && window != null)
            for (Control node = window.getFocusManager().getFocused(); node != null; node = node.getParent())
                if (node == body) {
                    bodyFocused = true;
                    break;
                }
        expanded = value;
        updateArrow();
        if (initialized) {
            fromHeight = visibleHeight;
            toHeight = value ? fullHeight : 0;
            started = frameTime;
            animating = true;
            body.setVisible(true);
            advanceAnimation(frameTime);
        }
        if (!value) releaseBodyInput(bodyFocused);
        requestLayout();
        if (notify) expandedChanged.emit(value);
        return this;
    }

    private void releaseBodyInput(boolean focused) {
        var window = getWindowContext().getOwner();
        if (window == null) return;
        window.cancelCaptures(body, CancelEvent.Reason.BLOCKED);
        boolean popup = window.getContext().getPopups().cancelFrom(body);
        window.getFocusManager().forgetSubtree(body);
        if ((focused || popup) && header.acceptsFocus())
            window.getFocusManager().requestFocus(header, window.getContext().getMui());
    }

    @Override
    protected boolean allowsChildInput(Control child) {
        return child != body || expanded;
    }

    @Override
    public String getThemeState() {
        return (expanded ? "expanded" : "collapsed") + (disabled ? ", disabled" : "") + (animating ? ", animating" : "");
    }

    @Override
    protected boolean advanceAnimation(long milliseconds) {
        frameTime = clock.getAsLong();
        if (animating) {
            double t = getAnimationDuration() == 0 ? 1 : Math.min(1, Math.max(0, (double) (frameTime - started) / getAnimationDuration()));
            double eased = t * t * (3 - 2 * t);
            visibleHeight = fromHeight + (toHeight - fromHeight) * eased;
            if (t == 1) {
                animating = false;
                if (!expanded) body.setVisible(false);
            }
            requestLayout();
        }
        return animating;
    }

    private void updateHeader() {
        int color = disabled ? header.getThemeItem(Button.DISABLED_COLOR) : getThemeItem(SECONDARY_COLOR);
        if (displayedText == null || displayedSecondaryColor != color) {
            var label = text.copy();
            if (!secondaryText.getString().isEmpty())
                label.append(" ").append(secondaryText.copy().withStyle(style -> style.withColor(TextColor.fromRgb(color))));
            header.setLabel(label);
            displayedText = label;
            displayedSecondaryColor = color;
        }
        updateArrow();
    }

    private void updateArrow() {
        ThemeKey<TextureRegion> arrow = expanded
                ? (disabled ? DISABLED_EXPANDED_ARROW : EXPANDED_ARROW)
                : (disabled ? DISABLED_COLLAPSED_ARROW : COLLAPSED_ARROW);
        header.setTrailingIcon(showArrow ? TextureSource.themed(arrow) : null);
    }

    private Size measureParts(Font font, int width) {
        syncPadding();
        updateHeader();
        Size headerSize = header.desiredSize(font, new Size(width, Integer.MAX_VALUE));
        Size contentSize = panel.desiredSize(font, new Size(width, Integer.MAX_VALUE));
        headerHeight = headerSize.height();
        double wanted = contentSize.height();
        if (!initialized) {
            initialized = true;
            visibleHeight = expanded ? wanted : 0;
            body.setVisible(expanded);
        } else if (animating && expanded && wanted != fullHeight) {
            fromHeight = visibleHeight;
            toHeight = wanted;
            started = frameTime;
        } else if (!animating) visibleHeight = expanded ? wanted : 0;
        fullHeight = wanted;
        updateArrow();
        return new Size(Math.max(getMinimumWidth(), Math.max(headerSize.width(), contentSize.width())),
                Math.max(getMinimumHeight(), headerHeight + (int) Math.ceil(visibleHeight)));
    }

    @Override
    public Size measureSize(Font font, Size available) {
        return measureParts(font, available.width());
    }

    @Override
    public int measureWidth(Font font, int available) {
        return measureParts(font, available).width();
    }

    @Override
    public int measureHeight(Font font, int available) {
        return measureParts(font, available).height();
    }

    @Override
    public void layout(Rect bounds, Font font) {
        try (var phase = DispatchGuard.enter()) {
            super.layout(bounds, font);
            measureParts(font, bounds.width());
            layoutChild(header, new Rect(0, 0, bounds.width(), headerHeight), font);
            layoutChild(body, new Rect(0, headerHeight, bounds.width(), (int) Math.ceil(visibleHeight)), font);
        }
    }

    @Override
    protected void notification(ControlNotification notification) {
        if (notification.type() == ControlNotification.Type.THEME_CHANGED) {
            displayedText = null;
            requestLayout();
        }
    }

    private final class BodyViewport extends Container {
        @Override
        protected boolean requiresAxisAlignedClip() {
            return true;
        }

        @Override
        public void layout(Rect bounds, Font font) {
            super.layout(bounds, font);
            layoutChild(panel, new Rect(0, 0, bounds.width(), (int) Math.ceil(fullHeight)), font);
        }

        @Override
        protected void drawContent(DrawContext context, int x, int y) {
            Rect area = getScreenBounds(x, y);
            if (area.height() == 0) return;
            context.enableScissor(area.x(), area.y(), area.right(), area.bottom());
            io.github.luyingyilong.ore.debug.DebugBridge.backend().clip(context.toScreenRect(area));
            try {
                super.drawContent(context, x, y);
            } finally {
                io.github.luyingyilong.ore.debug.DebugBridge.backend().unclip();
                context.disableScissor();
            }
        }
    }
}
