package io.github.luyingyilong.ore.menu;

import io.github.luyingyilong.ore.Slot;
import io.github.luyingyilong.ore.Inventory;
import io.github.luyingyilong.ore.event.*;
import io.github.luyingyilong.ore.inventory.*;
import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.window.HostInputHandler;
import net.minecraft.world.inventory.AbstractContainerMenu;
import java.util.*;
import java.util.function.BooleanSupplier;

/** One Menu, one gesture state, any number of slot views. Does not own the Menu or its storage. */
public final class MenuSession implements HostInputHandler,AutoCloseable {
    private final Window window;
    private final AbstractContainerMenu menu;
    private final MenuInteraction interaction;
    private final BooleanSupplier valid;
    private final MenuInventorySource source;
    private final Map<Integer,Slot> views=new LinkedHashMap<>();
    private Slot gestureOwner;
    private boolean gesture,closed,cancelling;
    private final Set<Integer> buttons=new HashSet<>();

    public MenuSession(Window window,AbstractContainerMenu menu,MenuInteraction interaction,BooleanSupplier valid){
        this.window=Objects.requireNonNull(window);this.menu=Objects.requireNonNull(menu);
        window.getSignalScope().checkThread();
        if(!window.isAlive()||window.getHostInputHandler()!=null)throw new IllegalStateException("Window already has an input session or is ending");
        this.interaction=Objects.requireNonNull(interaction);this.valid=Objects.requireNonNull(valid);
        source=new MenuInventorySource(this);
        window.setHostInputHandler(this);window.getSignalScope().own(this);
    }
    public Window getWindow(){return window;}
    public AbstractContainerMenu getMenu(){return menu;}
    public MenuInventorySource getSource(){return source;}
    public boolean isValid(){return !closed&&window.isAlive()&&!source.isClosed()&&valid.getAsBoolean();}
    public SlotBinding getBinding(int slot){return source.getBinding(slot);}
    public void validateView(Slot view,int slot){
        if(closed)throw new IllegalStateException("Menu session ended");
        Objects.checkIndex(slot,menu.slots.size());
        Slot previous=views.get(slot);
        if(previous!=null&&previous!=view&&previous.isAlive())throw new IllegalArgumentException("Menu slot already has an interactive view: "+slot);
    }
    public void validateViews(Collection<Slot> proposed){for(Slot view:proposed){
        if(view.getBinding()==null||view.getBinding().source()!=source)throw new IllegalArgumentException("Binding belongs to another source");
        validateView(view,view.getBinding().slot());
    }}
    public void register(Slot view){
        if(view.getBinding()==null||view.getBinding().source()!=source)throw new IllegalArgumentException("Binding belongs to another Menu session");
        validateView(view,view.getBinding().slot());views.put(view.getBinding().slot(),view);
    }
    public void unregister(Slot view){views.values().removeIf(value->value==view);cancelFrom(view);}
    private boolean available(Slot view){
        return isValid()&&view!=null&&view.isAlive()&&view.isInputAccessible()&&view.getMode()==Inventory.Mode.CONTAINER
                &&view.getBinding()!=null&&view.getBinding().source()==source&&!window.isSuspended()
                &&views.get(view.getBinding().slot())==view
                &&view.getWindowContext().getOwner()==window&&window.getFocusManager().allowsNativeInput(view)
                &&menu.getSlot(view.getBinding().slot()).isActive();
    }
    public Slot getSlotAt(int x,int y){
        Control target=window.getPointerTarget(x,y);
        for(Control node=target;node!=null;node=node.getParent())if(node instanceof Slot view&&available(view))return view;
        return null;
    }
    public net.minecraft.world.inventory.Slot getNativeSlotAt(double x,double y){
        Slot view=getSlotAt((int)Math.floor(x),(int)Math.floor(y));
        return view==null?null:menu.getSlot(view.getBinding().slot());
    }
    public net.minecraft.world.inventory.Slot getKeyboardSlot(){
        var context=window.getContext();Slot view=getSlotAt(context.getMouseX(),context.getMouseY());
        if(view==null&&window.getFocusManager().getFocused() instanceof Slot focused&&available(focused))view=focused;
        return view==null?null:menu.getSlot(view.getBinding().slot());
    }
    public boolean input(Slot view,InputEvent event){
        if(event instanceof CancelEvent){cancelFrom(view);return false;}
        if(!available(view))return false;
        if(event instanceof PointerEvent pointer){
            if(pointer.action()==PointerEvent.Action.PRESS){gesture=true;gestureOwner=view;buttons.add(pointer.button());}
            if(pointer.action()==PointerEvent.Action.RELEASE&&!buttons.remove(pointer.button()))return false;
            if(pointer.action()!=PointerEvent.Action.PRESS&&!gesture&&pointer.action()!=PointerEvent.Action.MOVE)return false;
            boolean handled=interaction.pointer(pointer);
            if(pointer.action()==PointerEvent.Action.RELEASE){gesture=!buttons.isEmpty();gestureOwner=null;}
            return handled;
        }
        return false;
    }
    @Override public boolean input(InputEvent event){
        if(!isValid()){cancel();return false;}
        if(event instanceof KeyEvent key){
            if(window.getFocusManager().getTextInputFocused()||window.getFocusManager().getInputScope()!=null)return false;
            return interaction.key(key);
        }
        if(event instanceof PointerEvent pointer){
            if(window.getFocusManager().getInputScope()!=null||window.getContext().getPopups().open())return false;
            // A Slot branch already offered this event while bubbling; never offer it twice.
            for(Control node=event.target();node!=null;node=node.getParent())
                if(node instanceof Slot view&&view.getMode()==Inventory.Mode.CONTAINER&&view.getMenuSession()==this)return false;
            if(pointer.action()==PointerEvent.Action.PRESS&&!interaction.ownsPointer(pointer.windowX(),pointer.windowY()))return false;
            if(pointer.action()==PointerEvent.Action.PRESS){gesture=true;gestureOwner=null;buttons.add(pointer.button());}
            if(pointer.action()==PointerEvent.Action.RELEASE&&!buttons.remove(pointer.button()))return false;
            boolean handled=interaction.pointer(pointer);
            if(pointer.action()==PointerEvent.Action.RELEASE){gesture=!buttons.isEmpty();gestureOwner=null;}
            return handled;
        }
        return false;
    }
    public boolean activate(Slot view){if(!available(view))return false;interaction.activate(view.getBinding().slot());source.refresh();return true;}
    public void update(){
        if(!isValid()){cancel();return;}
        if(gestureOwner!=null&&!available(gestureOwner))cancel();
        source.refresh();
    }
    public void cancelFrom(Slot view){if(gestureOwner==view)cancel();}
    @Override public void cancel(){
        if(cancelling)return;cancelling=true;
        try{gesture=false;gestureOwner=null;buttons.clear();interaction.cancel();}finally{cancelling=false;}
    }
    public void renderSlot(Slot view,DrawContext context,int x,int y){if(isValid()&&view.isAlive())interaction.renderSlot(view,context,x,y);}
    @Override public void close(){
        if(closed)return;cancel();closed=true;views.clear();source.close();
        if(window.getHostInputHandler()==this)window.setHostInputHandler(null);
    }
}
