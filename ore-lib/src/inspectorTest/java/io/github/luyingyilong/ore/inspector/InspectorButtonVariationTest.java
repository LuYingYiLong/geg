package io.github.luyingyilong.ore.inspector;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.LifecycleScheduler;
import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.theme.StyleBoxFlat;

import io.github.luyingyilong.ore.control.input.Button;

public final class InspectorButtonVariationTest {
    public static void main(String[] args) {
        Button button=new Button();DebugOverrides overrides=new DebugOverrides();
        DebugBridge.install(new DebugBridge.Backend(){@Override public Object value(Control c,String key,Object base){return overrides.value(c,key,base);}});
        try {
            var property=InspectorProperties.properties(button).stream().filter(p->p.key().equals("themeTypeVariation")).findFirst().orElseThrow();
            var normal=button.getThemeItem(Button.NORMAL);
            overrides.set(button,property.key(),property.parse("PrimaryButton"));
            var primary=button.getThemeItem(Button.NORMAL);
            check(primary!=normal&&button.getThemeResolution(Button.NORMAL).sources().get(0).item().equals("PrimaryButton:normal"),"preview invalidates resolved theme cache");
            button.setThemeTypeVariation("MenuButton");
            check(button.getThemeItem(Button.NORMAL)==primary,"source writes do not replace active preview");
            overrides.resetAll();check(button.getThemeItem(Button.NORMAL) instanceof StyleBoxFlat flat&&flat.color()==0,"restore latest underlying variant");
            overrides.undo();check(button.getThemeItem(Button.NORMAL)==primary,"undo theme preview restoration");
            try{property.parse("not_registered");throw new AssertionError("unknown variant accepted");}catch(IllegalArgumentException expected){}
            check(button.getThemeItem(Button.NORMAL)==primary,"invalid edit keeps last valid value");
        } finally {DebugBridge.install(null);button.queueFree();LifecycleScheduler.flush();}
        System.out.println("ORE_INSPECTOR_BUTTON_VARIATION_PASS");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
