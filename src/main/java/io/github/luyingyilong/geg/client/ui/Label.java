package io.github.luyingyilong.geg.client.ui;

import brachy.modularui.screen.viewport.ModularGuiContext;
import io.github.luyingyilong.geg.client.ui.layout.Control;
import io.github.luyingyilong.geg.client.ui.layout.Rect;
import io.github.luyingyilong.geg.client.ui.layout.Size;
import io.github.luyingyilong.geg.client.ui.theme.OreFonts;
import io.github.luyingyilong.geg.client.ui.theme.OreTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Shared Ore text measurement and rendering, including styled Minecraft Components. */
public final class Label extends Control {
    /** GUI pixels: 8 preserves Minecraft's usual unscaled text size. */
    public static final float DEFAULT_FONT_SIZE = 8.0F;
    public enum Overflow { VISIBLE, CLIP, ELLIPSIS, MARQUEE }
    public enum Wrap { NONE, WORD, CHARACTER }
    public enum Horizontal { LEFT, CENTER, RIGHT }
    public enum Vertical { TOP, CENTER, BOTTOM }
    public enum ScrollTrigger { ALWAYS, HOVER }

    private Component text;
    private Component styledText;
    private boolean hero;
    private Overflow overflow = Overflow.VISIBLE;
    private Wrap wrap = Wrap.NONE;
    private Horizontal horizontal = Horizontal.LEFT;
    private Vertical vertical = Vertical.TOP;
    private int color = OreTheme.TEXT_WHITE;
    private boolean shadow;
    private float scale = 1.0F;
    private float fontSize = DEFAULT_FONT_SIZE;
    private int maximumWidth = Integer.MAX_VALUE;
    private int maxLines = Integer.MAX_VALUE;
    private ScrollTrigger scrollTrigger = ScrollTrigger.HOVER;
    private float scrollSpeed = 20.0F;
    private long scrollEndPauseMs = 700L;
    private long scrollStartMs = Long.MIN_VALUE;

    public Label() { this(Component.empty()); }
    public Label(Component text) { this.text = Objects.requireNonNull(text); }
    public Label text(Component text) {
        Component next = Objects.requireNonNull(text);
        if (!this.text.equals(next)) {
            styledText = null;
            scrollStartMs = Long.MIN_VALUE;
            requestLayout();
        }
        this.text = next;
        return this;
    }
    public Label setText(Component text) { return text(text); }
    public Label hero(boolean hero) {
        if (this.hero != hero) {
            this.hero = hero;
            styledText = null;
            scrollStartMs = Long.MIN_VALUE;
            requestLayout();
        }
        return this;
    }
    public boolean hero() { return hero; }
    public Component styledText() {
        if (styledText == null) styledText = OreFonts.text(text, hero);
        return styledText;
    }

    @Override public void invalidateTextLayout() {
        scrollStartMs = Long.MIN_VALUE;
        super.invalidateTextLayout();
    }
    public Label overflow(Overflow overflow) {
        this.overflow = Objects.requireNonNull(overflow);
        scrollStartMs = Long.MIN_VALUE;
        return this;
    }
    public Label wrap(Wrap wrap) { this.wrap = Objects.requireNonNull(wrap); requestLayout(); return this; }
    public Label scrollTrigger(ScrollTrigger trigger) {
        scrollTrigger = Objects.requireNonNull(trigger);
        scrollStartMs = Long.MIN_VALUE;
        return this;
    }
    public Label scrollSpeed(float pixelsPerSecond) {
        if (!(pixelsPerSecond > 0.0F) || !Float.isFinite(pixelsPerSecond))
            throw new IllegalArgumentException("Scroll speed must be positive and finite");
        scrollSpeed = pixelsPerSecond;
        scrollStartMs = Long.MIN_VALUE;
        return this;
    }
    public Label scrollEndPause(long milliseconds) {
        if (milliseconds < 0L) throw new IllegalArgumentException("Pause cannot be negative");
        scrollEndPauseMs = milliseconds;
        scrollStartMs = Long.MIN_VALUE;
        return this;
    }
    public Label align(Horizontal horizontal, Vertical vertical) {
        this.horizontal = Objects.requireNonNull(horizontal);
        this.vertical = Objects.requireNonNull(vertical);
        return this;
    }
    public Label color(int color) { this.color = color; return this; }
    public int color() { return color; }
    public Label shadow(boolean shadow) { this.shadow = shadow; return this; }
    /** Sets the logical text size; scale remains an additional relative multiplier. */
    public Label fontSize(float fontSize) {
        if (!(fontSize > 0.0F) || !Float.isFinite(fontSize))
            throw new IllegalArgumentException("Font size must be positive and finite");
        if (this.fontSize != fontSize) {
            this.fontSize = fontSize;
            scrollStartMs = Long.MIN_VALUE;
            requestLayout();
        }
        return this;
    }
    public float fontSize() { return fontSize; }
    private float textScale() { return scale * (fontSize / DEFAULT_FONT_SIZE); }

