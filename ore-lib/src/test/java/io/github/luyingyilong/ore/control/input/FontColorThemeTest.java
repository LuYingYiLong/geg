package io.github.luyingyilong.ore.control.input;

import io.github.luyingyilong.ore.control.base.*;
import io.github.luyingyilong.ore.control.data.Table;
import io.github.luyingyilong.ore.control.display.DescriptionItem;
import io.github.luyingyilong.ore.control.display.Descriptions;
import io.github.luyingyilong.ore.control.display.Label;
import io.github.luyingyilong.ore.control.navigation.Menu;
import io.github.luyingyilong.ore.control.navigation.MenuItem;
import io.github.luyingyilong.ore.control.navigation.Navbar;
import io.github.luyingyilong.ore.control.overlay.Dialog;
import io.github.luyingyilong.ore.control.overlay.Drawer;
import io.github.luyingyilong.ore.control.overlay.Modal;
import io.github.luyingyilong.ore.theme.OreTheme;
import io.github.luyingyilong.ore.theme.Theme;
import io.github.luyingyilong.ore.theme.ThemeDB;
import io.github.luyingyilong.ore.theme.ThemeKey;
import io.github.luyingyilong.ore.window.Window;

import com.google.gson.JsonParser;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.io.*;
import java.util.*;

/** Font colors remain inherited, role-specific and reloadable without copied Label properties. */
public final class FontColorThemeTest {
    private static final Font FONT = new Font(id -> null, false) {
        @Override public int width(FormattedText text) { return text.getString().length() * 6; }
        @Override public int width(String text) { return text.length() * 6; }
        @Override public int width(FormattedCharSequence text) {
            int[] count = {0};
            text.accept((index, style, codepoint) -> { count[0]++; return true; });
            return count[0] * 6;
        }
    };

    public static void main(String[] args) {
        ThemeDB db = ThemeDB.getDefault();
        db.reload(resources(Map.of()));
        check(db.getDiagnostics().isEmpty(), "Migrated font-color resources are valid");
        label(db);
        descriptions();
        titles();
        menu();
        table();
        button();
        reload(db);
        removedApi(db);
        System.out.println("ORE_FONT_COLOR_THEME_PASS");
    }

    private static void label(ThemeDB db) {
        Theme theme = db.getTheme(ResourceLocation.parse("test:font_color")).set(Label.FONT_COLOR, 0x80112233);
        Label label = new Label(Component.literal("Styled").withStyle(style -> style.withColor(0xEE7733)));
        Window window = Window.embedded().setContent(label);
        window.setTheme(theme);
        try {
            check(label.getThemeItem(Label.FONT_COLOR) == 0x80112233 && label.getThemeOverride(Label.FONT_COLOR) == null,
                    "Inherited color preserves alpha without creating a local override");
            int width = label.measureWidth(FONT, 100);
            label.setThemeOverride(Label.FONT_COLOR, 0);
            check(label.getThemeItem(Label.FONT_COLOR) == 0 && label.getThemeOverride(Label.FONT_COLOR) == 0,
                    "Fully transparent black is a present color override");
            theme.set(Label.FONT_COLOR, 0xFF445566);
            label.removeThemeOverride(Label.FONT_COLOR);
            check(label.getThemeItem(Label.FONT_COLOR) == 0xFF445566 && label.measureWidth(FONT, 100) == width,
                    "Restore exposes the latest ancestor color without changing text geometry");
            check(label.getStyledText().getStyle().getColor().getValue() == 0xEE7733,
                    "Component text keeps its explicitly styled child colors");
            check(!Label.FONT_COLOR.affectsLayout(), "Font color is a paint property");
        } finally { free(window); }
    }

    private static void descriptions() {
        Descriptions details = new Descriptions().setTitle(Component.literal("Details"))
                .addItem("first", Component.literal("Name"), Component.literal("Value"));
        try {
            details.setThemeOverride(Descriptions.TITLE_FONT_COLOR, 0xFF112233)
                    .setThemeOverride(Descriptions.LABEL_FONT_COLOR, 0xFF445566)
                    .setThemeOverride(Descriptions.CONTENT_FONT_COLOR, 0x80778899);
            details.addItem("later", Component.literal("Added"), Component.literal("Later"));
            check(details.getTitleNode().getThemeItem(Label.FONT_COLOR) == 0xFF112233, "Descriptions title is independently themed");
            for (DescriptionItem item : details.getItems()) {
                check(item.getLabelNode().getThemeItem(Label.FONT_COLOR) == 0xFF445566
                                && item.getContentNode().getThemeItem(Label.FONT_COLOR) == 0x80778899,
                        "Current and future descriptions inherit their role colors");
                check(item.getLabelNode().getThemeOverride(Label.FONT_COLOR) == null
                                && item.getContentNode().getThemeOverride(Label.FONT_COLOR) == null,
                        "Descriptions never copies colors into child overrides");
            }
            details.removeThemeOverride(Descriptions.LABEL_FONT_COLOR);
            check(details.getItem("later").getLabelNode().getThemeItem(Label.FONT_COLOR) == OreTheme.TEXT_SECONDARY,
                    "Removing caption color restores the secondary variation");
            check(details.getItem("first").getContentNode().getThemeItem(Label.FONT_COLOR) == 0x80778899,
                    "Caption restore does not reset content color");
        } finally { free(details); }
    }

