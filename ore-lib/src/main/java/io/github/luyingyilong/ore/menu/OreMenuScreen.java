package io.github.luyingyilong.ore.menu;

import io.github.luyingyilong.ore.event.*;
import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.render.Canvas;
import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.window.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;
import org.lwjgl.glfw.GLFW;

/** MenuScreens registration target. The host supplies the Menu; Ore supplies the entire control tree. */
public abstract class OreMenuScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> implements MenuInteraction {
    private ScreenWindowHost host;
    private MenuSession session;
    private Canvas canvas;
    private MenuSurface surface;
    private boolean closing;
    private boolean lateOutsidePointer;

    protected OreMenuScreen(M menu,Inventory inventory,Component title){super(menu,inventory,title);}
    protected abstract Control createContent(MenuSession session);
    public final MenuSession getMenuSession(){return session;}
    public final Window getOreWindow(){return host==null?null:host.getWindow();}
    public final Control getContent(){return surface==null?null:surface.content;}

    @Override protected void init(){
        super.init();
        if(session!=null&&!session.isValid()){
            finish();minecraft.tell(()->{if(minecraft.screen==this)minecraft.setScreen(null);});return;
        }
        if(closing)return;
        if(host==null){
            host=ScreenWindowHost.getOrCreate(this);
            host.getWindow().setDebugName("Ore Menu: "+title.getString());
            session=new MenuSession(host.getWindow(),menu,this,()->!closing&&minecraft.player!=null&&minecraft.player.containerMenu==menu);
            try{
                surface=new MenuSurface(java.util.Objects.requireNonNull(createContent(session)));
                canvas=new Canvas(surface);canvas.attach(host);
            }catch(RuntimeException|Error error){finish();throw error;}
        }
        canvas.setViewport(new Rect(0,0,width,height));
    }
    @Override protected void containerTick(){
        if(session==null)return;
        if(!session.isValid()){finish();return;}
        session.update();
    }
    @Override public void render(GuiGraphics graphics,int x,int y,float partial){
        if(closing||canvas==null)return;
        if(!session.isValid()){finish();return;}
        session.update();host.prepareInput();
        graphics.fill(0,0,width,height,host.getWindow().getThemeItem(io.github.luyingyilong.ore.theme.OreTheme.OVERLAY));
        Rect bounds=EventGeometry.displayed(surface.scroll);
        leftPos=bounds.x();topPos=bounds.y();imageWidth=bounds.width();imageHeight=bounds.height();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ContainerScreenEvent.Render.Background(this,graphics,x,y));
        canvas.render(graphics,x,y,partial);
        hoveredSlot=session.getNativeSlotAt(x,y);
        graphics.pose().pushPose();
        try{
            graphics.pose().translate(leftPos,topPos,0);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ContainerScreenEvent.Render.Foreground(this,graphics,x,y));
            graphics.flush();
        }finally{graphics.pose().popPose();}
        if(host.getWindow().getFocusManager().getInputScope()==null&&!host.getWindow().getContext().getPopups().open())
            ((ContainerScreenAccess)this).oreRenderCarried(graphics,menu.getCarried(),x,y);
    }
    @Override protected final void renderBg(GuiGraphics graphics,float partial,int x,int y){ }
    @Override protected boolean hasClickedOutside(double x,double y,int left,int top,int button){
        return surface==null||!EventGeometry.contains(surface.scroll,(int)x,(int)y);
    }
    /** ScreenEvent/early bridges already dispatched the actual event. Never run vanilla twice. */
    @Override public final boolean mouseClicked(double x,double y,int button){
        // Viewers have already had their normal chance to consume an outside click.
        // ScreenInputBridge deliberately did not dispatch it, so this is still the only Ore event.
        if(host==null||getPointerInside((int)x,(int)y))return false;
        lateOutsidePointer=true;
        try{return host.getWindow().dispatchInput(new PointerEvent(host.getContext(),PointerEvent.Action.PRESS,
                (int)x,(int)y,button,host.getContext().getModifiers()));}
        finally{lateOutsidePointer=false;}
    }
    @Override public final boolean mouseReleased(double x,double y,int button){return false;}
    @Override public final boolean mouseDragged(double x,double y,int button,double dx,double dy){return false;}
    @Override public final boolean keyPressed(int key,int scan,int mods){return false;}
    @Override public final boolean keyReleased(int key,int scan,int mods){return false;}

    @Override public final boolean pointer(PointerEvent event){
        hoveredSlot=session.getNativeSlotAt(event.windowX(),event.windowY());
        boolean handled=switch(event.action()){
            case PRESS->super.mouseClicked(event.windowX(),event.windowY(),event.button());
            case RELEASE->super.mouseReleased(event.windowX(),event.windowY(),event.button());
            case MOVE->isQuickCrafting||host.getWindow().getPointerCaptured(event.button())
                    ?super.mouseDragged(event.windowX(),event.windowY(),event.button(),0,0):false;
        };
        session.getSource().refresh();return handled;
    }
    @Override public final boolean key(KeyEvent event){
        hoveredSlot=session.getKeyboardSlot();
        if(event.action()==KeyEvent.Action.RELEASE)return false;
        if(event.action()==KeyEvent.Action.REPEAT)return true;
        return super.keyPressed(event.keyCode(),event.scanCode(),event.modifiers());
    }
    @Override public final void activate(int slotId){slotClicked(menu.getSlot(slotId),slotId,0,ClickType.PICKUP);}
    @Override public final boolean ownsPointer(int x,int y){
        return getPointerInside(x,y)||lateOutsidePointer;
    }
    public final boolean getPointerInside(int x,int y){return surface!=null&&EventGeometry.contains(surface.scroll,x,y);}
    @Override public final void cancel(){if((Object)this instanceof ContainerScreenAccess bridge)bridge.oreCancelGesture();}
    @Override public final void renderSlot(io.github.luyingyilong.ore.Slot view,DrawContext context,int x,int y){
        var slot=menu.getSlot(view.getBinding().slot());var graphics=context.getGraphics();
        graphics.flush();graphics.pose().pushPose();
        try{graphics.pose().translate(x-slot.x,y-slot.y,0);((ContainerScreenAccess)this).oreRenderSlot(graphics,slot);graphics.flush();}
        finally{graphics.pose().popPose();}
    }
    @Override public net.minecraft.world.inventory.Slot getSlotUnderMouse(){
        return host==null||session==null?null:session.getNativeSlotAt(host.getContext().getMouseX(),host.getContext().getMouseY());
    }
    @Override public void onClose(){
        if(closing)return;
        super.onClose();finish();
    }
    @Override public void removed(){
        // Viewers may temporarily replace this Screen. Only an ended Menu is permanently freed.
        if(session!=null){session.cancel();if(!session.isValid())finish();}
    }
    private void finish(){
        if(closing)return;closing=true;
        if(session!=null)session.close();
        if(canvas!=null)canvas.close();
        if(host!=null)host.close();
    }
    private static final class MenuSurface extends Container {
        final Control content;
        final Scroll scroll;
        MenuSurface(Control content){this.content=content;scroll=new Scroll().setContent(content);addChild(scroll);}
        @Override public void layout(Rect bounds,Font font){
            super.layout(bounds,font);
            Size natural=content.desiredSize(font,new Size(bounds.width(),bounds.height()));
            int w=Math.min(bounds.width(),Math.max(content.getMinimumWidth(),natural.width()));
            int h=Math.min(bounds.height(),Math.max(content.getMinimumHeight(),natural.height()));
            layoutChild(scroll,new Rect(Math.max(0,(bounds.width()-w)/2),Math.max(0,(bounds.height()-h)/2),w,h),font);
        }
    }
}
