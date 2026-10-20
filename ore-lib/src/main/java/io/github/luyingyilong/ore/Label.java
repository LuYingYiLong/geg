package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.debug.DebugProperty;
import io.github.luyingyilong.ore.debug.Inspectable;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.layout.Size;
import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.theme.OreFonts;
import io.github.luyingyilong.ore.theme.OreTextMetrics;
import io.github.luyingyilong.ore.theme.OreTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Shared Ore text measurement and rendering, including styled Minecraft Components.
 */
public final class Label extends Control {
    /**
     * GUI pixels: 8 preserves Minecraft's usual unscaled text size.
     */
    public static final float DEFAULT_FONT_SIZE = 8.0F;
    private Component text;
    private Component styledText;
    private boolean hero;
    private boolean opticalCenter;
    private Overflow overflow = Overflow.VISIBLE;
    private Wrap wrap = Wrap.NONE;
    private Horizontal horizontal = Horizontal.LEFT;
    private Vertical vertical = Vertical.TOP;
    private float scale = 1.0F;
    private float fontSize = DEFAULT_FONT_SIZE;
    private int maximumWidth = Integer.MAX_VALUE;
    private int maxLines = Integer.MAX_VALUE;
    private ScrollTrigger scrollTrigger = ScrollTrigger.HOVER;
    private float scrollSpeed = 20.0F;
    private long scrollEndPauseMs = 700L;
    private long scrollStartMs = Long.MIN_VALUE;
    public Label() {
        this(Component.empty());
    }

    public Label(Component text) {
        this.text = Objects.requireNonNull(text);
    }

    public static void registerTheme(io.github.luyingyilong.ore.theme.ThemeDB db) {
        io.github.luyingyilong.ore.theme.OreTheme.registerType(db, "Label");
    }

    /**
     * Body-font measurement without allocating a Control, SignalScope or diagnostic identity.
     */
    public static int measureTextHeight(Control owner, Font font, Component text, int width, Wrap wrap, Overflow overflow, int maxLines) {
        if (maxLines < 1) throw new IllegalArgumentException("At least one line is required");
        float scale = owner.getThemeItem(OreTheme.FONT_SIZE) / DEFAULT_FONT_SIZE;
        return Math.min(maxLines, wrappedLines(font, OreFonts.text(Objects.requireNonNull(text), owner.getThemeItem(OreTheme.TEXT_FONT)), Math.max(1, (int) Math.floor(width / scale)),
                Objects.requireNonNull(wrap), Objects.requireNonNull(overflow)).size()) * OreTextMetrics.body().lineHeight(scale);
    }

