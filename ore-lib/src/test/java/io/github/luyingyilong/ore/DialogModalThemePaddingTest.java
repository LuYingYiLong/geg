package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.theme.*;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/** Theme spacing is checked against live Window layout, including compact and auto-sized panels. */
public final class DialogModalThemePaddingTest {
    private static final Font FONT = new Font(id -> null, false) {
        @Override public int width(FormattedText text) { return text.getString().length() * 6; }
    };

    public static void main(String[] args) {
        ThemeDB db = ThemeDB.getDefault();
        db.reload(ContainerThemePaddingTest.resources(Map.of()));
        check(db.getDiagnostics().isEmpty(), "all section padding declarations load");
        for (boolean modal : new boolean[]{false, true}) {
            defaults(modal);
            overridesAndActions(modal);
            inheritance(modal);
        }
        compactDialog();
        inspectorMinimumOverrides();
        styleMargins();
        reload();
        System.out.println("ORE_DIALOG_MODAL_THEME_PADDING_PASS");
    }

    private static void defaults(boolean modal) {
        try (Fixture f = new Fixture(modal)) {
            for (int width : new int[]{127, 128, 129, 427}) {
                f.layout(width, 240);
                Rect panel = f.panel();
                check(panel.height() == Button.HEIGHT + (modal ? 76 : 78), "default auto height stays unchanged: " + f.popup.getThemeType() + " " + panel
                        + " header=" + f.headerPadding().getBounds() + " body=" + f.bodyPadding().getBounds() + " footer=" + f.footerPadding().getBounds());
                check(f.bodyPadding().getInsets().equals(Padding.Insets.all(8)), "default body padding is applied once");
                check(f.content.getAbsoluteBounds().x() == panel.x() + (modal ? 9 : 8), "default body left edge");
                check(f.content.getBounds().width() == panel.width() - (modal ? 18 : 16), "default body width");
                check(f.action.getAbsoluteBounds().bottom() == panel.bottom() - (modal ? 9 : 8), "default footer bottom");
            }
            f.layout(427, 240);
            check(!f.window.getLayoutDirty(), "stable padding does not continuously invalidate Window layout");
            for (var method : f.popup.getClass().getMethods())
                check(!method.getName().matches("(set|get|clear)(Padding|HeaderPadding|BodyPadding|FooterPadding|HeaderInset|BodyInset|FooterInset)"),
                        "no duplicate section padding accessors");
        }
    }

    private static void overridesAndActions(boolean modal) {
        try (Fixture f = new Fixture(modal)) {
            spacing(f.popup, "header_", new Padding.Insets(2, 3, 4, 5));
            spacing(f.popup, "body_", new Padding.Insets(3, 7, 5, 11));
            spacing(f.popup, "footer_", new Padding.Insets(6, 9, 8, 7));
            if (modal) spacing(f.popup, "", new Padding.Insets(2, 4, 6, 8));
            for (int width : new int[]{127, 128, 129, 427}) {
                f.layout(width, 240);
                Rect panel = f.panel();int frameLeft = modal ? 8 : 0, frameRight = modal ? 4 : 0;
                int frameTop = modal ? 2 : 0, frameBottom = modal ? 6 : 0;
                check(panel.height() == Button.HEIGHT + (modal ? 76 : 66), "all sections contribute to auto height");
                check(f.content.getBounds().height() == 20, "padding does not compress or scale content");
                check(f.content.getAbsoluteBounds().x() == panel.x() + frameLeft + 11, "asymmetric body left edge");
                check(f.content.getBounds().width() == panel.width() - frameLeft - frameRight - 18, "asymmetric body width");
                check(f.headerPadding().getChild(0).getAbsoluteBounds().x() == panel.x() + frameLeft + 5, "header left inset");
                check(f.headerPadding().getChild(0).getAbsoluteBounds().y() == panel.y() + frameTop + 2, "header top inset");
                check(f.action.getAbsoluteBounds().x() == panel.x() + frameLeft + 7, "footer left inset");
                check(f.action.getBounds().width() == panel.width() - frameLeft - frameRight - 16, "footer horizontal insets");
                check(f.action.getAbsoluteBounds().bottom() == panel.bottom() - frameBottom - 8, "footer bottom inset");
                check(f.popup.getThemeOverride(f.key("body_padding_left")) == 11, "typed local override query");
            }
            spacing(f.popup, "footer_", new Padding.Insets(1, 2, 2, 3));
            f.layout(200, 240);
            check(f.footerPadding().getBounds().height() == 3 + Button.HEIGHT, "shrinking footer padding removes old minimum height");
            f.addAction(new Button(Component.empty()));f.layout(200, 240);
            check(f.footerPadding().getBounds().height() == 3 + 2 * Button.HEIGHT + 2, "multiple actions retain their gap and theme insets");
            f.clearActions();f.layout(200, 240);
            check(!f.footerPadding().getVisible() || !f.footerPadding().getParent().getVisible(), "empty footer occupies no space");
            check(f.content.isAlive(), "padding and action changes retain body state");
            Integer before = f.popup.getThemeOverride(f.key("body_padding_left"));
            rejects(() -> f.popup.setThemeOverride(f.key("body_padding_left"), -1));
            rejects(() -> f.popup.setThemeOverride(f.key("body_padding_left"), Integer.MAX_VALUE));
            check(f.popup.getThemeOverride(f.key("body_padding_left")).equals(before), "invalid padding leaves previous override intact");
            spacing(f.popup, "body_", Padding.Insets.all(0));f.layout(200, 240);
            check(f.bodyPadding().getInsets().equals(Padding.Insets.all(0)), "zero body padding is supported");
        }
    }

