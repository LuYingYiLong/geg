package io.github.luyingyilong.ore.gui;

import io.github.luyingyilong.ore.control.data.PlayerIdentityCell;
import io.github.luyingyilong.ore.control.data.Table;
import io.github.luyingyilong.ore.control.data.Tree;
import io.github.luyingyilong.ore.control.display.DescriptionItem;
import io.github.luyingyilong.ore.control.display.Label;
import io.github.luyingyilong.ore.control.display.avatar.Avatar;
import io.github.luyingyilong.ore.control.form.Form;
import io.github.luyingyilong.ore.control.inventory.Slot;
import io.github.luyingyilong.ore.control.layout.Collapse;
import io.github.luyingyilong.ore.control.layout.Panel;
import io.github.luyingyilong.ore.control.layout.Splitter;
import io.github.luyingyilong.ore.control.overlay.*;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Structural built-ins stay explicit; ordinary configuration still uses annotated setters.
 */
final class UiBuiltins {
    private UiBuiltins() {
    }

    static void register(UiTypeRegistry r) {
        r.builtin("Collapse", Collapse::new, UiTypeRegistry.Structure.COLLAPSE);
        r.builtin("Splitter", () -> new Splitter(new Panel(), new Panel()), UiTypeRegistry.Structure.SPLITTER);
        r.builtin("Avatar", Avatar::new, UiTypeRegistry.Structure.LEAF);
        r.builtin("Slot", Slot::new, UiTypeRegistry.Structure.LEAF);
        r.builtin("Overlay", Overlay::new, UiTypeRegistry.Structure.CHILDREN);
        r.builtin("Modal", () -> new Modal(Component.empty()), UiTypeRegistry.Structure.POPUP);
        r.put("Dialog", new UiTypeRegistry.Type(node -> new Dialog(integer(node, "width", 0), integer(node, "height", 0), integer(node, "verticalOffset", 0), Component.empty()),
                UiTypeRegistry.Structure.POPUP, node -> List.of(node.get("width", "0"), node.get("height", "0"), node.get("verticalOffset", "0")).toString(),
                Map.of(), Set.of("width", "height", "verticalOffset")));
        r.builtin("Drawer", () -> new Drawer(Component.empty()), UiTypeRegistry.Structure.POPUP);
        r.builtin("Toast", Toast::new, UiTypeRegistry.Structure.LEAF);
        r.builtin("Form", () -> {
            Form form = new Form();
            form.setVisible(true);
            return form;
        }, UiTypeRegistry.Structure.FORM);
        r.put("FormItem", new UiTypeRegistry.Type(UiForms::create, UiTypeRegistry.Structure.FORM_ITEM,
                node -> UiForms.constructionKey(r, node), Map.of(), Set.of("name")));
        r.builtin("RadioGroup", UiRadioGroup::new, UiTypeRegistry.Structure.RADIO_GROUP);
        r.put("Tree", new UiTypeRegistry.Type(node -> new Tree<String>(), UiTypeRegistry.Structure.TREE, node -> "",
                Map.of("selectionChanged", String.class, "expansionChanged", Tree.Expansion.class)));
        r.put("Table", new UiTypeRegistry.Type((node, children, bindings) -> table(r, node, bindings), UiTypeRegistry.Structure.TABLE,
                node -> node.children().stream().filter(n -> n.type().equals("column")).map(UiTypeRegistry::fingerprint).toList().toString(),
                Map.of("rowActivated", UiTableRow.class), Set.of()));
        r.put("Inventory", new UiTypeRegistry.Type(UiInventories::create, UiTypeRegistry.Structure.INVENTORY,
                node -> List.of(node.get("source", ""), node.get("slotCount", "36"), node.get("columns", "9"), node.get("slotIndices", ""),
                        node.get("mainSlots", ""), node.get("hotbarSlots", ""), UiTypeRegistry.itemKey(node)).toString(),
                Map.of(), Set.of("source", "slotCount", "columns", "slotIndices", "mainSlots", "hotbarSlots")));
        r.put("DescriptionItem", new UiTypeRegistry.Type(node -> new DescriptionItem(node.require("key"), Component.empty(), Component.empty()),
                UiTypeRegistry.Structure.LEAF, node -> node.require("key"), Map.of(), Set.of("key")));
    }

    private static Table<UiTableRow> table(UiTypeRegistry registry, UiBlueprint.Node node, UiBindings bindings) {
        Table<UiTableRow> table = new Table<>();
        try {
            table.setRowKey(UiTableRow::key);
            for (var column : node.children())
                if (column.type().equals("column")) {
                    UiTypeRegistry.attributes(column, Set.of("key", "text", "minWidth", "width", "grow", "wrap", "maxLines", "cell", "nameField", "playerIdField"));
                    if (!column.children().isEmpty())
                        throw new UiException(column.source(), "Column cannot contain controls; register a UiTableCell");
                    String key = column.require("key"), kind = column.get("cell", "text");
                    Table.Cell<UiTableRow> cell;
                    if (kind.startsWith("ref:"))
                        cell = ((UiTableCell) bindings.requireValue(kind.substring(4), UiTableCell.class)).cell();
                    else if (kind.equals("player")) cell = new PlayerIdentityCell<>(row -> {
                        String id = row.getValue(column.get("playerIdField", "uuid")).getString();
                        return id.isBlank() ? null : UUID.fromString(id);
                    }, row -> row.getValue(column.get("nameField", key)).getString());
                    else if (kind.equals("text")) cell = new Table.TextCell<UiTableRow>(row -> row.getValue(key))
                            .setWrap((Label.Wrap) registry.convert(Label.Wrap.class, column.get("wrap", "NONE"), "wrap"))
                            .setMaxLines(integer(column, "maxLines", Integer.MAX_VALUE));
                    else throw new UiException(column.source(), "Unknown table cell " + kind);
                    int min = integer(column, "minWidth", 24), width = integer(column, "width", min), grow = integer(column, "grow", 1);
                    if (min < 1 || width < min || grow < 0)
                        throw new UiException(column.source(), "Invalid table column size");
                    table.addColumn(new Table.Column<>(key, UiCodecs.component(column.get("text", key)), min, width, grow, available -> 22, cell));
                }
            return table;
        } catch (RuntimeException | Error failure) {
            table.queueFree("XML table factory failed");
            throw failure;
        }
    }

    static int integer(UiBlueprint.Node node, String name, int fallback) {
        try {
            return Integer.parseInt(node.get(name, Integer.toString(fallback)));
        } catch (NumberFormatException invalid) {
            throw new UiException(node.source(), "Invalid integer " + name, invalid);
        }
    }
}