    public Label scale(float scale) {
        if (!(scale > 0.0F) || !Float.isFinite(scale)) throw new IllegalArgumentException("Invalid text scale");
        this.scale = scale;
        scrollStartMs = Long.MIN_VALUE;
        requestLayout();
        return this;
    }
    public Label maxLines(int maxLines) {
        if (maxLines < 1) throw new IllegalArgumentException("At least one line is required");
        this.maxLines = maxLines;
        requestLayout();
        return this;
    }
    public Label maximumWidth(int maximumWidth) {
        if (maximumWidth < 0) throw new IllegalArgumentException("Maximum width cannot be negative");
        this.maximumWidth = maximumWidth;
        requestLayout();
        return this;
    }

    public Component text() { return text; }
    public Component getText() { return text; }
    public Overflow overflow() { return overflow; }
    public Wrap wrap() { return wrap; }

    @Override
    public int measureWidth(Font font, int available) {
        return Math.max(minimumWidth(), Math.min(maximumWidth, (int) Math.ceil(font.width(styledText()) * textScale())));
    }

    @Override
    public int measureHeight(Font font, int available) {
        return Math.max(minimumHeight(), Math.min(maxLines, lines(font, available).size()) * lineHeight(font));
    }

    @Override
    public Size measureSize(Font font, Size available) {
        return new Size(measureWidth(font, available.width()),
                measureHeight(font, available.width()));
    }

    public int lineHeight(Font font) { return Math.max(1, (int) Math.ceil((font.lineHeight + 1) * textScale())); }

    public List<FormattedCharSequence> lines(Font font, int width) {
        int logicalWidth = Math.max(1, (int) Math.floor(width / textScale()));
        Component display = styledText();
        if (wrap == Wrap.NONE || overflow == Overflow.MARQUEE) return List.of(display.getVisualOrderText());
        if (wrap == Wrap.WORD) return font.split(display, logicalWidth);
        List<FormattedCharSequence> result = new ArrayList<>();
        List<FormattedCharSequence> current = new ArrayList<>();
        int[] currentWidth = {0};
        display.getVisualOrderText().accept((position, style, codepoint) -> {
            if (codepoint == '\n') {
                result.add(FormattedCharSequence.composite(List.copyOf(current)));
                current.clear();
                currentWidth[0] = 0;
                return true;
            }
            FormattedCharSequence glyph = FormattedCharSequence.codepoint(codepoint, style);
            int glyphWidth = font.width(glyph);
            if (!current.isEmpty() && currentWidth[0] + glyphWidth > logicalWidth) {
                result.add(FormattedCharSequence.composite(List.copyOf(current)));
                current.clear();
                currentWidth[0] = 0;
            }
            current.add(glyph);
            currentWidth[0] += glyphWidth;
            return true;
        });
        result.add(FormattedCharSequence.composite(List.copyOf(current)));
        return result;
    }

    private FormattedCharSequence ellipsize(Font font, FormattedCharSequence line, int width, boolean forced) {
        if (!forced && font.width(line) * textScale() <= width) return line;
        Component ellipsis = Component.literal("…").setStyle(styledText().getStyle());
        int logicalWidth = Math.max(0, (int) Math.floor(width / textScale()) - font.width(ellipsis));
        List<FormattedCharSequence> glyphs = new ArrayList<>();
        int[] used = {0};
        line.accept((index, style, codepoint) -> {
            FormattedCharSequence glyph = FormattedCharSequence.codepoint(codepoint, style);
            int glyphWidth = font.width(glyph);
            if (used[0] + glyphWidth > logicalWidth) return false;
            glyphs.add(glyph);
            used[0] += glyphWidth;
            return true;
        });
        glyphs.add(ellipsis.getVisualOrderText());
        return FormattedCharSequence.composite(glyphs);
    }

