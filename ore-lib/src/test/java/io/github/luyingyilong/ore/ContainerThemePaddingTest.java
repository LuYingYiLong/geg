package io.github.luyingyilong.ore;

import com.google.gson.JsonParser;
import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.theme.*;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Theme spacing must reach actual geometry once, without replacing retained content. */
public final class ContainerThemePaddingTest {
    private static final Font FONT = new Font(id -> null, false) {
        @Override public int width(FormattedText text) { return text.getString().length() * 6; }
    };

    public static void main(String[] args) {
        ThemeDB db = ThemeDB.getDefault();
        db.reload(resources(Map.of()));
        check(db.getDiagnostics().isEmpty(), "padding keys load without diagnostics");
        inheritanceAndProperties(db);
        reload(db);
        System.out.println("ORE_CONTAINER_THEME_PADDING_PASS");
    }

    private static void inheritanceAndProperties(ThemeDB db) {
        Theme theme = db.getTheme(ResourceLocation.fromNamespaceAndPath("test", "container_padding"));
        Control content = new Control().setMinimum(0, 40);
        Collapse collapse = new Collapse().setContent(content).setExpandedNoSignal(true).setAnimationDuration(0);
        Window window = Window.embedded().setContent(collapse);
        window.setTheme(theme);
        Drawer drawer = new Drawer(Component.empty(), () -> 0L, cue -> { })
                .setClosable(false).setAnimationDuration(0).setContent(new Control());
        Overlay overlay = new Overlay();
        overlay.addChild(drawer);
        Window drawerWindow = Window.embedded().setContent(new Control()).setOverlay(overlay);
        drawerWindow.setTheme(theme);
        drawer.open();
        try {
            check(padding(collapse).equals(new Padding.Insets(4, 8, 4, 8)), "Collapse defaults are wide horizontally");
            check(collapse.getThemeItem(Collapse.PANEL).getContentMargin().equals(ThemeInsets.ZERO), "no duplicate style padding");
            check(padding(drawer).equals(Padding.Insets.all(4)), "Drawer default stays unchanged");
            check(collapse.getThemeOverride(Collapse.PADDING_LEFT) == null && drawer.getThemeOverride(Drawer.PADDING_LEFT) == null,
                    "defaults are effective values, not local overrides");
            for (int width : new int[]{127, 128, 129, 640}) {
                window.layoutViewport(width, 160, FONT);
                check(content.getBounds().width() == width - 16, "default horizontal padding applied once");
                check(content.getAbsoluteBounds().x() == 8, "default left padding");
                check(collapse.getVisibleBodyHeight() == 48, "default vertical padding applied once");
            }
            theme.set(Collapse.PADDING_TOP, 3).set(Collapse.PADDING_RIGHT, 11)
                    .set(Collapse.PADDING_BOTTOM, 5).set(Collapse.PADDING_LEFT, 7);
            theme.set(Drawer.PADDING_TOP, 2).set(Drawer.PADDING_RIGHT, 9)
                    .set(Drawer.PADDING_BOTTOM, 6).set(Drawer.PADDING_LEFT, 5);
            for (int width : new int[]{127, 128, 129, 640}) {
                window.layoutViewport(width, 160, FONT);
                drawerWindow.layoutViewport(width, 160, FONT);
                check(content.getBounds().width() == width - 18, "inherited asymmetric Collapse padding");
                check(content.getAbsoluteBounds().x() == 7, "inherited left padding");
                check(content.getAbsoluteBounds().y() == collapse.getHeaderButton().getBounds().height() + 3, "inherited top padding");
                Rect panel = drawer.getPanelBounds(), body = drawer.getBodyScroll().getAbsoluteBounds();
                check(body.equals(new Rect(panel.x() + 5, 2, panel.width() - 14, 152)), "Drawer headerless body uses all four theme insets");
            }
            theme.set(Collapse.PADDING_BOTTOM, 12);
            check(window.getLayoutDirty(), "live theme changes invalidate parent layout");
            window.layoutViewport(160, 160, FONT);
            check(collapse.getVisibleBodyHeight() == 55 && content.getBounds().height() == 40, "live padding changes height without scaling content");

            collapse.setThemeOverride(Collapse.PADDING_LEFT, 13);
            drawer.setThemeOverride(Drawer.PADDING_LEFT, 13);
            check(collapse.getThemeOverride(Collapse.PADDING_LEFT) == 13 && drawer.getThemeOverride(Drawer.PADDING_LEFT) == 13, "local overrides can be queried");
            collapse.setThemeOverride(Collapse.PADDING_TOP, 1);
            collapse.setThemeOverride(Collapse.PADDING_RIGHT, 2);
            collapse.setThemeOverride(Collapse.PADDING_BOTTOM, 3);
            collapse.setThemeOverride(Collapse.PADDING_LEFT, 4);
            drawer.setThemeOverride(Drawer.PADDING_TOP, 1);
            drawer.setThemeOverride(Drawer.PADDING_RIGHT, 4);
            drawer.setThemeOverride(Drawer.PADDING_BOTTOM, 4);
            drawer.setThemeOverride(Drawer.PADDING_LEFT, 1);
            theme.set(Collapse.PADDING_LEFT, 20).set(Drawer.PADDING_LEFT, 20);
            check(padding(collapse).equals(new Padding.Insets(1, 2, 3, 4)), "Collapse spacing uses local theme overrides");
            check(padding(drawer).equals(new Padding.Insets(1, 4, 4, 1)), "chat spacing uses local theme overrides");
            window.layoutViewport(160, 160, FONT);
            check(content.getBounds().width() == 154 && content.getAbsoluteBounds().x() == 4 && collapse.getVisibleBodyHeight() == 44,
                    "four local overrides reach actual Collapse layout");
            drawerWindow.layoutViewport(160, 160, FONT);
            check(drawer.getBodyScroll().getAbsoluteBounds().equals(new Rect(81, 1, 75, 155)), "four local overrides reach actual Drawer layout");
            check(drawer.getThemeResolution(Drawer.PADDING_LEFT).sources().get(0).kind().equals("local override"), "Inspector identifies local override source");
            collapse.removeThemeOverride(Collapse.PADDING_LEFT);
            drawer.removeThemeOverride(Drawer.PADDING_LEFT);
            check(collapse.getThemeOverride(Collapse.PADDING_LEFT) == null && drawer.getThemeOverride(Drawer.PADDING_LEFT) == null,
                    "removing override clears its query result");
            check(padding(collapse).left() == 20 && padding(drawer).left() == 20, "removing override restores latest inherited value");

            Padding.Insets before = padding(collapse), drawerBefore = padding(drawer);
            rejects(() -> collapse.setThemeOverride(Collapse.PADDING_LEFT, Integer.MAX_VALUE));
            rejects(() -> drawer.setThemeOverride(Drawer.PADDING_LEFT, Integer.MAX_VALUE));
            rejects(() -> collapse.setThemeOverride(Collapse.PADDING_TOP, -1));
            rejects(() -> drawer.setThemeOverride(Drawer.PADDING_BOTTOM, -1));
            check(before.equals(padding(collapse)) && drawerBefore.equals(padding(drawer)), "invalid spacing leaves all sides unchanged");
            check(collapse.getContent() == content && content.isAlive(), "spacing never replaces content");
            for (Class<?> type : new Class<?>[]{Collapse.class, Drawer.class}) for (var method : type.getMethods())
                check(!java.util.Set.of("setPadding", "clearPadding", "getPadding").contains(method.getName()), "container padding is configured and queried only through theme APIs");
        } finally {
            window.queueFree();drawerWindow.queueFree();LifecycleScheduler.flush();
        }
        check(content.isDisposed(), "retained Padding subtree is owned and freed");
    }

