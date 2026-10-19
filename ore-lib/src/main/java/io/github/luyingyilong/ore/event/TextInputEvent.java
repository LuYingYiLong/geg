package io.github.luyingyilong.ore.event;

import io.github.luyingyilong.ore.window.WindowContext;

import java.util.Objects;

public final class TextInputEvent extends InputEvent {
    private final String text;

    public TextInputEvent(WindowContext context, String text, int modifiers) {
        super(context, modifiers);
        this.text = Objects.requireNonNull(text);
    }

    public String text() {
        return text;
    }
}
