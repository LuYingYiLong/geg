package io.github.luyingyilong.ore.control.navigation;

import io.github.luyingyilong.ore.control.base.*;
import io.github.luyingyilong.ore.control.layout.Scroll;
import io.github.luyingyilong.ore.event.EventGeometry;
import io.github.luyingyilong.ore.event.KeyEvent;
import io.github.luyingyilong.ore.event.WindowInputs;
import io.github.luyingyilong.ore.render.Transform2D;
import io.github.luyingyilong.ore.theme.*;
import io.github.luyingyilong.ore.window.Window;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/** Public item contracts, state transitions and real Window input routing. */
public final class TabsTest {
    private static final Font FONT = new Font(id -> null, false) {
        @Override public int width(FormattedText text) { return text.getString().length() * 6; }
    };

    public static void main(String[] args) {
        contracts(); interaction(); retention(); layoutAndThemes(); iconsAndMaximumWidth(); reentry();
        System.out.println("ORE_TABS_PASS");
    }

    private static TabItem item(String key) { return new TabItem(key, Component.literal(key), new Control()); }
    private static TabItem disabled(String key) { return new TabItem(key, Component.literal(key), new Control(), null, true); }
    private static void free(Control control) { control.queueFree(); LifecycleScheduler.flush(); }

    private static void contracts() {
        Tabs tabs = new Tabs();
        List<String> changes = new ArrayList<>();
        tabs.selectionChanged().connect(tabs, changes::add);
        try {
            expect(IllegalArgumentException.class, () -> new TabItem(" ", Component.empty(), new Control()));
            tabs.addItem(disabled("locked")); check(tabs.getSelectedKey() == null, "All disabled has no page");
            TabItem a = item("a"), b = item("b"), c = item("c");
            tabs.addItem(a).addItem(b).addItem(c);
            check(changes.equals(List.of("a")), "First enabled page selected once");
            var snapshot = tabs.getItems();
            expect(UnsupportedOperationException.class, () -> snapshot.clear());
            expect(IllegalArgumentException.class, () -> tabs.addItem(item("a")));
            expect(IllegalStateException.class, () -> tabs.addItem(new TabItem("duplicate-content", Component.empty(), a.getContent())));
            Control owner = new Control(), owned = owner.ownPart("owned", new Control());
            expect(IllegalStateException.class, () -> tabs.addItem(new TabItem("owned", Component.empty(), owned)));
            check(tabs.getItems().size() == 4 && owner.getOwnedParts().get("owned") == owned, "Rejected ownership leaves both trees unchanged");
            free(owner);
            Control parent = new Control(), attached = new Control(); parent.addChild(attached);
            expect(IllegalStateException.class, () -> tabs.setItem(new TabItem("b", Component.empty(), attached)));
            check(tabs.getItem("b") == b && attached.getParent() == parent, "Replacement preflight preserves old page");
            free(parent);
            expect(IllegalArgumentException.class, () -> tabs.setSelectedKey("missing"));
            expect(IllegalArgumentException.class, () -> tabs.setSelectedKey("locked"));
            expect(IllegalArgumentException.class, () -> tabs.setSelectedKey(null));
            tabs.setSelectedKeyNoSignal("b"); check(changes.size() == 1, "NoSignal still switches without notification");
            tabs.setSelectedKey("b"); check(changes.size() == 1, "Same key does not notify");
            tabs.moveItem("b", 0);
            check(tabs.getSelectedKey().equals("b") && changes.size() == 1, "Reorder retains key and selection");
            check(snapshot.get(0).getKey().equals("locked") && tabs.getItems().get(0).getKey().equals("b"), "Items getter is an immutable snapshot");
            tabs.setItem(new TabItem("b", Component.literal("renamed"), b.getContent(), null, true));
            check(tabs.getSelectedKey().equals("a"), "Disabled active page selects next enabled neighbor");
            TabItem removed = tabs.removeItem("a");
            check(removed == a && removed.getContent().getParent() == null && removed.getContent().isAlive(), "Removal detaches user content for reuse");
            check(tabs.getSelectedKey().equals("c"), "Removal prefers following page");
            free(removed.getContent());
            tabs.removeItem("c").getContent().queueFree();
            check(tabs.getSelectedKey() == null && changes.get(changes.size() - 1) == null, "No enabled pages emits a cleared selection");
            check(tabs.removeItem("missing") == null, "Unknown removal is harmless");
            Control old = tabs.getItem("b").getContent(), replacement = new Control();
            tabs.setItem(new TabItem("b", Component.empty(), replacement));
            check(old.isAlive() && old.getParent() == null && replacement.getParent() != null, "Replacing content never disposes old content");
            free(old);
        } finally { free(tabs); }
        Tabs cycle = new Tabs(); Control parent = new Control(); parent.addChild(cycle);
        expect(IllegalArgumentException.class, () -> cycle.addItem(new TabItem("cycle", Component.empty(), parent)));
        check(parent.getChild(0) == cycle && cycle.getItems().isEmpty(), "Cycle rejection never temporarily reparents");
        free(parent);
        Tabs themed = new Tabs(); Control source = new Control(); themed.setThemeParent(source);
        expect(IllegalArgumentException.class, () -> themed.addItem(new TabItem("theme-cycle", Component.empty(), source)));
        check(source.getParent() == null && themed.getItems().isEmpty(), "Theme attachment rejection is atomic");
        themed.setThemeParent(null); free(themed); free(source);
    }

