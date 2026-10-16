package io.github.luyingyilong.geg.client.ui;

import io.github.luyingyilong.geg.client.ui.layout.Control;
import io.github.luyingyilong.geg.client.ui.layout.Overlay;
import io.github.luyingyilong.geg.client.ui.layout.Rect;
import io.github.luyingyilong.geg.client.ui.layout.Size;
import io.github.luyingyilong.geg.client.ui.layout.Window;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.lwjgl.glfw.GLFW;
import brachy.modularui.screen.viewport.ModularGuiContext;

import java.util.stream.IntStream;

/** Standalone checks for the animated panel's actual layout, mask and input isolation. */
public final class DrawerLayoutTest {
    private DrawerLayoutTest() { }

    public static void main(String[] args) {
        Font font = new Font(id -> null, false) {
            @Override public int width(FormattedText text) { return text.getString().length() * 6; }
        };
        long[] now = {0L};
        int[] closed = {0};
        int[] clicked = {0};
        Button outside = new Button(Component.literal("Outside"));
        outside.position(4, 4).requestedSize(60, 20);
        Control page = new Control().addChild(outside);
        Overlay overlay = new Overlay();
        Window window = new Window(true).content(page).overlay(overlay);
        Button inside = new Button(Component.literal("Inside")).onPress(() -> clicked[0]++);
        inside.position(0, 0).requestedSize(60, 20);
        Control content = new Control().preferred(60, 300).addChild(inside);
        Drawer drawer = new Drawer(Component.literal("Drawer"), () -> now[0])
                .content(content).onClose(() -> closed[0]++);
        overlay.add(drawer);
        window.layoutViewport(200, 120, font);
        check(!drawer.visible() && !drawer.isOpen());
        window.focusManager().requestFocus(outside, null);

        drawer.open();
        window.layoutViewport(200, 120, font);
        check(drawer.visible() && drawer.animating());
        check(drawer.panelBounds().equals(new Rect(200, 0, 100, 120)));
        check(drawer.maskColor() == 0);
        check(!window.focusManager().allowsNativeInput(outside));
        check(window.focusManager().allowsNativeInput(inside));
        now[0] = 100L;
        window.layoutViewport(200, 120, font);
        check(drawer.panelBounds().x() == 150);
        check((drawer.maskColor() >>> 24) == 90);
        now[0] = 200L;
        window.layoutViewport(200, 120, font);
        check(drawer.panelBounds().equals(new Rect(100, 0, 100, 120)));
        check(!drawer.animating() && drawer.maskColor() == Drawer.MASK_COLOR);
        check(inside.absoluteBounds().x() == 104);
        check(drawer.bodyScroll().maxScrollY() > 0);
        Rect hit = inside.absoluteBounds();
        check(window.pointerPressed(hit.x() + 1, hit.y() + 1, null));
        check(clicked[0] == 1);

        check(window.pointerPressed(5, 5, null));
        check(!drawer.isOpen() && drawer.visible() && drawer.animating());
        now[0] = 300L;
        window.layoutViewport(200, 120, font);
        check(drawer.panelBounds().x() == 150);
        check(window.pointerPressed(5, 5, null));
        check(closed[0] == 0);
        now[0] = 400L;
        window.layoutViewport(200, 120, font);
        check(!drawer.visible() && closed[0] == 1);
        check(window.focusManager().focused() == outside);
        check(window.focusManager().allowsNativeInput(outside));

        drawer.open();
        now[0] = 450L;
        window.layoutViewport(200, 120, font);
        int before = drawer.panelBounds().x();
        drawer.close();
        window.layoutViewport(200, 120, font);
        check(drawer.panelBounds().x() == before);
        now[0] = 460L;
        window.layoutViewport(200, 120, font);
        before = drawer.panelBounds().x();
        drawer.open();
        window.layoutViewport(200, 120, font);
        check(drawer.panelBounds().x() == before);
        now[0] = 700L;
        window.layoutViewport(200, 120, font);
        check(drawer.maskColor() == Drawer.MASK_COLOR && closed[0] == 1);
        check(window.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, null));
        check(!drawer.isOpen());
        now[0] = 900L;
        window.layoutViewport(200, 120, font);
        check(!drawer.visible() && closed[0] == 2);

