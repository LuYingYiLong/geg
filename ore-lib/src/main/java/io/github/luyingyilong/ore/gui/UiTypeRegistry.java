package io.github.luyingyilong.ore.gui;

import io.github.luyingyilong.ore.control.base.Container;
import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.display.*;
import io.github.luyingyilong.ore.control.input.*;
import io.github.luyingyilong.ore.control.layout.*;
import io.github.luyingyilong.ore.control.navigation.Menu;
import io.github.luyingyilong.ore.control.navigation.Navbar;
import io.github.luyingyilong.ore.control.navigation.Tabs;
import io.github.luyingyilong.ore.control.progress.ProgressBar;
import io.github.luyingyilong.ore.control.progress.Spin;
import io.github.luyingyilong.ore.control.progress.TextureProgressBar;
import net.minecraft.network.chat.Component;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Explicit allowlist. Factories and converters are registered by Java, never named by class path.
 */
public final class UiTypeRegistry {
    private final Map<String, Type> types = new LinkedHashMap<>();
    private final Map<Class<?>, Function<String, ?>> converters = new HashMap<>();

    public UiTypeRegistry() {
    }

    public static UiTypeRegistry createDefault() {
        UiTypeRegistry r = new UiTypeRegistry();
        r.builtin("Control", Control::new, Structure.CHILDREN);
        r.builtin("Container", Container::new, Structure.CHILDREN);
        r.builtin("Box", Box::new, Structure.CHILDREN);
        r.builtin("Grid", Grid::new, Structure.CHILDREN);
        r.builtin("Panel", Panel::new, Structure.CHILD);
        r.builtin("Padding", () -> new Padding(Padding.Insets.all(0)), Structure.CHILD);
        r.builtin("Scroll", Scroll::new, Structure.CONTENT);
        r.builtin("Label", Label::new, Structure.LEAF);
        r.builtin("TextureRect", TextureRect::new, Structure.LEAF);
        r.builtin("Tag", Tag::new, Structure.LEAF);
        r.builtin("Badge", Badge::new, Structure.LEAF);
        r.builtin("Divider", Divider::new, Structure.LEAF);
        r.builtin("Spin", Spin::new, Structure.LEAF);
        r.builtin("ProgressBar", ProgressBar::new, Structure.LEAF);
        r.builtin("TextureProgressBar", TextureProgressBar::new, Structure.LEAF);
        r.builtin("Button", Button::new, Structure.LEAF);
        r.builtin("Checkbox", () -> new Checkbox(Component.empty()), Structure.LEAF);
        r.builtin("Switch", Switch::new, Structure.LEAF);
        r.builtin("Radio", Radio::new, Structure.LEAF);
        r.builtin("Slider", Slider::new, Structure.LEAF);
        r.builtin("Input", () -> new InputControl(new Input()), Structure.LEAF);
        r.builtin("NumberInput", () -> new InputControl(new NumberInput()), Structure.LEAF);
        r.builtin("TextArea", () -> new InputControl(new TextArea(0.0F)), Structure.LEAF);
        r.builtin("Navbar", () -> new Navbar(Component.empty()), Structure.EXTRA);
        r.builtin("Menu", Menu::new, Structure.MENU);
        r.builtin("Tabs", Tabs::new, Structure.TABS);
        r.builtin("Descriptions", Descriptions::new, Structure.DESCRIPTIONS);
        r.put("Segmented", new Type(node -> new Segmented(items(node).stream().map(item -> UiCodecs.component(item.require("text"))).toList()),
                Structure.SEGMENTED, node -> items(node).stream().map(item -> item.require("text")).toList().toString(), Map.of()));
        r.put("Dropdown", new Type(node -> new Dropdown<String>(null, Integer.parseInt(node.get("maxVisibleItems", "8"))),
                Structure.DROPDOWN, node -> node.get("maxVisibleItems", "8"), Map.of("selectionChanged", String.class, "itemActivated", String.class)));
        UiBuiltins.register(r);
        return r;
    }

