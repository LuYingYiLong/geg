package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.layout.*;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/** A visible auto scrollbar crosses the 4-to-3-column breakpoint during provisional measurements. */
public final class LabelMarqueeGridTest {
    public static void main(String[] args) {
        Font font = new Font(id -> null, false) {
            @Override public int width(FormattedText text) { return text.getString().length() * 6; }
            @Override public int width(FormattedCharSequence text) {
                int[] count = {0}; text.accept((index, style, codepoint) -> { count[0]++; return true; });
                return count[0] * 6;
            }
        };
        Grid<Integer> grid = new Grid<Integer>().setColumns(96, 4, 4).setAdapter(new Grid.Adapter<>() {
            public int getSize() { return 100; }
            public Integer getItem(int index) { return index; }
            public Object getKey(Integer item) { return item; }
            public int measureHeight(Integer item, int width, Font font) { return 42; }
            public Grid.View<Integer> createView() { return new Card(); }
        });
        Scroll scroll = new Scroll().setHorizontal(Scroll.BarMode.DISABLED).setContent(grid);
        int offset = 0;
        for (int frame = 0; frame < 30; frame++) {
            scroll.layout(new Rect(0, 0, 396, 90), font);
            Card first = (Card) grid.getChildren().stream().filter(child -> ((Card) child).item == 0).findFirst().orElseThrow();
            offset = first.label.marqueeOffset(font, first.label.getBounds().width(), 1000 + frame * 100L, true);
        }
        if (offset <= 0) throw new AssertionError("Repeated grid layout keeps resetting the visible label's marquee");
    }

    private static final class Card extends Container implements Grid.View<Integer> {
        final Label label = new Label().setOverflow(Label.Overflow.MARQUEE);
        int item;
        Card() { this.addChild(label); }
        public void bind(Integer item) { this.item = item; label.setText(Component.literal("A very long market item name " + item)); }
        @Override public void layout(Rect bounds, Font font) {
            super.layout(bounds, font); fitChild(label, new Rect(0, 0, bounds.width(), 12), font);
        }
        public void draw(DrawContext context, Rect bounds, boolean hovered) { }
    }
}
