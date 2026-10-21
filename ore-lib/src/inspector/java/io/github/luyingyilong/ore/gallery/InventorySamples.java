package io.github.luyingyilong.ore.gallery;

import io.github.luyingyilong.ore.*;
import io.github.luyingyilong.ore.inventory.MemoryInventorySource;
import io.github.luyingyilong.ore.layout.*;
import net.minecraft.network.chat.Component;
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
        Box chest=new Box().setGap(4);
        chest.addChild(text("chest_title"));
        chest.addChild(new Inventory().setSource(source(root,27)));
        chest.addChild(new Divider());
        chest.addChild(new Inventory().setSource(source(root,36)).setPlayerSlots(
                IntStream.range(0,27).boxed().toList(),IntStream.range(27,36).boxed().toList()));
        root.addChild(new Scroll().setContent(new Panel().setChild(chest)));

        Box furnace=new Box().setGap(4);
        furnace.addChild(text("furnace_title"));
        var items=source(root,3);
        Box machine=new Box().setOrientation(Box.Orientation.HORIZONTAL).setGap(8);
        Box inputs=new Box().setGap(4);
        inputs.addChild(new Slot().setBinding(items.getBinding(0)));
        TextureProgressBar burning=TextureProgressSamples.flame(80);
        inputs.addChild(burning);
        inputs.addChild(new Slot().setBinding(items.getBinding(1)));
        machine.addChild(inputs);
        machine.addChild(TextureProgressSamples.arrow(45));
        machine.addChild(new Slot().setBinding(items.getBinding(2)));
        furnace.addChild(machine);
        furnace.addChild(new Inventory().setSource(source(root,36)).setPlayerSlots(
                IntStream.range(0,27).boxed().toList(),IntStream.range(27,36).boxed().toList()));
        root.addChild(new Scroll().setContent(new Panel().setChild(furnace)));
        return root;
    }
}
