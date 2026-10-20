package io.github.luyingyilong.ore.layout;

import io.github.luyingyilong.ore.event.EventGeometry;
import io.github.luyingyilong.ore.render.Affine2D;
import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.theme.OreTheme;

/** Focus is a local shape with a transform and a Window-space clip, never a clipped replacement rectangle. */
public record FocusGeometry(Rect getLocalBounds, Affine2D getLocalToWindow, Rect getClip) {
    public static FocusGeometry forControl(Control control,Rect treeBounds) {
        Rect origin=control.getAbsoluteBounds();
        return new FocusGeometry(treeBounds.translated(-origin.x(),-origin.y()),
                control.getLocalToWindowTransform(),EventGeometry.clip(control));
    }
    public Rect getVisibleBounds(Control styleOwner) {
        Rect result=getLocalToWindow.bounds(styleOwner.getThemeItem(OreTheme.FOCUS).getDrawRect(getLocalBounds));
        if(getClip!=null)result=EventGeometry.intersect(result,getClip);
        return result.width()==0||result.height()==0?null:result;
    }
    public void draw(Control owner,DrawContext context,int x,int y) {
        if(getVisibleBounds(owner)==null)return;
        var graphics=context.getGraphics();
        graphics.flush();graphics.pose().pushPose();
        float depth=500;
        for(Control node=owner;node!=null;node=node.getGeometryParent())depth+=node.getContentDepth();
        graphics.pose().translate(0,0,depth);
        if(getClip!=null)context.enableScissor(x+getClip.x(),y+getClip.y(),x+getClip.right(),y+getClip.bottom());
        try(var transform=context.pushTransform(Affine2D.translation(x,y).multiply(getLocalToWindow))) {
            owner.getThemeItem(OreTheme.FOCUS).draw(context,getLocalBounds);
        } finally {
            if(getClip!=null)context.disableScissor();
            graphics.flush();graphics.pose().popPose();
        }
    }
}
