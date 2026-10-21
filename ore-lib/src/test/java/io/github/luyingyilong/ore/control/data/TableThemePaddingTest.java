package io.github.luyingyilong.ore.control.data;

import io.github.luyingyilong.ore.control.base.ContainerThemePaddingTest;
import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.LifecycleScheduler;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.control.input.Button;
import io.github.luyingyilong.ore.control.layout.Padding;
import io.github.luyingyilong.ore.theme.ThemeDB;
import io.github.luyingyilong.ore.window.Window;

import io.github.luyingyilong.ore.event.WindowInputs;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

import java.util.*;

/** Cell and compact-row padding must share the same table owner for measuring, drawing and input. */
public final class TableThemePaddingTest {
    private static final Font FONT = new Font(id -> null, false) {
        @Override public int width(FormattedText text) { return text.getString().length() * 6; }
        @Override public int width(String text) { return text.length() * 6; }
        @Override public int width(net.minecraft.util.FormattedCharSequence text) {
            int[] count = {0};text.accept((index, style, codepoint) -> { count[0]++;return true; });return count[0] * 6;
        }
    };

    public static void main(String[] args) {
        ThemeDB db = ThemeDB.getDefault();
        db.reload(io.github.luyingyilong.ore.support.ThemeTestResources.resources(Map.of()));
        cells();
        compact();
        identity();
        System.out.println("ORE_TABLE_THEME_PADDING_PASS");
    }

    private static void cells() {
        Table.TextCell<Integer> text = new Table.TextCell<>(value -> Component.literal("Name " + value));
        Table<Integer> table = new Table<Integer>().setRowKey(value -> value)
                .addColumn(new Table.Column<>("name", Component.literal("Name"), 180, 180, 1, width -> 23, text));
        table.setRows(java.util.stream.IntStream.range(0, 50).boxed().toList());
        Window window = Window.embedded().setContent(table);
        try {
            window.layoutViewport(300, 160, FONT);
            Padding firstCell = (Padding) table.getChild(0).getChild(0);
            Control firstLabel = firstCell.getChild(0);
            check(firstCell.getInsets().equals(new Padding.Insets(2, 4, 2, 4)), "Text cell defaults stay unchanged");
            table.setThemeOverride(Table.CELL_PADDING_TOP, 10).setThemeOverride(Table.CELL_PADDING_RIGHT, 9)
                    .setThemeOverride(Table.CELL_PADDING_BOTTOM, 12).setThemeOverride(Table.CELL_PADDING_LEFT, 7);
            window.layoutViewport(300, 160, FONT);
            check(table.getChild(0).getChild(0) == firstCell && firstCell.getChild(0) == firstLabel, "Live cell padding reuses row instances");
            check(firstCell.getInsets().equals(new Padding.Insets(10, 9, 12, 7)), "Cell binds using Table local overrides");
            check(firstLabel.getBounds().equals(new Rect(7, 10, firstCell.getBounds().width() - 16, firstCell.getBounds().height() - 22)),
                    "Cell uses the same non-symmetric insets as measurement");
            int height = table.getLayoutRows().get(0).bounds().height();
            check(height == text.measureHeight(table, FONT, 0, 300), "Row height recalculates after padding changes");
            table.setScroll(height * 8 + 3);
            Object anchor = table.getVisibleRows().get(0).key();
            table.setThemeOverride(Table.HEADER_PADDING_TOP, 10).setThemeOverride(Table.HEADER_PADDING_BOTTOM, 12);
            window.layoutViewport(300, 160, FONT);
            check(table.getHeaderHeight() >= 32 && table.getVisibleRows().get(0).key().equals(anchor), "Changing header padding retains first visible row");
            table.setRows(java.util.stream.IntStream.range(0, 50).boxed().toList());
            window.layoutViewport(300, 160, FONT);
            for (Control row : table.getChildren()) {
                Padding cell = (Padding) row.getChild(0);
                check(cell.getInsets().equals(new Padding.Insets(10, 9, 12, 7)), "Recycled cells retain effective theme spacing");
            }
            table.removeThemeOverride(Table.CELL_PADDING_LEFT);
            window.layoutViewport(300, 160, FONT);
            check(((Padding) table.getChild(0).getChild(0)).getInsets().left() == 4, "Restoring cell padding refreshes retained rows");
        } finally { window.queueFree();LifecycleScheduler.flush(); }
    }

