package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.layout.Box;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.layout.Scroll;
import io.github.luyingyilong.ore.layout.Size;
import io.github.luyingyilong.ore.layout.SizeFlags;
import io.github.luyingyilong.ore.theme.OreTheme;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/** Responsive columns, retained item ownership, text wrapping and actual Box/Label geometry. */
public final class DescriptionsLayoutTest {
    public static void main(String[] args) {
        Font font = new Font(id -> null, false) {
            @Override public int width(String text) { return text.codePointCount(0, text.length()) * 6; }
            @Override public int width(FormattedText text) { return width(text.getString()); }
            @Override public int width(FormattedCharSequence text) {
                int[] count = {0};
                text.accept((index, style, codepoint) -> { count[0]++; return true; });
                return count[0] * 6;
            }
        };
        Descriptions details = new Descriptions().title(Component.literal("组织信息"))
                .add("name", Component.literal("名称"), Component.literal("橡木公社"))
                .add("leader", Component.literal("领袖"), Component.literal("Player"))
                .add("members", Component.literal("成员"), Component.literal("8"))
                .add("description", Component.literal("介绍"), Component.literal("中英文长段落 Long description 😀").withStyle(ChatFormatting.GOLD));
        check(details.children().size() == 1 && details.children().get(0) instanceof Box, "Descriptions is a retained Box tree");
        check(details.columns() == 3 && details.layout() == Descriptions.Layout.HORIZONTAL && details.colon(), "Lightweight defaults");
        for (int width : new int[]{80, 96, 127, 128, 129, 199, 200, 303, 304, 500}) {
            Size measured = details.measureSize(font, new Size(width, 1000));
            details.layout(new Rect(11, 17, width, measured.height()), font);
            int expected = width < 200 ? 1 : width < 304 ? 2 : 3;
            check(details.effectiveColumns(width) == expected, "Column count responds to cell minima and gaps at width " + width);
            check(measured.width() == Math.max(96, width), "Keep a column's minimum even below the viewport width");
            int cellWidth = (measured.width() - (expected - 1) * details.columnGap()) / expected;
            for (int index = 0; index < details.items().size(); index++) {
                DescriptionItem item = details.items().get(index);
                check(item.size().width() >= cellWidth && item.size().width() <= cellWidth + 1, "Equal column widths with stable pixel remainder");
                check(item.absoluteBounds().y() >= 17 + details.titleNode().lineHeight(font) + details.rowGap(), "Title remains above rows");
                Label label = item.labelNode(), content = item.contentNode();
                check(content.absoluteBounds().x() >= label.absoluteBounds().right() + details.itemGap(), "Horizontal label/value gap");
                check(content.absoluteBounds().bottom() <= item.absoluteBounds().bottom(), "Wrapped value fits the measured row");
                check(label.absoluteBounds().bottom() <= item.absoluteBounds().bottom(), "Wrapped caption fits the measured row");
                check(item.parent() instanceof Box && item.parent().parent() == details.children().get(0), "Rows own their items inside the retained tree");
            }
            for (int index = 1; index < expected; index++) {
                check(details.items().get(index).absoluteBounds().y() == details.item("name").absoluteBounds().y(), "Row items align at their top");
            }
            details.layout(new Rect(11, 17, width, measured.height()), font);
            check(!details.layoutDirty(), "Stable layout does not continually invalidate itself");
        }

        DescriptionItem name = details.item("name");
        check(name.labelNode().text().getString().equals("名称:"), "Optional colon is rendered through Label");
        details.colon(false).layout(Descriptions.Layout.VERTICAL);
        Size vertical = details.measureSize(font, new Size(304, 1000));
        details.layout(new Rect(0, 0, 304, vertical.height()), font);
        check(name.labelNode().text().getString().equals("名称"), "Disabling the colon preserves original text");
        check(name.contentNode().absoluteBounds().y() == name.labelNode().absoluteBounds().bottom() + details.itemGap(), "Vertical label/value order");
        check(name.contentNode().size().width() == name.size().width(), "Vertical text uses full column width");

        details.labelColor(OreTheme.TEXT_DARK).contentColor(OreTheme.TEXT_SECONDARY).fontSize(12).titleFontSize(16);
        details.add("new", Component.literal("时间"), Component.literal("2026-10-17"));
        check(details.item("new").labelNode().fontSize() == 12 && name.contentNode().fontSize() == 12, "Shared fonts apply to current and future items");
        check(name.labelNode().color() == OreTheme.TEXT_DARK && details.item("new").contentNode().color() == OreTheme.TEXT_SECONDARY, "Ore color defaults are configurable");
        check(details.titleFontSize() == 16, "Independent title font size");
        name.content(Component.literal("更新后的内容"));
        check(details.layoutDirty() && details.item("name") == name, "Updating values invalidates layout without replacing stable nodes");

        details.item("leader").visible(false);
        layout(details, font, 200);
        check(!details.item("leader").visible() && details.items().size() == 5, "Hidden items remain owned but do not occupy a cell");
        details.item("leader").visible(true);
        layout(details, font, 200);
        check(details.item("leader").parent() == name.parent(), "Shown items return to their original order");
        check(details.removeChild(details.item("members")) && details.items().size() == 4, "Tree removal updates the keyed items");
        DescriptionItem extra = new DescriptionItem("tree", Component.literal("来源"), Component.literal("addChild"));
        details.addChild(extra);
        check(details.item("tree") == extra, "addChild accepts description items");
        rejects(() -> details.add("name", Component.empty(), Component.empty()));
        rejects(() -> details.addChild(new Control()));
        rejects(() -> new Descriptions().add(extra));
        rejects(() -> details.columns(0));
        rejects(() -> details.minimumColumnWidth(0));
        rejects(() -> details.fontSize(Float.NaN));
        rejects(() -> details.rowGap(-1));

        Box parent = new Box().add(details);
        layout(details, font, 300);
        name.content(Component.literal("Changed"));
        check(parent.layoutDirty(), "Value changes reach enclosing containers");
        details.clear().title(Component.empty());
        check(details.measureSize(font, new Size(100, 100)).height() == 0 && details.items().isEmpty(), "Empty descriptions take no height");
        check(name.parent() == null && extra.parent() == null, "Clear releases item ownership for reuse");
        check(details.sizeFlagsVertical() == SizeFlags.SIZE_SHRINK_BEGIN, "Default vertical size follows content");

        Descriptions overflow = new Descriptions().add("long", Component.literal("名称"), Component.literal("中文内容 😀 ".repeat(30)));
        Scroll viewport = new Scroll().content(overflow);
        viewport.layout(new Rect(0, 0, 80, 40), font);
        check(overflow.size().width() >= 96 && viewport.maxScrollX() > 0 && viewport.maxScrollY() > 0,
                "An external Scroll exposes content that cannot fit the minimum column width or height");
    }

    private static void layout(Descriptions descriptions, Font font, int width) {
        Size measured = descriptions.measureSize(font, new Size(width, 1000));
        descriptions.layout(new Rect(0, 0, width, measured.height()), font);
    }
    private static void rejects(Runnable action) {
        try { action.run(); throw new AssertionError("Invalid descriptions configuration accepted"); }
        catch (IllegalArgumentException | IllegalStateException expected) { }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
