package io.github.luyingyilong.ore.control.overlay;

import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.theme.StyleBox;
import io.github.luyingyilong.ore.theme.ThemeKey;
import net.minecraft.network.chat.Component;
import java.util.function.LongSupplier;

/** Injects deterministic clocks/styles without publishing test constructors in the library. */
public final class OverlayTestAccess {
    private OverlayTestAccess() { }
    public static Drawer drawer(Component title) { return new Drawer(title); }
    public static Drawer drawer(Component title, LongSupplier clock) { return new Drawer(title, clock); }
    public static Drawer drawer(Component title, LongSupplier clock, java.util.function.Consumer<io.github.luyingyilong.ore.theme.OreSounds.Cue> sounds) {
        return new Drawer(title, clock, sounds);
    }
    public static Modal modal(Component title) { return new Modal(title); }
    public static Modal modal(Component title, ThemeKey<StyleBox> panel, ThemeKey<StyleBox> header, ThemeKey<StyleBox> body) {
        return new Modal(title, panel, header, body);
    }
    public static Rect textBounds(Toast toast, Rect area) { return toast.textBounds(area); }
}