    static String itemKey(UiBlueprint.Node node) {
        return items(node).stream().map(UiTypeRegistry::fingerprint).toList().toString();
    }

    static String fingerprint(UiBlueprint.Node node) {
        return node.type() + new TreeMap<>(node.attributes()).toString() + node.children().stream().map(UiTypeRegistry::fingerprint).toList();
    }

    static List<UiBlueprint.Node> items(UiBlueprint.Node node) {
        return node.children().stream().filter(child -> child.type().equals("item")).toList();
    }

    static List<UiBlueprint.Node> controls(Type type, UiBlueprint.Node node) {
        List<UiBlueprint.Node> result = new ArrayList<>();
        for (UiBlueprint.Node child : node.children()) {
            if (child.type().equals("connect") || child.type().equals("theme")) continue;
            switch (type.structure()) {
                case CHILDREN -> {
                    if (child.type().equals("slot") || child.type().equals("item"))
                        throw new UiException(child.source(), "Expected a control");
                    result.add(child);
                }
                case FORM -> {
                    if (!child.type().equals("FormItem"))
                        throw new UiException(child.source(), "Form accepts <FormItem>");
                    result.add(child);
                }
                case FORM_ITEM -> {
                    if (child.type().equals("validate")) continue;
                    if (!result.isEmpty())
                        throw new UiException(child.source(), "FormItem requires exactly one field control");
                    result.add(child);
                }
                case COLLAPSE, SPLITTER, POPUP -> {
                    if (!child.type().equals("slot")) throw new UiException(child.source(), "Expected a named slot");
                    attributes(child, Set.of("name"));
                    String slot = child.require("name");
                    Set<String> allowed = type.structure() == Structure.SPLITTER ? Set.of("first", "second")
                            : type.structure() == Structure.COLLAPSE ? Set.of("content") : Set.of("content", "actions");
                    if (!allowed.contains(slot)) throw new UiException(child.source(), "Unknown slot " + slot);
                    if (node.children().stream().filter(n -> n.type().equals("slot") && n.get("name", "").equals(slot)).count() != 1)
                        throw new UiException(child.source(), "Duplicate slot " + slot);
                    if (!slot.equals("actions") && child.children().size() != 1)
                        throw new UiException(child.source(), "Slot requires one control");
                    for (var value : child.children()) {
                        if (slot.equals("actions") && !value.type().equals("Button"))
                            throw new UiException(value.source(), "Actions require Button controls");
                        result.add(value);
                    }
                }
                case RADIO_GROUP -> {
                    if (!child.type().equals("item") || child.children().size() != 1 || !child.children().get(0).type().equals("Radio"))
                        throw new UiException(child.source(), "RadioGroup items require one Radio");
                    attributes(child, Set.of("value"));
                    child.require("value");
                    result.add(child.children().get(0));
                }
                case TREE, INVENTORY -> {
                    if (!child.type().equals("item")) throw new UiException(child.source(), "Expected <item>");
                }
                case TABLE -> {
                    if (!Set.of("column", "row").contains(child.type()))
                        throw new UiException(child.source(), "Expected <column> or <row>");
                }
                case CHILD, CONTENT, EXTRA -> {
                    String slot = type.structure() == Structure.CHILD ? "child" : type.structure() == Structure.CONTENT ? "content" : "extra";
                    if (!child.type().equals("slot") || !child.require("name").equals(slot) || child.children().size() != 1 || !result.isEmpty())
                        throw new UiException(child.source(), "Expected one <slot name=\"" + slot + "\"> with one control");
                    attributes(child, Set.of("name"));
                    result.add(child.children().get(0));
                }
                case TABS -> {
                    if (!child.type().equals("item") || child.children().size() != 1)
                        throw new UiException(child.source(), "Tab item requires one content control");
                    attributes(child, Set.of("key", "text", "icon", "disabled"));
                    child.require("key");
                    child.require("text");
                    result.add(child.children().get(0));
                }
                case MENU, DESCRIPTIONS, SEGMENTED, DROPDOWN -> {
                    if (!child.type().equals("item")) throw new UiException(child.source(), "Expected <item>");
                }
                default -> throw new UiException(child.source(), "This UI type does not accept XML children");
            }
        }
        if (type.structure() == Structure.FORM_ITEM && result.size() != 1)
            throw new UiException(node.source(), "FormItem requires one field control");
        return List.copyOf(result);
    }

