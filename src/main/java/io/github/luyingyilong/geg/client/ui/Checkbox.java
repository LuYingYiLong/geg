package io.github.luyingyilong.geg.client.ui;

import brachy.modularui.screen.viewport.ModularGuiContext;
import io.github.luyingyilong.geg.client.ui.layout.Container;
import io.github.luyingyilong.geg.client.ui.layout.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.function.Consumer;

/** Transparent icon-and-label toggle. Its whole assigned rectangle is clickable. */
public final class Checkbox extends Container {
    private static final int ICON_SIZE = 12;
    private static final int ICON_GAP = 4;
    private static final int HEIGHT = 18;

    private final Label label;
    private boolean checked;
    private boolean enabled = true;
    private Consumer<Boolean> onChange = value -> { };

    public Checkbox(Component text) {
        label = new Label(Objects.requireNonNull(text))
                .overflow(Label.Overflow.ELLIPSIS)
                .align(Label.Horizontal.LEFT, Label.Vertical.CENTER);
        addChild(label);
        minimum(ICON_SIZE, HEIGHT);
        focusable(true);
        focusExpansion(1);
    }

    public Checkbox text(Component text) {
        label.text(Objects.requireNonNull(text));
        return this;
    }

    public boolean checked() {
        return checked;
    }

    /** Programmatic changes do not fire the user-change callback. */
    public Checkbox checked(boolean checked) {
        this.checked = checked;
        return this;
    }

    public Checkbox enabled(boolean enabled) {
        this.enabled = enabled;
        label.color(enabled ? 0xFFD8D9DC : 0xFF858585);
        return this;
    }

    public Checkbox onChange(Consumer<Boolean> onChange) {
        this.onChange = Objects.requireNonNull(onChange);
        return this;
    }

    @Override public int measureWidth(Font font, int available) {
        return Math.max(minimumWidth(), ICON_SIZE + ICON_GAP + label.measureWidth(font, available));
    }

    @Override public int measureHeight(Font font, int available) {
        return Math.max(minimumHeight(), label.measureHeight(font, available));
    }

    @Override public void layout(Rect bounds, Font font) {
        super.layout(bounds, font);
        fitChild(label, new Rect(ICON_SIZE + ICON_GAP, 0,
                Math.max(0, bounds.width() - ICON_SIZE - ICON_GAP), bounds.height()), font);
    }

    @Override protected void render(ModularGuiContext context, int screenX, int screenY) {
        Rect area = screenBounds(screenX, screenY);
        boolean hovered = area.contains(context.getAbsMouseX(), context.getAbsMouseY());
        ResourceLocation texture = texture(hovered, hovered && Button.leftMousePressed());
        int y = area.y() + (area.height() - ICON_SIZE) / 2;
        context.getGraphics().setColor(1.0F, 1.0F, 1.0F, 1.0F);
        context.getGraphics().blit(texture, area.x(), y, 0.0F, 0.0F,
                ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
    }

    @Override protected boolean onPointerPressed(int rootX, int rootY) {
        if (!enabled || !hit(rootX, rootY)) return false;
        return activate();
    }

    @Override public boolean acceptsFocus() { return enabled && super.acceptsFocus(); }

    @Override public boolean activate() {
        if (!enabled) return false;
        checked = !checked;
        onChange.accept(checked);
        return true;
    }

    private ResourceLocation texture(boolean hovered, boolean pressed) {
        if (!enabled) return checked ? OreIcons.CHECKBOX_CHECKED_DISABLED : OreIcons.CHECKBOX_DISABLED;
        if (checked) return pressed ? OreIcons.CHECKBOX_CHECKED_PRESSED
                : hovered ? OreIcons.CHECKBOX_CHECKED_HOVERED : OreIcons.CHECKBOX_CHECKED_NORMAL;
        return hovered ? OreIcons.CHECKBOX_HOVERED : OreIcons.CHECKBOX_NORMAL;
    }
}
