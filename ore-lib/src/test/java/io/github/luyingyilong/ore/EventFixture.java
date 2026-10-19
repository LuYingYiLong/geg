package io.github.luyingyilong.ore;
import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.event.*;
/** Adopts an already measured fixture without changing its geometry. */
public final class EventFixture {
    private EventFixture(){}
    public static Window root(Control control){
        Control top=control;while(top.getParent()!=null)top=top.getParent();
        if(top instanceof Window window)return window;
        Window window=new Window(true);window.layout(new Rect(0,0,8192,8192),null);
        if(top instanceof Overlay overlay)window.setOverlay(overlay);else window.setContent(top);
        return window;
    }
    public static boolean press(Control control,int x,int y){return WindowInputs.press(root(control),x,y,null);}
    public static boolean release(Control control,int x,int y,boolean over){return WindowInputs.release(root(control),over?x:-100,over?y:-100);}
    public static boolean scroll(Control control,int x,int y,double delta,boolean horizontal){return WindowInputs.scroll(root(control),x,y,delta,horizontal);}
}
