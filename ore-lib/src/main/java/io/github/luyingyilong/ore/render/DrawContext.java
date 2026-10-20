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
    private final int mouseX, mouseY;
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
    public int getAbsMouseX() { checkOpen(); return mouseX; }
    public int getAbsMouseY() { checkOpen(); return mouseY; }
    public float getRenderPartialTicks() { checkOpen(); return partialTick; }
    public long getTimeMillis() { checkOpen(); return timeMillis; }

    /** GuiGraphics scissors are screen coordinates; unlike vertices, they do not apply the pose. */
    public void enableScissor(int left, int top, int right, int bottom) {
        checkOpen();
        enableTransformedScissor(graphics, left, top, right, bottom);
        clips++;
    }

    public static void enableTransformedScissor(GuiGraphics graphics, int left, int top, int right, int bottom) {
        Rect clip = toScreenRect(graphics.pose().last().pose(), new Rect(left, top, right - left, bottom - top));
        graphics.flush();
        graphics.enableScissor(clip.x(), clip.y(), clip.right(), clip.bottom());
    }

    public Rect toScreenRect(Rect local) { checkOpen(); return toScreenRect(graphics.pose().last().pose(), local); }

    public static Rect toScreenRect(Matrix4f pose, Rect local) {
        Vector3f a = pose.transformPosition(new Vector3f(local.x(), local.y(), 0));
        Vector3f b = pose.transformPosition(new Vector3f(local.right(), local.bottom(), 0));
        int x = (int) Math.floor(a.x), y = (int) Math.floor(a.y);
        return new Rect(x, y, Math.max(0, (int) Math.ceil(b.x) - x), Math.max(0, (int) Math.ceil(b.y) - y));
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

    public Scope pushTransform(double x, double y, double scale) {
        checkOpen();
        CanvasTransform.validate(x, y, scale);
        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale((float) scale, (float) scale, 1);
        TransformScope scope = new TransformScope();
        transforms.push(scope);
        return scope;
    }

    private final class TransformScope implements Scope {
        private boolean active = true;
        @Override public void close() {
            if (!active) return;
            if (transforms.peek() != this) throw new IllegalStateException("Transform scopes must close in reverse order");
            graphics.flush();
            graphics.pose().popPose();
            transforms.pop();
            active = false;
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
