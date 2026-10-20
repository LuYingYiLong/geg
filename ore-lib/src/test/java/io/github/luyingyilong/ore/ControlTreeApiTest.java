package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.form.*;
import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.event.WindowInputs;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/** Public tree contract and named slots, including mutations during a retained gesture. */
public final class ControlTreeApiTest {
    public static void main(String[] args) throws Exception {
        api(); snapshots(); atomicChanges(); reorder(); detachedFocus(); indexes(); slots(); hookFailures();
        System.out.println("ORE_CONTROL_TREE_API_PASS");
    }

    private static void api() throws Exception {
        for (String name : List.of("getParent", "getChildren", "getChildCount"))
            check(Modifier.isFinal(Control.class.getMethod(name).getModifiers()), name + " must be final");
        for (String name : List.of("addChild", "removeChild"))
            check(Modifier.isFinal(Control.class.getMethod(name, Control.class).getModifiers()), name + " must be final");
        check(Control.class.getMethod("addChild", Control.class).getReturnType() == void.class, "Adding is a statement");
        check(Modifier.isFinal(Control.class.getMethod("getChild", int.class).getModifiers()), "Final indexed query");
        check(Modifier.isFinal(Control.class.getMethod("moveChild", Control.class, int.class).getModifiers()), "Final reorder");
        for (Class<?> type : List.of(Box.class, Overlay.class, Grid.class, Form.class, Descriptions.class, Table.class))
            for (var method : type.getDeclaredMethods())
                check(!List.of("add", "attach", "column", "addChild", "removeChild", "getChildren").contains(method.getName()),
                        "No structural alias/override: " + type + "." + method.getName());
    }

    private static void snapshots() throws Exception {
        Control root = new Control(), first = new Control(), hidden = new Control();
        hidden.setVisible(false);
        root.addChild(first); root.addChild(hidden);
        root.ownPart("part", new Control()); root.ownPooled(new Control());
        List<Control> snapshot = root.getChildren();
        rejects(UnsupportedOperationException.class, () -> snapshot.add(new Control()));
        check(root.getChildCount() == 2 && root.getChild(1) == hidden, "Real children include hidden, exclude parts/pool");
        rejects(IndexOutOfBoundsException.class, () -> root.getChild(-1));
        rejects(IndexOutOfBoundsException.class, () -> root.getChild(2));
        first.queueFree();
        check(root.getChildren().equals(snapshot), "Queued children remain until detach");
        root.removeChild(hidden);
        check(snapshot.size() == 2 && root.getChildCount() == 1 && hidden.getParent() == null, "Snapshot is independent");
        Throwable[] error = {null};
        Thread thread = new Thread(() -> { try { root.addChild(new Control()); } catch (Throwable caught) { error[0] = caught; } });
        thread.start(); thread.join();
        check(error[0] instanceof IllegalStateException && root.getChildCount() == 1, "Cross-thread edits leave tree intact");
        root.queueFree(); hidden.queueFree(); LifecycleScheduler.flush();
    }

    private static void atomicChanges() {
        Panel panel = new Panel(null);
        Control original = new Control(), other = new Control(), attached = new Control();
        panel.setChild(original); other.addChild(attached);
        rejects(IllegalStateException.class, () -> panel.setChild(attached));
        check(panel.getChild(0) == original && attached.getParent() == other, "Failed replacement preserves both trees");
        rejects(IllegalArgumentException.class, () -> panel.setChild(panel));
        check(original.getParent() == panel, "Cycle validation precedes detach");
        rejects(IllegalStateException.class, () -> panel.addChild(new Control()));
        panel.setChild(original);
        Control replacement = new Control(); panel.setChild(replacement);
        check(original.getParent() == null && original.isAlive(), "Replacement detaches without freeing");
        Control owner = new Control(), part = owner.ownPart("part", new Control());
        rejects(IllegalArgumentException.class, () -> part.addChild(owner));
        check(owner.getParent() == null && part.getChildCount() == 0, "Mixed ownership cycles are atomic");
        panel.queueFree(); original.queueFree(); other.queueFree(); owner.queueFree(); LifecycleScheduler.flush();
    }

