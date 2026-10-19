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
    private static Object canvas;
    private static Scroll membershipsScroll;
    private static Box organizationList;
    private static List<Control> shell;
    private static OrganizationSyncPacket snapshot;
    private static UUID selected;
    private static int closedAt;
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
                List<OrganizationSyncPacket.Summary> organizations = new ArrayList<>(); organizations.add(summary);
                for (int i = 1; i < 16; i++) organizations.add(new OrganizationSyncPacket.Summary(UUID.randomUUID(),
                        "Organization " + i, "List refresh / 多行简介", summary.joinPolicy(), self, summary.members(), List.of()));
                snapshot = new OrganizationSyncPacket(List.copyOf(organizations), List.of(), ""); selected = id;
                ClientOrganizationScreen.acceptSnapshot(snapshot);
                var field=ClientOrganizationScreen.class.getDeclaredField("canvas");field.setAccessible(true);canvas=field.get(null);
                var root=canvas.getClass().getDeclaredField("window");root.setAccessible(true);window=(Window)root.get(canvas);
                var scroll = canvas.getClass().getDeclaredField("membershipsScroll"); scroll.setAccessible(true); membershipsScroll = (Scroll)scroll.get(canvas);
                var list = canvas.getClass().getDeclaredField("organizationList"); list.setAccessible(true); organizationList = (Box)list.get(canvas);
                shell = membershipsScroll.getParent().getChildren();
                Control previousRow = organizationList.getChildren().get(0);
                for (int i = 0; i < 20; i++) ClientOrganizationScreen.acceptSnapshot(snapshot);
                verifyMemberships();
                check(previousRow.isQueuedForDeletion() || previousRow.isDisposed(), "Replaced rows are permanently cleaned up");
                phase=2;
            }else if(phase==2&&ticks>45){
                verifyMemberships();
                capture("organization-list-before-details.png");
                check(membershipsScroll.getMaxScrollY() > 0, "Only the organization list overflows its Scroll viewport");
                check(shell.get(0).getAbsoluteBounds().y() == 0, "Navbar stays at the top");
                membershipsScroll.setScroll(0, 30);
                ClientOrganizationScreen.acceptSnapshot(snapshot); verifyMemberships();
                check(membershipsScroll.getScrollY() == 30, "A snapshot preserves the list's scroll position");
                var show=canvas.getClass().getDeclaredMethod("showDetails",UUID.class);show.setAccessible(true);show.invoke(canvas,selected);
                phase=10;
            }else if(phase==10&&ticks>55){
                dropdown=find(window);check(dropdown!=null,"Retained member dropdown exists");
                var a=EventGeometry.displayed(dropdown);x=a.x()+a.width()/2+window.getContext().getOriginX();y=a.y()+a.height()/2+window.getContext().getOriginY();
                movePointer(x,y); phase=11;
            }else if(phase==11&&ticks>58){
                mouse(org.lwjgl.glfw.GLFW.GLFW_PRESS);
                phase=3;
            }else if(phase==3&&ticks>61){
                mouse(org.lwjgl.glfw.GLFW.GLFW_RELEASE);
                check(dropdown.isOpen(),"Member dropdown opens after rendered frames");
                check(window.getContext().getPopups().open(),"Overlay popup present");
                window.getContext().getPopups().cancel();
                var close=canvas.getClass().getDeclaredMethod("closeDetails"); close.setAccessible(true); close.invoke(canvas);
                closedAt=ticks;phase=4;
            }else if(phase==4&&ticks>closedAt+15){
                verifyMemberships();
                check(window.getChildren().stream().noneMatch(Control::isDisposed), "Returning from details retains a live root tree");
                ClientOrganizationScreen.acceptSnapshot(new OrganizationSyncPacket(List.of(),List.of(),""));
                check(organizationList.getChildren().isEmpty(), "Leaving the last organization clears old rows");
                ClientOrganizationScreen.acceptSnapshot(snapshot);verifyMemberships();
                closedAt=ticks;phase=5;
            }else if(phase==5&&ticks>closedAt+5){
                capture("organization-list-refresh.png");
                phase=99;System.out.println("GEG_ORGANIZATION_LIST_REFRESH_PASS");System.out.println("GEG_MEMBER_DROPDOWN_CLIENT_PASS");mc.stop();
            }
        }catch(Throwable error){phase=99;error.printStackTrace();System.out.println("GEG_MEMBER_DROPDOWN_CLIENT_FAIL");mc.stop();}
    }
    private static void movePointer(int x,int y){
        var mc=Minecraft.getInstance();long handle=mc.getWindow().getWindow();
        double px=(double)x*mc.getWindow().getScreenWidth()/mc.screen.width,py=(double)y*mc.getWindow().getScreenHeight()/mc.screen.height;
        var callback=org.lwjgl.glfw.GLFW.glfwSetCursorPosCallback(handle,null);org.lwjgl.glfw.GLFW.glfwSetCursorPosCallback(handle,callback);
        org.lwjgl.glfw.GLFW.glfwSetCursorPos(handle,px,py);callback.invoke(handle,px,py);
    }
    private static void mouse(int action){
        long handle=Minecraft.getInstance().getWindow().getWindow();
        var callback=org.lwjgl.glfw.GLFW.glfwSetMouseButtonCallback(handle,null);org.lwjgl.glfw.GLFW.glfwSetMouseButtonCallback(handle,callback);
        callback.invoke(handle,0,action,0);
    }
    private static void capture(String name)throws Exception{
        var mc=Minecraft.getInstance();var path=mc.gameDirectory.toPath().resolve("screenshots/"+name);java.nio.file.Files.createDirectories(path.getParent());
        try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(path);}
        var field=net.minecraft.client.gui.GuiGraphics.class.getDeclaredField("scissorStack");field.setAccessible(true);var stack=field.get(window.getContext().getMui().getGraphics());
        var rectangles=stack.getClass().getDeclaredField("stack");rectangles.setAccessible(true);
        check(((java.util.Collection<?>)rectangles.get(stack)).isEmpty(), "No clip remains after rendering " + name);
    }
    private static void verifyMemberships(){
        check(!membershipsScroll.isDisposed() && !organizationList.isDisposed(), "The list Scroll survives refreshes");
        check(membershipsScroll.getParent().getChildren().equals(shell), "Navbar, action row and Scroll are reused");
        check(organizationList.getChildren().size()==snapshot.organizations().size(), "All memberships are present");
        check(organizationList.getChildren().stream().noneMatch(Control::isDisposed), "Visible organization rows are alive");
    }
    private static Dropdown<?> find(Control node){
        if(node instanceof Dropdown<?> d && d.getVisible()&&!d.getOptions().isEmpty())return d;
        for(var child:node.getChildren()){var d=find(child);if(d!=null)return d;}return null;
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
