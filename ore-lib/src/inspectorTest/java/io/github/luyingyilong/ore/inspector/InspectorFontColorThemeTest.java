package io.github.luyingyilong.ore.inspector;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.LifecycleScheduler;
import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.debug.DebugProperty;

import io.github.luyingyilong.ore.control.input.Button;
import io.github.luyingyilong.ore.control.display.Label;
import net.minecraft.network.chat.Component;

/** Hex color editing keeps alpha, session overrides and latest-base restore semantics. */
public final class InspectorFontColorThemeTest {
    public static void main(String[] args) {
        Label label = new Label(Component.literal("Preview"));
        Button button = new Button(Component.literal("Button"));
        DebugOverrides overrides = new DebugOverrides();
        DebugBridge.install(new DebugBridge.Backend() {
            @Override public Object value(Control target, String key, Object base) { return overrides.value(target, key, base); }
        });
        try {
            DebugProperty color = InspectorProperties.properties(label).stream()
                    .filter(property -> property.key().equals("theme:Label:font_color")).findFirst().orElseThrow();
            check(color.kind().equals("color"), "Color uses a text editor that accepts hex, not NumberInput");
            check(InspectorProperties.properties(label).stream().noneMatch(property -> property.key().equals("color")),
                    "Old getter is not reflected into a second property");
            check(color.parse("#123456").equals(0xFF123456) && color.parse("#80123456").equals(0x80123456), "RGB and ARGB parse losslessly");
            check(color.parse("4294967295").equals(0xFFFFFFFF) && color.parse("-2147483648").equals(0x80000000), "Signed and unsigned integer colors remain exact");
            overrides.set(label, color.key(), color.parse("#80123456"));
            label.setThemeOverride(Label.FONT_COLOR, 0xFF445566);
            check(label.getThemeItem(Label.FONT_COLOR) == 0x80123456 && label.getThemeOverride(Label.FONT_COLOR) == 0xFF445566,
                    "Preview remains separate from the latest application override");
            var diagnostic = DebugBridge.themes(label).items().stream().filter(item -> item.key().equals("Label:font_color")).findFirst().orElseThrow();
            check(diagnostic.value().equals("#80123456") && diagnostic.source().contains("Inspector override"), "Theme view reports effective alpha and preview source");
            for (String invalid : new String[]{"#bad", "#GG112233", "4294967296", "-2147483649", "1.5", "NaN"}) {
                try { color.parse(invalid); throw new AssertionError("Invalid color accepted: " + invalid); }
                catch (IllegalArgumentException expected) { }
                check(label.getThemeItem(Label.FONT_COLOR) == 0x80123456, "Invalid drafts retain the previous valid preview");
            }
            overrides.set(label, color.key(), null);
            check(label.getThemeItem(Label.FONT_COLOR) == 0xFF445566, "Restore sees latest base color");
            overrides.undo(); check(label.getThemeItem(Label.FONT_COLOR) == 0x80123456, "Undo restores the preview");
            overrides.redo(); check(label.getThemeItem(Label.FONT_COLOR) == 0xFF445566, "Redo restores latest base");
            DebugProperty buttonColor = InspectorProperties.properties(button).stream()
                    .filter(property -> property.key().equals("theme:Button:font_color")).findFirst().orElseThrow();
            overrides.set(button, buttonColor.key(), buttonColor.parse("#FF778899"));
            check(button.getThemeItem(Button.FONT_COLOR) == 0xFF778899 && button.getThemeOverride(Button.FONT_COLOR) == null,
                    "Button previews target the state theme without writing a local override");
        } finally {
            DebugBridge.install(null);
            label.queueFree(); button.queueFree(); LifecycleScheduler.flush();
        }
        System.out.println("ORE_INSPECTOR_FONT_COLOR_THEME_PASS");
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
