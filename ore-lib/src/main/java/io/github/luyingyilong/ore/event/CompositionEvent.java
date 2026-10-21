package io.github.luyingyilong.ore.event;

import io.github.luyingyilong.ore.control.input.InputPreedit;
import io.github.luyingyilong.ore.window.WindowContext;

/**
 * Preedit is display-only. Committed characters arrive exclusively as TextInputEvent.
 */
public final class CompositionEvent extends InputEvent {
    private final Action action;
    private final InputPreedit preedit;
    public CompositionEvent(WindowContext context, Action action, InputPreedit preedit) {
        super(context, 0);
        this.action = action;
        this.preedit = preedit;
    }

    public Action action() {
        return action;
    }

    public InputPreedit preedit() {
        return preedit;
    }

    public enum Action {UPDATE, CANCEL}
}
