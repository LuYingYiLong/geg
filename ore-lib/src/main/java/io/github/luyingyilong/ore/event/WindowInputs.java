package io.github.luyingyilong.ore.event;
import brachy.modularui.screen.viewport.ModularGuiContext;
import io.github.luyingyilong.ore.layout.Window;
/** Thin host conversion only; all routing and editing belongs to Window. */
public final class WindowInputs {
    private WindowInputs(){}
    public static boolean press(Window w,int x,int y,ModularGuiContext mui){return press(w,x,y,mui,0);}
    public static boolean press(Window w,int x,int y,ModularGuiContext mui,int button){
        if(mui!=null)w.context().bind(mui);
        boolean handled=w.dispatchInput(new PointerEvent(w.context(),PointerEvent.Action.PRESS,x,y,button,w.context().modifiers()));
        if(mui!=null && handled)w.context().pointerFocusOwned(true);
        return handled;
    }
    public static boolean release(Window w,int x,int y){return pointer(w,PointerEvent.Action.RELEASE,x,y,0);}
    public static boolean move(Window w,int x,int y){return pointer(w,PointerEvent.Action.MOVE,x,y,0);}
    public static boolean pointer(Window w,PointerEvent.Action action,int x,int y,int button){return w.dispatchInput(new PointerEvent(w.context(),action,x,y,button,w.context().modifiers()));}
    public static boolean keyDown(Window w,int key,int modifiers,ModularGuiContext mui){return key(w,KeyEvent.Action.PRESS,key,0,modifiers,mui);}
    public static boolean keyUp(Window w,int key,int modifiers,ModularGuiContext mui){return key(w,KeyEvent.Action.RELEASE,key,0,modifiers,mui);}
    public static boolean key(Window w,KeyEvent.Action action,int key,int scan,int modifiers,ModularGuiContext mui){
        if(mui!=null)w.context().bind(mui);
        if(action==KeyEvent.Action.PRESS && w.context().keyDown(key))action=KeyEvent.Action.REPEAT;
        return w.dispatchInput(new KeyEvent(w.context(),action,key,scan,modifiers));
    }
    public static boolean scroll(Window w,int x,int y,double delta,boolean horizontal){return w.dispatchInput(new ScrollEvent(w.context(),x,y,horizontal?delta:0,horizontal?0:delta,w.context().modifiers()));}
    public static boolean text(Window w,String text,int modifiers,ModularGuiContext mui){if(mui!=null)w.context().bind(mui);return w.dispatchInput(new TextInputEvent(w.context(),text,modifiers));}
}
