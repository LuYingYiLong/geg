package io.github.luyingyilong.ore.signal;

import java.util.*;

public final class SignalSilenceTest {
    public static void main(String[] args) {
        try (SignalScope scope = new SignalScope()) {
            SignalSource<Integer> source = new SignalSource<>(scope, "value", "Integer"); List<Integer> values = new ArrayList<>();
            source.signal().connect(scope, values::add);
            try (var outer = scope.silence()) { source.emit(1); try (var inner = scope.silence()) { source.emit(2); } source.emit(3); }
            source.emit(4); if (!values.equals(List.of(4))) throw new AssertionError("Scoped silence must not disconnect receivers");
            source.signal().connect(scope, value -> { try (var muted = scope.silence()) { source.emit(99); } });
            source.signal().connect(scope, value -> values.add(value * 10));
            source.emit(5); if (!values.equals(List.of(4, 5, 50))) throw new AssertionError("Muting nested emission must finish the current subscriber snapshot");
        }
        System.out.println("ORE_SIGNAL_SILENCE_PASS");
    }
}
