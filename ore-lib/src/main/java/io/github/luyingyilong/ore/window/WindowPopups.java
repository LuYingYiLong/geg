package io.github.luyingyilong.ore.window;

import io.github.luyingyilong.ore.signal.Signals;

import brachy.modularui.screen.viewport.ModularGuiContext;
import io.github.luyingyilong.ore.*;
import io.github.luyingyilong.ore.event.EventGeometry;
import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.theme.OreTheme;
import io.github.luyingyilong.ore.theme.OreIcons;
import net.minecraft.client.gui.Font;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Dropdown menus are real Overlay subtrees, scoped to the trigger's owning layer. */
public final class WindowPopups {
    private final WindowContext context;
    private Dropdown<?> owner;
    private Popup popup;
    private boolean updating;
    WindowPopups(WindowContext context) { this.context=context; }
    private Dropdown<?> find(Control node) {
        if(node==null||!(node.isAlive() && node.getVisible())||node instanceof Popup||node instanceof Drawer drawer&&!drawer.isOpen())return null;
        var children=node.getChildren();
        for(int i=children.size()-1;i>=0;i--){var result=find(children.get(i));if(result!=null)return result;}
        return node instanceof Dropdown<?> dropdown&&dropdown.isOpen()
                &&context.getOwner().getFocusManager().allowsPopupOwner(dropdown)?dropdown:null;
    }
    public void synchronize() {
        if(updating)return;updating=true;
        try {
            Dropdown<?> next=find(context.getOwner());
            if(next!=null) {
                Rect visible=EventGeometry.displayed(next);Rect clip=EventGeometry.clip(next);
                if(clip!=null)visible=EventGeometry.intersect(visible,clip);
                if(visible.width()==0||visible.height()==0){next.close();next=null;}
            }
            if(owner!=next || owner!=null && !popup.options.equals(owner.getOptions())) {
                dismiss(false);
                if(next!=null) {
                    owner=next;popup=new Popup(next);
                    var popupOverlay = context.getOwner().getPopupOverlay();
                    popupOverlay.addChild(popup);
                    popup.layout(context.getOwner().getBounds(),context.getMui()==null?null:context.getMui().getFont());
                }
            }
        } finally {updating=false;}
    }
    private void dismiss(boolean restore) {
        Dropdown<?> previous=owner;Popup old=popup;owner=null;popup=null;
        if(old!=null)old.queueFree();
        if(previous!=null) {
            previous.close();
            if(restore && previous.getParent()!=null)context.getOwner().getFocusManager().restoreFocus(previous,context.getMui());
        }
    }
    public boolean open(){return owner!=null;}
    public void cancel(){dismiss(false);}

    public final class Popup extends Container {
        private final Dropdown<?> trigger;
        private final List<? extends Dropdown.Option<?>> options;
        private final Container panel=new Container() {
            private Panel background;
            @Override protected void render(ModularGuiContext mui,int x,int y) {
                if (background == null) background = ownPart("background", new Panel(OreTheme.DROPDOWN_PANEL));
                background.drawAt(mui,getScreenBounds(x,y));
            }

        };
        private final Scroll scroll=new Scroll();
        private final Items items=new Items();
        Popup(Dropdown<?> trigger) {
            this.trigger=trigger;options=trigger.getOptions();
            panel.addChild(scroll);scroll.setContent(items);this.addChild(panel);
            scroll.setVertical(Scroll.BarMode.NEVER).setHorizontal(Scroll.BarMode.DISABLED);
            for(var option:options) {
                Button button=Signals.configure(new Button(option.label()).setType(Button.Type.MENU).setAlignment(Button.TextAlignment.LEFT)
                        .setSize(trigger.getControlSize()), connected -> connected.pressed().connect(connected, ()->select(option)));
                if(trigger.getCheckable()&&Objects.equals(option.value(),trigger.getSelected()))
                    button.setTrailingIcon(new Button.Icon(OreIcons.CHECKED,8,8));
                items.addChild(button);
            }
        }
        @SuppressWarnings({"rawtypes","unchecked"}) private void select(Dropdown.Option<?> option){
            Dropdown selected=trigger;dismiss(true);selected.select(option.value());
        }
        @Override public boolean getFocusScope(){return true;}
        @Override public float getContentDepth(){return 6500;}
        @Override public boolean onFocusKey(int key,int modifiers){
            if(key!=GLFW.GLFW_KEY_ESCAPE)return false;dismiss(true);return true;
        }
        @Override protected boolean pointerDown(int x,int y){
            if(!panel.hit(x,y))dismiss(true);return true;
        }
        @Override protected boolean wheel(int x,int y,double delta,boolean horizontal){return true;}
        @Override public void layout(Rect area,Font font){
            if(font==null)font=trigger.getLayoutFont();
            super.layout(area,font);
            Rect anchor=EventGeometry.displayed(trigger);Rect available=area;
            for(Control node=trigger.getParent();node!=null;node=node.getParent())
                if(node instanceof Drawer drawer){available=EventGeometry.intersect(available,drawer.getPanelBounds());break;}
                else if(node instanceof Modal modal){available=EventGeometry.intersect(available,modal.getPanelBounds());break;}
                else if(node instanceof Dialog dialog){available=EventGeometry.intersect(available,dialog.getPanelBounds());break;}
            int width=anchor.width();
            if(font!=null)for(Control child:items.getChildren())width=Math.max(width,child.measureWidth(font,Integer.MAX_VALUE)+2);
            width=Math.min(width,available.width());
            int wanted=2+Math.min(options.size(),trigger.getMaxVisibleItems())*(trigger.getControlSize().getHeight()-1)+1;
            int below=available.bottom()-anchor.bottom(),above=anchor.y()-available.y();
            int height=Math.min(wanted,Math.max(0,Math.max(below,above)));
            int y=below>=wanted||below>=above?anchor.bottom():anchor.y()-height;
            int x=Math.max(available.x(),Math.min(anchor.right()-width,available.right()-width));
            layoutChild(panel,new Rect(x,y,width,height),font);
            scroll.layout(new Rect(1,1,Math.max(0,width-2),Math.max(0,height-2)),font);
        }
        @Override public void draw(ModularGuiContext mui,int x,int y){
            layout(context.getOwner().getBounds(),mui.getFont());
            var g=mui.getGraphics();g.flush();g.pose().pushPose();
            try{g.pose().translate(0,0,6500);super.draw(mui,x,y);g.flush();}finally{g.pose().popPose();}
        }
        private final class Items extends Container {
            @Override public int measureHeight(Font font,int available){return Math.max(0,options.size()*(trigger.getControlSize().getHeight()-1)+1);}
            @Override public void layout(Rect bounds,Font font){
                super.layout(bounds,font);int y=0;
                for(Control child:getChildren()){layoutChild(child,new Rect(0,y,bounds.width(),trigger.getControlSize().getHeight()),font);y+=trigger.getControlSize().getHeight()-1;}
            }
        }
    }
}
