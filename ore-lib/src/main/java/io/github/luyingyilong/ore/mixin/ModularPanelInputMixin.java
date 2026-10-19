package io.github.luyingyilong.ore.mixin;

import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.viewport.LocatedWidget;
import brachy.modularui.screen.viewport.ModularGuiContext;
import io.github.luyingyilong.ore.window.WindowContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep Window's native-field focus when MUI remembers the Canvas as press/drag owner. */
@Mixin(value=ModularPanel.class,remap=false)
public abstract class ModularPanelInputMixin {
    @Inject(method="lambda$onMousePressed$3",at=@At("HEAD"))
    private void ore$beginPointer(int button,CallbackInfoReturnable<Boolean> callback) {
        WindowContext.of(((ModularPanel<?>)(Object)this).getContext()).pointerFocusOwned(false);
    }
    @Redirect(method="lambda$onMousePressed$3",at=@At(value="INVOKE",target="Lbrachy/modularui/screen/viewport/ModularGuiContext;removeFocus()V"))
    private void ore$preservePointerFocus(ModularGuiContext context) {
        if(!WindowContext.of(context).pointerFocusOwned())context.removeFocus();
    }
    @Redirect(method="lambda$onMousePressed$3",at=@At(value="INVOKE",target="Lbrachy/modularui/screen/viewport/ModularGuiContext;focus(Lbrachy/modularui/screen/viewport/LocatedWidget;)V"))
    private void ore$preserveNativeField(ModularGuiContext context,LocatedWidget widget) {
        if(!WindowContext.of(context).pointerFocusOwned())context.focus(widget);
    }
    @Inject(method="lambda$onMousePressed$3",at=@At("RETURN"))
    private void ore$endPointer(int button,CallbackInfoReturnable<Boolean> callback) {
        WindowContext.of(((ModularPanel<?>)(Object)this).getContext()).pointerFocusOwned(false);
    }
}
