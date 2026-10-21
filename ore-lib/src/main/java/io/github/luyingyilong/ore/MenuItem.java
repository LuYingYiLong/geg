package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.render.TextureSource;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;

/**
 * Stable keys identify actions; groups are always expanded, non-interactive headings.
 */
public record MenuItem(String key, Type type, Component label, List<MenuItem> children,
                       TextureSource icon, boolean disabled) {
    public MenuItem {
        if (Objects.requireNonNull(key).isBlank()) throw new IllegalArgumentException("Menu key is blank");
        Objects.requireNonNull(type);
        label = Objects.requireNonNull(label).copy();
        children = List.copyOf(children);
        if (type != Type.GROUP && !children.isEmpty())
            throw new IllegalArgumentException("Only menu groups can contain children");
    }

    public static MenuItem item(String key, Component label) {
        return new MenuItem(key, Type.ITEM, label, List.of(), null, false);
    }

    public static MenuItem group(String key, Component label, List<MenuItem> children) {
        return new MenuItem(key, Type.GROUP, label, children, null, false);
    }

    /**
     * Informational row, without a button, focus target or selection action.
     */
    public static MenuItem empty(String key, Component label) {
        return new MenuItem(key, Type.EMPTY, label, List.of(), null, false);
    }

    public MenuItem icon(TextureSource icon) {
        return new MenuItem(key, type, label, children, icon, disabled);
    }

    public MenuItem disabled(boolean disabled) {
        return new MenuItem(key, type, label, children, icon, disabled);
    }

    public enum Type {ITEM, GROUP, EMPTY}
}
