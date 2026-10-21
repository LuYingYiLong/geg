package io.github.luyingyilong.ore.gallery;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.SizeFlags;
import io.github.luyingyilong.ore.control.display.Label;
import io.github.luyingyilong.ore.control.display.TextureRect;
import io.github.luyingyilong.ore.control.inventory.Inventory;
import io.github.luyingyilong.ore.control.inventory.Slot;
import io.github.luyingyilong.ore.control.layout.Box;
import io.github.luyingyilong.ore.control.layout.Scroll;
import io.github.luyingyilong.ore.control.progress.ProgressBar;
import io.github.luyingyilong.ore.control.progress.TextureProgressBar;

import io.github.luyingyilong.ore.control.inventory.source.MemoryInventorySource;
import io.github.luyingyilong.ore.render.TextureRegion;
import io.github.luyingyilong.ore.render.TextureSource;
import io.github.luyingyilong.ore.theme.OreTheme;
import io.github.luyingyilong.ore.theme.StyleBoxEmpty;
import io.github.luyingyilong.ore.theme.ThemeInsets;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import java.util.stream.IntStream;

/** Isolated demonstration storage; none of these sources is a player or world inventory. */
final class InventorySamples {
    private static Label text(String key){return new Label(GalleryView.tr("sample."+key)).setWrap(Label.Wrap.WORD);}
    private static MemoryInventorySource source(Control owner,int size){
        MemoryInventorySource source=owner.getSignalScope().own(new MemoryInventorySource(size));
        if(size>0)source.setItem(0,new ItemStack(Items.IRON_ORE,32));
        if(size>1)source.setItem(1,new ItemStack(Items.COAL,16));
        if(size>2)source.setItem(2,new ItemStack(Items.IRON_INGOT,8));
        return source;
    }
    static Control inventory(){
        Box root=new Box().setGap(6);
        root.addChild(text("inventory_preview"));
        var preview=new Inventory().setSource(source(root,18));
        root.addChild(new Scroll().setContent(preview));
        root.addChild(text("inventory_select"));
        var selectable=new Inventory().setSource(source(root,36)).setMode(Inventory.Mode.SELECT)
                .setPlayerSlots(IntStream.range(0,27).boxed().toList(),IntStream.range(27,36).boxed().toList());
        root.addChild(new Scroll().setContent(selectable));
        return root;
    }
    static Control progress(){
        Box root=new Box().setGap(6);
        root.addChild(text("progress_directions"));
        for(var direction:ProgressBar.Direction.values()){
            ProgressBar progress=new ProgressBar().setValueNoSignal(65).setDirection(direction).setShowPercentage(true);
            root.addChild(progress);
        }
        root.addChild(new ProgressBar().setValueNoSignal(0).setShowPercentage(true));
        root.addChild(new ProgressBar().setValueNoSignal(100).setShowPercentage(true));
        return root;
    }
    static Control containers(){
        Box root=new Box().setGap(8);
        root.addChild(text("container_demo"));
        root.addChild(chest());
        root.addChild(furnace());
        return root;
    }

    /** Vanilla's three-row chest reuses two regions of the six-row atlas. */
    private static Control chest(){
        Preview skin=new Preview("Gallery vanilla chest",168);
        ResourceLocation atlas=ResourceLocation.fromNamespaceAndPath("minecraft","textures/gui/container/generic_54.png");
        skin.place(image(atlas,0,0,176,71),"chestBackgroundTop",0,0,176,71);
        skin.place(image(atlas,0,126,176,96),"chestBackgroundBottom",0,71,176,96);
        Inventory storage=transparent(new Inventory().setSource(source(skin,27)));
        skin.place(storage,"chestStorage",7,17,162,54);
        skin.place(player(skin,0),"chestPlayer",7,74,162,86);
        skin.place(caption("container.chest",false),"chestTitle",8,6,160,10);
        return skin;
    }

