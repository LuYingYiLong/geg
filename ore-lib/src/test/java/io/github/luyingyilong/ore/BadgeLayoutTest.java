package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.render.*;

import io.github.luyingyilong.ore.signal.Signals;

import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.theme.OreTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

/** Production Badge/Button layout, including dynamic counts, shadow footprint and pointer ownership. */
public final class BadgeLayoutTest {
    public static void main(String[] args) {
        Font font = new Font(id -> null, false) {
            @Override public int width(FormattedText text) { return text.getString().length() * 6; }
        };
        Badge badge = new Badge();
        check(badge.getCount() == null && badge.getDisplayText().isEmpty());
        check(badge.measureSize(font, new Size(100, 30)).equals(new Size(3, 4)));
        check(!badge.acceptsFocus() && badge.hitTop(0, 0) == null);
        badge.setCount(0);
        check(badge.getDisplayText().equals("0") && badge.measureSize(font, new Size(100, 30)).equals(new Size(12, 13)));
        badge.layout(new Rect(8, 10, 12, 13), font);
        check(badge.faceBounds(badge.getAbsoluteBounds()).equals(new Rect(8, 10, 12, 12)));
        check(badge.shadowBounds(badge.getAbsoluteBounds()).equals(new Rect(8, 22, 12, 1)));
        Label digits = (Label) badge.getChildren().get(0);
        check(digits.getBounds().equals(new Rect(4, 0, 6, 12)) && digits.getOpticalCenter());
        for (long value : new long[]{0L, 7L, 42L, 99L, 100L}) {
            badge.setCount(value);
            Size natural = badge.measureSize(font, new Size(100, 30));
            for (int extra : new int[]{0, 1, 6}) {
                badge.layout(new Rect(8, 10, natural.width() + extra, natural.height() + extra), font);
                Rect face = badge.faceBounds(badge.getAbsoluteBounds());
                Rect text = digits.getAbsoluteBounds();
                int visibleWidth = font.width(digits.getStyledText()) - 1;
                // Pixel rounding may differ by half a pixel, never by a full pixel.
                check(Math.abs(2 * text.x() + visibleWidth - (2 * face.x() + face.width())) <= 1);
                float drawTop = text.y() + digits.firstLineOrigin(text.height(), 1);
                check(Math.abs(2 * drawTop + 8 - (2 * face.y() + face.height())) <= 1);
                check(drawTop + 8 <= badge.shadowBounds(badge.getAbsoluteBounds()).y());
            }
        }
        badge.setCount(100);
        check(badge.getDisplayText().equals("99+") && badge.measureWidth(font, 100) == 22);
        badge.setOverflowCount(999);
        check(badge.getDisplayText().equals("100"));
        badge.setOverflowCount(Long.MAX_VALUE).setCount(Long.MAX_VALUE);
        check(badge.getDisplayText().equals(Long.toString(Long.MAX_VALUE)) && badge.measureWidth(font, 10) == 118);
        for (Tag.Type type : Tag.Type.values()) {
            badge.setType(type);
            check(badge.getType().getColor() == type.getColor() && digits.getColor() == type.textColor());
        }

        int[] actions = {0};
        Button button = Signals.configure(new Button(cue -> { }).setLabel(Component.literal("Mail")).setBadge(badge.setCount(7)), connected -> connected.pressed().connect(connected, () -> actions[0]++));
        int withBadge = button.measureWidth(font, 200);
        badge.setVisible(false);
        int withoutBadge = button.measureWidth(font, 200);
        check(withBadge == withoutBadge + 16);
        badge.setVisible(true);
        button.setPosition(5, 5).setRequestedSize(withBadge, Button.HEIGHT);
        var childContainer6 = new Control();
        childContainer6.addChild(button);
        Window window = new Window(true).setContent(childContainer6);
        window.layoutViewport(200, 80, font);
        check(badge.getBounds().right() == button.getSize().width() - Button.PADDING_X);
        Rect normal = badge.getBounds();
        check(Math.abs(normal.y() * 2 + normal.height() - button.getSize().height()) <= 1);
        Rect click = badge.getAbsoluteBounds();
        check(io.github.luyingyilong.ore.event.WindowInputs.press(window,click.x() + 1, click.y() + 1, null) && actions[0] == 0);
        check(io.github.luyingyilong.ore.event.WindowInputs.release(window,click.x() + 1, click.y() + 1) && actions[0] == 1);
        check(window.getFocusManager().getFocused() == button);
        io.github.luyingyilong.ore.event.WindowInputs.press(window,click.x() + 1, click.y() + 1, null);
        io.github.luyingyilong.ore.event.WindowInputs.release(window,190, 70);
        check(actions[0] == 1);

        button.setToggleButton(true).setButtonPressedNoSignal(true);
        window.layoutViewport(200, 80, font);
        check(badge.getBounds().equals(normal.translated(0, 2)));
        button.setType(Button.Type.TEXT);
        window.layoutViewport(200, 80, font);
        check(badge.getBounds().equals(normal));
        badge.setCount(1000);
        check(window.getLayoutDirty() && button.measureWidth(font, 200) > withBadge);
        badge.setOffset(2, -1);
        window.layoutViewport(200, 80, font);
        check(badge.getBounds().equals(badge.getLayoutBounds().translated(2, -1)));
        button.setTrailingIcon(TextureSource.region(TextureRegion.full(io.github.luyingyilong.ore.theme.IconTheme.ARROW_DOWN.defaultValue().getTexture(), 8, 8)));
        check(button.measureWidth(font, 200) >= withoutBadge + badge.measureWidth(font, 200) + 16);
        Badge other = new Badge();
        button.setBadge(other);
        check(badge.getParent() == null && other.getParent() == button);
        button.removeChild(other);
        check(button.getBadge() == null && other.getParent() == null);
        button.setBadge(badge).setBadge(null);
        check(button.measureWidth(font, 200) == withoutBadge + 12);
        badge.setCount(null);
        check(badge.measureSize(font, new Size(100, 30)).equals(new Size(3, 4)));
        rejects(() -> badge.setCount(-1));
        rejects(() -> badge.setOverflowCount(0));
    }

    private static void rejects(Runnable action) {
        try { action.run(); throw new AssertionError("Invalid badge count accepted"); }
        catch (IllegalArgumentException expected) { }
    }
    private static void check(boolean condition) { if (!condition) throw new AssertionError("Badge layout or interaction regression"); }
}
