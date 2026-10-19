package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.theme.OreSounds;
import io.github.luyingyilong.ore.theme.OreTheme;
import io.github.luyingyilong.ore.theme.OreTextMetrics;
import io.github.luyingyilong.ore.util.ToastQueue;
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
            @Override public int width(FormattedCharSequence text) {
                int[] count = {0};
                text.accept((index, style, codepoint) -> { count[0]++; return true; });
                return count[0] * 6;
            }
            @Override public List<FormattedCharSequence> split(FormattedText text, int width) {
                String value = text.getString(); List<FormattedCharSequence> lines = new ArrayList<>();
                int count = Math.max(1, width / 6);
                for (int from = 0; from < value.length(); from += count)
                    lines.add(Component.literal(value.substring(from, Math.min(value.length(), from + count))).getVisualOrderText());
                return lines.isEmpty() ? List.of(FormattedCharSequence.EMPTY) : lines;
            }
        };
        long[] now = {0}; List<OreSounds.Cue> sounds = new ArrayList<>();
        Toast toast = new Toast(() -> now[0], sounds::add).setText(Component.literal("New mail")).setDuration(1000);
        check(new Toast().getDuration() == 3000 && toast.getType() == Toast.Type.NEUTRAL && !toast.getVisible());
        ToastQueue queue = new ToastQueue(); queue.show("mail", toast);
        check(toast.getProgress() == 0 && queue.layout(font, 320, 240).get(0).area().bottom() < 0);
        toast.open(); check(sounds.equals(List.of(OreSounds.Cue.TOAST)));
        now[0] = 90; check(toast.getProgress() > 0 && toast.getProgress() < 1);
        now[0] = 180;
        Rect shown = queue.layout(font, 320, 240).get(0).area();
        check(shown.y() == 8 && shown.x() * 2 + shown.width() == 320);
        check(Toast.shadowBounds(shown).equals(shown.translated(1, 1)) && OreTheme.TOAST_SHADOW == 0x4D000000);
        now[0] = 1180; check(toast.getProgress() == 1 && !toast.getFinished());
        now[0] = 1260; check(toast.getProgress() > 0 && toast.getProgress() < 1);
        now[0] = 1340; check(toast.getFinished() && queue.size() == 0);

        now[0] = 2000;
        Toast persistent = new Toast(() -> now[0], sounds::add).setText(Component.literal("Working")).setDuration(0).open();
        now[0] = 100000; check(!persistent.getFinished() && persistent.getProgress() == 1);
        persistent.close(); now[0] += 80; double progress = persistent.getProgress(); persistent.close();
        check(progress > 0 && progress < 1); now[0] += 80; check(persistent.getFinished());

        queue.clear(); now[0] = 200000; sounds.clear();
        Toast first = new Toast(() -> now[0], sounds::add).setText(Component.literal("First"));
        queue.show("shared", first); now[0] += 1000;
        Toast updated = queue.show("shared", new Toast(() -> now[0], sounds::add).setText(Component.literal("Updated"))
                .setType(Toast.Type.INFORMATIVE).setDuration(5000));
        check(updated == first && queue.size() == 1 && sounds.size() == 1);
        check(first.getText().getString().equals("Updated") && first.getDuration() == 5000 && first.getProgress() == 1);
        now[0] += 4000; check(!first.getFinished());
        for (int i = 0; i < 4; i++) queue.show("item:" + i, new Toast(() -> now[0], sounds::add)
                .setText(Component.literal("邮件提示".repeat(30))).setDuration(0));
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
            first.setType(type);
            check(type.getColor() == Tag.Type.valueOf(type.name()).getColor());
            check(((Label) first.getChildren().get(0)).getColor() == Tag.Type.valueOf(type.name()).textColor());
        }
        textCentering(font);
        queue.clear(); check(queue.size() == 0);
        try { first.setDuration(-1); throw new AssertionError("Negative duration accepted"); } catch (IllegalArgumentException expected) { }
    }
    private static void textCentering(Font font) {
        Toast toast = new Toast(() -> 0L, cue -> { }).open();
        Label label = (Label) toast.getChildren().get(0);
        for (String text : new String[]{"Success", "领取成功", "邮件附件领取成功，请检查背包"}) {
            toast.setText(Component.literal(text));
            for (int width : new int[]{64, 127, 128, 129, 320}) {
                Size size = toast.measureSize(font, new Size(width, 200));
                for (int extraHeight : new int[]{0, 1, 12}) {
                    Rect face = new Rect(10, 20, size.width(), size.height() + extraHeight);
                    toast.layout(face, font);
                    Rect textArea = toast.textBounds(face);
                    check(label.getAbsoluteBounds().equals(textArea));
                    int lines = label.displayedLines(font, textArea.width(), textArea.height()).size();
                    float startY = textArea.y() + label.firstLineOrigin(textArea.height(), lines);
                    float visibleHeight = OreTextMetrics.body().opticalHeight(lines, 1);
                    check(label.getOpticalCenter());
                    check(Math.abs(2 * startY + visibleHeight - (2 * face.y() + face.height())) <= 1);
                    check(startY >= face.y() + 1 && startY + visibleHeight <= face.bottom() - 1);
                    check(textArea.bottom() == face.bottom() - 2);
                }
            }
        }
    }
    private static void check(boolean value) { if (!value) throw new AssertionError("Toast lifecycle or geometry regression"); }
}
