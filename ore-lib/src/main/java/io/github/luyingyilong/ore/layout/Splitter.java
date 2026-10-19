package io.github.luyingyilong.ore.layout;

import io.github.luyingyilong.ore.signal.*;

import io.github.luyingyilong.ore.Divider;
import net.minecraft.client.gui.Font;

import java.util.Objects;

/** Two resizable panes. The themed line and the pointer hit area are independent. */
public final class Splitter extends Container {
    public enum Orientation { HORIZONTAL, VERTICAL }
    public record Sizes(int first, int second) { }

    private final Control first, second;
    private final Divider divider = new Divider().setLayout(Divider.Layout.VERTICAL);
    private Orientation orientation = Orientation.HORIZONTAL;
    private double ratio = 0.35;
    private Integer preferredFirst, pendingFirst;
    private int gap = 4, dragSize = 6;
    private int firstMinimum, secondMinimum;
    private int firstMaximum = Integer.MAX_VALUE, secondMaximum = Integer.MAX_VALUE;
    private boolean showDivider = true, resizable = true, lazy, dragging;
    private int grabOffset;
    private Sizes sizes = new Sizes(0,0);
    private Sizes reportedSizes = sizes;
    private boolean silentResize;
    private Rect bar = new Rect(0,0,0,0);
    private final SignalSource<Sizes> resizeStarted = new SignalSource<>(this, "resizeStarted", "Sizes");
    private final SignalSource<Sizes> resized = new SignalSource<>(this, "resized", "Sizes");
    private final SignalSource<Sizes> resizeEnded = new SignalSource<>(this, "resizeEnded", "Sizes");

    public Splitter(Control first, Control second) {
        this.first = Objects.requireNonNull(first); this.second = Objects.requireNonNull(second);
        if (first == second) throw new IllegalArgumentException("Splitter requires two distinct panes");
        super.addChild(first); super.addChild(second); super.addChild(divider);
    }
    public Control getFirst() { return first; }
    public Control getSecond() { return second; }
    public Orientation getOrientation() { return orientation; }
    public Splitter setOrientation(Orientation orientation) {
        Objects.requireNonNull(orientation);
        if (this.orientation == orientation) return this;
        this.orientation = orientation; cancelInteraction();
        divider.setLayout(orientation == Orientation.HORIZONTAL ? Divider.Layout.VERTICAL : Divider.Layout.HORIZONTAL);
        requestLayout(); return this;
    }
    public double getRatio() { return ratio; }
    /** Switches to proportional sizing; the resolved sizes are notified after layout. */
    public Splitter setRatio(double ratio) {
        if (!Double.isFinite(ratio)) throw new IllegalArgumentException("Invalid split ratio");
        silentResize = false;
        this.ratio = Math.max(0,Math.min(1,ratio)); preferredFirst = null; requestLayout(); return this;
    }
    public Splitter setRatioNoSignal(double ratio) { setRatio(ratio); silentResize = true; return this; }
    /** Switches to a pixel-sized first pane; constraints are still applied during layout. */
    public Splitter setFirstSize(int pixels) { nonnegative(pixels); silentResize = false; preferredFirst = pixels; requestLayout(); return this; }
    public Splitter setFirstSizeNoSignal(int pixels) { setFirstSize(pixels); silentResize = true; return this; }
    public int getFirstSize() { return sizes.first(); }
    public int getSecondSize() { return sizes.second(); }
    public Sizes getSizes() { return sizes; }
    public int getGap() { return gap; }
    public Splitter setGap(int pixels) { nonnegative(pixels); if (gap == pixels) return this; gap = pixels; cancelInteraction(); requestLayout(); return this; }
    public int getDragSize() { return dragSize; }
    public Splitter setDragSize(int pixels) {
        if (pixels < 1) throw new IllegalArgumentException("Splitter drag size must be positive");
        dragSize = pixels; return this;
    }
    public boolean getShowDivider() { return showDivider; }
    public Splitter setShowDivider(boolean show) { showDivider = show; divider.setVisible(show && bothVisible()); return this; }
    public boolean getResizable() { return resizable; }
    public Splitter setResizable(boolean enabled) { resizable = enabled; if (!enabled) cancelInteraction(); return this; }
    public boolean getLazy() { return lazy; }
    public Splitter setLazy(boolean lazy) { if (this.lazy != lazy) { cancelInteraction(); this.lazy = lazy; } return this; }
    public boolean getDragging() { return dragging; }
    public int getFirstMinimum() { return firstMinimum; }
    public int getSecondMinimum() { return secondMinimum; }
    public int getFirstMaximum() { return firstMaximum; }
    public int getSecondMaximum() { return secondMaximum; }
    public Splitter setFirstMinimum(int pixels) { range(pixels,firstMaximum); firstMinimum = pixels; requestLayout(); return this; }
    public Splitter setSecondMinimum(int pixels) { range(pixels,secondMaximum); secondMinimum = pixels; requestLayout(); return this; }
    public Splitter setFirstMaximum(int pixels) { range(firstMinimum,pixels); firstMaximum = pixels; requestLayout(); return this; }
    public Splitter setSecondMaximum(int pixels) { range(secondMinimum,pixels); secondMaximum = pixels; requestLayout(); return this; }
    public Signal<Sizes> resizeStarted() { return resizeStarted.signal(); }
    public Signal<Sizes> resized() { return resized.signal(); }
    public Signal<Sizes> resizeEnded() { return resizeEnded.signal(); }

