package io.github.luyingyilong.ore.layout;

/**
 * GUI-pixel translation after layout; it does not reserve space in the parent container.
 */
public record Offset(int x, int y) {
    public static final Offset ZERO = new Offset(0, 0);
}
