package io.github.luyingyilong.ore.control.progress;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.ControlNotification;
import io.github.luyingyilong.ore.control.base.Offset;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.control.base.SizeFlags;
import io.github.luyingyilong.ore.control.display.Label;
import io.github.luyingyilong.ore.control.display.TextureRect;
import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.debug.DebugProperty;
import io.github.luyingyilong.ore.debug.Inspectable;
import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.render.TexturePainter;
import io.github.luyingyilong.ore.render.TextureRegion;
import io.github.luyingyilong.ore.render.TextureSource;
import io.github.luyingyilong.ore.signal.Signal;
import io.github.luyingyilong.ore.signal.SignalSource;
import io.github.luyingyilong.ore.theme.OreTheme;
import io.github.luyingyilong.ore.theme.ThemeDB;
import io.github.luyingyilong.ore.theme.ThemeKey;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.Objects;

/** Three retained texture layers sharing one reference canvas and one progress value. */
public final class TextureProgressBar extends Control {
    public enum FillMode {
        LEFT_TO_RIGHT, RIGHT_TO_LEFT, TOP_TO_BOTTOM, BOTTOM_TO_TOP,
        CENTER_HORIZONTAL, CENTER_VERTICAL, CLOCKWISE, COUNTER_CLOCKWISE, BIDIRECTIONAL_RADIAL
    }
    public enum StretchMode { KEEP, SCALE, KEEP_ASPECT_CENTERED }

    public static final ThemeKey<Integer> UNDER_TINT = tint("under_tint");
    public static final ThemeKey<Integer> PROGRESS_TINT = tint("progress_tint");
    public static final ThemeKey<Integer> OVER_TINT = tint("over_tint");
    public static final ThemeKey<Integer> FONT_COLOR = tint("font_color");
    public static final ThemeKey<Float> FONT_SIZE = new ThemeKey<>("TextureProgressBar", "font_size", Float.class,
            ThemeKey.Kind.FLOAT, 8.0F, true, v -> Float.isFinite(v) && v > 0);
    private static ThemeKey<Integer> tint(String name) {
        return ThemeKey.of("TextureProgressBar", name, Integer.class, ThemeKey.Kind.COLOR, 0xFFFFFFFF, false);
    }
    public static void registerTheme(ThemeDB db) {
        OreTheme.registerType(db, "TextureProgressBar");
        db.register(UNDER_TINT); db.register(PROGRESS_TINT); db.register(OVER_TINT);
        db.register(FONT_COLOR); db.register(FONT_SIZE);
    }

    private final ProgressValueModel values = new ProgressValueModel();
    private final SignalSource<Double> changed = new SignalSource<>(this, "valueChanged", "Double");
    private final Label percentage = ownPart("percentage", new Label().setOpticalCenter(true)
            .setAlignment(Label.Horizontal.CENTER, Label.Vertical.CENTER));
    private TextureSource underTexture, progressTexture, overTexture;
    private Offset progressOffset = Offset.ZERO, radialCenterOffset = Offset.ZERO;
    private FillMode fillMode = FillMode.LEFT_TO_RIGHT;
    private StretchMode stretchMode = StretchMode.KEEP;
    private TextureRect.ExpandMode expandMode = TextureRect.ExpandMode.KEEP_SIZE;
    private double radialInitialAngle, radialFillDegrees = 360;
    private float opacity = 1;
    private boolean showPercentage;
    private GeometryKey cachedKey;
    private DrawGeometry cachedGeometry;

