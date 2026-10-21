package io.github.luyingyilong.ore.control.base;

import io.github.luyingyilong.ore.control.input.Radio;
import io.github.luyingyilong.ore.control.layout.Scroll;
import io.github.luyingyilong.ore.theme.OreTheme;
import io.github.luyingyilong.ore.theme.StyleBoxEmpty;
import io.github.luyingyilong.ore.theme.StyleBoxFlat;
import io.github.luyingyilong.ore.theme.Theme;
import io.github.luyingyilong.ore.theme.ThemeDB;
import io.github.luyingyilong.ore.theme.ThemeInsets;
import io.github.luyingyilong.ore.window.FocusGeometry;
import io.github.luyingyilong.ore.window.Window;

import net.minecraft.resources.ResourceLocation;

public final class FocusThemeTest {
    public static void main(String[] args) {
        Control content=new Control();Radio radio=new Radio();
        radio.setPosition(2,15).setRequestedSize(20,18);content.addChild(radio);content.setPreferred(60,80);
        Scroll scroll=new Scroll().setEmbedded(true).setVertical(Scroll.BarMode.NEVER).setContent(content);
        Window window=Window.embedded().setContent(scroll);window.layoutViewport(60,20,null);scroll.scrollBy(0,10);
        FocusGeometry geometry=radio.getFocusGeometry();
        check(geometry.getLocalBounds().width()==8&&geometry.getClip().equals(new Rect(0,0,60,20)),"focus retains its local shape and viewport clip");
        check(geometry.getVisibleBounds(radio).bottom()<=20,"visible envelope clipped without changing the shape");
        Theme theme=ThemeDB.getDefault().getTheme(ResourceLocation.fromNamespaceAndPath("test","radio_focus"));
        content.setTheme(theme);theme.set(Radio.ICON_ROTATION_DEGREES,0F);
        check(radio.measureWidth(null,60)==8&&radio.getLayoutDirty(),"ancestor theme angle affects measurement");
        var style=new StyleBoxFlat(0,new ThemeInsets(1,1,1,1),0xFF8CB3FF,ThemeInsets.ZERO,new ThemeInsets(2,2,2,2));
        theme.set(OreTheme.FOCUS,style);check(radio.getThemeItem(OreTheme.FOCUS)==style,"focus uses the actual owner theme");
        radio.setThemeOverride(OreTheme.FOCUS,StyleBoxEmpty.INSTANCE);
        check(radio.getThemeItem(OreTheme.FOCUS)==StyleBoxEmpty.INSTANCE,"explicit empty stops style fallback");
        radio.removeThemeOverride(OreTheme.FOCUS);check(radio.getThemeItem(OreTheme.FOCUS)==style,"restore inherited focus");
        theme.set(Radio.ICON_ROTATION_DEGREES,45F);check(radio.measureWidth(null,60)==12,"live theme update changes footprint");
        window.queueFree();LifecycleScheduler.flush();check(radio.isDisposed(),"parts and theme subscriptions follow owner cleanup");
        System.out.println("ORE_FOCUS_THEME_PASS");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
