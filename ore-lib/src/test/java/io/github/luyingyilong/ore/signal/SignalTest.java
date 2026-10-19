package io.github.luyingyilong.ore.signal;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Pure Java contract, including a deliberately rejected wrong payload type. */
public final class SignalTest {
    public static void main(String[] args) throws Exception {
        try (SignalScope sourceLife = new SignalScope(); SignalScope receiver = new SignalScope()) {
            SignalSource<String> source = new SignalSource<>(sourceLife, "textChanged");
            ArrayList<String> events = new ArrayList<>();
            Connection first = source.signal().connect(receiver, value -> events.add("a:" + value));
            Connection second = source.signal().connect(receiver, value -> events.add("b:" + value));
            source.emit("one");
            check(events.equals(List.of("a:one", "b:one")), "Registration order");
            first.disconnect(); first.close();
            check(!first.isConnected() && second.isConnected(), "Independent idempotent handles");
            receiver.close();
            check(!second.isConnected() && source.signal().connectionCount() == 0, "Receiver cleanup");
            rejected(() -> source.signal().connect(receiver, events::add));
        }

        try (SignalScope scope = new SignalScope()) {
            SignalSource<Integer> source = new SignalSource<>(scope, "reentrant");
            List<Integer> events = new ArrayList<>();
            Connection[] pending = new Connection[1];
            source.signal().connect(scope, value -> {
                events.add(value);
                if (value == 1) {
                    pending[0].disconnect();
                    source.signal().connect(scope, next -> events.add(next * 10));
                    source.emit(2);
                }
            });
            pending[0] = source.signal().connect(scope, value -> { throw new AssertionError("Disconnected pending slot ran"); });
            source.emit(1);
            check(events.equals(List.of(1, 2, 20)), "Independent reentrant snapshots, new slot waits for next emit");
        }

        SignalScope sourceLife = new SignalScope(), receiver = new SignalScope();
        SignalSource0 action = new SignalSource0(sourceLife, "pressed");
        int[] count = {0};
        action.signal().connect(receiver, () -> { throw new IllegalArgumentException("Expected slot isolation test"); });
        Connection remaining = action.signal().connect(receiver, () -> count[0]++);
        action.emit();
        check(count[0] == 1, "RuntimeException does not prevent subsequent slots");
        AtomicReference<Throwable> wrongThread = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try { action.emit(); } catch (Throwable error) { wrongThread.set(error); }
        });
        thread.start(); thread.join();
        check(wrongThread.get() instanceof IllegalStateException, "Reject cross-thread access");
        sourceLife.close();
        check(!remaining.isConnected(), "Source destruction disconnects receiver");
        action.emit();
        check(count[0] == 1, "Destroyed source cannot emit");
        receiver.close();
        compileTimeSafety();
        System.out.println("ORE_SIGNAL_TEST_PASS");
    }

    private static void compileTimeSafety() throws Exception {
        Path directory = Files.createTempDirectory(Path.of("build/tmp"), "signal-types-");
        Path source = directory.resolve("WrongSlot.java");
        Files.writeString(source, """
                import io.github.luyingyilong.ore.signal.*;
                class WrongSlot {
                    void bind(Signal<String> signal, SignalScope owner) {
                        signal.connect(owner, (Integer value) -> {});
                    }
                }
                """);
        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var files = compiler.getStandardFileManager(diagnostics, null, null)) {
            boolean valid = compiler.getTask(null, files, diagnostics,
                    List.of("-proc:none", "-classpath", System.getProperty("java.class.path"), "-d", directory.toString()),
                    null, files.getJavaFileObjects(source.toFile())).call();
            check(!valid && !diagnostics.getDiagnostics().isEmpty(), "Wrong payload type must fail at compile time");
        }
    }

    private static void rejected(Runnable action) {
        try { action.run(); throw new AssertionError("Closed owner accepted connection"); }
        catch (IllegalStateException expected) { }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