    private static Rect caption(Tabs tabs, String key) { return EventGeometry.displayed(tabs.entry(key).caption); }
    private static void click(Window root, Rect rect) {
        WindowInputs.press(root, rect.x() + 3, rect.y() + 5, null);
        WindowInputs.release(root, rect.x() + 3, rect.y() + 5);
    }

    private static void interaction() {
        Tabs tabs = new Tabs().addItem(item("a")).addItem(disabled("locked")).addItem(item("b")).addItem(item("c"));
        Window root = Window.embedded().setContent(tabs);
        int[] changed = {0}, activated = {0};
        tabs.selectionChanged().connect(root, key -> changed[0]++);
        tabs.tabActivated().connect(root, key -> activated[0]++);
        root.layoutViewport(240, 100, FONT);
        try {
            Rect b = caption(tabs, "b");
            WindowInputs.press(root, b.x() + 3, 5, null);
            check(tabs.getSelectedKey().equals("a"), "Press only arms the target");
            WindowInputs.release(root, b.x() + 3, 5);
            check(tabs.getSelectedKey().equals("b") && changed[0] == 1 && activated[0] == 1, "Matching release selects exactly once");
            click(root, b); check(changed[0] == 1 && activated[0] == 2, "Selected tab activates without a second change");
            click(root, caption(tabs, "locked")); check(activated[0] == 2, "Disabled tab is inert");
            WindowInputs.press(root, 3, 5, null); WindowInputs.release(root, 230, 90);
            check(tabs.getSelectedKey().equals("b"), "Drag out cancels activation");
            WindowInputs.press(root, 3, 5, null); tabs.setSelectedKey("c"); WindowInputs.release(root, 3, 5);
            check(tabs.getSelectedKey().equals("c") && activated[0] == 2, "Code switch cancels an old pointer release");
            root.getFocusManager().requestFocus(tabs.bar, null);
            WindowInputs.keyDown(root, GLFW.GLFW_KEY_HOME, 0, null);
            check(tabs.bar.focusedKey.equals("a") && tabs.getSelectedKey().equals("c"), "Home changes focus without activation");
            WindowInputs.keyUp(root, GLFW.GLFW_KEY_HOME, 0, null);
            WindowInputs.keyDown(root, GLFW.GLFW_KEY_RIGHT, 0, null);
            WindowInputs.keyUp(root, GLFW.GLFW_KEY_RIGHT, 0, null);
            check(tabs.bar.focusedKey.equals("b"), "Arrow navigation skips disabled tabs");
            WindowInputs.keyDown(root, GLFW.GLFW_KEY_ENTER, 0, null);
            WindowInputs.key(root, KeyEvent.Action.REPEAT, GLFW.GLFW_KEY_ENTER, 0, 0, null);
            WindowInputs.keyUp(root, GLFW.GLFW_KEY_ENTER, 0, null);
            check(tabs.getSelectedKey().equals("b") && activated[0] == 3, "Keyboard releases activate once despite repeat");
            WindowInputs.keyDown(root, GLFW.GLFW_KEY_SPACE, 0, null);
            tabs.removeItem("b").getContent().queueFree();
            WindowInputs.keyUp(root, GLFW.GLFW_KEY_SPACE, 0, null);
            check(activated[0] == 3, "Removed/rebound header cannot consume old activation");
            root.getFocusManager().requestFocus(tabs.bar, null);
            WindowInputs.keyDown(root, GLFW.GLFW_KEY_END, 0, null); WindowInputs.keyUp(root, GLFW.GLFW_KEY_END, 0, null);
            check(tabs.bar.focusedKey.equals("c"), "End targets the last enabled header");
        } finally { free(root); }
    }

