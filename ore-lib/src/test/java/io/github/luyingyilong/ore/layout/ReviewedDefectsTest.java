package io.github.luyingyilong.ore.layout;

import io.github.luyingyilong.ore.Descriptions;
import io.github.luyingyilong.ore.Form;
import io.github.luyingyilong.ore.FormItem;
import io.github.luyingyilong.ore.Label;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/** Reproductions from the component-library review; exercise public APIs and host preparation. */
public final class ReviewedDefectsTest {
    public static void main(String[] args) {
        descriptionsDeletion();
        ownershipCycles();
        readyAfterMutation();
        wrappedBoxHeight();
        formVisibility();
        hiddenGridChildren();
        System.out.println("ORE_REVIEWED_DEFECTS_PASS");
    }

    private static void descriptionsDeletion() {
        Font font = font();
        Descriptions descriptions = new Descriptions().setColumns(1)
                .addItem("one", Component.literal("Name"), Component.literal("Alice"))
                .addItem("two", Component.literal("Details"), Component.literal("Example"));
        Rect area = new Rect(0, 0, 200, 100);
        descriptions.layout(area, font);
        descriptions.getItem("one").queueFree();
        descriptions.layout(area, font);
        check(descriptions.getItems().size() == 1, "Pending deletion must leave the descriptions index immediately");
        LifecycleScheduler.flush();
        descriptions.layout(area, font);
        check(!descriptions.removeItem("one"), "Deleted description no longer has an indexed entry");
        descriptions.addItem("one", Component.literal("Replacement"), Component.literal("Bob"));
        descriptions.layout(area, font);
        descriptions.getItem("two").setVisible(false);
        descriptions.layout(area, font);
        descriptions.getItem("two").queueFree();
        LifecycleScheduler.flush();
        descriptions.layout(area, font);
        check(descriptions.getItems().size() == 1, "Freed hidden items must also leave the index");
        descriptions.clearItems();
        descriptions.layout(area, font);
        check(descriptions.getItems().isEmpty(), "Clear after individual deletion is safe");
        descriptions.queueFree();
        LifecycleScheduler.flush();
    }

    private static void ownershipCycles() {
        Control owner = new Control();
        Control part = owner.ownPart("part", new Control());
        rejectsCycle(() -> part.addChild(owner));
        check(owner.getParent() == null && part.getChildren().isEmpty(), "Rejected attachment must be atomic");
        Control leaf = new Control();
        part.addChild(leaf);
        rejectsCycle(() -> leaf.ownPooled(owner));
        rejectsCycle(() -> leaf.ownPart("cycle", owner));
        rejectsCycle(() -> owner.addChild(owner));

        Control tree = new Control(), descendant = new Control();
        tree.addChild(descendant);
        rejectsCycle(() -> descendant.ownPart("ancestor", tree));
        rejectsCycle(() -> descendant.ownPooled(tree));
        rejectsCycle(() -> descendant.addChild(tree));

        Control poolOwner = new Control(), middle = new Control();
        Control pooled = poolOwner.ownPooled(new Control());
        poolOwner.addChild(middle);
        middle.addChild(pooled);
        rejectsCycle(() -> pooled.addChild(poolOwner));
        poolOwner.removeChild(middle);
        middle.queueFree();
        owner.queueFree();
        tree.queueFree();
        poolOwner.queueFree();
        LifecycleScheduler.flush();
        check(owner.isDisposed() && part.isDisposed() && leaf.isDisposed() && pooled.isDisposed(),
                "Valid mixed ownership can still be cleaned safely");
    }

    private static void readyAfterMutation() {
        List<String> log = new ArrayList<>();
        Control parent = new Control() {
            @Override protected void notification(ControlNotification notice) {
                if (notice.type() == ControlNotification.Type.READY) log.add("parent ready");
            }
        };
        Window root = new Window(true).setContent(parent);
        parent.addChild(new Control() {
            @Override protected void notification(ControlNotification notice) {
                if (notice.type() == ControlNotification.Type.READY) root.removeChild(parent);
            }
        });
        root.prepareTree();
        check(log.isEmpty() && !parent.isInsideTree() && !parent.isReady(), "Detached ancestor must not finish READY");
        root.setContent(parent);
        root.prepareTree();
        check(log.equals(List.of("parent ready")) && parent.isReady(), "Reattachment can finish READY exactly once");
        root.prepareTree();
        check(log.size() == 1, "Repeated preparation must not repeat READY");
        root.queueFree();
        LifecycleScheduler.flush();

        Control queuedParent = new Control();
        queuedParent.addChild(new Control() {
            @Override protected void notification(ControlNotification notice) {
                if (notice.type() == ControlNotification.Type.READY) queuedParent.queueFree();
            }
        });
        Window secondRoot = new Window(true).setContent(queuedParent);
        secondRoot.prepareTree();
        check(!queuedParent.isReady(), "Ancestor queued during child READY cannot become ready");
        secondRoot.queueFree();
        LifecycleScheduler.flush();
    }

