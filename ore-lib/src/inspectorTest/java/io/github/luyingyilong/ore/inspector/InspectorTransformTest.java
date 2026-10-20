package io.github.luyingyilong.ore.inspector;

import io.github.luyingyilong.ore.*;
import io.github.luyingyilong.ore.debug.*;
import io.github.luyingyilong.ore.layout.*;

public final class InspectorTransformTest {
    public static void main(String[] args) {
        Control control=new Control();Radio radio=new Radio();DebugOverrides overrides=new DebugOverrides();
        DebugBridge.install(new DebugBridge.Backend(){@Override public Object value(Control c,String key,Object base){return overrides.value(c,key,base);}});
        try {
            overrides.set(control,"renderRotation",property(control,"renderRotation").parse("45"));
            overrides.set(control,"renderScale",property(control,"renderScale").parse("2"));
            check(control.getRenderTransform().getRotationDegrees()==45&&control.getRenderTransform().getScale()==2,"typed transform preview");
            control.setRenderRotation(15);check(control.getRenderRotation()==45,"latest source stays under preview");
            control.setRenderTranslationX(9);
            overrides.resetAll();check(control.getRenderRotation()==15&&control.getRenderScale()==1
                    &&control.getRenderTranslationX()==9,"restore latest base without adopting unrelated preview values");
            overrides.undo();check(control.getRenderRotation()==45,"undo restore");overrides.redo();
            Scroll scroll=new Scroll();
            rejects(()->property(scroll,"renderRotation").parse("45"));
            check(scroll.getRenderRotation()==0,"invalid preview retains last valid geometry");
            overrides.set(radio,"iconRotationDegrees",property(radio,"iconRotationDegrees").parse("0"));
            check(radio.getIconRotationDegrees()==0&&radio.measureWidth(null,100)==8,"radio image angle preview");
            check(DebugBridge.themes(radio).items().stream().anyMatch(item->item.key().equals("Radio:icon_rotation_degrees")
                    &&item.value().equals("0.0")&&item.source().startsWith("Inspector override")),"effective theme angle diagnostics");
            scroll.queueFree();
        } finally {DebugBridge.install(null);control.queueFree();radio.queueFree();LifecycleScheduler.flush();}
        System.out.println("ORE_INSPECTOR_TRANSFORM_PASS");
    }
    private static DebugProperty property(Control c,String key){return InspectorProperties.properties(c).stream().filter(p->p.key().equals(key)).findFirst().orElseThrow();}
    private static void rejects(Runnable run){try{run.run();throw new AssertionError("Expected invalid transform");}catch(IllegalArgumentException expected){}}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
