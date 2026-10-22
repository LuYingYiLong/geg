package io.github.luyingyilong.ore.gui.dev;

import io.github.luyingyilong.ore.gui.UiLoader;
import io.github.luyingyilong.ore.gui.UiRuntime;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Source-only XML overlay. File events are hints; active documents also have a content-hash fallback. */
public final class UiSourceWatcher implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(UiSourceWatcher.class);
    private static final List<UiSourceWatcher> ACTIVE = new CopyOnWriteArrayList<>();
    private static final long DEBOUNCE_NANOS = 300_000_000L;
    private static final String MISSING = "missing";
    public record SourceStatus(Path path, boolean running, String error) { }

    private final Path root;
    private final WatchService watcher;
    private final Map<WatchKey, Path> directories = new HashMap<>();
    private final Set<Path> registered = new HashSet<>();
    private final Map<ResourceLocation, Long> changed = new HashMap<>();
    private final Map<ResourceLocation, String> observed = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AutoCloseable override;
    private final Thread thread;
    private final boolean fileEvents;
    private final long fallbackNanos;
    private volatile boolean running = true;
    private volatile String error = "";

    public UiSourceWatcher(Path resourceRoot, UiLoader loader) throws IOException {
        this(resourceRoot, loader, true, 1000);
    }

    // The event-free mode exercises lost-event recovery without depending on OS event timing.
    UiSourceWatcher(Path resourceRoot, UiLoader loader, boolean fileEvents, long fallbackMillis) throws IOException {
        root = resourceRoot.toAbsolutePath().normalize().resolve("assets");
        if (!Files.isDirectory(root)) throw new IOException("Missing resource assets directory: " + root);
        if (fallbackMillis < 1) throw new IllegalArgumentException("Positive fallback interval required");
        this.fileEvents = fileEvents;
        fallbackNanos = TimeUnit.MILLISECONDS.toNanos(fallbackMillis);
        watcher = root.getFileSystem().newWatchService();
        try { registerDirectories(); }
        catch (IOException | RuntimeException failure) { watcher.close(); throw failure; }
        override = loader.addResourceOverride(resource -> {
            Path file = path(resource);
            if (file == null) return null;
            if (!Files.isRegularFile(file)) {
                if (observed.containsKey(resource)) throw new IOException("Source XML is missing: " + file);
                return null;
            }
            byte[] bytes = Files.readAllBytes(file);
            observed.putIfAbsent(resource, fingerprint(bytes));
            return new StringReader(new String(bytes, StandardCharsets.UTF_8));
        });
        ACTIVE.add(this);
        thread = new Thread(this::run, "Ore XML source watcher");
        thread.setDaemon(true);
        thread.start();
        LOGGER.info("XML source watching enabled: {} (300ms debounce, {}ms content fallback)", root, fallbackMillis);
    }

    /** Null means the resource is currently loaded from packaged output rather than a watched source. */
    public static SourceStatus getSourceStatus(ResourceLocation resource) {
        List<UiSourceWatcher> watchers = List.copyOf(ACTIVE);
        for (int i = watchers.size() - 1; i >= 0; i--) {
            UiSourceWatcher source = watchers.get(i);
            if (source.observed.containsKey(resource))
                return new SourceStatus(source.path(resource), source.running && !source.closed.get(), source.error);
        }
        return null;
    }

    private Path path(ResourceLocation resource) {
        if (!resource.getPath().startsWith("gui/") || !resource.getPath().endsWith(".xml")) return null;
        Path file = root.resolve(resource.getNamespace()).resolve(resource.getPath()).normalize();
        return file.startsWith(root) ? file : null;
    }

    private void registerDirectory(Path directory) throws IOException {
        if (!registered.contains(directory)) {
            WatchKey key = directory.register(watcher, StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
            registered.add(directory);
            directories.put(key, directory);
        }
    }

    private void registerDirectories() throws IOException {
        // Observe only the namespace/gui hierarchy, never texture/font subdirectories.
        try {
            registerDirectory(root);
            try (var namespaces = Files.list(root)) {
                for (Path namespace : namespaces.filter(Files::isDirectory).toList()) {
                    registerDirectory(namespace);
                    Path gui = namespace.resolve("gui");
                    if (Files.isDirectory(gui)) try (var paths = Files.walk(gui)) {
                        for (Path directory : paths.filter(Files::isDirectory).toList()) registerDirectory(directory);
                    }
                }
            }
        } catch (UncheckedIOException transientWalkFailure) { throw transientWalkFailure.getCause(); }
    }

    private void run() {
        long nextFallback = System.nanoTime() + fallbackNanos;
        try {
            while (!closed.get()) {
                WatchKey key = watcher.poll(100, TimeUnit.MILLISECONDS);
                if (closed.get()) break;
                long now = System.nanoTime();
                if (key != null) {
                    Path directory = directories.get(key);
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (!fileEvents) continue;
                        if (event.kind() == StandardWatchEventKinds.OVERFLOW) { nextFallback = 0; continue; }
                        if (directory == null || !(event.context() instanceof Path relative)) continue;
                        ResourceLocation resource = resource(directory.resolve(relative));
                        if (resource != null && UiRuntime.getLoadedResources().contains(resource)) changed.put(resource, now);
                        if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) nextFallback = 0;
                    }
                    if (!key.reset()) registered.remove(directories.remove(key));
                }
                if (now >= nextFallback) {
                    try { registerDirectories(); error = ""; }
                    catch (IOException failure) { report(failure); }
                    for (ResourceLocation resource : UiRuntime.getLoadedResources()) detect(resource, now);
                    nextFallback = now + fallbackNanos;
                }
                for (var entry : new ArrayList<>(changed.entrySet())) {
                    if (!UiRuntime.getLoadedResources().contains(entry.getKey())) { changed.remove(entry.getKey()); continue; }
                    if (now - entry.getValue() >= DEBOUNCE_NANOS) reloadChanged(entry.getKey(), now);
                }
            }
        } catch (ClosedWatchServiceException ignored) {
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException failure) {
            report(failure);
        } finally { running = false; }
    }

    private void detect(ResourceLocation resource, long now) {
        Path file = path(resource);
        if (file == null) return;
        // A source introduced after opening the document must override the old built resource too.
        if (!observed.containsKey(resource) && !Files.isRegularFile(file)) return;
        try {
            if (!Objects.equals(observed.get(resource), fingerprint(file))) changed.putIfAbsent(resource, now);
        } catch (IOException failure) { report(failure); changed.putIfAbsent(resource, now); }
    }

    private void reloadChanged(ResourceLocation resource, long now) {
        try {
            String hash = fingerprint(path(resource));
            String old = observed.put(resource, hash);
            changed.remove(resource);
            if (!hash.equals(old)) {
                if (!closed.get()) UiRuntime.requestReload(resource);
                LOGGER.debug("XML source changed: {}", path(resource));
            }
        } catch (IOException failure) {
            // A temporarily locked/replaced file is retried; one failed read cannot kill watching.
            report(failure);
            changed.put(resource, now);
        }
    }

    private static String fingerprint(Path file) throws IOException {
        try { return fingerprint(Files.readAllBytes(file)); }
        catch (NoSuchFileException missing) { return MISSING; }
    }

    private static String fingerprint(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    private void report(Exception failure) {
        String message = failure.toString();
        if (!message.equals(error)) LOGGER.warn("XML source watch issue in {}: {}", root, message);
        error = message;
    }

    private ResourceLocation resource(Path file) {
        if (!file.startsWith(root)) return null;
        Path path = root.relativize(file);
        if (path.getNameCount() < 3 || !path.getName(1).toString().equals("gui") || !path.toString().endsWith(".xml")) return null;
        return ResourceLocation.tryParse(path.getName(0) + ":" + path.subpath(1, path.getNameCount()).toString().replace('\\', '/'));
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        ACTIVE.remove(this);
        running = false;
        try { watcher.close(); } catch (IOException failure) { report(failure); }
        try { override.close(); } catch (Exception failure) { throw new IllegalStateException(failure); }
        finally { thread.interrupt(); observed.clear(); }
        LOGGER.info("XML source watching stopped: {}", root);
    }
}
