package io.github.luyingyilong.ore.layout;

public record Rect(int x, int y, int width, int height) {
    public Rect {
        width = Math.max(0, width);
        height = Math.max(0, height);
    }

    public int right() { return x + width; }
    public int bottom() { return y + height; }
    public boolean contains(int px, int py) {
        return px >= x && px < right() && py >= y && py < bottom();
    }
    public Rect translated(int dx, int dy) { return new Rect(x + dx, y + dy, width, height); }
}
