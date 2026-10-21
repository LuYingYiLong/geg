package io.github.luyingyilong.ore.menu;

import io.github.luyingyilong.ore.*;
import io.github.luyingyilong.ore.event.EventGeometry;
import io.github.luyingyilong.ore.input.*;
import io.github.luyingyilong.ore.layout.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import java.util.*;
import java.util.stream.IntStream;

/** Opt-in isolated world fixture. Uses genuine vanilla Menu packets, not direct slot mutations on the client. */
@Mod.EventBusSubscriber(modid="geg",value=Dist.CLIENT)
public final class MenuClientTest {
    private static int ticks,changed,stage=-1;
    private static boolean done;
    private static volatile SimpleContainer storage;
    private static volatile Throwable serverError;
    private static TestScreen screen;
    private static TestFurnaceScreen furnace;
    @SubscribeEvent public static void opening(net.minecraftforge.client.event.ScreenEvent.Opening event){
        if(Boolean.getBoolean("ore.menu.smoke")&&stage>=0&&event.getNewScreen() instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen chest)
            event.setNewScreen(new TestScreen(chest.getMenu(),Minecraft.getInstance().player.getInventory(),chest.getTitle()));
        if(Boolean.getBoolean("ore.menu.smoke")&&stage>=13&&event.getNewScreen() instanceof net.minecraft.client.gui.screens.inventory.FurnaceScreen original)
            event.setNewScreen(new TestFurnaceScreen(original.getMenu(),Minecraft.getInstance().player.getInventory(),original.getTitle()));
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(!Boolean.getBoolean("ore.menu.smoke")||done||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(mc.player==null)return;
        try{
            if(serverError!=null)throw new AssertionError("Server fixture failed",serverError);
            if(++ticks>1500)throw new AssertionError("Container timeout stage="+stage);
            if(ticks<changed+8)return;
            if(stage>=14){furnaceTick(mc);return;}
            if(stage==-1){
                mc.getSingleplayerServer().execute(()->{
                    try{
                        var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        player.getInventory().clearContent();
                        storage=new SimpleContainer(27);
                        storage.setItem(0,new ItemStack(Items.IRON_INGOT,32));storage.setItem(1,new ItemStack(Items.COAL,16));
                        player.openMenu(new SimpleMenuProvider((id,inv,p)->ChestMenu.threeRows(id,inv,storage),Component.literal("Ore container smoke")));
                    }catch(Throwable error){serverError=error;}
                });stage=0;changed=ticks;return;
            }
            if(!(mc.screen instanceof TestScreen test)){
                if(stage==13){
                    check(mc.player.containerMenu==mc.player.inventoryMenu,"Escape closes the actual Menu");
                    mc.getSingleplayerServer().execute(()->{
                        try{
                            var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                            var items=new SimpleContainer(3);items.setItem(0,new ItemStack(Items.IRON_ORE,3));items.setItem(1,new ItemStack(Items.COAL,2));items.setItem(2,new ItemStack(Items.IRON_INGOT,5));
                            var data=new SimpleContainerData(4);data.set(0,200);data.set(1,400);data.set(2,30);data.set(3,100);
                            player.openMenu(new SimpleMenuProvider((id,inv,p)->new FurnaceMenu(id,inv,items,data),Component.literal("Ore furnace smoke")));
                        }catch(Throwable error){serverError=error;}
                    });stage=14;changed=ticks;
                }
                return;
            }
            screen=test;if(!screen.getContent().isReady())return;
            System.out.println("ORE_MENU_STEP "+stage+" carried="+screen.getMenu().getCarried().getCount());
            switch(stage++){
                case 0->{
                    check(screen.getMenuSession().isValid(),"real Menu bound");
                    var original=screen.getMenuSession();int width=screen.width,height=screen.height;
                    for(int narrow:new int[]{127,128,129}){
                        screen.resize(mc,narrow,180);screen.getOreWindow().getHost().getContext();
                        io.github.luyingyilong.ore.window.ScreenWindowHost.get(screen).prepareInput();
                        check(screen.getMenuSession()==original,"Resize retains the Menu and tree");
                        check(screen.top.getMinimumWidth()==162,"18px inventory preserves its nine columns");
                    }
                    screen.resize(mc,width,height);io.github.luyingyilong.ore.window.ScreenWindowHost.get(screen).prepareInput();click(0,0,0);
                }
                case 1->{check(carried()==32,"pickup uses Menu packet once");click(3,1,0);}
                case 2->{check(carried()==31&&item(3)==1,"right-click splits one");click(4,0,0);}
                case 3->{check(carried()==0&&item(4)==31,"place remaining stack");click(1,0,GLFW.GLFW_MOD_SHIFT);}
                case 4->{
                    check(item(1)==0&&IntStream.range(27,63).map(i->screen.getMenu().getSlot(i).getItem().is(Items.COAL)?item(i):0).sum()==16,"shift transfer reaches player view");
                    move(4);mouse(0,GLFW.GLFW_PRESS,0);mouse(0,GLFW.GLFW_RELEASE,0);
                }
                case 5->{check(carried()==31,"pickup for cross-view drag");move(5);mouse(0,GLFW.GLFW_PRESS,0);}
                case 6->{move(6);}
                case 7->{move(27);}
                case 8->{mouse(0,GLFW.GLFW_RELEASE,0);}
                case 9->{
                    check(item(5)>0&&item(6)>0&&item(27)>0,"drag distributes across both Inventory controls");
                    check(item(5)+item(6)+item(27)+carried()==31,"quick craft conserves items");
                    if(carried()>0)click(7,0,0);
                    var modal=new Modal(Component.literal("Input isolation")).setSize(180,100).setContent(new InputControl(new Input().setPlaceholder(Component.literal("Chinese / text"))));
                    screen.getOreWindow().getPopupOverlay().addChild(modal);
                }
                case 10->{
                    int old=item(5);click(5,0,0);check(item(5)==old,"modal blocks underlying container");
                    screen.getOreWindow().getPopupOverlay().getChild(0).queueFree();
                    Screenshot.grab(mc.gameDirectory,"ore-menu.png",mc.getMainRenderTarget(),message->{});
                }
                case 11->{
                    // A pending gesture loses its capture when the slot is hidden.
                    move(6);mouse(0,GLFW.GLFW_PRESS,0);
                    screen.top.getSlotControl(6).setVisible(false);
                    mouse(0,GLFW.GLFW_RELEASE,0);
                }
                case 12->{key(GLFW.GLFW_KEY_ESCAPE,GLFW.GLFW_PRESS,0);key(GLFW.GLFW_KEY_ESCAPE,GLFW.GLFW_RELEASE,0);}
                default->throw new AssertionError("Unexpected stage");
            }
            changed=ticks;
        }catch(Throwable error){done=true;System.err.println("ORE_MENU_CLIENT_FAIL stage="+stage);error.printStackTrace();mc.stop();}
    }
    private static void furnaceTick(Minecraft mc){
        if(stage==19&&!(mc.screen instanceof TestFurnaceScreen)){check(mc.player.containerMenu==mc.player.inventoryMenu,"Furnace closes its Menu");pass();return;}
        if(!(mc.screen instanceof TestFurnaceScreen test)||!test.getContent().isReady())return;
        furnace=test;
        System.out.println("ORE_FURNACE_STEP "+stage);
        switch(stage++){
            case 14->{check(furnace.getMenu().isLit()&&furnace.progress.getValue()>0&&furnace.fuel.getValue()>0,"Furnace progress is server synchronized");click(2,0,0);}
            case 15->{check(carried()==5,"Output pickup");click(1,0,0);}
            case 16->{check(carried()==5&&item(1)==2,"Fuel rejects ingots through real server slot rules");click(3,0,0);}
            case 17->{check(carried()==0,"Output moves into player inventory");Screenshot.grab(mc.gameDirectory,"ore-furnace.png",mc.getMainRenderTarget(),message->{});}
            case 18->{key(GLFW.GLFW_KEY_ESCAPE,GLFW.GLFW_PRESS,0);key(GLFW.GLFW_KEY_ESCAPE,GLFW.GLFW_RELEASE,0);}
            default->throw new AssertionError("Unexpected furnace stage");
        }
        changed=ticks;
    }
    private static void pass(){done=true;System.out.println("ORE_MENU_CLIENT_PASS");Minecraft.getInstance().stop();}
    private static AbstractContainerMenu currentMenu(){return stage>=15&&furnace!=null?furnace.getMenu():screen.getMenu();}
    private static int item(int index){return currentMenu().getSlot(index).getItem().getCount();}
    private static int carried(){return currentMenu().getCarried().getCount();}
    private static void click(int slot,int button,int mods){move(slot);mouse(button,GLFW.GLFW_PRESS,mods);mouse(button,GLFW.GLFW_RELEASE,mods);}
    private static void move(int id){
        io.github.luyingyilong.ore.Slot slot=stage>=15&&furnace!=null?(id<3?furnace.machine.get(id):furnace.player.getSlotControl(id))
                :(id<27?screen.top:screen.player).getSlotControl(id);Rect r=EventGeometry.displayed(slot);
        var mc=Minecraft.getInstance();long handle=mc.getWindow().getWindow();
        double x=(r.x()+r.width()/2.0)*mc.getWindow().getScreenWidth()/mc.screen.width;
        double y=(r.y()+r.height()/2.0)*mc.getWindow().getScreenHeight()/mc.screen.height;
        var callback=GLFW.glfwSetCursorPosCallback(handle,null);GLFW.glfwSetCursorPosCallback(handle,callback);
        GLFW.glfwSetCursorPos(handle,x,y);callback.invoke(handle,x,y);
    }
    private static void mouse(int button,int action,int mods){long handle=Minecraft.getInstance().getWindow().getWindow();var c=GLFW.glfwSetMouseButtonCallback(handle,null);GLFW.glfwSetMouseButtonCallback(handle,c);c.invoke(handle,button,action,mods);}
    private static void key(int key,int action,int mods){long handle=Minecraft.getInstance().getWindow().getWindow();var c=GLFW.glfwSetKeyCallback(handle,null);GLFW.glfwSetKeyCallback(handle,c);c.invoke(handle,key,GLFW.glfwGetKeyScancode(key),action,mods);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static final class TestScreen extends OreMenuScreen<ChestMenu>{
        Inventory top,player;
        TestScreen(ChestMenu menu,net.minecraft.world.entity.player.Inventory inventory,Component title){super(menu,inventory,title);}
        @Override protected Control createContent(MenuSession session){
            top=new Inventory().setSource(session.getSource()).setSlotIndices(IntStream.range(0,27).boxed().toList()).setMode(Inventory.Mode.CONTAINER);
            player=new Inventory().setSource(session.getSource()).setSlotIndices(IntStream.range(27,63).boxed().toList()).setMode(Inventory.Mode.CONTAINER)
                    .setPlayerSlots(IntStream.range(27,54).boxed().toList(),IntStream.range(54,63).boxed().toList());
            Box content=new Box().setGap(4);content.addChild(new Label(title));content.addChild(top);content.addChild(new Divider());content.addChild(player);
            content.setPreferred(250,230);
            return new Panel().setChild(new Padding(new Padding.Insets(4,4,4,4)).setChild(content));
        }
    }
    private static final class TestFurnaceScreen extends OreMenuScreen<FurnaceMenu>{
        final Map<Integer,io.github.luyingyilong.ore.Slot> machine=new HashMap<>();
        Inventory player;ProgressBar progress,fuel;
        TestFurnaceScreen(FurnaceMenu menu,net.minecraft.world.entity.player.Inventory inventory,Component title){super(menu,inventory,title);}
        @Override protected Control createContent(MenuSession session){
            for(int i=0;i<3;i++)machine.put(i,new io.github.luyingyilong.ore.Slot().setBinding(session.getBinding(i)).setMode(Inventory.Mode.CONTAINER));
            progress=new ProgressBar();fuel=new ProgressBar().setDirection(ProgressBar.Direction.BOTTOM_TO_TOP);
            fuel.setPreferred(10,26);
            Box inputs=new Box().setGap(4);inputs.addChild(machine.get(0));inputs.addChild(fuel);inputs.addChild(machine.get(1));
            Box row=new Box().setOrientation(Box.Orientation.HORIZONTAL).setGap(8);row.addChild(inputs);row.addChild(progress);row.addChild(machine.get(2));
            player=new Inventory().setSource(session.getSource()).setSlotIndices(IntStream.range(3,39).boxed().toList()).setMode(Inventory.Mode.CONTAINER)
                    .setPlayerSlots(IntStream.range(3,30).boxed().toList(),IntStream.range(30,39).boxed().toList());
            Box body=new Box().setGap(6);body.addChild(new Label(title));body.addChild(row);body.addChild(player);
            return new Panel().setChild(new Padding(new Padding.Insets(4,4,4,4)).setChild(body));
        }
        @Override protected void containerTick(){super.containerTick();progress.setValueNoSignal(menu.getBurnProgress()*100.0/24);fuel.setValueNoSignal(menu.getLitProgress()*100.0/13);}
    }
}
