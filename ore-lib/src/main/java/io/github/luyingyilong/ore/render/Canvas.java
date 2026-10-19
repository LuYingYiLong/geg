package io.github.luyingyilong.ore.render;

import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.window.CanvasRegion;
import io.github.luyingyilong.ore.window.ScreenWindowHost;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Objects;

/** An owned embedding surface, not a second control tree or an input dispatcher. */
public final class Canvas implements AutoCloseable {
    private final CanvasRegion region = new CanvasRegion();
    private ScreenWindowHost host;
    private boolean closed;

    public Canvas(Control content) { setContent(content); }

    public Control getContent() { return region.getContent(); }
    public Canvas setContent(Control content) {
        requireOpen();
        region.setContent(Objects.requireNonNull(content));
        return this;
    }

    public Rect getViewport() { return region.getViewport(); }
    public Canvas setViewport(Rect viewport) { requireOpen(); region.setViewport(viewport); return this; }
    public CanvasTransform getTransform() { return region.getTransform(); }
    public Canvas setTransform(CanvasTransform transform) { requireOpen(); region.setTransform(transform); return this; }
    public Canvas setHostClip(Rect clip) { requireOpen(); region.setHostClip(clip); return this; }
    public Rect getHostBounds() { return region.getHostBounds(); }
    public ScreenWindowHost getHost() { return host; }
    public boolean isClosed() { return closed || !region.isAlive(); }

    public void attach(ScreenWindowHost host) {
        requireOpen();
        Objects.requireNonNull(host);
        if (this.host == host) return;
        if (this.host != null) throw new IllegalStateException("Canvas cannot move between Screen hosts");
        host.attach(this, region);
        this.host = host;
    }

    /** Called by the external widget's draw callback. Input is supplied once by the host. */
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        requireOpen();
        if (host == null) throw new IllegalStateException("Attach Canvas before rendering");
        host.render(this, region, graphics, mouseX, mouseY, partialTick);
    }

    private void requireOpen() {
        region.getSignalScope().checkThread();
        if (isClosed()) throw new IllegalStateException("Canvas has been permanently closed");
    }

    @Override public void close() {
        if (closed) return;
        region.queueFree("Canvas closed");
        closed = true;
        if (host != null) host.remove(this, region);
    }
}
