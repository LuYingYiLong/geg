package io.github.luyingyilong.geg.client.ui.layout;

/** Offset is a post-layout translation, including descendants, clips, focus and captured input. */
public final class ControlOffsetTest {
    public static void main(String[] args) {
        Control first = new Control().minimum(20, 10).sizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_BEGIN);
        Control second = new Control().minimum(20, 10).sizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_BEGIN);
        Box row = new Box().orientation(Box.Orientation.HORIZONTAL).gap(4).add(first).add(second);
        row.layout(new Rect(10, 20, 80, 20), null);
        Rect assigned = first.bounds(), neighbor = second.bounds();
        Size natural = row.measureSize(null, new Size(80, 20));
        check(first.offset().equals(Offset.ZERO));
        first.offset(5, -3);
        check(row.layoutDirty());
        row.layout(new Rect(10, 20, 80, 20), null);
        check(first.layoutBounds().equals(assigned) && first.bounds().equals(assigned.translated(5, -3)));
        check(second.bounds().equals(neighbor) && row.measureSize(null, new Size(80, 20)).equals(natural));
        Control child = new Control().position(2, 3).requestedSize(4, 5).offset(-1, 2);
        first.addChild(child);
        row.offset(7, 9);
        row.layout(new Rect(10, 20, 80, 20), null);
        Rect parent = first.absoluteBounds();
        check(child.absoluteBounds().equals(new Rect(parent.x() + 1, parent.y() + 5, 4, 5)));
        check(child.screenBounds(30, 40).equals(child.absoluteBounds().translated(30, 40)));
        check(child.hit(child.absoluteBounds().x(), child.absoluteBounds().y()));
        check(child.focusBounds().equals(child.absoluteBounds()));
        row.layout(new Rect(10, 20, 80, 20), null);
        check(first.absoluteBounds().equals(parent)); // Relayout must not accumulate offsets.
        try { first.position(1, 1); throw new AssertionError("Container placement restriction lost"); }
        catch (IllegalStateException expected) { }
        try { first.offset(null); throw new AssertionError("Null offset accepted"); }
        catch (NullPointerException expected) { }

        Probe button = new Probe();
        button.position(20, 100).requestedSize(30, 20).offset(5, -2);
        Control content = new Control().minimum(100, 300).addChild(button);
        Scroll scroll = new Scroll().horizontal(Scroll.BarMode.DISABLED).content(content);
        scroll.offset(3, 4);
        Window window = new Window(true).content(scroll);
        window.layoutViewport(100, 80, null);
        scroll.setScroll(0, 80);
        Rect shown = button.absoluteBounds().translated(0, -scroll.scrollY());
        check(window.pointerPressed(shown.x() + 1, shown.y() + 1, null));
        check(window.pointerReleased(shown.x() + 1, shown.y() + 1) && button.actions == 1);
        check(window.focusManager().focused() == button);
        check(button.focusBounds().equals(button.absoluteBounds()));
        // Offscreen contents remain clipped despite the visual translation.
        check(scroll.hitTop(shown.x() + 1, 200) == null);

        VirtualContent virtual = new VirtualContent();
        virtual.offset(5, 12);
        Scroll virtualScroll = new Scroll().embedded(true).content(virtual);
        Window virtualWindow = new Window(true).content(virtualScroll);
        virtualWindow.layoutViewport(100, 80, null);
        check(virtualScroll.contentExtent().equals(new Size(305, 312)));
        check(virtual.region.equals(new Rect(-5, -12, 100, 80)));
        virtualScroll.setScroll(20, 50);
        check(virtual.region.equals(new Rect(15, 38, 100, 80)));
        virtual.restored = 90;
        virtualWindow.layoutViewport(100, 80, null);
        check(virtualScroll.scrollY() == 102 && virtual.region.y() == 90);
    }

    private static final class VirtualContent extends Control implements ScrollContent {
        private Rect region;
        private int restored = -1;
        @Override public Size contentExtent() { return new Size(300, 300); }
        @Override public void visibleRegion(Rect region) { this.region = region; }
        @Override public int consumeRestoredScrollY() { int result = restored; restored = -1; return result; }
    }

    private static final class Probe extends Control {
        private int actions;
        private Probe() { focusable(true); }
        @Override protected boolean onPointerPressed(int x, int y) { capturePointer(); return hit(x, y); }
        @Override public boolean releasePointer(boolean over) { if (over) actions++; return true; }
    }
    private static void check(boolean condition) { if (!condition) throw new AssertionError("Control Offset regression"); }
}
