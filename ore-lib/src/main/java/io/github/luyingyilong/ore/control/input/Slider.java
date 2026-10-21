package io.github.luyingyilong.ore.control.input;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.ControlNotification;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.control.base.SizeFlags;
import io.github.luyingyilong.ore.control.display.Label;
import io.github.luyingyilong.ore.control.display.TextureRect;
import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.render.TextureRegion;
import io.github.luyingyilong.ore.render.TextureSource;
import io.github.luyingyilong.ore.theme.IconTheme;
import io.github.luyingyilong.ore.theme.OreTheme;
import io.github.luyingyilong.ore.theme.StyleBox;
import io.github.luyingyilong.ore.theme.StyleBoxTexture;
import io.github.luyingyilong.ore.theme.ThemeDB;
import io.github.luyingyilong.ore.theme.ThemeInsets;
import io.github.luyingyilong.ore.theme.ThemeKey;

import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.debug.Inspectable;
import io.github.luyingyilong.ore.event.InputEvent;
import io.github.luyingyilong.ore.event.KeyEvent;
import io.github.luyingyilong.ore.event.PointerEvent;
import io.github.luyingyilong.ore.signal.Signal;
import io.github.luyingyilong.ore.signal.SignalSource;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;

/** Horizontal numeric input. Physical thumbs keep their identity when their values cross. */
public final class Slider extends Control {
    public static final ThemeKey<StyleBox> SLIDER = track("slider", "slider");
    public static final ThemeKey<StyleBox> SLIDER_DISABLED = track("slider_disabled", "slider_disabled");
    public static final ThemeKey<StyleBox> GRABBER_AREA = track("grabber_area", "grabber_area");
    public static final ThemeKey<TextureRegion> GRABBER_NORMAL = IconTheme.texture("Slider", "grabber_normal", "grabber_normal", 14, 15);
    public static final ThemeKey<TextureRegion> GRABBER_HOVERED = IconTheme.texture("Slider", "grabber_hovered", "grabber_hover", 14, 15);
    public static final ThemeKey<TextureRegion> GRABBER_PRESSED = IconTheme.texture("Slider", "grabber_pressed", "grabber_pressed", 14, 15);
    public static final ThemeKey<TextureRegion> GRABBER_DISABLED = IconTheme.texture("Slider", "grabber_disabled", "grabber_disabled", 14, 15);
    public static final ThemeKey<TextureRegion> TICK = IconTheme.texture("Slider", "tick", "tick", 1, 6);
    public static final ThemeKey<TextureRegion> TICK_DISABLED = IconTheme.texture("Slider", "tick_disabled", "tick_disabled", 1, 6);
    private static final int LABEL_GAP = 4;
    private static final MathContext RATIO_PRECISION = MathContext.DECIMAL128;
    private final TextureRect firstImage = ownPart("first_thumb", image());
    private final TextureRect secondImage = ownPart("second_thumb", image());
    private final TextureRect tickImage = ownPart("tick", image());
    private final SignalSource<Double> valueChanged = new SignalSource<>(this, "valueChanged", "Double");
    private final SignalSource<RangeValue> rangeChanged = new SignalSource<>(this, "rangeChanged", "RangeValue");
    private final SignalSource<Change> changeCompleted = new SignalSource<>(this, "changeCompleted", "Change");
    private final Set<Integer> heldKeys = new HashSet<>();
    private final Map<Double, Label> markLabels = new LinkedHashMap<>();
    private NavigableMap<Double, Component> marks = Collections.emptyNavigableMap();
    private double min, max = 100, step = 1, first, second;
    private boolean range, disabled, keyboard = true, reverse, showTicks, dragging;
    private int activeThumb = 1, hoveredThumb = -1;
    private double grabOffset;
    private Change gestureStart;
    private long markGeneration;
    private Function<Double, Component> valueFormatter = value -> Component.literal(decimal(value).stripTrailingZeros().toPlainString());

