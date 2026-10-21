package io.github.luyingyilong.ore.control.base;

import io.github.luyingyilong.ore.signal.SignalSource0;
import io.github.luyingyilong.ore.window.Window;

import io.github.luyingyilong.ore.control.input.Button;
import io.github.luyingyilong.ore.event.WindowInputs;
import java.util.*;

/** Tests the public host/event boundaries as well as detached ownership. */
public final class LifecycleTest {
    public static void main(String[] args) throws Exception {
        deletion(); ordering(); readyMutation(); queuedEnter(); adapterReady(); signals(); pointer(); visibility(); preFreeMutation(); exceptions();
        System.out.println("ORE_LIFECYCLE_PASS");
    }
    private static void deletion() throws Exception {
        List<String> log = new ArrayList<>();
        Probe parent = new Probe("p", log), child = new Probe("c", log), pooled = new Probe("pool", log);
        parent.addChild(child); parent.ownPart("part", new Probe("part", log)); parent.ownPooled(pooled);
        child.queueFree(); parent.queueFree(); parent.queueFree();
        check(parent.isQueuedForDeletion() && !parent.isDisposed() && child.isQueuedForDeletion(), "Entire ownership closure is pending");
        parent.removeChild(child);
        rejects(() -> new Control().addChild(child)); rejects(() -> parent.addChild(new Control()));
        rejects(() -> parent.ownPart("late", new Control()));
        LifecycleScheduler.flush();
        check(parent.isDisposed() && child.isDisposed() && pooled.isDisposed(), "Detached and pooled instances cleaned exactly once");
        check(log.indexOf("p:PRE_FREE") < log.indexOf("c:PRE_FREE"), "Ancestor requests merge before child cleanup");
        check(Collections.frequency(log, "c:PRE_FREE") == 1, "No double cleanup");
        check(log.indexOf("c:release") < log.indexOf("p:release"), "Resources release bottom-up");
        Control detached = new Control(); Throwable[] error = {null};
        Thread other = new Thread(() -> { try { detached.queueFree(); } catch (Throwable caught) { error[0] = caught; } });
        other.start(); other.join(); check(error[0] instanceof IllegalStateException, "Cross-thread free rejected");
        detached.queueFree(); LifecycleScheduler.flush(); check(detached.isDisposed(), "Never-mounted control freed");
    }
    private static void ordering() {
        List<String> log = new ArrayList<>(); Probe p = new Probe("p", log), c = new Probe("c", log), part = new Probe("part", log);
        p.addChild(c); p.ownPart("internal", part);
        Window root = new Window(true).setContent(p); root.prepareTree();
        before(log,"p:ENTER_TREE","c:ENTER_TREE"); before(log,"c:READY","p:READY"); before(log,"part:READY","p:READY");
        check(c.isInsideTree() && part.isReady(), "Parts enter with their owner");
        root.hostDetached(); LifecycleScheduler.notifications(); check(c.isInsideTree() && c.isSuspended(), "Suspend is not exit");
        root.prepareTree(); check(!c.isSuspended(), "Resume retained tree");
        p.removeChild(c); LifecycleScheduler.notifications(); check(!c.isInsideTree() && !c.isDisposed(), "Detach reversible");
        p.addChild(c); root.prepareTree(); check(Collections.frequency(log,"c:READY") == 1, "Ready once after reattachment");
        log.clear(); root.queueFree(); LifecycleScheduler.flush();
        before(log,"p:PRE_FREE","c:PRE_FREE"); before(log,"c:EXIT_TREE","p:EXIT_TREE");
    }
    private static void readyMutation() {
        List<String> log = new ArrayList<>(); Probe added = new Probe("added",log), removed = new Probe("removed",log);
        Probe p = new Probe("p", log) {
            @Override protected void notification(ControlNotification n) {
                super.notification(n);
                if(n.type() == ControlNotification.Type.ENTER_TREE) { this.addChild(added); removeChild(removed); }
            }
        };
        p.addChild(removed); Window root = new Window(true).setContent(p); root.prepareTree();
        before(log,"added:ENTER_TREE","added:READY"); before(log,"added:READY","p:READY");
        check(!removed.isReady(), "Removed during preparation never receives ready");
        root.queueFree(); removed.queueFree(); LifecycleScheduler.flush();
    }
    private static void queuedEnter() {
        List<String> log = new ArrayList<>(); Probe child = new Probe("child", log);
        Probe parent = new Probe("parent", log) { @Override protected void notification(ControlNotification n) {
            super.notification(n); if (n.type() == ControlNotification.Type.ENTER_TREE) child.queueFree();
        }};
        parent.addChild(child); Window root = new Window(true).setContent(parent); root.prepareTree(); LifecycleScheduler.flush();
        before(log, "child:ENTER_TREE", "child:EXIT_TREE");
        check(!child.isReady() && child.isDisposed(), "Queued entry preserves transition history, without spurious READY");
        root.queueFree(); LifecycleScheduler.flush();
    }
    private static void adapterReady() {
        boolean[] attached = {false};
        Control field = new Control() { @Override protected boolean lifecycleReady() { return attached[0]; } };
        Window root = new Window(true).setContent(field);
        root.prepareTree(); check(root.isInsideTree() && !field.isReady() && !root.isReady(), "Host preparation waits for input adapter");
        attached[0] = true; root.prepareTree(); check(field.isReady() && root.isReady(), "Adapter activation completes READY");
        root.queueFree(); LifecycleScheduler.flush();
    }
    private static void signals() {
        Control source = new Control(), receiver = new Control(); SignalSource0 changed = new SignalSource0(source,"changed");
        List<Integer> calls = new ArrayList<>();
        changed.signal().connect(receiver, () -> { calls.add(1); source.queueFree(); check(!LifecycleScheduler.flush(), "Cannot free mid-signal"); });
        changed.signal().connect(receiver, () -> calls.add(2)); changed.emit();
        check(calls.equals(List.of(1,2)), "In-flight emission completes in order");
        LifecycleScheduler.flush(); check(changed.signal().connectionCount()==0, "Cleanup disconnects source");
        receiver.queueFree(); LifecycleScheduler.flush();
    }
    private static void pointer() {
        int[] activations={0}; Button button=new Button(); button.pressed().connect(button,()->activations[0]++);
        Window root=new Window(true).setContent(button);root.layoutViewport(100,40,null);
        WindowInputs.press(root,5,5,null);button.queueFree();WindowInputs.release(root,5,5);
        check(activations[0]==0 && root.getFocusManager().getFocused()==null,"Queued button cancels capture and focus immediately");
        check(root.hitTop(5,5)!=button,"Pending button cannot be hit");
        root.queueFree();LifecycleScheduler.flush();
    }
    private static void visibility() {
        List<String> log=new ArrayList<>();Probe p=new Probe("p",log),c=new Probe("c",log);p.addChild(c);
        Window root=new Window(true).setContent(p);root.prepareTree();log.clear();
        p.setVisible(false);LifecycleScheduler.notifications();check(log.contains("c:VISIBILITY_CHANGED"),"Ancestor hiding propagates effective visibility");
        check(c.getVisible(),"Inherited visibility never rewrites the local property");
        p.layout(new Rect(0,0,20,10),null);p.layout(new Rect(0,0,30,15),null);LifecycleScheduler.notifications();
        check(Collections.frequency(log,"p:RESIZED")==1,"Resize merged at layout commit");
        root.queueFree();LifecycleScheduler.flush();
    }
    private static void preFreeMutation() {
        List<String> log = new ArrayList<>(); Probe c = new Probe("c", log);
        Probe p = new Probe("p", log) { @Override protected void notification(ControlNotification n) {
            super.notification(n);
            if (n.type() == ControlNotification.Type.PRE_FREE) { removeChild(c); LifecycleScheduler.notifications(); }
        }};
        p.addChild(c); Window root = new Window(true).setContent(p); root.prepareTree(); log.clear();
        root.queueFree(); LifecycleScheduler.flush();
        before(log, "p:PRE_FREE", "c:PRE_FREE"); before(log, "c:PRE_FREE", "c:EXIT_TREE");
        check(root.isDisposed() && c.isDisposed(), "Reentrant notification drain cannot reorder cleanup transitions");
    }
    private static void exceptions() {
        List<String> log=new ArrayList<>();Probe late=new Probe("late",log);
        Probe p=new Probe("p",log){@Override protected void notification(ControlNotification n){
            super.notification(n);if(n.type()==ControlNotification.Type.PRE_FREE){late.queueFree();throw new IllegalStateException("expected lifecycle regression");}
        }};
        Probe c=new Probe("c",log);p.addChild(c);p.queueFree();LifecycleScheduler.flush();
        check(p.isDisposed()&&c.isDisposed()&&late.isDisposed(),"Notification errors and reentrant requests cannot skip cleanup");
    }
    private static class Probe extends Control {
        final String name;final List<String> log;
        Probe(String name,List<String> log){this.name=name;this.log=log;setDebugName(name);getSignalScope().own(()->log.add(name+":release"));}
        @Override protected void notification(ControlNotification n){log.add(name+":"+n.type());}
    }
    private static void before(List<String> log,String first,String next){check(log.indexOf(first)>=0&&log.indexOf(first)<log.indexOf(next),first+" before "+next+": "+log);}
    private static void rejects(Runnable r){try{r.run();throw new AssertionError("Invalid lifecycle mutation accepted");}catch(IllegalStateException expected){}}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
