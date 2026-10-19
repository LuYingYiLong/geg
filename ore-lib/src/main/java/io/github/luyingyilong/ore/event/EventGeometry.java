package io.github.luyingyilong.ore.event;
import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.Table;
/** The same content-branch transform is used for routing, native inputs, popups and focus. */
public final class EventGeometry {
    private EventGeometry() {}
    public static Rect displayed(Control control, Rect area) {
        for(Control child=control,node=control.parent();node!=null;child=node,node=node.parent())
            if(node instanceof Scroll scroll && scroll.isContentChild(child))area=area.translated(-scroll.scrollX(),-scroll.scrollY());
        return area;
    }
    public static Rect displayed(Control control){return displayed(control,control.absoluteBounds());}
    public static Rect point(Control control,int x,int y){
        Rect area=displayed(control);var tree=control.absoluteBounds();
        return new Rect(x+tree.x()-area.x(),y+tree.y()-area.y(),0,0);
    }
    public static Rect intersect(Rect a,Rect b){
        int x=Math.max(a.x(),b.x()),y=Math.max(a.y(),b.y());
        return new Rect(x,y,Math.max(0,Math.min(a.right(),b.right())-x),Math.max(0,Math.min(a.bottom(),b.bottom())-y));
    }
    public static Rect clip(Control control){
        Rect clip=null;
        for(Control child=control,node=control.parent();node!=null;child=node,node=node.parent()){
            Rect next=null;
            if(node instanceof Scroll scroll && scroll.isContentChild(child))next=displayed(node,scroll.viewport().translated(node.absoluteBounds().x(),node.absoluteBounds().y()));
            else if(node instanceof Table<?> table)next=displayed(node,table.bodyBounds());
            else if(node instanceof Window)next=node.absoluteBounds();
            if(next!=null)clip=clip==null?next:intersect(clip,next);
        }
        return clip;
    }
    public static boolean contains(Control control,int x,int y){
        if(control==null || !(control.isAlive() && control.visible()))return false;
        Rect clip=clip(control);Rect p=point(control,x,y);
        return (clip==null||clip.contains(x,y))&&control.hit(p.x(),p.y());
    }
}
