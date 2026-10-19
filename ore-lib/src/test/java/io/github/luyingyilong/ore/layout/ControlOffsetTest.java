package io.github.luyingyilong.ore.layout;

/** Offset is a post-layout translation, including descendants, clips, focus and captured input. */
public final class ControlOffsetTest {
    public static void main(String[] args) {
        Control first = new Control().setMinimum(20, 10).setSizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_BEGIN);
        Control second = new Control().setMinimum(20, 10).setSizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_BEGIN);
        Box row = new Box().setOrientation(Box.Orientation.HORIZONTAL).setGap(4);
        row.addChild(first);
        row.addChild(second);
        row.layout(new Rect(10, 20, 80, 20), null);
        Rect assigned = first.getBounds(), neighbor = second.getBounds();
        Size natural = row.measureSize(null, new Size(80, 20));
        check(first.getOffset().equals(Offset.ZERO));
        first.setOffset(5, -3);
        check(row.getLayoutDirty());
        row.layout(new Rect(10, 20, 80, 20), null);
        check(first.getLayoutBounds().equals(assigned) && first.getBounds().equals(assigned.translated(5, -3)));
        check(second.getBounds().equals(neighbor) && row.measureSize(null, new Size(80, 20)).equals(natural));
        Control child = new Control().setPosition(2, 3).setRequestedSize(4, 5).setOffset(-1, 2);
        first.addChild(child);
        row.setOffset(7, 9);
        row.layout(new Rect(10, 20, 80, 20), null);
        Rect parent = first.getAbsoluteBounds();
        check(child.getAbsoluteBounds().equals(new Rect(parent.x() + 1, parent.y() + 5, 4, 5)));
        check(child.getScreenBounds(30, 40).equals(child.getAbsoluteBounds().translated(30, 40)));
        check(child.hit(child.getAbsoluteBounds().x(), child.getAbsoluteBounds().y()));
        check(child.getFocusBounds().equals(child.getAbsoluteBounds()));
        row.layout(new Rect(10, 20, 80, 20), null);
        check(first.getAbsoluteBounds().equals(parent)); // Relayout must not accumulate offsets.
        try { first.setPosition(1, 1); throw new AssertionError("Container placement restriction lost"); }
        catch (IllegalStateException expected) { }
        try { first.setOffset(null); throw new AssertionError("Null offset accepted"); }
        catch (NullPointerException expected) { }

        Probe button = new Probe();
        button.setPosition(20, 100).setRequestedSize(30, 20).setOffset(5, -2);
        Control content = new Control().setMinimum(100, 300);
        content.addChild(button);
        Scroll scroll = new Scroll().setHorizontal(Scroll.BarMode.DISABLED).setContent(content);
        scroll.setOffset(3, 4);
        Window window = new Window(true).setContent(scroll);
        window.layoutViewport(100, 80, null);
        scroll.setScroll(0, 80);
        Rect shown = button.getAbsoluteBounds().translated(0, -scroll.getScrollY());
        check(io.github.luyingyilong.ore.event.WindowInputs.press(window,shown.x() + 1, shown.y() + 1, null));
        check(io.github.luyingyilong.ore.event.WindowInputs.release(window,shown.x() + 1, shown.y() + 1) && button.actions == 1);
        check(window.getFocusManager().getFocused() == button);
        check(button.getFocusBounds().equals(button.getAbsoluteBounds()));
        // Offscreen contents remain clipped despite the visual translation.
        check(scroll.hitTop(shown.x() + 1, 200) == null);

        VirtualContent virtual = new VirtualContent();
        virtual.setOffset(5, 12);
        Scroll virtualScroll = new Scroll().setEmbedded(true).setContent(virtual);
        Window virtualWindow = new Window(true).setContent(virtualScroll);
        virtualWindow.layoutViewport(100, 80, null);
        check(virtualScroll.getContentExtent().equals(new Size(305, 312)));
        check(virtual.region.equals(new Rect(-5, -12, 100, 80)));
        virtualScroll.setScroll(20, 50);
        check(virtual.region.equals(new Rect(15, 38, 100, 80)));
        virtual.restored = 90;
        virtualWindow.layoutViewport(100, 80, null);
        check(virtualScroll.getScrollY() == 102 && virtual.region.y() == 90);
    }

    private static final class VirtualContent extends Control implements ScrollContent {
        private Rect region;
        private int restored = -1;
        @Override public Size getContentExtent() { return new Size(300, 300); }
        @Override public void setVisibleRegion(Rect region) { this.region = region; }
        @Override public int consumeRestoredScrollY() { int result = restored; restored = -1; return result; }
    }

    private static final class Probe extends Control {
        private int actions;
        private Probe() { setFocusable(true); }
        @Override protected boolean pointerDown(int x, int y) { return hit(x, y); }
        @Override protected boolean pointerUp(boolean over) { if (over) actions++; return true; }
    }
    private static void check(boolean condition) { if (!condition) throw new AssertionError("Control Offset regression"); }
}
