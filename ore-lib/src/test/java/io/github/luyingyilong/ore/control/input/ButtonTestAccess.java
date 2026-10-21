package io.github.luyingyilong.ore.control.input;

import io.github.luyingyilong.ore.theme.OreSounds;
import io.github.luyingyilong.ore.theme.StyleBox;
import java.util.function.Consumer;

/** Test-only access to injected sounds and state styles; production visibility stays unchanged. */
public final class ButtonTestAccess {
    private ButtonTestAccess() { }
    public static Button create(Consumer<OreSounds.Cue> sounds) { return new Button(sounds); }
    public static StyleBox style(Button button, boolean hovered, boolean pressed) {
        return button.getPresentationStyle(hovered, pressed);
    }
}
