package io.github.luyingyilong.ore.control.navigation;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.render.TextureSource;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/** Immutable page description. Its content belongs to Tabs only while the item is installed. */
public final class TabItem {
    private final String key;
    private final Component label;
    private final Control content;
    private final TextureSource icon;
    private final boolean disabled;

    public TabItem(String key, Component label, Control content) {
        this(key, label, content, null, false);
    }

    public TabItem(String key, Component label, Control content, TextureSource icon, boolean disabled) {
        if (Objects.requireNonNull(key).isBlank()) throw new IllegalArgumentException("Tab key is blank");
        this.key = key;
        this.label = Objects.requireNonNull(label).copy();
        this.content = Objects.requireNonNull(content);
        this.icon = icon;
        this.disabled = disabled;
    }

    public String getKey() { return key; }
    public Component getLabel() { return label.copy(); }
    public Control getContent() { return content; }
    public TextureSource getIcon() { return icon; }
    public boolean getDisabled() { return disabled; }
}