    private static void nonnegative(int pixels) { if (pixels < 0) throw new IllegalArgumentException("Negative split size"); }
    private static void range(int min,int max) { nonnegative(min); if (max < min) throw new IllegalArgumentException("Invalid split size range"); }
    private boolean horizontal() { return orientation == Orientation.HORIZONTAL; }
    private boolean bothVisible() { return first.getVisible() && second.getVisible(); }
    private int axisMinimum(Control pane,int configured) { return Math.max(configured,horizontal()?pane.getMinimumWidth():pane.getMinimumHeight()); }
    private static int sum(int a,int b) { return (int)Math.min(Integer.MAX_VALUE,(long)a+b); }
    private int extent() { return horizontal()?getBounds().width():getBounds().height(); }
    private Sizes resolve(int extent,Integer position) {
        int minA = axisMinimum(first,firstMinimum), minB = axisMinimum(second,secondMinimum);
        int maxA = Math.max(minA,firstMaximum), maxB = Math.max(minB,secondMaximum);
        int available = Math.max(sum(minA,minB),Math.min(Math.max(0,extent-gap),sum(maxA,maxB)));
        int requested = position != null ? position : preferredFirst != null ? preferredFirst : (int)Math.round(available*ratio);
        int a = Math.max(Math.max(minA,available-maxB),Math.min(Math.min(maxA,available-minB),requested));
        return new Sizes(a,available-a);
    }
    @Override public Size measureSize(Font font,Size available) {
        int a = first.getVisible()?axisMinimum(first,firstMinimum):0, b = second.getVisible()?axisMinimum(second,secondMinimum):0;
        int crossA = !first.getVisible()?0:horizontal()?first.getMinimumHeight():first.getMinimumWidth();
        int crossB = !second.getVisible()?0:horizontal()?second.getMinimumHeight():second.getMinimumWidth();
        int axis = sum(sum(a,b),bothVisible()?gap:0), cross = Math.max(crossA,crossB);
        return new Size(Math.max(getMinimumWidth(),horizontal()?axis:cross),Math.max(getMinimumHeight(),horizontal()?cross:axis));
    }
    @Override public void layout(Rect bounds,Font font) {
        super.layout(bounds,font);
        if (!bothVisible()) {
            cancelInteraction(); divider.setVisible(false);
            sizes = new Sizes(first.getVisible()?extent():0,second.getVisible()?extent():0);
            if (first.getVisible()) fitChild(first,new Rect(0,0,bounds.width(),bounds.height()),font);
            if (second.getVisible()) fitChild(second,new Rect(0,0,bounds.width(),bounds.height()),font);
            bar = new Rect(0,0,0,0); reportLayoutSizes(); return;
        }
        sizes = resolve(extent(),null);
        int a = sizes.first();
        if (horizontal()) {
            fitChild(first,new Rect(0,0,a,bounds.height()),font);
            fitChild(second,new Rect(a+gap,0,sizes.second(),bounds.height()),font);
        } else {
            fitChild(first,new Rect(0,0,bounds.width(),a),font);
            fitChild(second,new Rect(0,a+gap,bounds.width(),sizes.second()),font);
        }
        int linePosition = dragging && lazy && pendingFirst != null ? resolve(extent(),pendingFirst).first() : a;
        bar = horizontal()?new Rect(linePosition,0,gap,bounds.height()):new Rect(0,linePosition,bounds.width(),gap);
        divider.setVisible(showDivider); fitChild(divider,bar,font);
        reportLayoutSizes();
    }
    private void reportLayoutSizes() {
        if (dragging && lazy) return;
        boolean changed = !reportedSizes.equals(sizes);
        reportedSizes = sizes;
        boolean silent = silentResize; silentResize = false;
        if (changed && !silent) resized.emit(sizes);
    }
    /** Local reserved separator area; the themed line is centered inside it. */
    public Rect getDividerBounds() { return bar; }
    private boolean atBar(int x,int y) {
        if (!getVisible() || !resizable || !bothVisible() || !hit(x,y)) return false;
        Rect area = getAbsoluteBounds();
        Rect target = horizontal()?new Rect(bar.x()+(gap-dragSize)/2,0,dragSize,getBounds().height())
                :new Rect(0,bar.y()+(gap-dragSize)/2,getBounds().width(),dragSize);
        return target.translated(area.x(),area.y()).contains(x,y);
    }
    @Override public Control hitTop(int x,int y) { return atBar(x,y)?this:super.hitTop(x,y); }

