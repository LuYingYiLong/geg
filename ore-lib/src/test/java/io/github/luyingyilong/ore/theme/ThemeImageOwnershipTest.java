package io.github.luyingyilong.ore.theme;

import io.github.luyingyilong.ore.*;
import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.render.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** Theme ownership, shared symbol fallback and real internal image resolution. */
public final class ThemeImageOwnershipTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("test", path); }
    private static TextureRegion image(String name) { return TextureRegion.full(id(name), 7, 7); }

    public static void main(String[] args) {
        declarations(); fallbackAndParts(); stateOwnership(); diagnostics();
        System.out.println("ORE_THEME_IMAGE_OWNERSHIP_PASS");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void declarations() {
        ThemeDB db = new ThemeDB();
        Dropdown.registerTheme(db); Checkbox.registerTheme(db); Switch.registerTheme(db); Spin.registerTheme(db);
        Tree.registerTheme(db); Navbar.registerTheme(db); Dialog.registerTheme(db); Modal.registerTheme(db); Drawer.registerTheme(db);
        Dropdown.registerTheme(db); IconTheme.registerTheme(db);
        check(db.getTypeChain("Icons").equals(List.of("Icons")), "Shared symbols are an independent resource namespace");
        check(db.getKeys().stream().noneMatch(key -> key.type().equals("Control") && key.kind() == ThemeKey.Kind.TEXTURE),
                "Control must not acquire every control's state textures");
        check(db.getKeys().stream().noneMatch(key -> key.name().startsWith("checkbox_") || key.name().startsWith("switch_")),
                "Control type already scopes state names");
        rejects(() -> db.getKey("Control", "close", TextureRegion.class));
        rejects(() -> db.registerFallback(IconTheme.ARROW_DOWN, Dropdown.ARROW_ICON));
        rejects(() -> db.registerFallback(Dropdown.ARROW_ICON, IconTheme.ARROW_LEFT));
        ThemeKey<Integer> color = ThemeKey.of("Control", "test_color", Integer.class, ThemeKey.Kind.COLOR, 0, false);
        db.register(color);
        rejects(() -> db.registerFallback((ThemeKey)color, (ThemeKey)IconTheme.CLOSE));
        var loop = IconTheme.texture("Dropdown", "loop", "arrow_down", 7, 7);
        db.register(loop); db.registerVariation("TestDropdown", "Dropdown");
        rejects(() -> db.registerFallback(loop, db.getKey("TestDropdown", "loop", TextureRegion.class)));
    }

    private static void fallbackAndParts() {
        ThemeDB db = ThemeDB.getDefault();
        TextureRegion shared = image("shared"), scoped = image("scoped"), local = image("local"), varied = image("varied");
        Theme parent = db.getTheme(id("image_parent")).set(Dropdown.ARROW_ICON, scoped);
        Theme child = db.getTheme(id("image_child")).setParent(parent).set(IconTheme.ARROW_DOWN, shared);
        Dropdown<String> dropdown = new Dropdown<>("all", 5);
        dropdown.setTheme(child);
        TextureRect arrow = dropdown.getArrowControl();
        check(arrow.getResolvedTextureRegion() == shared, "Nearer shared symbol wins over a distant scoped item");
        var trace = arrow.getThemeResolution(Dropdown.ARROW_ICON).sources();
        check(trace.get(trace.size() - 1).item().equals("Icons:arrow_down")
                && trace.get(trace.size() - 1).reason().equals("fallback for Dropdown:arrow"), "Fallback source is explicit in diagnostics");
        child.set(Dropdown.ARROW_ICON, scoped);
        check(arrow.getResolvedTextureRegion() == scoped, "Scoped item wins within the same resource layer");
        dropdown.setThemeOverride(IconTheme.ARROW_DOWN, local);
        check(arrow.getResolvedTextureRegion() == local, "Owned image inherits its owner's shared-symbol override");
        dropdown.setThemeOverride(Dropdown.ARROW_ICON, scoped);
        check(arrow.getResolvedTextureRegion() == scoped, "Scoped local override wins over shared local override");
        dropdown.removeThemeOverride(Dropdown.ARROW_ICON); dropdown.removeThemeOverride(IconTheme.ARROW_DOWN);
        db.registerVariation("ImageDropdown", "Dropdown");
        var variant = db.getKey("ImageDropdown", "arrow", TextureRegion.class);
        child.set(variant, varied); dropdown.setThemeTypeVariation("ImageDropdown");
        check(arrow.getResolvedTextureRegion() == varied, "Internal Button/TextureRect retain the Dropdown's semantic variation");
        child.remove(variant); child.remove(Dropdown.ARROW_ICON);
        check(arrow.getResolvedTextureRegion() == shared, "Missing variation and scoped item return to the shared symbol");
        TextureRect symbol = new TextureRect().setTexture(TextureSource.themed(IconTheme.ARROW_DOWN));
        var unrelated = IconTheme.texture("TextureRect", "arrow_down", "arrow_left", 7, 7);
        db.register(unrelated); symbol.setTheme(child); child.set(unrelated, local);
        check(symbol.getResolvedTextureRegion() == shared, "Resource namespaces cannot be shadowed by an unrelated Control type");

        Theme closes = db.getTheme(id("closes")).set(IconTheme.CLOSE, shared).set(Modal.CLOSE_ICON, local);
        Modal modal = new Modal(Component.literal("Title")); modal.setTheme(closes);
        Drawer drawer = new Drawer(Component.literal("Title")); drawer.setTheme(closes);
        check(findImage(modal, Modal.CLOSE_ICON).getResolvedTextureRegion() == local, "Modal owns its close icon item");
        check(findImage(drawer, Drawer.CLOSE_ICON).getResolvedTextureRegion() == shared, "Drawer independently falls back to the shared close icon");
        modal.setThemeOverride(Modal.CLOSE_ICON, shared);
        drawer.setThemeOverride(Drawer.CLOSE_ICON, local);
        check(findImage(modal, Modal.CLOSE_ICON).getResolvedTextureRegion() == shared
                && findImage(drawer, Drawer.CLOSE_ICON).getResolvedTextureRegion() == local,
                "Close buttons explicitly inherit local overrides through their layout skeleton");
        Dialog dialog = new Dialog(Component.literal("Title"));
        Navbar navbar = new Navbar(Component.literal("Title"));
        dialog.setThemeOverride(Dialog.CLOSE_ICON, local); navbar.setThemeOverride(Navbar.BACK_ICON, local);
        check(findImage(dialog, Dialog.CLOSE_ICON).getResolvedTextureRegion() == local
                && findImage(navbar, Navbar.BACK_ICON).getResolvedTextureRegion() == local,
                "Dialog and Navbar honor local image overrides too");
        dropdown.queueFree(); symbol.queueFree(); modal.queueFree(); drawer.queueFree();
        dialog.queueFree(); navbar.queueFree(); LifecycleScheduler.flush();
        check(arrow.isDisposed(), "Theme resolution does not change internal image lifetime");
    }

    private static void stateOwnership() {
        var db = ThemeDB.getDefault();
        TextureRegion selected = image("state");
        Theme theme = db.getTheme(id("states")).set(Checkbox.CHECKED_NORMAL, selected)
                .set(Switch.BUTTON_NORMAL, selected).set(Spin.TEXTURE, selected);
        Checkbox checkbox = new Checkbox(Component.literal("Checked")); checkbox.setTheme(theme);
        Switch toggle = new Switch(); toggle.setTheme(theme);
        Spin spinner = new Spin(); spinner.setTheme(theme);
        check(checkbox.getThemeItem(Checkbox.CHECKED_NORMAL) == selected, "Checkbox state belongs to Checkbox");
        check(toggle.getThemeItem(Switch.BUTTON_NORMAL) == selected, "Switch state belongs to Switch");
        check(spinner.getThemeType().equals("Spin") && spinner.getThemeItem(Spin.TEXTURE) == selected, "Spin declares its own type and texture");
        checkbox.queueFree(); toggle.queueFree(); spinner.queueFree(); LifecycleScheduler.flush();
    }

    private static void diagnostics() {
        Control plain = new Control();
        check(DebugBridge.themes(plain).items().stream().noneMatch(item -> item.key().startsWith("Icons:")
                || item.key().startsWith("Checkbox:") || item.key().startsWith("Switch:")), "Inspector plain nodes have no unrelated icon catalog");
        Checkbox checkbox = new Checkbox(Component.empty());
        check(DebugBridge.themes(checkbox).items().stream().anyMatch(item -> item.key().equals("Checkbox:checked_normal")),
                "Inspector shows the consuming control's state items");
        plain.queueFree(); checkbox.queueFree(); LifecycleScheduler.flush();
    }

    private static TextureRect findImage(Control control, ThemeKey<TextureRegion> key) {
        if(control instanceof TextureRect image && image.getTexture() instanceof TextureSource.Themed source && source.getKey().equals(key)) return image;
        for(Control child : control.getChildren()) { TextureRect image = findImage(child, key); if(image != null) return image; }
        for(Control part : control.getOwnedParts().values()) { TextureRect image = findImage(part, key); if(image != null) return image; }
        return null;
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch(IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected invalid theme declaration to be rejected");
    }
    private static void check(boolean condition, String message) { if(!condition) throw new AssertionError(message); }
}
