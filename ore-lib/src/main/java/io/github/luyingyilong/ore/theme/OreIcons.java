package io.github.luyingyilong.ore.theme;

import net.minecraft.resources.ResourceLocation;

public final class OreIcons {
    public static final ResourceLocation SMALL_CLOSE = icon("small_close");
    public static final ResourceLocation CLOSE = icon("close");
    public static final ResourceLocation CHECKED = icon("checked");
    public static final ResourceLocation ARROW_DOWN = icon("arrow_down");
    public static final ResourceLocation ARROW_DOWN_LIGHT = icon("arrow_down_light");
    public static final ResourceLocation ARROW_RIGHT_LIGHT = icon("arrow_right_light");
    public static final ResourceLocation ARROW_LEFT = icon("arrow_left");
    public static final ResourceLocation FLAG = icon("flag");
    public static final ResourceLocation ORG = icon("org");
    public static final ResourceLocation LOADING = icon("loading");
    public static final ResourceLocation CHECKBOX_NORMAL = icon("checkbox_normal");
    public static final ResourceLocation CHECKBOX_HOVERED = icon("checkbox_hovered");
    public static final ResourceLocation CHECKBOX_DISABLED = icon("checkbox_disabled");
    public static final ResourceLocation CHECKBOX_CHECKED_NORMAL = icon("checkbox_checked_normal");
    public static final ResourceLocation CHECKBOX_CHECKED_HOVERED = icon("checkbox_checked_hovered");
    public static final ResourceLocation CHECKBOX_CHECKED_PRESSED = icon("checkbox_checked_pressed");
    public static final ResourceLocation CHECKBOX_CHECKED_DISABLED = icon("checkbox_checked_disabled");
    public static final ResourceLocation FILTER = icon("filter");
    public static final ResourceLocation EMAIL = icon("email");
    public static final ResourceLocation EMAIL_READED = icon("email_readed");
    public static final ResourceLocation GIFT = icon("gift");
    public static final ResourceLocation GIFT_OPENED = icon("gift_opened");

    private OreIcons() {
    }

    private static ResourceLocation icon(String name) {
        return ResourceLocation.fromNamespaceAndPath("ore",
                "textures/modularui/icons/" + name + ".png");
    }
}
