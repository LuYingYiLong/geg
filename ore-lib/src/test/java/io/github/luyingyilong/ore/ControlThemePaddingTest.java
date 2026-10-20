package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.event.WindowInputs;
import io.github.luyingyilong.ore.form.*;
import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.render.*;
import io.github.luyingyilong.ore.theme.*;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

import java.util.*;

/** Theme padding must affect measured and retained geometry, not only the painted background. */
public final class ControlThemePaddingTest {
    private static final Font FONT = new Font(id -> null, false) {
        @Override public int width(FormattedText text) { return text.getString().length() * 6; }
        @Override public int width(String text) { return text.length() * 6; }
        @Override public int width(net.minecraft.util.FormattedCharSequence text) {
            int[] count = {0};text.accept((index, style, codepoint) -> { count[0]++;return true; });return count[0] * 6;
        }
        @Override public List<net.minecraft.util.FormattedCharSequence> split(FormattedText text, int width) {
            List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>();
            String value = text.getString();int step = Math.max(1, width / 6);
            for (int start = 0; start < value.length(); start += step)
                lines.add(Component.literal(value.substring(start, Math.min(value.length(), start + step))).getVisualOrderText());
            return lines.isEmpty() ? List.of(Component.empty().getVisualOrderText()) : lines;
        }
    };

    public static void main(String[] args) {
        ThemeDB db = ThemeDB.getDefault();
        db.reload(ContainerThemePaddingTest.resources(Map.of()));
        check(db.getDiagnostics().isEmpty(), "all appearance padding keys are declared");
        compactText();
        button();
        navbar();
        menu();
        form();
        popup();
        reload(db);
        api();
        System.out.println("ORE_CONTROL_THEME_PADDING_PASS");
    }

    private static void compactText() {
        Tag tag = new Tag(Component.literal("Tag"));
        Size before = tag.measureSize(FONT, new Size(200, 100));
        tag.setThemeOverride(Tag.PADDING_TOP, 3).setThemeOverride(Tag.PADDING_RIGHT, 9)
                .setThemeOverride(Tag.PADDING_BOTTOM, 5).setThemeOverride(Tag.PADDING_LEFT, 7);
        Size after = tag.measureSize(FONT, new Size(200, 100));
        check(after.equals(new Size(before.width() + 8, before.height() + 4)), "Tag measures asymmetric padding");
        tag.layout(new Rect(0, 0, 100, 30), FONT);
        check(tag.getChild(0).getBounds().equals(new Rect(7, 3, 84, 22)), "Tag applies padding once");
        tag.setOutlined(true);
        tag.layout(new Rect(0, 0, 100, 30), FONT);
        check(tag.getChild(0).getBounds().equals(new Rect(8, 4, 82, 20)), "Tag border remains separate from padding");

        Toast toast = new Toast(Component.literal("Toast"));
        before = toast.measureSize(FONT, new Size(200, 100));
        toast.setThemeOverride(Toast.PADDING_TOP, 3).setThemeOverride(Toast.PADDING_RIGHT, 9)
                .setThemeOverride(Toast.PADDING_BOTTOM, 5).setThemeOverride(Toast.PADDING_LEFT, 7);
        after = toast.measureSize(FONT, new Size(200, 100));
        check(after.equals(new Size(before.width() + 8, before.height() + 4)), "Toast measurement follows the same insets as drawing");
        toast.layout(new Rect(0, 0, 100, 30), FONT);
        check(toast.textBounds(new Rect(0, 0, 100, 30)).equals(new Rect(7, 3, 84, 22)), "Toast text painter uses theme padding");
        check(toast.getChild(0).getBounds().equals(toast.textBounds(new Rect(0, 0, 100, 30))), "Toast retained Label matches GUI painter");

        Badge badge = new Badge().setCount(88);
        before = badge.measureSize(FONT, new Size(200, 100));
        badge.setThemeOverride(Badge.PADDING_TOP, 3).setThemeOverride(Badge.PADDING_RIGHT, 9)
                .setThemeOverride(Badge.PADDING_BOTTOM, 5).setThemeOverride(Badge.PADDING_LEFT, 7);
        after = badge.measureSize(FONT, new Size(200, 100));
        check(after.width() == before.width() + 12 && after.height() == 19, "Badge keeps minimum face and shadow separate from text padding");
        badge.layout(new Rect(0, 0, 60, 31), FONT);
        Rect text = badge.getChild(0).getBounds();
        check(text.x() >= 7 && text.right() <= 51 && text.y() == 3 && text.bottom() == 25, "Badge centers in its asymmetric content area");
        badge.setCount(null);
        check(badge.measureSize(FONT, new Size(200, 100)).equals(new Size(3, 4)), "Dot Badge has no text padding");
        free(tag, toast, badge);
    }

