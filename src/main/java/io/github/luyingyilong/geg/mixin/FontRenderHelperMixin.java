package io.github.luyingyilong.geg.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(targets = "brachy.modularui.drawable.text.FontRenderHelper", remap = false)
public abstract class FontRenderHelperMixin {
    // Other mods add formatting codes outside ModularUI's assumed '0'...'r' range.
    // Index by the full char value instead of subtracting '0'.
    @ModifyConstant(method = "<clinit>", constant = @Constant(intValue = 67), require = 1)
    private static int geg$expandFormattingMap(int originalSize) {
        return Character.MAX_VALUE + 1;
    }

    @ModifyConstant(method = "<clinit>", constant = @Constant(intValue = 48), require = 2)
    private static int geg$useDirectFormattingIndex(int originalOffset) {
        return 0;
    }

    @ModifyConstant(method = "getForCharacter", constant = @Constant(intValue = 48), require = 2)
    private static int geg$useDirectLookupIndex(int originalOffset) {
        return 0;
    }

    @ModifyConstant(method = "getForCharacter", constant = @Constant(intValue = 114), require = 1)
    private static int geg$acceptAllFormattingCharacters(int originalMaximum) {
        return Character.MAX_VALUE;
    }
}
