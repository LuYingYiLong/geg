package io.github.luyingyilong.ore.control.input;

import io.github.luyingyilong.ore.control.display.Label;
import io.github.luyingyilong.ore.control.display.TextureRect;
import io.github.luyingyilong.ore.theme.IconTheme;
import io.github.luyingyilong.ore.theme.OreSounds;
import io.github.luyingyilong.ore.theme.OreTheme;
import io.github.luyingyilong.ore.theme.ThemeDB;
import io.github.luyingyilong.ore.theme.ThemeKey;

import io.github.luyingyilong.ore.control.base.Container;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.render.TextureRegion;
import io.github.luyingyilong.ore.render.TextureSource;
import io.github.luyingyilong.ore.signal.Signal;
import io.github.luyingyilong.ore.signal.SignalSource;
import io.github.luyingyilong.ore.event.gesture.ReleaseGesture;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/**
 * Transparent icon-and-label toggle. Its whole assigned rectangle is clickable.
 */
public final class Checkbox extends Container {

    public static final ThemeKey<TextureRegion> UNCHECKED_NORMAL = IconTheme.texture("Checkbox", "unchecked_normal", "checkbox_normal", 12, 12);
    public static final ThemeKey<TextureRegion> UNCHECKED_HOVERED = IconTheme.texture("Checkbox", "unchecked_hovered", "checkbox_hover", 12, 12);
    public static final ThemeKey<TextureRegion> UNCHECKED_DISABLED = IconTheme.texture("Checkbox", "unchecked_disabled", "checkbox_disabled", 12, 12);
    public static final ThemeKey<TextureRegion> CHECKED_NORMAL = IconTheme.texture("Checkbox", "checked_normal", "checkbox_checked_normal", 12, 12);
    public static final ThemeKey<TextureRegion> CHECKED_HOVERED = IconTheme.texture("Checkbox", "checked_hovered", "checkbox_checked_hover", 12, 12);
    public static final ThemeKey<TextureRegion> CHECKED_PRESSED = IconTheme.texture("Checkbox", "checked_pressed", "checkbox_checked_pressed", 12, 12);
    public static final ThemeKey<TextureRegion> CHECKED_DISABLED = IconTheme.texture("Checkbox", "checked_disabled", "checkbox_checked_disabled", 12, 12);
    private static final int ICON_SIZE = 12;
    private static final int ICON_GAP = 4;
    private static final int HEIGHT = 18;
    private final Label label;
    private final TextureRect image = ownPart("icon", new TextureRect().setStretchMode(TextureRect.StretchMode.SCALE).setExpandMode(TextureRect.ExpandMode.IGNORE_SIZE));
    private final SignalSource<Boolean> toggled = new SignalSource<>(this, "toggled", "Boolean");
    private final ReleaseGesture<Boolean> gesture = new ReleaseGesture<>();
    private boolean checked;
    private boolean enabled = true;

    public Checkbox(Component text) {
        label = ownPart("label", new Label(Objects.requireNonNull(text)).setOpticalCenter(true)
                .setOverflow(Label.Overflow.ELLIPSIS)
                .setAlignment(Label.Horizontal.LEFT, Label.Vertical.CENTER));
        updateChildren(java.util.List.of(label));
        setMinimum(ICON_SIZE, HEIGHT);
        setFocusable(true);

    }

    public static void registerTheme(ThemeDB db) {
        OreTheme.registerType(db, "Checkbox");
        db.register(UNCHECKED_NORMAL);
        db.register(UNCHECKED_HOVERED);
        db.register(UNCHECKED_DISABLED);
        db.register(CHECKED_NORMAL);
        db.register(CHECKED_HOVERED);
        db.register(CHECKED_PRESSED);
        db.register(CHECKED_DISABLED);
    }

    @Override
    public String getThemeState() {
        return !enabled ? "disabled" : checked ? "checked" : "unchecked";
    }

    @Override
    protected void validateChildren(java.util.List<io.github.luyingyilong.ore.control.base.Control> proposed) {
        if (!isUpdatingChildren())
            throw new IllegalStateException("Checkbox has a fixed skeleton; configure its properties");
    }

    public Checkbox setText(Component text) {
        label.setText(Objects.requireNonNull(text));
        return this;
    }

    public boolean getChecked() {
        return checked;
    }

    /**
     * State setters notify only when the value changes.
     */
    public Checkbox setChecked(boolean checked) {
        if (this.checked == checked) return this;
        this.checked = checked;
        toggled.emit(checked);
        return this;
    }

    public Checkbox setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) cancelInteraction();
        label.setThemeTypeVariation(enabled ? "" : "LabelDisabled");
        return this;
    }

    public Signal<Boolean> toggled() {
        return toggled.signal();
    }

    public Checkbox setCheckedNoSignal(boolean value) {
        checked = value;
        return this;
    }

    @Override
    public int measureWidth(Font font, int available) {
        if (!isAlive()) return 0;
        return Math.max(getMinimumWidth(), ICON_SIZE + ICON_GAP + label.measureWidth(font, available));
    }

    @Override
    public int measureHeight(Font font, int available) {
        if (!isAlive()) return 0;
        return Math.max(getMinimumHeight(), label.measureHeight(font, available));
    }

    @Override
    public void layout(Rect bounds, Font font) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            super.layout(bounds, font);
            fitChild(label, new Rect(ICON_SIZE + ICON_GAP, 0,
                    Math.max(0, bounds.width() - ICON_SIZE - ICON_GAP), bounds.height()), font);
        }
    }

    @Override
    protected void render(DrawContext context, int screenX, int screenY) {
        Rect area = getScreenBounds(screenX, screenY);
        boolean hovered = area.contains(context.getAbsMouseX(), context.getAbsMouseY());
        var texture = getTextureKey(hovered, gesture.keyPressed(true)
                || hovered && gesture.pointerPending());
        int y = area.y() + (area.height() - ICON_SIZE) / 2;
        image.setTexture(TextureSource.themed(texture));
        image.layout(new Rect(area.x(), y, ICON_SIZE, ICON_SIZE), context.getFont());
        image.draw(context, 0, 0);
    }

    @Override
    protected boolean pointerDown(int rootX, int rootY) {
        if (!enabled || !hit(rootX, rootY)) return false;

        gesture.pointerDown(true);
        return true;
    }

    @Override
    protected boolean pointerUp(boolean over) {
        var release = gesture.pointerUp(true, over && enabled && getVisible());
        if (release.target() != null) activate();
        return release.handled();
    }

    @Override
    public boolean onActivationPressed(int keyCode) {
        if (!enabled || !getVisible()) return false;
        gesture.keyDown(keyCode, true);
        return true;
    }

    @Override
    public boolean onActivationReleased(int keyCode) {
        var release = gesture.keyUp(keyCode, enabled && getVisible() ? true : null);
        if (release.target() != null) activate();
        return release.handled();
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
    public boolean acceptsFocus() {
        return enabled && super.acceptsFocus();
    }

    @Override
    public boolean activate() {
        if (!enabled || !getVisible()) return false;
        OreSounds.play(OreSounds.Cue.CLICK);
        setChecked(!checked);
        return true;
    }

    private ThemeKey<TextureRegion> getTextureKey(boolean hovered, boolean pressed) {
        if (!enabled) return checked ? CHECKED_DISABLED : UNCHECKED_DISABLED;
        if (checked) return pressed ? CHECKED_PRESSED
                : hovered ? CHECKED_HOVERED : CHECKED_NORMAL;
        return hovered ? UNCHECKED_HOVERED : UNCHECKED_NORMAL;
    }
}