    private static void reload(ThemeDB db) {
        Collapse collapse = new Collapse().setContent(new Control().setMinimum(0, 20)).setExpandedNoSignal(true);
        Window window = Window.embedded().setContent(collapse);
        Drawer drawer = new Drawer(Component.empty(), () -> 0L, cue -> { }).setClosable(false).setAnimationDuration(0);
        Overlay overlay = new Overlay();overlay.addChild(drawer);
        Window drawerWindow = Window.embedded().setOverlay(overlay);drawer.open();
        try {
            window.layoutViewport(160, 100, FONT);drawerWindow.layoutViewport(160, 100, FONT);
            db.reload(resources(Map.of("Collapse:padding_top", 9, "Collapse:padding_left", 12,
                    "Drawer:padding_top", 7, "Drawer:padding_right", 10)));
            check(db.getDiagnostics().isEmpty(), "reloaded padding validates");
            window.layoutViewport(160, 100, FONT);drawerWindow.layoutViewport(160, 100, FONT);
            check(padding(collapse).equals(new Padding.Insets(9, 8, 4, 12)), "Collapse resource reload");
            check(collapse.getVisibleBodyHeight() == 33, "reloaded spacing changes body height");
            check(padding(drawer).equals(new Padding.Insets(7, 10, 4, 4)), "Drawer resource reload");
            check(drawer.getBodyScroll().getAbsoluteBounds().equals(new Rect(84, 7, 66, 89)), "Drawer reflows after reload");
            drawer.setThemeOverride(Drawer.PADDING_TOP, 1);
            drawer.setThemeOverride(Drawer.PADDING_RIGHT, 4);
            drawer.setThemeOverride(Drawer.PADDING_BOTTOM, 4);
            drawer.setThemeOverride(Drawer.PADDING_LEFT, 1);
            db.reload(resources(Map.of("Drawer:padding_top", 20, "Drawer:padding_left", 15)));
            drawerWindow.layoutViewport(160, 100, FONT);
            check(padding(drawer).equals(new Padding.Insets(1, 4, 4, 1)), "local spacing survives resource reload");
            check(drawer.getThemeOverride(Drawer.PADDING_TOP) == 1 && drawer.getThemeOverride(Drawer.PADDING_LEFT) == 1,
                    "override queries survive resource reload");
            check(drawer.getBodyScroll().getAbsoluteBounds().equals(new Rect(81, 1, 75, 95)), "reloaded Drawer keeps its local spacing geometry");
        } finally {
            window.queueFree();drawerWindow.queueFree();LifecycleScheduler.flush();
            db.reload(resources(Map.of()));
        }
    }