    private static void button() {
        Button button = new Button(cue -> { }).setIcon(TextureSource.themed(IconTheme.ARROW_LEFT));
        var image = button.getIconControl().getResolvedTextureRegion();
        button.setThemeOverride(Button.ICON_PADDING_LEFT, 15).setThemeOverride(Button.ICON_PADDING_RIGHT, 7)
                .setThemeOverride(Button.ICON_PADDING_TOP, 8).setThemeOverride(Button.ICON_PADDING_BOTTOM, 9);
        Size after = button.measureSize(FONT, new Size(200, 100));
        check(after.width() == image.getWidth() + 22 && after.height() == image.getHeight() + 17, "Icon-only Button measures themed inset instead of a fixed constant");
        button.setLabel(Component.literal("Mail")).setIcon(null);
        Badge badge = new Badge().setCount(7);
        button.setBadge(badge);
        button.setThemeOverride(Button.BADGE_PADDING_RIGHT, 13).setThemeOverride(Button.BADGE_PADDING_TOP, 3)
                .setThemeOverride(Button.BADGE_PADDING_BOTTOM, 7);
        button.layout(new Rect(0, 0, 120, 40), FONT);
        check(badge.getBounds().right() == 107, "Button Badge right inset is themed");
        check(badge.getBounds().y() == 3 + (30 - badge.getBounds().height()) / 2, "Button Badge uses padded vertical center");
        button.setToggleButton(true).setButtonPressedNoSignal(true);
        button.layout(new Rect(0, 0, 120, 40), FONT);
        check(badge.getBounds().right() == 107 && badge.getBounds().y() == 3 + (30 - badge.getBounds().height()) / 2 + 2,
                "Pressed content offset composes with padding");
        button.setThemeOverride(Button.BADGE_PADDING_LEFT, 100);
        check(button.measureWidth(FONT, 300) >= 113 + badge.measureWidth(FONT, 300), "Badge left inset also participates in minimum required width");
        free(button);
        Button small = new Button(cue -> { }).setSize(Button.Size.SMALL).setLabel(Component.literal("Small"));
        check(small.measureHeight(FONT, 200) == Button.Size.SMALL.getHeight(), "Default single-line leading does not enlarge small buttons");
        small.setFontSize(16);
        check(small.measureHeight(FONT, 200) == 24, "Larger optical text plus themed margin contributes to measured height");
        free(small);
    }

    private static void navbar() {
        Navbar navbar = new Navbar(Component.literal("N"));
        Control extra = new Control().setMinimum(52, 20);
        navbar.setExtra(extra);
        navbar.setThemeOverride(Navbar.PADDING_TOP, 4).setThemeOverride(Navbar.PADDING_RIGHT, 9)
                .setThemeOverride(Navbar.PADDING_BOTTOM, 7).setThemeOverride(Navbar.PADDING_LEFT, 5);
        for (int width : new int[]{127, 128, 129, 400}) {
            int height = navbar.measureHeight(FONT, width);
            navbar.layout(new Rect(0, 0, width, height), FONT);
            Rect title = navbar.getChild(0).getBounds();
            check(title.y() == 4 && title.x() * 2 + title.width() == width + 5 - 9, "Navbar centers within padded content");
            check(navbar.getChild(1).getBounds().x() == 5 && navbar.getChild(1).getBounds().y() == 4, "Navbar back begins at theme inset");
            check(extra.getBounds().right() == width - 9 && extra.getBounds().bottom() <= height - 7,
                    "Wrapped and inline extras respect the same left/right inset");
        }
        free(navbar);
    }

