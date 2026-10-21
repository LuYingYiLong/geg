package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.theme.ThemeInsets;
import io.github.luyingyilong.ore.theme.ThemePadding;
import io.github.luyingyilong.ore.theme.IconTheme;
import io.github.luyingyilong.ore.theme.ThemeDB;
import io.github.luyingyilong.ore.theme.ThemeKey;

import io.github.luyingyilong.ore.render.*;

import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.screen.OreScreen;
import io.github.luyingyilong.ore.theme.OreTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * A centered navigation title, optional back action, and right-aligned actions that wrap.
 */
public final class Navbar extends Container {
    private static final ThemePadding PADDING = ThemePadding.of("Navbar", "", 0, 0, 3, 0);
    public static final ThemeKey<Integer> PADDING_TOP = PADDING.top();
    public static final ThemeKey<Integer> PADDING_RIGHT = PADDING.right();
    public static final ThemeKey<Integer> PADDING_BOTTOM = PADDING.bottom();
    public static final ThemeKey<Integer> PADDING_LEFT = PADDING.left();


    public static final ThemeKey<TextureRegion> BACK_ICON = IconTheme.item("Navbar", "back_icon", IconTheme.ARROW_LEFT);

    public static void registerTheme(ThemeDB db) {
        OreTheme.registerType(db, "Navbar");
        PADDING.register(db);
        IconTheme.registerTheme(db);
        db.register(BACK_ICON);
        db.registerFallback(BACK_ICON, IconTheme.ARROW_LEFT);
    }

