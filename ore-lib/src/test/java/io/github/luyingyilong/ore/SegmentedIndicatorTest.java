package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.theme.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Selected indicators are decoration, with their own theme and bounded geometry. */
public final class SegmentedIndicatorTest {
    public static void main(String[] args) {
        defaults(); overrides(); validation(); resourceConfiguration();
        System.out.println("ORE_SEGMENTED_INDICATOR_PASS");
    }

    private static Segmented options() {
        return new Segmented(List.of(Component.literal("A"),Component.literal("B")));
    }

    private static void defaults() {
        Segmented segmented=options();
        check(segmented.getThemeItem(OreTheme.SEGMENTED_INDICATOR) instanceof StyleBoxFlat flat
                && flat.color()==0xFFFFFFFF,"Default indicator is a white StyleBoxFlat");
        for(int width=1;width<200;width++) {
            Rect option=new Rect(10,30,width,20);
            int expectedWidth=Math.min(16,Math.max(1,width/3));
            check(segmented.getSelectedIndicatorRect(option).equals(new Rect(
                    10+(width-expectedWidth)/2,48,expectedWidth,1)),"Default geometry preserves the old pixels");
        }
        for(int width=0;width<25;width++) for(int height=0;height<25;height++) {
            Rect area=new Rect(10,30,width,height), line=segmented.getSelectedIndicatorRect(area);
            check(line.x()>=area.x() && line.y()>=area.y() && line.right()<=area.right()
                    && line.bottom()<=area.bottom(),"Tiny targets never produce reversed or out-of-bounds rectangles");
        }
        segmented.queueFree(); LifecycleScheduler.flush();
    }

    private static void overrides() {
        Segmented segmented=options();
        Window window=new Window(true).setContent(segmented); window.layoutViewport(120,20,null);
        Rect bounds=segmented.getBounds(), focus=segmented.getFocusBounds();
        Size natural=segmented.measureSize(null,new Size(120,20));
        int hit=segmented.hitIndex(0,0,120,10,10);
        Theme theme=ThemeDB.getDefault().getTheme(ResourceLocation.fromNamespaceAndPath("test","indicator"));
        theme.set(OreTheme.SEGMENTED_INDICATOR_HEIGHT,3).set(OreTheme.SEGMENTED_INDICATOR_WIDTH_RATIO,0.5F)
                .set(OreTheme.SEGMENTED_INDICATOR_MAX_WIDTH,64).set(OreTheme.SEGMENTED_INDICATOR_BOTTOM_GAP,2);
        segmented.setTheme(theme); window.layoutViewport(120,20,null);
        check(segmented.getSelectedIndicatorRect(new Rect(10,30,100,20)).equals(new Rect(35,45,50,3)),"Inherited geometry values are applied");
        var blue=new StyleBoxFlat(0xFF8CB3FF,ThemeInsets.ZERO,0,ThemeInsets.ZERO,ThemeInsets.ZERO);
        segmented.setThemeOverride(OreTheme.SEGMENTED_INDICATOR,blue);
        segmented.setThemeOverride(OreTheme.SEGMENTED_INDICATOR_MAX_WIDTH,20);
        check(segmented.getSelectedIndicatorRect(new Rect(10,30,100,20)).equals(new Rect(50,45,20,3)),"Local overrides clamp and center the line");
        check(segmented.getThemeItem(OreTheme.SEGMENTED_INDICATOR)==blue,"Indicator appearance resolves through its own StyleBox key");
        check(!segmented.getLayoutDirty() && segmented.getBounds().equals(bounds)
                && segmented.getFocusBounds().equals(focus) && segmented.measureSize(null,new Size(120,20)).equals(natural)
                && segmented.hitIndex(0,0,120,10,10)==hit,"Indicator changes affect neither layout, focus nor hit testing");
        check(DebugBridge.themes(segmented).items().stream().anyMatch(item->item.key().equals("Segmented:selected_indicator")
                && item.source().equals("local override")),"Inspector receives indicator values and their source");
        segmented.setThemeOverride(OreTheme.SEGMENTED_BUTTON_SELECTED,new StyleBoxEmpty(ThemeInsets.ZERO,ThemeInsets.ZERO,
                new ThemeInsets(0,7,0,0),new Offset(4,8)));
        check(segmented.getSelectedIndicatorRect(new Rect(10,30,100,20)).equals(new Rect(50,45,20,3)),"Background and content offsets do not move the indicator");
        segmented.removeThemeOverride(OreTheme.SEGMENTED_INDICATOR_MAX_WIDTH);
        check(segmented.getSelectedIndicatorRect(new Rect(10,30,100,20)).width()==50,"Removing a local override restores inherited geometry");
        segmented.setThemeOverride(OreTheme.SEGMENTED_INDICATOR,StyleBoxEmpty.INSTANCE);
        check(segmented.getThemeItem(OreTheme.SEGMENTED_INDICATOR)==StyleBoxEmpty.INSTANCE,"Empty style hides the indicator without falling through");
        theme.set(OreTheme.SEGMENTED_INDICATOR_WIDTH_RATIO,0F);
        check(segmented.getSelectedIndicatorRect(new Rect(0,0,100,20)).width()==0,"Zero ratio hides the line");
        theme.set(OreTheme.SEGMENTED_INDICATOR_WIDTH_RATIO,1F).set(OreTheme.SEGMENTED_INDICATOR_MAX_WIDTH,0);
        check(segmented.getSelectedIndicatorRect(new Rect(0,0,100,20)).width()==0,"Zero max width hides the line");
        theme.set(OreTheme.SEGMENTED_INDICATOR_MAX_WIDTH,Integer.MAX_VALUE)
                .set(OreTheme.SEGMENTED_INDICATOR_HEIGHT,Integer.MAX_VALUE).set(OreTheme.SEGMENTED_INDICATOR_BOTTOM_GAP,0);
        check(segmented.getSelectedIndicatorRect(new Rect(10,30,5,2)).equals(new Rect(10,30,5,2)),"Oversized width/height are bounded by the option");
        theme.set(OreTheme.SEGMENTED_INDICATOR_BOTTOM_GAP,Integer.MAX_VALUE);
        check(segmented.getSelectedIndicatorRect(new Rect(10,30,5,2)).height()==0,"Oversized bottom gap safely hides the line");
        window.queueFree(); LifecycleScheduler.flush();
    }