    private static void reorder() {
        List<ControlNotification.Type> notices = new ArrayList<>();
        Control probe = new Control() {
            @Override protected void notification(ControlNotification notice) { notices.add(notice.type()); }
        };
        Button button = new Button(cue -> { });
        button.setMinimum(20, 20);
        int[] presses = {0}; button.pressed().connect(button, () -> presses[0]++);
        Box box = new Box().setOrientation(Box.Orientation.HORIZONTAL);
        box.addChild(button); box.addChild(probe);
        Window window = new Window(true).setContent(box);
        window.prepareTree(); window.layoutViewport(100, 40, font()); LifecycleScheduler.notifications(); notices.clear();
        WindowInputs.press(window, 3, 3, null);
        box.moveChild(button, 1);
        check(box.getChild(1) == button && button.getParent() == box, "Move changes real order");
        WindowInputs.release(window, 3, 3);
        LifecycleScheduler.notifications();
        check(presses[0] == 1 && notices.isEmpty() && probe.isReady(), "Move preserves capture, connection and READY");
        List<Control> before = box.getChildren();
        rejects(IndexOutOfBoundsException.class, () -> box.moveChild(button, 2));
        rejects(IllegalArgumentException.class, () -> box.moveChild(new Control(), 0));
        check(box.getChildren().equals(before), "Invalid reorder has no effects");
        window.queueFree(); LifecycleScheduler.flush();
    }

    private static void detachedFocus() {
        Button button = new Button(cue -> { });
        Box body = new Box(); body.addChild(button);
        Window window = new Window(true).setContent(body);
        // A host can dispatch input before MUI has declared the tree READY.
        window.layoutViewport(100, 40, font());
        WindowInputs.press(window, 3, 3, null);
        check(window.getFocusManager().getFocused() == button, "The unprepared host owns focus");
        body.removeChild(button);
        check(window.getFocusManager().getFocused() == null, "Detach clears host focus even before ENTER_TREE");
        body.addChild(button);
        int[] presses = {0}; button.pressed().connect(button, () -> presses[0]++);
        WindowInputs.release(window, 3, 3);
        check(presses[0] == 0, "Reattachment cannot resurrect a captured press");
        window.queueFree(); LifecycleScheduler.flush();
    }

    private static void indexes() {
        Form form = new Form();
        FormItem item = new FormItem<>(new FieldKey<>("value", String.class), Component.empty(), FormFields.readOnly(String.class, new Control(), () -> "one"));
        form.addItem(item);
        rejects(IllegalArgumentException.class, () -> form.addItem(new FormItem<>(new FieldKey<>("value", String.class), Component.empty(), FormFields.readOnly(String.class, new Control(), () -> "two"))));
        check(form.getValue(new FieldKey<>("value", String.class)).equals("one") && form.getChildCount() == 1, "Form validates before indexing");
        form.removeChild(item);
        rejects(IllegalArgumentException.class, () -> form.getValue(new FieldKey<>("value", String.class)));
        form.addItem(item); item.queueFree(); LifecycleScheduler.flush();
        rejects(IllegalArgumentException.class, () -> form.getValue(new FieldKey<>("value", String.class)));

        Descriptions descriptions = new Descriptions().setColumns(1)
                .addItem("one", Component.empty(), Component.literal("one"))
                .addItem("two", Component.empty(), Component.literal("two"));
        // No getters need to prune or rearrange the actual tree.
        DescriptionItem one = descriptions.getItem("one");
        one.getParent().removeChild(one);
        check(descriptions.getItems().size() == 1 && one.isAlive(), "Actual removal unregisters nested descriptions");
        descriptions.addItem(one);
        rejects(IllegalStateException.class, () -> descriptions.addChild(new Control()));
        descriptions.queueFree(); one.queueFree(); form.queueFree(); LifecycleScheduler.flush();
    }