    private static void compact() {
        List<Button> buttons = new ArrayList<>();int[] activated = {0};
        Table<Integer> table = new Table<Integer>().setRowKey(value -> value)
                .addColumn(new Table.Column<>("action", Component.literal("Action"), 200, 200, 1, width -> Button.HEIGHT,
                        new Table.ControlCell<>(() -> {
                            Button button = io.github.luyingyilong.ore.control.input.ButtonTestAccess.create(cue -> { }).setLabel(Component.literal("Act"));
                            button.pressed().connect(button, () -> activated[0]++);buttons.add(button);return button;
                        }, (button, row) -> { })));
        table.setRows(List.of(1));
        table.setThemeOverride(Table.COMPACT_PADDING_TOP, 3).setThemeOverride(Table.COMPACT_PADDING_RIGHT, 11)
                .setThemeOverride(Table.COMPACT_PADDING_BOTTOM, 7).setThemeOverride(Table.COMPACT_PADDING_LEFT, 9);
        Window window = Window.embedded().setContent(table);
        try {
            for (int width : new int[]{127, 128, 129}) {
                window.layoutViewport(width, 180, FONT);
                check(table.getCompact() && table.getHeaderHeight() == 0, "Compact mode hides header");
                Button button = buttons.get(0);
                check(button.getBounds().equals(new Rect(9, 13, width - 20, Button.HEIGHT)), "Compact row uses Table padding on both axes");
                check(table.getContentHeight() == Button.HEIGHT + 20, "Compact measurement includes top/bottom padding once");
                Rect area = button.getAbsoluteBounds();
                WindowInputs.press(window, area.x() + 4, area.y() + 4, null);
                window.layoutViewport(width, 180, FONT);
                WindowInputs.release(window, area.x() + 4, area.y() + 4);
            }
            check(activated[0] == 3 && buttons.size() == 1, "Padded compact button survives repeated layout and activates once");
        } finally { window.queueFree();LifecycleScheduler.flush(); }
    }

    private static void identity() {
        PlayerIdentityCell<String> cell = new PlayerIdentityCell<>(name -> null, name -> name);
        Table<String> table = new Table<String>().setRowKey(value -> value)
                .addColumn(new Table.Column<>("player", Component.literal("Player"), 100, 100, 1, width -> 23, cell));
        table.setRows(List.of("Player"));
        table.setThemeOverride(Table.CELL_PADDING_TOP, 5).setThemeOverride(Table.CELL_PADDING_RIGHT, 11)
                .setThemeOverride(Table.CELL_PADDING_BOTTOM, 7).setThemeOverride(Table.CELL_PADDING_LEFT, 9);
        Window window = Window.embedded().setContent(table);
        try {
            window.layoutViewport(240, 180, FONT);
            check(cell.measureHeight(table, null, "Player", 240) == 28, "Avatar cell shares Table text-cell padding");
            Control identity = table.getChild(0).getChild(0);
            Padding padding = (Padding) identity.getChild(0);
            check(padding.getInsets().equals(new Padding.Insets(5, 11, 7, 9)), "Avatar/name retained content uses the same theme owner");
            Control row = padding.getChild(0);
            check(row.getBounds().x() == 9 && row.getBounds().y() == 5, "Identity content respects non-symmetric padding");
            check(row.getChild(0).getBounds().y() * 2 + row.getChild(0).getBounds().height() == row.getBounds().height(), "Avatar remains vertically centered");
        } finally { window.queueFree();LifecycleScheduler.flush(); }
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
