package io.github.luyingyilong.ore.inspector;
import io.github.luyingyilong.ore.Label;
import io.github.luyingyilong.ore.debug.*;
import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.theme.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class InspectorThemesTest {
    public static void main(String[] args) {
        Theme shared=ThemeDB.getDefault().getTheme(ResourceLocation.fromNamespaceAndPath("test","inspected"));
        shared.set(OreTheme.TEXT_COLOR,0xFF102030);
        Label label=new Label(Component.literal("Preview")); label.setTheme(shared);
        var snapshot=DebugBridge.themes(label);
        check(snapshot.items().stream().anyMatch(i->i.key().equals("Label:color") && i.value().equals("#FF102030") && i.source().equals("own theme")),"Effective theme and source");
        DebugBridge.install(new DebugBridge.Backend() {
            public Object value(Control target,String key,Object base) { return target==label && key.equals("color")?0xFF445566:base; }
        });
        try {
            var effective=DebugBridge.themes(label);
            check(effective.items().stream().anyMatch(i->i.key().equals("Label:color") && i.value().equals("#FF445566") && i.source().startsWith("Inspector override")),"Preview is labelled separately from the latest base");
            InspectorThemePanel panel=new InspectorThemePanel(); panel.update(effective);
            var retained=panel.body().getChildren(); panel.update(effective);
            check(retained.equals(panel.body().getChildren()),"Ordinary snapshots reuse Labels");
            shared.set(OreTheme.TEXT_COLOR,0xFF203040);
            check(label.getColor()==0xFF445566,"Inspector override survives base updates");
            DebugBridge.install(null); check(label.getColor()==0xFF203040,"Restoring reveals latest theme base");
            panel.body().queueFree(); label.queueFree(); LifecycleScheduler.flush();
            check(DebugBridge.themes(label).items().isEmpty(),"Disposed selection has no editable/live theme data");
        } finally { DebugBridge.install(null); }
        System.out.println("ORE_INSPECTOR_THEMES_PASS");
    }
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