    private static void slots() {
        Window window = new Window(true); Control body = new Control(); Overlay overlay = new Overlay();
        window.setContent(body).setOverlay(overlay);
        rejects(IllegalArgumentException.class, () -> window.setContent(overlay));
        check(window.getChildCount() == 2 && body.getParent() == window, "Window roles survive failed replacement");
        overlay.queueFree(); LifecycleScheduler.flush();
        check(window.getPopupOverlay() != overlay && window.getChildCount() == 2, "Freed Overlay reference is cleared");
        Scroll scroll = new Scroll().setContent(new Control());
        rejects(IllegalStateException.class, () -> scroll.removeChild(scroll.getChild(0)));
        Control content = scroll.getContent(); content.queueFree(); LifecycleScheduler.flush();
        check(scroll.getContent() == null && scroll.getChildCount() == 2, "Scroll clears content but keeps its bars");
        Control verticalBar = scroll.getVerticalBar(); verticalBar.queueFree(); LifecycleScheduler.flush();
        check(scroll.getVerticalBar() == null && scroll.getChildCount() == 1, "Forced scrollbar removal clears its cached reference");
        scroll.layout(new Rect(0, 0, 100, 50), null);
        Button button = new Button(cue -> { }); Badge badge = new Badge(); button.setBadge(badge);
        badge.queueFree(); LifecycleScheduler.flush();
        check(button.getBadge() == null && button.getChildCount() == 0, "Badge reference follows actual removal");
        Navbar navbar = new Navbar(Component.empty()); Control extra = new Control(); navbar.setExtra(extra);
        Control occupied = new Control(); button.addChild(occupied);
        rejects(IllegalStateException.class, () -> navbar.setExtra(occupied));
        check(extra.getParent() == navbar && occupied.getParent() == button, "Navbar replacement is atomic");
        check(!navbar.removeChild(new Control()), "Removing a non-child has no effects");
        check(navbar.removeChild(extra) && extra.getParent() == null, "An action can be removed through the real tree API");
        navbar.setExtra(extra);
        extra.queueFree(); LifecycleScheduler.flush();
        check(navbar.getChildCount() == 2, "Navbar action cleanup leaves its skeleton");
        for (Control node : List.of(window, scroll, button, navbar)) node.queueFree();
        LifecycleScheduler.flush();
    }

    private static void hookFailures() {
        Control parent = new Control() {
            @Override protected void childrenChanged() { throw new IllegalStateException("expected index hook regression"); }
        };
        Control child = new Control();
        parent.addChild(child);
        check(child.getParent() == parent && parent.getChild(0) == child, "A failed post-commit hook cannot split the tree");
        parent.queueFree(); LifecycleScheduler.flush();
        check(parent.isDisposed() && child.isDisposed() && parent.getChildCount() == 0 && child.getParent() == null,
                "A post-commit hook cannot prevent forced cleanup");
    }

    private static net.minecraft.client.gui.Font font() {
        return new net.minecraft.client.gui.Font(id -> null, false) {
            @Override public int width(String text) { return text.codePointCount(0, text.length()) * 6; }
            @Override public int width(net.minecraft.network.chat.FormattedText text) { return width(text.getString()); }
            @Override public int width(net.minecraft.util.FormattedCharSequence text) {
                int[] count = {0}; text.accept((index, style, codepoint) -> { count[0]++; return true; });
                return count[0] * 6;
            }
        };
    }

    private static void rejects(Class<? extends Throwable> expected, Runnable action) {
        try { action.run(); } catch (Throwable failure) {
            if (expected.isInstance(failure)) return;
            throw new AssertionError("Expected " + expected + ", got " + failure, failure);
        }
        throw new AssertionError("Expected " + expected);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