    private static void inheritance(boolean modal) {
        try (Fixture f = new Fixture(modal)) {
            Theme theme = ThemeDB.getDefault().getTheme(ResourceLocation.fromNamespaceAndPath("test", modal ? "modal_spacing" : "dialog_spacing"));
            theme.set(f.key("body_padding_right"), 5);
            f.window.setTheme(theme);f.layout(200, 240);
            check(f.bodyPadding().getInsets().right() == 5 && f.popup.getThemeOverride(f.key("body_padding_right")) == null, "ancestor theme reaches actual section");
            f.popup.setThemeOverride(f.key("body_padding_right"), 11);
            theme.set(f.key("body_padding_right"), 3);
            check(f.window.getLayoutDirty(), "theme mutation invalidates the containing Window");
            f.layout(200, 240);check(f.bodyPadding().getInsets().right() == 11, "local override wins over updated ancestor");
            f.popup.removeThemeOverride(f.key("body_padding_right"));f.layout(200, 240);
            check(f.bodyPadding().getInsets().right() == 3, "removal restores latest ancestor value");
        }
    }

    private static void compactDialog() {
        try (Fixture f = new Fixture(false)) {
            for (int width : new int[]{127, 128, 129, 427}) {
                f.layout(width, 100);
                check(f.headerPadding().getInsets().top() == 2 && f.footerPadding().getInsets().bottom() == 2,
                        "short viewports preserve compact defaults");
                f.popup.setThemeOverride(Dialog.HEADER_PADDING_TOP, 5);
                f.popup.setThemeOverride(Dialog.FOOTER_PADDING_BOTTOM, 3);
                f.layout(width, 100);
                check(f.headerPadding().getInsets().top() == 5 && f.footerPadding().getInsets().bottom() == 3,
                        "ordinary explicit overrides also win in compact layout");
                check(f.popup.getThemeResolution(Dialog.COMPACT_HEADER_PADDING_TOP).sources().get(0).item().equals(Dialog.HEADER_PADDING_TOP.getId()),
                        "Inspector identifies compact-to-ordinary fallback");
                f.popup.removeThemeOverride(Dialog.HEADER_PADDING_TOP);f.popup.removeThemeOverride(Dialog.FOOTER_PADDING_BOTTOM);
            }
            f.popup.setThemeOverride(Dialog.COMPACT_HEADER_PADDING_TOP, 1);f.layout(200, 100);
            check(f.headerPadding().getInsets().top() == 1, "compact-specific spacing is configurable");
            f.layout(200, 240);check(f.headerPadding().getInsets().top() == 8, "compact override does not alter regular layout");
        }
    }

    private static void styleMargins() {
        for (boolean modal : new boolean[]{false, true}) try (Fixture f = new Fixture(modal)) {
            spacing(f.popup, "body_", Padding.Insets.all(0));spacing(f.popup, "footer_", Padding.Insets.all(0));
            if (modal) spacing(f.popup, "", Padding.Insets.all(0));
            f.popup.setThemeOverride(modal ? OreTheme.MODAL_PANEL : OreTheme.DIALOG_PANEL,
                    new StyleBoxEmpty(new ThemeInsets(2, 3, 4, 5), ThemeInsets.ZERO));
            if (modal) f.popup.setThemeOverride(OreTheme.MODAL_FOOTER,
                    new StyleBoxEmpty(new ThemeInsets(3, 4, 5, 6), ThemeInsets.ZERO));
            f.layout(200, 240);
            check(f.content.getAbsoluteBounds().x() == f.panel().x() + 2, "panel style margin is independent and consumed once");
            check(f.content.getBounds().width() == f.panel().width() - 6, "section padding does not duplicate StyleBox margin");
            check(f.action.getAbsoluteBounds().x() == f.panel().x() + (modal ? 5 : 2), "footer StyleBox margin reaches actions");
            check(f.action.getAbsoluteBounds().bottom() == f.panel().bottom() - (modal ? 11 : 5), "footer StyleBox bottom is counted once");
        }
    }

