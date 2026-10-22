package io.github.luyingyilong.ore.gui.dev;

import io.github.luyingyilong.ore.control.base.LifecycleScheduler;
import io.github.luyingyilong.ore.control.display.Label;
import io.github.luyingyilong.ore.gui.*;
import io.github.luyingyilong.ore.signal.SignalScope;
import net.minecraft.resources.ResourceLocation;

import java.io.StringReader;
import java.nio.file.*;
import java.util.Comparator;
import java.util.function.BooleanSupplier;

public final class UiSourceWatcherTest {
    private static final ResourceLocation RESOURCE = ResourceLocation.parse("example:gui/test.xml");
    public static void main(String[] args) throws Exception {
        events();
        fallback();
        introducedSource();
        System.out.println("ORE_XML_WATCHER_PASS");
    }

    private static void events() throws Exception {
        Path root = root(), file = root.resolve("assets/example/gui/test.xml");
        Files.writeString(file, xml("initial"));
        UiLoader loader = loader();
        try (UiSourceWatcher watcher = new UiSourceWatcher(root, loader); SignalScope owner = new SignalScope();
             UiDocument document = loader.open(RESOURCE, new UiBindings(), owner)) {
            Label original = label(document);
            UiReloadStatus status = new UiReloadStatus(document);
            try {
                var initialStatus = status.getControl().getText();
                check(UiSourceWatcher.getSourceStatus(RESOURCE).running(), "Source status reports an active watcher and actual source path");
                Files.writeString(file, xml("intermediate")); Thread.sleep(30);
                Files.writeString(file, xml("latest"));
                await(() -> document.getVersion() == 2);
                check(label(document) == original && original.getText().getString().equals("latest"), "Burst saves update the existing document once");
                UiReloadStatus.tick(); check(!initialStatus.equals(status.getControl().getText()), "Visible reload status updates its version");
                settle(); check(document.getVersion() == 2, "No duplicate event/hash reload");
                Files.writeString(file, xml("latest")); settle();
                check(document.getVersion() == 2, "Saving identical contents does not reload");
                Path temporary = file.resolveSibling("test.xml.tmp");
                Files.writeString(temporary, xml("atomic"));
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
                await(() -> document.getVersion() == 3);
                check(original.getText().getString().equals("atomic"), "IDE-style replace save detected");
                Files.writeString(file, "<Label id='label' unknown='bad'/>");
                await(() -> document.getStatus() == UiDocument.Status.ERROR);
                UiReloadStatus.tick();
                check(status.getControl().getThemeOverride(Label.FONT_COLOR) == 0xFFF46D6D && original.getText().getString().equals("atomic"), "Invalid source keeps last UI and visible error");
                Files.writeString(file, xml("recovered")); await(() -> document.getVersion() == 4);
                UiReloadStatus.tick(); check(status.getControl().getThemeOverride(Label.FONT_COLOR) == 0xFF8CB3FF && document.getError().isEmpty(), "Next valid save clears the error");
                Files.delete(file); await(() -> document.getStatus() == UiDocument.Status.ERROR);
                check(document.getError().contains("missing") && label(document) == original, "Deleted source cannot silently load stale built output");
                Files.delete(file.getParent()); Files.createDirectories(file.getParent()); Files.writeString(file, xml("restored"));
                await(() -> document.getVersion() == 5);
                check(original.getText().getString().equals("restored"), "Recreated directory is watched again");
                watcher.close(); check(UiSourceWatcher.getSourceStatus(RESOURCE) == null, "Close removes source status and resource overlay");
                Files.writeString(file, xml("closed")); settle(); check(document.getVersion() == 5, "Closed watcher cannot request more reloads");
            } finally { status.getControl().queueFree(); }
        } finally { cleanup(root); }
    }

    private static void fallback() throws Exception {
        Path root = root(), file = root.resolve("assets/example/gui/test.xml");
        Files.writeString(file, xml("aaaa"));
        UiLoader loader = loader();
        try {
            // Notifications explicitly disabled, so this can only pass through content polling.
            try (UiSourceWatcher own = new UiSourceWatcher(root, loader, false, 120); SignalScope owner = new SignalScope();
                 UiDocument document = loader.open(RESOURCE, new UiBindings(), owner)) {
                var time = Files.getLastModifiedTime(file);
                Files.writeString(file, xml("bbbb")); Files.setLastModifiedTime(file, time);
                await(() -> document.getVersion() == 2);
                check(label(document).getText().getString().equals("bbbb"), "Hash fallback sees same size/mtime changes without any file event");
                settle(); check(document.getVersion() == 2, "Fallback does not reload unchanged source");
                check(UiRuntime.getLoadedResources().contains(RESOURCE), "Worker receives immutable active resource snapshot");
            }
            UiRuntime.flush(); check(!UiRuntime.getLoadedResources().contains(RESOURCE), "Closed documents leave fallback tracking");
        } finally { cleanup(root); }
    }

    private static void introducedSource() throws Exception {
        Path root = root(), file = root.resolve("assets/example/gui/test.xml");
        UiLoader loader = loader();
        try (UiSourceWatcher watcher = new UiSourceWatcher(root, loader, false, 120); SignalScope owner = new SignalScope();
             UiDocument document = loader.open(RESOURCE, new UiBindings(), owner)) {
            check(label(document).getText().getString().equals("fallback"), "Missing source initially uses packaged XML");
            Files.writeString(file, xml("introduced")); await(() -> document.getVersion() == 2);
            check(label(document).getText().getString().equals("introduced"), "New source replaces packaged XML without rebuilding");
        } finally { cleanup(root); }
    }

    private static UiLoader loader() { return new UiLoader(UiTypeRegistry.createDefault(), id -> new StringReader(xml("fallback")), Runnable::run); }
    private static String xml(String text) { return "<Label id='label' text='" + text + "'/>"; }
    private static Label label(UiDocument document) { return document.getRoot().findControlFromId("label", Label.class); }
    private static Path root() throws Exception {
        Path parent = Path.of("build/tmp").toAbsolutePath().normalize(); Files.createDirectories(parent);
        Path root = Files.createTempDirectory(parent, "xml-source-"); Files.createDirectories(root.resolve("assets/example/gui")); return root;
    }
    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + 6_000_000_000L;
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) { Thread.sleep(30); UiRuntime.flush(); }
        check(condition.getAsBoolean(), "Timed out waiting for source-only live reload");
    }
    private static void settle() throws Exception {
        long until = System.nanoTime() + 800_000_000L;
        while (System.nanoTime() < until) { Thread.sleep(30); UiRuntime.flush(); }
    }
    private static void cleanup(Path root) throws Exception {
        UiRuntime.flush(); LifecycleScheduler.flush();
        if (!root.startsWith(Path.of("build/tmp").toAbsolutePath().normalize())) throw new AssertionError("Cleanup escaped test workspace");
        try (var files = Files.walk(root)) { for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
