package io.github.luyingyilong.ore.gallery;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.LifecycleScheduler;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.control.base.Size;
import io.github.luyingyilong.ore.control.display.Label;
import io.github.luyingyilong.ore.control.display.TextureRect;
import io.github.luyingyilong.ore.control.inventory.Inventory;
import io.github.luyingyilong.ore.control.inventory.Slot;
import io.github.luyingyilong.ore.control.progress.TextureProgressBar;

import io.github.luyingyilong.ore.control.inventory.source.InventorySource;
import io.github.luyingyilong.ore.render.TextureRegion;
import io.github.luyingyilong.ore.theme.OreTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import java.util.*;

/** Fixed native texture geometry, with Ore models and retained mock inventory data on top. */
public final class GalleryContainerLayoutTest {
    public static void main(String[] args) throws Exception {
        var boot=net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");boot.setAccessible(true);boot.setBoolean(null,true);
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.core.registries.BuiltInRegistries.bootStrap();
        Font font=new Font(id->null,false){@Override public int width(FormattedText text){return text.getString().length()*6;}};
        GalleryView view=new GalleryView();view.select("containers");Control retained=view.getSample();
        Control chest=find(retained,"Gallery vanilla chest"),furnace=find(retained,"Gallery vanilla blast furnace");
        Map<Control,List<Control>> children=new IdentityHashMap<>();children.put(chest,chest.getChildren());children.put(furnace,furnace.getChildren());
        Set<InventorySource> sources=Collections.newSetFromMap(new IdentityHashMap<>());
        view.getWindow().setThemeOverride(Inventory.GAP,9).setThemeOverride(Inventory.SECTION_GAP,12).setThemeOverride(Slot.PADDING_LEFT,10);
        for(int width:new int[]{127,128,129,480}){
            view.setPreviewWidth(width==480?0:width);view.getWindow().layoutViewport(width,240,font);
            check(view.getSample()==retained,"resize retains mock inventories");
            check(chest.getSize().equals(new Size(176,168))&&furnace.getSize().equals(new Size(176,166)),"skins never stretch or shrink");
            check(children.get(chest).equals(chest.getChildren())&&children.get(furnace).equals(furnace.getChildren()),"skin nodes are retained");
            image(retained,"chestBackgroundTop",new Rect(0,0,176,71),new Rect(0,0,176,71),"generic_54.png");
            image(retained,"chestBackgroundBottom",new Rect(0,71,176,96),new Rect(0,126,176,96),"generic_54.png");
            image(retained,"furnaceBackground",new Rect(0,0,176,166),new Rect(0,0,176,166),"blast_furnace.png");
            Inventory storage=(Inventory)find(retained,"chestStorage"),chestPlayer=(Inventory)find(retained,"chestPlayer"),furnacePlayer=(Inventory)find(retained,"furnacePlayer");
            title(chest,chestPlayer,8,72);title(furnace,furnacePlayer,8,70);
            check(!storage.getShowTitle()&&find(retained,"chestInventoryTitle")==null&&find(retained,"furnaceInventoryTitle")==null,"no duplicate external inventory captions");
            item(chest,storage.getSlotControl(0),8,18);item(chest,storage.getSlotControl(26),152,54);
            item(chest,chestPlayer.getSlotControl(0),8,85);item(chest,chestPlayer.getSlotControl(26),152,121);
            item(chest,chestPlayer.getSlotControl(27),8,143);item(chest,chestPlayer.getSlotControl(35),152,143);
            item(furnace,(Slot)find(retained,"furnaceInput"),56,17);item(furnace,(Slot)find(retained,"furnaceFuel"),56,53);
            item(furnace,(Slot)find(retained,"furnaceOutput"),116,35);
            item(furnace,furnacePlayer.getSlotControl(0),8,84);item(furnace,furnacePlayer.getSlotControl(26),152,120);
            item(furnace,furnacePlayer.getSlotControl(27),8,142);item(furnace,furnacePlayer.getSlotControl(35),152,142);
            TextureProgressBar flame=(TextureProgressBar)find(retained,"furnaceFlame"),arrow=(TextureProgressBar)find(retained,"furnaceArrow");
            check(flame.getBounds().equals(new Rect(56,36,14,14))&&arrow.getBounds().equals(new Rect(79,34,24,16)),"native progress anchors");
            region(flame.getResolvedProgressTexture(),new Rect(176,0,14,14),"blast_furnace.png");
            region(arrow.getResolvedProgressTexture(),new Rect(176,14,24,16),"blast_furnace.png");
            check(flame.getUnderTexture()==null&&arrow.getUnderTexture()==null&&flame.getFillMode()==TextureProgressBar.FillMode.BOTTOM_TO_TOP,"background already supplies inactive progress");
            List<Slot> slots=new ArrayList<>();slots(retained,slots);check(slots.size()==102,"both scenes retain all preview slots");
            for(Slot slot:slots){check(!slot.getBackgroundVisible()&&slot.getSize().equals(new Size(18,18)),"transparent fixed-pixel slots");sources.add(slot.getBinding().source());}
            for(var source:sources)check(source.slotChanged().connectionCount()==source.getSlotCount(),"no duplicate binding subscription during relayout");
        }
        view.getWindow().queueFree();LifecycleScheduler.flush();
        for(var source:sources)check(source.isClosed()&&source.slotChanged().connectionCount()==0,"preview data and bindings end with gallery");
        System.out.println("ORE_GALLERY_VANILLA_CONTAINERS_PASS");
    }
    private static void image(Control root,String name,Rect bounds,Rect source,String file){
        TextureRect image=(TextureRect)find(root,name);check(image.getBounds().equals(bounds)&&image.getStretchMode()==TextureRect.StretchMode.KEEP,"native image placement");region(image.getResolvedTextureRegion(),source,file);
    }
    private static void region(TextureRegion image,Rect source,String file){
        check(image.getTexture().toString().equals("minecraft:textures/gui/container/"+file)&&image.getImageWidth()==256&&image.getImageHeight()==256
                &&new Rect(image.getX(),image.getY(),image.getWidth(),image.getHeight()).equals(source),"reuse vanilla atlas region");
    }
    private static void item(Control scene,Slot slot,int x,int y){Rect a=slot.getAbsoluteBounds(),b=scene.getAbsoluteBounds();check(a.x()-b.x()+1==x&&a.y()-b.y()+1==y,"item aligns with native slot: "+slot.getDebugName());}
    private static void title(Control scene,Inventory inventory,int x,int y){
        Label title=inventory.getTitleControl();Rect a=title.getAbsoluteBounds(),b=scene.getAbsoluteBounds();
        check(inventory.getShowTitle()&&title.getVisible()&&title.getParent()==inventory,"player inventory owns its visible title");
        check(a.x()-b.x()==x&&a.y()-b.y()==y,"built-in title receives its current two-pixel visual lift");
        check(title.getThemeItem(OreTheme.FONT_COLOR)==OreTheme.TEXT_DARK,"inventory title uses dark text without recoloring item counts");
        check(inventory.getSlotControl(0).getChild(0).getThemeItem(OreTheme.FONT_COLOR)!=OreTheme.TEXT_DARK,"item count keeps its normal text color");
        check(scene.getChildren().stream().filter(c->c instanceof Label).count()==1,"scene owns only its container caption");
    }
    private static Control find(Control root,String name){if(root.getDebugName().equals(name))return root;for(Control c:root.getChildren()){Control match=find(c,name);if(match!=null)return match;}return null;}
    private static void slots(Control root,List<Slot> out){if(root instanceof Slot slot)out.add(slot);for(Control c:root.getChildren())slots(c,out);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
