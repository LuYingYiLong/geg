package io.github.luyingyilong.ore.inspector;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.LifecycleScheduler;
import io.github.luyingyilong.ore.control.display.Label;
import io.github.luyingyilong.ore.control.input.Button;
import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.debug.DebugProperty;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

/** Font previews use the theme read path, preserving the latest base and history. */
public final class InspectorFontThemeTest {
    public static void main(String[] args) {
        Font font = new Font(id -> null, false) {
            @Override public int width(FormattedText text) { return text.getString().length() * 6; }
        };
        Label label = new Label(Component.literal("ABC"));
        Button button = new Button(Component.literal("Button"));
        DebugOverrides overrides = new DebugOverrides();
        DebugBridge.install(new DebugBridge.Backend() {
            @Override public Object value(Control target, String key, Object base) { return overrides.value(target, key, base); }
        });
        try {
            DebugProperty property = InspectorProperties.properties(label).stream()
                    .filter(value -> value.key().equals("theme:Label:font_size")).findFirst().orElseThrow();
            check(InspectorProperties.properties(label).stream().filter(value -> value.key().equals(property.key())).count() == 1,
                    "Font has one explicit theme adapter, without reflected legacy getter");
            overrides.set(label, property.key(), property.parse("16"));
            check(label.getThemeItem(Label.FONT_SIZE) == 16.0F && label.measureWidth(font, 100) == 36, "Theme preview affects real measurement");
            check(label.getThemeOverride(Label.FONT_SIZE) == null && label.getThemeResolution(Label.FONT_SIZE).value() == 8.0F,
                    "Preview does not write a local override or the cached base");
            label.setThemeOverride(Label.FONT_SIZE, 12.0F);
            check(label.getThemeItem(Label.FONT_SIZE) == 16.0F && label.getThemeResolution(Label.FONT_SIZE).value() == 12.0F,
                    "Application updates the base while Inspector retains its preview");
            var diagnostic = DebugBridge.themes(label).items().stream().filter(value -> value.key().equals("Label:font_size")).findFirst().orElseThrow();
            check(diagnostic.value().equals("16.0") && diagnostic.source().contains("Inspector override"), "Theme view reports effective font and preview source");
            for (String invalid : new String[]{"0", "-1", "NaN", "Infinity"}) {
                try { property.parse(invalid);throw new AssertionError("Invalid preview accepted"); } catch (IllegalArgumentException expected) { }
                check(label.getThemeItem(Label.FONT_SIZE) == 16.0F && label.getThemeOverride(Label.FONT_SIZE) == 12.0F,
                        "Invalid draft preserves preview and base");
            }
            overrides.set(label, property.key(), null);
            check(label.getThemeItem(Label.FONT_SIZE) == 12.0F, "Restore exposes latest base");
            overrides.undo();check(label.getThemeItem(Label.FONT_SIZE) == 16.0F, "Undo retains theme preview");
            overrides.redo();check(label.getThemeItem(Label.FONT_SIZE) == 12.0F, "Redo restores latest base");
            label.removeThemeOverride(Label.FONT_SIZE);
            check(label.getThemeItem(Label.FONT_SIZE) == 8.0F, "Ordinary theme restore still works after preview");

            DebugProperty buttonFont = InspectorProperties.properties(button).stream()
                    .filter(value -> value.key().equals("theme:Label:font_size")).findFirst().orElseThrow();
            overrides.set(buttonFont.target(), buttonFont.key(), buttonFont.parse("16"));
            check(button.measureHeight(font, 200) == 22, "Owned Button label preview updates measured optical height");
            overrides.removeTree(button);
            check(button.measureHeight(font, 200) == Button.HEIGHT, "Clearing owned previews restores Button geometry");
        } finally { DebugBridge.install(null);label.queueFree();button.queueFree();LifecycleScheduler.flush(); }
        System.out.println("ORE_INSPECTOR_FONT_THEME_PASS");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
