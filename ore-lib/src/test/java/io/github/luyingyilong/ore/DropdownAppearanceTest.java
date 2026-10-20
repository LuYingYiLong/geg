package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.render.*;

import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.theme.OreTheme;
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
        check(dropdown.getIcon() == null && dropdown.getShowArrow() && dropdown.getTriggerButton().getThemeTypeVariation().isEmpty());
        dropdown.setOptions(List.of(new Dropdown.Option<>("all", Component.literal("All mods")),
                new Dropdown.Option<>("minecraft", Component.literal("minecraft"))));
        dropdown.setTriggerLabel(Component.empty()).setIcon(TextureSource.themed(io.github.luyingyilong.ore.theme.IconTheme.FILTER))
                .setShowArrow(false);
        dropdown.getTriggerButton().setThemeTypeVariation("TextButton");
        Button reference = new Button(Component.empty()).setIcon(TextureSource.themed(io.github.luyingyilong.ore.theme.IconTheme.FILTER)).setThemeTypeVariation("TextButton");
        int width = dropdown.measureWidth(font, 300);
        int height = dropdown.measureHeight(font, 300);
        check(width == 24 && height == 24);
        check(width == reference.measureWidth(font, 300) && height == reference.measureHeight(font, 300));
        check(dropdown.getIcon().equals(reference.getIcon()) && !dropdown.getShowArrow() && dropdown.getTriggerButton().getThemeTypeVariation().equals("TextButton"));
        dropdown.layout(new Rect(200, 5, width, height), font);
        check(EventFixture.press(dropdown,210,15) && !dropdown.isOpen());
        check(EventFixture.release(dropdown,210,15,true) && dropdown.isOpen());

        int menuWidth = new Label(Component.literal("minecraft")).measureWidth(font, 300) + 22;
        int optionX = 200 + width - menuWidth + 4;
        check(optionX < 200); // This is outside the icon button but inside the expanded menu.
        int optionY = 5 + height + 1 + Button.Size.MEDIUM.getHeight() - 1 + 3;
        check(EventFixture.press(dropdown,optionX,optionY));
        check(EventFixture.release(dropdown,optionX,optionY,true));
        check(dropdown.getSelected().equals("minecraft") && !dropdown.isOpen());
        dropdown.setShowArrow(true);
        check(dropdown.measureWidth(font, 300) > width);
        dropdown.setShowArrow(false).setIcon((TextureSource) null).setTriggerLabel(Component.literal("Filter"));
        check(dropdown.measureWidth(font, 300) > 24 && dropdown.getIcon() == null);
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Dropdown appearance or menu geometry regression");
    }
}
