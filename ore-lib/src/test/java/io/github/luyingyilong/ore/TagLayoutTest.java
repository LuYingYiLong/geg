package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.theme.OreTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

/** Tag measurement, color, sizing flags and parent layout invalidation. */
public final class TagLayoutTest {
    public static void main(String[] args) {
        Font font = new Font(id -> null, false) {
            @Override public int width(FormattedText text) { return text.getString().codePointCount(0, text.getString().length()) * 6; }
        };
        Tag tag = new Tag(Component.literal("状态"));
        check(tag.type() == Tag.Type.NEUTRAL && !tag.outlined() && !tag.acceptsFocus());
        Label label = (Label) tag.children().get(0);
        check(tag.text().getString().equals("状态"));
        Size natural = tag.measureSize(font, new Size(120, 40));
        check(natural.equals(new Size(20, 14)));
        Box parent = new Box().orientation(Box.Orientation.HORIZONTAL).add(tag);
        parent.layout(new Rect(0, 0, 120, 40), font);
        check(tag.size().equals(natural) && tag.bounds().y() == 13);
        tag.outlined(true);
        check(parent.layoutDirty());
        Size outlined = tag.measureSize(font, new Size(120, 40));
        check(outlined.equals(new Size(22, 16)));
        parent.layout(new Rect(0, 0, 120, 40), font);
        check(label.bounds().equals(new Rect(5, 3, 12, 10)));
        tag.sizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).sizeFlagsVertical(SizeFlags.SIZE_FILL);
        parent.layout(new Rect(0, 0, 120, 40), font);
        check(tag.size().equals(new Size(120, 40)));
        check(label.bounds().x() * 2 + label.size().width() == tag.size().width());
        check(label.bounds().y() * 2 + label.size().height() == tag.size().height());
        tag.text(Component.translatable("ore.test.title"));
        check(parent.layoutDirty() && tag.text().equals(Component.translatable("ore.test.title")));
        int[] colors = {0xFF6CC349, 0xFF1E1E1F, 0xFF313233, 0xFF8CB3FF, 0xFFFFE866, 0xFFF46D6D, 0xFFAC90F3};
        for (Tag.Type type : Tag.Type.values()) {
            tag.type(type);
            check(tag.type() == type && type.color() == colors[type.ordinal()]);
            check(label.color() == (type == Tag.Type.NEUTRAL || type == Tag.Type.SECONDARY
                    ? OreTheme.TEXT_WHITE : OreTheme.TEXT_DARK));
        }
        check(OreTheme.TAG_OUTLINE == 0xFF1E1E1F);
        try { tag.type(null); throw new AssertionError("Null type accepted"); } catch (NullPointerException expected) { }
        try { tag.text(null); throw new AssertionError("Null text accepted"); } catch (NullPointerException expected) { }
    }

    private static void check(boolean condition) { if (!condition) throw new AssertionError("Tag layout or appearance regression"); }
}