    public Slider() {
        setPreferred(128, 15);
        setSizeFlagsHorizontal(SizeFlags.SIZE_FILL);
        setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        setFocusable(true);
    }

    private static TextureRect image() {
        return new TextureRect().setStretchMode(TextureRect.StretchMode.KEEP_CENTERED)
                .setExpandMode(TextureRect.ExpandMode.IGNORE_SIZE);
    }

    private static ThemeKey<StyleBox> track(String name, String file) {
        return ThemeKey.of("Slider", name, StyleBox.class, ThemeKey.Kind.STYLE,
                new StyleBoxTexture(TextureRegion.full(ResourceLocation.fromNamespaceAndPath("ore", "textures/gui/" + file + ".png"), 6, 6),
                        new ThemeInsets(2, 2, 2, 2), ThemeInsets.ZERO, ThemeInsets.ZERO,
                        StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1), true);
    }

    public static void registerTheme(ThemeDB db) {
        OreTheme.registerType(db, "Slider");
        for (var key : List.of(SLIDER, SLIDER_DISABLED, GRABBER_AREA, GRABBER_NORMAL,
                GRABBER_HOVERED, GRABBER_PRESSED, GRABBER_DISABLED, TICK, TICK_DISABLED)) db.register(key);
    }

    public record RangeValue(double lower, double upper) {
        public RangeValue {
            finite(lower); finite(upper);
            if (lower > upper) throw new IllegalArgumentException("Range lower must not exceed upper");
        }
    }

    /** A completed user gesture; scalar mode represents [min, value]. */
    public record Change(boolean range, double lower, double upper) { }

    public Signal<Double> valueChanged() { return valueChanged.signal(); }
    public Signal<RangeValue> rangeChanged() { return rangeChanged.signal(); }
    public Signal<Change> changeCompleted() { return changeCompleted.signal(); }
    public double getMin() { return min; }
    public double getMax() { return max; }
    public double getStep() { return step; }
    public boolean getRange() { return range; }

    public Slider setMin(double value) { return setLimits(value, max, true); }
    public Slider setMinNoSignal(double value) { return setLimits(value, max, false); }
    public Slider setMax(double value) { return setLimits(min, value, true); }
    public Slider setMaxNoSignal(double value) { return setLimits(min, value, false); }
    public Slider setLimits(double lower, double upper) { return setLimits(lower, upper, true); }
    public Slider setLimitsNoSignal(double lower, double upper) { return setLimits(lower, upper, false); }

    private Slider setLimits(double lower, double upper, boolean notify) {
        finite(lower); finite(upper);
        if (lower >= upper) throw new IllegalArgumentException("Slider min must be less than max");
        validateMarks(marks, lower, upper);
        if (min == lower && max == upper) return this;
        Change before = snapshot();
        cancelInteraction();
        min = lower; max = upper;
        first = normalize(first); second = normalize(second);
        requestLayout();
        if (notify) emitChange(before);
        return this;
    }

    public Slider setStep(double value) { return setStep(value, true); }
    public Slider setStepNoSignal(double value) { return setStep(value, false); }

    private Slider setStep(double value, boolean notify) {
        finite(value);
        if (value <= 0) throw new IllegalArgumentException("Slider step must be positive");
        if (step == value) return this;
        Change before = snapshot();
        cancelInteraction(); step = value;
        first = normalize(first); second = normalize(second);
        if (notify) emitChange(before);
        return this;
    }

    public double getValue() {
        requireMode(false);
        return second;
    }

    public Slider setValue(double value) { return setValue(value, true); }
    public Slider setValueNoSignal(double value) { return setValue(value, false); }

    private Slider setValue(double value, boolean notify) {
        requireMode(false);
        double next = normalize(value);
        if (second == next) return this;
        Change before = snapshot();
        cancelInteraction(); second = next;
        if (notify) emitChange(before);
        return this;
    }

    public RangeValue getRangeValue() {
        requireMode(true);
        return new RangeValue(Math.min(first, second), Math.max(first, second));
    }

