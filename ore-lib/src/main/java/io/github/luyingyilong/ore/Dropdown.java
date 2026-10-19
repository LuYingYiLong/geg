package io.github.luyingyilong.ore;

import brachy.modularui.screen.viewport.ModularGuiContext;
import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.theme.OreIcons;
import io.github.luyingyilong.ore.theme.OreSounds;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.util.function.Consumer;

/** Retained trigger. The owning Window creates and routes its Overlay menu. */
public final class Dropdown<T> extends Control {
    public static final int ITEM_PADDING=4, PANEL_PADDING=1, ITEM_OVERLAP=1;
    private static final Button.Icon ARROW=new Button.Icon(OreIcons.ARROW_DOWN,8,8);
    private final Button triggerButton;
    private final Button.Size size;
    private final int maxVisibleItems;
    private List<Option<T>> options=List.of();
    private T selected;
    private Component triggerLabel;
    private boolean checkable=true,showArrow=true,open,held;
    private int activationKey=-1;
    private Consumer<T> onSelect=value->{};
    public Dropdown(T initialValue,int maxVisibleItems){this(initialValue,maxVisibleItems,Button.Size.MEDIUM);}
    public Dropdown(T initialValue,int maxVisibleItems,Button.Size size){
        selected=initialValue;this.maxVisibleItems=Math.max(1,maxVisibleItems);this.size=Objects.requireNonNull(size);
        triggerButton=new Button().size(size).alignment(Button.TextAlignment.LEFT).trailingIcon(ARROW);
        minimum(0,size.height());focusable(true);focusExpansion(1);
    }
    public Dropdown<T> onSelect(Consumer<T> action){onSelect=Objects.requireNonNull(action);return this;}
    public Dropdown<T> triggerLabel(Component label){triggerLabel=Objects.requireNonNull(label);requestLayout();return this;}
    public Dropdown<T> icon(Button.Icon icon){triggerButton.icon(icon);requestLayout();return this;}
    public Dropdown<T> icon(ResourceLocation texture,int width,int height){return icon(new Button.Icon(texture,width,height));}
    public Button.Icon icon(){return triggerButton.icon();}
    public Dropdown<T> type(Button.Type type){triggerButton.type(type);return this;}
    public Button.Type type(){return triggerButton.type();}
    public Dropdown<T> showArrow(boolean value){showArrow=value;triggerButton.trailingIcon(value?ARROW:null);requestLayout();return this;}
    public boolean showArrow(){return showArrow;}
    public Dropdown<T> checkable(boolean value){checkable=value;return this;}
    public boolean checkable(){return checkable;}
    public Button.Size controlSize(){return size;}
    public int maxVisibleItems(){return maxVisibleItems;}
    public List<Option<T>> options(){return options;}
    public T selected(){return selected;}
    public boolean isOpen(){return open;}
    public void setOptions(List<Option<T>> values){
        List<Option<T>> next=List.copyOf(values);
        if(!options.equals(next)){close();options=next;requestLayout();}
        if(options.stream().noneMatch(option->Objects.equals(option.value(),selected)))selected=options.isEmpty()?null:options.get(0).value();
    }
    public void select(T value){
        close();
        if(options.stream().noneMatch(option->Objects.equals(option.value(),value)))return;
        boolean changed=!Objects.equals(selected,value);selected=value;
        if(changed)requestLayout();
        if(changed||!checkable)onSelect.accept(value);
    }
    public void close(){open=false;cancelInteraction();}
    @Override public void cancelInteraction(){held=false;activationKey=-1;}
    @Override public void onFocusChanged(boolean focused){if(!focused)cancelInteraction();}
    @Override public boolean acceptsFocus(){return super.acceptsFocus()&&!options.isEmpty();}
    private Component text(){return triggerLabel!=null?triggerLabel:options.stream().filter(option->Objects.equals(option.value(),selected)).findFirst().map(Option::label).orElse(Component.empty());}
    @Override public int measureWidth(Font font,int available){triggerButton.label(text());return Math.max(super.measureWidth(font,available),triggerButton.measureWidth(font,available));}
    @Override public int measureHeight(Font font,int available){return Math.max(super.measureHeight(font,available),triggerButton.measureHeight(font,available));}
    @Override protected void render(ModularGuiContext context,int x,int y){
        Rect area=screenBounds(x,y);triggerButton.label(text());
        DebugBridge.backend().internal(this,"trigger",triggerButton);
        boolean hovered=area.contains(context.getAbsMouseX(),context.getAbsMouseY());
        triggerButton.drawPresentation(context,area,hovered,activationKey!=-1||held&&hovered);
    }
    @Override protected boolean pointerDown(int x,int y){if(!hit(x,y)||options.isEmpty())return false;held=true;return true;}
    @Override protected boolean pointerUp(int x,int y,boolean over){boolean pending=held;held=false;if(pending&&over)activate();return pending;}
    @Override public boolean activate(){
        if(!visible()||options.isEmpty())return false;
        OreSounds.play(OreSounds.Cue.CLICK);open=!open;return true;
    }
    @Override public boolean onActivationPressed(int key){if(!acceptsFocus())return false;if(activationKey==-1)activationKey=key;return true;}
    @Override public boolean onActivationReleased(int key){if(activationKey!=key)return false;activationKey=-1;return activate();}
    public record Option<T>(T value,Component label){}
}
