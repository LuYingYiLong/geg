package io.github.luyingyilong.geg.client.ui.layout;

/** A virtualized child can expose its full extent while receiving only the visible region. */
public interface ScrollContent {
    Size contentExtent();

    void visibleRegion(Rect region);

    /** Returns an anchor restored after a data or width change, or -1 when none is pending. */
    default int consumeRestoredScrollY() { return -1; }

    default void clearScrollAnchor() { }
}
