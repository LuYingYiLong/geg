package io.github.luyingyilong.ore.inspector;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.LifecycleScheduler;
import io.github.luyingyilong.ore.debug.DebugBridge;

import io.github.luyingyilong.ore.control.progress.TextureProgressBar;
import io.github.luyingyilong.ore.render.TextureSource;
import io.github.luyingyilong.ore.theme.IconTheme;

public final class InspectorTextureProgressTest {
    public static void main(String[] args) {
        TextureProgressBar bar = new TextureProgressBar().setProgressTexture(TextureSource.themed(IconTheme.LIT_PROGRESS)).setValueNoSignal(25);
        var properties = InspectorProperties.properties(bar);
        var value = properties.stream().filter(p -> p.key().equals("value")).findFirst().orElseThrow();
        var min = properties.stream().filter(p -> p.key().equals("min")).findFirst().orElseThrow();
        var opacity = properties.stream().filter(p -> p.key().equals("opacity")).findFirst().orElseThrow();
        var angle = properties.stream().filter(p -> p.key().equals("radialInitialAngle")).findFirst().orElseThrow();
        check(angle.parse("30.1").equals(30.1), "valid decimal angles are not rounded during preview");
        check(value.parse("80").equals(80.0) && bar.getValue() == 25, "setter validation does not mutate source values");
        reject(() -> min.parse("100")); reject(() -> opacity.parse("1.1"));
        DebugOverrides overrides = new DebugOverrides();
        DebugBridge.install(new DebugBridge.Backend() {
            @Override public Object value(Control node, String key, Object base) { return overrides.value(node, key, base); }
        });
        int[] notifications = {0}; bar.valueChanged().connect(bar, v -> notifications[0]++);
        try {
            overrides.set(bar, "value", value.parse("80")); check(bar.getValue() == 80 && notifications[0] == 0, "preview does not emit business changes");
            overrides.undo(); check(bar.getValue() == 25, "undo restores base value");
            overrides.redo(); check(bar.getValue() == 80, "redo restores preview");
            overrides.set(bar, "progressOffsetX", 3); check(bar.getProgressOffset().x() == 3, "composite offset adapter");
            overrides.set(bar, "fillMode", TextureProgressBar.FillMode.CLOCKWISE); check(bar.getFillMode() == TextureProgressBar.FillMode.CLOCKWISE, "enum preview");
            bar.setValueNoSignal(42); overrides.removeTree(bar); check(bar.getValue() == 42, "restore uses newest base value");
            check(DebugBridge.themes(bar).items().stream().anyMatch(item -> item.key().equals("TextureProgressBar:progress_tint")), "theme tint source visible");
        } finally { DebugBridge.install(null); bar.queueFree(); LifecycleScheduler.flush(); }
        System.out.println("ORE_INSPECTOR_TEXTURE_PROGRESS_PASS");
    }
    private static void reject(Runnable action) { try { action.run(); throw new AssertionError("Expected invalid preview rejection"); } catch (IllegalArgumentException expected) { } }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
