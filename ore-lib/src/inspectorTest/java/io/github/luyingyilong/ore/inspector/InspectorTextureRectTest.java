package io.github.luyingyilong.ore.inspector;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.LifecycleScheduler;
import io.github.luyingyilong.ore.control.display.TextureRect;
import io.github.luyingyilong.ore.control.input.Button;
import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.debug.DebugProperty;
import io.github.luyingyilong.ore.render.TextureSource;

import io.github.luyingyilong.ore.theme.OreTheme;

public final class InspectorTextureRectTest {
    public static void main(String[] args) {
        Button owner=new Button().setIcon(TextureSource.themed(io.github.luyingyilong.ore.theme.IconTheme.ARROW_DOWN));
        TextureRect image=owner.getIconControl(); DebugOverrides overrides=new DebugOverrides();
        DebugBridge.install(new DebugBridge.Backend() {
            public Object value(Control c,String key,Object base) { return overrides.value(c,key,base); }
        });
        try {
            var opacity=property(image,"opacity");
            check(opacity.parse("0.5").equals(0.5F) && image.getOpacity()==1,"Validation uses setters without mutating the image");
            rejects(()->opacity.parse("NaN")); rejects(()->opacity.parse("1.1"));
            overrides.set(image,"opacity",opacity.parse("0.5")); check(image.getOpacity()==0.5F,"Opacity is live");
            overrides.set(image,"flipHorizontal",property(image,"flipHorizontal").parse("true"));
            overrides.set(image,"stretchMode",property(image,"stretchMode").parse("TILE"));
            overrides.set(image,"expandMode",property(image,"expandMode").parse("IGNORE_SIZE"));
            overrides.set(image,"modulate",property(image,"modulate").parse("#80ffffff"));
            check(image.getFlipHorizontal() && image.getStretchMode()==TextureRect.StretchMode.TILE
                    && image.getExpandMode()==TextureRect.ExpandMode.IGNORE_SIZE && image.getModulate()==0x80FFFFFF,"All image presentation properties use Inspector overrides");
            overrides.undo(); check(image.getModulate()==0xFFFFFFFF,"Undo restores modulation"); overrides.redo();
            image.setOpacity(0.75F); check(image.getOpacity()==0.5F,"Application updates remain beneath the preview");
            overrides.resetAll(); check(image.getOpacity()==0.75F,"Reset restores the latest application value");
            overrides.set(image,"opacity",0.25F); overrides.removeTree(owner);
            check(image.getOpacity()==0.75F,"Virtual owner rebinding clears owned image overrides");
            overrides.undo(); check(image.getOpacity()==0.75F,"Old undo states cannot restore a recycled image's overrides");
            check(InspectorProperties.properties(image).stream().noneMatch(p->p.key().equals("texture")),"Image sources are not an editable reflection property");
            check(InspectorProperties.properties(image).stream().noneMatch(p->p.key().equals("x") || p.key().equals("width")),"The owner controls internal image placement");
        } finally { DebugBridge.install(null); owner.queueFree(); LifecycleScheduler.flush(); }
        check(image.isDisposed(),"Inspector does not retain disposed image parts");
        System.out.println("ORE_INSPECTOR_TEXTURE_RECT_PASS");
    }
    private static DebugProperty property(Control target,String key) {
        return InspectorProperties.properties(target).stream().filter(p->p.key().equals(key)).findFirst().orElseThrow();
    }
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
    private static void rejects(Runnable task) {
        try { task.run(); } catch(IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected invalid image property rejection");
    }
}
