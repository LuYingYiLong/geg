package io.github.luyingyilong.geg.client.ui;

import brachy.modularui.screen.viewport.ModularGuiContext;
import io.github.luyingyilong.geg.client.ui.layout.Control;
import io.github.luyingyilong.geg.client.ui.layout.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Shared Ore text measurement and rendering, including styled Minecraft Components. */
public final class Label extends Control {
    public enum Overflow { VISIBLE, CLIP, ELLIPSIS }
    public enum Wrap { NONE, WORD, CHARACTER }
    public enum Horizontal { LEFT, CENTER, RIGHT }
    public enum Vertical { TOP, CENTER, BOTTOM }

    private Component text;
    private Overflow overflow = Overflow.VISIBLE;
    private Wrap wrap = Wrap.NONE;
    private Horizontal horizontal = Horizontal.LEFT;
    private Vertical vertical = Vertical.TOP;
    private int color = 0xFFFFFFFF;
    private boolean shadow;
    private float scale = 1.0F;
    private int maxLines = Integer.MAX_VALUE;

    public Label(Component text) { this.text = Objects.requireNonNull(text); }
    public Label text(Component text) { this.text = Objects.requireNonNull(text); return this; }
    public Label overflow(Overflow overflow) { this.overflow = Objects.requireNonNull(overflow); return this; }
    public Label wrap(Wrap wrap) { this.wrap = Objects.requireNonNull(wrap); return this; }
    public Label align(Horizontal horizontal, Vertical vertical) {
        this.horizontal = Objects.requireNonNull(horizontal);
        this.vertical = Objects.requireNonNull(vertical);
        return this;
    }
    public Label color(int color) { this.color = color; return this; }
    public Label shadow(boolean shadow) { this.shadow = shadow; return this; }
    public Label scale(float scale) {
        if (!(scale > 0.0F) || !Float.isFinite(scale)) throw new IllegalArgumentException("Invalid text scale");
        this.scale = scale;
        return this;
    }
    public Label maxLines(int maxLines) {
        if (maxLines < 1) throw new IllegalArgumentException("At least one line is required");
        this.maxLines = maxLines;
        return this;
    }

    public Component text() { return text; }
    public Overflow overflow() { return overflow; }
    public Wrap wrap() { return wrap; }

    @Override
    public int measureWidth(Font font, int available) {
        return (int) Math.ceil(font.width(text) * scale);
    }

    @Override
    public int measureHeight(Font font, int available) {
        int width = Math.max(1, (int) Math.floor(available / scale));
        return Math.min(maxLines, lines(font, width).size()) * lineHeight(font);
    }

    public int lineHeight(Font font) { return Math.max(1, (int) Math.ceil((font.lineHeight + 1) * scale)); }

    public List<FormattedCharSequence> lines(Font font, int width) {
        int logicalWidth = Math.max(1, (int) Math.floor(width / scale));
        if (wrap == Wrap.NONE) return List.of(text.getVisualOrderText());
        if (wrap == Wrap.WORD) return font.split(text, logicalWidth);
        List<FormattedCharSequence> result = new ArrayList<>();
        List<FormattedCharSequence> current = new ArrayList<>();
        int[] currentWidth = {0};
        text.getVisualOrderText().accept((position, style, codepoint) -> {
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
        if (!forced && font.width(line) * scale <= width) return line;
        int logicalWidth = Math.max(0, (int) Math.floor(width / scale) - font.width("…"));
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
        glyphs.add(FormattedCharSequence.forward("…", Style.EMPTY));
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
            if (overflow == Overflow.ELLIPSIS && (truncated || font.width(line) * scale > width)) {
                line = ellipsize(font, line, width, truncated);
            }
            result.add(line);
        }
        return List.copyOf(result);
    }

    @Override
    public void draw(ModularGuiContext context, int screenX, int screenY) {
        drawAt(context, screenBounds(screenX, screenY));
    }

    public void drawAt(ModularGuiContext context, Rect area) {
        if (!visible() || area.width() <= 0 || area.height() <= 0) return;
        Font font = context.getFont();
        GuiGraphics graphics = context.getGraphics();
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
            int lineWidth = (int) Math.ceil(font.width(line) * scale);
            int lineX = area.x() + switch (horizontal) {
                case LEFT -> 0;
                case CENTER -> (area.width() - lineWidth) / 2;
                case RIGHT -> area.width() - lineWidth;
            };
            if (scale == 1.0F) graphics.drawString(font, line, lineX, startY + index * lineHeight, color, shadow);
            else {
                graphics.pose().pushPose();
                graphics.pose().translate(lineX, startY + index * lineHeight, 0.0F);
                graphics.pose().scale(scale, scale, 1.0F);
                graphics.drawString(font, line, 0, 0, color, shadow);
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