    private static void menu() {
        Menu menu = new Menu();
        menu.setItems(List.of(MenuItem.group("group", Component.literal("Group"), List.of(
                MenuItem.empty("empty", Component.literal("Empty")), MenuItem.item("item", Component.literal("Item"))))));
        Window window = Window.embedded().setContent(menu);
        try {
            window.layoutViewport(160, 200, FONT);
            List<Padding> retained = descendants(menu, Padding.class);
            check(retained.size() == 2, "Only heading and informational row receive Menu padding");
            Button button = menu.getButton("item");
            int[] activated = {0};
            menu.itemActivated().connect(window, value -> activated[0]++);
            menu.setThemeOverride(Menu.PADDING_TOP, 2).setThemeOverride(Menu.PADDING_RIGHT, 11)
                    .setThemeOverride(Menu.PADDING_BOTTOM, 3).setThemeOverride(Menu.PADDING_LEFT, 7);
            for (int frame = 0; frame < 8; frame++) window.layoutViewport(160, 200, FONT);
            check(retained.equals(descendants(menu, Padding.class)) && menu.getButton("item") == button, "Theme changes retain Menu rows and bindings");
            for (Padding padding : retained) {
                check(padding.getInsets().equals(new Padding.Insets(2, 11, 3, 7)), "Menu heading/empty insets stay in sync");
                check(padding.getChild(0).getBounds().x() == 7, "Retained Menu Label gets the actual padding");
            }
            Rect area = button.getAbsoluteBounds();
            WindowInputs.press(window, area.x() + 4, area.y() + 4, null);
            WindowInputs.release(window, area.x() + 4, area.y() + 4);
            check(activated[0] == 1, "Relayout does not duplicate subscriptions");
        } finally { free(window); }
    }

    private static void form() {
        Checkbox checkbox = new Checkbox(Component.literal("Check"));
        FormItem<Boolean> item = new FormItem<>(new FieldKey<>("checked", Boolean.class), Component.literal("Choice"), FormFields.checkbox(checkbox));
        int height = item.rowHeight(FONT, 200);
        item.setThemeOverride(FormItem.PADDING_TOP, 3).setThemeOverride(FormItem.PADDING_RIGHT, 11)
                .setThemeOverride(FormItem.PADDING_BOTTOM, 9).setThemeOverride(FormItem.PADDING_LEFT, 7);
        check(item.rowHeight(FONT, 200) == height + 6, "FormItem measures all sides instead of fixed trailing row padding");
        item.layout(new Rect(0, 0, 200, item.rowHeight(FONT, 200)), FONT);
        check(item.getChild(0).getBounds().x() == 7 && item.getChild(0).getBounds().y() == 3, "Form label follows theme padding");
        check(checkbox.getBounds().x() == 7 && checkbox.getBounds().width() == 182, "Field allocation agrees with padded measurement");
        item.setHorizontal(true);
        item.layout(new Rect(0, 0, 200, item.rowHeight(FONT, 200)), FONT);
        check(checkbox.getBounds().right() == 189 && checkbox.getBounds().y() == 3, "Horizontal field shares the same inset geometry");
        free(item);
    }