    public Slider setRangeValue(RangeValue value) { return setRangeValue(value, true); }
    public Slider setRangeValueNoSignal(RangeValue value) { return setRangeValue(value, false); }

    private Slider setRangeValue(RangeValue value, boolean notify) {
        requireMode(true); Objects.requireNonNull(value);
        double lower = normalize(value.lower()), upper = normalize(value.upper());
        if (Math.min(first, second) == lower && Math.max(first, second) == upper) return this;
        Change before = snapshot();
        cancelInteraction(); first = lower; second = upper;
        if (notify) emitChange(before);
        return this;
    }

    public Slider setRange(boolean value) { return setRange(value, true); }
    public Slider setRangeNoSignal(boolean value) { return setRange(value, false); }

    private Slider setRange(boolean value, boolean notify) {
        if (range == value) return this;
        cancelInteraction();
        if (value) first = min;
        else second = Math.max(first, second);
        range = value; activeThumb = 1;
        if (notify) {
            if (range) rangeChanged.emit(getRangeValue()); else valueChanged.emit(second);
        }
        return this;
    }

    private void requireMode(boolean expectedRange) {
        if (range != expectedRange) throw new IllegalStateException(expectedRange ? "Slider is not in range mode" : "Slider is in range mode");
    }

    private static void finite(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Slider numbers must be finite");
    }

    private static BigDecimal decimal(double value) { return BigDecimal.valueOf(value); }

    private double normalize(double value) {
        finite(value);
        BigDecimal start = decimal(min), end = decimal(max);
        BigDecimal clamped = decimal(value).max(start).min(end);
        BigDecimal snapped = start.add(clamped.subtract(start).divide(decimal(step), 0, RoundingMode.HALF_UP).multiply(decimal(step))).min(end);
        if (end.subtract(clamped).abs().compareTo(snapped.subtract(clamped).abs()) <= 0) snapped = end;
        double result = snapped.doubleValue();
        return result == 0 ? 0 : result;
    }

    @Inspectable(group = "Slider")
    public boolean getDisabled() { return DebugBridge.value(this, "disabled", disabled); }
    public Slider setDisabled(boolean value) {
        if (DebugBridge.previewWrite(this, "disabled", value)) return this;
        disabled = value;
        if (value) cancelInteraction();
        return this;
    }

    @Inspectable(group = "Slider")
    public boolean getKeyboard() { return DebugBridge.value(this, "keyboard", keyboard); }
    public Slider setKeyboard(boolean value) {
        if (DebugBridge.previewWrite(this, "keyboard", value)) return this;
        keyboard = value;
        if (!value) cancelInteraction();
        return this;
    }

    @Inspectable(group = "Slider")
    public boolean getReverse() { return DebugBridge.value(this, "reverse", reverse); }
    public Slider setReverse(boolean value) {
        if (DebugBridge.previewWrite(this, "reverse", value)) return this;
        if (reverse != value) cancelInteraction();
        reverse = value;
        return this;
    }

    @Inspectable(group = "Slider")
    public boolean getShowTicks() { return DebugBridge.value(this, "showTicks", showTicks); }
    public Slider setShowTicks(boolean value) {
        if (DebugBridge.previewWrite(this, "showTicks", value)) return this;
        showTicks = value;
        return this;
    }

    public NavigableMap<Double, Component> getMarks() { return marks; }
    public Slider setMarks(Map<Double, Component> value) {
        Objects.requireNonNull(value);
        TreeMap<Double, Component> copy = new TreeMap<>(value);
        validateMarks(copy, min, max);
        if (marks.equals(copy)) return this;
        Map<Double, Label> previous = new LinkedHashMap<>(markLabels);
        markLabels.clear();
        marks = Collections.unmodifiableNavigableMap(copy);
        for (var entry : marks.entrySet()) {
            Label label = previous.remove(entry.getKey());
            if (label == null) label = ownPart("mark_" + (++markGeneration), new Label(entry.getValue())
                    .setThemeTypeVariation("LabelSecondary").setAlignment(Label.Horizontal.CENTER, Label.Vertical.CENTER)
                    .setOpticalCenter(true).setOverflow(Label.Overflow.ELLIPSIS));
            else label.setText(entry.getValue());
            markLabels.put(entry.getKey(), label);
        }
        previous.values().forEach(Control::queueFree);
        requestLayout();
        return this;
    }

