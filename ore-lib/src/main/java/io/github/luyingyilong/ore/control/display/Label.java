package io.github.luyingyilong.ore.control.display;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.control.base.Size;
import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.debug.DebugProperty;
import io.github.luyingyilong.ore.debug.Inspectable;
import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.text.OreText;
import io.github.luyingyilong.ore.text.TextLayout;
import io.github.luyingyilong.ore.text.TextLayoutEngine;
import io.github.luyingyilong.ore.text.TextRenderer;
import io.github.luyingyilong.ore.theme.OreFonts;
import io.github.luyingyilong.ore.theme.OreTheme;
import io.github.luyingyilong.ore.theme.ThemeKey;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

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
    public static final ThemeKey<Float> FONT_SIZE = OreTheme.FONT_SIZE;
    public static final ThemeKey<Integer> FONT_COLOR = OreTheme.FONT_COLOR;
    private Component text;
    private Component styledText;
    private boolean hero;
    private boolean opticalCenter;
    private Overflow overflow = Overflow.VISIBLE;
    private Wrap wrap = Wrap.NONE;
    private Horizontal horizontal = Horizontal.LEFT;
    private Vertical vertical = Vertical.TOP;
    private float scale = 1.0F;
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
        var family = owner instanceof Label label ? label.getFont()
                : owner.getThemeItem(owner.getThemeItem(OreTheme.HERO) ? OreTheme.HERO_FONT : OreTheme.TEXT_FONT);
        var layout = OreText.layout(text, family, owner.getThemeItem(FONT_SIZE), width,
                overflow == Overflow.MARQUEE ? TextLayoutEngine.Wrap.NONE : TextLayoutEngine.Wrap.valueOf(wrap.name()),
                maxLines, false, owner.getThemeItem(OreTheme.FONT_METRICS), font);
        return (int) Math.ceil(layout.getHeight());
    }

    @Override
    protected boolean requiresAxisAlignedClip() {
        return getOverflow() != Overflow.VISIBLE;
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
    @io.github.luyingyilong.ore.gui.UiProperty
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

    public record Alignment(Horizontal horizontal, Vertical vertical) {
        public Alignment { java.util.Objects.requireNonNull(horizontal); java.util.Objects.requireNonNull(vertical); }
    }
    @io.github.luyingyilong.ore.gui.UiProperty
    public Alignment getAlignment() { return new Alignment(horizontal, vertical); }
    public Label setAlignment(Alignment value) { return setAlignment(value.horizontal(), value.vertical()); }

    public Label setAlignment(Horizontal horizontal, Vertical vertical) {
        this.horizontal = Objects.requireNonNull(horizontal);
        this.vertical = Objects.requireNonNull(vertical);
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

    @Inspectable(group = "Text", min = 0.0001, max = 64)
    @io.github.luyingyilong.ore.gui.UiProperty
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
    @io.github.luyingyilong.ore.gui.UiProperty
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
    @io.github.luyingyilong.ore.gui.UiProperty
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
    @io.github.luyingyilong.ore.gui.UiProperty
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
    @io.github.luyingyilong.ore.gui.UiProperty
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
        float fontSize = getThemeItem(FONT_SIZE);
        return scale * (fontSize / DEFAULT_FONT_SIZE);
    }

    public Label setMaximumWidth(int maximumWidth) {
        if (maximumWidth < 0) throw new IllegalArgumentException("Maximum width cannot be negative");
        this.maximumWidth = maximumWidth;
        requestLayout();
        return this;
    }

    @Inspectable(group = "Text", max = 2048)
    @io.github.luyingyilong.ore.gui.UiProperty
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
    @io.github.luyingyilong.ore.gui.UiProperty
    public Overflow getOverflow() {
        Overflow overflow = DebugBridge.value(this, "overflow", this.overflow);
        return overflow;
    }

    public Label setOverflow(Overflow overflow) {
        validateGeometryRequirement(overflow != Overflow.VISIBLE, false);
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
    @io.github.luyingyilong.ore.gui.UiProperty
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
        return Math.max(getMinimumWidth(), Math.min(maximumWidth, (int) Math.ceil(getTextLayout(font, Integer.MAX_VALUE).getWidth())));
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

    public TextLayout getTextLayout(Font font, int width) {
        return layout(font, width, getMaxLines(), getOverflow() == Overflow.ELLIPSIS);
    }

    private TextLayout layout(Font font, int width, int count, boolean ellipsis) {
        Wrap wrapping = getOverflow() == Overflow.MARQUEE ? Wrap.NONE : getWrap();
        return OreText.layout(getStyledText(), getFont(), 8 * textScale(), Math.max(0, width),
                TextLayoutEngine.Wrap.valueOf(wrapping.name()), count, ellipsis,
                getThemeItem(OreTheme.FONT_METRICS), font);
    }

    public int lineHeight(Font font) {
        return (int) getTextLayout(font, Integer.MAX_VALUE).getMetrics().lineHeight();
    }

    float firstLineOrigin(int height, int lines) {
        return firstLineOrigin(height, lines, getTextLayout(null, Integer.MAX_VALUE).getMetrics());
    }

    private float firstLineOrigin(int height, int lines, io.github.luyingyilong.ore.text.FontRepository.Metrics metrics) {
        Vertical align = DebugBridge.value(this, "vertical", vertical);
        float block = lines * metrics.lineHeight();
        return switch (align) {
            case TOP -> 0;
            case BOTTOM -> height - block;
            case CENTER -> getOpticalCenter()
                    ? Math.round(height / 2F - metrics.ascent() - (metrics.opticalTop() + metrics.opticalBottom()) / 2
                    - (lines - 1) * metrics.lineHeight() / 2 + getThemeItem(OreTheme.FONT_METRICS).opticalOffsetY())
                    : (float) Math.floor((height - block) / 2);
        };
    }

    public List<FormattedCharSequence> lines(Font font, int width) {
        return layout(font, width, Integer.MAX_VALUE, false).getLines().stream().map(line -> line.text().sequence()).toList();
    }

    public List<FormattedCharSequence> displayedLines(Font font, int width, int height) {
        int count = getOverflow() == Overflow.VISIBLE ? getMaxLines() : Math.min(getMaxLines(), Math.max(1, height / lineHeight(font)));
        return layout(font, width, count, getOverflow() == Overflow.ELLIPSIS).getLines().stream().map(line -> line.text().sequence()).toList();
    }

    /**
     * Screen-pixel offset for a left-right marquee; package-visible for deterministic tests.
     */
    int marqueeOffset(Font font, int width, long nowMs, boolean hovered) {
        ScrollTrigger scrollTrigger = DebugBridge.value(this, "scrollTrigger", this.scrollTrigger);
        float scrollSpeed = DebugBridge.value(this, "scrollSpeed", this.scrollSpeed);
        long scrollEndPauseMs = DebugBridge.value(this, "scrollEndPauseMs", this.scrollEndPauseMs);
        int distance = Math.max(0, (int) Math.ceil(getTextLayout(font, Integer.MAX_VALUE).getWidth()) - width);
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
        paint(context.getGraphics(), context.getFont(), getScreenBounds(screenX, screenY), context.getAbsMouseX(), context.getAbsMouseY());
    }

    private void paint(GuiGraphics graphics, Font font, Rect area, int mouseX, int mouseY) {
        Overflow overflow = DebugBridge.value(this, "overflow", this.overflow);
        Horizontal horizontal = DebugBridge.value(this, "horizontal", this.horizontal);
        int color = getThemeItem(FONT_COLOR);
        if (!getVisible() || area.width() <= 0 || area.height() <= 0) return;
        int count = overflow == Overflow.VISIBLE ? getMaxLines() : Math.min(getMaxLines(), Math.max(1, area.height() / lineHeight(font)));
        TextLayout layout = layout(font, area.width(), count, overflow == Overflow.ELLIPSIS);
        float startY = area.y() + firstLineOrigin(area.height(), layout.getLines().size(), layout.getMetrics());
        int scrollOffset = overflow == Overflow.MARQUEE
                ? marqueeOffset(font, area.width(), System.nanoTime() / 1_000_000L, area.contains(mouseX, mouseY)) : 0;
        if (overflow != Overflow.VISIBLE) {
            DrawContext.enableTransformedScissor(graphics, area.x(), area.y(), area.right(), area.bottom());
            DebugBridge.backend().clip(DrawContext.toScreenRect(graphics.pose().last().pose(), area));
        }
        try {
            for (int index = 0; index < layout.getLines().size(); index++) {
                var line = layout.getLines().get(index);
                float lineX = area.x() + switch (horizontal) {
                    case LEFT -> 0;
                    case CENTER -> (area.width() - line.width()) / 2;
                    case RIGHT -> area.width() - line.width();
                };
                if (overflow == Overflow.MARQUEE && line.width() > area.width()) lineX = area.x() - scrollOffset;
                float top = startY + index * layout.getMetrics().lineHeight();
                if (DebugBridge.active()) {
                    var m = layout.getMetrics();
                    DebugBridge.backend().text(this, graphics, area, lineX, top, line.width(), m.lineHeight(),
                            top, top + m.ascent(), top + m.ascent() + m.opticalTop(), top + m.ascent() + m.opticalBottom(),
                            8 * textScale(), layout.getDiagnostics(), scrollOffset);
                }
                TextRenderer.draw(graphics, layout.getLineLayout(index), lineX, top, color, getThemeItem(OreTheme.TEXT_SHADOW));
            }
        } finally {
            if (overflow != Overflow.VISIBLE) {
                graphics.flush();
                graphics.disableScissor();
                DebugBridge.backend().unclip();
            }
        }
    }

    @Override
    public void debugProperties(java.util.List<DebugProperty> out) {
        super.debugProperties(out);
        out.add(DebugProperty.themeNumber(this, FONT_SIZE, "Text", 0.0001, 256));
        out.add(DebugProperty.themeColor(this, FONT_COLOR, "Text"));
        out.add(DebugProperty.choice(this, "horizontal", "Text", () -> DebugBridge.value(this, "horizontal", this.horizontal)));
        out.add(DebugProperty.choice(this, "vertical", "Text", () -> DebugBridge.value(this, "vertical", this.vertical)));
    }

    public enum Overflow {VISIBLE, CLIP, ELLIPSIS, MARQUEE}

    public enum Wrap {NONE, WORD, CHARACTER}

    public enum Horizontal {LEFT, CENTER, RIGHT}

    public enum Vertical {TOP, CENTER, BOTTOM}

    public enum ScrollTrigger {ALWAYS, HOVER}

}
