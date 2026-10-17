package io.github.luyingyilong.geg.client.ui;

import io.github.luyingyilong.geg.client.ui.layout.*;
import io.github.luyingyilong.geg.client.ui.theme.OreSounds;
import io.github.luyingyilong.geg.client.ui.theme.OreTheme;
import io.github.luyingyilong.geg.client.ui.util.ToastQueue;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

public final class ToastLayoutTest {
    public static void main(String[] args) {
        Font font = new Font(id -> null, false) {
            @Override public int width(FormattedText text) { return text.getString().length() * 6; }
            @Override public List<FormattedCharSequence> split(FormattedText text, int width) {
                String value = text.getString(); List<FormattedCharSequence> lines = new ArrayList<>();
                int count = Math.max(1, width / 6);
                for (int from = 0; from < value.length(); from += count)
                    lines.add(Component.literal(value.substring(from, Math.min(value.length(), from + count))).getVisualOrderText());
                return lines.isEmpty() ? List.of(FormattedCharSequence.EMPTY) : lines;
            }
        };
        long[] now = {0}; List<OreSounds.Cue> sounds = new ArrayList<>();
        Toast toast = new Toast(() -> now[0], sounds::add).text(Component.literal("New mail")).duration(1000);
        check(new Toast().duration() == 3000 && toast.type() == Toast.Type.NEUTRAL && !toast.visible());
        ToastQueue queue = new ToastQueue(); queue.show("mail", toast);
        check(toast.progress() == 0 && queue.layout(font, 320, 240).get(0).area().bottom() < 0);
        toast.open(); check(sounds.equals(List.of(OreSounds.Cue.TOAST)));
        now[0] = 90; check(toast.progress() > 0 && toast.progress() < 1);
        now[0] = 180;
        Rect shown = queue.layout(font, 320, 240).get(0).area();
        check(shown.y() == 8 && shown.x() * 2 + shown.width() == 320);
        check(Toast.shadowBounds(shown).equals(shown.translated(1, 1)) && OreTheme.TOAST_SHADOW == 0x4D000000);
        now[0] = 1180; check(toast.progress() == 1 && !toast.finished());
        now[0] = 1260; check(toast.progress() > 0 && toast.progress() < 1);
        now[0] = 1340; check(toast.finished() && queue.size() == 0);

        now[0] = 2000;
        Toast persistent = new Toast(() -> now[0], sounds::add).text(Component.literal("Working")).duration(0).open();
        now[0] = 100000; check(!persistent.finished() && persistent.progress() == 1);
        persistent.close(); now[0] += 80; double progress = persistent.progress(); persistent.close();
        check(progress > 0 && progress < 1); now[0] += 80; check(persistent.finished());

        queue.clear(); now[0] = 200000; sounds.clear();
        Toast first = new Toast(() -> now[0], sounds::add).text(Component.literal("First"));
        queue.show("shared", first); now[0] += 1000;
        Toast updated = queue.show("shared", new Toast(() -> now[0], sounds::add).text(Component.literal("Updated"))
                .type(Toast.Type.INFORMATIVE).duration(5000));
        check(updated == first && queue.size() == 1 && sounds.size() == 1);
        check(first.text().getString().equals("Updated") && first.duration() == 5000 && first.progress() == 1);
        now[0] += 4000; check(!first.finished());
        for (int i = 0; i < 4; i++) queue.show("item:" + i, new Toast(() -> now[0], sounds::add)
                .text(Component.literal("邮件提示".repeat(30))).duration(0));
        check(queue.size() == 3);
        now[0] += Toast.ENTER_DURATION;
        for (int width : new int[]{127, 128, 129, 854}) for (int height : new int[]{40, 127, 240}) {
            var frames = queue.layout(font, width, height);
            check(!frames.isEmpty() && frames.size() <= 3);
            for (var frame : frames) {
                check(frame.area().x() >= 0 && frame.area().right() < width);
                check(frame.area().y() >= 0 && frame.area().bottom() < height);
                check(frame.toast().hitTop(frame.area().x() + 1, frame.area().y() + 1) == null);
            }
        }
        for (Toast.Type type : Toast.Type.values()) {
            first.type(type);
            check(type.color() == Tag.Type.valueOf(type.name()).color());
            check(((Label) first.children().get(0)).color() == Tag.Type.valueOf(type.name()).textColor());
        }
        queue.clear(); check(queue.size() == 0);
        try { first.duration(-1); throw new AssertionError("Negative duration accepted"); } catch (IllegalArgumentException expected) { }
    }
    private static void check(boolean value) { if (!value) throw new AssertionError("Toast lifecycle or geometry regression"); }
}
