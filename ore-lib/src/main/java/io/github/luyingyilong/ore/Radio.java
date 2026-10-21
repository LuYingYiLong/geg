package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.debug.Inspectable;
import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.render.TextureRegion;
import io.github.luyingyilong.ore.render.TextureSource;
import io.github.luyingyilong.ore.render.Transform2D;
import io.github.luyingyilong.ore.signal.Signal;
import io.github.luyingyilong.ore.signal.Signal0;
import io.github.luyingyilong.ore.signal.SignalSource;
import io.github.luyingyilong.ore.signal.SignalSource0;
import io.github.luyingyilong.ore.theme.*;
import io.github.luyingyilong.ore.util.ReleaseGesture;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;

/**
 * Single-choice icon and upright text. Group membership is independent of the layout parent.
 */
public final class Radio extends Container {
    public static final ThemeKey<Float> ICON_ROTATION_DEGREES = new ThemeKey<>("Radio", "icon_rotation_degrees",
            Float.class, ThemeKey.Kind.FLOAT, 45F, true, Float::isFinite);
    public static final ThemeKey<TextureRegion> UNCHECKED_NORMAL = icon("unchecked_normal", "radio_unchecked_normal");
    public static final ThemeKey<TextureRegion> UNCHECKED_HOVERED = icon("unchecked_hovered", "radio_unchecked_hover");
    public static final ThemeKey<TextureRegion> UNCHECKED_PRESSED = icon("unchecked_pressed", "radio_unchecked_pressed");
    public static final ThemeKey<TextureRegion> UNCHECKED_DISABLED = icon("unchecked_disabled", "radio_unchecked_disabled");
    public static final ThemeKey<TextureRegion> CHECKED_NORMAL = icon("checked_normal", "radio_checked_normal");
    public static final ThemeKey<TextureRegion> CHECKED_HOVERED = icon("checked_hovered", "radio_checked_hover");
    public static final ThemeKey<TextureRegion> CHECKED_PRESSED = icon("checked_pressed", "radio_checked_pressed");
    public static final ThemeKey<TextureRegion> CHECKED_DISABLED = icon("checked_disabled", "radio_checked_disabled");
    private final Label label = ownPart("label", new Label().setOpticalCenter(true)
            .setAlignment(Label.Horizontal.LEFT, Label.Vertical.CENTER));
    private final TextureRect image = ownPart("icon", new TextureRect().setExpandMode(TextureRect.ExpandMode.IGNORE_SIZE)
            .setStretchMode(TextureRect.StretchMode.KEEP_CENTERED));
    private final ReleaseGesture<Boolean> gesture = new ReleaseGesture<>();
    private final SignalSource<Boolean> toggled = new SignalSource<>(this, "toggled", "Boolean");
    private final SignalSource0 activated = new SignalSource0(this, "activated");
    RadioGroup<?> group;
    private boolean checked, disabled;
    public Radio() {
        this(Component.empty());
    }
    public Radio(Component text) {
        label.setText(Objects.requireNonNull(text));
        updateChildren(List.of(label));
        setMinimum(0, 18);
        setFocusable(true);
        image.setTexture(TextureSource.themed(UNCHECKED_NORMAL));
    }

    private static ThemeKey<TextureRegion> icon(String name, String file) {
        return IconTheme.texture("Radio", name, file, 8, 8);
    }

    public static void registerTheme(ThemeDB db) {
        OreTheme.registerType(db, "Radio");
        db.register(ICON_ROTATION_DEGREES);
        for (var key : List.of(UNCHECKED_NORMAL, UNCHECKED_HOVERED, UNCHECKED_PRESSED, UNCHECKED_DISABLED,
                CHECKED_NORMAL, CHECKED_HOVERED, CHECKED_PRESSED, CHECKED_DISABLED))
            db.register(key);
    }

    @Override
    protected void validateChildren(List<Control> children) {
        if (!isUpdatingChildren()) throw new IllegalStateException("Radio has a fixed skeleton; configure its text");
    }

    public Component getText() {
        return label.getText();
    }

    public Radio setText(Component text) {
        label.setText(Objects.requireNonNull(text));
        return this;
    }

    public boolean getChecked() {
        return checked;
    }

    public Radio setChecked(boolean value) {
        getSignalScope().checkThread();
        if (!isAlive()) throw new IllegalStateException("Radio is no longer alive");
        if (group != null) group.setRadioChecked(this, value, true);
        else if (checked != value) {
            checked = value;
            toggled.emit(value);
        }
        return this;
    }

    public Radio setCheckedNoSignal(boolean value) {
        getSignalScope().checkThread();
        if (!isAlive()) throw new IllegalStateException("Radio is no longer alive");
        if (group != null) group.setRadioChecked(this, value, false);
        else checked = value;
        return this;
    }

    void setGroupChecked(boolean value) {
        checked = value;
    }

    void emitToggled(boolean value) {
        if (isAlive()) toggled.emit(value);
    }

    public Signal<Boolean> toggled() {
        return toggled.signal();
    }

    public Signal0 activated() {
        return activated.signal();
    }

    public boolean getDisabled() {
        return disabled;
    }

