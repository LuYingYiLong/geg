package io.github.luyingyilong.ore.event;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.control.input.Button;
import io.github.luyingyilong.ore.control.layout.Scroll;
import io.github.luyingyilong.ore.window.Window;

import io.github.luyingyilong.ore.signal.Signals;

import io.github.luyingyilong.ore.debug.DebugBridge;
import java.util.*;

/** Exercise the public Window entrance, not container-internal phase callbacks. */
public final class InputEventDispatchTest {
    public static void main(String[] args) throws Exception {
        bubbling(); capture(); coordinates(); ownership(); picking(); heldKey();
        System.out.println("ORE_INPUT_EVENT_DISPATCH_PASS");
    }
    private static void bubbling() {
        List<String> calls=new ArrayList<>();
        Probe parent=new Probe("parent",calls),child=new Probe("child",calls),lower=new Probe("lower",calls);
        parent.consume=true;child.consume=false;lower.consume=true;
        child.setPosition(10,5).setRequestedSize(30,20);parent.addChild(child);
        var childContainer26 = new Control();
        childContainer26.addChild(lower.setRequestedSize(100, 60));
        childContainer26.addChild(parent.setRequestedSize(100, 60));
        Control page=childContainer26;
        Window root=new Window(true).setContent(page);root.layoutViewport(100,60,null);
        check(WindowInputs.press(root,12,8,null),"Parent consumes child hit");
        check(calls.equals(List.of("child:PRESS:2,3","parent:PRESS:12,8")),"One target path, no lower sibling");
        calls.clear();WindowInputs.release(root,12,8);
        check(calls.equals(List.of("parent:RELEASE:12,8")),"Handler owns release rather than decorative child");
        parent.consume=false;calls.clear();WindowInputs.press(root,12,8,null);
        check(calls.stream().noneMatch(value->value.startsWith("lower")),"Unhandled target must not click through sibling");
    }
    private static void capture() {
        List<String> calls=new ArrayList<>();Probe a=new Probe("a",calls),b=new Probe("b",calls);
        a.consume=true;b.consume=true;a.setRequestedSize(30,30);b.setPosition(40,0).setRequestedSize(30,30);
        var childContainer27 = new Control();
        childContainer27.addChild(a);
        childContainer27.addChild(b);
        Window root=new Window(true).setContent(childContainer27);root.layoutViewport(100,60,null);
        root.dispatchInput(new PointerEvent(root.getContext(),PointerEvent.Action.PRESS,5,5,0,0));
        root.dispatchInput(new PointerEvent(root.getContext(),PointerEvent.Action.PRESS,45,5,1,0));
        root.dispatchInput(new PointerEvent(root.getContext(),PointerEvent.Action.RELEASE,45,5,1,0));
        check(b.commits==1&&a.commits==0,"Independent button captures");
        WindowInputs.move(root,90,50);WindowInputs.release(root,90,50);
        check(a.commits==0&&!a.armed,"Drag outside releases original capture without activation");
        WindowInputs.press(root,5,5,null);a.setVisible(false);a.setVisible(true);WindowInputs.release(root,5,5);
        check(a.commits==0&&a.cancels>0,"Hidden then restored cannot accept stale release");
        WindowInputs.press(root,5,5,null);root.getFocusManager().forgetSubtree(a);WindowInputs.release(root,5,5);
        check(a.commits==0,"Rebinding cancels capture");
        WindowInputs.press(root,5,5,null);root.dispatchInput(new CancelEvent(root.getContext(),CancelEvent.Reason.CLOSED));
        WindowInputs.release(root,5,5);check(a.commits==0,"Window cancellation clears capture");
    }
    private static void coordinates() {
        List<String> calls=new ArrayList<>();Probe child=new Probe("item",calls);child.consume=true;
        child.setPosition(80,100).setRequestedSize(30,30);
        var childContainer28 = new Control().setPreferred(300, 400);
        childContainer28.addChild(child);
        Scroll inner=new Scroll().setContent(childContainer28);
        inner.setPosition(40,50).setRequestedSize(100,100);
        var childContainer29 = new Control().setPreferred(300, 400);
        childContainer29.addChild(inner);
        Scroll outer=new Scroll().setContent(childContainer29);
        Window root=new Window(true).setContent(outer);root.layoutViewport(140,140,null);
        outer.setScroll(20,30);inner.setScroll(70,90);
        Rect visible=EventGeometry.displayed(child);
        check(visible.x()==30&&visible.y()==30,"Nested content offsets apply once");
        WindowInputs.press(root,32,33,null);WindowInputs.release(root,32,33);
        check(calls.contains("item:PRESS:2,3")&&child.commits==1,"Local pointer coordinates invert rendering");
        Rect bar=EventGeometry.displayed(inner.getVerticalBar());
        check(bar.x()==inner.getVerticalBar().getAbsoluteBounds().x()-20,"Own scrollbar only receives outer content offset");
        check(EventGeometry.clip(child).width()>0,"Nested viewport intersection exists");
    }
    private static void ownership() throws Exception {
        Window first=new Window(true),second=new Window(true);first.layoutViewport(50,50,null);
        PointerEvent event=new PointerEvent(first.getContext(),PointerEvent.Action.PRESS,1,1,0,0);
        try{second.dispatchInput(event);throw new AssertionError("Foreign event accepted");}catch(IllegalArgumentException expected){}
        first.dispatchInput(event);
        try{first.dispatchInput(event);throw new AssertionError("Event replayed");}catch(IllegalStateException expected){}
        Throwable[] failure={null};Thread thread=new Thread(()->{try{WindowInputs.press(first,1,1,null);}catch(Throwable error){failure[0]=error;}});
        thread.start();thread.join();check(failure[0] instanceof IllegalStateException,"Cross-thread delivery rejected");
    }
    private static void picking() {
        int[] activated={0};Button button=Signals.configure(io.github.luyingyilong.ore.control.input.ButtonTestAccess.create(cue->{}), connected -> connected.pressed().connect(connected, ()->activated[0]++));
        Window root=new Window(true).setContent(button);root.layoutViewport(100,40,null);
        WindowInputs.press(root,5,5,null);
        DebugBridge.install(new DebugBridge.Backend(){@Override public boolean picking(Window window){return window==root;}});
        try{WindowInputs.release(root,5,5);check(activated[0]==0,"Picking cancels in-flight business click");}
        finally{DebugBridge.install(null);}
    }
    private static void heldKey() {
        int[] leaked={0};
        Control trap=new Control(){@Override public void input(InputEvent event){
            if(event instanceof KeyEvent){leaked[0]++;event.accept();}
        }}.setFocusable(true).setRequestedSize(30,20).setPosition(50,0);
        Window root=new Window(true);
        Button button=Signals.configure(io.github.luyingyilong.ore.control.input.ButtonTestAccess.create(cue->{}).setActionMode(Button.ACTION_MODE.BUTTON_PRESS), connected -> connected.pressed().connect(connected, ()->root.getFocusManager().requestFocus(trap,null)));
        button.setRequestedSize(30,20);
        var childContainer30 = new Control();
        childContainer30.addChild(button);
        childContainer30.addChild(trap);
        root.setContent(childContainer30);root.layoutViewport(100,40,null);
        root.getFocusManager().requestFocus(button,null);
        WindowInputs.keyDown(root,org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE,0,null);
        WindowInputs.keyDown(root,org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE,0,null);
        WindowInputs.keyUp(root,org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE,0,null);
        check(leaked[0]==0,"Held activation must not leak into the new focus target");
    }
    private static final class Probe extends Control {
        final String name;final List<String> calls;boolean consume,armed;int commits,cancels;
        Probe(String name,List<String> calls){this.name=name;this.calls=calls;}
        @Override public void input(InputEvent event){
            if(event instanceof CancelEvent){cancels++;armed=false;return;}
            if(!(event instanceof PointerEvent pointer))return;
            calls.add(name+":"+pointer.action()+":"+event.localX()+","+event.localY());
            if(pointer.action()==PointerEvent.Action.PRESS&&consume){armed=true;event.capturePointer();event.accept();}
            if(pointer.action()==PointerEvent.Action.RELEASE&&armed){if(pointer.over())commits++;armed=false;event.accept();}
        }
        @Override public void cancelInteraction(){armed=false;}
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
