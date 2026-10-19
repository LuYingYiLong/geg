package io.github.luyingyilong.ore.mixin;

import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import io.github.luyingyilong.ore.theme.OreTrueTypeGlyphProvider;
import net.minecraft.client.gui.font.providers.TrueTypeGlyphProviderDefinition;
import org.lwjgl.stb.STBTTFontinfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.ByteBuffer;

@Mixin(value = TrueTypeGlyphProviderDefinition.class, remap = false)
public abstract class TrueTypeGlyphProviderDefinitionMixin {
    // Official development name and Forge 1.20.1's production SRG name, without a host refmap.
    @Redirect(method = {"load", "m_285764_"},
            at = @At(value = "NEW", target = "com/mojang/blaze3d/font/TrueTypeGlyphProvider"))
    private TrueTypeGlyphProvider ore$correctSideBearing(ByteBuffer memory, STBTTFontinfo font, float size,
                                                         float oversample, float shiftX, float shiftY, String skip) {
        return OreTrueTypeGlyphProvider.create(((TrueTypeGlyphProviderDefinition) (Object) this).location(),
                memory, font, size, oversample, shiftX, shiftY, skip);
    }
}