    public Radio setDisabled(boolean value) {
        disabled = value;
        label.setThemeTypeVariation(value ? "LabelDisabled" : "");
        if (value) {
            cancelInteraction();
            var window = getWindowContext().getOwner();
            if (window != null) window.getFocusManager().forgetSubtree(this);
        }
        return this;
    }

    @Inspectable(group = "Appearance")
    public float getIconRotationDegrees() {
        return DebugBridge.value(this, "iconRotationDegrees", getThemeItem(ICON_ROTATION_DEGREES));
    }

    public Radio setIconRotationDegrees(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Angle must be finite");
        if (DebugBridge.previewWrite(this, "iconRotationDegrees", value)) return this;
        setThemeProperty(ICON_ROTATION_DEGREES, value);
        return this;
    }

    public Radio clearIconRotationDegrees() {
        clearThemeProperty(ICON_ROTATION_DEGREES);
        return this;
    }

    public TextureRect getIconControl() {
        return image;
    }

    private int iconExtent() {
        double r = Math.toRadians(getIconRotationDegrees() % 360);
        return (int) Math.ceil(8 * (Math.abs(Math.cos(r)) + Math.abs(Math.sin(r))) - 1e-9);
    }

    @Override
    public int getMinimumWidth() {
        return Math.max(super.getMinimumWidth(), iconExtent());
    }

    @Override
    public int getMinimumHeight() {
        return Math.max(super.getMinimumHeight(), Math.max(18, iconExtent()));
    }

    @Override
    public int measureWidth(Font font, int available) {
        return Math.max(getMinimumWidth(), iconExtent() + (getText().getString().isEmpty() ? 0 : 4 + label.measureWidth(font, available)));
    }

    @Override
    public int measureHeight(Font font, int available) {
        return Math.max(Math.max(getMinimumHeight(), iconExtent()), label.measureHeight(font, available));
    }

    @Override
    public void layout(Rect bounds, Font font) {
        super.layout(bounds, font);
        fitChild(label, new Rect(iconExtent() + 4, 0, Math.max(0, bounds.width() - iconExtent() - 4), bounds.height()), font);
        layoutIcon(font);
    }

    private void layoutIcon(Font font) {
        Rect area = getAbsoluteBounds();
        int extent = iconExtent();
        int left = (extent - 8) / 2, top = (area.height() - 8) / 2;
        image.layout(new Rect(area.x() + left, area.y() + top, 8, 8), font);
        // Keep the visual center exact when the rotated slot or row has an odd size.
        image.setRenderTransform(new Transform2D((extent - 8) / 2.0 - left, (area.height() - 8) / 2.0 - top,
                getIconRotationDegrees(), 1, 0.5, 0.5));
    }

    @Override
    public FocusGeometry getFocusGeometry() {
        layoutIcon(getLayoutFont());
        return FocusGeometry.forControl(image, image.getAbsoluteBounds());
    }

    @Override
    public String getThemeState() {
        return disabled ? "disabled" : checked ? "checked" : "unchecked";
    }

    @Override
    protected void render(DrawContext context, int x, int y) {
        layoutIcon(context.getFont());
        Rect area = getScreenBounds(x, y);
        boolean hovered = area.contains(context.getAbsMouseX(), context.getAbsMouseY());
        boolean pressed = gesture.keyPressed(true) || hovered && gesture.pointerPending();
        ThemeKey<TextureRegion> key = disabled ? (checked ? CHECKED_DISABLED : UNCHECKED_DISABLED)
                : checked ? (pressed ? CHECKED_PRESSED : hovered ? CHECKED_HOVERED : CHECKED_NORMAL)
                : (pressed ? UNCHECKED_PRESSED : hovered ? UNCHECKED_HOVERED : UNCHECKED_NORMAL);
        image.setTexture(TextureSource.themed(key));
        image.draw(context, x, y);
    }

    @Override
    public boolean acceptsFocus() {
        return !disabled && super.acceptsFocus();
    }

    @Override
    protected boolean pointerDown(int x, int y) {
        if (disabled || !hit(x, y)) return false;
        gesture.pointerDown(true);
        return true;
    }

    @Override
    protected boolean pointerUp(boolean over) {
        var result = gesture.pointerUp(true, over && !disabled && getVisible());
        if (result.target() != null) activate();
        return result.handled();
    }

    @Override
    public boolean onActivationPressed(int key) {
        if (!acceptsFocus()) return false;
        gesture.keyDown(key, true);
        return true;
    }

    @Override
    public boolean onActivationReleased(int key) {
        var result = gesture.keyUp(key, acceptsFocus() ? true : null);
        if (result.target() != null) activate();
        return result.handled();
    }

    @Override
    public boolean activate() {
        if (!isAlive() || disabled || !getVisible()) return false;
        OreSounds.play(OreSounds.Cue.CLICK);
        setChecked(true);
        if (isAlive()) activated.emit();
        return true;
    }

    @Override
    public void cancelInteraction() {
        gesture.cancel();
    }

    @Override
    public void onFocusChanged(boolean focused) {
        if (!focused) cancelInteraction();
    }

    @Override
    protected void notification(ControlNotification event) {
        if (event.type() == ControlNotification.Type.PRE_FREE && group != null) group.removeRadio(this);
    }
}
