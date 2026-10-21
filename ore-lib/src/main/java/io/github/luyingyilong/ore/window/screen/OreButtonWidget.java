package io.github.luyingyilong.ore.window.screen;

import brachy.modularui.api.ITheme;
import brachy.modularui.api.UIType;
import brachy.modularui.screen.viewport.ModularGuiContext;
import io.github.luyingyilong.ore.control.input.Button;
import io.github.luyingyilong.ore.control.base.Rect;
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
    private final io.github.luyingyilong.ore.window.Window root = new io.github.luyingyilong.ore.window.Window(true);
    private final io.github.luyingyilong.ore.window.WindowContext hostContext = root.getContext();

    public OreButtonWidget(Button button, Component narration) {
        super(0, 0, 32, Button.HEIGHT, narration);
        this.button = Objects.requireNonNull(button);
        if (button.getParent() != null) throw new IllegalArgumentException("Button already belongs to a control tree");
        var contentRoot = new io.github.luyingyilong.ore.control.base.Control();
        contentRoot.addChild(button);
        root.setContent(contentRoot);
    }

    public Button button() { return button; }
    public boolean pointerHeld() { return pointerHeld; }

    /** Disabled buttons remain visible and still reserve space in recipe-viewer overlays. */
    public Rect getExclusionBounds() {
        if (!visible || !root.isAlive() || root.isSuspended() || !button.isAlive() || button.isSuspended() || !button.getVisible())
            return new Rect(0, 0, 0, 0);
        return io.github.luyingyilong.ore.event.EventGeometry.displayed(button);
    }

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
        if (context != null) root.prepareTree();
        int width=context==null?getX()+getWidth():context.getScreenArea().width;
        int height=context==null?getY()+getHeight():context.getScreenArea().height;
        root.layout(new Rect(0,0,Math.max(1,width),Math.max(1,height)),font);
        button.layout(new Rect(getX(), getY(), getWidth(), getHeight()), font);
    }

    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!button.isAlive() || !button.getVisible()) { cancelInput(); return; }
        if(!hostContext.getFocused())cancelInput();
        syncLayout();
        if (context == null) context = new ButtonContext();
        var minecraft = Minecraft.getInstance();
        context.updateScreenArea(minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
        context.updateState(mouseX, mouseY, partialTick);
        context.setOverrideFont(font);
        context.setGraphics(graphics); hostContext.bind(context);
        root.prepareTree();
        syncLayout();
        context.reset();
        context.pushViewport(null, context.getScreenArea());
        boolean disabled = button.getDisabled();
        if (!active) button.setDisabled(true);
        try (var host = hostContext.enter()) {
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            try (var frame = io.github.luyingyilong.ore.window.MuiDrawing.begin(context, hostContext)) {
                button.draw(frame,0,0);
                if(isFocused() && active && !button.getDisabled() && minecraft.getLastInputType().isKeyboard())
                    button.drawFocus(frame,0,0);
            }
            graphics.flush();
        } finally {
            button.setDisabled(disabled);
            context.popViewport(null);
            context.setGraphics(null);
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private boolean usable() { return root.isAlive() && active && visible && button.getVisible() && !button.getDisabled(); }

    @Override public boolean isMouseOver(double mouseX, double mouseY) {
        return visible && io.github.luyingyilong.ore.event.EventGeometry.contains(button,(int)Math.floor(mouseX),(int)Math.floor(mouseY));
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        if (mouseButton != 0 || !usable()) return false;
        syncLayout();
        if (!isMouseOver(mouseX, mouseY)) return false;
        pointerHeld = io.github.luyingyilong.ore.event.WindowInputs.press(root,(int)mouseX,(int)mouseY,context);
        return pointerHeld;
    }

    /** The host also forwards releases outside the widget so dragging out cancels instead of sticking. */
    @Override public boolean mouseReleased(double mouseX, double mouseY, int mouseButton) {
        if (mouseButton != 0 || !pointerHeld) return false;
        pointerHeld = false;
        syncLayout();
        if (usable()) io.github.luyingyilong.ore.event.WindowInputs.release(root,(int)mouseX,(int)mouseY);
        else button.cancelInteraction();
        return true;
    }

    @Override public boolean mouseDragged(double mouseX, double mouseY, int mouseButton, double deltaX, double deltaY) {
        if (mouseButton != 0 || !pointerHeld) return false;
        if (!usable()) { cancelInput(); return true; }
        return io.github.luyingyilong.ore.event.WindowInputs.move(root,(int)mouseX,(int)mouseY);
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isFocused() || !usable() || !activationKey(keyCode)) return false;
        root.getFocusManager().requestFocus(button,null);
        return io.github.luyingyilong.ore.event.WindowInputs.key(root,io.github.luyingyilong.ore.event.KeyEvent.Action.PRESS,keyCode,scanCode,modifiers,context);
    }

    @Override public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (!isFocused() || !usable() || !activationKey(keyCode)) return false;
        return io.github.luyingyilong.ore.event.WindowInputs.key(root,io.github.luyingyilong.ore.event.KeyEvent.Action.RELEASE,keyCode,scanCode,modifiers,context);
    }

    private static boolean activationKey(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE;
    }

    @Override public void setFocused(boolean focused) {
        super.setFocused(focused);
        button.notifyFocusChanged(focused);
        if (!focused) cancelInput();
    }

    public void cancelInput() { pointerHeld = false; root.cancelInteractions(); hostContext.detach(); }

    public void queueFree() { cancelInput(); root.queueFree("host ended"); }

    @Override public ScreenRectangle getRectangle() {
        Rect area = io.github.luyingyilong.ore.event.EventGeometry.displayed(button);
        return new ScreenRectangle(area.x(), area.y(), area.width(), area.height());
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }

    /** Only drawable context services are needed; no ModularScreen or parent replacement is created. */
    private static final class ButtonContext extends ModularGuiContext {
        private ButtonContext() { super(UIType.NONE, null); }
        @Override public ITheme getTheme() { return ITheme.getDefault(); }
    }
}
