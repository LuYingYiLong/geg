package io.github.luyingyilong.geg.client.ui;

import io.github.luyingyilong.geg.client.ui.layout.*;
import io.github.luyingyilong.geg.client.ui.theme.OreIcons;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Tree state, icon binding and virtual geometry without a running client. */
public final class TreeTest {
    public static void main(String[] args) throws Exception {
        Tree<String> tree = new Tree<String>().defaultExpandAll(false).rowHeight(26).indent(12);
        var icon = new Button.Icon(OreIcons.ORG, 7, 7);
        tree.items(List.of(
                new Tree.Item<>("root", "", Component.literal("Root"), "root"),
                new Tree.Item<>("disabled", "root", Component.literal("Disabled"), "disabled", true, true, null),
                new Tree.Item<>("folder", "root", Component.literal("Folder"), "folder", false, false, null),
                new Tree.Item<>("leaf", "folder", Component.literal("Leaf with icon"), "leaf", false, true, icon)));
        tree.layout(new Rect(0,0,140,156), null); tree.visibleRegion(new Rect(0,0,140,156));
        check(tree.visibleRows()==1 && tree.expandedKeys().isEmpty(), "Default collapsed state");
        Button switcher = (Button)tree.children().get(0).children().get(0);
        check(switcher.icon().texture().equals(OreIcons.ARROW_RIGHT_LIGHT), "Collapsed sprite");
        AtomicInteger expanded = new AtomicInteger(), selected = new AtomicInteger();
        tree.onExpand(event -> { check(event.expandedKeys().equals(tree.expandedKeys()), "Expansion event snapshot"); expanded.incrementAndGet(); });
        tree.onSelect(value -> selected.incrementAndGet());
        tree.selected("root"); tree.onFocusMove(FocusManager.Direction.RIGHT);
        check(tree.visibleRows()==3 && expanded.get()==1, "Keyboard expansion reports change");
        check(switcher.icon().texture().equals(OreIcons.ARROW_DOWN_LIGHT), "Expanded sprite");
        tree.onFocusMove(FocusManager.Direction.RIGHT);
        check(tree.selected().equals("root") && selected.get()==0, "Skip disabled node and navigate nonselectable folder without selecting");
        tree.onFocusMove(FocusManager.Direction.RIGHT); tree.onFocusMove(FocusManager.Direction.RIGHT);
        check(tree.selected().equals("leaf") && selected.get()==1, "Navigate into child");
        tree.expandedKeys(Set.of("root", "folder"));
        check(expanded.get()==2, "Programmatic expansion is silent");
        tree.showIcon(true).blockNode(false); tree.layout(new Rect(0,0,240,156),null);
        Control leaf = tree.children().stream().filter(row -> row.bounds().y()==3*26).findFirst().orElseThrow();
        Button leafLabel = (Button)leaf.children().get(1);
        check(leafLabel.icon()==icon && leafLabel.bounds().x()==2*12+16, "Per-node icon and indentation");
        check(leafLabel.bounds().width()<tree.contentExtent().width(), "Content-only click area");
        check(!leaf.children().get(0).visible(), "Leaf has no switcher");
        tree.showSwitcher(false);tree.layout(new Rect(0,0,240,156),null);
        check(leafLabel.bounds().x()==2*12, "Hidden switchers release their column");
        tree.disabled(true);check(!tree.acceptsFocus() && !tree.onFocusMove(FocusManager.Direction.UP), "Disabled tree blocks keyboard interaction");
        tree.disabled(false).selectable(false);tree.onFocusMove(FocusManager.Direction.UP);
        check(selected.get()==1, "Global selectable flag");
        tree.expandAll(false);check(tree.visibleRows()==1,"Collapse all");
        tree.search("Leaf");check(tree.visibleRows()==3,"Search temporarily expands matching ancestry");
        tree.search("");check(tree.visibleRows()==1,"Search preserves collapsed state");
        rejects(() -> tree.indent(-1)); rejects(() -> tree.rowHeight(0));
        rejects(() -> tree.expandedKeys(Set.of("missing")));
        rejects(() -> tree.items(List.of(new Tree.Item<>("orphan","missing",Component.empty(),""))));
        rejects(() -> tree.items(List.of(new Tree.Item<>("a","b",Component.empty(),""),new Tree.Item<>("b","a",Component.empty(),""))));
        check(tree.items().size()==4,"Rejected data does not corrupt the previous tree");
        tree.selected("removed-node");
        check(tree.selected().equals("removed-node"),"Controlled selection tolerates a stale key during data refresh");
        for(String name:List.of("arrow_right_light","arrow_down_light"))
            try(var resource=TreeTest.class.getResourceAsStream("/assets/geg/textures/modularui/icons/"+name+".png")) {
                check(resource!=null,"Switcher resource exists");
                byte[] bytes=resource.readNBytes(24);
                check(java.nio.ByteBuffer.wrap(bytes).getInt(16)==7 && java.nio.ByteBuffer.wrap(bytes).getInt(20)==7,"7px switcher sprites");
            }
        System.out.println("GEG_TREE_TEST_PASS");
    }
    private static void check(boolean ok,String message) { if(!ok)throw new AssertionError(message); }
    private static void rejects(Runnable action) { try { action.run();throw new AssertionError("Expected invalid value"); } catch(IllegalArgumentException expected) { } }
}
