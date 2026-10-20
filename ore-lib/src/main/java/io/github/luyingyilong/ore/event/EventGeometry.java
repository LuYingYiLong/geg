package io.github.luyingyilong.ore.event;

import io.github.luyingyilong.ore.Table;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.layout.Scroll;
import io.github.luyingyilong.ore.layout.Window;

/**
 * The same content-branch transform is used for routing, native inputs, popups and focus.
 */
public final class EventGeometry {
    private EventGeometry() {
    }

    /** Maps original, unscrolled tree coordinates to Window coordinates. */
    public static io.github.luyingyilong.ore.render.Affine2D matrix(Control control) { return matrix(control,true); }

    private static io.github.luyingyilong.ore.render.Affine2D matrix(Control control,boolean canvasTransform) {
        var path=new java.util.ArrayList<Control>();
        for(Control node=control;node!=null;node=node.getGeometryParent())path.add(node);
        java.util.Collections.reverse(path);
        var result=io.github.luyingyilong.ore.render.Affine2D.IDENTITY;
        Control parent=null;
        for(Control node:path) {
            if(parent instanceof Scroll scroll && scroll.isContentChild(node))
                result=result.multiply(io.github.luyingyilong.ore.render.Affine2D.translation(-scroll.getScrollX(),-scroll.getScrollY()));
            if(canvasTransform && node instanceof io.github.luyingyilong.ore.window.CanvasRegion region) {
                var c=region.getTransform();
                result=result.multiply(new io.github.luyingyilong.ore.render.Affine2D(c.scale(),0,0,c.scale(),c.x(),c.y()));
            }
            result=result.multiply(node.getRenderTransform().toAffine(node.getAbsoluteBounds()));
            parent=node;
        }
        return result;
    }

    public static Rect displayed(Control control, Rect area) { return matrix(control).bounds(area); }
    public static Rect untransformed(Control control, Rect area) {
        for(Control child=control,node=control.getParent();node!=null;child=node,node=node.getParent())
            if(node instanceof Scroll scroll && scroll.isContentChild(child))area=area.translated(-scroll.getScrollX(),-scroll.getScrollY());
        return area;
    }
    public static Rect displayed(Control control) { return displayed(control,control.getAbsoluteBounds()); }
    public static io.github.luyingyilong.ore.render.Affine2D.Point precisePoint(Control control,double x,double y) {
        return matrix(control).inverse().transform(x,y);
    }
    public static Rect point(Control control,int x,int y) {
        var point=precisePoint(control,x,y);
        return new Rect((int)Math.floor(point.x()),(int)Math.floor(point.y()),0,0);
    }

    /** The dispatcher owns transforms/clips; each node retains its own paint-order policy. */
    public static Control hitTop(Control node,int x,int y) {
        if(node==null||!node.isInputAccessible())return null;
        Rect clip=clip(node);if(clip!=null&&!clip.contains(x,y))return null;
        var point=precisePoint(node,x,y);
        return node.hitTestGeometry((int)Math.floor(point.x()),(int)Math.floor(point.y()),x,y);
    }

    public static Rect intersect(Rect a, Rect b) {
        int x = Math.max(a.x(), b.x()), y = Math.max(a.y(), b.y());
        return new Rect(x, y, Math.max(0, Math.min(a.right(), b.right()) - x), Math.max(0, Math.min(a.bottom(), b.bottom()) - y));
    }

    public static Rect clip(Control control) {
        Rect clip = null;
        for (Control child = control, node = control.getGeometryParent(); node != null; child = node, node = node.getGeometryParent()) {
            Rect next = null;
            if (node instanceof Scroll scroll && scroll.isContentChild(child))
                next = displayed(node, scroll.getViewport().translated(node.getAbsoluteBounds().x(), node.getAbsoluteBounds().y()));
            else if (node instanceof Table<?> table) next = displayed(node, table.getBodyBounds());
            else if (node instanceof io.github.luyingyilong.ore.window.CanvasRegion region) next = region.getHostBounds();
            else if (node instanceof Window) next = node.getAbsoluteBounds();
            else if (node.getClipBounds()!=null) next=displayed(node,node.getClipBounds());
            if (next != null) clip = clip == null ? next : intersect(clip, next);
        }
        return clip;
    }

    public static boolean contains(Control control, int x, int y) {
        if (control == null || !control.isInputAccessible()) return false;
        Rect clip = clip(control);
        Rect p = point(control, x, y);
        return (clip == null || clip.contains(x, y)) && control.hit(p.x(), p.y());
    }
}
