package io.github.luyingyilong.ore.compat.recipeviewer;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.render.Canvas;
import io.github.luyingyilong.ore.render.CanvasTransform;
import io.github.luyingyilong.ore.window.RecipeCanvasFrames;
import io.github.luyingyilong.ore.window.ScreenWindowHost;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.function.Supplier;

/** One view per viewer layout, never per recipe-category singleton. Factory runs only on mounting. */
public final class RecipeCanvasView implements RecipeCanvasFrames.Layout {
    public enum Viewer { JEI, EMI, REI }
    private final Supplier<? extends Control> factory;
    private final Viewer viewer;
    private WeakReference<Screen> screen = new WeakReference<>(null);
    private Canvas canvas;

    public RecipeCanvasView(Viewer viewer, Supplier<? extends Control> factory) {
        this.viewer = Objects.requireNonNull(viewer);
        this.factory = Objects.requireNonNull(factory);
    }

    public static boolean getEnabled(Viewer viewer) {
        var mods = net.minecraftforge.fml.ModList.get();
        return viewer == Viewer.EMI || !mods.isLoaded("emi") && (viewer == Viewer.REI || !mods.isLoaded("roughlyenoughitems"));
    }

    @Override public Screen getScreen() { return screen.get(); }
    public Canvas getCanvas() { return canvas; }

    /** The pose already places the widget; mouse and clipping are normalized to host GUI pixels. */
    public void render(GuiGraphics graphics, int width, int height, float partial) {
        if (!getEnabled(viewer)) return;
        var mc = Minecraft.getInstance();
        if (mc.screen == null || width <= 0 || height <= 0) return;
        if (getScreen() != mc.screen || canvas == null || canvas.isClosed()) {
            close();
            screen = new WeakReference<>(mc.screen);
            canvas = new Canvas(factory.get());
            canvas.attach(ScreenWindowHost.getOrCreate(mc.screen));
        }
        canvas.setViewport(new Rect(0, 0, width, height))
                .setTransform(CanvasTransform.fromPose(graphics.pose().last().pose()));
        // A viewer's scissor can be narrower than the widget (e.g. a recipe-scroll viewport).
        canvas.setHostClip(io.github.luyingyilong.ore.render.DrawContext.getHostClip());
        RecipeCanvasFrames.rendered(this);
        int mouseX = (int) (mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth());
        int mouseY = (int) (mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight());
        canvas.render(graphics, mouseX, mouseY, partial);
    }

    @Override public void close() {
        if (canvas != null) canvas.close();
        canvas = null;
        screen.clear();
        RecipeCanvasFrames.forget(this);
    }
}
