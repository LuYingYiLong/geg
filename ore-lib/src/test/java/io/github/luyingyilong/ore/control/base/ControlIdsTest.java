package io.github.luyingyilong.ore.control.base;

import java.util.*;

public final class ControlIdsTest {
    private static final class Scope extends Container { Scope() { establishIdScope(); } }
    public static void main(String[] args) throws Exception {
        Control root = new Control().setId("root"), a = new Control().setId("a"), b = new Control().setId("b");
        root.addChild(a); root.addChild(b);
        check(root.findControlFromId("root") == root && a.findControlFromId("b") == null, "Search includes self and descendants only");
        a.setVisible(false); check(root.findControlFromId("a") == a, "Hidden IDs remain discoverable");
        reject(() -> a.setId("b")); check(a.getId().equals("a") && root.findControlFromId("b") == b, "Rejected rename has no side effects");
        reject(() -> a.setId("bad id")); reject(() -> root.findControlFromId("a", Container.class));
        Control duplicate = new Control().setId("a"); reject(() -> root.addChild(duplicate)); check(duplicate.getParent() == null, "Rejected mount leaves subtree detached");
        root.removeChild(a); reject(() -> duplicate.addChild(a)); check(a.getParent() == null && duplicate.findControlFromId("a") == duplicate, "Combining detached scopes rejects duplicates");
        // The removed root ID clashes here: rename before combining ordinary subtrees.
        a.setId("child"); duplicate.addChild(a);
        check(duplicate.findControlFromId("child") == a && root.findControlFromId("child") == null, "Detach and merge rebuild authoritative indexes");
        Scope first = new Scope(), second = new Scope(); first.addChild(new Control().setId("repeat")); second.addChild(new Control().setId("repeat"));
        root.addChild(first); root.addChild(second);
        check(root.findControlFromId("repeat") == null && first.findControlFromId("repeat") != second.findControlFromId("repeat"), "Independent document scopes");
        Control factory = new Control().setId("factory"); ControlIdScope.isolateChildren(factory);
        Control inner = new Control().setId("b"); factory.addChild(inner); root.addChild(factory);
        check(root.findControlFromId("factory") == factory && root.findControlFromId("b") == b && factory.findControlFromId("b") == inner, "Opaque factories expose their root, not private descendant IDs");
        Control internal = root.ownPart("internal", new Control().setId("b"));
        check(root.findControlFromId("b") == b && internal.getId().equals("b"), "Owned parts excluded from ordinary ID indexes");
        Control latePart = new Control().setId("latePart"); root.addChild(latePart);
        check(root.findControlFromId("latePart") == latePart, "Ordinary child initially indexed");
        root.ownPart("late", latePart); check(root.findControlFromId("latePart") == null, "Late internal ownership registration removes the ordinary ID immediately");
        Control publicContent = new Control().setId("publicContent"); latePart.addChild(publicContent);
        check(root.findControlFromId("publicContent")==publicContent && root.findControlFromId("latePart")==null,
                "An internal slot wrapper does not hide its public content IDs");
        Control conflictingContent=new Control().setId("publicContent");reject(() -> root.addChild(conflictingContent));conflictingContent.queueFree();
        root.moveChild(b, 0); check(root.getChild(0) == b && root.findControlFromId("b") == b, "Reorder preserves identity");
        b.queueFree(); check(root.findControlFromId("b") == null, "Queued nodes unregistered immediately");
        factory.resetBindingScope(); check(root.findControlFromId("factory") == null && inner.getId().isEmpty(), "Virtual rebind revokes old identities recursively");
        Throwable[] failure = {null}; Thread thread = new Thread(() -> { try { root.setId("other"); } catch (Throwable e) { failure[0] = e; } }); thread.start(); thread.join();
        check(failure[0] instanceof IllegalStateException, "ID operations are thread-confined");
        root.queueFree(); duplicate.queueFree(); LifecycleScheduler.flush();
        System.out.println("ORE_CONTROL_IDS_PASS");
    }
    private static void reject(Runnable action) { try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; } throw new AssertionError("Expected ID rejection"); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
