package io.github.luyingyilong.ore.control.base;

import io.github.luyingyilong.ore.control.display.DescriptionItem;
import io.github.luyingyilong.ore.control.display.Descriptions;
import io.github.luyingyilong.ore.control.display.Label;
import io.github.luyingyilong.ore.control.input.Button;
import io.github.luyingyilong.ore.control.navigation.Navbar;
import io.github.luyingyilong.ore.control.overlay.Dialog;
import io.github.luyingyilong.ore.control.overlay.Drawer;
import io.github.luyingyilong.ore.control.overlay.Modal;
import io.github.luyingyilong.ore.control.overlay.Overlay;
import io.github.luyingyilong.ore.theme.OreTheme;
import io.github.luyingyilong.ore.theme.Theme;
import io.github.luyingyilong.ore.theme.ThemeDB;
import io.github.luyingyilong.ore.window.Window;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.*;

/** Font size has one theme source for measurement, retained parts and resource reloads. */
public final class FontSizeThemeTest {
    private static final Font FONT = new Font(id -> null, false) {
        @Override public int width(String text) { return text.codePointCount(0, text.length()) * 6; }
        @Override public int width(FormattedText text) { return width(text.getString()); }
        @Override public int width(FormattedCharSequence text) {
            int[] count = {0};text.accept((index, style, codepoint) -> { count[0]++;return true; });return count[0] * 6;
        }
        @Override public List<FormattedCharSequence> split(FormattedText text, int width) {
            int[] points = text.getString().codePoints().toArray();int step = Math.max(1, width / 6);
            List<FormattedCharSequence> lines = new ArrayList<>();
            for (int from = 0; from < points.length; from += step)
                lines.add(Component.literal(new String(points, from, Math.min(step, points.length - from))).getVisualOrderText());
            return lines.isEmpty() ? List.of(FormattedCharSequence.EMPTY) : lines;
        }
    };

    public static void main(String[] args) {
        ThemeDB db = ThemeDB.getDefault();
        db.reload(io.github.luyingyilong.ore.support.ThemeTestResources.resources(Map.of()));
        check(db.getDiagnostics().isEmpty(), "Font roles register before resource loading");
        labelAndReload(db);
        ownedLabel();
        titles();
        descriptions();
        removedApi();
        System.out.println("ORE_FONT_SIZE_THEME_PASS");
    }

