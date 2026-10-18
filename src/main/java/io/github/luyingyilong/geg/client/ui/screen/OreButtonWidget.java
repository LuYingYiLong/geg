package io.github.luyingyilong.geg.client.ui.screen;

import brachy.modularui.api.ITheme;
import brachy.modularui.api.UIType;
import brachy.modularui.screen.viewport.ModularGuiContext;
import io.github.luyingyilong.geg.client.ui.Button;
import io.github.luyingyilong.geg.client.ui.layout.Rect;
import io.github.luyingyilong.geg.client.ui.theme.OreTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;

/** Embeds a real Ore Button in a vanilla screen, without replacing or darkening that screen. */
public final class OreButtonWidget extends AbstractWidget {
    private final Button button;
    private Font font;
    private ModularGuiContext context;
    private boolean pointerHeld;
    private final io.github.luyingyilong.geg.client.ui.window.WindowContext hostContext = new io.github.luyingyilong.geg.client.ui.window.WindowContext(false);

    public OreButtonWidget(Button button, Component narration) {
        super(0, 0, 32, Button.HEIGHT, narration);
        this.button = Objects.requireNonNull(button);
        if (button.parent() != null) throw new IllegalArgumentException("Button already belongs to a control tree");
    }

    public Button button() { return button; }
    public boolean pointerHeld() { return pointerHeld; }

    /** Call on screen initialization, resize or content change; coordinates are GUI viewport pixels. */
    public void place(Font font, int x, int y, int minimumWidth) {
        this.font = Objects.requireNonNull(font);
        setX(x); setY(y);
        setWidth(Math.max(minimumWidth, button.measureWidth(font, Integer.MAX_VALUE)));
        setHeight(button.measureHeight(font, Integer.MAX_VALUE));
        syncLayout();
    }

    private void syncLayout() {
        if (font == null) font = Minecraft.getInstance().font;
        button.layout(new Rect(getX(), getY(), getWidth(), getHeight()), font);
    }

    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!button.visible()) { cancelInput(); return; }
        syncLayout();
        if (context == null) context = new ButtonContext();
        var minecraft = Minecraft.getInstance();
        context.updateScreenArea(minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
        context.updateState(mouseX, mouseY, partialTick);
        context.setOverrideFont(font);
        context.setGraphics(graphics); hostContext.bind(context);
        context.reset();
        context.pushViewport(null, context.getScreenArea());
        boolean disabled = button.disabled();
        if (!active) button.disabled(true);
        try (var host = hostContext.enter()) {
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            button.draw(context, 0, 0);
            if (isFocused() && active && !button.disabled() && minecraft.getLastInputType().isKeyboard()) {
                Rect outline = button.focusBounds();
                int extra = button.focusExpansion();
                graphics.renderOutline(outline.x() - extra, outline.y() - extra,
                        outline.width() + 2 * extra, outline.height() + 2 * extra, 0xFFFFFFFF);
            }
            graphics.flush();
        } finally {
            button.disabled(disabled);
            context.popViewport(null);
            context.setGraphics(null);
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private boolean usable() { return active && visible && button.visible() && !button.disabled(); }

    @Override public boolean isMouseOver(double mouseX, double mouseY) {
        Rect area = button.absoluteBounds();
        return visible && button.visible() && mouseX >= area.x() && mouseX < area.right()
                && mouseY >= area.y() && mouseY < area.bottom();
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        if (mouseButton != 0 || !usable()) return false;
        syncLayout();
        if (!isMouseOver(mouseX, mouseY)) return false;
        pointerHeld = button.pressAt((int) mouseX, (int) mouseY);
        return pointerHeld;
    }

    /** The host also forwards releases outside the widget so dragging out cancels instead of sticking. */
    @Override public boolean mouseReleased(double mouseX, double mouseY, int mouseButton) {
        if (mouseButton != 0 || !pointerHeld) return false;
        pointerHeld = false;
        syncLayout();
        if (usable()) button.releasePointer(isMouseOver(mouseX, mouseY));
        else button.cancelInteraction();
        return true;
    }

    @Override public boolean mouseDragged(double mouseX, double mouseY, int mouseButton, double deltaX, double deltaY) {
        if (mouseButton != 0 || !pointerHeld) return false;
        if (!usable()) { cancelInput(); return true; }
        return button.dragPointer((int) mouseX, (int) mouseY);
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isFocused() || !usable() || !activationKey(keyCode)) return false;
        return button.onActivationPressed(keyCode);
    }

    @Override public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (!isFocused() || !usable() || !activationKey(keyCode)) return false;
        return button.onActivationReleased(keyCode);
    }

    private static boolean activationKey(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE;
    }

    @Override public void setFocused(boolean focused) {
        super.setFocused(focused);
        button.onFocusChanged(focused);
        if (!focused) cancelInput();
    }

    public void cancelInput() { pointerHeld = false; button.cancelInteractions(); }

    @Override public ScreenRectangle getRectangle() {
        Rect area = button.absoluteBounds();
        return new ScreenRectangle(area.x(), area.y(), area.width(), area.height());
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }

    /** Only drawable context services are needed; no ModularScreen or parent replacement is created. */
    private static final class ButtonContext extends ModularGuiContext {
        private ButtonContext() { super(UIType.NONE, null); }
        @Override public ITheme getTheme() { return ITheme.get(OreTheme.NAME); }
    }
}
