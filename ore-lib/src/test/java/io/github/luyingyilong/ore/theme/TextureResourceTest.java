package io.github.luyingyilong.ore.theme;

import io.github.luyingyilong.ore.render.TextureRegion;
import net.minecraft.resources.ResourceLocation;
import java.io.Reader;
import java.io.StringReader;

/** One image-header read per reload, shared by image items and background styles. */
public final class TextureResourceTest {
    public static void main(String[] args) {
        ThemeDB db=new ThemeDB(); db.registerType("Control",null); db.registerType("Image","Control");
        var id=ResourceLocation.fromNamespaceAndPath("test","images");
        TextureRegion fallback=TextureRegion.full(ResourceLocation.fromNamespaceAndPath("test","fallback"),1,1);
        var first=ThemeKey.of("Image","first",TextureRegion.class,ThemeKey.Kind.TEXTURE,fallback,true);
        var second=ThemeKey.of("Image","second",TextureRegion.class,ThemeKey.Kind.TEXTURE,fallback,true);
        var panel=ThemeKey.of("Image","panel",StyleBox.class,ThemeKey.Kind.STYLE,StyleBoxEmpty.INSTANCE,true);
        db.register(first); db.register(second); db.register(panel); db.registerTheme(id);
        int[] reads={0},size={17,9};
        String[] json={"""
                {
                  "Image:first":{"location":"test:shared"},
                  "Image:second":{"location":"test:shared","x":1,"y":2,"width":3,"height":4},
                  "Image:panel":{"style":"texture","location":"test:shared"}
                }
                """};
        ThemeDB.Resources resources=new ThemeDB.Resources() {
            public Reader open(ResourceLocation file) { return new StringReader(json[0]); }
            public int[] imageSize(ResourceLocation image) { reads[0]++; return size.clone(); }
        };
        db.reload(resources); Theme theme=db.getTheme(id);
        check(db.getDiagnostics().isEmpty() && reads[0]==1,"Image and StyleBox entries share a reload cache");
        check(theme.get(first).getWidth()==17 && theme.get(first).getHeight()==9,"Omitted regions use the full image");
        check(theme.get(second).getX()==1 && theme.get(second).getWidth()==3,"Explicit atlas regions are preserved");
        TextureRegion old=theme.get(first); size[0]=20; size[1]=12; db.reload(resources);
        check(reads[0]==2 && theme.get(first).getWidth()==20 && old.getWidth()==17,"Reload refreshes metadata and preserves immutable old snapshots");
        json[0]="{\"Image:first\":{\"location\":\"test:shared\",\"contentMargin\":{\"left\":2}}}";
        db.reload(resources);
        check(!db.getDiagnostics().isEmpty() && theme.get(first)==null,"Image items reject background-only fields rather than retaining an old parser");
        System.out.println("ORE_TEXTURE_RESOURCE_PASS");
    }
    private static void check(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