    private static void validation() {
        Segmented segmented=options();
        for(float value:new float[]{-0.1F,1.01F,Float.NaN,Float.POSITIVE_INFINITY})
            rejects(()->segmented.setThemeOverride(OreTheme.SEGMENTED_INDICATOR_WIDTH_RATIO,value));
        rejects(()->segmented.setThemeOverride(OreTheme.SEGMENTED_INDICATOR_HEIGHT,-1));
        rejects(()->segmented.setThemeOverride(OreTheme.SEGMENTED_INDICATOR_MAX_WIDTH,-1));
        rejects(()->segmented.setThemeOverride(OreTheme.SEGMENTED_INDICATOR_BOTTOM_GAP,-1));
        segmented.queueFree(); LifecycleScheduler.flush();
    }

    private static void resourceConfiguration() {
        ThemeDB db=new ThemeDB(); db.registerType("Control",null); db.registerType("Segmented","Control");
        for(var key:List.of(OreTheme.SEGMENTED_INDICATOR,OreTheme.SEGMENTED_INDICATOR_HEIGHT,
                OreTheme.SEGMENTED_INDICATOR_WIDTH_RATIO,OreTheme.SEGMENTED_INDICATOR_MAX_WIDTH,
                OreTheme.SEGMENTED_INDICATOR_BOTTOM_GAP)) db.register(key);
        ResourceLocation id=ResourceLocation.fromNamespaceAndPath("test","indicator_resource");
        db.registerTheme(id);
        String[] json={"{}"};
        ThemeDB.Resources resources=new ThemeDB.Resources() {
            public java.io.Reader open(ResourceLocation file) { return new java.io.StringReader(json[0]); }
            public int[] imageSize(ResourceLocation image) { return new int[]{16,16}; }
        };
        json[0]="""
                {
                  "Segmented:selected_indicator":{"style":"flat","color":"#8CB3FF"},
                  "Segmented:selected_indicator_height":3,
                  "Segmented:selected_indicator_width_ratio":0.5,
                  "Segmented:selected_indicator_max_width":64,
                  "Segmented:selected_indicator_bottom_gap":2
                }
                """;
        db.reload(resources); Theme theme=db.getTheme(id);
        check(db.getDiagnostics().isEmpty() && theme.get(OreTheme.SEGMENTED_INDICATOR) instanceof StyleBoxFlat flat
                && flat.color()==0xFF8CB3FF && theme.get(OreTheme.SEGMENTED_INDICATOR_HEIGHT)==3
                && theme.get(OreTheme.SEGMENTED_INDICATOR_WIDTH_RATIO)==0.5F
                && theme.get(OreTheme.SEGMENTED_INDICATOR_MAX_WIDTH)==64 && theme.get(OreTheme.SEGMENTED_INDICATOR_BOTTOM_GAP)==2,
                "All indicator values load from JSON using their declared types");
        json[0]="{\"Segmented:selected_indicator\":{\"style\":\"texture\",\"location\":\"test:line\"}}";
        db.reload(resources);
        check(db.getDiagnostics().isEmpty() && theme.get(OreTheme.SEGMENTED_INDICATOR) instanceof StyleBoxTexture,
                "Texture indicators use the standard StyleBox loader");
        json[0]="{\"Segmented:selected_indicator\":{\"style\":\"empty\"}}";
        db.reload(resources);
        check(theme.get(OreTheme.SEGMENTED_INDICATOR) instanceof StyleBoxEmpty,"Explicit empty indicators survive resource loading");
        for(String invalid:List.of("\"selected_indicator_height\":-1","\"selected_indicator_height\":1.5",
                "\"selected_indicator_width_ratio\":2","\"selected_indicator_width_ratio\":-0.1",
                "\"selected_indicator_width_ratio\":\"0.5\"","\"selected_indicator_max_width\":-1",
                "\"selected_indicator_bottom_gap\":true")) {
            json[0]="{\"Segmented:"+invalid.substring(1)+"}"; db.reload(resources);
            check(!db.getDiagnostics().isEmpty(),"Invalid indicator configuration is reported");
            String key=invalid.substring(1,invalid.indexOf('"',1));
            boolean missing=key.equals("selected_indicator_width_ratio")
                    ?theme.get(db.getKey("Segmented",key,Float.class))==null
                    :theme.get(db.getKey("Segmented",key,Integer.class))==null;
            check(missing,"Invalid configuration falls through rather than installing unsafe values");
        }
    }
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
    private static void rejects(Runnable operation) {
        try { operation.run(); } catch(IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected invalid indicator value rejection");
    }
}
