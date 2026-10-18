package io.github.luyingyilong.geg.client.ui;

import io.github.luyingyilong.geg.client.ui.layout.*;
import io.github.luyingyilong.geg.client.ui.theme.OreIcons;
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
        check(badge.count() == null && badge.displayText().isEmpty());
        check(badge.measureSize(font, new Size(100, 30)).equals(new Size(3, 4)));
        check(!badge.acceptsFocus() && badge.hitTop(0, 0) == null);
        badge.count(0);
        check(badge.displayText().equals("0") && badge.measureSize(font, new Size(100, 30)).equals(new Size(12, 13)));
        badge.layout(new Rect(8, 10, 12, 13), font);
        check(badge.faceBounds(badge.absoluteBounds()).equals(new Rect(8, 10, 12, 12)));
        check(badge.shadowBounds(badge.absoluteBounds()).equals(new Rect(8, 22, 12, 1)));
        Label digits = (Label) badge.children().get(0);
        check(digits.bounds().equals(new Rect(4, 0, 6, 12)) && digits.opticalCenter());
        for (long value : new long[]{0L, 7L, 42L, 99L, 100L}) {
            badge.count(value);
            Size natural = badge.measureSize(font, new Size(100, 30));
            for (int extra : new int[]{0, 1, 6}) {
                badge.layout(new Rect(8, 10, natural.width() + extra, natural.height() + extra), font);
                Rect face = badge.faceBounds(badge.absoluteBounds());
                Rect text = digits.absoluteBounds();
                int visibleWidth = font.width(digits.styledText()) - 1;
                // Pixel rounding may differ by half a pixel, never by a full pixel.
                check(Math.abs(2 * text.x() + visibleWidth - (2 * face.x() + face.width())) <= 1);
                float drawTop = text.y() + digits.firstLineOrigin(text.height(), 1);
                check(Math.abs(2 * drawTop + 8 - (2 * face.y() + face.height())) <= 1);
                check(drawTop + 8 <= badge.shadowBounds(badge.absoluteBounds()).y());
            }
        }
        badge.count(100);
        check(badge.displayText().equals("99+") && badge.measureWidth(font, 100) == 22);
        badge.overflowCount(999);
        check(badge.displayText().equals("100"));
        badge.overflowCount(Long.MAX_VALUE).count(Long.MAX_VALUE);
        check(badge.displayText().equals(Long.toString(Long.MAX_VALUE)) && badge.measureWidth(font, 10) == 118);
        for (Tag.Type type : Tag.Type.values()) {
            badge.type(type);
            check(badge.type().color() == type.color() && digits.color() == type.textColor());
        }

        int[] actions = {0};
        Button button = new Button(cue -> { }).label(Component.literal("Mail")).badge(badge.count(7))
                .onPressed(() -> actions[0]++);
        int withBadge = button.measureWidth(font, 200);
        badge.visible(false);
        int withoutBadge = button.measureWidth(font, 200);
        check(withBadge == withoutBadge + 16);
        badge.visible(true);
        button.position(5, 5).requestedSize(withBadge, Button.HEIGHT);
        Window window = new Window(true).content(new Control().addChild(button));
        window.layoutViewport(200, 80, font);
        check(badge.bounds().right() == button.size().width() - Button.PADDING_X);
        Rect normal = badge.bounds();
        check(Math.abs(normal.y() * 2 + normal.height() - button.size().height()) <= 1);
        Rect click = badge.absoluteBounds();
        check(window.pointerPressed(click.x() + 1, click.y() + 1, null) && actions[0] == 0);
        check(window.pointerReleased(click.x() + 1, click.y() + 1) && actions[0] == 1);
        check(window.focusManager().focused() == button);
        window.pointerPressed(click.x() + 1, click.y() + 1, null);
        window.pointerReleased(190, 70);
        check(actions[0] == 1);

        button.toggleButton(true).buttonPressedNoSignal(true);
        window.layoutViewport(200, 80, font);
        check(badge.bounds().equals(normal.translated(0, 2)));
        button.type(Button.Type.TEXT);
        window.layoutViewport(200, 80, font);
        check(badge.bounds().equals(normal));
        badge.count(1000);
        check(window.layoutDirty() && button.measureWidth(font, 200) > withBadge);
        badge.offset(2, -1);
        window.layoutViewport(200, 80, font);
        check(badge.bounds().equals(badge.layoutBounds().translated(2, -1)));
        button.trailingIcon(new Button.Icon(OreIcons.ARROW_DOWN, 8, 8));
        check(button.measureWidth(font, 200) >= withoutBadge + badge.measureWidth(font, 200) + 16);
        Badge other = new Badge();
        button.badge(other);
        check(badge.parent() == null && other.parent() == button);
        button.removeChild(other);
        check(button.badge() == null && other.parent() == null);
        button.badge(badge).badge(null);
        check(button.measureWidth(font, 200) == withoutBadge + 12);
        badge.count(null);
        check(badge.measureSize(font, new Size(100, 30)).equals(new Size(3, 4)));
        rejects(() -> badge.count(-1));
        rejects(() -> badge.overflowCount(0));
    }

    private static void rejects(Runnable action) {
        try { action.run(); throw new AssertionError("Invalid badge count accepted"); }
        catch (IllegalArgumentException expected) { }
    }
    private static void check(boolean condition) { if (!condition) throw new AssertionError("Badge layout or interaction regression"); }
}
