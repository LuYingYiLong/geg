package io.github.luyingyilong.ore.gui;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable parse output; safe to create off-thread, contains no controls or callbacks.
 */
public record UiBlueprint(ResourceLocation resource, Node root) {
    public record Source(ResourceLocation resource, int line, int column, String id) {
        @Override
        public String toString() {
            return resource + ":" + line + ":" + column + (id.isEmpty() ? "" : " [id=" + id + "]");
        }
    }

    public record Node(String type, Map<String, String> attributes, List<Node> children, Source source) {
        public Node {
            attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
            children = List.copyOf(children);
        }

        public String get(String key, String fallback) {
            return attributes.getOrDefault(key, fallback);
        }

        public String require(String key) {
            String value = attributes.get(key);
            if (value == null || value.isBlank()) throw new UiException(source, "Missing " + key);
            return value;
        }
    }
}