    static void attributes(UiBlueprint.Node node, Set<String> allowed) {
        for (String name : node.attributes().keySet())
            if (!allowed.contains(name))
                throw new UiException(node.source(), "Unknown " + node.type() + " attribute: " + name);
    }

    public Set<String> getRegisteredTypes() {
        return Set.copyOf(types.keySet());
    }

    public UiTypeRegistry register(String name, Function<UiBlueprint.Node, Control> factory) {
        return register(name, factory, node -> node.attributes().toString(), Map.of());
    }

    /**
     * Opaque factory subtrees are Java-owned. Changed construction keys replace the subtree.
     */
    public UiTypeRegistry register(String name, Function<UiBlueprint.Node, Control> factory,
                                   Function<UiBlueprint.Node, String> constructionKey, Map<String, Class<?>> signalPayloads) {
        return register(name, factory, constructionKey, signalPayloads, Set.of());
    }

    public UiTypeRegistry register(String name, Function<UiBlueprint.Node, Control> factory,
                                   Function<UiBlueprint.Node, String> constructionKey, Map<String, Class<?>> signalPayloads,
                                   Set<String> constructionAttributes) {
        if (!name.contains(":")) throw new IllegalArgumentException("Extension UI types require a namespace");
        put(name, new Type(Objects.requireNonNull(factory), Structure.OPAQUE, Objects.requireNonNull(constructionKey), Map.copyOf(signalPayloads), Set.copyOf(constructionAttributes)));
        return this;
    }

    public <T> UiTypeRegistry registerConverter(Class<T> type, Function<String, T> converter) {
        if (converters.putIfAbsent(type, converter) != null)
            throw new IllegalArgumentException("Converter already registered: " + type);
        return this;
    }

    Object convert(Class<?> type, String value, String property) {
        Function<String, ?> converter = converters.get(type);
        return converter == null ? UiCodecs.parse(type, value, property) : converter.apply(value);
    }

    Type require(UiBlueprint.Node node) {
        Type type = types.get(node.type());
        if (type == null) throw new UiException(node.source(), "Unregistered UI type: " + node.type());
        return type;
    }

    void put(String name, Type type) {
        if (types.putIfAbsent(name, type) != null)
            throw new IllegalArgumentException("UI type already registered: " + name);
    }

    void builtin(String name, Supplier<? extends Control> factory, Structure structure) {
        put(name, new Type(node -> factory.get(), structure, node -> "", Map.of()));
    }

    enum Structure {
        CHILDREN, CHILD, CONTENT, EXTRA, TABS, MENU, DESCRIPTIONS, SEGMENTED, DROPDOWN, LEAF, OPAQUE,
        COLLAPSE, SPLITTER, POPUP, FORM, FORM_ITEM, TREE, TABLE, INVENTORY, RADIO_GROUP
    }

    @FunctionalInterface
    interface Factory {
        Control create(UiBlueprint.Node node, List<Control> children, UiBindings bindings);
    }

    record Type(Factory factory, Structure structure,
                Function<UiBlueprint.Node, String> constructionKey, Map<String, Class<?>> payloads,
                Set<String> constructionAttributes) {
        Type(Function<UiBlueprint.Node, Control> factory, Structure structure, Function<UiBlueprint.Node, String> key,
             Map<String, Class<?>> payloads, Set<String> attributes) {
            this((node, children, bindings) -> factory.apply(node), structure, key, payloads, attributes);
        }

        Type(Function<UiBlueprint.Node, Control> factory, Structure structure, Function<UiBlueprint.Node, String> key, Map<String, Class<?>> payloads) {
            this(factory, structure, key, payloads, Set.of());
        }
    }
}
