package io.github.luyingyilong.ore.window;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.luyingyilong.ore.control.base.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;

import java.util.ArrayDeque;

/**
 * Host-sized GUI graphics: vanilla GuiGraphics scissor uses Minecraft's primary window.
 */
final class WindowGraphics extends GuiGraphics {
    private final WindowContext context;
    private final ArrayDeque<Rect> clips = new ArrayDeque<>();

    WindowGraphics(Minecraft minecraft, MultiBufferSource.BufferSource buffers, WindowContext context) {
        super(minecraft, buffers);
        this.context = context;
    }

    @Override
    public int guiWidth() {
        return context.getGuiWidth();
    }

    @Override
    public int guiHeight() {
        return context.getGuiHeight();
    }

    @Override
    public void enableScissor(int left, int top, int right, int bottom) {
        flush();
        Rect clip = new Rect(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
        if (!clips.isEmpty()) {
            Rect parent = clips.peek();
            int x = Math.max(left, parent.x()), y = Math.max(top, parent.y());
            clip = new Rect(x, y, Math.max(0, Math.min(right, parent.right()) - x), Math.max(0, Math.min(bottom, parent.bottom()) - y));
        }
        clips.push(clip);
        applyClip();
    }

    @Override
    public void disableScissor() {
        flush();
        if (clips.isEmpty()) throw new IllegalStateException("Unbalanced window scissor");
        clips.pop();
        applyClip();
    }

    private void applyClip() {
        if (clips.isEmpty()) {
            RenderSystem.disableScissor();
            return;
        }
        Rect area = clips.peek();
        double scale = context.getGuiScale();
        int x = (int) Math.floor(area.x() * scale), right = (int) Math.ceil(area.right() * scale);
        int top = (int) Math.floor(area.y() * scale), bottom = (int) Math.ceil(area.bottom() * scale);
        RenderSystem.enableScissor(x, context.getFramebufferHeight() - bottom, Math.max(0, right - x), Math.max(0, bottom - top));
    }
}