    private static Control furnace(){
        Preview skin=new Preview("Gallery vanilla blast furnace",166);
        ResourceLocation atlas=ResourceLocation.fromNamespaceAndPath("minecraft","textures/gui/container/blast_furnace.png");
        skin.place(image(atlas,0,0,176,166),"furnaceBackground",0,0,176,166);
        var items=source(skin,3);
        skin.place(transparent(new Slot().setBinding(items.getBinding(0))),"furnaceInput",55,16,18,18);
        skin.place(transparent(new Slot().setBinding(items.getBinding(1))),"furnaceFuel",55,52,18,18);
        // Vanilla draws the output item at (116,35); an 18px transparent slot starts 1px earlier.
        skin.place(transparent(new Slot().setBinding(items.getBinding(2))),"furnaceOutput",115,34,18,18);
        skin.place(player(skin,1),"furnacePlayer",7,72,162,87);
        TextureProgressBar flame=new TextureProgressBar().setProgressTexture(region(atlas,176,0,14,14))
                .setFillMode(TextureProgressBar.FillMode.BOTTOM_TO_TOP).setValueNoSignal(80);
        TextureProgressBar arrow=new TextureProgressBar().setProgressTexture(region(atlas,176,14,24,16)).setValueNoSignal(45);
        skin.place(flame,"furnaceFlame",56,36,14,14);
        skin.place(arrow,"furnaceArrow",79,34,24,16);
        skin.place(caption("container.blast_furnace",true),"furnaceTitle",8,6,160,10);
        return skin;
    }

    private static Inventory player(Control owner,int titleGap){
        Inventory inventory=transparent(new Inventory().setSource(source(owner,36)).setPlayerSlots(
                IntStream.range(0,27).boxed().toList(),IntStream.range(27,36).boxed().toList())).setShowTitle(true);
        inventory.setThemeOverride(Inventory.TITLE_GAP,titleGap).setThemeOverride(Inventory.TITLE_HEIGHT,10);
        // Vanilla's text starts one pixel to the right of the 18px slot frames.
        inventory.getTitleControl().setOffset(1,0).setThemeOverride(OreTheme.FONT_COLOR,OreTheme.TEXT_DARK);
        return inventory;
    }
    private static Inventory transparent(Inventory inventory){
        // Storage grids have no inventory caption; player() enables their built-in title.
        inventory.setShowTitle(false);
        inventory.setThemeOverride(Inventory.GAP,0).setThemeOverride(Inventory.SECTION_GAP,4);
        for(Control child:inventory.getChildren())if(child instanceof Slot slot)transparent(slot);
        return inventory;
    }
    private static Slot transparent(Slot slot){
        slot.setBackgroundVisible(false);
        slot.setThemeOverride(OreTheme.SLOT_PANEL,new StyleBoxEmpty(ThemeInsets.ZERO,ThemeInsets.ZERO));
        slot.setThemeOverride(Slot.PADDING_TOP,1).setThemeOverride(Slot.PADDING_RIGHT,1)
                .setThemeOverride(Slot.PADDING_BOTTOM,1).setThemeOverride(Slot.PADDING_LEFT,1);
        slot.setThemeOverride(Slot.COUNT_PADDING_TOP,0).setThemeOverride(Slot.COUNT_PADDING_RIGHT,0)
                .setThemeOverride(Slot.COUNT_PADDING_BOTTOM,0).setThemeOverride(Slot.COUNT_PADDING_LEFT,0);
        return slot;
    }
    private static Label caption(String key,boolean centered){
        Label label=new Label(Component.translatable(key)).setShadow(false).setOverflow(Label.Overflow.ELLIPSIS)
                .setAlignment(centered?Label.Horizontal.CENTER:Label.Horizontal.LEFT,Label.Vertical.TOP);
        label.setThemeOverride(OreTheme.FONT_COLOR,OreTheme.TEXT_DARK);
        return label;
    }
    private static TextureSource region(ResourceLocation atlas,int x,int y,int width,int height){
        return TextureSource.region(new TextureRegion(atlas,256,256,x,y,width,height));
    }
    private static TextureRect image(ResourceLocation atlas,int x,int y,int width,int height){
        return new TextureRect().setTexture(region(atlas,x,y,width,height)).setStretchMode(TextureRect.StretchMode.KEEP);
    }
    /** A retained fixed-pixel scene; Gallery's existing Scroll handles narrow previews. */
    private static final class Preview extends Control {
        Preview(String name,int height){
            setDebugName(name);setMinimum(176,height);setPreferred(176,height);
            setSizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_BEGIN).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
        }
        void place(Control control,String name,int x,int y,int width,int height){
            control.setDebugName(name);control.setPosition(x,y).setRequestedSize(width,height);addChild(control);
        }
    }
}
