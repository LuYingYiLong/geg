package io.github.luyingyilong.geg.client.ui;

import io.github.luyingyilong.geg.client.ui.layout.SizeFlags;
import brachy.modularui.screen.viewport.ModularGuiContext;
import io.github.luyingyilong.geg.client.ui.layout.Container;
import io.github.luyingyilong.geg.client.ui.layout.Control;
import io.github.luyingyilong.geg.client.ui.layout.Panel;
import io.github.luyingyilong.geg.client.ui.layout.Rect;
import io.github.luyingyilong.geg.client.ui.layout.Size;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** A full-width centered title with right-aligned actions that flow onto later rows. */
public final class Header extends Container {
    public static final int ROW_HEIGHT = 25;
    private static final int GAP = 6;
    private static final int PADDING = 0;
    private Panel background;
    private final Label title = new Label().color(OreTheme.TEXT_DARK).hero(true).overflow(Label.Overflow.ELLIPSIS)
            .align(Label.Horizontal.CENTER, Label.Vertical.CENTER);
    private final List<Control> extra = new ArrayList<>();

    public Header(Component title) {
        this.title.text(title);
        addChild(this.title);
        sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
    }

    public Header title(Component text) { title.text(text); return this; }
    public Component title() { return title.text(); }
    public Header titleColor(int color) { title.color(color); return this; }
    public Header fontSize(float fontSize) { title.fontSize(fontSize); return this; }
    public float fontSize() { return title.fontSize(); }

    @Override public Header addChild(Control child) {
        super.addChild(child);
        if (child != title) extra.add(child);
        return this;
    }

    @Override public boolean removeChild(Control child) {
        if (child == title || !super.removeChild(child)) return false;
        extra.remove(child);
        return true;
    }

    /** Replaces the action slots; each control retains its own minimum and desired size. */
    public Header extra(Control... controls) {
        for (Control control : List.copyOf(extra)) removeChild(control);
        for (Control control : controls) addChild(control);
        return this;
    }

    private record Row(List<Control> controls, int height) { }
    private record Arrangement(boolean inline, List<Row> rows, int height) { }

    private int titleRowHeight(Font font) {
        return font == null ? ROW_HEIGHT : Math.max(ROW_HEIGHT, title.lineHeight(font));
    }

    private int desiredWidth(Control control, Font font, int width) {
        return Math.max(control.minimumWidth(), control.measureSize(font, new Size(width, ROW_HEIGHT)).width());
    }

    private Arrangement arrange(Font font, int outerWidth) {
        int width = Math.max(0, outerWidth - PADDING * 2);
        List<Control> active = extra.stream().filter(Control::visible).toList();
        int minimum = active.stream().mapToInt(Control::minimumWidth).sum()
                + Math.max(0, active.size() - 1) * GAP;
        int titleWidth = font == null ? title.minimumWidth() : title.measureWidth(font, width);
        if (active.isEmpty() || titleWidth + 2 * (minimum + GAP) <= width) {
            int height = titleRowHeight(font);
            for (Control control : active)
                height = Math.max(height, control.measureSize(font, new Size(width, ROW_HEIGHT)).height());
            return new Arrangement(true, List.of(new Row(active, height)), height);
        }
        List<Row> rows = new ArrayList<>();
        List<Control> current = new ArrayList<>();
        int used = 0;
        int height = ROW_HEIGHT;
        for (Control control : active) {
            int next = control.minimumWidth() + (current.isEmpty() ? 0 : GAP);
            if (!current.isEmpty() && used + next > width) {
                rows.add(new Row(List.copyOf(current), height));
                current.clear();
                used = 0;
                height = ROW_HEIGHT;
            }
            used += control.minimumWidth() + (current.isEmpty() ? 0 : GAP);
            current.add(control);
            height = Math.max(height, control.measureSize(font, new Size(width, ROW_HEIGHT)).height());
        }
        if (!current.isEmpty()) rows.add(new Row(List.copyOf(current), height));
        return new Arrangement(false, rows, titleRowHeight(font) + rows.stream().mapToInt(Row::height).sum());
    }

    @Override public int measureWidth(Font font, int available) { return Math.max(minimumWidth(), available); }
    @Override public int measureHeight(Font font, int available) {
        return Math.max(minimumHeight(), arrange(font, available).height());
    }
    @Override public Size measureSize(Font font, Size available) {
        return new Size(measureWidth(font, available.width()), measureHeight(font, available.width()));
    }

    @Override public void layout(Rect bounds, Font font) {
        super.layout(bounds, font);
        int width = Math.max(0, bounds.width() - PADDING * 2);
        Arrangement arrangement = arrange(font, bounds.width());
        int reserve = 0;
        int y = arrangement.inline() ? 0 : titleRowHeight(font);
        for (Row row : arrangement.rows()) {
            int limit = width;
            if (arrangement.inline() && !row.controls().isEmpty()) {
                int titleWidth = font == null ? title.minimumWidth() : title.measureWidth(font, width);
                limit = Math.max(0, (width - titleWidth) / 2 - GAP);
            }
            int used = layoutRow(row, font, width, limit, y);
            if (arrangement.inline() && !row.controls().isEmpty()) reserve = used + GAP;
            y += row.height();
        }
        fitChild(title, new Rect(PADDING + reserve, 0, Math.max(0, width - 2 * reserve),
                arrangement.inline() ? arrangement.height() : titleRowHeight(font)), font);
    }

    private int layoutRow(Row row, Font font, int width, int limit, int y) {
        int[] widths = row.controls().stream().mapToInt(Control::minimumWidth).toArray();
        int used = Math.max(0, widths.length - 1) * GAP;
        for (int value : widths) used += value;
        int remaining = Math.max(0, limit - used);
        for (int index = 0; index < widths.length; index++) {
            int growth = Math.min(remaining,
                    Math.max(0, desiredWidth(row.controls().get(index), font, width) - widths[index]));
            widths[index] += growth;
            remaining -= growth;
            used += growth;
        }
        int x = PADDING + Math.max(0, width - used);
        for (int index = 0; index < widths.length; index++) {
            Control control = row.controls().get(index);
            int height = Math.max(control.minimumHeight(), control.measureSize(font,
                    new Size(widths[index], row.height())).height());
            fitChild(control, new Rect(x, y + (row.height() - height) / 2, widths[index], height), font);
            x += widths[index] + GAP;
        }
        return used;
    }

    @Override protected void render(ModularGuiContext context, int screenX, int screenY) {
        if (background == null) background = new Panel(OreTheme.HEADER_PANEL);
        background.drawAt(context, screenBounds(screenX, screenY));
    }
}