    private static void validateMarks(Map<Double, Component> marks, double min, double max) {
        for (var entry : marks.entrySet()) {
            finite(Objects.requireNonNull(entry.getKey())); Objects.requireNonNull(entry.getValue());
            if (entry.getKey() < min || entry.getKey() > max) throw new IllegalArgumentException("Mark is outside Slider bounds");
        }
    }

    public Function<Double, Component> getValueFormatter() { return valueFormatter; }
    public Slider setValueFormatter(Function<Double, Component> formatter) {
        valueFormatter = Objects.requireNonNull(formatter);
        return this;
    }

    /** Sorted role of the active physical thumb: 0 = lower, 1 = upper. */
    public int getActiveThumb() { return !range ? 1 : (first <= second ? activeThumb : 1 - activeThumb); }
    public boolean getDragging() { return dragging; }
    @Override public boolean acceptsFocus() { return !getDisabled() && super.acceptsFocus(); }

    private int thumbWidth() {
        return List.of(GRABBER_NORMAL, GRABBER_HOVERED, GRABBER_PRESSED, GRABBER_DISABLED).stream()
                .mapToInt(key -> getThemeItem(key).getWidth()).max().orElse(14);
    }
    private int thumbHeight() {
        return List.of(GRABBER_NORMAL, GRABBER_HOVERED, GRABBER_PRESSED, GRABBER_DISABLED).stream()
                .mapToInt(key -> getThemeItem(key).getHeight()).max().orElse(15);
    }
    private int trackHeight(StyleBox style) { return style instanceof StyleBoxTexture texture ? texture.getTextureRegion().getHeight() : 6; }
    private int artworkHeight() {
        return Math.max(thumbHeight(), Math.max(trackHeight(getThemeItem(GRABBER_AREA)),
                Math.max(trackHeight(getThemeItem(SLIDER)), trackHeight(getThemeItem(SLIDER_DISABLED)))));
    }
    private int labelHeight(Font font) { return markLabels.values().stream().mapToInt(label -> label.lineHeight(font)).max().orElse(0); }
    private int naturalHeight(Font font) { return artworkHeight() + (marks.isEmpty() ? 0 : LABEL_GAP + labelHeight(font)); }
    @Override public int getMinimumWidth() { return Math.max(super.getMinimumWidth(), 2 * thumbWidth()); }
    @Override public int getMinimumHeight() { return Math.max(super.getMinimumHeight(), naturalHeight(getLayoutFont())); }
    @Override public int measureHeight(Font font, int available) { return isAlive() ? Math.max(getMinimumHeight(), naturalHeight(font)) : 0; }

