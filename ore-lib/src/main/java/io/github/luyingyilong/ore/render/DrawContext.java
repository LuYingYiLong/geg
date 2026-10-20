package io.github.luyingyilong.ore.render;

import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.window.WindowContext;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Objects;

/** A borrowed, frame-scoped drawing surface. Never retain this object in a control. */
public final class DrawContext implements AutoCloseable {
    private final GuiGraphics graphics;
    private final Font font;
    private final WindowContext window;
    private final Rect viewport;
    private double mouseX, mouseY;
    private final float partialTick;
    private final long timeMillis;
    private final Thread thread = Thread.currentThread();
    private boolean closed;
    private int clips;
    private final java.util.ArrayDeque<TransformScope> transforms = new java.util.ArrayDeque<>();

    public DrawContext(GuiGraphics graphics, Font font, WindowContext window,
                       Rect viewport, int mouseX, int mouseY, float partialTick) {
        this.graphics = Objects.requireNonNull(graphics);
        this.font = Objects.requireNonNull(font);
        this.window = Objects.requireNonNull(window);
        this.viewport = Objects.requireNonNull(viewport);
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.partialTick = partialTick;
        timeMillis = net.minecraft.Util.getMillis();
    }

    private void checkOpen() {
        if (Thread.currentThread() != thread) throw new IllegalStateException("DrawContext belongs to its rendering thread");
        if (closed) throw new IllegalStateException("DrawContext has left its rendering frame");
    }

    public GuiGraphics getGraphics() { checkOpen(); return graphics; }
    public Font getFont() { checkOpen(); return font; }
    public WindowContext getWindowContext() { checkOpen(); return window; }
    public Rect getViewport() { checkOpen(); return viewport; }
    public int getAbsMouseX() { checkOpen(); return (int)Math.floor(mouseX); }
    public int getAbsMouseY() { checkOpen(); return (int)Math.floor(mouseY); }
    public float getRenderPartialTicks() { checkOpen(); return partialTick; }
    public long getTimeMillis() { checkOpen(); return timeMillis; }

    /** GuiGraphics scissors are screen coordinates; unlike vertices, they do not apply the pose. */
    public void enableScissor(int left, int top, int right, int bottom) {
        checkOpen();
        enableTransformedScissor(graphics, left, top, right, bottom);
        clips++;
    }

    /** A clip already expressed in host GUI coordinates, independent of the current pose. */
    public void enableScreenScissor(Rect clip) {
        checkOpen();graphics.flush();graphics.enableScissor(clip.x(),clip.y(),clip.right(),clip.bottom());clips++;
    }

    public static void enableTransformedScissor(GuiGraphics graphics, int left, int top, int right, int bottom) {
        Rect clip = toScreenRect(graphics.pose().last().pose(), new Rect(left, top, right - left, bottom - top));
        graphics.flush();
        graphics.enableScissor(clip.x(), clip.y(), clip.right(), clip.bottom());
    }

    public Rect toScreenRect(Rect local) { checkOpen(); return toScreenRect(graphics.pose().last().pose(), local); }

    public static Rect toScreenRect(Matrix4f pose, Rect local) {
        float minX=Float.POSITIVE_INFINITY,minY=Float.POSITIVE_INFINITY,maxX=Float.NEGATIVE_INFINITY,maxY=Float.NEGATIVE_INFINITY;
        for(int x:new int[]{local.x(),local.right()}) for(int y:new int[]{local.y(),local.bottom()}) {
            Vector3f p=pose.transformPosition(new Vector3f(x,y,0));
            minX=Math.min(minX,p.x);minY=Math.min(minY,p.y);maxX=Math.max(maxX,p.x);maxY=Math.max(maxY,p.y);
        }
        int x=(int)Math.floor(minX),y=(int)Math.floor(minY);
        return new Rect(x,y,(int)Math.ceil(maxX)-x,(int)Math.ceil(maxY)-y);
    }

    /** Borrow an existing viewer scissor without popping or replacing its stack. */
    public static Rect getHostClip() {
        if (!org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST)) return null;
        var main = net.minecraft.client.Minecraft.getInstance().getWindow();
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            var box = stack.mallocInt(4);
            org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_SCISSOR_BOX, box);
            double scale = main.getGuiScale();
            int left = (int) Math.floor(box.get(0) / scale);
            int top = (int) Math.floor((main.getHeight() - box.get(1) - box.get(3)) / scale);
            int right = (int) Math.ceil((box.get(0) + box.get(2)) / scale);
            int bottom = (int) Math.ceil((main.getHeight() - box.get(1)) / scale);
            return new Rect(left, top, right - left, bottom - top);
        }
    }

    public void disableScissor() {
        checkOpen();
        if (clips == 0) throw new IllegalStateException("Unbalanced Ore clip scope");
        graphics.flush();
        graphics.disableScissor();
        clips--;
    }

    public Scope pushControlTransform(io.github.luyingyilong.ore.layout.Control control,int x,int y) {
        if(control.getRenderTransform().equals(Transform2D.IDENTITY)) return () -> { };
        return pushTransform(control.getRenderTransform().toAffine(control.getScreenBounds(x,y)));
    }

    public Scope pushTransform(Affine2D transform) { return pushTransform(transform,true); }

    public Scope pushTransform(double x,double y,double scale) {
        CanvasTransform.validate(x,y,scale);
        return pushTransform(new Affine2D(scale,0,0,scale,x,y),false);
    }

    private Scope pushTransform(Affine2D transform,boolean transformMouse) {
        checkOpen();
        var matrix=transform.matrix();
        var point=transformMouse?transform.inverse().transform(mouseX,mouseY):new Affine2D.Point(mouseX,mouseY);
        graphics.flush();graphics.pose().pushPose();
        try { graphics.pose().mulPoseMatrix(matrix); } catch(RuntimeException|Error error){graphics.pose().popPose();throw error;}
        TransformScope scope=new TransformScope(mouseX,mouseY);
        mouseX=point.x();mouseY=point.y();
        transforms.push(scope);
        return scope;
    }

    private final class TransformScope implements Scope {
        private boolean active=true;
        private final double oldX,oldY;
        TransformScope(double x,double y){oldX=x;oldY=y;}
        @Override public void close() {
            if(!active)return;
            if(transforms.peek()!=this)throw new IllegalStateException("Transform scopes must close in reverse order");
            try { graphics.flush(); } finally {
                graphics.pose().popPose();transforms.pop();mouseX=oldX;mouseY=oldY;active=false;
            }
        }
    }

    @Override public void close() {
        if (closed) return;
        checkOpen();
        while (clips > 0) disableScissor();
        while (!transforms.isEmpty()) transforms.peek().close();
        closed = true;
    }

    public interface Scope extends AutoCloseable {
        @Override void close();
    }
}
