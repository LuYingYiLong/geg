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
        Descriptions details = new Descriptions().setTitle(Component.literal("组织信息"))
                .add("name", Component.literal("名称"), Component.literal("橡木公社"))
                .add("leader", Component.literal("领袖"), Component.literal("Player"))
                .add("members", Component.literal("成员"), Component.literal("8"))
                .add("description", Component.literal("介绍"), Component.literal("中英文长段落 Long description 😀").withStyle(ChatFormatting.GOLD));
        check(details.getChildren().size() == 1 && details.getChildren().get(0) instanceof Box, "Descriptions is a retained Box tree");
        check(details.getColumns() == 3 && details.getLayout() == Descriptions.Layout.HORIZONTAL && details.getColon(), "Lightweight defaults");
        for (int width : new int[]{80, 96, 127, 128, 129, 199, 200, 303, 304, 500}) {
            Size measured = details.measureSize(font, new Size(width, 1000));
            details.layout(new Rect(11, 17, width, measured.height()), font);
            int expected = width < 200 ? 1 : width < 304 ? 2 : 3;
            check(details.effectiveColumns(width) == expected, "Column count responds to cell minima and gaps at width " + width);
            check(measured.width() == Math.max(96, width), "Keep a column's minimum even below the viewport width");
            int cellWidth = (measured.width() - (expected - 1) * details.getColumnGap()) / expected;
            for (int index = 0; index < details.getItems().size(); index++) {
                DescriptionItem item = details.getItems().get(index);
                check(item.getSize().width() >= cellWidth && item.getSize().width() <= cellWidth + 1, "Equal column widths with stable pixel remainder");
                check(item.getAbsoluteBounds().y() >= 17 + details.getTitleNode().lineHeight(font) + details.getRowGap(), "Title remains above rows");
                Label label = item.getLabelNode(), content = item.getContentNode();
                check(content.getAbsoluteBounds().x() >= label.getAbsoluteBounds().right() + details.getItemGap(), "Horizontal label/value gap");
                check(content.getAbsoluteBounds().bottom() <= item.getAbsoluteBounds().bottom(), "Wrapped value fits the measured row");
                check(label.getAbsoluteBounds().bottom() <= item.getAbsoluteBounds().bottom(), "Wrapped caption fits the measured row");
                check(item.getParent() instanceof Box && item.getParent().getParent() == details.getChildren().get(0), "Rows own their items inside the retained tree");
            }
            for (int index = 1; index < expected; index++) {
                check(details.getItems().get(index).getAbsoluteBounds().y() == details.getItem("name").getAbsoluteBounds().y(), "Row items align at their top");
            }
            details.layout(new Rect(11, 17, width, measured.height()), font);
            check(!details.getLayoutDirty(), "Stable layout does not continually invalidate itself");
        }

        DescriptionItem name = details.getItem("name");
        check(name.getLabelNode().getText().getString().equals("名称:"), "Optional colon is rendered through Label");
        details.setColon(false).setLayout(Descriptions.Layout.VERTICAL);
        Size vertical = details.measureSize(font, new Size(304, 1000));
        details.layout(new Rect(0, 0, 304, vertical.height()), font);
        check(name.getLabelNode().getText().getString().equals("名称"), "Disabling the colon preserves original text");
        check(name.getContentNode().getAbsoluteBounds().y() == name.getLabelNode().getAbsoluteBounds().bottom() + details.getItemGap(), "Vertical label/value order");
        check(name.getContentNode().getSize().width() == name.getSize().width(), "Vertical text uses full column width");

        details.setLabelColor(OreTheme.TEXT_DARK).setContentColor(OreTheme.TEXT_SECONDARY).setFontSize(12).setTitleFontSize(16);
        details.add("new", Component.literal("时间"), Component.literal("2026-10-17"));
        check(details.getItem("new").getLabelNode().getFontSize() == 12 && name.getContentNode().getFontSize() == 12, "Shared fonts apply to current and future items");
        check(name.getLabelNode().getColor() == OreTheme.TEXT_DARK && details.getItem("new").getContentNode().getColor() == OreTheme.TEXT_SECONDARY, "Ore color defaults are configurable");
        check(details.getTitleFontSize() == 16, "Independent title font size");
        name.setContent(Component.literal("更新后的内容"));
        check(details.getLayoutDirty() && details.getItem("name") == name, "Updating values invalidates layout without replacing stable nodes");

        details.getItem("leader").setVisible(false);
        layout(details, font, 200);
        check(!details.getItem("leader").getVisible() && details.getItems().size() == 5, "Hidden items remain owned but do not occupy a cell");
        details.getItem("leader").setVisible(true);
        layout(details, font, 200);
        check(details.getItem("leader").getParent() == name.getParent(), "Shown items return to their original order");
        check(details.removeChild(details.getItem("members")) && details.getItems().size() == 4, "Tree removal updates the keyed items");
        DescriptionItem extra = new DescriptionItem("tree", Component.literal("来源"), Component.literal("addChild"));
        details.addChild(extra);
        check(details.getItem("tree") == extra, "addChild accepts description items");
        rejects(() -> details.add("name", Component.empty(), Component.empty()));
        rejects(() -> details.addChild(new Control()));
        rejects(() -> new Descriptions().add(extra));
        rejects(() -> details.setColumns(0));
        rejects(() -> details.setMinimumColumnWidth(0));
        rejects(() -> details.setFontSize(Float.NaN));
        rejects(() -> details.setRowGap(-1));

        Box parent = new Box().add(details);
        layout(details, font, 300);
        name.setContent(Component.literal("Changed"));
        check(parent.getLayoutDirty(), "Value changes reach enclosing containers");
        details.clear().setTitle(Component.empty());
        check(details.measureSize(font, new Size(100, 100)).height() == 0 && details.getItems().isEmpty(), "Empty descriptions take no height");
        check(name.getParent() == null && extra.getParent() == null, "Clear releases item ownership for reuse");
        check(details.getSizeFlagsVertical() == SizeFlags.SIZE_SHRINK_BEGIN, "Default vertical size follows content");

        Descriptions overflow = new Descriptions().add("long", Component.literal("名称"), Component.literal("中文内容 😀 ".repeat(30)));
        Scroll viewport = new Scroll().setContent(overflow);
        viewport.layout(new Rect(0, 0, 80, 40), font);
        check(overflow.getSize().width() >= 96 && viewport.getMaxScrollX() > 0 && viewport.getMaxScrollY() > 0,
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
