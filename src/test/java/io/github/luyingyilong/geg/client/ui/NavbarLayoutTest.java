package io.github.luyingyilong.geg.client.ui;

import io.github.luyingyilong.geg.client.ui.layout.Box;
import io.github.luyingyilong.geg.client.ui.layout.Control;
import io.github.luyingyilong.geg.client.ui.layout.Rect;
import io.github.luyingyilong.geg.client.ui.layout.Size;
import io.github.luyingyilong.geg.client.ui.layout.SizeFlags;
import io.github.luyingyilong.geg.client.ui.layout.Window;
import io.github.luyingyilong.geg.client.ui.theme.OreIcons;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.lwjgl.glfw.GLFW;

/** Navigation chrome shares Button's release, cancellation and keyboard focus behavior. */
public final class NavbarLayoutTest {
    public static void main(String[] args) {
        Font font = new Font(id -> null, false) {
            @Override public int width(FormattedText text) { return text.getString().length() * 6; }
        };
        int[] returned = {0};
        Navbar navbar = new Navbar(Component.literal("Navigation")).onBack(() -> returned[0]++);
        Button back = navbar.children().stream().filter(Button.class::isInstance).map(Button.class::cast).findFirst().orElseThrow();
        Control title = navbar.children().get(0);
        check(navbar.showBackButton(), "Back is visible by default");
        check(back.type() == Button.Type.TEXT && back.icon().texture().equals(OreIcons.ARROW_LEFT), "Use the left-arrow TEXT Button");
        check(back.actionMode() == Button.ACTION_MODE.BUTTON_RELEASE, "Back activates on release");
        for (int width : new int[]{25, 127, 128, 129, 640}) {
            int height = navbar.measureSize(font, new Size(width, 240)).height();
            navbar.layout(new Rect(0, 0, width, height), font);
            check(title.bounds().x() * 2 + title.bounds().width() == width, "Title stays centered across the entire navbar");
            check(back.bounds().x() == 0 && back.size().equals(new Size(22, 22)), "Back stays 22px square at the far left");
            check(height == 25 && title.bounds().height() == 22, "A 22px content row reserves 3px below it");
            check(back.bounds().y() == 0, "Back shrinks to the top of the content row");
            check(back.bounds().bottom() <= height - Navbar.BOTTOM_PADDING, "Navigation controls leave the bottom gap clear");
        }
        Control action = new Control().minimum(40, 20);
        navbar.extra(action);
        navbar.extra(new Control().minimum(60, 20));
        check(back.parent() == navbar && title.parent() == navbar && action.parent() == null, "Replacing extras keeps navigation chrome");
        check(!navbar.removeChild(back) && !navbar.removeChild(title), "Internal chrome cannot be removed as an extra");
        navbar.extra();
        Control body = new Control().sizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        Window window = new Window(true).content(new Box().add(navbar).add(body));
        window.layoutViewport(200, 120, font);
        Rect area = back.absoluteBounds();
        int x = area.x() + area.width() / 2, y = area.y() + area.height() / 2;
        check(window.pointerPressed(x, y, null) && returned[0] == 0, "Press does not navigate immediately");
        window.pointerReleased(x, y);
        check(returned[0] == 1, "Release inside navigates once");
        window.pointerPressed(x, y, null);
        window.pointerReleased(199, 100);
        check(returned[0] == 1, "Dragging outside cancels navigation");
        window.focusManager().requestFocus(back, null);
        window.keyPressed(GLFW.GLFW_KEY_ENTER, 0, null);
        check(returned[0] == 1, "Keyboard press also waits for release");
        window.keyReleased(GLFW.GLFW_KEY_ENTER, 0, null);
        check(returned[0] == 2, "Keyboard release invokes onBack");
        window.pointerPressed(x, y, null);
        navbar.showBackButton(false);
        check(navbar.layoutDirty() && !back.acceptsFocus(), "Hiding back invalidates layout and removes focus eligibility");
        window.pointerReleased(x, y);
        check(returned[0] == 2, "Hiding while pressed cancels navigation");
        window.layoutViewport(200, 120, font);
        check(!navbar.showBackButton() && title.bounds().x() == 0 && title.bounds().width() == 200, "Hidden back does not reserve title space");
        navbar.showBackButton(true);
        window.layoutViewport(200, 120, font);
        check(back.visible() && title.bounds().x() > 0, "Showing back restores its reserved space");
        navbar.fontSize(40.0F);
        window.layoutViewport(200, 120, font);
        check(title.size().height() > back.size().height() && back.bounds().y() == 0,
                "A taller title does not vertically center the top-aligned back button");
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