    private static void titles() {
        Navbar navbar = new Navbar(Component.literal("Title"));
        Label extra = new Label(Component.literal("Extra"));
        navbar.setExtra(extra);
        try {
            navbar.setThemeOverride(OreTheme.NAVBAR_TITLE_FONT_COLOR, 0xFF123456);
            check(navbar.getChild(0).getThemeItem(Label.FONT_COLOR) == 0xFF123456
                            && extra.getThemeItem(Label.FONT_COLOR) == OreTheme.TEXT_WHITE,
                    "Navbar title color does not affect extra content");
            navbar.removeThemeOverride(OreTheme.NAVBAR_TITLE_FONT_COLOR);
            check(navbar.getChild(0).getThemeItem(Label.FONT_COLOR) == OreTheme.TEXT_DARK, "Navbar restores its dark title variation");
        } finally { free(navbar); }
        for (Control popup : List.of(new Dialog(Component.literal("Dialog")), io.github.luyingyilong.ore.control.overlay.OverlayTestAccess.modal(Component.literal("Modal")),
                io.github.luyingyilong.ore.control.overlay.OverlayTestAccess.drawer(Component.literal("Drawer"), () -> 0L, cue -> { }))) {
            try {
                popup.setThemeOverride(OreTheme.TITLE_FONT_COLOR, 0x80778899);
                Label title = labels(popup).stream().filter(Label::getHero).findFirst().orElseThrow();
                check(title.getThemeItem(Label.FONT_COLOR) == 0x80778899 && title.getThemeOverride(Label.FONT_COLOR) == null,
                        "Popup titles use the owner theme without a second color field");
                popup.removeThemeOverride(OreTheme.TITLE_FONT_COLOR);
                check(title.getThemeItem(Label.FONT_COLOR) == OreTheme.TEXT_WHITE, "Popup title restores inherited white");
            } finally { free(popup); }
        }
    }

    private static void menu() {
        Menu menu = new Menu().setEmpty(Component.literal("Empty"));
        try {
            Label whole = labels(menu).stream().filter(label -> label.getThemeTypeVariation().equals("MenuEmpty")).findFirst().orElseThrow();
            menu.setThemeOverride(Menu.EMPTY_FONT_COLOR, 0x80123456);
            check(whole.getThemeItem(Label.FONT_COLOR) == 0x80123456, "Whole-menu empty text updates in place");
            menu.setItems(List.of(MenuItem.group("group", Component.literal("Group"), List.of(MenuItem.empty("empty", Component.literal("Empty"))))));
            Label grouped = labels(menu).stream().filter(label -> label.getThemeTypeVariation().equals("MenuEmpty")).findFirst().orElseThrow();
            check(grouped.getThemeItem(Label.FONT_COLOR) == 0x80123456 && grouped.getThemeOverride(Label.FONT_COLOR) == null,
                    "Rebuilt group empty text inherits the same role override");
            menu.removeThemeOverride(Menu.EMPTY_FONT_COLOR);
            check(grouped.getThemeItem(Label.FONT_COLOR) == OreTheme.TEXT_WHITE, "Empty text restores white without rebuilding");
            check(!grouped.acceptsFocus(), "Empty text remains non-interactive");
        } finally { free(menu); }
    }

