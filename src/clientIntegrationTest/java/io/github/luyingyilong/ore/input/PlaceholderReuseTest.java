package io.github.luyingyilong.ore.input;

import io.github.luyingyilong.ore.Label;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.signal.SignalDiagnostics;
import io.github.luyingyilong.ore.signal.SignalScope;
import net.minecraft.network.chat.Component;

/** Placeholders belong to permanent input lifetimes, not temporary ModularUI attachment. */
public final class PlaceholderReuseTest {
    public static void verify() {
        exercise(new Input(), false);
        exercise(new NumberInput(), false);
        exercise(new TextArea(0), true);
        System.out.println("ORE_PLACEHOLDER_REUSE_PASS");
    }

    private static void exercise(Input input, boolean multiline) {
        Component hint = Component.literal("输入名称 / Placeholder");
        input.setPlaceholder(hint);
        Label label = input.placeholderLabel(hint);
        var ids = SignalDiagnostics.inspect(label).signals().stream().map(SignalDiagnostics.SignalInfo::id).toList();
        for (int i = 0; i < 1000; i++) {
            check(input.placeholderLabel(hint) == label, "Drawing must reuse the same Label");
            check(SignalDiagnostics.inspect(label).signals().stream().map(SignalDiagnostics.SignalInfo::id).toList().equals(ids), "Signal identities remain stable");
        }
        check(label.getWrap() == (multiline ? Label.Wrap.WORD : Label.Wrap.NONE), "Multiline hints retain word wrapping");
        var mutable = Component.literal("Before");
        input.placeholderLabel(mutable);
        mutable.append(" after");
        check(input.placeholderLabel(mutable).getText().getString().equals("Before after"), "Mutable hint content refreshes without replacing the Label");
        input.hintColor(0xFF123456);
        check(input.placeholderLabel(mutable).getColor() == 0xFF123456, "Hint styling remains live");
        input.dispose();
        check(!label.isDisposed() && !input.getSignalScope().isClosed(), "Temporary MUI disposal preserves the logical input and its hint");
        check(input.placeholderLabel(hint) == label, "Reattaching reuses the existing hint");
        var receiver = new Control();
        label.visibilityChanged().connect(receiver, ignored -> { });
        try (SignalScope session = new SignalScope()) {
            session.own(input.getSignalScope());
        }
        io.github.luyingyilong.ore.layout.LifecycleScheduler.flush();
        check(label.isDisposed() && label.getSignalScope().isClosed(), "Permanent session close disposes the hint");
        check(SignalDiagnostics.inspect(receiver).incoming().isEmpty(), "Hint subscriptions are released");
        input.getSignalScope().close();
        receiver.queueFree();
        io.github.luyingyilong.ore.layout.LifecycleScheduler.flush();
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
