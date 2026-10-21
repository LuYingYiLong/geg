package io.github.luyingyilong.ore.render;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.theme.ThemeKey;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Shareable source description. Controls are never shared by image descriptions. */
public sealed interface TextureSource permits TextureSource.Direct,TextureSource.Themed {
    TextureRegion resolve(Control owner);
    static TextureSource region(TextureRegion region) { return new Direct(region); }
    static TextureSource themed(ThemeKey<TextureRegion> key) {
        return Sources.THEMED.computeIfAbsent(Objects.requireNonNull(key),Themed::new);
    }
    record Direct(TextureRegion getRegion) implements TextureSource {
        public Direct { Objects.requireNonNull(getRegion); }
        public TextureRegion resolve(Control owner) { return getRegion; }
    }
    record Themed(ThemeKey<TextureRegion> getKey) implements TextureSource {
        public Themed { Objects.requireNonNull(getKey); }
        public TextureRegion resolve(Control owner) { return owner.getThemeItem(getKey); }
    }
    final class Sources {
        private Sources() { }
        // Registry keys have database lifetime; this cache never retains a Control or a Theme.
        private static final ConcurrentHashMap<ThemeKey<TextureRegion>,Themed> THEMED=new ConcurrentHashMap<>();
    }
}