    public static final int ROW_HEIGHT = 24;
    private static final int GAP = 6;
    private static final int BACK_BUTTON_SIZE = 22;
    private final Label title = new Label().setThemeTypeVariation("NavbarTitle").setOpticalCenter(true).setOverflow(Label.Overflow.ELLIPSIS)
            .setAlignment(Label.Horizontal.CENTER, Label.Vertical.CENTER);
    private final Button back = new Button().setThemeTypeVariation("TextButton").setIcon(TextureSource.themed(BACK_ICON));
    private final List<Control> extra = new ArrayList<>();
    private Panel background;
    private Runnable backAction = OreScreen::back;
    public Navbar(Component title) {
        this.title.setText(title);
        this.title.setThemeParent(this);
        back.pressed().connect(this, () -> backAction.run());
        back.setMinimum(BACK_BUTTON_SIZE, BACK_BUTTON_SIZE);
        back.setThemeParent(this);
        back.setSizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_BEGIN).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
        updateChildren(List.of(this.title, back));
        setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
    }

    public Component getTitle() {
        return title.getText();
    }

    public Navbar setTitle(Component text) {
        title.setText(text);
        return this;
    }

    public boolean getShowBackButton() {
        return back.getVisible();
    }

    public Navbar setShowBackButton(boolean show) {
        if (!show) back.cancelInteraction();
        back.setVisible(show);
        return this;
    }

    public Navbar onBack(Runnable action) {
        backAction = java.util.Objects.requireNonNull(action);
        return this;
    }


    @Override
    protected void validateChildren(List<Control> proposed) {
        if (!isUpdatingChildren() && (proposed.size() < 2 || proposed.get(0) != title || proposed.get(1) != back
                || proposed.stream().anyMatch(child -> child.getParent() != this)))
            throw new IllegalStateException("Navbar has a fixed skeleton; use setExtra");
    }

    @Override
    protected void childrenChanged() {
        extra.clear();
        for (Control child : getChildren()) if (child != title && child != back) extra.add(child);
    }

    /**
     * Replaces right-side actions atomically, preserving retained controls.
     */
    public Navbar setExtra(Control... controls) {
        List<Control> next = new ArrayList<>();
        next.add(title);
        next.add(back);
        next.addAll(List.of(controls));
        updateChildren(next);
        return this;
    }

    private int titleRowHeight(Font font) {
        return font == null ? ROW_HEIGHT : Math.max(ROW_HEIGHT, title.lineHeight(font));
    }

    private int desiredWidth(Control control, Font font, int width) {
        return Math.max(control.getMinimumWidth(), control.measureSize(font, new Size(width, ROW_HEIGHT)).width());
    }

    private int backReserve() {
        return getShowBackButton() ? BACK_BUTTON_SIZE + GAP : 0;
    }

    private Arrangement arrange(Font font, int outerWidth) {
        int width = Math.max(0, outerWidth);
        List<Control> active = extra.stream().filter(c -> c.isAlive() && c.getVisible()).toList();
        int minimum = active.stream().mapToInt(Control::getMinimumWidth).sum()
                + Math.max(0, active.size() - 1) * GAP;
        int titleWidth = font == null ? title.getMinimumWidth() : title.measureWidth(font, width);
        if (active.isEmpty() || titleWidth + 2 * Math.max(backReserve(), minimum + GAP) <= width) {
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
            int next = control.getMinimumWidth() + (current.isEmpty() ? 0 : GAP);
            if (!current.isEmpty() && used + next > width) {
                rows.add(new Row(List.copyOf(current), height));
                current.clear();
                used = 0;
                height = ROW_HEIGHT;
            }
            used += control.getMinimumWidth() + (current.isEmpty() ? 0 : GAP);
            current.add(control);
            height = Math.max(height, control.measureSize(font, new Size(width, ROW_HEIGHT)).height());
        }
        if (!current.isEmpty()) rows.add(new Row(List.copyOf(current), height));
        return new Arrangement(false, rows, titleRowHeight(font) + rows.stream().mapToInt(Row::height).sum());
    }

    @Override
    public int measureWidth(Font font, int available) {
        if (!isAlive()) return 0;
        return Math.max(getMinimumWidth(), available);
    }

    @Override
    public int measureHeight(Font font, int available) {
        if (!isAlive()) return 0;
        var insets = contentInsets();
        return Math.max(getMinimumHeight(), arrange(font, Math.max(0, available - insets.getHorizontal())).height() + insets.getVertical());
    }

    @Override
    public Size measureSize(Font font, Size available) {
        if (!isAlive()) return new Size(0, 0);
        return new Size(measureWidth(font, available.width()), measureHeight(font, available.width()));
    }

    @Override
    public void layout(Rect bounds, Font font) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            super.layout(bounds, font);
            var margin = contentInsets();
            int width = Math.max(0, bounds.width() - margin.getHorizontal());
            Arrangement arrangement = arrange(font, width);
            int reserve = Math.min(backReserve(), width / 2);
            int y = arrangement.inline() ? 0 : titleRowHeight(font);
            for (Row row : arrangement.rows()) {
                int limit = width;
                if (arrangement.inline() && !row.controls().isEmpty()) {
                    int titleWidth = font == null ? title.getMinimumWidth() : title.measureWidth(font, width);
                    limit = Math.max(0, (width - titleWidth) / 2 - GAP);
                }
                int used = layoutRow(row, font, width, limit, margin.left(), y + margin.top());
                if (arrangement.inline() && !row.controls().isEmpty()) reserve = Math.max(reserve, used + GAP);
                y += row.height();
            }
            int titleHeight = arrangement.inline() ? arrangement.height() : titleRowHeight(font);
            fitChild(title, new Rect(margin.left() + reserve, margin.top(), Math.max(0, width - 2 * reserve), titleHeight), font);
            if (getShowBackButton())
                fitChild(back, new Rect(margin.left(), margin.top(), BACK_BUTTON_SIZE, titleHeight), font);
        }
    }

    private int layoutRow(Row row, Font font, int width, int limit, int left, int y) {
        int[] widths = row.controls().stream().mapToInt(Control::getMinimumWidth).toArray();
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
        int x = left + Math.max(0, width - used);
        for (int index = 0; index < widths.length; index++) {
            Control control = row.controls().get(index);
            int height = Math.max(control.getMinimumHeight(), control.measureSize(font,
                    new Size(widths[index], row.height())).height());
            fitChild(control, new Rect(x, y + (row.height() - height) / 2, widths[index], height), font);
            x += widths[index] + GAP;
        }
        return used;
    }

    @Override
    protected void render(DrawContext context, int screenX, int screenY) {
        if (background == null) background = ownPart("background", new Panel(OreTheme.HEADER_PANEL));
        Rect area = getScreenBounds(screenX, screenY);
        background.drawAt(context, new Rect(area.x(), area.y(), area.width(), Math.max(0, area.height() - getThemeItem(PADDING_BOTTOM))));
    }

    private ThemeInsets contentInsets() {
        var padding = PADDING.getInsets(this);
        var margin = getThemeItem(OreTheme.HEADER_PANEL).getContentMargin();
        return new ThemeInsets(padding.left() + margin.left(), padding.top() + margin.top(),
                padding.right() + margin.right(), padding.bottom() + margin.bottom());
    }

    private record Row(List<Control> controls, int height) {
    }

    private record Arrangement(boolean inline, List<Row> rows, int height) {
    }
}
