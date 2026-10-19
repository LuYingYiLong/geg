package io.github.luyingyilong.ore;


import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.theme.OreIcons;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Tree state, icon binding and virtual geometry without a running client. */
public final class TreeTest {
    public static void main(String[] args) throws Exception {
        Tree<String> tree = new Tree<String>().setDefaultExpandAll(false).setRowHeight(26).setIndent(12);
        var icon = new Button.Icon(OreIcons.ORG, 7, 7);
        tree.setItems(List.of(
                new Tree.Item<>("root", "", Component.literal("Root"), "root"),
                new Tree.Item<>("disabled", "root", Component.literal("Disabled"), "disabled", true, true, null),
                new Tree.Item<>("folder", "root", Component.literal("Folder"), "folder", false, false, null),
                new Tree.Item<>("leaf", "folder", Component.literal("Leaf with icon"), "leaf", false, true, icon)));
        tree.layout(new Rect(0,0,140,156), null); tree.setVisibleRegion(new Rect(0,0,140,156));
        check(tree.getVisibleRows()==1 && tree.getExpandedKeys().isEmpty(), "Default collapsed state");
        Button switcher = (Button)tree.getChildren().get(0).getChildren().get(0);
        check(switcher.getIcon().texture().equals(OreIcons.ARROW_RIGHT_LIGHT), "Collapsed sprite");
        AtomicInteger expanded = new AtomicInteger(), selected = new AtomicInteger();
        tree.expansionChanged().connect(tree, event -> { check(event.expandedKeys().equals(tree.getExpandedKeys()), "Expansion event snapshot"); expanded.incrementAndGet(); });
        tree.selectionChanged().connect(tree, value -> selected.incrementAndGet());
        tree.setSelectedNoSignal("root"); tree.onFocusMove(FocusManager.Direction.RIGHT);
        check(tree.getVisibleRows()==3 && expanded.get()==1, "Keyboard expansion reports change");
        check(switcher.getIcon().texture().equals(OreIcons.ARROW_DOWN_LIGHT), "Expanded sprite");
        tree.onFocusMove(FocusManager.Direction.RIGHT);
        check(tree.getSelected().equals("root") && selected.get()==0, "Skip disabled node and navigate nonselectable folder without selecting");
        tree.onFocusMove(FocusManager.Direction.RIGHT); tree.onFocusMove(FocusManager.Direction.RIGHT);
        check(tree.getSelected().equals("leaf") && selected.get()==1, "Navigate into child");
        tree.setExpandedKeys(Set.of("root", "folder"));
        check(expanded.get()==2, "Programmatic expansion is silent");
        tree.setShowIcon(true).setBlockNode(false); tree.layout(new Rect(0,0,240,156),null);
        Control leaf = tree.getChildren().stream().filter(row -> row.getBounds().y()==3*26).findFirst().orElseThrow();
        Button leafLabel = (Button)leaf.getChildren().get(1);
        check(leafLabel.getIcon()==icon && leafLabel.getBounds().x()==2*12+16, "Per-node icon and indentation");
        check(leafLabel.getBounds().width()<tree.getContentExtent().width(), "Content-only click area");
        check(!leaf.getChildren().get(0).getVisible(), "Leaf has no switcher");
        tree.setShowSwitcher(false);tree.layout(new Rect(0,0,240,156),null);
        check(leafLabel.getBounds().x()==2*12, "Hidden switchers release their column");
        tree.setDisabled(true);check(!tree.acceptsFocus() && !tree.onFocusMove(FocusManager.Direction.UP), "Disabled tree blocks keyboard interaction");
        tree.setDisabled(false).setSelectable(false);tree.onFocusMove(FocusManager.Direction.UP);
        check(selected.get()==1, "Global selectable flag");
        tree.expandAll(false);check(tree.getVisibleRows()==1,"Collapse all");
        tree.setSearch("Leaf");check(tree.getVisibleRows()==3,"Search temporarily expands matching ancestry");
        tree.setSearch("");check(tree.getVisibleRows()==1,"Search preserves collapsed state");
        rejects(() -> tree.setIndent(-1)); rejects(() -> tree.setRowHeight(0));
        rejects(() -> tree.setExpandedKeys(Set.of("missing")));
        rejects(() -> tree.setItems(List.of(new Tree.Item<>("orphan","missing",Component.empty(),""))));
        rejects(() -> tree.setItems(List.of(new Tree.Item<>("a","b",Component.empty(),""),new Tree.Item<>("b","a",Component.empty(),""))));
        check(tree.getItems().size()==4,"Rejected data does not corrupt the previous tree");
        tree.setSelected("removed-node");
        check(tree.getSelected().equals("removed-node"),"Controlled selection tolerates a stale key during data refresh");
        for(String name:List.of("arrow_right_light","arrow_down_light"))
            try(var resource=TreeTest.class.getResourceAsStream("/assets/ore/textures/modularui/icons/"+name+".png")) {
                check(resource!=null,"Switcher resource exists");
                byte[] bytes=resource.readNBytes(24);
                check(java.nio.ByteBuffer.wrap(bytes).getInt(16)==7 && java.nio.ByteBuffer.wrap(bytes).getInt(20)==7,"7px switcher sprites");
            }
        System.out.println("ORE_TREE_TEST_PASS");
    }
    private static void check(boolean ok,String message) { if(!ok)throw new AssertionError(message); }
    private static void rejects(Runnable action) { try { action.run();throw new AssertionError("Expected invalid value"); } catch(IllegalArgumentException expected) { } }
}
