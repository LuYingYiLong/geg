package io.github.luyingyilong.ore.theme;

import io.github.luyingyilong.ore.render.*;

import io.github.luyingyilong.ore.Label;
import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.signal.SignalScope;
import net.minecraft.resources.ResourceLocation;
import java.io.*;
import java.util.*;

public final class ThemeSystemTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("test",path); }
    private static final ThemeDB.Resources BUILTIN=new ThemeDB.Resources() {
        public Reader open(ResourceLocation id) throws IOException {
            var in=ThemeSystemTest.class.getResourceAsStream("/assets/"+id.getNamespace()+"/"+id.getPath());
            if(in==null) throw new FileNotFoundException(id.toString()); return new InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8);
        }
        public int[] imageSize(ResourceLocation id) throws IOException {
            try(var in=ThemeSystemTest.class.getResourceAsStream("/assets/"+id.getNamespace()+"/"+id.getPath())) {
                if(in==null) throw new FileNotFoundException(id.toString());
                var image=javax.imageio.ImageIO.read(in); return new int[]{image.getWidth(),image.getHeight()};
            }
        }
    };
    public static void main(String[] args) throws Exception {
        declarations(); resources(); inheritance(); geometry(); changesAndLifetimes();
        System.out.println("ORE_THEME_SYSTEM_PASS");
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static void declarations() {
        ThemeDB db=new ThemeDB(); db.registerType("Control",null); db.registerType("test:Widget","Control");
        ThemeKey<Integer> key=ThemeKey.of("test:Widget","amount",Integer.class,ThemeKey.Kind.INTEGER,4,true);
        db.register(key); db.register(key);
        rejects(()->db.register(ThemeKey.of("test:Widget","amount",Boolean.class,ThemeKey.Kind.BOOLEAN,false,false)));
        rejects(()->db.registerType("test:Widget",null)); rejects(()->db.registerType("Broken","Missing"));
        Theme theme=db.getTheme(id("program")); theme.set(key,7); check(theme.get(key)==7,"Typed values");
        @SuppressWarnings("rawtypes") ThemeKey raw=key;
        rejects(()->theme.set(raw,"not an integer"));
        check(ThemeDB.parseColor("#102030")==0xFF102030 && ThemeDB.parseColor("#40102030")==0x40102030,"ARGB syntax");
        rejects(()->ThemeDB.parseColor("#1234"));
        rejects(()->new ThemeInsets(-1,0,0,0));
    }
    private static void resources() {
        ThemeDB db=ThemeDB.getDefault(); db.reload(BUILTIN);
        check(db.getDiagnostics().isEmpty(),"All shipped theme declarations/textures load: "+db.getDiagnostics());
        check(db.getDefaultTheme().getId().equals(OreTheme.ID),"Native theme identity");
        check(db.getDefaultTheme().get(OreTheme.PANEL) instanceof StyleBoxTexture,"Native schema loaded");
        ThemeDB isolated=new ThemeDB(); isolated.registerType("Control",null); isolated.registerType("Test","Control");
        var style=ThemeKey.of("Test","panel",StyleBox.class,ThemeKey.Kind.STYLE,StyleBoxEmpty.INSTANCE,true);
        isolated.register(style); isolated.registerTheme(id("child"));
        Map<String,String> files=new HashMap<>();
        files.put("child","{\"parent\":\"test:parent\",\"Test:panel\":{\"style\":\"empty\"}}");
        files.put("parent","{\"parent\":\"ore:default\",\"Test:panel\":{\"style\":\"flat\",\"color\":\"#123456\"}}");
        ThemeDB.Resources source=new ThemeDB.Resources() {
            public Reader open(ResourceLocation file) throws IOException { String s=files.get(file.getPath().replace("themes/","").replace(".json","")); if(s==null) throw new FileNotFoundException(file.toString()); return new StringReader(s); }
            public int[] imageSize(ResourceLocation image) { return new int[]{16,12}; }
        };
        isolated.reload(source); Theme child=isolated.getTheme(id("child"));
        check(child.get(style) instanceof StyleBoxEmpty && child.get(style).getContentMargin().equals(ThemeInsets.ZERO),"Explicit empty stops background fallback; margins default zero");
        check(child.getParent()==isolated.getTheme(id("parent")),"Only declared resources/parents loaded");
        files.put("child","{"); isolated.reload(source); check(child.get(style) instanceof StyleBoxEmpty,"Malformed reload preserves last valid graph");
        files.put("child","{\"parent\":\"test:parent\"}"); files.put("parent","{\"parent\":\"test:child\"}");
        isolated.reload(source); check(child.get(style) instanceof StyleBoxEmpty,"Cycle preserves previous graph");
        files.put("child","{\"Test:panel\":{\"style\":\"texture\",\"location\":\"test:x\",\"textureBorders\":{\"borderLeft\":17}}}");
        isolated.reload(source); check(!isolated.getDiagnostics().isEmpty() && child.get(style)==null,"Invalid item falls through, never installs bad UVs");
        files.put("child","{\"Test:panel\":{\"style\":\"texture\",\"location\":\"test:x\"}}");
        isolated.reload(source); var texture=(StyleBoxTexture)child.get(style);
        check(texture.getTextureRegion().getImageWidth()==16 && texture.getTextureRegion().getHeight()==12 && texture.getContentMargin().equals(ThemeInsets.ZERO),"Actual image dimensions and full region defaults");
        check(texture.getDrawInset().equals(ThemeInsets.ZERO) && texture.getContentOffset().equals(Offset.ZERO),"Presentation defaults do not imply a pressed offset");
        for(String kind:List.of("empty","flat","texture")) {
            String location=kind.equals("texture")?",\"location\":\"test:x\"":"";
            files.put("child","{\"Test:panel\":{\"style\":\""+kind+"\""+location+",\"drawInset\":{\"top\":5},\"contentOffset\":{\"x\":-2,\"y\":3}}}");
            isolated.reload(source); StyleBox parsed=child.get(style);
            check(isolated.getDiagnostics().isEmpty() && parsed.getDrawInset().top()==5 && parsed.getContentOffset().equals(new Offset(-2,3)),"All StyleBoxes parse independent presentation geometry");
        }
        for(String invalid:List.of("\"drawInset\":{\"top\":-1}","\"drawInset\":{\"top\":1.5}",
                "\"contentOffset\":{\"y\":\"2\"}","\"contentOffset\":{\"y\":1.5}","\"contentOffset\":{\"y\":2147483648}",
                "\"contentOffset\":{\"z\":2}")) {
            files.put("child","{\"Test:panel\":{\"style\":\"empty\","+invalid+"}}"); isolated.reload(source);
            check(!isolated.getDiagnostics().isEmpty() && child.get(style)==null,"Invalid presentation geometry falls through");
        }
        for(var key:List.of(OreTheme.BUTTON_SECONDARY_PRESSED,OreTheme.BUTTON_PRIMARY_PRESSED,
                OreTheme.BUTTON_DESTRUCTIVE_PRESSED,OreTheme.SEGMENTED_BUTTON_SELECTED)) {
            StyleBox configured=db.getDefaultTheme().get(key);
            check(configured.getDrawInset().top()==2 && configured.getContentOffset().equals(new Offset(0,2)),"Shipped state styles preserve the 2px effect");
        }
    }
    private static void inheritance() {
        ThemeDB db=ThemeDB.getDefault();
        Theme distant=db.getTheme(id("distant")).set(OreTheme.TEXT_COLOR,0xFF111111);
        Theme nearby=db.getTheme(id("nearby")).set(OreTheme.TEXT_COLOR,0xFF222222);
        Theme own=db.getTheme(id("own")).set(OreTheme.TEXT_COLOR,0xFF333333);
        Control root=new Control().setTheme(distant), parent=new Control().setTheme(nearby); Label label=new Label();
        root.addChild(parent); parent.addChild(label);
        check(label.getTheme()==null && label.getColor()==0xFF222222,"Nearest ancestor theme, getter stays local");
        label.setTheme(own); check(label.getColor()==0xFF333333,"Own theme precedence");
        label.setThemeOverride(OreTheme.TEXT_COLOR,0xFF444444); check(label.getColor()==0xFF444444,"Local override");
        label.setColor(0xFF555555); check(label.getColor()==0xFF555555,"Explicit setter wins");
        label.clearColor(); check(label.getColor()==0xFF444444,"Clear explicit property restores local override");
        label.removeThemeOverride(OreTheme.TEXT_COLOR); check(label.getColor()==0xFF333333,"Remove override restores theme");
        own.set(OreTheme.LABEL_DARK_COLOR,0xFF666666); label.setThemeTypeVariation("LabelDark");
        check(label.getColor()==0xFF666666,"Variation before base type in each theme layer");
        Control part=parent.ownPart("internal",new Control());
        check(part.getThemeItem(OreTheme.TEXT_COLOR)==0xFF222222,"Internal parts inherit lifecycle owner");
        parent.setThemeOverride(OreTheme.TEXT_COLOR,0xFF777777);
        check(part.getThemeItem(OreTheme.TEXT_COLOR)==0xFF777777,"Owned internal parts inherit owner overrides");
        parent.removeThemeOverride(OreTheme.TEXT_COLOR);
        Control overlay=new Control().setThemeParent(label); check(overlay.getThemeItem(OreTheme.TEXT_COLOR)==0xFF333333,"Overlay inherits opener theme without drawing parent");
        label.setThemeOverride(OreTheme.TEXT_COLOR,0xFF888888);
        check(overlay.getThemeItem(OreTheme.TEXT_COLOR)==0xFF888888,"Popup style source includes local overrides");
        label.removeThemeOverride(OreTheme.TEXT_COLOR);
        rejects(()->parent.setThemeParent(part)); rejects(()->own.setParent(own));
        check(DebugBridge.themes(label).items().stream().anyMatch(i->i.key().equals("Label:color") && i.chain().stream().anyMatch(t->t.kind().equals("own theme"))),"Read-only source trace");
        root.queueFree(); LifecycleScheduler.flush(); check(overlay.getThemeParent()==null,"Destroyed opener removes style relationship");
        overlay.queueFree(); LifecycleScheduler.flush();
        Label explicitFont=new Label().setHero(true).setFont(id("custom_font"));
        check(explicitFont.getStyledText().getStyle().getFont().equals(id("custom_font")),"Explicit font wins over hero family selection");
        explicitFont.clearFont();
        check(explicitFont.getFont().equals(OreTheme.HERO_FONT.defaultValue()),"Clearing explicit font restores hero inheritance");
        explicitFont.queueFree(); LifecycleScheduler.flush();
    }
    private static void geometry() {
        var margin=new ThemeInsets(1,2,3,4); var expand=new ThemeInsets(2,3,4,5);
        StyleBoxTexture texture=new StyleBoxTexture(new TextureRegion(id("image"), 20, 16, 2, 1, 14, 12), new ThemeInsets(2,3,5,4), margin, expand, StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 0.5F);
        Rect original=new Rect(10,20,30,40); check(texture.getContentRect(original).equals(new Rect(11,22,26,34)),"Content insets");
        check(texture.getDrawRect(original).equals(new Rect(8,17,36,48)) && original.width()==30,"Expand is paint only");
        for(int w=0;w<12;w++) for(int h=0;h<12;h++) {
            var tiny=new StyleBoxTexture(new TextureRegion(id("x"), 20, 16, 2, 1, 14, 12), new ThemeInsets(2,3,5,4), ThemeInsets.ZERO, ThemeInsets.ZERO, StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1);
            for(var patch:tiny.getPatches(new Rect(0,0,w,h))) {
                check(patch.target().x()>=0 && patch.target().right()<=w && patch.target().bottom()<=h,"Small target stays inside allocated paint rect");
                check(patch.source().x()>=2 && patch.source().right()<=16 && patch.source().bottom()<=13,"Source UV never inverted/out of region");
            }
        }
        rejects(()->new StyleBoxTexture(new TextureRegion(id("x"), 8, 8, 0, 0, 9, 8), ThemeInsets.ZERO, ThemeInsets.ZERO, ThemeInsets.ZERO, StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1));
        var tiled=new StyleBoxTexture(new TextureRegion(id("x"), 4, 4, 0, 0, 4, 4), ThemeInsets.ZERO, ThemeInsets.ZERO, ThemeInsets.ZERO, StyleBoxTexture.AxisMode.TILE, StyleBoxTexture.AxisMode.TILE, true, 1);
        check(tiled.getPatches(new Rect(0,0,9,6)).size()==6,"Tiles include partial final tile");
        Panel panel=new Panel(OreTheme.PANEL); panel.setThemeOverride(OreTheme.PANEL,new StyleBoxEmpty(margin,expand));
        Control content=new Control().setPreferred(10,5); panel.setChild(content);
        check(panel.measureSize(null,new Size(100,100)).equals(new Size(14,11)),"Panel consumes margins once");
        panel.layout(new Rect(0,0,30,20),null); check(content.getBounds().equals(new Rect(1,2,26,14)),"Drawing and child layout share content rect");
        panel.queueFree(); LifecycleScheduler.flush();
    }
    private static void changesAndLifetimes() throws Exception {
        ThemeDB db=ThemeDB.getDefault(); int baseline=db.changed().connectionCount();
        Theme shared=db.getTheme(id("shared")); Probe probe=new Probe(); probe.setTheme(shared);
        Theme descendant=db.getTheme(id("descendant")).setParent(shared);
        int[] inheritedChanges={0}; SignalScope observer=new SignalScope();
        descendant.changed().connect(observer, layout -> {
            inheritedChanges[0]++;
            check(probe.getThemeItem(OreTheme.TEXT_COLOR).equals(shared.get(OreTheme.TEXT_COLOR)),"Signal observers read the latest cache");
        });
        probe.getThemeItem(OreTheme.TEXT_COLOR); check(db.changed().connectionCount()==baseline+1,"One lifetime-scoped database subscription");
        shared.set(OreTheme.TEXT_COLOR,0xFF123456); shared.set(OreTheme.TEXT_COLOR,0xFF234567);
        LifecycleScheduler.notifications(); check(probe.changes==1 && probe.getThemeItem(OreTheme.TEXT_COLOR)==0xFF234567,"Theme notifications coalesce, cache observes latest");
        probe.layout(new Rect(0,0,20,20),null); shared.set(OreTheme.FONT_SIZE,12F);
        check(probe.getLayoutDirty(),"Font changes invalidate layout");
        Control parent=new Control(); parent.ownPooled(probe); parent.queueFree(); LifecycleScheduler.flush();
        check(probe.isDisposed() && db.changed().connectionCount()==baseline,"Pooled cleanup releases subscriptions");
        check(inheritedChanges[0]==3,"Derived themes receive parent changes"); observer.close();
        shared.set(OreTheme.TEXT_COLOR,0xFF345678); check(shared.get(OreTheme.TEXT_COLOR)==0xFF345678,"Shared theme outlives controls");
        Throwable[] error={null}; var worker=new Thread(()->{try{shared.set(OreTheme.TEXT_COLOR,0);}catch(Throwable e){error[0]=e;}});worker.start();worker.join();
        check(error[0] instanceof IllegalStateException,"Cross-thread theme edit rejected");
    }
    private static final class Probe extends Control { int changes; protected void notification(ControlNotification n) { if(n.type()==ControlNotification.Type.THEME_CHANGED) changes++; } }
    private static void check(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
    private static void rejects(Runnable action) { try { action.run(); } catch(IllegalArgumentException | IllegalStateException | ClassCastException expected) { return; } throw new AssertionError("Expected invalid configuration rejection"); }
}
