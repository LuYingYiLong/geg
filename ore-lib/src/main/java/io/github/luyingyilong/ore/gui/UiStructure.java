package io.github.luyingyilong.ore.gui;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.display.DescriptionItem;
import io.github.luyingyilong.ore.control.display.Descriptions;
import io.github.luyingyilong.ore.control.form.Form;
import io.github.luyingyilong.ore.control.form.FormItem;
import io.github.luyingyilong.ore.control.input.Button;
import io.github.luyingyilong.ore.control.input.Dropdown;
import io.github.luyingyilong.ore.control.input.Radio;
import io.github.luyingyilong.ore.control.input.Segmented;
import io.github.luyingyilong.ore.control.layout.*;
import io.github.luyingyilong.ore.control.navigation.*;
import io.github.luyingyilong.ore.control.overlay.Dialog;
import io.github.luyingyilong.ore.control.overlay.Drawer;
import io.github.luyingyilong.ore.control.overlay.Modal;
import io.github.luyingyilong.ore.render.TextureSource;

import java.util.*;

final class UiStructure {
    private UiStructure() {
    }

    static void clear(UiTypeRegistry.Type type, Control control) {
        switch (type.structure()) {
            case CHILDREN, CHILD, FORM -> {
                for (Control child : control.getChildren()) control.removeChild(child);
            }
            case COLLAPSE -> detach(((Collapse) control).getContent());
            case SPLITTER -> {
                Splitter splitter = (Splitter) control;
                for (Control child : splitter.getFirst().getChildren()) splitter.getFirst().removeChild(child);
                for (Control child : splitter.getSecond().getChildren()) splitter.getSecond().removeChild(child);
            }
            case POPUP -> {
                if (control instanceof Drawer drawer) {
                    detach(drawer.getContent());
                    drawer.getActions().forEach(UiStructure::detach);
                } else if (control instanceof Modal modal) {
                    detach(modal.getContent());
                    modal.getActions().forEach(UiStructure::detach);
                } else {
                    Dialog dialog = (Dialog) control;
                    detach(dialog.getContent());
                    dialog.getActions().forEach(UiStructure::detach);
                }
            }
            case RADIO_GROUP -> {
                UiRadioGroup group = (UiRadioGroup) control;
                group.bind(Map.of(), null);
                for (Control child : group.contentBox().getChildren()) group.contentBox().removeChild(child);
            }
            case CONTENT -> {
                Scroll scroll = (Scroll) control;
                if (scroll.getContent() != null) scroll.removeChild(scroll.getContent());
            }
            case EXTRA -> {
                Navbar navbar = (Navbar) control;
                for (Control extra : navbar.getExtra()) navbar.removeChild(extra);
            }
            case TABS -> {
                Tabs tabs = (Tabs) control;
                for (TabItem item : tabs.getItems()) tabs.removeItem(item.getKey());
            }
            default -> {
            }
        }
    }

