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
        check(tag.getType() == Tag.Type.NEUTRAL && !tag.getOutlined() && !tag.acceptsFocus());
        Label label = (Label) tag.getChildren().get(0);
        check(tag.getText().getString().equals("状态"));
        Size natural = tag.measureSize(font, new Size(120, 40));
        check(natural.equals(new Size(20, 14)));
        Box parent = new Box().setOrientation(Box.Orientation.HORIZONTAL);
        parent.addChild(tag);
        parent.layout(new Rect(0, 0, 120, 40), font);
        check(tag.getSize().equals(natural) && tag.getBounds().y() == 13);
        tag.setOutlined(true);
        check(parent.getLayoutDirty());
        Size outlined = tag.measureSize(font, new Size(120, 40));
        check(outlined.equals(new Size(22, 16)));
        parent.layout(new Rect(0, 0, 120, 40), font);
        check(label.getBounds().equals(new Rect(5, 3, 12, 10)));
        tag.setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).setSizeFlagsVertical(SizeFlags.SIZE_FILL);
        parent.layout(new Rect(0, 0, 120, 40), font);
        check(tag.getSize().equals(new Size(120, 40)));
        check(label.getBounds().x() * 2 + label.getSize().width() == tag.getSize().width());
        check(label.getBounds().y() * 2 + label.getSize().height() == tag.getSize().height());
        tag.setText(Component.translatable("ore.test.title"));
        check(parent.getLayoutDirty() && tag.getText().equals(Component.translatable("ore.test.title")));
        int[] colors = {0xFF6CC349, 0xFF1E1E1F, 0xFF313233, 0xFF8CB3FF, 0xFFFFE866, 0xFFF46D6D, 0xFFAC90F3};
        for (Tag.Type type : Tag.Type.values()) {
            tag.setType(type);
            check(tag.getType() == type && type.getColor() == colors[type.ordinal()]);
            check(label.getThemeItem(Label.FONT_COLOR) == (type == Tag.Type.NEUTRAL || type == Tag.Type.SECONDARY
                    ? OreTheme.TEXT_WHITE : OreTheme.TEXT_DARK));
        }
        check(OreTheme.TAG_OUTLINE == 0xFF1E1E1F);
        try { tag.setType(null); throw new AssertionError("Null type accepted"); } catch (NullPointerException expected) { }
        try { tag.setText(null); throw new AssertionError("Null text accepted"); } catch (NullPointerException expected) { }
    }

    private static void check(boolean condition) { if (!condition) throw new AssertionError("Tag layout or appearance regression"); }
}
