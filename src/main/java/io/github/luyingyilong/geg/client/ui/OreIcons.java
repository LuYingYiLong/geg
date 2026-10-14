package io.github.luyingyilong.geg.client.ui;

import io.github.luyingyilong.geg.GegMod;
import net.minecraft.resources.ResourceLocation;

public final class OreIcons {
    public static final ResourceLocation SMALL_CLOSE = icon("small_close");
    public static final ResourceLocation CHECKED = icon("checked");
    public static final ResourceLocation ARROW_DOWN = icon("arrow_down");

    private OreIcons() {
    }

    private static ResourceLocation icon(String name) {
        return ResourceLocation.fromNamespaceAndPath(GegMod.MODID,
                "textures/modularui/icons/" + name + ".png");
    }
}
