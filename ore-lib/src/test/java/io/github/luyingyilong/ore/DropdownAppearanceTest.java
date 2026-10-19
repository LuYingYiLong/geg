package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.theme.OreIcons;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

import java.util.List;

/** Icon-only triggers keep Button metrics and menu hit geometry independent of their narrow width. */
public final class DropdownAppearanceTest {
    public static void main(String[] args) {
        Font font = new Font(id -> null, false) {
            @Override public int width(FormattedText text) { return text.getString().length() * 6; }
        };
        Dropdown<String> dropdown = new Dropdown<>("all", 8);
        check(dropdown.icon() == null && dropdown.showArrow() && dropdown.type() == Button.Type.SECONDARY);
        dropdown.setOptions(List.of(new Dropdown.Option<>("all", Component.literal("All mods")),
                new Dropdown.Option<>("minecraft", Component.literal("minecraft"))));
        dropdown.triggerLabel(Component.empty()).icon(OreIcons.FILTER, 16, 16)
                .showArrow(false).type(Button.Type.TEXT);
        Button reference = new Button(Component.empty()).icon(OreIcons.FILTER, 16, 16).type(Button.Type.TEXT);
        int width = dropdown.measureWidth(font, 300);
        int height = dropdown.measureHeight(font, 300);
        check(width == 24 && height == 24);
        check(width == reference.measureWidth(font, 300) && height == reference.measureHeight(font, 300));
        check(dropdown.icon().equals(reference.icon()) && !dropdown.showArrow() && dropdown.type() == Button.Type.TEXT);
        dropdown.layout(new Rect(200, 5, width, height), font);
        check(EventFixture.press(dropdown,210,15) && !dropdown.isOpen());
        check(EventFixture.release(dropdown,210,15,true) && dropdown.isOpen());

        int menuWidth = new Label(Component.literal("minecraft")).measureWidth(font, 300) + 22;
        int optionX = 200 + width - menuWidth + 4;
        check(optionX < 200); // This is outside the icon button but inside the expanded menu.
        int optionY = 5 + height + 1 + Button.Size.MEDIUM.height() - 1 + 3;
        check(EventFixture.press(dropdown,optionX,optionY));
        check(EventFixture.release(dropdown,optionX,optionY,true));
        check(dropdown.selected().equals("minecraft") && !dropdown.isOpen());
        dropdown.showArrow(true);
        check(dropdown.measureWidth(font, 300) > width);
        dropdown.showArrow(false).icon((Button.Icon) null).triggerLabel(Component.literal("Filter"));
        check(dropdown.measureWidth(font, 300) > 24 && dropdown.icon() == null);
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Dropdown appearance or menu geometry regression");
    }
}
