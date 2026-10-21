package io.github.luyingyilong.ore.control.navigation;

import io.github.luyingyilong.ore.control.input.Button;

import io.github.luyingyilong.ore.control.layout.Box;
import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.control.base.Size;
import io.github.luyingyilong.ore.control.base.SizeFlags;
import io.github.luyingyilong.ore.window.Window;
import io.github.luyingyilong.ore.theme.OreTheme;
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
        Button back = navbar.getChildren().stream().filter(Button.class::isInstance).map(Button.class::cast).findFirst().orElseThrow();
        Control title = navbar.getChildren().get(0);
        check(navbar.getShowBackButton(), "Back is visible by default");
        check(back.getThemeTypeVariation().equals("TextButton") && back.getIconControl().getResolvedTextureRegion().getTexture().equals(io.github.luyingyilong.ore.theme.IconTheme.ARROW_LEFT.defaultValue().getTexture()), "Use the left-arrow TEXT Button");
        check(back.getActionMode() == Button.ACTION_MODE.BUTTON_RELEASE, "Back activates on release");
        for (int width : new int[]{25, 127, 128, 129, 640}) {
            int height = navbar.measureSize(font, new Size(width, 240)).height();
            navbar.layout(new Rect(0, 0, width, height), font);
            check(title.getBounds().x() * 2 + title.getBounds().width() == width, "Title stays centered across the entire navbar");
            check(back.getBounds().x() == 0 && back.getSize().equals(new Size(22, 22)), "Back stays 22px square at the far left");
            check(height == Navbar.ROW_HEIGHT + Navbar.PADDING_BOTTOM.defaultValue() && title.getBounds().height() == Navbar.ROW_HEIGHT,
                    "The configured content row reserves the navbar's bottom padding");
            check(back.getBounds().y() == 0, "Back shrinks to the top of the content row");
            check(back.getBounds().bottom() <= height - Navbar.PADDING_BOTTOM.defaultValue(), "Navigation controls leave the bottom gap clear");
        }
        Control action = new Control().setMinimum(40, 20);
        navbar.setExtra(action);
        navbar.setExtra(new Control().setMinimum(60, 20));
        check(back.getParent() == navbar && title.getParent() == navbar && action.getParent() == null, "Replacing extras keeps navigation chrome");
        for (Control chrome : java.util.List.of(back, title)) {
            try { navbar.removeChild(chrome); throw new AssertionError("Chrome removal must be rejected"); }
            catch (IllegalStateException expected) { check(chrome.getParent() == navbar, "Rejected chrome removal is atomic"); }
        }
        navbar.setExtra();
        Control body = new Control().setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        var childContainer51 = new Box();
        childContainer51.addChild(navbar);
        childContainer51.addChild(body);
        Window window = new Window(true).setContent(childContainer51);
        window.layoutViewport(200, 120, font);
        Rect area = back.getAbsoluteBounds();
        int x = area.x() + area.width() / 2, y = area.y() + area.height() / 2;
        check(io.github.luyingyilong.ore.event.WindowInputs.press(window,x, y, null) && returned[0] == 0, "Press does not navigate immediately");
        io.github.luyingyilong.ore.event.WindowInputs.release(window,x, y);
        check(returned[0] == 1, "Release inside navigates once");
        io.github.luyingyilong.ore.event.WindowInputs.press(window,x, y, null);
        io.github.luyingyilong.ore.event.WindowInputs.release(window,199, 100);
        check(returned[0] == 1, "Dragging outside cancels navigation");
        window.getFocusManager().requestFocus(back, null);
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ENTER, 0, null);
        check(returned[0] == 1, "Keyboard press also waits for release");
        io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_ENTER, 0, null);
        check(returned[0] == 2, "Keyboard release invokes onBack");
        io.github.luyingyilong.ore.event.WindowInputs.press(window,x, y, null);
        navbar.setShowBackButton(false);
        check(navbar.getLayoutDirty() && !back.acceptsFocus(), "Hiding back invalidates layout and removes focus eligibility");
        io.github.luyingyilong.ore.event.WindowInputs.release(window,x, y);
        check(returned[0] == 2, "Hiding while pressed cancels navigation");
        window.layoutViewport(200, 120, font);
        check(!navbar.getShowBackButton() && title.getBounds().x() == 0 && title.getBounds().width() == 200, "Hidden back does not reserve title space");
        navbar.setShowBackButton(true);
        window.layoutViewport(200, 120, font);
        check(back.getVisible() && title.getBounds().x() > 0, "Showing back restores its reserved space");
        navbar.setThemeOverride(OreTheme.TITLE_FONT_SIZE, 40.0F);
        window.layoutViewport(200, 120, font);
        check(title.getSize().height() > back.getSize().height() && back.getBounds().y() == 0,
                "A taller title does not vertically center the top-aligned back button");
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
