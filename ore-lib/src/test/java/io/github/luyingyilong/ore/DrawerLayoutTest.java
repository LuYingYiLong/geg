package io.github.luyingyilong.ore;

import brachy.modularui.screen.viewport.ModularGuiContext;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.Overlay;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.layout.Size;
import io.github.luyingyilong.ore.layout.Window;
import io.github.luyingyilong.ore.theme.OreIcons;
import io.github.luyingyilong.ore.theme.OreSounds;
import io.github.luyingyilong.ore.theme.OreTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
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
        check((drawer.maskColor() >>> 24) == 64);
        now[0] = 200L;
        window.layoutViewport(200, 120, font);
        check(drawer.panelBounds().equals(new Rect(100, 0, 100, 120)));
        check(!drawer.animating() && drawer.maskColor() == OreTheme.OVERLAY_COLOR);
        check(inside.absoluteBounds().x() == 104);
        check(drawer.bodyScroll().maxScrollY() > 0);
        Rect hit = inside.absoluteBounds();
        check(io.github.luyingyilong.ore.event.WindowInputs.press(window,hit.x() + 1, hit.y() + 1, null));
        check(clicked[0] == 0);
        check(io.github.luyingyilong.ore.event.WindowInputs.release(window,hit.x() + 1, hit.y() + 1));
        check(clicked[0] == 1);

        check(io.github.luyingyilong.ore.event.WindowInputs.press(window,5, 5, null));
        check(!drawer.isOpen() && drawer.visible() && drawer.animating());
        now[0] = 300L;
        window.layoutViewport(200, 120, font);
        check(drawer.panelBounds().x() == 150);
        check((drawer.maskColor() >>> 24) == 64);
        check(io.github.luyingyilong.ore.event.WindowInputs.press(window,5, 5, null));
        check(closed[0] == 0);
        now[0] = 400L;
        window.layoutViewport(200, 120, font);
        check(!drawer.visible() && closed[0] == 1);
        check(drawer.maskColor() == 0);
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
        check(drawer.maskColor() == OreTheme.OVERLAY_COLOR && closed[0] == 1);
        check(io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ESCAPE, 0, null));
        io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_ESCAPE,0,null);
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
        transitionSounds();
        closingCancelsPendingAction(font);
        closeButtonRelease(font);
        leftPlacement(font);
        optionalHeader(font);
    }

    private static void optionalHeader(Font font) {
        Drawer drawer = new Drawer(Component.literal("Title"), () -> 0L, cue -> { }).animationDuration(0);
        Window window = new Window(true).content(new Control()).overlay(new Overlay().add(drawer));
        drawer.open();
        window.layoutViewport(240, 120, font);
        Control column = drawer.children().get(0).children().get(0);
        Control header = column.children().get(0), body = column.children().get(1);
        Button close = findIconButton(drawer);
        check(drawer.closable() && close.visible() && header.visible());
        int originalBodyTop = body.absoluteBounds().y();
        drawer.closable(false);
        window.layoutViewport(240, 120, font);
        check(!drawer.closable() && !close.visible() && header.visible());
        check(body.absoluteBounds().y() == originalBodyTop);
        drawer.title(Component.empty());
        for (int width : new int[]{127, 128, 129, 640}) {
            window.layoutViewport(width, 120, font);
            check(!header.visible() && body.absoluteBounds().y() == Drawer.PADDING);
            check(body.bounds().height() == 120 - 2 * Drawer.PADDING);
        }
        drawer.closable(true);
        window.layoutViewport(240, 120, font);
        check(header.visible() && close.visible());
        check(close.absoluteBounds().right() == drawer.panelBounds().right() - Drawer.PADDING);
        drawer.title(Component.literal("Title")).closable(false);
        window.layoutViewport(240, 120, font);
        check(header.visible() && !close.visible());
        drawer.title(Component.literal(""));
        window.layoutViewport(240, 120, font);
        check(!header.visible());
        check(io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ESCAPE, 0, null) && !drawer.isOpen());
        io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_ESCAPE,0,null);
        drawer.open();
        window.layoutViewport(240, 120, font);
        check(io.github.luyingyilong.ore.event.WindowInputs.press(window,5, 5, null) && !drawer.isOpen());
    }

    private static void leftPlacement(Font font) {
        long[] now = {0};
        int[] clicked = {0};
        Button inside = new Button(cue -> { }).onPress(() -> clicked[0]++);
        inside.position(0, 0).requestedSize(40, 20);
        Drawer drawer = new Drawer(Component.literal("Left"), () -> now[0], cue -> { })
                .placement(Drawer.Placement.LEFT).content(new Control().preferred(40, 20).addChild(inside));
        Window window = new Window(true).content(new Control()).overlay(new Overlay().add(drawer));
        check(drawer.placement() == Drawer.Placement.LEFT);
        drawer.open();
        window.layoutViewport(200, 120, font);
        check(drawer.panelBounds().equals(new Rect(-100, 0, 100, 120)));
        now[0] = 100;
        window.layoutViewport(200, 120, font);
        check(drawer.panelBounds().x() == -50 && (drawer.maskColor() >>> 24) == 64);
        now[0] = 200;
        window.layoutViewport(200, 120, font);
        check(drawer.panelBounds().equals(new Rect(0, 0, 100, 120)));
        Rect hit = inside.absoluteBounds();
        check(hit.x() == Drawer.PADDING);
        check(io.github.luyingyilong.ore.event.WindowInputs.press(window,hit.x() + 1, hit.y() + 1, null));
        check(io.github.luyingyilong.ore.event.WindowInputs.release(window,hit.x() + 1, hit.y() + 1) && clicked[0] == 1);
        check(io.github.luyingyilong.ore.event.WindowInputs.press(window,180, 10, null) && !drawer.isOpen());
        now[0] = 300;
        window.layoutViewport(200, 120, font);
        check(drawer.panelBounds().x() == -50);
        drawer.open();
        window.layoutViewport(200, 120, font);
        check(drawer.panelBounds().x() == -50);
        now[0] = 500;
        drawer.animationDuration(0);
        for (int width : new int[]{127, 128, 129, 640}) {
            window.layoutViewport(width, 120, font);
            check(drawer.panelBounds().equals(new Rect(0, 0, width / 2, 120)));
        }
        drawer.placement(Drawer.Placement.RIGHT);
        window.layoutViewport(640, 120, font);
        check(drawer.panelBounds().right() == 640);
        drawer.placement(Drawer.Placement.LEFT).width(1000);
        window.layoutViewport(127, 120, font);
        check(drawer.panelBounds().equals(new Rect(0, 0, 127, 120)));
        drawer.close();
        check(!drawer.visible());
    }

    private static void closeButtonRelease(Font font) {
        for (int width : new int[]{127, 128, 129, 640}) {
            int[] closed = {0};
            int[] closeSounds = {0};
            Drawer drawer = new Drawer(Component.literal("Close"), () -> 0L, cue -> {
                if (cue == OreSounds.Cue.DRAWER_CLOSE) closeSounds[0]++;
            }).animationDuration(0).onClose(() -> closed[0]++);
            Window window = new Window(true).content(new Control()).overlay(new Overlay().add(drawer));
            drawer.open();
            window.layoutViewport(width, 150, font);
            Button close = findIconButton(drawer);
            check(close != null && close.type() == Button.Type.TEXT);
            check(close.actionMode() == Button.ACTION_MODE.BUTTON_RELEASE && close.focusExpansion() == 1);
            check(close.icon().equals(new Button.Icon(OreIcons.CLOSE, 7, 7)));
            Rect bounds = close.absoluteBounds();
            check(bounds.width() == 20 && bounds.height() == 20);
            check(bounds.width() == 20 && bounds.height() == 20);
            int x = bounds.x() + 1;
            int y = bounds.y() + 1; // The blank padding also belongs to the button.
            check(io.github.luyingyilong.ore.event.WindowInputs.press(window,x, y, null) && closed[0] == 0);
            check(io.github.luyingyilong.ore.event.WindowInputs.release(window,bounds.x() - 1, y) && closed[0] == 0);
            check(io.github.luyingyilong.ore.event.WindowInputs.press(window,x, y, null) && closed[0] == 0);
            check(io.github.luyingyilong.ore.event.WindowInputs.release(window,x, y) && closed[0] == 1);
            check(!io.github.luyingyilong.ore.event.WindowInputs.release(window,x, y) && closed[0] == 1);

            drawer.open();
            window.layoutViewport(width, 150, font);
            window.focusManager().requestFocus(close, null);
            check(io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ENTER, 0, null) && closed[0] == 1);
            check(io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ENTER, 0, null) && closed[0] == 1);
            check(io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_ENTER, 0, null) && closed[0] == 2);
            check(closeSounds[0] == 2);
        }
    }

    private static Button findIconButton(Control node) {
        if (node instanceof Button button && button.icon() != null) return button;
        for (Control child : node.children()) {
            Button found = findIconButton(child);
            if (found != null) return found;
        }
        return null;
    }

    private static void closingCancelsPendingAction(Font font) {
        long[] now = {0};
        int[] actions = {0};
        Button button = new Button(cue -> { }).onPress(() -> actions[0]++);
        button.position(0, 0).requestedSize(40, 20);
        Drawer drawer = new Drawer(Component.literal("Pending"), () -> now[0], cue -> { })
                .content(new Control().preferred(40, 20).addChild(button));
        Window window = new Window(true).content(new Control()).overlay(new Overlay().add(drawer));
        drawer.open();
        now[0] = 200;
        window.layoutViewport(200, 120, font);
        window.focusManager().requestFocus(button, null);
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ENTER, 0, null);
        drawer.close();
        io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_ENTER, 0, null);
        check(drawer.visible() && !drawer.isOpen() && actions[0] == 0);
    }

    private static void transitionSounds() {
        long[] now = {0};
        List<OreSounds.Cue> sounds = new ArrayList<>();
        Drawer drawer = new Drawer(Component.literal("Sound"), () -> now[0], sounds::add);
        drawer.close();
        check(sounds.isEmpty());
        drawer.open().open();
        check(sounds.equals(List.of(OreSounds.Cue.DRAWER_OPEN)));
        now[0] = 50;
        drawer.close().close();
        check(sounds.equals(List.of(OreSounds.Cue.DRAWER_OPEN, OreSounds.Cue.DRAWER_CLOSE)));
        drawer.open(); // Reversing a transition is a new opening, not a second frame sound.
        check(sounds.size() == 3 && sounds.get(2) == OreSounds.Cue.DRAWER_OPEN);
        now[0] = 1000;
        drawer.open();
        check(sounds.size() == 3);
        drawer.animationDuration(0).close().close();
        check(sounds.size() == 4 && sounds.get(3) == OreSounds.Cue.DRAWER_CLOSE);
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
            check(row != null && io.github.luyingyilong.ore.event.WindowInputs.press(window,x, y, null));
            check(io.github.luyingyilong.ore.event.WindowInputs.release(window,x, y));
            check(selected[0] == row.item());
        }
        window.focusManager().requestFocus(table, null);
        popup.visible(true);
        window.layoutViewport(640, 180, font);
        check(window.focusManager().focused() == approve);
        check(io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ESCAPE, 0, null));
        io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_ESCAPE,0,null);
        check(!popup.visible() && drawer.isOpen());
        check(window.focusManager().focused() == table);
        check(io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ESCAPE, 0, null));
        io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_ESCAPE,0,null);
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
        Navbar header = new Navbar(Component.literal("Font")).fontSize(40.0F);
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
