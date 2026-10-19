package io.github.luyingyilong.ore.mixin;

import io.github.luyingyilong.ore.window.ScreenInputBridge;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = KeyboardHandler.class, priority = 1200, remap = false)
public abstract class ExternalKeyboardMixin {
    @Inject(method = {"keyPress", "m_90893_"}, at = @At("HEAD"), cancellable = true)
    private void ore$key(long window, int key, int scan, int action, int modifiers, CallbackInfo callback) {
        if (ScreenInputBridge.getEarlyRouting(window) && ScreenInputBridge.key(key, scan, action, modifiers, true)) callback.cancel();
    }
    @Inject(method = {"charTyped", "m_90889_"}, at = @At("HEAD"), cancellable = true)
    private void ore$text(long window, int codePoint, int modifiers, CallbackInfo callback) {
        if (ScreenInputBridge.getEarlyRouting(window)
                && ScreenInputBridge.text(new String(Character.toChars(codePoint)), modifiers, true)) callback.cancel();
    }
    @Inject(method = {"keyPress", "m_90893_"}, at = @At("RETURN"))
    private void ore$keyEnd(long window, int key, int scan, int action, int modifiers, CallbackInfo callback) {
        ScreenInputBridge.endEarly();
    }
    @Inject(method = {"charTyped", "m_90889_"}, at = @At("RETURN"))
    private void ore$textEnd(long window, int codePoint, int modifiers, CallbackInfo callback) {
        ScreenInputBridge.endEarly();
    }
}