        drawer.animationDuration(0).open();
        for (int width : new int[]{127, 128, 129, 640}) {
            window.layoutViewport(width, 120, font);
            check(drawer.panelBounds().width() == width / 2);
            check(drawer.panelBounds().right() == width);
        }
        drawer.width(1000).action(Component.literal("Apply"), () -> { });
        for (int width : new int[]{127, 128, 129, 640}) {
            window.layoutViewport(width, 120, font);
            check(drawer.panelBounds().equals(new Rect(0, 0, width, 120)));
            Control frame = drawer.children().get(0);
            Control column = frame.children().get(0);
            check(column.absoluteBounds().x() == Drawer.PADDING);
            check(column.bounds().width() == width - Drawer.PADDING * 2);
            Control footer = column.children().get(2);
            Control action = footer.children().get(0);
            check(action.bounds().width() == width - Drawer.PADDING * 2);
            check(action.absoluteBounds().bottom() == 120 - Drawer.PADDING);
        }
        drawer.close().close();
        check(!drawer.visible() && closed[0] == 3);
        fontSizesFollowLabels(font);
        tableAndNestedOverlay(font);
    }

    private static void tableAndNestedOverlay(Font font) {
        int[] selected = {-1};
        Table<Integer> table = new Table<Integer>().rowKey(row -> row)
                .column(new Table.Column<>("member", Component.literal("Member"),
                        48, 100, 1, width -> 20, new Table.Cell<>() {
                    @Override public void draw(ModularGuiContext context, Integer row, Rect bounds) { }
                })).onRowClick(row -> selected[0] = row);
        table.minimum(0, 60);
        table.rows(IntStream.range(0, 300).boxed().toList());
        Drawer drawer = new Drawer(Component.literal("Organization")).animationDuration(0)
                .content(table).action(Component.literal("Applications"), () -> { });
        Control popup = new Control() {
            @Override public boolean focusScope() { return true; }
            @Override public boolean onFocusKey(int keyCode, int modifiers) {
                if (keyCode != GLFW.GLFW_KEY_ESCAPE) return false;
                visible(false);
                return true;
            }
        };
        Button approve = new Button(Component.literal("Approve"));
        approve.position(20, 20).requestedSize(60, 20);
        popup.addChild(approve);
        popup.visible(false);
        Window window = new Window(true).content(new Control())
                .overlay(new Overlay().add(drawer).add(popup));
        drawer.open();
        for (int width : new int[]{127, 128, 129, 640}) {
            window.layoutViewport(width, 180, font);
            check(drawer.panelBounds().right() == width && drawer.panelBounds().width() == width / 2);
            check(table.absoluteBounds().x() == drawer.panelBounds().x() + Drawer.PADDING);
            table.setScroll(46);
            check(table.activeViews() < 300);
            int x = table.absoluteBounds().x() + 1;
            int y = table.absoluteBounds().y() + table.headerHeight() + 1;
            Table.Row<Integer> row = table.hitRow(x, y);
            check(row != null && window.pointerPressed(x, y, null));
            check(selected[0] == row.item());
        }
        window.focusManager().requestFocus(table, null);
        popup.visible(true);
        window.layoutViewport(640, 180, font);
        check(window.focusManager().focused() == approve);
        check(window.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, null));
        check(!popup.visible() && drawer.isOpen());
        check(window.focusManager().focused() == table);
        check(window.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, null));
        check(!drawer.isOpen() && !drawer.visible());
    }

    private static void fontSizesFollowLabels(Font font) {
        Button button = new Button(Component.literal("Font"));
        int originalWidth = button.measureWidth(font, 200);
        Control parent = new Control().addChild(button);
        parent.layout(new Rect(0, 0, 200, 100), font);
        button.fontSize(16.0F);
        check(button.fontSize() == 16.0F && parent.layoutDirty());
        check(button.measureWidth(font, 200) == 2 * originalWidth - 2 * Button.PADDING_X);
        Header header = new Header(Component.literal("Font")).fontSize(40.0F);
        check(header.fontSize() == 40.0F);
        int height = header.measureSize(font, new Size(200, 100)).height();
        header.layout(new Rect(0, 0, 200, height), font);
        Label title = (Label) header.children().get(0);
        check(title.fontSize() == 40.0F && title.bounds().height() >= title.lineHeight(font));
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Unexpected drawer layout or transition");
    }
}
