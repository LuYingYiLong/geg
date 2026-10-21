package io.github.luyingyilong.ore.gallery;

import io.github.luyingyilong.ore.Slot;
import io.github.luyingyilong.ore.event.EventGeometry;
import io.github.luyingyilong.ore.layout.Control;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Isolated Gallery check; these are mock inventories, not real container interactions. */
@Mod.EventBusSubscriber(modid="geg",value=Dist.CLIENT)
public final class GalleryVanillaContainersClientTest {
    private static int phase,ticks,at,index;
    private static Control scene;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(!Boolean.getBoolean("ore.gallery.container.smoke")||event.phase!=TickEvent.Phase.END||phase==99)return;
        var mc=Minecraft.getInstance();if(mc.player==null)return;
        try{
            if(++ticks>400)throw new AssertionError("Gallery container timeout phase "+phase);
            if(phase==0){keyF9();phase=1;at=ticks;}
            else if(phase==1&&ticks>at+4&&OreGallery.getView()!=null){
                OreGallery.getView().select("inventory");phase=7;at=ticks;
            }else if(phase==7&&ticks>at+4){
                Control sample=OreGallery.getView().getSample();if(sample.getLayoutDirty())return;
                var inventory=findInventory(sample);var title=(io.github.luyingyilong.ore.Label)inventory.getChild(0);
                check(inventory.getShowTitle()&&title.getVisible()&&title.getBounds().x()==0&&title.getBounds().y()==-3,"default Inventory title is lifted three pixels");
                check(inventory.getSlotControl(0).getBounds().y()==title.getSize().height()+inventory.getThemeItem(io.github.luyingyilong.ore.Inventory.TITLE_GAP),"title reserves space above slots in the real client");
                capture("ore-gallery-inventory-title.png");
                OreGallery.getView().select("containers");scene=OreGallery.getView().getSample();phase=2;at=ticks;
            }else if(phase==2&&ticks>at+4){
                check(scene==OreGallery.getView().getSample(),"resizing retains the native scenes");
                // World loading may run several ticks before the first Gallery render/layout.
                if(scene.getLayoutDirty())return;
                check(find(scene,"furnaceBackground").getSize().width()==176,"background uses native dimensions: "+find(scene,"furnaceBackground").getBounds());
                nativePlayer("Gallery vanilla chest","chestPlayer",74,85,143);
                nativePlayer("Gallery vanilla blast furnace","furnacePlayer",72,84,142);
                if(index<4){OreGallery.getView().setPreviewWidth(new int[]{127,128,129,0}[index++]);at=ticks;}
                else{OreGallery.getView().getContentScroll().setScroll(0,0);phase=3;at=ticks;}
            }else if(phase==3&&ticks>at+5){
                capture("ore-gallery-chest.png");OreGallery.getView().getContentScroll().setScroll(0,Integer.MAX_VALUE);phase=4;at=ticks;
            }else if(phase==4&&ticks>at+5){
                capture("ore-gallery-blast-furnace.png");
                Slot input=(Slot)find(scene,"furnaceInput");var rect=EventGeometry.displayed(input);var clip=EventGeometry.clip(input);
                if(clip!=null)rect=EventGeometry.intersect(rect,clip);
                check(rect.width()>0&&rect.height()>0&&input.hasTooltip()&&!input.getBackgroundVisible(),"visible transparent slot retains item tooltip");
                int x=rect.x()+rect.width()/2,y=rect.y()+rect.height()/2;
                long handle=mc.getWindow().getWindow();var cursor=GLFW.glfwSetCursorPosCallback(handle,null);GLFW.glfwSetCursorPosCallback(handle,cursor);
                double px=(double)x*mc.getWindow().getScreenWidth()/mc.screen.width,py=(double)y*mc.getWindow().getScreenHeight()/mc.screen.height;
                GLFW.glfwSetCursorPos(handle,px,py);cursor.invoke(handle,px,py);
                check(OreGallery.getView().getWindow().tooltipTarget(x,y)==input,"background and progress do not swallow slot hover");
                phase=5;at=ticks;
            }else if(phase==5&&ticks>at+5){
                capture("ore-gallery-blast-furnace-tooltip.png");OreGallery.stop();phase=6;at=ticks;
            }else if(phase==6&&ticks>at+4){
                check(scene.isDisposed(),"scene and mock data are freed");phase=99;System.out.println("ORE_GALLERY_VANILLA_CONTAINERS_CLIENT_PASS");mc.stop();
            }
        }catch(Throwable error){phase=99;capture("ore-gallery-container-failure.png");error.printStackTrace();System.out.println("ORE_GALLERY_VANILLA_CONTAINERS_CLIENT_FAIL");mc.stop();}
    }
    private static void capture(String name){var mc=Minecraft.getInstance();net.minecraft.client.Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->System.out.println(message.getString()));}
    private static void keyF9(){var mc=Minecraft.getInstance();long h=mc.getWindow().getWindow();var key=GLFW.glfwSetKeyCallback(h,null);GLFW.glfwSetKeyCallback(h,key);key.invoke(h,GLFW.GLFW_KEY_F9,0,GLFW.GLFW_PRESS,0);key.invoke(h,GLFW.GLFW_KEY_F9,0,GLFW.GLFW_RELEASE,0);}
    private static Control find(Control c,String name){if(c.getDebugName().equals(name))return c;for(Control child:c.getChildren()){Control result=find(child,name);if(result!=null)return result;}return null;}
    private static io.github.luyingyilong.ore.Inventory findInventory(Control c){if(c instanceof io.github.luyingyilong.ore.Inventory inventory)return inventory;for(Control child:c.getChildren()){var result=findInventory(child);if(result!=null)return result;}return null;}
    private static void nativePlayer(String container,String name,int titleY,int firstY,int hotbarY){
        Control background=find(scene,container);var inventory=(io.github.luyingyilong.ore.Inventory)find(scene,name);
        var origin=background.getAbsoluteBounds();var title=inventory.getTitleControl().getAbsoluteBounds();
        var first=inventory.getSlotControl(0).getAbsoluteBounds();var hotbar=inventory.getSlotControl(27).getAbsoluteBounds();
        check(inventory.getShowTitle()&&title.x()-origin.x()==8&&title.y()-origin.y()==titleY-3,"built-in title is lifted without shifting the native slot anchors");
        check(first.x()-origin.x()+1==8&&first.y()-origin.y()+1==firstY&&hotbar.y()-origin.y()+1==hotbarY,"player slots retain vanilla coordinates in the client");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
