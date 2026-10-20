package io.github.luyingyilong.ore.theme;

import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.Padding;

/** Typed declarations and reads for four independent GUI-pixel padding values. */
public record ThemePadding(ThemeKey<Integer> top, ThemeKey<Integer> right,
                           ThemeKey<Integer> bottom, ThemeKey<Integer> left) {
    public static ThemePadding of(String type, String prefix, int top, int right, int bottom, int left) {
        return new ThemePadding(key(type, prefix + "padding_top", top), key(type, prefix + "padding_right", right),
                key(type, prefix + "padding_bottom", bottom), key(type, prefix + "padding_left", left));
    }

    private static ThemeKey<Integer> key(String type, String name, int value) {
        return new ThemeKey<>(type, name, Integer.class, ThemeKey.Kind.INTEGER, value, true,
                padding -> padding >= 0 && padding <= Integer.MAX_VALUE / 2);
    }

    public void register(ThemeDB db) {
        db.register(top);
        db.register(right);
        db.register(bottom);
        db.register(left);
    }

    public ThemeInsets getInsets(Control owner) {
        return new ThemeInsets(owner.getThemeItem(left), owner.getThemeItem(top),
                owner.getThemeItem(right), owner.getThemeItem(bottom));
    }

    public Padding.Insets getLayoutInsets(Control owner) {
        var insets = getInsets(owner);
        return new Padding.Insets(insets.top(), insets.right(), insets.bottom(), insets.left());
    }
}
