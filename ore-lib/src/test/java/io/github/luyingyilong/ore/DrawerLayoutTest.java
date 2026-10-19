package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.signal.Signals;

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
        outside.setPosition(4, 4).setRequestedSize(60, 20);
        Control page = new Control().addChild(outside);
        Overlay overlay = new Overlay();
        Window window = new Window(true).setContent(page).setOverlay(overlay);
        Button inside = Signals.configure(new Button(Component.literal("Inside")), connected -> connected.pressed().connect(connected, () -> clicked[0]++));
        inside.setPosition(0, 0).setRequestedSize(60, 20);
        Control content = new Control().setPreferred(60, 300).addChild(inside);
        Drawer drawer = Signals.configure(new Drawer(Component.literal("Drawer"), () -> now[0])
                .setContent(content), connected -> connected.closed().connect(connected, () -> closed[0]++));
        overlay.add(drawer);
        window.layoutViewport(200, 120, font);
        check(!drawer.getVisible() && !drawer.isOpen());
        window.getFocusManager().requestFocus(outside, null);

        drawer.open();
        window.layoutViewport(200, 120, font);
        check(drawer.getVisible() && drawer.getAnimating());
        check(drawer.getPanelBounds().equals(new Rect(200, 0, 100, 120)));
        check(drawer.maskColor() == 0);
        check(!window.getFocusManager().allowsNativeInput(outside));
        check(window.getFocusManager().allowsNativeInput(inside));
        now[0] = 100L;
        window.layoutViewport(200, 120, font);
        check(drawer.getPanelBounds().x() == 150);
        check((drawer.maskColor() >>> 24) == 64);
        now[0] = 200L;
        window.layoutViewport(200, 120, font);
        check(drawer.getPanelBounds().equals(new Rect(100, 0, 100, 120)));
        check(!drawer.getAnimating() && drawer.maskColor() == OreTheme.OVERLAY_COLOR);
        check(inside.getAbsoluteBounds().x() == 104);
        check(drawer.getBodyScroll().getMaxScrollY() > 0);
        Rect hit = inside.getAbsoluteBounds();
        check(io.github.luyingyilong.ore.event.WindowInputs.press(window,hit.x() + 1, hit.y() + 1, null));
        check(clicked[0] == 0);
        check(io.github.luyingyilong.ore.event.WindowInputs.release(window,hit.x() + 1, hit.y() + 1));
        check(clicked[0] == 1);

        check(io.github.luyingyilong.ore.event.WindowInputs.press(window,5, 5, null));
        check(!drawer.isOpen() && drawer.getVisible() && drawer.getAnimating());
        now[0] = 300L;
        window.layoutViewport(200, 120, font);
        check(drawer.getPanelBounds().x() == 150);
        check((drawer.maskColor() >>> 24) == 64);
        check(io.github.luyingyilong.ore.event.WindowInputs.press(window,5, 5, null));
        check(closed[0] == 0);
        now[0] = 400L;
        window.layoutViewport(200, 120, font);
        check(!drawer.getVisible() && closed[0] == 1);
        check(drawer.maskColor() == 0);
        check(window.getFocusManager().getFocused() == outside);
        check(window.getFocusManager().allowsNativeInput(outside));

        drawer.open();
        now[0] = 450L;
        window.layoutViewport(200, 120, font);
        int before = drawer.getPanelBounds().x();
        drawer.close();
        window.layoutViewport(200, 120, font);
        check(drawer.getPanelBounds().x() == before);
        now[0] = 460L;
        window.layoutViewport(200, 120, font);
        before = drawer.getPanelBounds().x();
        drawer.open();
        window.layoutViewport(200, 120, font);
        check(drawer.getPanelBounds().x() == before);
        now[0] = 700L;
        window.layoutViewport(200, 120, font);
        check(drawer.maskColor() == OreTheme.OVERLAY_COLOR && closed[0] == 1);
        check(io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ESCAPE, 0, null));
        io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_ESCAPE,0,null);
        check(!drawer.isOpen());
        now[0] = 900L;
        window.layoutViewport(200, 120, font);
        check(!drawer.getVisible() && closed[0] == 2);

        drawer.setAnimationDuration(0).open();
        for (int width : new int[]{127, 128, 129, 640}) {
            window.layoutViewport(width, 120, font);
            check(drawer.getPanelBounds().width() == width / 2);
            check(drawer.getPanelBounds().right() == width);
        }
        drawer.setWidth(1000).addAction(Signals.configure(new Button(Component.literal("Apply")).setType(Button.Type.SECONDARY), actionButton -> actionButton.pressed().connect(actionButton, () -> { })));
        for (int width : new int[]{127, 128, 129, 640}) {
            window.layoutViewport(width, 120, font);
            check(drawer.getPanelBounds().equals(new Rect(0, 0, width, 120)));
            Control frame = drawer.getChildren().get(0);
            Control column = frame.getChildren().get(0);
            check(column.getAbsoluteBounds().x() == Drawer.PADDING);
            check(column.getBounds().width() == width - Drawer.PADDING * 2);
            Control footer = column.getChildren().get(2);
            Control action = footer.getChildren().get(0);
            check(action.getBounds().width() == width - Drawer.PADDING * 2);
            check(action.getAbsoluteBounds().bottom() == 120 - Drawer.PADDING);
        }
        drawer.close().close();
        check(!drawer.getVisible() && closed[0] == 3);
        fontSizesFollowLabels(font);
        tableAndNestedOverlay(font);
        transitionSounds();
        closingCancelsPendingAction(font);
        closeButtonRelease(font);
        leftPlacement(font);
        optionalHeader(font);
    }

    private static void optionalHeader(Font font) {
        Drawer drawer = new Drawer(Component.literal("Title"), () -> 0L, cue -> { }).setAnimationDuration(0);
        Window window = new Window(true).setContent(new Control()).setOverlay(new Overlay().add(drawer));
        drawer.open();
        window.layoutViewport(240, 120, font);
        Control column = drawer.getChildren().get(0).getChildren().get(0);
        Control header = column.getChildren().get(0), body = column.getChildren().get(1);
        Button close = findIconButton(drawer);
        check(drawer.getClosable() && close.getVisible() && header.getVisible());
        int originalBodyTop = body.getAbsoluteBounds().y();
        drawer.setClosable(false);
        window.layoutViewport(240, 120, font);
        check(!drawer.getClosable() && !close.getVisible() && header.getVisible());
        check(body.getAbsoluteBounds().y() == originalBodyTop);
        drawer.setTitle(Component.empty());
        for (int width : new int[]{127, 128, 129, 640}) {
            window.layoutViewport(width, 120, font);
            check(!header.getVisible() && body.getAbsoluteBounds().y() == Drawer.PADDING);
            check(body.getBounds().height() == 120 - 2 * Drawer.PADDING);
        }
        drawer.setClosable(true);
        window.layoutViewport(240, 120, font);
        check(header.getVisible() && close.getVisible());
        check(close.getAbsoluteBounds().right() == drawer.getPanelBounds().right() - Drawer.PADDING);
        drawer.setTitle(Component.literal("Title")).setClosable(false);
        window.layoutViewport(240, 120, font);
        check(header.getVisible() && !close.getVisible());
        drawer.setTitle(Component.literal(""));
        window.layoutViewport(240, 120, font);
        check(!header.getVisible());
        check(io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ESCAPE, 0, null) && !drawer.isOpen());
        io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_ESCAPE,0,null);
        drawer.open();
        window.layoutViewport(240, 120, font);
        check(io.github.luyingyilong.ore.event.WindowInputs.press(window,5, 5, null) && !drawer.isOpen());
    }

    private static void leftPlacement(Font font) {
        long[] now = {0};
        int[] clicked = {0};
        Button inside = Signals.configure(new Button(cue -> { }), connected -> connected.pressed().connect(connected, () -> clicked[0]++));
        inside.setPosition(0, 0).setRequestedSize(40, 20);
        Drawer drawer = new Drawer(Component.literal("Left"), () -> now[0], cue -> { })
                .setPlacement(Drawer.Placement.LEFT).setContent(new Control().setPreferred(40, 20).addChild(inside));
        Window window = new Window(true).setContent(new Control()).setOverlay(new Overlay().add(drawer));
        check(drawer.getPlacement() == Drawer.Placement.LEFT);
        drawer.open();
        window.layoutViewport(200, 120, font);
        check(drawer.getPanelBounds().equals(new Rect(-100, 0, 100, 120)));
        now[0] = 100;
        window.layoutViewport(200, 120, font);
        check(drawer.getPanelBounds().x() == -50 && (drawer.maskColor() >>> 24) == 64);
        now[0] = 200;
        window.layoutViewport(200, 120, font);
        check(drawer.getPanelBounds().equals(new Rect(0, 0, 100, 120)));
        Rect hit = inside.getAbsoluteBounds();
        check(hit.x() == Drawer.PADDING);
        check(io.github.luyingyilong.ore.event.WindowInputs.press(window,hit.x() + 1, hit.y() + 1, null));
        check(io.github.luyingyilong.ore.event.WindowInputs.release(window,hit.x() + 1, hit.y() + 1) && clicked[0] == 1);
        check(io.github.luyingyilong.ore.event.WindowInputs.press(window,180, 10, null) && !drawer.isOpen());
        now[0] = 300;
        window.layoutViewport(200, 120, font);
        check(drawer.getPanelBounds().x() == -50);
        drawer.open();
        window.layoutViewport(200, 120, font);
        check(drawer.getPanelBounds().x() == -50);
        now[0] = 500;
        drawer.setAnimationDuration(0);
        for (int width : new int[]{127, 128, 129, 640}) {
            window.layoutViewport(width, 120, font);
            check(drawer.getPanelBounds().equals(new Rect(0, 0, width / 2, 120)));
        }
        drawer.setPlacement(Drawer.Placement.RIGHT);
        window.layoutViewport(640, 120, font);
        check(drawer.getPanelBounds().right() == 640);
        drawer.setPlacement(Drawer.Placement.LEFT).setWidth(1000);
        window.layoutViewport(127, 120, font);
        check(drawer.getPanelBounds().equals(new Rect(0, 0, 127, 120)));
        drawer.close();
        check(!drawer.getVisible());
    }

    private static void closeButtonRelease(Font font) {
        for (int width : new int[]{127, 128, 129, 640}) {
            int[] closed = {0};
            int[] closeSounds = {0};
            Drawer drawer = Signals.configure(new Drawer(Component.literal("Close"), () -> 0L, cue -> {
                if (cue == OreSounds.Cue.DRAWER_CLOSE) closeSounds[0]++;
            }).setAnimationDuration(0), connected -> connected.closed().connect(connected, () -> closed[0]++));
            Window window = new Window(true).setContent(new Control()).setOverlay(new Overlay().add(drawer));
            drawer.open();
            window.layoutViewport(width, 150, font);
            Button close = findIconButton(drawer);
            check(close != null && close.getType() == Button.Type.TEXT);
            check(close.getActionMode() == Button.ACTION_MODE.BUTTON_RELEASE && close.getFocusExpansion() == 1);
            check(close.getIcon().equals(new Button.Icon(OreIcons.CLOSE, 7, 7)));
            Rect bounds = close.getAbsoluteBounds();
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
            window.getFocusManager().requestFocus(close, null);
            check(io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ENTER, 0, null) && closed[0] == 1);
            check(io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ENTER, 0, null) && closed[0] == 1);
            check(io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_ENTER, 0, null) && closed[0] == 2);
            check(closeSounds[0] == 2);
        }
    }

    private static Button findIconButton(Control node) {
        if (node instanceof Button button && button.getIcon() != null) return button;
        for (Control child : node.getChildren()) {
            Button found = findIconButton(child);
            if (found != null) return found;
        }
        return null;
    }

    private static void closingCancelsPendingAction(Font font) {
        long[] now = {0};
        int[] actions = {0};
        Button button = Signals.configure(new Button(cue -> { }), connected -> connected.pressed().connect(connected, () -> actions[0]++));
        button.setPosition(0, 0).setRequestedSize(40, 20);
        Drawer drawer = new Drawer(Component.literal("Pending"), () -> now[0], cue -> { })
                .setContent(new Control().setPreferred(40, 20).addChild(button));
        Window window = new Window(true).setContent(new Control()).setOverlay(new Overlay().add(drawer));
        drawer.open();
        now[0] = 200;
        window.layoutViewport(200, 120, font);
        window.getFocusManager().requestFocus(button, null);
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ENTER, 0, null);
        drawer.close();
        io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_ENTER, 0, null);
        check(drawer.getVisible() && !drawer.isOpen() && actions[0] == 0);
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
        drawer.setAnimationDuration(0).close().close();
        check(sounds.size() == 4 && sounds.get(3) == OreSounds.Cue.DRAWER_CLOSE);
    }

    private static void tableAndNestedOverlay(Font font) {
        int[] selected = {-1};
        Table<Integer> table = Signals.configure(new Table<Integer>().setRowKey(row -> row)
                .column(new Table.Column<>("member", Component.literal("Member"),
                        48, 100, 1, width -> 20, new Table.Cell<>() {
                    @Override public void draw(ModularGuiContext context, Integer row, Rect bounds) { }
                })), connected -> connected.rowActivated().connect(connected, row -> selected[0] = row));
        table.setMinimum(0, 60);
        table.setRows(IntStream.range(0, 300).boxed().toList());
        Drawer drawer = new Drawer(Component.literal("Organization")).setAnimationDuration(0)
                .setContent(table).addAction(Signals.configure(new Button(Component.literal("Applications")).setType(Button.Type.SECONDARY), actionButton -> actionButton.pressed().connect(actionButton, () -> { })));
        Control popup = new Control() {
            @Override public boolean getFocusScope() { return true; }
            @Override public boolean onFocusKey(int keyCode, int modifiers) {
                if (keyCode != GLFW.GLFW_KEY_ESCAPE) return false;
                setVisible(false);
                return true;
            }
        };
        Button approve = new Button(Component.literal("Approve"));
        approve.setPosition(20, 20).setRequestedSize(60, 20);
        popup.addChild(approve);
        popup.setVisible(false);
        Window window = new Window(true).setContent(new Control())
                .setOverlay(new Overlay().add(drawer).add(popup));
        drawer.open();
        for (int width : new int[]{127, 128, 129, 640}) {
            window.layoutViewport(width, 180, font);
            check(drawer.getPanelBounds().right() == width && drawer.getPanelBounds().width() == width / 2);
            check(table.getAbsoluteBounds().x() == drawer.getPanelBounds().x() + Drawer.PADDING);
            table.setScroll(46);
            check(table.getActiveViews() < 300);
            int x = table.getAbsoluteBounds().x() + 1;
            int y = table.getAbsoluteBounds().y() + table.getHeaderHeight() + 1;
            Table.Row<Integer> row = table.hitRow(x, y);
            check(row != null && io.github.luyingyilong.ore.event.WindowInputs.press(window,x, y, null));
            check(io.github.luyingyilong.ore.event.WindowInputs.release(window,x, y));
            check(selected[0] == row.item());
        }
        window.getFocusManager().requestFocus(table, null);
        popup.setVisible(true);
        window.layoutViewport(640, 180, font);
        check(window.getFocusManager().getFocused() == approve);
        check(io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ESCAPE, 0, null));
        io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_ESCAPE,0,null);
        check(!popup.getVisible() && drawer.isOpen());
        check(window.getFocusManager().getFocused() == table);
        check(io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ESCAPE, 0, null));
        io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_ESCAPE,0,null);
        check(!drawer.isOpen() && !drawer.getVisible());
    }

    private static void fontSizesFollowLabels(Font font) {
        Button button = new Button(Component.literal("Font"));
        int originalWidth = button.measureWidth(font, 200);
        Control parent = new Control().addChild(button);
        parent.layout(new Rect(0, 0, 200, 100), font);
        button.setFontSize(16.0F);
        check(button.getFontSize() == 16.0F && parent.getLayoutDirty());
        check(button.measureWidth(font, 200) == 2 * originalWidth - 2 * Button.PADDING_X);
        Navbar header = new Navbar(Component.literal("Font")).setFontSize(40.0F);
        check(header.getFontSize() == 40.0F);
        int height = header.measureSize(font, new Size(200, 100)).height();
        header.layout(new Rect(0, 0, 200, height), font);
        Label title = (Label) header.getChildren().get(0);
        check(title.getFontSize() == 40.0F && title.getBounds().height() >= title.lineHeight(font));
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Unexpected drawer layout or transition");
    }
}
