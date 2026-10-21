package io.github.luyingyilong.ore.window.container;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import java.util.UUID;

/** Server-only fixtures: no client UI imports, no host business classes, never in the release artifact. */
@GameTestHolder("ore_test")
@PrefixGameTestTemplate(false)
public final class ContainerGameTests {
    @GameTest(template="empty")
    public static void twoPlayersShareStorage(GameTestHelper helper){
        var a=FakePlayerFactory.get(helper.getLevel(),new GameProfile(UUID.randomUUID(),"ore_a"));
        var b=FakePlayerFactory.get(helper.getLevel(),new GameProfile(UUID.randomUUID(),"ore_b"));
        a.getInventory().clearContent();b.getInventory().clearContent();
        var storage=new SimpleContainer(27);storage.setItem(0,new ItemStack(Items.IRON_INGOT,32));
        var first=ChestMenu.threeRows(1,a.getInventory(),storage);
        var second=ChestMenu.threeRows(2,b.getInventory(),storage);
        first.clicked(0,0,ClickType.PICKUP,a);second.clicked(0,0,ClickType.PICKUP,b);
        helper.assertTrue(first.getCarried().getCount()==32&&second.getCarried().isEmpty(),"Same stack was picked up twice");
        first.clicked(1,1,ClickType.PICKUP,a);second.clicked(1,0,ClickType.PICKUP,b);
        helper.assertTrue(first.getCarried().getCount()==31&&second.getCarried().getCount()==1,"Shared split lost synchronization");
        first.clicked(2,0,ClickType.PICKUP,a);second.clicked(3,0,ClickType.PICKUP,b);
        first.quickMoveStack(a,2);second.quickMoveStack(b,3);
        helper.assertTrue(a.getInventory().countItem(Items.IRON_INGOT)+b.getInventory().countItem(Items.IRON_INGOT)==32,"Shift transfer changed total items");
        first.setCarried(new ItemStack(Items.GOLD_INGOT,7));first.removed(a);
        helper.assertTrue(first.getCarried().isEmpty()&&a.getInventory().countItem(Items.GOLD_INGOT)==7,"Closing lost carried items");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void furnaceRestrictionsAndProgress(GameTestHelper helper){
        var player=FakePlayerFactory.get(helper.getLevel(),new GameProfile(UUID.randomUUID(),"ore_furnace"));
        var storage=new SimpleContainer(3);var data=new SimpleContainerData(4);
        storage.setItem(0,new ItemStack(Items.IRON_ORE,3));storage.setItem(1,new ItemStack(Items.COAL,2));storage.setItem(2,new ItemStack(Items.IRON_INGOT,5));
        var menu=new FurnaceMenu(3,player.getInventory(),storage,data);
        helper.assertTrue(!menu.getSlot(2).mayPlace(new ItemStack(Items.STONE)),"Result slot accepted input");
        helper.assertTrue(!menu.getSlot(1).mayPlace(new ItemStack(Items.STONE))&&menu.getSlot(1).mayPlace(new ItemStack(Items.COAL)),"Fuel restrictions changed");
        menu.setCarried(new ItemStack(Items.STONE));menu.clicked(2,0,ClickType.PICKUP,player);
        helper.assertTrue(menu.getCarried().is(Items.STONE)&&storage.getItem(2).getCount()==5,"Invalid output swap changed storage");
        menu.setData(0,200);menu.setData(1,400);menu.setData(2,20);menu.setData(3,100);
        helper.assertTrue(menu.isLit()&&menu.getLitProgress()>0&&menu.getBurnProgress()>0,"Synced progress unavailable");
        menu.setCarried(ItemStack.EMPTY);menu.clicked(2,0,ClickType.PICKUP,player);
        helper.assertTrue(menu.getCarried().getCount()==5&&storage.getItem(2).isEmpty(),"Valid result pickup failed");
        helper.succeed();
    }
}