    private static List<FormattedCharSequence> wrappedLines(Font font, Component display, int logicalWidth, Wrap wrap, Overflow overflow) {
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

    public static void draw(Control owner, DrawContext context, Component text, Rect area, int color,
                            Overflow overflow, Horizontal horizontal, Vertical vertical) {
        Label painter = immediate(context, text);
        painter.setThemeParent(owner);
        painter.setColor(color).setOverflow(overflow).setAlignment(horizontal, vertical)
                .paint(context.getGraphics(), context.getFont(), area, context.getAbsMouseX(), context.getAbsMouseY(), owner);
    }

    /**
     * Compatibility painters borrow one window-owned scratch label; retained UI should own its Labels.
     */
    static Label immediate(DrawContext context, Component text) {
        return context.getWindowContext().getTextPainter().setText(text)
                .setHero(false).setFontSize(DEFAULT_FONT_SIZE).setScale(1.0F).setWrap(Wrap.NONE).setMaxLines(Integer.MAX_VALUE)
                .setShadow(false).setOpticalCenter(false);
    }

    @Inspectable(group = "Text")
    public boolean getHero() {
        boolean hero = DebugBridge.value(this, "hero", getThemeItem(OreTheme.HERO));
        return hero;
    }

    public Label setHero(boolean hero) {
        if (DebugBridge.previewWrite(this, "hero", hero)) return this;
        setThemeProperty(OreTheme.HERO, hero);
        if (this.hero != hero) {
            this.hero = hero;
            styledText = null;
            scrollStartMs = Long.MIN_VALUE;
            requestLayout();
        }
        return this;
    }

    @Inspectable(group = "Text")
    public boolean getOpticalCenter() {
        boolean opticalCenter = DebugBridge.value(this, "opticalCenter", this.opticalCenter);
        return opticalCenter;
    }

    /**
     * Use a fixed font-family visual box for centering, never the current string's glyph bounds.
     */
    public Label setOpticalCenter(boolean opticalCenter) {
        if (DebugBridge.previewWrite(this, "opticalCenter", opticalCenter)) return this;
        this.opticalCenter = opticalCenter;
        return this;
    }

    public Component getStyledText() {
        if (DebugBridge.active())
            return OreFonts.text(getText(), getThemeItem(getHero() ? OreTheme.HERO_FONT : OreTheme.TEXT_FONT));
        if (styledText == null)
            styledText = OreFonts.text(text, getThemeItem(getHero() ? OreTheme.HERO_FONT : OreTheme.TEXT_FONT));
        return styledText;
    }

    @Override
    public void invalidateTextLayout() {
        styledText = null;
        scrollStartMs = Long.MIN_VALUE;
        super.invalidateTextLayout();
    }

    public Label setAlignment(Horizontal horizontal, Vertical vertical) {
        this.horizontal = Objects.requireNonNull(horizontal);
        this.vertical = Objects.requireNonNull(vertical);
        return this;
    }

    @Inspectable(group = "Text", min = -2147483648.0, max = 4294967295.0)
    public int getColor() {
        int color = DebugBridge.value(this, "color", getThemeItem(OreTheme.TEXT_COLOR));
        return color;
    }

    public Label setColor(int color) {
        if (DebugBridge.previewWrite(this, "color", color)) return this;
        setThemeProperty(OreTheme.TEXT_COLOR, color);
        return this;
    }

    @Override
    public Label setThemeTypeVariation(String variation) {
        super.setThemeTypeVariation(variation);
        return this;
    }

    public Label clearHero() {
        clearThemeProperty(OreTheme.HERO);
        return this;
    }

    public Label clearColor() {
        clearThemeProperty(OreTheme.TEXT_COLOR);
        return this;
    }

    public Label clearFontSize() {
        clearThemeProperty(OreTheme.FONT_SIZE);
        return this;
    }

    public Label clearShadow() {
        clearThemeProperty(OreTheme.TEXT_SHADOW);
        return this;
    }

    public net.minecraft.resources.ResourceLocation getFont() {
        return getThemeItem(getHero() ? OreTheme.HERO_FONT : OreTheme.TEXT_FONT);
    }

    public Label setFont(net.minecraft.resources.ResourceLocation font) {
        setThemeProperty(OreTheme.TEXT_FONT, font);
        setThemeProperty(OreTheme.HERO_FONT, font);
        return this;
    }

    public Label clearFont() {
        clearThemeProperty(OreTheme.TEXT_FONT);
        clearThemeProperty(OreTheme.HERO_FONT);
        return this;
    }

    public boolean getShadow() {
        return getThemeItem(OreTheme.TEXT_SHADOW);
    }

    public Label setShadow(boolean shadow) {
        setThemeProperty(OreTheme.TEXT_SHADOW, shadow);
        return this;
    }

    @Inspectable(group = "Text", min = 0.0001, max = 256)
    public float getFontSize() {
        float fontSize = DebugBridge.value(this, "fontSize", getThemeItem(OreTheme.FONT_SIZE));
        return fontSize;
    }

    /**
     * Sets the logical text size; scale remains an additional relative multiplier.
     */
    public Label setFontSize(float fontSize) {
        if (!(fontSize > 0.0F) || !Float.isFinite(fontSize))
            throw new IllegalArgumentException("Font size must be positive and finite");
        if (DebugBridge.previewWrite(this, "fontSize", fontSize)) return this;
        setThemeProperty(OreTheme.FONT_SIZE, fontSize);
        if (this.fontSize != fontSize) {
            this.fontSize = fontSize;
            scrollStartMs = Long.MIN_VALUE;
            requestLayout();
        }
        return this;
    }

    @Inspectable(group = "Text", min = 0.0001, max = 64)
    public float getScale() {
        return DebugBridge.value(this, "scale", scale);
    }

    public Label setScale(float scale) {
        if (!(scale > 0.0F) || !Float.isFinite(scale)) throw new IllegalArgumentException("Invalid text scale");
        if (DebugBridge.previewWrite(this, "scale", scale)) return this;
        if (this.scale != scale) {
            this.scale = scale;
            scrollStartMs = Long.MIN_VALUE;
            requestLayout();
        }
        return this;
    }

    @Inspectable(group = "Text", min = 1, max = Integer.MAX_VALUE)
    public int getMaxLines() {
        return DebugBridge.value(this, "maxLines", maxLines);
    }

    public Label setMaxLines(int maxLines) {
        if (maxLines < 1) throw new IllegalArgumentException("At least one line is required");
        if (DebugBridge.previewWrite(this, "maxLines", maxLines)) return this;
        if (this.maxLines != maxLines) {
            this.maxLines = maxLines;
            requestLayout();
        }
        return this;
    }

    @Inspectable(group = "Text")
    public ScrollTrigger getScrollTrigger() {
        return DebugBridge.value(this, "scrollTrigger", scrollTrigger);
    }

    public Label setScrollTrigger(ScrollTrigger trigger) {
        ScrollTrigger next = Objects.requireNonNull(trigger);
        if (DebugBridge.previewWrite(this, "scrollTrigger", next)) return this;
        if (scrollTrigger != next) {
            scrollTrigger = next;
            scrollStartMs = Long.MIN_VALUE;
        }
        return this;
    }

    @Inspectable(group = "Text", min = 0.0001, max = 100000)
    public float getScrollSpeed() {
        return DebugBridge.value(this, "scrollSpeed", scrollSpeed);
    }

    public Label setScrollSpeed(float pixelsPerSecond) {
        if (!(pixelsPerSecond > 0.0F) || !Float.isFinite(pixelsPerSecond))
            throw new IllegalArgumentException("Scroll speed must be positive and finite");
        if (DebugBridge.previewWrite(this, "scrollSpeed", pixelsPerSecond)) return this;
        if (scrollSpeed != pixelsPerSecond) {
            scrollSpeed = pixelsPerSecond;
            scrollStartMs = Long.MIN_VALUE;
        }
        return this;
    }

    @Inspectable(group = "Text", setter = "setScrollEndPauseMs", min = 0, max = 100000)
    public long getScrollEndPauseMs() {
        return DebugBridge.value(this, "scrollEndPauseMs", scrollEndPauseMs);
    }

    public Label setScrollEndPauseMs(long milliseconds) {
        if (milliseconds < 0L) throw new IllegalArgumentException("Pause cannot be negative");
        if (DebugBridge.previewWrite(this, "scrollEndPauseMs", milliseconds)) return this;
        if (scrollEndPauseMs != milliseconds) {
            scrollEndPauseMs = milliseconds;
            scrollStartMs = Long.MIN_VALUE;
        }
        return this;
    }

    private float textScale() {
        float scale = DebugBridge.value(this, "scale", this.scale);
        float fontSize = DebugBridge.value(this, "fontSize", getThemeItem(OreTheme.FONT_SIZE));
        return scale * (fontSize / DEFAULT_FONT_SIZE);
    }

    public Label setMaximumWidth(int maximumWidth) {
        if (maximumWidth < 0) throw new IllegalArgumentException("Maximum width cannot be negative");
        this.maximumWidth = maximumWidth;
        requestLayout();
        return this;
    }

    @Inspectable(group = "Text", max = 2048)
    public Component getText() {
        Component text = DebugBridge.value(this, "text", this.text);
        return text;
    }

    public Label setText(Component text) {
        Component next = Objects.requireNonNull(text);
        if (DebugBridge.previewWrite(this, "text", next)) return this;
        if (!this.text.equals(next)) {
            styledText = null;
            scrollStartMs = Long.MIN_VALUE;
            requestLayout();
        }
        this.text = next;
        return this;
    }

    @Inspectable(group = "Text")
    public Overflow getOverflow() {
        Overflow overflow = DebugBridge.value(this, "overflow", this.overflow);
        return overflow;
    }

    public Label setOverflow(Overflow overflow) {
        Overflow next = Objects.requireNonNull(overflow);
        if (DebugBridge.previewWrite(this, "overflow", next)) return this;
        if (this.overflow != next) {
            this.overflow = next;
            scrollStartMs = Long.MIN_VALUE;
            requestLayout();
        }
        return this;
    }

    @Inspectable(group = "Text")
    public Wrap getWrap() {
        Wrap wrap = DebugBridge.value(this, "wrap", this.wrap);
        return wrap;
    }

    public Label setWrap(Wrap wrap) {
        Objects.requireNonNull(wrap);
        if (DebugBridge.previewWrite(this, "wrap", wrap)) return this;
        if (this.wrap != wrap) {
            this.wrap = wrap;
            requestLayout();
        }
        return this;
    }

    @Override
    public int measureWidth(Font font, int available) {
        if (!isAlive()) return 0;
        return Math.max(getMinimumWidth(), Math.min(maximumWidth, (int) Math.ceil(font.width(getStyledText()) * textScale())));
    }

    @Override
    public int measureHeight(Font font, int available) {
        if (!isAlive()) return 0;
        int maxLines = DebugBridge.value(this, "maxLines", this.maxLines);
        return Math.max(getMinimumHeight(), Math.min(maxLines, lines(font, available).size()) * lineHeight(font));
    }

    @Override
    public Size measureSize(Font font, Size available) {
        if (!isAlive()) return new Size(0, 0);
        return new Size(measureWidth(font, available.width()),
                measureHeight(font, available.width()));
    }

    public int lineHeight(Font font) {
        boolean hero = DebugBridge.value(this, "hero", getThemeItem(OreTheme.HERO));
        return OreTextMetrics.family(hero).lineHeight(textScale());
    }

    float firstLineOrigin(int height, int lines) {
        boolean hero = DebugBridge.value(this, "hero", getThemeItem(OreTheme.HERO));
        boolean opticalCenter = DebugBridge.value(this, "opticalCenter", this.opticalCenter);
        Vertical vertical = DebugBridge.value(this, "vertical", this.vertical);
        var metrics = OreTextMetrics.family(hero);
        return switch (vertical) {
            case TOP -> metrics.drawOrigin(textScale());
            case CENTER -> metrics.centeredOrigin(height, lines, textScale(), opticalCenter);
            case BOTTOM -> height - metrics.blockHeight(lines, textScale()) + metrics.drawOrigin(textScale());
        };
    }

    public List<FormattedCharSequence> lines(Font font, int width) {
        Overflow overflow = DebugBridge.value(this, "overflow", this.overflow);
        Wrap wrap = DebugBridge.value(this, "wrap", this.wrap);
        int logicalWidth = Math.max(1, (int) Math.floor(width / textScale()));
        Component display = getStyledText();
        return wrappedLines(font, display, logicalWidth, wrap, overflow);
    }

    private FormattedCharSequence ellipsize(Font font, FormattedCharSequence line, int width, boolean forced) {
        if (!forced && font.width(line) * textScale() <= width) return line;
        Component ellipsis = Component.literal("…").setStyle(getStyledText().getStyle());
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
        Overflow overflow = DebugBridge.value(this, "overflow", this.overflow);
        int maxLines = DebugBridge.value(this, "maxLines", this.maxLines);
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

    /**
     * Screen-pixel offset for a left-right marquee; package-visible for deterministic tests.
     */
    int marqueeOffset(Font font, int width, long nowMs, boolean hovered) {
        ScrollTrigger scrollTrigger = DebugBridge.value(this, "scrollTrigger", this.scrollTrigger);
        float scrollSpeed = DebugBridge.value(this, "scrollSpeed", this.scrollSpeed);
        long scrollEndPauseMs = DebugBridge.value(this, "scrollEndPauseMs", this.scrollEndPauseMs);
        int distance = Math.max(0, (int) Math.ceil(font.width(getStyledText()) * textScale()) - width);
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
    protected void render(DrawContext context, int screenX, int screenY) {
        drawAt(context, getScreenBounds(screenX, screenY));
    }

    private void drawAt(DrawContext context, Rect area) {
        paint(context.getGraphics(), context.getFont(), area, context.getAbsMouseX(), context.getAbsMouseY());
    }

    /**
     * Internal shared painter for Ore components hosted outside a ModularUI screen.
     */
    void paint(GuiGraphics graphics, Font font, Rect area, int mouseX, int mouseY) {
        paint(graphics, font, area, mouseX, mouseY, this);
    }

    private void paint(GuiGraphics graphics, Font font, Rect area, int mouseX, int mouseY, Control debugOwner) {
        Overflow overflow = DebugBridge.value(this, "overflow", this.overflow);
        Horizontal horizontal = DebugBridge.value(this, "horizontal", this.horizontal);
        int color = DebugBridge.value(this, "color", getThemeItem(OreTheme.TEXT_COLOR));
        if (!getVisible() || area.width() <= 0 || area.height() <= 0) return;
        float textScale = textScale();
        List<FormattedCharSequence> lines = displayedLines(font, area.width(), area.height());
        int lineHeight = lineHeight(font);
        int visibleLines = lines.size();
        float startY = area.y() + firstLineOrigin(area.height(), visibleLines);
        int scrollOffset = overflow == Overflow.MARQUEE
                ? marqueeOffset(font, area.width(), System.nanoTime() / 1_000_000L, area.contains(mouseX, mouseY)) : 0;
        if (overflow != Overflow.VISIBLE) {
            DrawContext.enableTransformedScissor(graphics, area.x(), area.y(), area.right(), area.bottom());
            DebugBridge.backend().clip(DrawContext.toScreenRect(graphics.pose().last().pose(), area));
        }
        for (int index = 0; index < visibleLines; index++) {
            FormattedCharSequence line = lines.get(index);
            int lineWidth = (int) Math.ceil(font.width(line) * textScale);
            int lineX = area.x() + switch (horizontal) {
                case LEFT -> 0;
                case CENTER -> (area.width() - lineWidth) / 2;
                case RIGHT -> area.width() - lineWidth;
            };
            if (overflow == Overflow.MARQUEE && lineWidth > area.width()) {
                lineX = area.x() - scrollOffset;
            }
            if (DebugBridge.active() && debugOwner != null) {
                var metrics = OreTextMetrics.family(getHero());
                java.util.Set<String> spanFonts = new java.util.LinkedHashSet<>();
                line.accept((at, style, codepoint) -> {
                    spanFonts.add(style.getFont().toString());
                    return true;
                });
                String chain = getHero() ? "Minecraft Ten → Noto Sans → Minecraft default" : "Mojangles → Noto Sans → Minecraft default";
                DebugBridge.backend().text(debugOwner, graphics, area,
                        lineX, startY + index * lineHeight, lineWidth, lineHeight,
                        startY + index * lineHeight - metrics.drawOrigin(textScale),
                        startY + index * lineHeight + 7 * textScale,
                        startY + index * lineHeight + metrics.inkTop() * textScale,
                        startY + index * lineHeight + metrics.inkBottom() * textScale,
                        8 * textScale, "Span font IDs: " + spanFonts + "; configured fallback: " + chain + "; not a per-glyph provider attribution", scrollOffset);
            }
            graphics.pose().pushPose();
            graphics.pose().translate(lineX, startY + index * lineHeight, 0.0F);
            graphics.pose().scale(textScale, textScale, 1.0F);
            OreFonts.draw(graphics, font, line, 0, 0, color, getThemeItem(OreTheme.TEXT_SHADOW));
            graphics.pose().popPose();
        }
        if (overflow != Overflow.VISIBLE) {
            graphics.disableScissor();
            DebugBridge.backend().unclip();
        }
    }

    @Override
    public void debugProperties(java.util.List<DebugProperty> out) {
        super.debugProperties(out);
        out.add(DebugProperty.choice(this, "horizontal", "Text", () -> DebugBridge.value(this, "horizontal", this.horizontal)));
        out.add(DebugProperty.choice(this, "vertical", "Text", () -> DebugBridge.value(this, "vertical", this.vertical)));
    }

    public enum Overflow {VISIBLE, CLIP, ELLIPSIS, MARQUEE}

    public enum Wrap {NONE, WORD, CHARACTER}

    public enum Horizontal {LEFT, CENTER, RIGHT}

    public enum Vertical {TOP, CENTER, BOTTOM}

    public enum ScrollTrigger {ALWAYS, HOVER}

}
