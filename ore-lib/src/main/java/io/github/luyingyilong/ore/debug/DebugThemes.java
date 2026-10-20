package io.github.luyingyilong.ore.debug;

import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.theme.*;
import java.util.*;

/** Immutable, read-only diagnostics. No snapshot retains a Control or mutable Theme. */
public final class DebugThemes {
    private DebugThemes() { }
    public record Item(String key,String value,String source,List<ThemeDB.Trace> chain) {
        public Item { chain=List.copyOf(chain); }
    }
    public record Snapshot(String type,String variation,String state,List<Item> items) {
        public Snapshot { items=List.copyOf(items); }
        public static Snapshot empty() { return new Snapshot("","","",List.of()); }
    }
    public static Snapshot snapshot(Control control) {
        if(control==null || !control.isAlive()) return Snapshot.empty();
        List<Item> items=new ArrayList<>();
        for(ThemeKey<?> key:control.getThemeKeys()) {
            var result=control.getThemeResolution(key);
            Object effective=result.value();
            String property=switch(key.name()) { case "color" -> "color"; case "font_size" -> "fontSize"; default -> "theme:"+key.getId(); };
            Object preview=DebugBridge.backend().value(control,property,effective);
            String source=result.sources().get(result.sources().size()-1).kind();
            if(!Objects.equals(preview,effective)) source="Inspector override (base: "+format(key,effective)+")";
            items.add(new Item(key.getId(),format(key,preview),source,result.sources()));
        }
        return new Snapshot(control.getThemeType(),control.getThemeTypeVariation(),control.getThemeState(),items);
    }
    private static String format(ThemeKey<?> key,Object value) {
        if(key.kind()==ThemeKey.Kind.COLOR && value instanceof Integer color) return String.format("#%08X",color);
        if(value instanceof StyleBoxTexture box) return "StyleBoxTexture["+box.getTextureRegion()
                +", borders="+box.textureBorders()+", content="+box.getContentMargin()+", expand="+box.getExpandMargin()
                +", axes="+box.horizontalMode()+"/"+box.verticalMode()+", center="+box.drawCenter()+", opacity="+box.opacity()
                +", drawInset="+box.getDrawInset()+", contentOffset="+box.getContentOffset()+"]";
        return String.valueOf(value);
    }
}