    private static Padding.Insets padding(Control control) {
        ThemeDB db = ThemeDB.getDefault();String type = control.getThemeType();
        return new Padding.Insets(control.getThemeItem(db.getKey(type, "padding_top", Integer.class)),
                control.getThemeItem(db.getKey(type, "padding_right", Integer.class)),
                control.getThemeItem(db.getKey(type, "padding_bottom", Integer.class)),
                control.getThemeItem(db.getKey(type, "padding_left", Integer.class)));
    }

    static ThemeDB.Resources resources(Map<String, Integer> overrides) {
        return new ThemeDB.Resources() {
            @Override public Reader open(ResourceLocation location) throws IOException {
                var stream = ContainerThemePaddingTest.class.getResourceAsStream("/assets/" + location.getNamespace() + "/" + location.getPath());
                if (stream == null) throw new FileNotFoundException(location.toString());
                Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
                if (!location.getNamespace().equals("ore") || !location.getPath().equals("themes/ore.json")) return reader;
                try (reader) {
                    var json = JsonParser.parseReader(reader).getAsJsonObject();
                    overrides.forEach(json::addProperty);
                    return new StringReader(json.toString());
                }
            }
            @Override public int[] imageSize(ResourceLocation location) throws IOException {
                try (var stream = ContainerThemePaddingTest.class.getResourceAsStream("/assets/" + location.getNamespace() + "/" + location.getPath())) {
                    if (stream == null) throw new FileNotFoundException(location.toString());
                    var image = javax.imageio.ImageIO.read(stream);
                    return new int[]{image.getWidth(), image.getHeight()};
                }
            }
        };
    }

    private static void rejects(Runnable action) {
        try { action.run();throw new AssertionError("Invalid padding accepted"); }
        catch (IllegalArgumentException expected) { }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