    Rect getArtworkBounds() {
        Rect area = getAbsoluteBounds();
        return new Rect(area.x(), area.y() + Math.max(0, (area.height() - naturalHeight(getLayoutFont())) / 2), area.width(), artworkHeight());
    }
    private double valueOf(int thumb) { return thumb == 0 ? first : second; }
    private double fraction(double value) {
        double fraction = decimal(value).subtract(decimal(min)).divide(decimal(max).subtract(decimal(min)), RATIO_PRECISION).doubleValue();
        return getReverse() ? 1 - fraction : fraction;
    }
    private double center(double value) {
        Rect area = getArtworkBounds();
        return area.x() + thumbWidth() / 2.0 + Math.max(0, area.width() - thumbWidth()) * fraction(value);
    }
    Rect getThumbBounds(int physicalThumb) {
        Rect area = getArtworkBounds();
        return new Rect((int)Math.round(center(valueOf(physicalThumb)) - thumbWidth() / 2.0),
                area.y() + (area.height() - thumbHeight()) / 2, thumbWidth(), thumbHeight());
    }
    Rect getRailBounds() {
        Rect area = getArtworkBounds();
        int height = trackHeight(getThemeItem(getDisabled() ? SLIDER_DISABLED : SLIDER));
        return new Rect(area.x() + thumbWidth() / 2, area.y() + (area.height() - height) / 2,
                Math.max(0, area.width() - thumbWidth()), height);
    }
    Rect getSelectedBounds() {
        Rect artwork = getArtworkBounds();
        int height = trackHeight(getThemeItem(GRABBER_AREA));
        int a = (int)Math.round(center(range ? first : min)), b = (int)Math.round(center(second));
        return new Rect(Math.min(a, b), artwork.y() + (artwork.height() - height) / 2, Math.abs(a - b), height);
    }
    @Override public Rect getFocusBounds() { return getThumbBounds(range ? activeThumb : 1); }

    private int nearest(double x) {
        if (!range) return 1;
        double a = Math.abs(x - center(first)), b = Math.abs(x - center(second));
        return a == b ? activeThumb : a < b ? 0 : 1;
    }
    private int hovered(double x, double y) {
        int top = range ? activeThumb : 1;
        if (contains(getThumbBounds(top), x, y)) return top;
        return range && contains(getThumbBounds(1 - top), x, y) ? 1 - top : -1;
    }
    private static boolean contains(Rect rect, double x, double y) { return x >= rect.x() && x < rect.right() && y >= rect.y() && y < rect.bottom(); }

    @Override public void input(InputEvent event) {
        if (getDisabled() && (dragging || !heldKeys.isEmpty())) cancelInteraction();
        if (event instanceof PointerEvent pointer && pointer.button() == 0) {
            double x = getAbsoluteBounds().x() + event.getPreciseLocalX();
            double y = getAbsoluteBounds().y() + event.getPreciseLocalY();
            hoveredThumb = hovered(x, y);
            if (pointer.action() == PointerEvent.Action.PRESS && !getDisabled() && contains(getArtworkBounds(), x, y)) {
                cancelInteraction();
                int hitThumb = hovered(x, y);
                boolean onThumb = hitThumb >= 0;
                activeThumb = onThumb ? hitThumb : nearest(x);
                grabOffset = onThumb ? x - center(valueOf(activeThumb)) : 0;
                gestureStart = snapshot(); dragging = true;
                if (!onThumb) moveTo(x);
                event.capturePointer(); event.accept(); return;
            }
            if (dragging && pointer.action() == PointerEvent.Action.MOVE) {
                moveTo(x); event.accept(); return;
            }
            if (dragging && pointer.action() == PointerEvent.Action.RELEASE) {
                moveTo(x);
                Change start = gestureStart;
                dragging = false; gestureStart = null;
                complete(start); event.accept(); return;
            }
        }
        if (event instanceof KeyEvent key && keyInput(key)) { event.accept(); return; }
        super.input(event);
    }

    private void moveTo(double x) {
        Rect rail = getRailBounds();
        if (rail.width() <= 0) return;
        double ratio = Math.max(0, Math.min(1, (x - grabOffset - center(min)) / (getReverse() ? -rail.width() : rail.width())));
        BigDecimal candidate = decimal(min).add(decimal(max).subtract(decimal(min)).multiply(decimal(ratio)));
        setActiveValue(normalize(candidate.doubleValue()));
    }

    private void setActiveValue(double value) {
        Change before = snapshot();
        if (activeThumb == 0 && range) first = value; else second = value;
        emitChange(before);
    }