    private static void table() {
        Table<Integer> table = new Table<Integer>().setRowKey(value -> value)
                .addColumn(new Table.Column<>("name", Component.literal("Name"), 200, 200, 1, width -> 23,
                        new Table.TextCell<>(value -> Component.literal("Value " + value))));
        table.setRows(List.of(1, 2));
        Window window = Window.embedded().setContent(table);
        try {
            for (int width : new int[]{127, 128, 129, 320}) {
                table.setThemeOverride(Table.HEADER_FONT_COLOR, 0x80123456);
                window.layoutViewport(width, 160, FONT);
                Label header = (Label) table.getOwnedParts().get("header_name");
                check(header.getThemeItem(Label.FONT_COLOR) == 0x80123456, "Fixed header inherits the Table role override");
                Label caption = (Label) table.getChild(0).getOwnedParts().get("caption_name");
                check(caption.getThemeItem(Label.FONT_COLOR) == 0x80123456, "Compact row caption uses the same Table source before drawing");
                table.removeThemeOverride(Table.HEADER_FONT_COLOR);
                check(header.getThemeItem(Label.FONT_COLOR) == OreTheme.TEXT_SECONDARY
                                && caption.getThemeItem(Label.FONT_COLOR) == OreTheme.TEXT_SECONDARY,
                        "Restoring the header color reaches both retained forms");
            }
        } finally { free(window); }
    }

    private static void button() {
        Button button = new Button(Component.literal("Button"));
        try {
            List<ThemeKey<Integer>> keys = List.of(Button.FONT_COLOR, Button.FONT_HOVER_COLOR, Button.FONT_PRESSED_COLOR, Button.FONT_DISABLED_COLOR);
            for (ThemeKey<Integer> key : keys) {
                button.setThemeOverride(key, 0x80123456);
                check(button.getThemeItem(key) == 0x80123456, "Button state font colors are independently overrideable");
                button.removeThemeOverride(key);
            }
            check(Button.getTextKey(true, false, false) == Button.FONT_HOVER_COLOR
                            && Button.getTextKey(false, true, false) == Button.FONT_PRESSED_COLOR
                            && Button.getTextKey(false, false, true) == Button.FONT_DISABLED_COLOR,
                    "Rendering selects the migrated state keys");
            button.setThemeTypeVariation("PrimaryButton");
            check(button.getThemeItem(Button.FONT_COLOR) == OreTheme.TEXT_WHITE, "Primary variation keeps its default white text");
        } finally { free(button); }
    }

    private static void reload(ThemeDB db) {
        Label label = new Label();
        try {
            check(label.getThemeItem(Label.FONT_COLOR) == OreTheme.TEXT_WHITE, "Initial font color");
            db.reload(resources(Map.of("Label:font_color", "#80123456")));
            check(db.getDiagnostics().isEmpty() && label.getThemeItem(Label.FONT_COLOR) == 0x80123456, "Resource reload updates an existing Label");
            label.setThemeOverride(Label.FONT_COLOR, 0xAA112233);
            db.reload(resources(Map.of("Label:font_color", "#FF445566")));
            check(label.getThemeItem(Label.FONT_COLOR) == 0xAA112233, "Resource reload preserves local color");
            label.removeThemeOverride(Label.FONT_COLOR);
            check(label.getThemeItem(Label.FONT_COLOR) == 0xFF445566, "Restore exposes the latest resource color");
        } finally { free(label); db.reload(resources(Map.of())); }
    }

    private static void removedApi(ThemeDB db) {
        Set<String> old = Set.of("setColor", "getColor", "clearColor", "setTitleColor", "getTitleColor",
                "setLabelColor", "getLabelColor", "setContentColor", "getContentColor", "setHeaderColor", "setEmptyColor", "getEmptyColor");
        for (Class<?> type : List.of(Label.class, Button.class, Navbar.class, Dialog.class, Modal.class, Drawer.class, Descriptions.class, Table.class, Menu.class))
            for (var method : type.getMethods()) check(!old.contains(method.getName()), "No redundant font-color API: " + type.getSimpleName());
        try { db.getKey("Label", "color", Integer.class); throw new AssertionError("Old Label color item still registered"); }
        catch (IllegalArgumentException expected) { }
    }

    private static List<Label> labels(Control root) {
        List<Label> result = new ArrayList<>();
        if (root instanceof Label label) result.add(label);
        for (Control child : root.getChildren()) result.addAll(labels(child));
        return result;
    }

    private static ThemeDB.Resources resources(Map<String, String> colors) {
        ThemeDB.Resources original = io.github.luyingyilong.ore.support.ThemeTestResources.resources(Map.of());
        return new ThemeDB.Resources() {
            @Override public Reader open(ResourceLocation id) throws IOException {
                Reader reader = original.open(id);
                if (!id.equals(ResourceLocation.parse("ore:themes/ore.json"))) return reader;
                try (reader) {
                    var json = JsonParser.parseReader(reader).getAsJsonObject();
                    colors.forEach(json::addProperty);
                    return new StringReader(json.toString());
                }
            }
            @Override public int[] imageSize(ResourceLocation id) throws IOException { return original.imageSize(id); }
        };
    }

    private static void free(Control control) { control.queueFree(); LifecycleScheduler.flush(); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
