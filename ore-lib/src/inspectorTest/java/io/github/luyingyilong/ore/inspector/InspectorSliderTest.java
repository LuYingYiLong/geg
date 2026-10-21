package io.github.luyingyilong.ore.inspector;

import io.github.luyingyilong.ore.Slider;
import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.layout.LifecycleScheduler;

public final class InspectorSliderTest {
    public static void main(String[] args) {
        Slider slider = new Slider().setRangeNoSignal(true).setRangeValueNoSignal(new Slider.RangeValue(20, 80));
        var properties = InspectorProperties.properties(slider);
        var disabled = properties.stream().filter(property -> property.key().equals("disabled")).findFirst().orElseThrow();
        check(disabled.parse("true").equals(true) && !slider.getDisabled(), "preview setter preserves source state");
        check(properties.stream().anyMatch(property -> property.key().equals("reverse")), "annotation discovery");
        DebugOverrides overrides = new DebugOverrides();
        DebugBridge.install(new DebugBridge.Backend() {
            @Override public Object value(io.github.luyingyilong.ore.layout.Control control, String key, Object base) {
                return overrides.value(control, key, base);
            }
        });
        try {
            overrides.set(slider, "disabled", disabled.parse("true"));
            check(slider.getDisabled() && !slider.acceptsFocus(), "effective disable is honored");
            overrides.undo(); check(!slider.getDisabled(), "undo restores effective state");
            overrides.redo(); check(slider.getDisabled(), "redo reapplies preview");
            overrides.removeTree(slider); check(!slider.getDisabled(), "restore source state");
            check(slider.getRangeValue().equals(new Slider.RangeValue(20, 80)), "preview never mutates numeric input");
            check(DebugBridge.themes(slider).items().stream().anyMatch(item -> item.key().equals("Slider:grabber_normal")), "thumb theme source visible");
        } finally { DebugBridge.install(null); slider.queueFree(); LifecycleScheduler.flush(); }
        System.out.println("ORE_INSPECTOR_SLIDER_PASS");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
