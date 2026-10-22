package io.github.luyingyilong.ore.gui;

import com.mojang.authlib.GameProfile;
import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.Offset;
import io.github.luyingyilong.ore.control.base.Size;
import io.github.luyingyilong.ore.control.data.Table;
import io.github.luyingyilong.ore.control.data.Tree;
import io.github.luyingyilong.ore.control.display.avatar.Avatar;
import io.github.luyingyilong.ore.control.inventory.Slot;
import io.github.luyingyilong.ore.control.layout.Splitter;
import io.github.luyingyilong.ore.control.overlay.Modal;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.*;

import static io.github.luyingyilong.ore.gui.UiConfiguration.Accessor;
import static io.github.luyingyilong.ore.gui.UiConfiguration.Write;

/**
 * Typed, reversible writes for properties that cannot be represented by a single reflective setter.
 */
final class UiBuiltinProperties {
    private UiBuiltinProperties() {
    }

    static void parse(UiLoader loader, Control control, UiBlueprint.Node node, UiBindings bindings,
                      List<Write> writes, Set<String> handled) {
        if ((control instanceof io.github.luyingyilong.ore.control.overlay.Drawer || control instanceof io.github.luyingyilong.ore.control.overlay.Toast) && any(node, "visible")) {
            if (any(node, "open")) throw new UiException(node.source(), "Use open or visible, not both");
            handled.add("visible");
            boolean value = (boolean) UiCodecs.parse(boolean.class, node.require("visible"), "visible");
            Accessor a = new Accessor(c -> c instanceof io.github.luyingyilong.ore.control.overlay.Drawer d ? d.getOpen() : ((io.github.luyingyilong.ore.control.overlay.Toast) c).getOpen(),
                    (c, v) -> {
                        if (c instanceof io.github.luyingyilong.ore.control.overlay.Drawer d)
                            d.setOpenQuietly((boolean) v);
                        else ((io.github.luyingyilong.ore.control.overlay.Toast) c).setOpenQuietly((boolean) v);
                    });
            writes.add(new Write("open", value, a.read().apply(control), true, a));
        }
        if (control instanceof io.github.luyingyilong.ore.control.form.FormItem<?> item && node.children().stream().anyMatch(n -> n.type().equals("validate"))) {
            var rules = UiForms.validators(node, item.getKey().type(), bindings);
            Accessor a = new Accessor(c -> ((io.github.luyingyilong.ore.control.form.FormItem<?>) c).getValidators(), UiBuiltinProperties::setValidators);
            writes.add(new Write("validators", rules, a.read().apply(control), false, a));
        }
        if (control instanceof Avatar && any(node, "playerId", "playerName", "initials")) {
            handled.addAll(Set.of("playerId", "playerName", "initials"));
            if (node.attributes().containsKey("initials") && any(node, "playerId", "playerName"))
                throw new UiException(node.source(), "Use playerId/playerName or initials");
            Identity value;
            if (node.attributes().containsKey("initials")) value = new Identity(null, node.require("initials"));
            else
                value = new Identity(new GameProfile(node.attributes().containsKey("playerId") ? UUID.fromString(node.require("playerId")) : null, node.get("playerName", "")), "");
            Accessor a = new Accessor(c -> new Identity(((Avatar) c).getPlayer(), ((Avatar) c).getName()), (c, v) -> {
                Identity identity = (Identity) v;
                if (identity.profile() == null) ((Avatar) c).setInitials(identity.initials());
                else ((Avatar) c).setPlayer(identity.profile());
            });
            writes.add(new Write("player", value, a.read().apply(control), false, a));
        }
        if (control instanceof Slot && any(node, "item", "count")) {
            handled.addAll(Set.of("item", "count"));
            if (node.attributes().containsKey("binding"))
                throw new UiException(node.source(), "Slot item preview and binding are mutually exclusive");
            ItemStack item = (ItemStack) loader.getRegistry().convert(ItemStack.class, node.get("item", "minecraft:air"), "item");
            int count = UiBuiltins.integer(node, "count", item.getCount());
            if (count < 0) throw new UiException(node.source(), "Negative item count");
            Accessor a = new Accessor(c -> new Preview(((Slot) c).getItem().copy(), ((Slot) c).getQuantity()), (c, v) -> {
                Preview value = (Preview) v;
                ((Slot) c).setItem(value.item(), value.count());
            });
            writes.add(new Write("item", new Preview(item, count), a.read().apply(control), false, a));
        }
        if (control instanceof Splitter splitter && any(node, "firstMinimum", "firstMaximum", "secondMinimum", "secondMaximum")) {
            handled.addAll(Set.of("firstMinimum", "firstMaximum", "secondMinimum", "secondMaximum"));
            Accessor a = new Accessor(c -> {
                Splitter s = (Splitter) c;
                return List.of(s.getFirstMinimum(), s.getFirstMaximum(), s.getSecondMinimum(), s.getSecondMaximum());
            },
                    (c, v) -> {
                        List<?> n = (List<?>) v;
                        ((Splitter) c).setLimits((int) n.get(0), (int) n.get(1), (int) n.get(2), (int) n.get(3));
                    });
            var values = List.of(UiBuiltins.integer(node, "firstMinimum", splitter.getFirstMinimum()), UiBuiltins.integer(node, "firstMaximum", splitter.getFirstMaximum()),
                    UiBuiltins.integer(node, "secondMinimum", splitter.getSecondMinimum()), UiBuiltins.integer(node, "secondMaximum", splitter.getSecondMaximum()));
            writes.add(new Write("paneLimits", values, a.read().apply(control), false, a));
        }
        if (control instanceof Modal modal) {
            if (any(node, "width", "height")) {
                handled.addAll(Set.of("width", "height"));
                Size value = new Size(UiBuiltins.integer(node, "width", 0), UiBuiltins.integer(node, "height", 0));
                if (value.width() < 0 || value.height() < 0)
                    throw new UiException(node.source(), "Negative modal size");
                Accessor a = new Accessor(c -> ((Modal) c).getRequestedSize(), (c, v) -> {
                    Size size = (Size) v;
                    ((Modal) c).setSize(size.width(), size.height());
                });
                writes.add(new Write("size", value, a.read().apply(control), false, a));
            }
            if (any(node, "position")) {
                handled.add("position");
                Offset value = (Offset) loader.getRegistry().convert(Offset.class, node.require("position"), "position");
                Accessor a = new Accessor(c -> ((Modal) c).getRequestedPosition(), (c, v) -> {
                    if (v == null) ((Modal) c).centered();
                    else {
                        Offset o = (Offset) v;
                        ((Modal) c).setPosition(o.x(), o.y());
                    }
                });
                writes.add(new Write("position", value, a.read().apply(control), false, a));
            }
        }
        if (control instanceof Table<?> && node.children().stream().anyMatch(n -> n.type().equals("row"))) {
            List<UiTableRow> rows = rows(node);
            Accessor a = new Accessor(c -> ((Table<?>) c).getRows(), UiBuiltinProperties::setRows);
            writes.add(new Write("rows", rows, a.read().apply(control), false, a));
        }
        if (control instanceof Tree<?> && !UiTypeRegistry.items(node).isEmpty()) {
            List<Tree.Item<String>> items = new ArrayList<>();
            treeItems(loader, node, "", items);
            Accessor a = new Accessor(c -> ((Tree<?>) c).getItems(), UiBuiltinProperties::setTreeItems);
            writes.add(new Write("items", List.copyOf(items), a.read().apply(control), false, a));
        }
        if (control instanceof Tree<?> tree && any(node, "expandedKeys")) {
            handled.add("expandedKeys");
            Set<String> keys = node.require("expandedKeys").isBlank() ? Set.of() : new LinkedHashSet<>(Arrays.asList(node.require("expandedKeys").split(",")));
            Accessor a = new Accessor(c -> ((Tree<?>) c).getExpandedKeys(), (c, v) -> ((Tree<?>) c).setExpandedKeysNoSignal(strings(v)));
            writes.add(new Write("expandedKeys", keys, tree.getExpandedKeys(), true, a));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setValidators(Control c, Object value) {
        ((io.github.luyingyilong.ore.control.form.FormItem) c).setValidators((List) value);
    }

    @SuppressWarnings("unchecked")
    private static Collection<String> strings(Object value) {
        return (Collection<String>) value;
    }

    @SuppressWarnings("unchecked")
    private static void setRows(Control c, Object value) {
        ((Table<UiTableRow>) c).setRows((List<UiTableRow>) value);
    }

    @SuppressWarnings("unchecked")
    private static void setTreeItems(Control c, Object value) {
        ((Tree<String>) c).setItems((List<Tree.Item<String>>) value);
    }

    private static boolean any(UiBlueprint.Node node, String... names) {
        return Arrays.stream(names).anyMatch(node.attributes()::containsKey);
    }

    private static List<UiTableRow> rows(UiBlueprint.Node node) {
        Set<String> keys = new HashSet<>(), columns = new HashSet<>();
        for (var column : node.children())
            if (column.type().equals("column")) {
                columns.add(column.require("key"));
                columns.add(column.get("nameField", column.require("key")));
                columns.add(column.get("playerIdField", "uuid"));
            }
        List<UiTableRow> result = new ArrayList<>();
        for (var row : node.children())
            if (row.type().equals("row")) {
                UiTypeRegistry.attributes(row, Set.of("key"));
                String key = row.require("key");
                if (!keys.add(key)) throw new UiException(row.source(), "Duplicate table row " + key);
                Map<String, Component> values = new LinkedHashMap<>();
                for (var cell : row.children()) {
                    if (!cell.type().equals("cell") || !cell.children().isEmpty())
                        throw new UiException(cell.source(), "Row requires <cell column=... text=.../>");
                    UiTypeRegistry.attributes(cell, Set.of("column", "text"));
                    String column = cell.require("column");
                    if (!columns.contains(column) || values.putIfAbsent(column, UiCodecs.component(cell.require("text"))) != null)
                        throw new UiException(cell.source(), "Unknown/duplicate table cell " + column);
                }
                for (var column : node.children())
                    if (column.type().equals("column") && column.get("cell", "text").equals("player")) {
                        Component id = values.get(column.get("playerIdField", "uuid"));
                        if (id != null && !id.getString().isBlank()) UUID.fromString(id.getString());
                    }
                result.add(new UiTableRow(key, values));
            }
        return List.copyOf(result);
    }

    private static void treeItems(UiLoader loader, UiBlueprint.Node parent, String parentKey, List<Tree.Item<String>> out) {
        for (var item : UiTypeRegistry.items(parent)) {
            UiTypeRegistry.attributes(item, Set.of("key", "parent", "text", "value", "disabled", "selectable", "icon"));
            for (var child : item.children())
                if (!child.type().equals("item"))
                    throw new UiException(child.source(), "Tree items accept nested items");
            String key = item.require("key"), link = item.get("parent", parentKey);
            if (!parentKey.isEmpty() && !link.equals(parentKey))
                throw new UiException(item.source(), "Nested Tree parent mismatch");
            var icon = item.attributes().containsKey("icon") ? (io.github.luyingyilong.ore.render.TextureSource) loader.getRegistry().convert(io.github.luyingyilong.ore.render.TextureSource.class, item.require("icon"), "icon") : null;
            out.add(new Tree.Item<>(key, link, UiCodecs.component(item.get("text", key)), item.get("value", key),
                    (boolean) UiCodecs.parse(boolean.class, item.get("disabled", "false"), "disabled"),
                    (boolean) UiCodecs.parse(boolean.class, item.get("selectable", "true"), "selectable"), icon));
            treeItems(loader, item, key, out);
        }
    }

    private record Identity(GameProfile profile, String initials) {
    }

    private record Preview(ItemStack item, int count) {
    }
}
