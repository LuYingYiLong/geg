package io.github.luyingyilong.geg.client.ui.window;

import brachy.modularui.screen.viewport.ModularGuiContext;
import io.github.luyingyilong.geg.client.ui.Dropdown;
import io.github.luyingyilong.geg.client.ui.layout.*;

/** Shared popup layer for generic embedded and native windows; existing canvases retain their adapters. */
public final class WindowPopups {
    private final WindowContext context;
    private Dropdown<?> capture;
    WindowPopups(WindowContext context) { this.context=context; }
    private Dropdown<?> find(Control node) {
        if(node==null||!node.visible())return null;
        var children=node.children();
        for(int i=children.size()-1;i>=0;i--){var result=find(children.get(i));if(result!=null)return result;}
        return node instanceof Dropdown<?> dropdown&&dropdown.isOpen()
                &&context.owner().focusManager().allowsNativeInput(dropdown)?dropdown:null;
    }
    private Rect area(Control control) {
        Rect area=control.absoluteBounds();
        for(Control child=control,node=control.parent();node!=null;child=node,node=node.parent())
            if(node instanceof Scroll scroll&&scroll.isContentChild(child))area=area.translated(-scroll.scrollX(),-scroll.scrollY());
        return area;
    }
    public boolean press(int x,int y) {
        Dropdown<?> dropdown=find(context.owner()); if(dropdown==null)return false;
        context.owner().focusManager().pointerActivity();capture=dropdown;Rect area=area(dropdown);
        dropdown.click(area.x(),area.y(),area.width(),area.height(),x,y);return true;
    }
    public boolean release(int x,int y) {
        if(capture==null)return false;Dropdown<?> dropdown=capture;capture=null;
        if(dropdown.visible()&&context.owner().focusManager().allowsNativeInput(dropdown))dropdown.releaseAt(x,y);else dropdown.cancelInteraction();
        return true;
    }
    public boolean capturing() { return capture != null; }
    public boolean scroll(double delta) { Dropdown<?> dropdown=find(context.owner());return dropdown!=null&&dropdown.scroll(delta); }
    public boolean open() { return find(context.owner())!=null; }
    public void cancel() { if(capture!=null)capture.cancelInteraction();capture=null; }
    public void draw(ModularGuiContext mui,int x,int y) {
        Dropdown<?> dropdown=find(context.owner());if(dropdown==null)return;Rect area=area(dropdown).translated(x,y);
        var graphics=mui.getGraphics();graphics.flush();graphics.pose().pushPose();
        try{graphics.pose().translate(0,0,6500);dropdown.drawMenu(mui,area.x(),area.y(),area.width(),area.height());graphics.flush();}
        finally{graphics.pose().popPose();}
    }
}