    @Override protected boolean pointerDown(int x,int y) {
        if (!atBar(x,y)) return false;
        Rect area = getAbsoluteBounds();
        grabOffset = horizontal()?x-area.x()-bar.x():y-area.y()-bar.y();
        dragging = true; pendingFirst = sizes.first(); resizeStarted.emit(sizes); return true;
    }
    private void applyPosition(int position) {
        if (preferredFirst != null) preferredFirst = position;
        else { Sizes target = resolve(extent(),position); ratio = (double)target.first()/Math.max(1,sum(target.first(),target.second())); }
        requestLayout();
    }
    @Override protected boolean pointerMove(int x,int y) {
        if (!dragging) return false;
        if (!resizable || !bothVisible()) { cancelInteraction(); return false; }
        Rect area = getAbsoluteBounds();
        int position = (horizontal()?x-area.x():y-area.y())-grabOffset;
        Sizes target = resolve(extent(),position);
        if (!Objects.equals(pendingFirst,target.first())) {
            pendingFirst = target.first();
            if (!lazy) applyPosition(target.first()); else requestLayout();
            reportedSizes = target;
            resized.emit(target);
        }
        return true;
    }
    @Override protected boolean pointerUp(boolean over) {
        if (!dragging) return false;
        Sizes target = resolve(extent(),pendingFirst);
        if (lazy && pendingFirst != null) applyPosition(target.first());
        dragging = false; pendingFirst = null; requestLayout(); resizeEnded.emit(target); return true;
    }
    @Override public void cancelInteraction() {
        boolean wasDragging = dragging; dragging = false; pendingFirst = null;
        if (wasDragging && lazy) reportedSizes = sizes;
        if (wasDragging) requestLayout();
    }
}