    static void apply(UiTypeRegistry registry, UiTypeRegistry.Type type, Control control, UiBlueprint.Node node, List<Control> children) {
        switch (type.structure()) {
            case CHILDREN -> children.forEach(control::addChild);
            case FORM -> children.forEach(child -> ((Form) control).addItem((FormItem<?>) child));
            case FORM_ITEM -> {
                if (((FormItem<?>) control).getField().getControl() != children.get(0))
                    throw new UiException(node.source(), "FormItem field identity changed; give each field a stable id");
            }
            case COLLAPSE, SPLITTER, POPUP -> {
                int index = 0;
                for (var slot : node.children())
                    if (slot.type().equals("slot")) for (var ignored : slot.children()) {
                        Control child = children.get(index++);
                        String name = slot.require("name");
                        if (control instanceof Collapse collapse) collapse.setContent(child);
                        else if (control instanceof Splitter splitter)
                            ((Panel) (name.equals("first") ? splitter.getFirst() : splitter.getSecond())).setChild(child);
                        else if (control instanceof Drawer drawer) {
                            if (name.equals("content")) drawer.setContent(child);
                            else drawer.addAction((Button) child);
                        } else if (control instanceof Modal modal) {
                            if (name.equals("content")) modal.setContent(child);
                            else modal.addAction((Button) child);
                        } else {
                            Dialog dialog = (Dialog) control;
                            if (name.equals("content")) dialog.setContent(child);
                            else dialog.addAction((Button) child);
                        }
                    }
            }
            case RADIO_GROUP -> {
                UiRadioGroup group = (UiRadioGroup) control;
                Map<String, Radio> radios = new LinkedHashMap<>();
                List<UiBlueprint.Node> items = UiTypeRegistry.items(node);
                for (int i = 0; i < items.size(); i++) {
                    String key = items.get(i).require("value");
                    if (key.isBlank() || radios.putIfAbsent(key, (Radio) children.get(i)) != null)
                        throw new UiException(items.get(i).source(), "RadioGroup values must be nonblank and unique");
                }
                children.forEach(group.contentBox()::addChild);
                group.bind(radios, group.getSelected());
            }
            case CHILD -> {
                if (!children.isEmpty()) {
                    if (control instanceof Panel panel) panel.setChild(children.get(0));
                    else ((Padding) control).setChild(children.get(0));
                }
            }
            case CONTENT -> {
                if (!children.isEmpty()) ((Scroll) control).setContent(children.get(0));
            }
            case EXTRA -> {
                if (!children.isEmpty()) ((Navbar) control).setExtra(children.get(0));
            }
            case TABS -> {
                Tabs tabs = (Tabs) control;
                String selected = tabs.getSelectedKey();
                List<UiBlueprint.Node> items = UiTypeRegistry.items(node);
                for (int i = 0; i < items.size(); i++) {
                    var item = items.get(i);
                    tabs.addItem(new TabItem(item.require("key"), UiCodecs.component(item.require("text")), children.get(i), icon(registry, item), bool(item, "disabled")));
                }
                if (selected != null && tabs.getItem(selected) != null && !tabs.getItem(selected).getDisabled())
                    tabs.setSelectedKeyNoSignal(selected);
            }
            case MENU -> {
                List<MenuItem> items = menuItems(registry, node);
                Menu menu = (Menu) control;
                if (!items.equals(menu.getItems())) menu.setItems(items);
            }
            case DESCRIPTIONS -> {
                Descriptions descriptions = (Descriptions) control;
                List<DescriptionItem> previous = descriptions.getItems();
                for (DescriptionItem item : previous) descriptions.removeItem(item.getKey());
                Set<String> keys = new HashSet<>();
                for (var item : UiTypeRegistry.items(node)) {
                    UiTypeRegistry.attributes(item, Set.of("key", "label", "text"));
                    if (!item.children().isEmpty())
                        throw new UiException(item.source(), "Description item cannot contain controls");
                    String key = item.require("key");
                    if (!keys.add(key)) throw new UiException(item.source(), "Duplicate description key " + key);
                    DescriptionItem existing = previous.stream().filter(value -> value.getKey().equals(key)).findFirst().orElse(null);
                    if (existing == null)
                        descriptions.addItem(key, UiCodecs.component(item.require("label")), UiCodecs.component(item.get("text", "")));
                    else {
                        existing.setLabel(UiCodecs.component(item.require("label"))).setContent(UiCodecs.component(item.get("text", "")));
                        descriptions.addItem(existing);
                    }
                }
                for (DescriptionItem item : previous)
                    if (!keys.contains(item.getKey())) {
                        descriptions.removeItem(item.getKey());
                        item.queueFree("XML description removed");
                    }
            }
            case SEGMENTED -> {
                Segmented segmented = (Segmented) control;
                List<UiBlueprint.Node> items = UiTypeRegistry.items(node);
                for (int i = 0; i < items.size(); i++) {
                    var item = items.get(i);
                    UiTypeRegistry.attributes(item, Set.of("text", "icon"));
                    if (!item.children().isEmpty())
                        throw new UiException(item.source(), "Segmented item is a value, not a container");
                    segmented.setIcon(i, icon(registry, item));
                }
            }
            case DROPDOWN -> {
                @SuppressWarnings("unchecked") Dropdown<String> dropdown = (Dropdown<String>) control;
                List<Dropdown.Option<String>> options = new ArrayList<>();
                Set<String> values = new HashSet<>();
                for (var item : UiTypeRegistry.items(node)) {
                    UiTypeRegistry.attributes(item, Set.of("value", "text"));
                    if (!item.children().isEmpty())
                        throw new UiException(item.source(), "Dropdown item cannot contain controls");
                    String value = item.require("value");
                    if (!values.add(value)) throw new UiException(item.source(), "Duplicate dropdown value " + value);
                    options.add(new Dropdown.Option<>(value, UiCodecs.component(item.require("text"))));
                }
                dropdown.setOptions(options);
            }
            default -> {
            }
        }
    }

    private static void detach(Control control) {
        if (control != null && control.getParent() != null) control.getParent().removeChild(control);
    }

    private static List<MenuItem> menuItems(UiTypeRegistry registry, UiBlueprint.Node node) {
        List<MenuItem> values = new ArrayList<>();
        for (var item : UiTypeRegistry.items(node)) {
            UiTypeRegistry.attributes(item, Set.of("key", "text", "type", "icon", "disabled"));
            for (var child : item.children())
                if (!child.type().equals("item"))
                    throw new UiException(child.source(), "Menu groups accept item elements");
            MenuItem.Type type = MenuItem.Type.valueOf(item.get("type", "ITEM"));
            values.add(new MenuItem(item.require("key"), type, UiCodecs.component(item.require("text")), menuItems(registry, item), icon(registry, item), bool(item, "disabled")));
        }
        return List.copyOf(values);
    }

    private static TextureSource icon(UiTypeRegistry registry, UiBlueprint.Node item) {
        return item.attributes().containsKey("icon") ? (TextureSource) registry.convert(TextureSource.class, item.require("icon"), "icon") : null;
    }

    private static boolean bool(UiBlueprint.Node item, String key) {
        return (boolean) UiCodecs.parse(boolean.class, item.get(key, "false"), key);
    }
}
