package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.event.*;
import io.github.luyingyilong.ore.layout.*;
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
        child.position(10,5).requestedSize(30,20);parent.addChild(child);
        Control page=new Control().addChild(lower.requestedSize(100,60)).addChild(parent.requestedSize(100,60));
        Window root=new Window(true).content(page);root.layoutViewport(100,60,null);
        check(WindowInputs.press(root,12,8,null),"Parent consumes child hit");
        check(calls.equals(List.of("child:PRESS:2,3","parent:PRESS:12,8")),"One target path, no lower sibling");
        calls.clear();WindowInputs.release(root,12,8);
        check(calls.equals(List.of("parent:RELEASE:12,8")),"Handler owns release rather than decorative child");
        parent.consume=false;calls.clear();WindowInputs.press(root,12,8,null);
        check(calls.stream().noneMatch(value->value.startsWith("lower")),"Unhandled target must not click through sibling");
    }
    private static void capture() {
        List<String> calls=new ArrayList<>();Probe a=new Probe("a",calls),b=new Probe("b",calls);
        a.consume=true;b.consume=true;a.requestedSize(30,30);b.position(40,0).requestedSize(30,30);
        Window root=new Window(true).content(new Control().addChild(a).addChild(b));root.layoutViewport(100,60,null);
        root.dispatchInput(new PointerEvent(root.context(),PointerEvent.Action.PRESS,5,5,0,0));
        root.dispatchInput(new PointerEvent(root.context(),PointerEvent.Action.PRESS,45,5,1,0));
        root.dispatchInput(new PointerEvent(root.context(),PointerEvent.Action.RELEASE,45,5,1,0));
        check(b.commits==1&&a.commits==0,"Independent button captures");
        WindowInputs.move(root,90,50);WindowInputs.release(root,90,50);
        check(a.commits==0&&!a.armed,"Drag outside releases original capture without activation");
        WindowInputs.press(root,5,5,null);a.visible(false);a.visible(true);WindowInputs.release(root,5,5);
        check(a.commits==0&&a.cancels>0,"Hidden then restored cannot accept stale release");
        WindowInputs.press(root,5,5,null);root.focusManager().forgetSubtree(a);WindowInputs.release(root,5,5);
        check(a.commits==0,"Rebinding cancels capture");
        WindowInputs.press(root,5,5,null);root.dispatchInput(new CancelEvent(root.context(),CancelEvent.Reason.CLOSED));
        WindowInputs.release(root,5,5);check(a.commits==0,"Window cancellation clears capture");
    }
    private static void coordinates() {
        List<String> calls=new ArrayList<>();Probe child=new Probe("item",calls);child.consume=true;
        child.position(80,100).requestedSize(30,30);
        Scroll inner=new Scroll().content(new Control().preferred(300,400).addChild(child));
        inner.position(40,50).requestedSize(100,100);
        Scroll outer=new Scroll().content(new Control().preferred(300,400).addChild(inner));
        Window root=new Window(true).content(outer);root.layoutViewport(140,140,null);
        outer.setScroll(20,30);inner.setScroll(70,90);
        Rect visible=EventGeometry.displayed(child);
        check(visible.x()==30&&visible.y()==30,"Nested content offsets apply once");
        WindowInputs.press(root,32,33,null);WindowInputs.release(root,32,33);
        check(calls.contains("item:PRESS:2,3")&&child.commits==1,"Local pointer coordinates invert rendering");
        Rect bar=EventGeometry.displayed(inner.verticalBar());
        check(bar.x()==inner.verticalBar().absoluteBounds().x()-20,"Own scrollbar only receives outer content offset");
        check(EventGeometry.clip(child).width()>0,"Nested viewport intersection exists");
    }
    private static void ownership() throws Exception {
        Window first=new Window(true),second=new Window(true);first.layoutViewport(50,50,null);
        PointerEvent event=new PointerEvent(first.context(),PointerEvent.Action.PRESS,1,1,0,0);
        try{second.dispatchInput(event);throw new AssertionError("Foreign event accepted");}catch(IllegalArgumentException expected){}
        first.dispatchInput(event);
        try{first.dispatchInput(event);throw new AssertionError("Event replayed");}catch(IllegalStateException expected){}
        Throwable[] failure={null};Thread thread=new Thread(()->{try{WindowInputs.press(first,1,1,null);}catch(Throwable error){failure[0]=error;}});
        thread.start();thread.join();check(failure[0] instanceof IllegalStateException,"Cross-thread delivery rejected");
    }
    private static void picking() {
        int[] activated={0};Button button=new Button(cue->{}).onPress(()->activated[0]++);
        Window root=new Window(true).content(button);root.layoutViewport(100,40,null);
        WindowInputs.press(root,5,5,null);
        DebugBridge.install(new DebugBridge.Backend(){@Override public boolean picking(Window window){return window==root;}});
        try{WindowInputs.release(root,5,5);check(activated[0]==0,"Picking cancels in-flight business click");}
        finally{DebugBridge.install(null);}
    }
    private static void heldKey() {
        int[] leaked={0};
        Control trap=new Control(){@Override public void input(InputEvent event){
            if(event instanceof KeyEvent){leaked[0]++;event.accept();}
        }}.focusable(true).requestedSize(30,20).position(50,0);
        Window root=new Window(true);
        Button button=new Button(cue->{}).actionMode(Button.ACTION_MODE.BUTTON_PRESS)
                .onPress(()->root.focusManager().requestFocus(trap,null));
        button.requestedSize(30,20);
        root.content(new Control().addChild(button).addChild(trap));root.layoutViewport(100,40,null);
        root.focusManager().requestFocus(button,null);
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
