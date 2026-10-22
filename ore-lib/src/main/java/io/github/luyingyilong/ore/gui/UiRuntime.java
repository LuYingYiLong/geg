package io.github.luyingyilong.ore.gui;

import io.github.luyingyilong.ore.signal.DispatchGuard;
import net.minecraft.resources.ResourceLocation;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared safe point, after the complete embedded + native render pass; tick is the fallback.
 */
public final class UiRuntime {
    private static final List<WeakReference<UiDocument>> DOCUMENTS = new ArrayList<>();
    private static final Set<ResourceLocation> REQUESTS = ConcurrentHashMap.newKeySet();
    private static volatile boolean reloadAll;
    private static boolean flushing;
    private static volatile Set<ResourceLocation> loadedResources = Set.of();

    private UiRuntime() {
    }

    static void register(UiDocument document) {
        DOCUMENTS.add(new WeakReference<>(document));
        updateResources();
    }

    /**
     * Immutable client-thread snapshot; development watchers may read it on their worker thread.
     */
    public static Set<ResourceLocation> getLoadedResources() {
        return loadedResources;
    }

    private static void updateResources() {
        Set<ResourceLocation> resources = new HashSet<>();
        for (var reference : DOCUMENTS) {
            UiDocument document = reference.get();
            if (document != null && !document.isClosed()) resources.add(document.getResource());
        }
        loadedResources = Set.copyOf(resources);
    }

    public static void requestReload(ResourceLocation resource) {
        REQUESTS.add(Objects.requireNonNull(resource));
    }

    public static void requestAll() {
        reloadAll = true;
    }

    public static void flush() {
        if (DispatchGuard.active() || flushing) return;
        flushing = true;
        try {
            boolean all = reloadAll;
            reloadAll = false;
            Set<ResourceLocation> requested = new HashSet<>(REQUESTS);
            REQUESTS.removeAll(requested);
            for (var reference : List.copyOf(DOCUMENTS)) {
                UiDocument document = reference.get();
                if (document == null || document.isClosed()) continue;
                if (all || requested.contains(document.getResource())) document.reload();
                document.applyPending();
            }
            DOCUMENTS.removeIf(reference -> reference.get() == null || reference.get().isClosed());
            updateResources();
        } finally {
            flushing = false;
        }
    }

    public static void closeAll() {
        for (var reference : List.copyOf(DOCUMENTS)) {
            UiDocument document = reference.get();
            if (document != null) document.close();
        }
        DOCUMENTS.clear();
        REQUESTS.clear();
        reloadAll = false;
        loadedResources = Set.of();
    }
}
