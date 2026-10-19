package io.github.luyingyilong.geg.organization.client;

import io.github.luyingyilong.geg.organization.*;
import io.github.luyingyilong.ore.*;
import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.event.EventGeometry;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Opt-in actual organization screen test; uses only transient client snapshots. */
@Mod.EventBusSubscriber(modid="geg",value=Dist.CLIENT)
public final class MemberDropdownClientTest {
    private static int phase,ticks;
    private static Window window;
    private static Dropdown<?> dropdown;
    private static int x,y;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(!Boolean.getBoolean("geg.member.dropdown.smoke")||event.phase!=TickEvent.Phase.END||phase==99)return;
        var mc=Minecraft.getInstance();if(mc.player==null)return;
        try {
            if(++ticks>700)throw new AssertionError("Timeout phase="+phase);
            if(phase==0){ClientOrganizationScreen.open();phase=1;}
            else if(phase==1&&ticks>25){
                UUID id=UUID.randomUUID(), self=mc.player.getUUID();
                var summary=new OrganizationSyncPacket.Summary(id,"Dropdown test","Members",Organization.JoinPolicy.UNRESTRICTED,self,
                    List.of(new OrganizationSyncPacket.Member(self,mc.player.getGameProfile().getName(),Organization.Role.LEADER)),List.of());
                ClientOrganizationScreen.acceptSnapshot(new OrganizationSyncPacket(List.of(summary),List.of(),""));
                var field=ClientOrganizationScreen.class.getDeclaredField("canvas");field.setAccessible(true);Object canvas=field.get(null);
                var show=canvas.getClass().getDeclaredMethod("showDetails",UUID.class);show.setAccessible(true);show.invoke(canvas,id);
                var root=canvas.getClass().getDeclaredField("window");root.setAccessible(true);window=(Window)root.get(canvas);
                phase=2;
            }else if(phase==2&&ticks>45){
                dropdown=find(window);check(dropdown!=null,"Retained member dropdown exists");
                var a=EventGeometry.displayed(dropdown);x=a.x()+a.width()/2+window.context().originX();y=a.y()+a.height()/2+window.context().originY();
                window.context().mui().updateState(x,y,0);
                check(net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ScreenEvent.MouseButtonPressed.Pre(mc.screen,x,y,0)),"Press accepted");
                phase=3;
            }else if(phase==3&&ticks>48){
                window.context().mui().updateState(x,y,0);
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ScreenEvent.MouseButtonReleased.Pre(mc.screen,x,y,0));
                check(dropdown.isOpen(),"Member dropdown opens after rendered frames");
                check(window.context().popups().open(),"Overlay popup present");
                phase=99;System.out.println("GEG_MEMBER_DROPDOWN_CLIENT_PASS");mc.stop();
            }
        }catch(Throwable error){phase=99;error.printStackTrace();System.out.println("GEG_MEMBER_DROPDOWN_CLIENT_FAIL");mc.stop();}
    }
    private static Dropdown<?> find(Control node){
        if(node instanceof Dropdown<?> d && d.visible()&&!d.options().isEmpty())return d;
        for(var child:node.children()){var d=find(child);if(d!=null)return d;}return null;
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
