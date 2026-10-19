package io.github.luyingyilong.ore.layout;

/** A virtualized child can expose its full extent while receiving only the visible region. */
public interface ScrollContent {
    Size getContentExtent();

    void setVisibleRegion(Rect region);

    /** Returns an anchor restored after a data or width change, or -1 when none is pending. */
    default int consumeRestoredScrollY() { return -1; }

    default void clearScrollAnchor() { }
}