    private static void wrappedBoxHeight() {
        Font font = font();
        for (String text : List.of("ABCDEFGHIJKLMNOP", "橡木台阶市场详情组织简介测试文本")) {
            for (int width : new int[]{100, 127, 128, 129}) {
                Label label = new Label(Component.literal(text)).setWrap(Label.Wrap.CHARACTER);
                label.setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL);
                Box row = new Box().setOrientation(Box.Orientation.HORIZONTAL).setGap(4);
                row.addChild(label);
                row.addChild(new Control().setMinimum(50, 10));
                Control footer = new Control().setMinimum(0, 10);
                Box column = new Box();
                column.addChild(row);
                column.addChild(footer);
                column.layout(new Rect(0, 0, width, 200), font);
                int required = label.measureSize(font, new Size(label.getSize().width(), 200)).height();
                check(label.getSize().height() >= required, "Row must measure wrapping at the allocated child width");
                check(footer.getBounds().y() >= label.getBounds().bottom(), "Wrapped label must not overlap the next row");
                column.queueFree();
            }
        }
        LifecycleScheduler.flush();
    }

    private static void formVisibility() {
        Form form = new Form();
        form.addItem(new FormItem("example", Component.literal("Example"), new Control().setMinimum(10, 10), () -> "value"));
        Control generic = form;
        List<Boolean> changes = new ArrayList<>();
        form.visibilityChanged().connect(form, changes::add);
        check(!generic.getVisible() && !form.validate(), "A new form starts hidden");
        generic.setVisible(true);
        check(form.getVisible() && form.validate(), "Generic Control visibility must show and enable a form");
        generic.setVisible(true);
        generic.setVisible(false);
        check(!form.getVisible() && !form.validate(), "Generic Control visibility must hide and disable a form");
        check(changes.equals(List.of(true, false)), "Visibility has one state and only emits actual changes");
        form.queueFree();
        LifecycleScheduler.flush();
    }

    private static void hiddenGridChildren() {
        for (Grid.LayoutMode mode : Grid.LayoutMode.values()) {
            Control first = new Control().setMinimum(10, 20), second = new Control().setMinimum(10, 30);
            first.setVisible(false);
            Grid<Object> grid = new Grid<>().setMode(mode).setColumns(10, 1, 4);
            grid.addChild(first);
            grid.addChild(second);
            Rect area = new Rect(0, 0, 50, 100);
            grid.layout(area, null);
            check(second.getBounds().y() == 0 && grid.getContentHeight() == 30, "Hidden fixed children reserve no cells");
            first.setVisible(true);
            grid.layout(area, null);
            check(second.getBounds().y() == 24 && grid.getContentHeight() == 54, "Showing a fixed child restores its cell");
            first.queueFree();
            grid.layout(area, null);
            check(second.getBounds().y() == 0 && grid.getContentHeight() == 30, "Pending fixed children reserve no cells");
            second.setVisible(false);
            grid.layout(area, null);
            check(grid.getContentWidth() == 0 && grid.getContentHeight() == 0, "An entirely hidden grid has no content extent");
            grid.queueFree();
            LifecycleScheduler.flush();
        }
    }

    private static Font font() {
        return new Font(id -> null, false) {
            @Override public int width(String text) { return text.codePointCount(0, text.length()) * 6; }
            @Override public int width(FormattedText text) { return width(text.getString()); }
            @Override public int width(FormattedCharSequence text) {
                int[] count = {0};
                text.accept((index, style, codepoint) -> { count[0]++; return true; });
                return count[0] * 6;
            }
        };
    }

    private static void rejectsCycle(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Cyclic ownership was accepted");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