    private static final class Probe extends Control {
        int ready, exits, freed;
        @Override protected void notification(ControlNotification notification) {
            switch (notification.type()) {
                case READY -> ready++;
                case EXIT_TREE -> exits++;
                case PRE_FREE -> freed++;
                default -> { }
            }
        }
    }

    private static void retention() {
        Probe first = new Probe(), second = new Probe();
        Tabs tabs = new Tabs().addItem(new TabItem("a", Component.literal("A"), first))
                .addItem(new TabItem("b", Component.literal("B"), second));
        Window root = Window.embedded().setContent(tabs);
        root.prepareTree(); root.layoutViewport(128, 100, FONT);
        Control caption = tabs.entry("a").caption;
        var parts = caption.getOwnedParts();
        try {
            for (int i = 0; i < 6; i++) {
                tabs.setSelectedKey("b"); tabs.setSelectedKey("a");
                tabs.setItem(new TabItem("a", Component.literal("Title " + i), first));
                tabs.moveItem("a", i % 2);
                root.prepareTree(); root.layoutViewport(128, 100, FONT);
            }
            check(first.ready == 1 && second.ready == 1 && first.exits == 0 && second.exits == 0, "Hidden pages remain in the same MUI/tree session");
            check(first.getVisible() && second.getVisible() && first.getParent().getVisible() && !second.getParent().getVisible(), "Only generated wrappers control page visibility");
            check(!second.isInputAccessible() && first.isInputAccessible(), "Hidden pages cannot receive input or focus");
            check(tabs.entry("a").caption == caption && caption.getOwnedParts().equals(parts), "Title updates and reorder reuse both internal parts");
            second.queueFree("test content deletion"); LifecycleScheduler.flush();
            check(tabs.getItem("b") == null && second.freed == 1, "Permanent content removal unregisters its key once");
        } finally { free(root); }
        check(first.freed == 1 && parts.values().stream().allMatch(Control::isDisposed), "Parent cleanup includes hidden pages and caption parts");
    }

    private static void layoutAndThemes() {
        ThemeDB db = ThemeDB.getDefault(); db.reload(io.github.luyingyilong.ore.support.ThemeTestResources.resources(Map.of()));
        check(db.getDiagnostics().isEmpty(), "Tabs declarations and shipped resources load without diagnostics");
        Tabs tabs = new Tabs();
        for (int i = 0; i < 12; i++) tabs.addItem(new TabItem("tab_" + i, Component.literal("Long tab " + i), new Control()));
        Window root = Window.embedded().setContent(tabs);
        try {
            for (var key : List.of(Tabs.FONT_COLOR, Tabs.FONT_HOVER_COLOR, Tabs.FONT_PRESSED_COLOR, Tabs.FONT_SELECTED_COLOR))
                check(tabs.getThemeItem(key) == 0xFFFFFFFF, "Dark tab textures use white theme text in every enabled state");
            check(tabs.getThemeItem(Tabs.FONT_DISABLED_COLOR) == 0x80FFFFFF, "Disabled tab text retains half-alpha white");
            for (int width : new int[]{127, 128, 129, 480}) {
                check(tabs.desiredSize(FONT, new Size(width, 120)).width() <= width, "Header overflow cannot inflate its outer Scroll");
                root.layoutViewport(width, 120, FONT);
                int firstWidth = tabs.entry("tab_0").caption.getBounds().width();
                int headerHeight = tabs.header.getBounds().height();
                tabs.setSelectedKey("tab_11"); root.layoutViewport(width, 120, FONT);
                check(tabs.getHeaderScrollX() > 0, "Selected header is revealed in a narrow viewport");
                check(!tabs.header.getHorizontalVisible() && !tabs.header.getVerticalVisible(), "Header scrollbars stay hidden");
                check(tabs.entry("tab_0").caption.getBounds().width() == firstWidth, "Selected and normal styles cannot change header widths");
                check(tabs.entry("tab_11").page.getBounds().height() == 120 - headerHeight, "Active page fills remaining height");
                check(tabs.entry("tab_11").caption.getBounds().x() > 0, "Headers remain a single line without width distribution");
                tabs.setSelectedKey("tab_0"); root.layoutViewport(width, 120, FONT);
                WindowInputs.scroll(root, 5, 5, -1, false);
                check(tabs.getHeaderScrollX() > 0, "Ordinary wheel falls back to horizontal overflow");
            }
            var caption = tabs.entry("tab_0").caption;
            tabs.setThemeOverride(Tabs.INDICATOR_WIDTH_RATIO, 0F);
            check(caption.indicator(new Rect(0, 0, 60, 22)).width() == 0, "Local indicator override reaches internal captions");
            tabs.removeThemeOverride(Tabs.INDICATOR_WIDTH_RATIO);
            check(caption.indicator(new Rect(0, 0, 60, 22)).width() == 16, "Indicator defaults match Segmented");
            tabs.setThemeOverride(Tabs.ITEM_GAP, 8); root.layoutViewport(128, 120, FONT);
            check(tabs.entry("tab_1").caption.getBounds().x() == caption.getBounds().right() + 8, "Themed gaps participate in layout");
            expect(IllegalArgumentException.class, () -> tabs.setRenderTransform(new Transform2D(0, 0, 45, 1, 0.5, 0.5)));
            check(tabs.getRenderTransform().equals(Transform2D.IDENTITY), "Header scissor observes transform limitations atomically");
        } finally { free(root); }
    }