    private static void popup() {
        Dropdown<String> dropdown = new Dropdown<>("a", 4);
        dropdown.setOptions(List.of(new Dropdown.Option<>("a", Component.literal("A")), new Dropdown.Option<>("b", Component.literal("B"))));
        dropdown.setPosition(20, 20).setRequestedSize(100, 20);
        dropdown.setThemeOverride(Dropdown.PADDING_TOP, 3).setThemeOverride(Dropdown.PADDING_RIGHT, 9)
                .setThemeOverride(Dropdown.PADDING_BOTTOM, 5).setThemeOverride(Dropdown.PADDING_LEFT, 7);
        Control root = new Control();root.addChild(dropdown);
        Window window = Window.embedded().setContent(root);
        try {
            window.layoutViewport(400, 220, FONT);
            WindowInputs.press(window, 30, 25, null);WindowInputs.release(window, 30, 25);
            check(dropdown.isOpen(), "Dropdown opens from Window input chain");
            var popup = window.getPopupOverlay().getChild(0);
            var panel = popup.getChild(0);
            Scroll scroll = (Scroll) panel.getChild(0);
            Rect area = panel.getAbsoluteBounds(), viewport = scroll.getAbsoluteBounds();
            check(viewport.x() == area.x() + 7 && viewport.y() == area.y() + 3
                    && viewport.width() == area.width() - 16 && viewport.height() == area.height() - 8,
                    "Overlay uses trigger's padding, outside the ordinary tree branch");
            dropdown.setThemeOverride(Dropdown.PADDING_LEFT, 13);
            window.layoutViewport(400, 220, FONT);
            check(scroll.getBounds().x() == 13, "Open menu reflows after a live override");
            Control option = scroll.getContent().getChild(1);
            Rect hit = option.getAbsoluteBounds();
            WindowInputs.press(window, hit.x() + 4, hit.y() + 4, null);
            WindowInputs.release(window, hit.x() + 4, hit.y() + 4);
            check(dropdown.getSelected().equals("b") && !dropdown.isOpen(), "Padded option drawing and pointer coordinates agree");
        } finally { free(window); }
    }

    private static void reload(ThemeDB db) {
        Tag tag = new Tag(Component.literal("Reload"));
        Window window = Window.embedded().setContent(tag);
        try {
            window.layoutViewport(120, 60, FONT);
            Control label = tag.getChild(0);
            tag.setThemeOverride(Tag.PADDING_LEFT, 13);
            db.reload(ContainerThemePaddingTest.resources(Map.of("Tag:padding_top", 6, "Tag:padding_left", 20)));
            window.layoutViewport(120, 60, FONT);
            check(label == tag.getChild(0) && label.getBounds().x() == 13 && label.getBounds().y() == 6, "Reload updates retained Label, preserving local override");
            tag.removeThemeOverride(Tag.PADDING_LEFT);
            window.layoutViewport(120, 60, FONT);
            check(label.getBounds().x() == 20 && tag.getThemeOverride(Tag.PADDING_LEFT) == null, "Removing override restores latest resource padding");
            rejects(() -> tag.setThemeOverride(Tag.PADDING_LEFT, -1));
            rejects(() -> tag.setThemeOverride(Tag.PADDING_LEFT, Integer.MAX_VALUE));
            check(label.getBounds().x() == 20, "Invalid padding cannot alter the current layout");
        } finally { free(window);db.reload(ContainerThemePaddingTest.resources(Map.of())); }
    }

    private static void api() {
        for (Class<?> type : List.of(Button.class, Tag.class, Toast.class, Badge.class, Navbar.class, Menu.class,
                Table.TextCell.class, Slot.class, FormItem.class, Dropdown.class))
            for (var method : type.getMethods())
                check(!Set.of("setPadding", "getPadding", "clearPadding", "setInset", "setInsets", "getInsets").contains(method.getName()),
                        "Appearance padding must have no duplicate setter/getter: " + type.getSimpleName());
        Padding padding = new Padding(new Padding.Insets(1, 2, 3, 4));
        check(padding.getInsets().left() == 4, "Explicit layout Padding keeps its structural API");
        free(padding);
    }

    private static <T> List<T> descendants(Control root, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (Control child : root.getChildren()) { if (type.isInstance(child)) result.add(type.cast(child)); result.addAll(descendants(child, type)); }
        return List.copyOf(result);
    }
    private static void free(Control... nodes) { for (Control node : nodes) node.queueFree();LifecycleScheduler.flush(); }
    private static void rejects(Runnable action) {
        try { action.run();throw new AssertionError("Invalid padding accepted"); } catch (IllegalArgumentException expected) { }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
