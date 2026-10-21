package io.github.luyingyilong.ore.control.base;

import io.github.luyingyilong.ore.control.display.DescriptionItem;
import io.github.luyingyilong.ore.control.display.Descriptions;
import io.github.luyingyilong.ore.control.display.Label;
import io.github.luyingyilong.ore.control.display.TextureRect;
import io.github.luyingyilong.ore.control.layout.Box;
import io.github.luyingyilong.ore.control.layout.Scroll;
import io.github.luyingyilong.ore.control.overlay.Dialog;
import io.github.luyingyilong.ore.control.overlay.Modal;
import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.signal.SignalDiagnostics;
import io.github.luyingyilong.ore.support.InventoryTestSupport;
import io.github.luyingyilong.ore.theme.StyleBoxEmpty;
import io.github.luyingyilong.ore.theme.Theme;
import io.github.luyingyilong.ore.theme.ThemeDB;
import io.github.luyingyilong.ore.theme.ThemeKey;
import io.github.luyingyilong.ore.window.Window;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Ownership, responsive reuse and exactly-once overlay drawing through the real Control entry. */
public final class RetainedPartsTest {
    public static void main(String[] args) {
        InventoryTestSupport.initializeRegistries();
        descriptions();
        dialogOwnership();
        overlays();
        System.out.println("ORE_RETAINED_PARTS_PASS");
    }

    private static void descriptions() {
        Descriptions details = new Descriptions();
        for (int index = 0; index < 9; index++)
            details.addItem("item" + index, Component.literal("Label"), Component.literal("Value"));
        Scroll scroll = new Scroll().setHorizontal(Scroll.BarMode.DISABLED).setContent(details);
        List<DescriptionItem> items = details.getItems();
        List<Label> labels = items.stream().map(DescriptionItem::getContentNode).toList();
        Rect area = new Rect(0, 0, 204, 30);
        // Resolve the initial shared theme subscriptions before checking steady-state allocations.
        details.measureSize(InventoryTestSupport.FONT, new Size(198, 100));
        Control marker0 = new Control(), marker1 = new Control();
        long ordinaryAllocation = SignalDiagnostics.ownerId(marker1) - SignalDiagnostics.ownerId(marker0);
        try {
            // Neither provisional measurement nor a changed measurement width may mutate the tree.
            List<Control> original = details.getChild(0).getChildren();
            for (int width : new int[]{198, 204, 304, 127, 128, 129})
                details.measureSize(InventoryTestSupport.FONT, new Size(width, 100));
            Control afterMeasure = new Control();
            check(SignalDiagnostics.ownerId(afterMeasure) - SignalDiagnostics.ownerId(marker1) == ordinaryAllocation,
                    "Descriptions measurement creates no controls or signals");
            check(details.getChild(0).getChildren().equals(original), "Measurement preserves the actual tree");
            afterMeasure.queueFree();

            // Warm both sides of the scrollbar breakpoint, including the partial-row spacer.
            for (int width : new int[]{198, 204, 304})
                details.layout(new Rect(0, 0, width, 100), InventoryTestSupport.FONT);
            scroll.layout(area, InventoryTestSupport.FONT);
            Set<Control> retained = owned(details);
            List<Control> rows = details.getChild(0).getChildren().stream().filter(Box.class::isInstance).toList();
            Control before = new Control();
            for (int iteration = 0; iteration < 30; iteration++) {
                scroll.layout(area, InventoryTestSupport.FONT);
                for (int width : new int[]{304, 204, 198, 127, 128, 129})
                    details.layout(new Rect(0, 0, width, 100), InventoryTestSupport.FONT);
                LifecycleScheduler.flush();
            }
            Control after = new Control();
            check(SignalDiagnostics.ownerId(after) - SignalDiagnostics.ownerId(before) == ordinaryAllocation,
                    "Scrollbar probes and responsive relayout reuse all row and spacer controls");
            check(owned(details).equals(retained), "Responsive relayout preserves every owned instance");
            check(details.getItems().equals(items) && items.stream().map(DescriptionItem::getContentNode).toList().equals(labels),
                    "Responsive relayout retains items and their internal Labels");
            check(details.getChild(0).getChildren().stream().filter(Box.class::isInstance).toList().equals(rows),
                    "Retained row order stays stable");
            scroll.layout(area, InventoryTestSupport.FONT);
            check(scroll.getViewport().width() == 198 && items.get(0).getParent() != items.get(1).getParent(),
                    "The final narrow viewport uses one column");
            details.getItem("item0").setVisible(false);
            details.layout(new Rect(0, 0, 304, 100), InventoryTestSupport.FONT);
            details.getItem("item0").setVisible(true);
            details.layout(new Rect(0, 0, 304, 100), InventoryTestSupport.FONT);
            check(details.getItems().equals(items), "Visibility changes preserve the keyed item index");
            scroll.queueFree();
            LifecycleScheduler.flush();
            check(retained.stream().allMatch(Control::isDisposed), "Hidden rows and detached pooled spacers are freed with Descriptions");
            before.queueFree(); after.queueFree();
        } finally {
            scroll.queueFree(); marker0.queueFree(); marker1.queueFree();
            LifecycleScheduler.flush();
        }
    }