    public TextureProgressBar() {
        updateChildren(List.of(percentage));
        percentage.setVisible(false);
        setSizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_BEGIN).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        refreshText();
    }
    @Override protected void validateChildren(List<Control> children) {
        if (!isUpdatingChildren()) throw new IllegalStateException("TextureProgressBar has a fixed skeleton");
    }
    @Override public boolean hit(int x, int y) { return false; }
    @Override protected Control hitTestTree(int x, int y, int windowX, int windowY) { return null; }
    @io.github.luyingyilong.ore.gui.UiSignal
    public Signal<Double> valueChanged() { return changed.signal(); }

    private double effectiveLimit(boolean upper) {
        double min = DebugBridge.value(this, "min", values.getMin()), max = DebugBridge.value(this, "max", values.getMax());
        if (!Double.isFinite(min) || !Double.isFinite(max) || min >= max || !Double.isFinite(max - min))
            return upper ? values.getMax() : values.getMin();
        return upper ? max : min;
    }
    @io.github.luyingyilong.ore.gui.UiProperty
    public double getMin() { return effectiveLimit(false); }
    @io.github.luyingyilong.ore.gui.UiProperty
    public double getMax() { return effectiveLimit(true); }
    @Inspectable(group = "Progress") public double getValue() {
        return Math.max(getMin(), Math.min(getMax(), DebugBridge.value(this, "value", values.getValue())));
    }
    public double getFraction() { return (getValue() - getMin()) / (getMax() - getMin()); }
    public TextureProgressBar setMin(double value) {
        return setLimits(value, values.getMax());
    }
    public TextureProgressBar setMax(double value) {
        return setLimits(values.getMin(), value);
    }
    public TextureProgressBar setMinNoSignal(double value) { return setLimitsNoSignal(value, values.getMax()); }
    public TextureProgressBar setMaxNoSignal(double value) { return setLimitsNoSignal(values.getMin(), value); }
    public TextureProgressBar setLimits(double min, double max) {
        double old = values.getValue(); setLimitsNoSignal(min, max);
        if (old != values.getValue()) changed.emit(values.getValue());
        return this;
    }
    public TextureProgressBar setLimitsNoSignal(double min, double max) {
        values.setLimits(min, max); clearGeometry(); refreshText(); return this;
    }
    public TextureProgressBar setValue(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Invalid progress value");
        if (DebugBridge.previewWrite(this, "value", value)) return this;
        double old = values.getValue(); setValueNoSignal(value);
        if (old != values.getValue()) changed.emit(values.getValue());
        return this;
    }
    public TextureProgressBar setValueNoSignal(double value) {
        double old = values.getValue(); values.setValue(value);
        if (old != values.getValue()) clearGeometry();
        refreshText(); return this;
    }

    @io.github.luyingyilong.ore.gui.UiProperty
    public TextureSource getUnderTexture() { return underTexture; }
    @io.github.luyingyilong.ore.gui.UiProperty
    public TextureSource getProgressTexture() { return progressTexture; }
    @io.github.luyingyilong.ore.gui.UiProperty
    public TextureSource getOverTexture() { return overTexture; }
    public TextureProgressBar setUnderTexture(TextureSource source) { checkTexture(source); if (!Objects.equals(underTexture, source)) { underTexture = source; imageChanged(); } return this; }
    public TextureProgressBar setProgressTexture(TextureSource source) { checkTexture(source); if (!Objects.equals(progressTexture, source)) { progressTexture = source; imageChanged(); } return this; }
    public TextureProgressBar setOverTexture(TextureSource source) { checkTexture(source); if (!Objects.equals(overTexture, source)) { overTexture = source; imageChanged(); } return this; }
    private void checkTexture(TextureSource source) {
        getSignalScope().checkThread();
        if (!isAlive()) throw new IllegalStateException("Cannot update an ending progress view");
        if (source != null) source.resolve(this);
    }
    public TextureRegion getResolvedUnderTexture() { return resolve(underTexture); }
    public TextureRegion getResolvedProgressTexture() { return resolve(progressTexture); }
    public TextureRegion getResolvedOverTexture() { return resolve(overTexture); }
    private TextureRegion resolve(TextureSource source) { return source == null ? null : source.resolve(this); }
    @io.github.luyingyilong.ore.gui.UiProperty
    public Offset getProgressOffset() {
        return new Offset(DebugBridge.value(this, "progressOffsetX", progressOffset.x()), DebugBridge.value(this, "progressOffsetY", progressOffset.y()));
    }
    public TextureProgressBar setProgressOffset(Offset offset) {
        if (!progressOffset.equals(Objects.requireNonNull(offset))) { progressOffset = offset; imageChanged(); } return this;
    }
    @Inspectable(group = "Progress") public FillMode getFillMode() { return DebugBridge.value(this, "fillMode", fillMode); }
    public TextureProgressBar setFillMode(FillMode mode) {
        Objects.requireNonNull(mode); if (DebugBridge.previewWrite(this, "fillMode", mode)) return this;
        if (fillMode != mode) { fillMode = mode; clearGeometry(); } return this;
    }
    @Inspectable(group = "Image") public StretchMode getStretchMode() { return DebugBridge.value(this, "stretchMode", stretchMode); }
    public TextureProgressBar setStretchMode(StretchMode mode) {
        Objects.requireNonNull(mode); if (DebugBridge.previewWrite(this, "stretchMode", mode)) return this;
        if (stretchMode != mode) { stretchMode = mode; clearGeometry(); } return this;
    }
    @Inspectable(group = "Image") public TextureRect.ExpandMode getExpandMode() { return DebugBridge.value(this, "expandMode", expandMode); }
    public TextureProgressBar setExpandMode(TextureRect.ExpandMode mode) {
        Objects.requireNonNull(mode); if (DebugBridge.previewWrite(this, "expandMode", mode)) return this;
        if (expandMode != mode) { expandMode = mode; imageChanged(); } return this;
    }
    @Inspectable(group = "Image", min = 0, max = 1) public float getOpacity() { return DebugBridge.value(this, "opacity", opacity); }
    public TextureProgressBar setOpacity(float opacity) {
        if (!Float.isFinite(opacity) || opacity < 0 || opacity > 1) throw new IllegalArgumentException("Opacity must be between 0 and 1");
        if (!DebugBridge.previewWrite(this, "opacity", opacity)) { this.opacity = opacity; synchronizeText(); } return this;
    }
    @Inspectable(group = "Progress") public double getRadialInitialAngle() { return DebugBridge.value(this, "radialInitialAngle", radialInitialAngle); }
    public TextureProgressBar setRadialInitialAngle(double angle) {
        if (!Double.isFinite(angle)) throw new IllegalArgumentException("Invalid radial angle");
        double normalized = angle >= 0 && angle < 360 ? angle : ((angle % 360) + 360) % 360;
        if (!DebugBridge.previewWrite(this, "radialInitialAngle", normalized)) { radialInitialAngle = normalized; clearGeometry(); } return this;
    }
    @Inspectable(group = "Progress", min = 0, max = 360) public double getRadialFillDegrees() { return DebugBridge.value(this, "radialFillDegrees", radialFillDegrees); }
    public TextureProgressBar setRadialFillDegrees(double degrees) {
        if (!Double.isFinite(degrees) || degrees < 0 || degrees > 360) throw new IllegalArgumentException("Radial fill must be between 0 and 360 degrees");
        if (!DebugBridge.previewWrite(this, "radialFillDegrees", degrees)) { radialFillDegrees = degrees; clearGeometry(); } return this;
    }
    @io.github.luyingyilong.ore.gui.UiProperty
    public Offset getRadialCenterOffset() {
        return new Offset(DebugBridge.value(this, "radialCenterOffsetX", radialCenterOffset.x()), DebugBridge.value(this, "radialCenterOffsetY", radialCenterOffset.y()));
    }
    public TextureProgressBar setRadialCenterOffset(Offset offset) { radialCenterOffset = Objects.requireNonNull(offset); clearGeometry(); return this; }
    @Inspectable(group = "Text") public boolean getShowPercentage() { return DebugBridge.value(this, "showPercentage", showPercentage); }
    public TextureProgressBar setShowPercentage(boolean show) {
        if (!DebugBridge.previewWrite(this, "showPercentage", show)) { showPercentage = show; percentage.setVisible(show); requestLayout(); } return this;
    }
    private void refreshText() { percentage.setText(Component.literal(Math.round(getFraction() * 100) + "%")); }
    private void synchronizeText() {
        percentage.setVisible(getShowPercentage()); refreshText();
        int color = getThemeItem(FONT_COLOR);
        percentage.setThemeOverride(OreTheme.FONT_COLOR, (color & 0xFFFFFF) | (Math.round((color >>> 24) * getOpacity()) << 24));
        percentage.setThemeOverride(OreTheme.FONT_SIZE, getThemeItem(FONT_SIZE));
    }
    private void clearGeometry() { cachedKey = null; cachedGeometry = null; }
    private void imageChanged() { clearGeometry(); requestLayout(); }
    @Override protected void notification(ControlNotification notification) {
        if (notification.type() == ControlNotification.Type.THEME_CHANGED) clearGeometry();
        if (notification.type() == ControlNotification.Type.PRE_FREE) { underTexture = progressTexture = overTexture = null; clearGeometry(); }
    }

    private record Reference(double left, double top, double width, double height) { }
    private Reference reference(TextureRegion under, TextureRegion progress, TextureRegion over, Offset offset) {
        double left = progress == null ? 0 : Math.min(0, offset.x()), top = progress == null ? 0 : Math.min(0, offset.y());
        double right = Math.max(under == null ? 0 : under.getWidth(), over == null ? 0 : over.getWidth());
        double bottom = Math.max(under == null ? 0 : under.getHeight(), over == null ? 0 : over.getHeight());
        if (progress != null) { right = Math.max(right, (double) offset.x() + progress.getWidth()); bottom = Math.max(bottom, (double) offset.y() + progress.getHeight()); }
        return new Reference(left, top, right - left, bottom - top);
    }
    private Reference reference() { return reference(getResolvedUnderTexture(), getResolvedProgressTexture(), getResolvedOverTexture(), getProgressOffset()); }
    private static int intrinsic(double value) { return (int) Math.min(Integer.MAX_VALUE, Math.ceil(value)); }
    @Override public int getMinimumWidth() { return Math.max(super.getMinimumWidth(), getExpandMode() == TextureRect.ExpandMode.KEEP_SIZE ? intrinsic(reference().width()) : 0); }
    @Override public int getMinimumHeight() { return Math.max(super.getMinimumHeight(), getExpandMode() == TextureRect.ExpandMode.KEEP_SIZE ? intrinsic(reference().height()) : 0); }
    @Override public int measureWidth(Font font, int available) { synchronizeText(); return Math.max(getMinimumWidth(), getShowPercentage() ? percentage.measureWidth(font, available) : 0); }
    @Override public int measureHeight(Font font, int available) { synchronizeText(); return Math.max(getMinimumHeight(), getShowPercentage() ? percentage.measureHeight(font, available) : 0); }
    @Override public void layout(Rect bounds, Font font) { super.layout(bounds, font); synchronizeText(); percentage.layout(new Rect(0, 0, bounds.width(), bounds.height()), font); }

    private record GeometryKey(Rect area, TextureRegion under, TextureRegion progress, TextureRegion over, Offset offset,
            FillMode fill, StretchMode stretch, double fraction, double angle, double degrees, Offset center) { }
    public record DrawGeometry(TextureRegion under, List<TexturePainter.Quad> underQuads,
            TextureRegion progress, List<TexturePainter.Triangle> progressTriangles,
            TextureRegion over, List<TexturePainter.Quad> overQuads) { }

    /** Immutable cached geometry; also consumed by diagnostics and CPU-only regression tests. */
    public DrawGeometry getDrawGeometry(Rect area) {
        GeometryKey key = new GeometryKey(area, getResolvedUnderTexture(), getResolvedProgressTexture(), getResolvedOverTexture(),
                getProgressOffset(), getFillMode(), getStretchMode(), getFraction(), getRadialInitialAngle(), getRadialFillDegrees(), getRadialCenterOffset());
        if (key.equals(cachedKey)) return cachedGeometry;
        Reference reference = reference(key.under, key.progress, key.over, key.offset);
        double sx = 1, sy = 1, x = area.x(), y = area.y();
        if (reference.width <= 0 || reference.height <= 0 || area.width() == 0 || area.height() == 0)
            return cache(key, new DrawGeometry(key.under, List.of(), key.progress, List.of(), key.over, List.of()));
        if (key.stretch != StretchMode.KEEP) {
            sx = area.width() / reference.width; sy = area.height() / reference.height;
            if (key.stretch == StretchMode.KEEP_ASPECT_CENTERED) {
                sx = sy = Math.min(sx, sy); x += (area.width() - reference.width * sx) / 2; y += (area.height() - reference.height * sy) / 2;
            }
        }
        x -= reference.left * sx; y -= reference.top * sy;
        double cx = key.progress == null ? 0 : Math.max(0, Math.min(key.progress.getWidth(), key.progress.getWidth() / 2.0 + key.center.x()));
        double cy = key.progress == null ? 0 : Math.max(0, Math.min(key.progress.getHeight(), key.progress.getHeight() / 2.0 + key.center.y()));
        return cache(key, new DrawGeometry(key.under, quad(key.under, x, y, sx, sy), key.progress,
                TextureProgressGeometry.build(key.progress, key.fill, key.fraction, key.angle, key.degrees, cx, cy,
                        x + key.offset.x() * sx, y + key.offset.y() * sy, sx, sy), key.over, quad(key.over, x, y, sx, sy)));
    }
    private DrawGeometry cache(GeometryKey key, DrawGeometry geometry) { cachedKey = key; return cachedGeometry = geometry; }
    private static List<TexturePainter.Quad> quad(TextureRegion image, double x, double y, double sx, double sy) {
        return image == null ? List.of() : List.of(new TexturePainter.Quad((float) x, (float) y, (float) (image.getWidth() * sx), (float) (image.getHeight() * sy),
                image.getX(), image.getY(), image.getX() + image.getWidth(), image.getY() + image.getHeight()));
    }
    @Override protected void render(DrawContext context, int x, int y) {
        DrawGeometry geometry = getDrawGeometry(getScreenBounds(x, y));
        if (geometry.under != null) TexturePainter.draw(context, geometry.under, geometry.underQuads, getThemeItem(UNDER_TINT), getOpacity());
        if (geometry.progress != null) TexturePainter.drawTriangles(context, geometry.progress, geometry.progressTriangles, getThemeItem(PROGRESS_TINT), getOpacity());
        if (geometry.over != null) TexturePainter.draw(context, geometry.over, geometry.overQuads, getThemeItem(OVER_TINT), getOpacity());
    }
    @Override public void debugProperties(List<DebugProperty> out) {
        super.debugProperties(out);
        out.add(new DebugProperty(this, "min", "Progress", "number", List.of(), this::getMin, -Double.MAX_VALUE, Double.MAX_VALUE,
                value -> ProgressValueModel.validateLimits((Double) value, getMax())));
        out.add(new DebugProperty(this, "max", "Progress", "number", List.of(), this::getMax, -Double.MAX_VALUE, Double.MAX_VALUE,
                value -> ProgressValueModel.validateLimits(getMin(), (Double) value)));
        out.add(DebugProperty.number(this, "progressOffsetX", "Progress", () -> getProgressOffset().x(), Integer.MIN_VALUE, Integer.MAX_VALUE));
        out.add(DebugProperty.number(this, "progressOffsetY", "Progress", () -> getProgressOffset().y(), Integer.MIN_VALUE, Integer.MAX_VALUE));
        out.add(DebugProperty.number(this, "radialCenterOffsetX", "Progress", () -> getRadialCenterOffset().x(), Integer.MIN_VALUE, Integer.MAX_VALUE));
        out.add(DebugProperty.number(this, "radialCenterOffsetY", "Progress", () -> getRadialCenterOffset().y(), Integer.MIN_VALUE, Integer.MAX_VALUE));
        out.add(DebugProperty.themeColor(this, UNDER_TINT, "Image")); out.add(DebugProperty.themeColor(this, PROGRESS_TINT, "Image")); out.add(DebugProperty.themeColor(this, OVER_TINT, "Image"));
    }
}