    private boolean keyInput(KeyEvent key) {
        int code = key.keyCode();
        boolean adjustment = code == GLFW.GLFW_KEY_LEFT || code == GLFW.GLFW_KEY_RIGHT || code == GLFW.GLFW_KEY_HOME || code == GLFW.GLFW_KEY_END;
        if (!adjustment) return false;
        if (key.action() == KeyEvent.Action.RELEASE) {
            if (!heldKeys.remove(code)) return false;
            if (heldKeys.isEmpty()) {
                Change start = gestureStart; gestureStart = null; complete(start);
            }
            return true;
        }
        if (getDisabled() || !getKeyboard()) return false;
        if ((key.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 && range && (code == GLFW.GLFW_KEY_LEFT || code == GLFW.GLFW_KEY_RIGHT)) {
            if (key.action() == KeyEvent.Action.PRESS) {
                int left = center(first) <= center(second) ? 0 : 1;
                activeThumb = code == GLFW.GLFW_KEY_LEFT ? left : 1 - left;
                if (getWindowContext().getOwner() != null) getWindowContext().getOwner().getFocusManager().keyboardNavigation();
            }
            return true;
        }
        if (key.action() == KeyEvent.Action.REPEAT && !heldKeys.contains(code)) return true;
        if (dragging) return true;
        if (heldKeys.isEmpty()) gestureStart = snapshot();
        heldKeys.add(code);
        if (getWindowContext().getOwner() != null) getWindowContext().getOwner().getFocusManager().keyboardNavigation();
        double current = valueOf(activeThumb);
        if (code == GLFW.GLFW_KEY_HOME) setActiveValue(min);
        else if (code == GLFW.GLFW_KEY_END) setActiveValue(max);
        else {
            int direction = (code == GLFW.GLFW_KEY_RIGHT ? 1 : -1) * (getReverse() ? -1 : 1);
            BigDecimal index = decimal(current).subtract(decimal(min)).divide(decimal(step), 0,
                    direction > 0 ? RoundingMode.FLOOR : RoundingMode.CEILING).add(BigDecimal.valueOf(direction));
            setActiveValue(normalize(decimal(min).add(index.multiply(decimal(step))).max(decimal(min)).min(decimal(max)).doubleValue()));
        }
        return true;
    }

    private Change snapshot() { return new Change(range, range ? Math.min(first, second) : min, range ? Math.max(first, second) : second); }
    private void emitChange(Change before) {
        Change after = snapshot();
        if (range ? before.range() != after.range() || before.lower() != after.lower() || before.upper() != after.upper() : before.upper() != after.upper()) {
            if (range) rangeChanged.emit(getRangeValue()); else valueChanged.emit(second);
        }
    }
    private void complete(Change start) {
        if (start != null && isAlive() && !getDisabled() && !start.equals(snapshot())) changeCompleted.emit(snapshot());
    }
    @Override public void cancelInteraction() {
        dragging = false; heldKeys.clear(); gestureStart = null; hoveredThumb = -1;
    }
    @Override public void onFocusChanged(boolean value) { if (!value) cancelInteraction(); }

    /** Bounded by visible pixels, even when the numeric domain has trillions of steps. */
    List<Double> getVisibleTicks() {
        if (!getShowTicks() && marks.isEmpty()) return List.of();
        int capacity = Math.max(1, getRailBounds().width() / 3);
        NavigableSet<Double> candidates = new TreeSet<>();
        candidates.add(min); candidates.add(max);
        if (!marks.isEmpty()) candidates.addAll(marks.keySet());
        else {
            BigDecimal count = decimal(max).subtract(decimal(min)).divide(decimal(step), 0, RoundingMode.FLOOR);
            BigDecimal stride = count.divide(BigDecimal.valueOf(capacity), 0, RoundingMode.CEILING).max(BigDecimal.ONE);
            for (BigDecimal index = BigDecimal.ZERO; index.compareTo(count) <= 0; index = index.add(stride))
                candidates.add(decimal(min).add(index.multiply(decimal(step))).doubleValue());
        }
        List<Double> result = new ArrayList<>();
        double previous = Double.NEGATIVE_INFINITY;
        for (double value : getReverse() ? candidates.descendingSet() : candidates) {
            double x = center(value);
            if (x - previous >= 3) { result.add(value); previous = x; }
        }
        return List.copyOf(result);
    }

    private void drawImage(TextureRect image, ThemeKey<TextureRegion> texture, Rect rect, DrawContext context) {
        image.setTexture(TextureSource.themed(texture));
        image.layout(rect, context.getFont());
        image.draw(context, 0, 0);
    }
    @Override protected void validateChildren(List<Control> proposed) {
        if (!proposed.isEmpty()) throw new IllegalStateException("Slider is a leaf control");
    }
    @Override protected void render(DrawContext context, int screenX, int screenY) {
        if (getDisabled() && (dragging || !heldKeys.isEmpty())) cancelInteraction();
        hoveredThumb = hovered(context.getAbsMouseX() - screenX, context.getAbsMouseY() - screenY);
        Rect rail = getRailBounds().translated(screenX, screenY);
        getThemeItem(getDisabled() ? SLIDER_DISABLED : SLIDER).draw(context, rail);
        Rect selected = getSelectedBounds().translated(screenX, screenY);
        if (selected.width() > 0 && !getDisabled()) getThemeItem(GRABBER_AREA).draw(context, selected);
        var tick = getDisabled() ? TICK_DISABLED : TICK;
        for (double value : getVisibleTicks()) {
            var texture = getThemeItem(tick);
            drawImage(tickImage, tick, new Rect((int)Math.round(center(value)) - texture.getWidth() / 2 + screenX,
                    rail.y() + (rail.height() - texture.getHeight()) / 2, texture.getWidth(), texture.getHeight()), context);
        }
        if (range) drawThumb(1 - activeThumb, context, screenX, screenY);
        drawThumb(range ? activeThumb : 1, context, screenX, screenY);
        int previousRight = Integer.MIN_VALUE;
        for (var entry : (getReverse() ? marks.descendingMap() : marks).entrySet()) {
            Label label = markLabels.get(entry.getKey());
            Rect artwork = getArtworkBounds();
            int width = Math.min(artwork.width(), label.measureWidth(context.getFont(), artwork.width()));
            int x = Math.max(artwork.x(), Math.min(artwork.right() - width, (int)Math.round(center(entry.getKey()) - width / 2.0)));
            if (x < previousRight) continue;
            label.layout(new Rect(x + screenX, artwork.bottom() + LABEL_GAP + screenY, width, labelHeight(context.getFont())), context.getFont());
            label.draw(context, 0, 0); previousRight = x + width + 2;
        }
    }
    private void drawThumb(int thumb, DrawContext context, int screenX, int screenY) {
        ThemeKey<TextureRegion> texture = getDisabled() ? GRABBER_DISABLED
                : (dragging || !heldKeys.isEmpty()) && thumb == activeThumb ? GRABBER_PRESSED
                : thumb == hoveredThumb ? GRABBER_HOVERED : GRABBER_NORMAL;
        drawImage(thumb == 0 ? firstImage : secondImage, texture, getThumbBounds(thumb).translated(screenX, screenY), context);
    }
    @Override public boolean hasTooltip() { return !getDisabled() && hoveredThumb >= 0; }
    @Override public void renderTooltip(DrawContext context, int mouseX, int mouseY) {
        if (!hasTooltip()) return;
        Component text = valueFormatter.apply(valueOf(hoveredThumb));
        if (text != null) context.getGraphics().renderTooltip(context.getFont(), text, mouseX, mouseY);
    }
    @Override public String getThemeState() {
        return (getDisabled() ? "disabled" : dragging || !heldKeys.isEmpty() ? "pressed" : hoveredThumb >= 0 ? "hover" : "normal")
                + "; " + snapshot() + "; min=" + min + "; max=" + max + "; step=" + step + "; active=" + getActiveThumb();
    }
    @Override protected void notification(ControlNotification notification) {
        if (notification.type() == ControlNotification.Type.PRE_FREE) {
            markLabels.clear(); marks = Collections.emptyNavigableMap(); valueFormatter = null;
        }
    }
}