    private static void dialogOwnership() {
        Dialog empty = new Dialog(Component.empty());
        Control body = empty.getOwnedParts().get("body");
        check(body != null && body.getParent() == null, "The unused Dialog body has explicit ownership");
        empty.queueFree();
        check(body.isQueuedForDeletion(), "The unused body joins its owner's deletion request immediately");
        LifecycleScheduler.flush();
        check(body.isDisposed(), "An empty Dialog frees its unused body");

        Dialog dialog = new Dialog(Component.literal("Title"));
        Control inset = dialog.getOwnedParts().get("body");
        int[] freed = {0};
        Control content = new Control() {
            @Override protected void notification(ControlNotification notification) {
                if (notification.type() == ControlNotification.Type.PRE_FREE) freed[0]++;
            }
        };
        dialog.setContent(content);
        Control replacement = new Control();
        dialog.setContent(replacement);
        check(content.getParent() == null && content.isAlive(), "Replacing content only detaches the caller's old node");
        dialog.setContent(content);
        check(replacement.getParent() == null && replacement.isAlive(), "Content replacement preserves the slot contract");
        inset.getParent().removeChild(inset);
        dialog.queueFree(); dialog.queueFree();
        LifecycleScheduler.flush();
        check(inset.isDisposed() && content.isDisposed() && freed[0] == 1,
                "A detached Dialog body and its subtree are freed exactly once");
        replacement.queueFree(); LifecycleScheduler.flush();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void overlays() {
        ThemeDB db = ThemeDB.getDefault();
        Theme theme = db.getTheme(ResourceLocation.parse("test:retained_parts"));
        for (ThemeKey key : db.getKeys())
            if (key.kind() == ThemeKey.Kind.STYLE) theme.set(key, StyleBoxEmpty.INSTANCE);
        Window window = Window.embedded();
        Graphics graphics = new Graphics();
        Map<Control, Integer> paints = new IdentityHashMap<>();
        DebugBridge.Backend previous = DebugBridge.backend();
        DebugBridge.install(new DebugBridge.Backend() {
            @Override public Object value(Control node, String property, Object base) {
                // Keep real dispatch for Labels and TextureRects without requiring a GPU/font atlas.
                return property.equals("opacity") ? 0.0F : base;
            }
            @Override public void paint(Control node, DrawContext context, int x, int y) {
                paints.merge(node, 1, Integer::sum);
                check(!LifecycleScheduler.flush(), "Overlay painting stays inside the lifecycle guard");
            }
        });
        try (DrawContext context = new DrawContext(graphics, InventoryTestSupport.FONT, window.getContext(),
                new Rect(0, 0, 320, 240), 0, 0, 0)) {
            for (boolean modal : new boolean[]{false, true}) {
                Box body = new Box();
                Label label = new Label(Component.literal("Body"));
                TextureRect image = new TextureRect();
                body.addChild(label); body.addChild(image);
                Control overlay = modal ? new Modal(Component.literal("Title")).setContent(body)
                        : new Dialog(Component.literal("Title")).setContent(body);
                window.ownPart(modal ? "modal" : "dialog", overlay);
                overlay.setTheme(theme);
                overlay.layout(new Rect(0, 0, 320, 240), InventoryTestSupport.FONT);
                Set<Control> retained = owned(overlay);
                Matrix4f pose = new Matrix4f(graphics.pose().last().pose());
                paints.clear(); graphics.fills = 0;
                overlay.draw(context, 0, 0);
                check(paints.get(overlay) == 1 && paints.get(label) == 1 && paints.get(image) == 1,
                        "Overlay, internal Label and TextureRect each report one paint");
                check(paints.values().stream().allMatch(count -> count == 1) && graphics.fills == 1,
                        "The whole retained overlay tree and its mask paint exactly once");
                check(pose.equals(graphics.pose().last().pose()), "Overlay drawing restores its depth scope");
                overlay.setVisible(false); paints.clear(); graphics.fills = 0;
                overlay.draw(context, 0, 0);
                check(paints.isEmpty() && graphics.fills == 0, "Hidden overlays emit no paint or primitives");
                overlay.setVisible(true); overlay.queueFree();
                overlay.draw(null, 0, 0);
                check(paints.isEmpty(), "Queued overlays cannot paint");
                LifecycleScheduler.flush();
                check(retained.stream().allMatch(Control::isDisposed), "Overlay cleanup includes every internal drawing node");
            }
        } finally {
            DebugBridge.install(previous);
            window.queueFree(); LifecycleScheduler.flush();
        }
    }

    private static Set<Control> owned(Control root) {
        Set<Control> result = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Control> pending = new ArrayList<>(List.of(root));
        for (int index = 0; index < pending.size(); index++) {
            Control node = pending.get(index);
            if (result.add(node)) pending.addAll(node.ownedSnapshot());
        }
        return result;
    }

    private static final class Graphics extends GuiGraphics {
        int fills;
        Graphics() { super(null, null); }
        @Override public void flush() { }
        @Override public void fill(int left, int top, int right, int bottom, int color) { fills++; }
        @Override public void enableScissor(int left, int top, int right, int bottom) { }
        @Override public void disableScissor() { }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
