package io.github.luyingyilong.ore.control.base;

import io.github.luyingyilong.ore.control.data.PlayerIdentityCell;
import io.github.luyingyilong.ore.control.data.Table;
import io.github.luyingyilong.ore.control.display.Label;

import io.github.luyingyilong.ore.window.Window;
import io.github.luyingyilong.ore.signal.SignalDiagnostics;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Repeated measurements/layout must not allocate signal-bearing rendering controls. */
public final class PainterReuseTest {
    public static void main(String[] args) {
        Font font = font();
        Component text = Component.literal("中文 😀 styled words\nSecond line").withStyle(style -> style.withColor(0xAABBCC));
        Label reference = new Label(text);
        for (Label.Wrap wrap : Label.Wrap.values()) for (Label.Overflow overflow : Label.Overflow.values())
            for (int width : new int[]{1, 12, 127, 128, 129}) for (int maxLines : new int[]{1, 2, Integer.MAX_VALUE}) {
                reference.setWrap(wrap).setOverflow(overflow).setMaxLines(maxLines);
                check(reference.measureHeight(font, width) == Label.measureTextHeight(reference, font, text, width, wrap, overflow, maxLines),
                        "Allocation-free measurements match Label's styled wrapping and precision");
            }

        Table.TextCell<Integer> cell = new Table.TextCell<Integer>(value -> text).setWrap(Label.Wrap.WORD).setMaxLines(3);
        PlayerIdentityCell<Integer> identity = new PlayerIdentityCell<>(value -> new UUID(0, value), value -> "Player " + value);
        Table<Integer> table = new Table<Integer>().setRowKey(value -> value)
                .addColumn(new Table.Column<>("body", Component.literal("Text"), 20, 160, 1, width -> 32, cell));
        table.setRows(List.of(1, 2, 3, 4, 5));
        table.layout(new Rect(0, 0, 240, 110), font);
        List<Label> rowLabels = labels(table);
        check(!rowLabels.isEmpty(), "Text cells have retained row controls");
        cell.measureHeight(table, font, 1, 100);
        identity.measureHeight(table, font, 1, 100);
        Control marker0 = new Control(), marker1 = new Control();
        long ordinaryAllocation = SignalDiagnostics.ownerId(marker1) - SignalDiagnostics.ownerId(marker0);
        for (int i = 0; i < 1000; i++) {
            Label.measureTextHeight(reference, font, text, 127 + i % 3, Label.Wrap.WORD, Label.Overflow.ELLIPSIS, 2);
            cell.measureHeight(table, font, i, 127);
            identity.measureHeight(table, font, i, 127);
            table.layout(new Rect(0, 0, 240, 110), font);
        }
        Control marker2 = new Control();
        check(SignalDiagnostics.ownerId(marker2) - SignalDiagnostics.ownerId(marker1) == ordinaryAllocation,
                "Repeated text measurement and Table layout allocate no Controls or signals");
        check(labels(table).equals(rowLabels), "Table row labels survive ordinary refreshes");
        table.queueFree();
        io.github.luyingyilong.ore.control.base.LifecycleScheduler.flush();
        check(rowLabels.stream().allMatch(Control::isDisposed), "Table disposes retained text cells");

        Window first = Window.embedded(), second = Window.embedded();
        Label firstPainter = first.ownPart("caption", new Label());
        Label secondPainter = second.ownPart("caption", new Label());
        check(firstPainter == first.getOwnedParts().get("caption") && firstPainter != secondPainter, "Retained Labels are isolated per Window");
        first.queueFree();
        io.github.luyingyilong.ore.control.base.LifecycleScheduler.flush();
        check(firstPainter.isDisposed() && secondPainter.isAlive(), "Retained Label ends only with its owning Window");
        second.queueFree(); reference.queueFree(); marker0.queueFree(); marker1.queueFree(); marker2.queueFree();
        io.github.luyingyilong.ore.control.base.LifecycleScheduler.flush();
        System.out.println("ORE_PAINTER_REUSE_PASS");
    }

    private static List<Label> labels(Control root) {
        List<Label> labels = new ArrayList<>();
        if (root instanceof Label label) labels.add(label);
        for (Control child : root.getChildren()) labels.addAll(labels(child));
        return labels;
    }

    private static Font font() {
        return new Font(id -> null, false) {
            private final StringSplitter splitter = new StringSplitter((codepoint, style) -> 6.0F);
            @Override public int width(FormattedText text) { return text.getString().codePointCount(0, text.getString().length()) * 6; }
            @Override public int width(FormattedCharSequence text) {
                int[] width = {0}; text.accept((i, style, codepoint) -> { width[0] += 6; return true; }); return width[0];
            }
            @Override public StringSplitter getSplitter() { return splitter; }
            @Override public List<FormattedCharSequence> split(FormattedText text, int width) {
                return splitter.splitLines(text, width, Style.EMPTY).stream().map(line -> Language.getInstance().getVisualOrder(line)).toList();
            }
        };
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
