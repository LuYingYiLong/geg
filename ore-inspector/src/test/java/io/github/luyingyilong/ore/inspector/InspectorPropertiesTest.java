package io.github.luyingyilong.ore.inspector;

import io.github.luyingyilong.ore.Button;
import io.github.luyingyilong.ore.Label;
import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.debug.DebugProperty;
import io.github.luyingyilong.ore.debug.Inspectable;
import io.github.luyingyilong.ore.layout.*;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Discovery, real-setter validation and unchanged session-only overlay semantics. */
public final class InspectorPropertiesTest {
    public static void run() {
        Ordinary ordinary = new Ordinary();
        List<DebugProperty> first = InspectorProperties.properties(ordinary);
        check(first.stream().anyMatch(p -> p.key().equals("sizeFlagsHorizontal")), "Inherited annotated getters");
        check(first.stream().noneMatch(p -> p.key().equals("secret")), "Unannotated public properties stay private to application");
        check(first.stream().map(DebugProperty::key).toList().equals(
                InspectorProperties.properties(ordinary).stream().map(DebugProperty::key).toList()), "Stable property order across cached discovery");
        DebugProperty count = property(ordinary, "count");
        ordinary.layout(new Rect(0, 0, 20, 20), null);
        check(count.parse("8").equals(8) && ordinary.rawCount == 2 && !ordinary.layoutDirty(), "Setter preview cannot mutate base or layout");
        rejects(() -> count.parse("7")); // custom setter constraint absent from annotation
        check(ordinary.rawCount == 2, "Setter rejection leaves base intact");
        ordinary.count(4);
        check(ordinary.rawCount == 4, "Validation scope is always cleared on failure");
        rejects(() -> count.parse("11"));
        rejects(() -> count.parse("1.25"));
        rejects(() -> property(ordinary, "sizeFlagsHorizontal").parse("7"));
        rejects(() -> property(ordinary, "sizeFlagsStretchRatio").parse("NaN"));
        rejects(() -> property(ordinary, "sizeFlagsStretchRatio").parse("0"));
        rejects(() -> property(ordinary, "wide").parse("9223372036854775808"));
        check(property(ordinary, "wide").parse("9223372036854775807").equals(Long.MAX_VALUE), "Long values retain exact precision");
        rejects(() -> property(ordinary, "integer").parse("2147483648"));
        check(property(ordinary, "enabled").parse("true").equals(true), "Boolean discovery");
        rejects(() -> property(ordinary, "enabled").parse("yes"));
        check(property(ordinary, "caption").parse("preview").equals("preview"), "String and alternate setter discovery");
        rejects(() -> property(ordinary, "caption").parse("too much text"));
        rejects(() -> property(ordinary, "normalized").parse("-1"));

        Label label = new Label(Component.translatable("gui.geg.market.title"));
        Component source = label.text();
        Object preview = property(label, "text").parse("Temporary text");
        check(preview instanceof Component && label.text() == source, "Text preview preserves original translated Component");
        check(property(label, "color").parse("#ffffff").equals(0xFFFFFFFF), "RGB keeps opaque alpha");
        check(property(label, "color").parse("#80000000").equals(0x80000000), "ARGB keeps transparency");
        rejects(() -> property(label, "fontSize").parse("0"));
        property(label, "hero").parse("true");
        property(label, "scale").parse("1.5");
        property(label, "scrollEndPauseMs").parse("900");
        property(label, "scrollTrigger").parse("ALWAYS");
        rejects(() -> property(label, "scrollTrigger").parse("INVALID"));
        property(new Button(), "type").parse("TEXT");
        property(new Grid<>(), "mode").parse("MASONRY");
        property(new Scroll(), "embedded").parse("true");

        DebugProperty explicit = property(new Explicit(), "count");
        check(explicit.max() == 4 && InspectorProperties.properties(new Explicit()).stream()
                .filter(p -> p.key().equals("count")).count() == 1, "Explicit adapter takes precedence without duplicate entries");
        rejects(() -> explicit.parse("6"));
        rejects(() -> InspectorProperties.properties(new MissingSetter()));
        rejects(() -> InspectorProperties.properties(new InvalidSignature()));
        rejects(() -> InspectorProperties.properties(new DuplicateKey()));

        DebugOverrides changes = new DebugOverrides();
        DebugBridge.install(new DebugBridge.Backend() {
            @Override public Object value(Control c, String key, Object base) { return changes.value(c, key, base); }
        });
        try {
            changes.set(ordinary, "count", count.parse("8"));
            ordinary.count(6);
            check(ordinary.count() == 8 && ordinary.rawCount == 6, "Reflection edit overlays latest application base");
            changes.set(ordinary, "count", null);
            check(ordinary.count() == 6, "Reset restores latest base");
            changes.undo(); check(ordinary.count() == 8, "Undo restores annotation edit");
            changes.redo(); check(ordinary.count() == 6, "Redo restores latest base");
            changes.set(ordinary, "count", count.parse("10"));
            changes.removeTree(ordinary);
            changes.undo(); check(ordinary.count() == 6, "Removed binding cannot regain edits through undo");
        } finally { DebugBridge.install(null); }
        System.out.println("GEG_INSPECTOR_REFLECTION_PASS");
    }

    public static class Ordinary extends Control {
        private int rawCount = 2;
        @Inspectable(min = 0, max = 10)
        public int count() { return DebugBridge.value(this, "count", rawCount); }
        public Ordinary count(int count) {
            if ((count & 1) != 0) throw new IllegalArgumentException("Even values only");
            if (DebugBridge.previewWrite(this, "count", count)) return this;
            rawCount = count; requestLayout(); return this;
        }
        public String secret() { return "not exposed"; }
        @Inspectable public long wide() { return 0L; }
        public Ordinary wide(long value) { DebugBridge.previewWrite(this, "wide", value); return this; }
        @Inspectable public int integer() { return 0; }
        public Ordinary integer(int value) { DebugBridge.previewWrite(this, "integer", value); return this; }
        @Inspectable public boolean enabled() { return false; }
        public Ordinary enabled(boolean value) { DebugBridge.previewWrite(this, "enabled", value); return this; }
        @Inspectable(setter = "setCaption", max = 8) public String caption() { return "base"; }
        public Ordinary setCaption(String value) { DebugBridge.previewWrite(this, "caption", value); return this; }
        @Inspectable public int normalized() { return 0; }
        public Ordinary normalized(int value) { DebugBridge.previewWrite(this, "normalized", Math.max(0, value)); return this; }
    }

    public static final class Explicit extends Ordinary {
        @Override public void debugProperties(List<DebugProperty> out) {
            super.debugProperties(out);
            out.add(DebugProperty.number(this, "count", "Layout", this::count, 0, 4));
        }
    }
    public static final class MissingSetter extends Control {
        @Inspectable public int missing() { return 1; }
    }
    public static final class InvalidSignature extends Control {
        @Inspectable public int invalid(int value) { return value; }
    }
    public static final class DuplicateKey extends Control {
        @Inspectable(key = "same") public int first() { return 0; }
        @Inspectable(key = "same") public int second() { return 0; }
        public void first(int value) { }
        public void second(int value) { }
    }

    private static DebugProperty property(Control target, String key) {
        return InspectorProperties.properties(target).stream().filter(p -> p.key().equals(key)).findFirst().orElseThrow();
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void rejects(Runnable action) {
        try { action.run(); throw new AssertionError("Expected invalid value to be rejected"); }
        catch (IllegalArgumentException expected) { }
    }
}