    private static void labelAndReload(ThemeDB db) {
        Theme theme = db.getTheme(ResourceLocation.fromNamespaceAndPath("test", "font_size"));
        Label label = new Label(Component.literal("ABCDE"));
        Window window = Window.embedded().setContent(label);
        window.setTheme(theme);
        try {
            check(label.getThemeItem(Label.FONT_SIZE) == 8.0F && label.getThemeOverride(Label.FONT_SIZE) == null, "Default is inherited, not an explicit property");
            window.layoutViewport(120, 80, FONT);
            theme.set(Label.FONT_SIZE, 12.0F);
            check(window.getLayoutDirty() && label.measureWidth(FONT, 100) == 45, "Inherited font changes invalidate layout and measurement");
            label.setThemeOverride(Label.FONT_SIZE, 16.0F);
            check(label.measureWidth(FONT, 100) == 60 && label.lineHeight(FONT) == 20, "Local font applies to advance and line box");
            label.setWrap(Label.Wrap.CHARACTER);
            check(label.lines(FONT, 24).size() == 3, "Wrap uses the same effective size");
            label.setScale(0.5F);
            check(label.measureWidth(FONT, 100) == 30 && label.lineHeight(FONT) == 10, "Scale remains a relative multiplier");
            theme.set(Label.FONT_SIZE, 14.0F);
            label.removeThemeOverride(Label.FONT_SIZE);
            check(label.getThemeItem(Label.FONT_SIZE) == 14.0F && label.getThemeOverride(Label.FONT_SIZE) == null, "Removing override restores the latest ancestor size");
            for (float invalid : new float[]{0, -1, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
                rejects(() -> label.setThemeOverride(Label.FONT_SIZE, invalid));
                check(label.getThemeItem(Label.FONT_SIZE) == 14.0F, "Invalid font cannot mutate the previous value");
            }
        } finally { free(window); }

        Label reloaded = new Label(Component.literal("ABCDE"));
        window = Window.embedded().setContent(reloaded);
        try {
            check(reloaded.measureWidth(FONT, 200) == 30, "Existing text resolves its initial resource font");
            window.layoutViewport(100, 80, FONT);
            db.reload(io.github.luyingyilong.ore.support.ThemeTestResources.resources(Map.of("Label:font_size", 12)));
            check(window.getLayoutDirty() && reloaded.measureWidth(FONT, 200) == 45, "Resource reload updates an existing Label");
            reloaded.setThemeOverride(Label.FONT_SIZE, 16.0F);
            db.reload(io.github.luyingyilong.ore.support.ThemeTestResources.resources(Map.of("Label:font_size", 10)));
            check(reloaded.getThemeItem(Label.FONT_SIZE) == 16.0F, "Reload preserves local size override");
            reloaded.removeThemeOverride(Label.FONT_SIZE);
            check(reloaded.getThemeItem(Label.FONT_SIZE) == 10.0F, "Restore sees newest resource size");
        } finally { free(window);db.reload(io.github.luyingyilong.ore.support.ThemeTestResources.resources(Map.of())); }
    }

    private static void ownedLabel() {
        Button button = new Button(Component.literal("Button"));
        Label label = (Label) button.getOwnedParts().get("label");
        try {
            button.setThemeOverride(Label.FONT_SIZE, 16.0F);
            check(label.getThemeItem(Label.FONT_SIZE) == 16.0F && label.getThemeOverride(Label.FONT_SIZE) == null,
                    "Button's retained Label inherits size without copying it");
            check(button.measureHeight(FONT, 200) == 22, "Button minimum follows optical ink plus themed margin");
            check(button.getThemeKeys().stream().anyMatch(key -> key.getId().equals(Label.FONT_SIZE.getId())), "Inspector includes explicit cross-type theme items");
            button.removeThemeOverride(Label.FONT_SIZE);
            check(label.getThemeItem(Label.FONT_SIZE) == 8.0F && button.measureHeight(FONT, 200) == Button.HEIGHT,
                    "Button restore reaches its already-created Label");
        } finally { free(button); }
    }

    private static void titles() {
        Navbar navbar = new Navbar(Component.literal("Title"));
        Label extra = new Label(Component.literal("Body"));
        navbar.setExtra(extra);
        Window window = Window.embedded().setContent(navbar);
        try {
            navbar.setThemeOverride(OreTheme.TITLE_FONT_SIZE, 40.0F);
            for (int width : new int[]{127, 128, 129, 640}) {
                window.layoutViewport(width, 180, FONT);
                check(navbar.getChild(0).getThemeItem(Label.FONT_SIZE) == 40.0F, "Navbar title uses the title role");
                check(navbar.getChild(0).getBounds().height() >= 50, "Large themed title contributes to row height");
                check(extra.getThemeItem(Label.FONT_SIZE) == 8.0F, "Title size does not resize normal extras");
            }
        } finally { free(window); }

        checkTitle(new Dialog(Component.literal("Dialog")), new Label(Component.literal("Body")));
        checkTitle(io.github.luyingyilong.ore.control.overlay.OverlayTestAccess.modal(Component.literal("Modal")), new Label(Component.literal("Body")));
        checkTitle(io.github.luyingyilong.ore.control.overlay.OverlayTestAccess.drawer(Component.literal("Drawer"), () -> 0L, cue -> { }).setAnimationDuration(0), new Label(Component.literal("Body")));
    }

    private static void checkTitle(Control popup, Label body) {
        if (popup instanceof Dialog dialog) dialog.setContent(body);
        else if (popup instanceof Modal modal) modal.setContent(body);
        else if (popup instanceof Drawer drawer) { drawer.setContent(body);drawer.open(); }
        popup.setThemeOverride(OreTheme.TITLE_FONT_SIZE, 16.0F);
        Overlay overlay = new Overlay();overlay.addChild(popup);
        Window window = Window.embedded().setContent(new Control()).setOverlay(overlay);
        try {
            window.layoutViewport(320, 180, FONT);
            Label title = findHero(popup);
            check(title != null && title.getThemeItem(Label.FONT_SIZE) == 16.0F, popup.getClass().getSimpleName() + " title inherits local theme override");
            check(title.getThemeOverride(Label.FONT_SIZE) == null && body.getThemeItem(Label.FONT_SIZE) == 8.0F,
                    "Title role remains distinct from body typography");
            popup.removeThemeOverride(OreTheme.TITLE_FONT_SIZE);
            window.layoutViewport(320, 180, FONT);
            check(title.getThemeItem(Label.FONT_SIZE) == 8.0F, "Popup title restores inherited size");
        } finally { free(window); }
    }

    private static Label findHero(Control node) {
        if (node instanceof Label label && label.getHero()) return label;
        for (Control child : node.getChildren()) { Label found = findHero(child);if (found != null) return found; }
        return null;
    }

    private static void descriptions() {
        Descriptions details = new Descriptions().setTitle(Component.literal("Details"))
                .addItem("first", Component.literal("Name"), Component.literal("Value"));
        try {
            Label title = details.getTitleNode();
            details.setThemeOverride(Label.FONT_SIZE, 12.0F);
            details.setThemeOverride(Descriptions.TITLE_FONT_SIZE, 16.0F);
            details.addItem("later", Component.literal("Added"), Component.literal("Later"));
            for (DescriptionItem item : details.getItems()) {
                check(item.getLabelNode().getThemeItem(Label.FONT_SIZE) == 12.0F && item.getContentNode().getThemeItem(Label.FONT_SIZE) == 12.0F,
                        "Current/future description fields inherit live size");
                check(item.getLabelNode().getThemeOverride(Label.FONT_SIZE) == null && item.getContentNode().getThemeOverride(Label.FONT_SIZE) == null,
                        "Descriptions creates no per-label copied font overrides");
            }
            check(title.getThemeItem(Label.FONT_SIZE) == 16.0F, "Descriptions title has an independent theme role");
            details.removeThemeOverride(Label.FONT_SIZE);
            check(details.getItem("later").getContentNode().getThemeItem(Label.FONT_SIZE) == 8.0F && title.getThemeItem(Label.FONT_SIZE) == 16.0F,
                    "Restoring body size keeps explicit title size");
            details.removeThemeOverride(Descriptions.TITLE_FONT_SIZE);
            check(title.getThemeItem(Label.FONT_SIZE) == 8.0F, "Restoring title reaches default without a stale field");
        } finally { free(details); }
    }

    private static void removedApi() {
        Set<String> old = Set.of("setFontSize", "getFontSize", "clearFontSize", "setTitleFontSize", "getTitleFontSize");
        for (Class<?> type : List.of(Label.class, Button.class, Navbar.class, Dialog.class, Modal.class, Drawer.class, Descriptions.class))
            for (var method : type.getMethods()) check(!old.contains(method.getName()), "No duplicate font-size API: " + type.getSimpleName());
    }
    private static void free(Control node) { node.queueFree();LifecycleScheduler.flush(); }
    private static void rejects(Runnable action) {
        try { action.run();throw new AssertionError("Invalid size accepted"); } catch (IllegalArgumentException expected) { }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
