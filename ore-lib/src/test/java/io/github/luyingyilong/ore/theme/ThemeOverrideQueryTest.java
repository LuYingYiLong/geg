package io.github.luyingyilong.ore.theme;

import io.github.luyingyilong.ore.Button;
import io.github.luyingyilong.ore.Collapse;
import io.github.luyingyilong.ore.Dropdown;
import io.github.luyingyilong.ore.Label;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.LifecycleScheduler;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.render.TextureRegion;
import net.minecraft.resources.ResourceLocation;

/** Local override inspection must remain distinct from effective theme resolution. */
public final class ThemeOverrideQueryTest {
    public static void main(String[] args) throws InterruptedException {
        localAndInherited();
        propertiesAndFallbacks();
        readOnlyAndValidation();
        System.out.println("ORE_THEME_OVERRIDE_QUERY_PASS");
    }

    private static void localAndInherited() {
        ThemeDB db = ThemeDB.getDefault();
        Theme theme = db.getTheme(id("local_query")).set(Collapse.PADDING_LEFT, 6);
        Control parent = new Control().setTheme(theme);
        Collapse collapse = new Collapse();parent.addChild(collapse);
        try {
            check(collapse.getThemeOverride(Collapse.PADDING_LEFT) == null, "inherited theme is not a local override");
            check(collapse.getThemeItem(Collapse.PADDING_LEFT) == 6, "effective query includes ancestor theme");
            parent.setThemeOverride(Collapse.PADDING_LEFT, 7);
            collapse.setThemeParent(parent);
            check(collapse.getThemeItem(Collapse.PADDING_LEFT) == 7, "explicit style source can inherit overrides");
            check(collapse.getThemeOverride(Collapse.PADDING_LEFT) == null, "inherited owner override is not this node's override");
            collapse.setThemeOverride(Collapse.PADDING_LEFT, 0);
            check(collapse.getThemeOverride(Collapse.PADDING_LEFT) == 0, "zero is a present override");
            var equivalent = ThemeKey.of("Collapse", "padding_left", Integer.class, ThemeKey.Kind.INTEGER, 0, true);
            check(!equivalent.equals(Collapse.PADDING_LEFT), "fixture uses a different key instance and declaration metadata");
            check(collapse.getThemeOverride(equivalent) == 0, "query matches the full item ID rather than record identity");
            collapse.setThemeOverride(equivalent, 9);
            check(collapse.getThemeOverride(Collapse.PADDING_LEFT) == 9, "replacing an equivalent ID is visible from the registered key");
            collapse.removeThemeOverride(Collapse.PADDING_LEFT);
            check(collapse.getThemeOverride(equivalent) == null && collapse.getThemeItem(Collapse.PADDING_LEFT) == 7,
                    "removal clears local value and restores inherited result");
        } finally {parent.queueFree();LifecycleScheduler.flush();}
    }

    private static void propertiesAndFallbacks() {
        Label label = new Label().setShadow(true);
        Button button = new Button().setThemeTypeVariation("PrimaryButton");
        Dropdown<String> dropdown = new Dropdown<>("all", 5);
        try {
            label.setThemeOverride(OreTheme.FONT_COLOR, 0xFF654321);
            label.setThemeOverride(OreTheme.TEXT_SHADOW, false);
            check(label.getThemeOverride(OreTheme.FONT_COLOR) == 0xFF654321 && label.getThemeItem(Label.FONT_COLOR) == 0xFF654321,
                    "font color is resolved directly from its theme override");
            check(Boolean.FALSE.equals(label.getThemeOverride(OreTheme.TEXT_SHADOW)), "false is distinct from absence");
            check(label.getShadow(), "explicit non-color property can still win over its stored override");
            label.clearShadow();
            check(!label.getShadow(), "clearing explicit property reveals the already stored override");
            var variant = ThemeDB.getDefault().getKey("PrimaryButton", "normal", StyleBox.class);
            button.setThemeOverride(variant, StyleBoxEmpty.INSTANCE);
            check(button.getThemeOverride(variant) == StyleBoxEmpty.INSTANCE, "exact variant item is queryable");
            check(button.getThemeOverride(Button.NORMAL) == null && button.getThemeItem(Button.NORMAL) == StyleBoxEmpty.INSTANCE,
                    "local query does not silently perform variant fallback");
            TextureRegion arrow = TextureRegion.full(id("arrow.png"), 7, 7);
            dropdown.setThemeOverride(IconTheme.ARROW_DOWN, arrow);
            check(dropdown.getThemeOverride(IconTheme.ARROW_DOWN) == arrow, "typed texture override is returned unchanged");
            check(dropdown.getThemeOverride(Dropdown.ARROW_ICON) == null && dropdown.getThemeItem(Dropdown.ARROW_ICON) == arrow,
                    "local query does not silently perform shared-symbol fallback");
        } finally {label.queueFree();button.queueFree();dropdown.queueFree();LifecycleScheduler.flush();}
    }

    private static void readOnlyAndValidation() throws InterruptedException {
        Control control = new Control();
        control.layout(new Rect(0, 0, 40, 40), null);
        ThemeDB db = ThemeDB.getDefault();
        int subscriptions = db.changed().connectionCount(), declarations = db.getKeys().size();
        boolean dirty = control.getLayoutDirty();
        try {
            for (int i = 0; i < 100; i++) check(control.getThemeOverride(Collapse.PADDING_LEFT) == null, "unset remains absent");
            check(control.getLayoutDirty() == dirty && control.getChildCount() == 0, "queries never mutate layout or tree");
            check(db.changed().connectionCount() == subscriptions && db.getKeys().size() == declarations,
                    "queries do not register declarations or subscribe to themes");
            rejects(() -> control.getThemeOverride(ThemeKey.of("Collapse", "missing", Integer.class, ThemeKey.Kind.INTEGER, 0, false)));
            rejects(() -> control.getThemeOverride(ThemeKey.of("Collapse", "padding_left", String.class, ThemeKey.Kind.FONT, "bad", false)));
            rejects(() -> control.getThemeOverride(ThemeKey.of("Collapse", "padding_left", Integer.class, ThemeKey.Kind.COLOR, 0, false)));
            try {control.getThemeOverride(null);throw new AssertionError("null key accepted");}
            catch (NullPointerException expected) { }
            Throwable[] failure = {null};
            Thread worker = new Thread(() -> {
                try {control.getThemeOverride(Collapse.PADDING_LEFT);}
                catch (Throwable error) {failure[0] = error;}
            });
            worker.start();worker.join();
            check(failure[0] instanceof IllegalStateException, "query enforces control thread ownership");
            control.setThemeOverride(Collapse.PADDING_LEFT, 3);
        } finally {control.queueFree();LifecycleScheduler.flush();}
        check(control.getThemeOverride(Collapse.PADDING_LEFT) == null, "permanent cleanup releases local override values");
    }

    private static ResourceLocation id(String path) {return ResourceLocation.fromNamespaceAndPath("test", path);}
    private static void rejects(Runnable action) {
        try {action.run();throw new AssertionError("Invalid theme query accepted");}
        catch (IllegalArgumentException expected) { }
    }
    private static void check(boolean condition, String message) {if (!condition) throw new AssertionError(message);}
}
