package io.github.luyingyilong.ore.theme;
import io.github.luyingyilong.ore.control.base.Rect;
/** GUI pixels for content/expansion; source pixels for texture borders. */
public record ThemeInsets(int left, int top, int right, int bottom) {
    public static final ThemeInsets ZERO = new ThemeInsets(0,0,0,0);
    public ThemeInsets { if (left < 0 || top < 0 || right < 0 || bottom < 0) throw new IllegalArgumentException("Negative theme inset"); Math.addExact(left,right); Math.addExact(top,bottom); }
    public int getHorizontal() { return Math.addExact(left,right); }
    public int getVertical() { return Math.addExact(top,bottom); }
    public Rect inset(Rect r) { return new Rect(r.x()+Math.min(left,r.width()), r.y()+Math.min(top,r.height()), Math.max(0,r.width()-getHorizontal()), Math.max(0,r.height()-getVertical())); }
    public Rect expand(Rect r) { return new Rect(r.x()-left,r.y()-top,Math.addExact(r.width(),getHorizontal()),Math.addExact(r.height(),getVertical())); }
}
