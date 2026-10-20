package io.github.luyingyilong.ore.theme;
import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.layout.Offset;
import java.util.Objects;
public record StyleBoxFlat(int color, ThemeInsets borders, int borderColor, ThemeInsets getContentMargin,
                           ThemeInsets getExpandMargin, ThemeInsets getDrawInset,
                           Offset getContentOffset) implements StyleBox {
    public StyleBoxFlat(int color, ThemeInsets borders, int borderColor, ThemeInsets contentMargin, ThemeInsets expandMargin) {
        this(color, borders, borderColor, contentMargin, expandMargin, ThemeInsets.ZERO, Offset.ZERO);
    }
    public StyleBoxFlat {
        Objects.requireNonNull(borders); Objects.requireNonNull(getContentMargin); Objects.requireNonNull(getExpandMargin);
        Objects.requireNonNull(getDrawInset); Objects.requireNonNull(getContentOffset);
    }
    public void draw(DrawContext context, Rect area) {
        Rect r=getDrawRect(area); var g=context.getGraphics();
        if ((color >>> 24)!=0) g.fill(r.x(),r.y(),r.right(),r.bottom(),color);
        if ((borderColor >>> 24)==0) return;
        int l=Math.min(r.width(),borders.left()), t=Math.min(r.height(),borders.top());
        int right=Math.min(r.width()-l,borders.right()), b=Math.min(r.height()-t,borders.bottom());
        g.fill(r.x(),r.y(),r.x()+l,r.bottom(),borderColor);
        g.fill(r.right()-right,r.y(),r.right(),r.bottom(),borderColor);
        g.fill(r.x()+l,r.y(),r.right()-right,r.y()+t,borderColor);
        g.fill(r.x()+l,r.bottom()-b,r.right()-right,r.bottom(),borderColor);
    }
}
