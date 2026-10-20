package io.github.luyingyilong.geg.client.theme;

import io.github.luyingyilong.ore.render.TextureRegion;
import io.github.luyingyilong.ore.theme.OreTheme;
import io.github.luyingyilong.ore.theme.ThemeDB;
import io.github.luyingyilong.ore.theme.ThemeKey;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** Business symbols and host theme; Ore itself has no knowledge of mail or organizations. */
public final class GegThemes {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("geg", "ui");
    public static final String MAIL_ICONS = "geg:MailIcons", ORGANIZATION_ICONS = "geg:OrganizationIcons";
    public static final ThemeKey<TextureRegion> MAIL_UNREAD = icon(MAIL_ICONS, "unread", "email");
    public static final ThemeKey<TextureRegion> MAIL_READ = icon(MAIL_ICONS, "read", "email_readed");
    public static final ThemeKey<TextureRegion> MAIL_REWARD_UNREAD = icon(MAIL_ICONS, "reward_unread", "gift");
    public static final ThemeKey<TextureRegion> MAIL_REWARD_READ = icon(MAIL_ICONS, "reward_read", "gift_opened");
    public static final ThemeKey<TextureRegion> ORGANIZATION_CREATE = icon(ORGANIZATION_ICONS, "create", "flag");
    public static final ThemeKey<TextureRegion> ORGANIZATION_JOIN = icon(ORGANIZATION_ICONS, "join", "org");
    private static final List<ThemeKey<TextureRegion>> KEYS = List.of(MAIL_UNREAD, MAIL_READ,
            MAIL_REWARD_UNREAD, MAIL_REWARD_READ, ORGANIZATION_CREATE, ORGANIZATION_JOIN);

    private GegThemes() { }

    public static void register(ThemeDB db) {
        db.registerType(MAIL_ICONS, null);
        db.registerType(ORGANIZATION_ICONS, null);
        KEYS.forEach(db::register);
        // Tests or another host can use the declarations without changing their own default theme.
        db.registerTheme(ID);
        db.getTheme(ID).setParent(db.getTheme(OreTheme.ID));
    }

    private static ThemeKey<TextureRegion> icon(String type, String name, String file) {
        return ThemeKey.of(type, name, TextureRegion.class, ThemeKey.Kind.TEXTURE,
                TextureRegion.full(ResourceLocation.fromNamespaceAndPath("geg", "textures/icons/" + file + ".png"), 16, 16), true);
    }
}
