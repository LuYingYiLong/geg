package io.github.luyingyilong.ore.layout;

import brachy.modularui.screen.viewport.ModularGuiContext;
import net.minecraft.client.gui.Font;

/** Closing animations may synchronously detach layers or dispose their entire window. */
public final class OverlayMutationTest {
    public static void main(String[] args) {
        Rect viewport = new Rect(0, 0, 200, 100);
        Overlay layout = new Overlay();
        int[] layouts = {0, 0};
        Control removed = new Control() {
            @Override public void layout(Rect bounds, Font font) { layouts[0]++; super.layout(bounds, font); }
        };
        Control added = new Control() {
            @Override public void layout(Rect bounds, Font font) { layouts[1]++; super.layout(bounds, font); }
        };
        Control closer = new Control() {
            @Override public void layout(Rect bounds, Font font) {
                super.layout(bounds, font); queueFree(); removed.queueFree(); layout.add(added);
            }
        };
        layout.add(closer).add(removed);
        layout.layout(viewport, null);
        check(layouts[0] == 0 && layouts[1] == 0, "Removed layers are skipped; added layers wait for the next pass");
        layout.layout(viewport, null);
        check(layouts[1] == 1, "The new layer participates in the next layout");
        layout.queueFree();

        Overlay drawing = new Overlay();
        int[] paints = {0};
        drawing.add(new Control() {
            @Override public void draw(ModularGuiContext context, int x, int y) { drawing.queueFree(); }
        }).add(new Control() {
            @Override public void draw(ModularGuiContext context, int x, int y) { paints[0]++; }
        });
        drawing.draw(null, 0, 0);
        check(drawing.isQueuedForDeletion() && paints[0] == 0, "Closing the root during draw does not paint destroyed layers");

        Overlay picking = new Overlay();
        Control underneath = new Control();
        picking.add(underneath).add(new Control() {
            @Override public Control hitTop(int x, int y) { underneath.queueFree(); queueFree(); return null; }
        });
        picking.layout(viewport, null);
        check(picking.hitTop(10, 10) == null, "Hit testing also skips removed layers");
        picking.queueFree();
        LifecycleScheduler.flush();
        System.out.println("ORE_OVERLAY_MUTATION_PASS");
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
