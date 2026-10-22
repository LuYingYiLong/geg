package io.github.luyingyilong.ore.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import io.github.luyingyilong.ore.signal.SignalOwner;
import io.github.luyingyilong.ore.theme.ThemeDB;
import io.github.luyingyilong.ore.theme.ThemeKey;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * Parsing runs on the supplied executor; all control operations stay on the calling UI thread.
 */
public final class UiLoader {
    private static UiLoader defaultLoader;
    private final UiTypeRegistry registry;
    private final Executor executor;
    private final java.util.concurrent.CopyOnWriteArrayList<ResourceOverride> overrides = new java.util.concurrent.CopyOnWriteArrayList<>();
    private volatile Resources resources;

    public UiLoader(UiTypeRegistry registry, Resources resources) {
        this(registry, resources, ForkJoinPool.commonPool());
    }

    public UiLoader(UiTypeRegistry registry, Resources resources, Executor executor) {
        this.registry = Objects.requireNonNull(registry);
        this.resources = Objects.requireNonNull(resources);
        this.executor = Objects.requireNonNull(executor);
    }

    public static UiDocument load(ResourceLocation resource, UiBindings bindings, SignalOwner owner) {
        return getDefault().open(resource, bindings, owner);
    }

    public static UiLoader getDefault() {
        if (defaultLoader == null) defaultLoader = new UiLoader(UiTypeRegistry.createDefault(), new Resources() {
            public Reader open(ResourceLocation id) throws IOException {
                return Minecraft.getInstance().getResourceManager().openAsReader(id);
            }

            public InputStream openBinary(ResourceLocation id) throws IOException {
                return Minecraft.getInstance().getResourceManager().open(id);
            }
        });
        return defaultLoader;
    }

    public static UiBlueprint parse(ResourceLocation resource, Reader reader) {
        requireResource(resource);
        return UiParser.parse(resource, reader);
    }

    private static void requireResource(ResourceLocation resource) {
        Objects.requireNonNull(resource);
        if (!resource.getPath().startsWith("gui/") || !resource.getPath().endsWith(".xml"))
            throw new IllegalArgumentException("UI resources must be <namespace>:gui/<name>.xml");
    }

    private static Resources managerResources(ResourceManager manager) {
        return new Resources() {
            public Reader open(ResourceLocation resource) throws IOException {
                return manager.openAsReader(resource);
            }

            public InputStream openBinary(ResourceLocation resource) throws IOException {
                return manager.open(resource);
            }
        };
    }

    public static void resourcesReloaded(ResourceManager manager) {
        if (defaultLoader != null) defaultLoader.resources = managerResources(manager);
        UiRuntime.requestAll();
    }

    public AutoCloseable addResourceOverride(ResourceOverride override) {
        overrides.add(Objects.requireNonNull(override));
        return () -> overrides.remove(override);
    }

    private Reader openResource(ResourceLocation resource, Resources fallback) throws IOException {
        var sources = List.copyOf(overrides);
        for (int i = sources.size() - 1; i >= 0; i--) {
            Reader reader = sources.get(i).open(resource);
            if (reader != null) return reader;
        }
        return fallback.open(resource);
    }

    public UiTypeRegistry getRegistry() {
        return registry;
    }

    UiTypeRegistry registry() {
        return registry;
    }

    public UiDocument open(ResourceLocation resource, UiBindings bindings, SignalOwner owner) {
        Objects.requireNonNull(owner).getSignalScope().checkThread();
        requireResource(resource);
        UiBlueprint blueprint = read(resource);
        UiDocument document = new UiDocument(this, blueprint, Objects.requireNonNull(bindings), owner);
        UiRuntime.register(document);
        return document;
    }

    UiBlueprint read(ResourceLocation resource) {
        try (Reader reader = openResource(resource, resources)) {
            return parse(resource, reader);
        } catch (IOException error) {
            throw new UiException(new UiBlueprint.Source(resource, 1, 1, ""), error.getMessage(), error);
        }
    }

    CompletableFuture<UiBlueprint> readAsync(ResourceLocation resource) {
        Resources snapshot = resources;
        return CompletableFuture.supplyAsync(() -> {
            try (Reader reader = openResource(resource, snapshot)) {
                return parse(resource, reader);
            } catch (IOException error) {
                throw new UiException(new UiBlueprint.Source(resource, 1, 1, ""), error.getMessage(), error);
            }
        }, executor);
    }

    ThemeKey<?> themeKey(String id) {
        int split = id.lastIndexOf(':');
        if (split <= 0) throw new IllegalArgumentException("Expected Type:item theme key");
        ThemeDB db = ThemeDB.getDefault();
        String type = id.substring(0, split), name = id.substring(split + 1);
        for (String base : db.getTypeChain(type))
            for (ThemeKey<?> key : db.getKeys())
                if (key.type().equals(base) && key.name().equals(name)) return db.getKey(type, name, key.valueType());
        throw new IllegalArgumentException("Unknown theme key " + id);
    }

    Object themeValue(ThemeKey<?> key, String text) {
        JsonElement value = switch (key.kind()) {
            case COLOR, FONT -> new JsonPrimitive(text);
            default -> JsonParser.parseString(text);
        };
        try {
            return ThemeDB.getDefault().decodeValue(key, value, new ThemeDB.Resources() {
                public Reader open(ResourceLocation resource) throws IOException {
                    return resources.open(resource);
                }

                public int[] imageSize(ResourceLocation resource) throws IOException {
                    try (var stream = resources.openBinary(resource); var image = com.mojang.blaze3d.platform.NativeImage.read(stream)) {
                        return new int[]{image.getWidth(), image.getHeight()};
                    }
                }
            });
        } catch (IOException error) {
            throw new IllegalArgumentException(error.getMessage(), error);
        }
    }

    @FunctionalInterface
    public interface Resources {
        Reader open(ResourceLocation resource) throws IOException;

        default InputStream openBinary(ResourceLocation resource) throws IOException {
            throw new IOException("Binary resource unavailable: " + resource);
        }
    }

    @FunctionalInterface
    public interface ResourceOverride {
        /**
         * Return null when this source does not own the resource.
         */
        Reader open(ResourceLocation resource) throws IOException;
    }
}
