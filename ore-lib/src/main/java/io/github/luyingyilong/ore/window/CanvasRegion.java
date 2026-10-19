package io.github.luyingyilong.ore.window;

import io.github.luyingyilong.ore.event.EventGeometry;
import io.github.luyingyilong.ore.layout.Container;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.render.CanvasTransform;
import io.github.luyingyilong.ore.render.DrawContext;
import net.minecraft.client.gui.Font;

import java.util.List;
import java.util.Objects;

/** The real tree node behind a Canvas. Geometry stays in local GUI units until this boundary. */
public final class CanvasRegion extends Container {
    private Control content;
    private Rect viewport = new Rect(0, 0, 1, 1);
    private Rect hostClip;
    private CanvasTransform transform = CanvasTransform.IDENTITY;

    public CanvasRegion() { setDebugName("Canvas"); }
    public Control getContent() { return content; }
    public void setContent(Control content) { replaceChild(this.content, Objects.requireNonNull(content)); }
    @Override protected void validateChildren(List<Control> proposed) {
        if (proposed.size() > 1) throw new IllegalStateException("Canvas has one content slot");
        for (Control child : proposed) validateContent(child);
    }
    private static void validateContent(Control control) {
        if (control instanceof io.github.luyingyilong.ore.layout.Window)
            throw new IllegalArgumentException("Embed Window content in Canvas, not another Window host");
        for (Control child : control.getChildren()) validateContent(child);
    }
    @Override protected void childrenChanged() { content = getChildCount() == 0 ? null : getChild(0); }
    public Rect getViewport() { return viewport; }
    public void setViewport(Rect viewport) {
        Objects.requireNonNull(viewport);
        if (viewport.width() < 0 || viewport.height() < 0) throw new IllegalArgumentException("Negative Canvas viewport");
        if (!this.viewport.equals(viewport)) { this.viewport = viewport; requestLayout(); }
    }
    public CanvasTransform getTransform() { return transform; }
    public void setTransform(CanvasTransform transform) {
        Objects.requireNonNull(transform);
        if (!this.transform.equals(transform)) { this.transform = transform; requestLayout(); }
    }
    public void setHostClip(Rect clip) { hostClip = clip; }
    public Rect getHostBounds() {
        Rect area = transform.toScreen(viewport);
        if (hostClip != null) area = EventGeometry.intersect(area, hostClip);
        var owner = getWindowContext().getOwner();
        if (owner != null) area = EventGeometry.intersect(area, owner.getAbsoluteBounds());
        return area;
    }

    @Override public void layout(Rect ignored, Font font) {
        super.layout(viewport, font);
        if (content != null) fitChild(content, new Rect(0, 0, viewport.width(), viewport.height()), font);
    }

    @Override public Control hitTop(int x, int y) {
        if (!isAlive() || !getVisible() || isSuspended() || !getHostBounds().contains(x, y)) return null;
        int localX = (int) Math.floor(transform.toLocalX(x)), localY = (int) Math.floor(transform.toLocalY(y));
        return content == null ? null : content.hitTop(localX, localY);
    }

    /** Do not apply host coordinates twice when walking the captured event path. */
    public static CanvasRegion find(Control node) {
        for (; node != null; node = node.getParent()) if (node instanceof CanvasRegion region) return region;
        return null;
    }

    @Override public void draw(DrawContext context, int x, int y) {
        if (!isAlive() || !getVisible()) return;
        var graphics = context.getGraphics();
        Rect clip = getHostBounds();
        if (clip.width() == 0 || clip.height() == 0) return;
        context.enableScissor(clip.x(), clip.y(), clip.right(), clip.bottom());
        io.github.luyingyilong.ore.debug.DebugBridge.backend().clip(clip);
        try (var transformScope = context.pushTransform(transform.x(), transform.y(), transform.scale());
             var local = new DrawContext(graphics, context.getFont(), context.getWindowContext(), context.getTheme(),
                     viewport, (int) Math.floor(transform.toLocalX(context.getAbsMouseX())),
                     (int) Math.floor(transform.toLocalY(context.getAbsMouseY())), context.getRenderPartialTicks())) {
            var mui = context.getWindowContext().getMui();
            int oldX = mui.getAbsMouseX(), oldY = mui.getAbsMouseY();
            mui.updateState(local.getAbsMouseX(), local.getAbsMouseY(), local.getRenderPartialTicks());
            try { super.draw(local, 0, 0); }
            finally { mui.updateState(oldX, oldY, context.getRenderPartialTicks()); }
        } finally {
            io.github.luyingyilong.ore.debug.DebugBridge.backend().unclip();
            context.disableScissor();
        }
    }
}
