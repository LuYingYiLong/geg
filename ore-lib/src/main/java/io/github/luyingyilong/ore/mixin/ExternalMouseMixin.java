package io.github.luyingyilong.ore.mixin;

import io.github.luyingyilong.ore.window.ScreenInputBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MouseHandler.class, priority = 1200, remap = false)
public abstract class ExternalMouseMixin {
    private static int ore$x() {
        var mc = Minecraft.getInstance();
        return (int) (mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth());
    }
    private static int ore$y() {
        var mc = Minecraft.getInstance();
        return (int) (mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight());
    }
    @Inject(method = {"onPress", "m_91530_"}, at = @At("HEAD"), cancellable = true)
    private void ore$button(long window, int button, int action, int modifiers, CallbackInfo callback) {
        if (ScreenInputBridge.getEarlyRouting(window)
                && ScreenInputBridge.pointer(ore$x(), ore$y(), button, action == GLFW.GLFW_PRESS, modifiers, true)) callback.cancel();
    }
    @Inject(method = {"onScroll", "m_91526_"}, at = @At("HEAD"), cancellable = true)
    private void ore$scroll(long window, double x, double y, CallbackInfo callback) {
        if (ScreenInputBridge.getEarlyRouting(window) && ScreenInputBridge.scroll(ore$x(), ore$y(), x, y, true)) callback.cancel();
    }
    @Inject(method = {"onPress", "m_91530_"}, at = @At("RETURN"))
    private void ore$buttonEnd(long window, int button, int action, int modifiers, CallbackInfo callback) {
        ScreenInputBridge.endEarly();
    }
    @Inject(method = {"onScroll", "m_91526_"}, at = @At("RETURN"))
    private void ore$scrollEnd(long window, double x, double y, CallbackInfo callback) {
        ScreenInputBridge.endEarly();
    }
}