    private static void inspectorMinimumOverrides() {
        try (Fixture f = new Fixture(true)) {
            f.layout(200, 240);
            Control body = f.bodyPadding(), footer = f.footerPadding().getParent();
            var previous = io.github.luyingyilong.ore.debug.DebugBridge.backend();
            io.github.luyingyilong.ore.debug.DebugBridge.install(new io.github.luyingyilong.ore.debug.DebugBridge.Backend() {
                @Override public Object value(Control node, String key, Object base) {
                    if (key.equals("minimumHeight")) {
                        if (node == body) return 24;
                        if (node == footer) return 48;
                    }
                    return base;
                }
            });
            try {
                f.layout(200, 240);f.layout(200, 240);
                check(body.getMinimumHeight() == 24 && footer.getMinimumHeight() == 48, "Inspector effective minimums remain active");
                check(!f.window.getLayoutDirty(), "Inspector minimum overrides do not cause perpetual layout invalidation");
                f.popup.setThemeOverride(Modal.BODY_PADDING_TOP, 4);
                f.layout(200, 240);f.layout(200, 240);
                check(!f.window.getLayoutDirty(), "new theme minimum is applied once below Inspector override");
            } finally {io.github.luyingyilong.ore.debug.DebugBridge.install(previous);}
            f.layout(200, 240);
            check(body.getMinimumHeight() == 12, "restoring Inspector reveals latest theme-derived minimum");
        }
    }

    private static void reload() {
        ThemeDB db = ThemeDB.getDefault();
        try (Fixture dialog = new Fixture(false);Fixture modal = new Fixture(true)) {
            dialog.layout(200, 240);modal.layout(200, 240);
            db.reload(ContainerThemePaddingTest.resources(Map.of("Dialog:body_padding_left", 10, "Dialog:footer_padding_bottom", 3,
                    "Modal:padding_left", 4, "Modal:body_padding_right", 12, "Modal:footer_padding_top", 2)));
            check(db.getDiagnostics().isEmpty(), "section padding resource reload validates");
            dialog.layout(200, 240);modal.layout(200, 240);
            check(dialog.content.getAbsoluteBounds().x() == dialog.panel().x() + 10, "Dialog reload updates body placement");
            check(dialog.action.getAbsoluteBounds().bottom() == dialog.panel().bottom() - 3, "Dialog reload updates footer placement");
            check(modal.content.getBounds().width() == modal.panel().width() - 25, "Modal reload updates frame and body width together");
            check(modal.footerPadding().getBounds().height() == 10 + Button.HEIGHT, "Modal reload also updates footer minimum");
            dialog.popup.setThemeOverride(Dialog.BODY_PADDING_LEFT, 5);modal.popup.setThemeOverride(Modal.FOOTER_PADDING_TOP, 0);
            db.reload(ContainerThemePaddingTest.resources(Map.of("Dialog:body_padding_left", 20, "Modal:footer_padding_top", 9)));
            dialog.layout(200, 240);modal.layout(200, 240);
            check(dialog.bodyPadding().getInsets().left() == 5 && modal.footerPadding().getInsets().top() == 0, "local overrides survive reload");
        } finally {db.reload(ContainerThemePaddingTest.resources(Map.of()));}
    }

    private static void spacing(Control popup, String section, Padding.Insets insets) {
        ThemeDB db = ThemeDB.getDefault();String type = popup.getThemeType();
        popup.setThemeOverride(db.getKey(type, section + "padding_top", Integer.class), insets.top());
        popup.setThemeOverride(db.getKey(type, section + "padding_right", Integer.class), insets.right());
        popup.setThemeOverride(db.getKey(type, section + "padding_bottom", Integer.class), insets.bottom());
        popup.setThemeOverride(db.getKey(type, section + "padding_left", Integer.class), insets.left());
    }

    private static final class Fixture implements AutoCloseable {
        final boolean modal;
        final Control content = new Control().setMinimum(40, 20);
        final Button action = new Button(Component.empty());
        final Control popup;
        final Window window;
        Fixture(boolean modal) {
            this.modal = modal;
            popup = modal ? new Modal(Component.empty()).setSize(120, 0).setContent(content).addAction(action)
                    : new Dialog(120, 0, 0, Component.empty()).setContent(content).addAction(action);
            Overlay overlay = new Overlay();overlay.addChild(popup);
            window = Window.embedded().setContent(new Control()).setOverlay(overlay);
        }
        void layout(int width, int height) {window.layoutViewport(width, height, FONT);}
        ThemeKey<Integer> key(String name) {return ThemeDB.getDefault().getKey(popup.getThemeType(), name, Integer.class);}
        Rect panel() {return modal ? ((Modal)popup).getPanelBounds() : ((Dialog)popup).getPanelBounds();}
        Padding bodyPadding() {return (Padding)content.getParent();}
        Padding headerPadding() {Control head = bodyPadding().getParent().getChild(0);return (Padding)(modal ? head.getChild(0) : head);}
        Padding footerPadding() {Control column = bodyPadding().getParent();Control footer = column.getChild(column.getChildCount()-1);return (Padding)(modal ? footer.getChild(0) : footer);}
        void addAction(Button button) {if (modal) ((Modal)popup).addAction(button);else ((Dialog)popup).addAction(button);}
        void clearActions() {if (modal) ((Modal)popup).clearActions();else ((Dialog)popup).clearActions();}
        @Override public void close() {window.queueFree();LifecycleScheduler.flush();}
    }

    private static void rejects(Runnable action) {
        try {action.run();throw new AssertionError("Invalid padding accepted");}
        catch (IllegalArgumentException expected) { }
    }
    private static void check(boolean condition, String message) {if (!condition) throw new AssertionError(message);}
}