    private static void iconsAndMaximumWidth() {
        var image = io.github.luyingyilong.ore.render.TextureSource.region(io.github.luyingyilong.ore.render.TextureRegion.full(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("test", "icons/wide.png"), 16, 8));
        Tabs tabs = new Tabs().addItem(new TabItem("long", Component.literal("A very long tab caption"), new Control(), image, false))
                .addItem(item("next"));
        Window root = Window.embedded().setContent(tabs);
        try {
            int natural = tabs.bar.measureWidth(FONT, 128);
            check(tabs.getMaxTabWidth() == 0, "Tab widths are uncapped by default");
            tabs.setMaxTabWidth(64); root.layoutViewport(128, 100, FONT);
            var caption = tabs.entry("long").caption;
            check(caption.getBounds().width() == 64 && tabs.bar.measureWidth(FONT, 128) < natural, "Maximum width caps actual layout and scroll extent");
            var label = (io.github.luyingyilong.ore.control.display.Label) caption.getOwnedParts().get("label");
            var icon = caption.getOwnedParts().get("icon");
            var parts = caption.getOwnedParts();
            for (var key : List.of(Tabs.NORMAL, Tabs.HOVER, Tabs.PRESSED, Tabs.SELECTED, Tabs.DISABLED)) {
                var style = tabs.getThemeItem(key); Rect area = new Rect(10, 20, 64, 22), content = style.getContentDrawRect(area);
                caption.layoutContents(area, style, FONT);
                check(icon.getBounds().equals(new Rect(icon.getBounds().x(), content.y() + (content.height() - 4) / 2 - 2, 8, 4)),
                        "Every icon state shares the caption's -2px optical offset");
                check(label.getBounds().y() == content.y() - 2 && label.getBounds().width() <= content.width() - 12,
                        "Ellipsized label reserves image and gap without shifting the caption baseline");
                check(label.getOverflow() == io.github.luyingyilong.ore.control.display.Label.Overflow.ELLIPSIS, "Long tab captions use ELLIPSIS");
                check(label.getMaxLines() == 1, "Tab captions remain on a single line");
            }
            check(caption.getOwnedParts().equals(parts), "Presentation updates reuse image and label nodes");
            expect(IllegalArgumentException.class, () -> tabs.setMaxTabWidth(-1));
            check(tabs.getMaxTabWidth() == 64, "Invalid width leaves last valid value intact");
            tabs.setMaxTabWidth(1); root.layoutViewport(128, 100, FONT);
            check(caption.getBounds().width() == 1, "Explicit cap also bounds tabs smaller than their decoration");
            caption.layoutContents(caption.getAbsoluteBounds(), tabs.getThemeItem(Tabs.SELECTED), FONT);
            check(label.getBounds().width() == 0, "Very small caps keep nonnegative text geometry");
            tabs.setMaxTabWidth(0); root.layoutViewport(128, 100, FONT);
            check(tabs.bar.measureWidth(FONT, 128) == natural, "Zero restores natural widths");
        } finally { free(root); }
    }

    private static void reentry() {
        Tabs tabs = new Tabs().addItem(item("a")).addItem(item("b"));
        List<String> events = new ArrayList<>();
        tabs.selectionChanged().connect(tabs, key -> { events.add(key); if (Objects.equals(key, "b")) tabs.setSelectedKey("a"); });
        tabs.setSelectedKey("b");
        check(events.equals(List.of("b", "a")) && tabs.getSelectedKey().equals("a")
                && tabs.entry("a").page.getVisible() && !tabs.entry("b").page.getVisible(), "Reentrant signals observe committed tree and selection");
        free(tabs);
    }

    private static void expect(Class<? extends Throwable> type, Runnable action) {
        try { action.run(); } catch (Throwable error) { if (type.isInstance(error)) return; throw new AssertionError("Wrong failure", error); }
        throw new AssertionError("Expected " + type.getSimpleName());
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
