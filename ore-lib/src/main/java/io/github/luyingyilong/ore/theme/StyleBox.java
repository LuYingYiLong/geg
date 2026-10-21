package io.github.luyingyilong.ore.theme;
import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.control.base.Size;
import io.github.luyingyilong.ore.control.base.Offset;
public sealed interface StyleBox permits StyleBoxEmpty, StyleBoxFlat, StyleBoxTexture {
    ThemeInsets getContentMargin();
    ThemeInsets getExpandMargin();
    ThemeInsets getDrawInset();
    Offset getContentOffset();
    void draw(DrawContext context, Rect area);
    default Rect getContentRect(Rect area) { return getContentMargin().inset(area); }
    /** Presentation-only geometry; neither inset nor offset changes layout or hit regions. */
    default Rect getContentDrawRect(Rect area) {
        return getContentRect(area).translated(getContentOffset().x(), getContentOffset().y());
    }
    /** Focus follows the inset background, without expanding for decorative paint margins. */
    default Rect getBackgroundRect(Rect area) { return getDrawInset().inset(area); }
    default Rect getDrawRect(Rect area) { return getExpandMargin().expand(getBackgroundRect(area)); }
    default Size getMinimumSize() { return new Size(getContentMargin().getHorizontal(),getContentMargin().getVertical()); }
}