    public List<FormattedCharSequence> displayedLines(Font font, int width, int height) {
        List<FormattedCharSequence> source = lines(font, width);
        int count = Math.min(maxLines, source.size());
        if (overflow != Overflow.VISIBLE) count = Math.min(count,
                Math.max(1, height / lineHeight(font)));
        List<FormattedCharSequence> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            FormattedCharSequence line = source.get(index);
            boolean truncated = index == count - 1 && source.size() > count;
            if (overflow == Overflow.ELLIPSIS && (truncated || font.width(line) * textScale() > width)) {
                line = ellipsize(font, line, width, truncated);
            }
            result.add(line);
        }
        return List.copyOf(result);
    }

    /** Screen-pixel offset for a left-right marquee; package-visible for deterministic tests. */
    int marqueeOffset(Font font, int width, long nowMs, boolean hovered) {
        int distance = Math.max(0, (int) Math.ceil(font.width(styledText()) * textScale()) - width);
        if (distance == 0 || (scrollTrigger == ScrollTrigger.HOVER && !hovered)) {
            scrollStartMs = Long.MIN_VALUE;
            return 0;
        }
        if (scrollStartMs == Long.MIN_VALUE || nowMs < scrollStartMs) scrollStartMs = nowMs;
        double travelMs = distance * 1000.0D / scrollSpeed;
        double cycleMs = travelMs * 2.0D + scrollEndPauseMs * 2.0D;
        double phase = (nowMs - scrollStartMs) % cycleMs;
        if (phase < travelMs) return Math.min(distance, (int) Math.floor(phase * scrollSpeed / 1000.0D));
        phase -= travelMs;
        if (phase < scrollEndPauseMs) return distance;
        phase -= scrollEndPauseMs;
        if (phase < travelMs) return Math.max(0, distance - (int) Math.floor(phase * scrollSpeed / 1000.0D));
        return 0;
    }

    @Override
    protected void render(ModularGuiContext context, int screenX, int screenY) {
        drawAt(context, screenBounds(screenX, screenY));
    }

    private void drawAt(ModularGuiContext context, Rect area) {
        paint(context.getGraphics(), context.getFont(), area, context.getAbsMouseX(), context.getAbsMouseY());
    }

    /** Internal shared painter for Ore components hosted outside a ModularUI screen. */
    void paint(GuiGraphics graphics, Font font, Rect area, int mouseX, int mouseY) {
        if (!visible() || area.width() <= 0 || area.height() <= 0) return;
        float textScale = textScale();
        List<FormattedCharSequence> lines = displayedLines(font, area.width(), area.height());
        int lineHeight = lineHeight(font);
        int visibleLines = lines.size();
        int blockHeight = visibleLines * lineHeight;
        int startY = area.y() + switch (vertical) {
            case TOP -> 0;
            case CENTER -> (area.height() - blockHeight) / 2;
            case BOTTOM -> area.height() - blockHeight;
        };
        if (overflow != Overflow.VISIBLE) graphics.enableScissor(area.x(), area.y(), area.right(), area.bottom());
        for (int index = 0; index < visibleLines; index++) {
            FormattedCharSequence line = lines.get(index);
            int lineWidth = (int) Math.ceil(font.width(line) * textScale);
            int lineX = area.x() + switch (horizontal) {
                case LEFT -> 0;
                case CENTER -> (area.width() - lineWidth) / 2;
                case RIGHT -> area.width() - lineWidth;
            };
            if (overflow == Overflow.MARQUEE && lineWidth > area.width()) {
                boolean hovered = area.contains(mouseX, mouseY);
                lineX = area.x() - marqueeOffset(font, area.width(), System.nanoTime() / 1_000_000L, hovered);
            }
            if (textScale == 1.0F) OreFonts.draw(graphics, font, line, lineX, startY + index * lineHeight, color, shadow);
            else {
                graphics.pose().pushPose();
                graphics.pose().translate(lineX, startY + index * lineHeight, 0.0F);
                graphics.pose().scale(textScale, textScale, 1.0F);
                OreFonts.draw(graphics, font, line, 0, 0, color, shadow);
                graphics.pose().popPose();
            }
        }
        if (overflow != Overflow.VISIBLE) graphics.disableScissor();
    }

    public static void draw(ModularGuiContext context, Component text, Rect area, int color,
                            Overflow overflow, Horizontal horizontal, Vertical vertical) {
        draw(context, text, area, color, false, overflow, horizontal, vertical);
    }

    public static void draw(ModularGuiContext context, Component text, Rect area, int color,
                            boolean shadow, Overflow overflow, Horizontal horizontal, Vertical vertical) {
        new Label(text).color(color).shadow(shadow).overflow(overflow)
                .align(horizontal, vertical).drawAt(context, area);
    }
}
