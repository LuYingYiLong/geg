package io.github.luyingyilong.ore.text;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** A value snapshot, never a retained MutableComponent or Control. */
public record StyledText(String text, List<Span> spans) {
    public record Span(int start, int end, Style style) { }
    public StyledText { spans = List.copyOf(spans); }
    public static StyledText of(Component component) { return of(component, Style.DEFAULT_FONT); }
    public static StyledText of(Component component, net.minecraft.resources.ResourceLocation fallback) {
        StringBuilder text = new StringBuilder();
        List<Span> spans = new ArrayList<>();
        component.visit((style, value) -> {
            int start = text.length(); text.append(value);
            if (!value.isEmpty()) spans.add(new Span(start, text.length(), style));
            return Optional.empty();
        }, Style.EMPTY.withFont(fallback));
        return new StyledText(text.toString(), spans);
    }
    public Style getStyle(int offset) {
        for (Span span : spans) if (offset >= span.start && offset < span.end) return span.style;
        return spans.isEmpty() ? Style.EMPTY : spans.get(spans.size() - 1).style;
    }
    public StyledText slice(int start, int end) {
        List<Span> result = new ArrayList<>();
        for (Span span : spans) if (span.end > start && span.start < end)
            result.add(new Span(Math.max(start, span.start) - start, Math.min(end, span.end) - start, span.style));
        return new StyledText(text.substring(start, end), result);
    }
    public StyledText append(String suffix) {
        List<Span> result = new ArrayList<>(spans);
        result.add(new Span(text.length(), text.length() + suffix.length(), getStyle(Math.max(0, text.length() - 1))));
        return new StyledText(text + suffix, result);
    }
    public FormattedCharSequence sequence() {
        return sink -> {
            for (int at = 0; at < text.length();) {
                int cp = text.codePointAt(at);
                if (!sink.accept(at, getStyle(at), cp)) return false;
                at += Character.charCount(cp);
            }
            return true;
        };
    }
}
