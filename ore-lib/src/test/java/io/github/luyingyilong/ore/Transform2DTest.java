package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.event.*;
import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.render.*;
import net.minecraft.network.chat.Component;

public final class Transform2DTest {
    public static void main(String[] args) throws Exception {
        Rect area=new Rect(10,20,8,8);
        check(Transform2D.IDENTITY.toAffine(area).bounds(area).equals(area),"identity");
        var rotation=Transform2D.rotation(45).toAffine(area);
        var point=rotation.transform(11.25,22.75);
        var inverse=rotation.inverse().transform(point.x(),point.y());
        check(Math.abs(inverse.x()-11.25)<1e-9&&Math.abs(inverse.y()-22.75)<1e-9,"inverse preserves precision");
        check(rotation.bounds(area).equals(new Rect(8,18,12,12)),"four-corner envelope");
        var quarter=Transform2D.rotation(90).toAffine(new Rect(20,20,20,10));
        check(quarter.bounds(new Rect(20,20,20,10)).equals(new Rect(25,15,10,20)),"quarter-turn envelope");
        check(DrawContext.toScreenRect(rotation.matrix(),area).equals(rotation.bounds(area)),"paint/geometry agree");
        fails(()->Transform2D.rotation(Double.NaN));
        fails(()->new Transform2D(0,0,0,0,0.5,0.5));
        fails(()->new Transform2D(0,0,0,1,2,0.5));

        Window window=Window.embedded();Control body=new Control();Probe probe=new Probe();
        probe.setPosition(20,20).setRequestedSize(20,10);probe.setRenderTransform(Transform2D.rotation(45));
        body.addChild(probe);window.setContent(body);window.layoutViewport(120,90,null);
        check(EventGeometry.contains(probe,30,25)&&!EventGeometry.contains(probe,19,15),"inverse hit, not bounding-box hit");
        press(window,30,25);release(window,30,25);check(probe.commits==1,"one release activation");
        press(window,30,25);release(window,0,0);check(probe.commits==1,"drag out cancels");
        body.setRenderTransform(new Transform2D(0,40,-45,2,0,0));
        var composite=EventGeometry.matrix(probe);var p=composite.transform(30,25);
        check(EventGeometry.contains(probe,(int)Math.round(p.x()),(int)Math.round(p.y())),"nested transform hit");
        var before=body.getRenderTransform();
        Scroll scroll=new Scroll();fails(()->body.addChild(scroll));check(scroll.getParent()==null&&body.getChildCount()==1,"attachment refusal is atomic");
        body.setRenderTransform(Transform2D.IDENTITY);body.addChild(scroll);
        fails(()->body.setRenderTransform(Transform2D.rotation(45)));check(body.getRenderTransform().equals(Transform2D.IDENTITY),"clip refusal retains prior transform");
        Label label=new Label(Component.literal("text"));label.setRenderTransform(Transform2D.rotation(45));
        fails(()->label.setOverflow(Label.Overflow.CLIP));check(label.getOverflow()==Label.Overflow.VISIBLE,"clip policy refusal is atomic");
        NativeField field=new NativeField();fails(()->field.setRenderTransform(Transform2D.rotation(90)));
        Control free=new Control();free.setRenderTransform(Transform2D.rotation(45));
        fails(()->free.ownPart("input",new NativeField()));
        Thread thread=new Thread(()->{try{probe.setRenderTransform(before);}catch(IllegalStateException expected){probe.rejectedThread=true;}});
        thread.start();thread.join();check(probe.rejectedThread,"cross-thread mutation rejected");
        window.queueFree();label.queueFree();field.queueFree();free.queueFree();LifecycleScheduler.flush();
        scaledScroll();
        System.out.println("ORE_TRANSFORM_2D_PASS");
    }
    private static final class NativeField extends Control {
        @Override protected boolean requiresUprightInput(){return true;}
    }
    private static void scaledScroll() {
        Scroll scroll=new Scroll().setContent(new Control().setPreferred(240,180));
        scroll.setRenderTransform(new Transform2D(5,5,0,0.75,0,0));
        Window window=Window.embedded().setContent(scroll);window.layoutViewport(120,60,null);
        Control bar=scroll.getVerticalBar();Rect bounds=bar.getAbsoluteBounds();
        var start=EventGeometry.matrix(bar).transform(bounds.x()+3,bounds.y()+2);
        press(window,(int)Math.floor(start.x()),(int)Math.floor(start.y()));
        check(window.getPointerCaptured(0),"scaled scrollbar press is captured");
        var end=EventGeometry.matrix(bar).transform(bounds.x()+3,bounds.bottom()-2);
        window.dispatchInput(new PointerEvent(window.getContext(),PointerEvent.Action.MOVE,(int)end.x(),(int)end.y(),0,0));
        release(window,(int)end.x(),(int)end.y());
        check(scroll.getScrollY()>0&&!window.getPointerCaptured(0),"scaled scrollbar drags and releases through Window");
        window.queueFree();LifecycleScheduler.flush();
    }
    private static final class Probe extends Control {
        int commits;boolean held,rejectedThread;
        @Override protected boolean pointerDown(int x,int y){held=true;return true;}
        @Override protected boolean pointerUp(boolean over){if(held&&over)commits++;held=false;return true;}
    }
    private static void press(Window w,int x,int y){w.dispatchInput(new PointerEvent(w.getContext(),PointerEvent.Action.PRESS,x,y,0,0));}
    private static void release(Window w,int x,int y){w.dispatchInput(new PointerEvent(w.getContext(),PointerEvent.Action.RELEASE,x,y,0,0));}
    private static void fails(Runnable action){try{action.run();throw new AssertionError("Expected rejection");}catch(IllegalArgumentException|IllegalStateException expected){}}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
