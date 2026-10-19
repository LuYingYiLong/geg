package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.signal.*;

import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.layout.Container;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.theme.OreIcons;
import io.github.luyingyilong.ore.theme.OreSounds;
import io.github.luyingyilong.ore.theme.OreTheme;
import io.github.luyingyilong.ore.util.ReleaseGesture;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Transparent icon-and-label toggle. Its whole assigned rectangle is clickable. */
public final class Checkbox extends Container {
    private static final int ICON_SIZE = 12;
    private static final int ICON_GAP = 4;
    private static final int HEIGHT = 18;

    private final Label label;
    private boolean checked;
    private boolean enabled = true;
    private final SignalSource<Boolean> toggled = new SignalSource<>(this, "toggled", "Boolean");
    private final ReleaseGesture<Boolean> gesture = new ReleaseGesture<>();

    public Checkbox(Component text) {
        label = ownPart("label", new Label(Objects.requireNonNull(text)).setOpticalCenter(true).setColor(OreTheme.TEXT_WHITE)
                .setOverflow(Label.Overflow.ELLIPSIS)
                .setAlignment(Label.Horizontal.LEFT, Label.Vertical.CENTER));
        updateChildren(java.util.List.of(label));
        setMinimum(ICON_SIZE, HEIGHT);
        setFocusable(true);
        setFocusExpansion(1);
    }

    @Override protected void validateChildren(java.util.List<io.github.luyingyilong.ore.layout.Control> proposed) {
        if (!isUpdatingChildren()) throw new IllegalStateException("Checkbox has a fixed skeleton; configure its properties");
    }

    public Checkbox setText(Component text) {
        label.setText(Objects.requireNonNull(text));
        return this;
    }

    public boolean getChecked() {
        return checked;
    }

    /** State setters notify only when the value changes. */
    public Checkbox setChecked(boolean checked) {
        if (this.checked == checked) return this;
        this.checked = checked;
        toggled.emit(checked);
        return this;
    }

    public Checkbox setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) cancelInteraction();
        label.setColor(enabled ? OreTheme.TEXT_WHITE : OreTheme.TEXT_DISABLED);
        return this;
    }

    public Signal<Boolean> toggled() { return toggled.signal(); }
    public Checkbox setCheckedNoSignal(boolean value) { checked = value; return this; }

    @Override public int measureWidth(Font font, int available) {
        if (!isAlive()) return 0;
        return Math.max(getMinimumWidth(), ICON_SIZE + ICON_GAP + label.measureWidth(font, available));
    }

    @Override public int measureHeight(Font font, int available) {
        if (!isAlive()) return 0;
        return Math.max(getMinimumHeight(), label.measureHeight(font, available));
    }

    @Override public void layout(Rect bounds, Font font) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            super.layout(bounds, font);
            fitChild(label, new Rect(ICON_SIZE + ICON_GAP, 0,
                    Math.max(0, bounds.width() - ICON_SIZE - ICON_GAP), bounds.height()), font);
        }
    }

    @Override protected void render(DrawContext context, int screenX, int screenY) {
        Rect area = getScreenBounds(screenX, screenY);
        boolean hovered = area.contains(context.getAbsMouseX(), context.getAbsMouseY());
        ResourceLocation texture = texture(hovered, gesture.keyPressed(true)
                || hovered && gesture.pointerPending());
        int y = area.y() + (area.height() - ICON_SIZE) / 2;
        context.getGraphics().setColor(1.0F, 1.0F, 1.0F, 1.0F);
        context.getGraphics().blit(texture, area.x(), y, 0.0F, 0.0F,
                ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
    }

    @Override protected boolean pointerDown(int rootX, int rootY) {
        if (!enabled || !hit(rootX, rootY)) return false;

        gesture.pointerDown(true);
        return true;
    }

    @Override protected boolean pointerUp(boolean over) {
        var release = gesture.pointerUp(true, over && enabled && getVisible());
        if (release.target() != null) activate();
        return release.handled();
    }

    @Override public boolean onActivationPressed(int keyCode) {
        if (!enabled || !getVisible()) return false;
        gesture.keyDown(keyCode, true);
        return true;
    }

    @Override public boolean onActivationReleased(int keyCode) {
        var release = gesture.keyUp(keyCode, enabled && getVisible() ? true : null);
        if (release.target() != null) activate();
        return release.handled();
    }

    @Override public void cancelInteraction() { gesture.cancel(); }
    @Override public void onFocusChanged(boolean focused) { if (!focused) cancelInteraction(); }

    @Override public boolean acceptsFocus() { return enabled && super.acceptsFocus(); }

    @Override public boolean activate() {
        if (!enabled || !getVisible()) return false;
        OreSounds.play(OreSounds.Cue.CLICK);
        setChecked(!checked);
        return true;
    }

    private ResourceLocation texture(boolean hovered, boolean pressed) {
        if (!enabled) return checked ? OreIcons.CHECKBOX_CHECKED_DISABLED : OreIcons.CHECKBOX_DISABLED;
        if (checked) return pressed ? OreIcons.CHECKBOX_CHECKED_PRESSED
                : hovered ? OreIcons.CHECKBOX_CHECKED_HOVERED : OreIcons.CHECKBOX_CHECKED_NORMAL;
        return hovered ? OreIcons.CHECKBOX_HOVERED : OreIcons.CHECKBOX_NORMAL;
    }
}
