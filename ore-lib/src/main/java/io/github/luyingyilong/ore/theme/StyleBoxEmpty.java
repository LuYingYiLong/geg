package io.github.luyingyilong.ore.theme;
import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.control.base.Offset;
import java.util.Objects;
public record StyleBoxEmpty(ThemeInsets getContentMargin, ThemeInsets getExpandMargin,
                            ThemeInsets getDrawInset, Offset getContentOffset) implements StyleBox {
    public static final StyleBoxEmpty INSTANCE = new StyleBoxEmpty(ThemeInsets.ZERO,ThemeInsets.ZERO);
    public StyleBoxEmpty(ThemeInsets contentMargin, ThemeInsets expandMargin) {
        this(contentMargin, expandMargin, ThemeInsets.ZERO, Offset.ZERO);
    }
    public StyleBoxEmpty {
        Objects.requireNonNull(getContentMargin); Objects.requireNonNull(getExpandMargin);
        Objects.requireNonNull(getDrawInset); Objects.requireNonNull(getContentOffset);
    }
    public void draw(DrawContext context, Rect area) { }
}
