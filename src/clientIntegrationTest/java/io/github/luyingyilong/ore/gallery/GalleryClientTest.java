package io.github.luyingyilong.ore.gallery;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.control.base.Size;
import io.github.luyingyilong.ore.control.form.FieldKey;
import io.github.luyingyilong.ore.control.form.Form;
import io.github.luyingyilong.ore.control.form.FormFields;
import io.github.luyingyilong.ore.control.form.FormValues;
import io.github.luyingyilong.ore.control.input.Button;
import io.github.luyingyilong.ore.control.input.Input;
import io.github.luyingyilong.ore.control.input.InputControl;
import io.github.luyingyilong.ore.control.input.NumberInput;
import io.github.luyingyilong.ore.control.input.Radio;
import io.github.luyingyilong.ore.control.inventory.Slot;
import io.github.luyingyilong.ore.control.layout.Collapse;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import java.math.BigDecimal;
import java.util.*;

/** Opt-in isolated client: real GLFW callbacks, every sample, typed values and repeated reopening. */
@Mod.EventBusSubscriber(modid="geg",value=Dist.CLIENT)
public final class GalleryClientTest {
    private static int ticks,phase,index,at;
    private static boolean radioChecked;
    private static boolean slotChecked;
    private static int collapsePhase;
    private static GalleryView view;
    private static Control previous;
    private static Input input;
    private static Form form;
    private static final FieldKey<String> NAME=new FieldKey<>("name",String.class);
    private static final FieldKey<BigDecimal> AMOUNT=new FieldKey<>("amount",BigDecimal.class);
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(!Boolean.getBoolean("ore.gallery.smoke")||event.phase!=TickEvent.Phase.END||phase==99)return;
        var mc=Minecraft.getInstance();if(mc.player==null)return;
        try{
            if(++ticks>1500)throw new AssertionError("Timeout phase="+phase+" sample="+index);
            if(phase==0){key(GLFW.GLFW_KEY_F9,0);phase=1;}
            else if(phase==1&&OreGallery.getView()!=null&&OreGallery.getView().getWindow().getContext().getMui()!=null){
                view=OreGallery.getView();phase=2;at=ticks;
            }else if(phase==2&&ticks-at>4){
                if(previous!=null && previous!=view.getSample())check(previous.isDisposed(),"Replaced sample freed at render safe point");
                if(index==1)capture();
                if(view.getSelected().equals("slot")&&!slotChecked) {
                    slotThemePadding();slotChecked=true;
                }
                if(view.getSelected().equals("radio")&&!radioChecked) {
                    List<Radio> radios=radios(view.getSample());
                    System.out.println("ORE_RADIO_GEOMETRY "+io.github.luyingyilong.ore.event.EventGeometry.displayed(radios.get(0))+" clip="+io.github.luyingyilong.ore.event.EventGeometry.clip(radios.get(0)));
                    capture();clickControl(radios.get(0));check(radios.get(0).getChecked(),"Real GLFW radio release selects");
                    view.getWindow().getFocusManager().requestFocus(radios.get(3),view.getWindow().getContext().getMui());
                    key(GLFW.GLFW_KEY_SPACE,0);
                    check(radios.get(3).getChecked()&&!radios.get(2).getChecked(),"RadioGroup keyboard activation is exclusive");
                    view.getWindow().getFocusManager().keyboardNavigation();
                    view.getWindow().getFocusManager().requestFocus(radios.get(2),view.getWindow().getContext().getMui());
                    check(view.getWindow().getFocusManager().getOutlineTarget()==radios.get(2),"Radio supplies a focused diamond geometry");
                    radioChecked=true;at=ticks;return;
                }
                if(view.getSelected().equals("radio"))capture();
                if(view.getSelected().equals("collapse")&&collapsePhase<4) {
                    Collapse collapse=find(view.getSample(),Collapse.class);
                    if(collapsePhase==0) {
                        view.getWindow().getFocusManager().requestFocus(collapse.getHeaderButton(),view.getWindow().getContext().getMui());
                        clickControl(collapse.getHeaderButton());check(collapse.getExpanded(),"Actual release opens Collapse");
                        collapsePhase=1;at=ticks;return;
                    }
                    if(ticks-at<9)return;
                    if(collapsePhase==1) {
                        check(!collapse.getAnimating(),"Collapse finishes its height animation");
                        InputControl field=inputs(collapse.getContent()).get(0);
                        view.getWindow().getFocusManager().requestFocus(field,view.getWindow().getContext().getMui());
                        clickControl(field);check(field.getWidget().isFocused(),"Collapse body input receives focus");
                        text("保留草稿");
                        collapse.setExpanded(false);
                        check(!field.getWidget().isFocused()&&!field.isInputAccessible(),"Closing Collapse releases native input immediately");
                        check(view.getWindow().getFocusManager().getFocused()==collapse.getHeaderButton(),"Closing returns focus to header");
                        collapsePhase=2;at=ticks;return;
                    }
                    if(collapsePhase==2) {
                        check(!collapse.getAnimating()&&collapse.getVisibleBodyHeight()==0,"Collapse finishes closing");
                        clickControl(collapse.getHeaderButton());collapsePhase=3;at=ticks;return;
                    }
                    check(inputs(collapse.getContent()).get(0).getWidget().getText().equals("保留草稿"),"Reopening retains the input document");
                    capture();collapsePhase=4;
                }
                if(index<GalleryCatalog.getEntries().size()){
                    previous=view.getSample();String id=GalleryCatalog.getEntries().get(index++).id();
                    view.select(id);view.setPreviewWidth(127+(index%3));at=ticks;
                    System.out.println("ORE_GALLERY_SAMPLE "+id);
                }else{view.select("input");phase=3;at=ticks;}
            }else if(phase==3&&ticks-at>5){
                input=inputs(view.getSample()).get(0).getWidget();click(inputs(view.getSample()).get(0));
                Control owner=input.getThemeControl();
                try { owner.setRenderTransform(io.github.luyingyilong.ore.render.Transform2D.rotation(90));throw new AssertionError("Rotated MUI input accepted"); }
                catch(IllegalArgumentException expected) { }
                check(owner.getRenderTransform().equals(io.github.luyingyilong.ore.render.Transform2D.IDENTITY),"Rejected MUI transform preserves caret geometry");
                int connections=input.textChanged().connectionCount();
                try { FormFields.text(input); throw new AssertionError("Double wrapping accepted"); }
                catch (IllegalArgumentException expected) { }
                check(input.getThemeControl()==owner && input.textChanged().connectionCount()==connections,"Rejected adapter preserves existing input ownership and subscriptions");
                owner.setRenderTransform(new io.github.luyingyilong.ore.render.Transform2D(5,3,0,1.25,0.5,0.5));
                clickControl(owner);check(input.isFocused(),"Translated/scaled MUI input uses the Window hit geometry");
                owner.setRenderTransform(io.github.luyingyilong.ore.render.Transform2D.IDENTITY);
                text("中文Abc");check(input.getText().equals("中文Abc"),"Input accepts Unicode exactly once");
                key(GLFW.GLFW_KEY_A,GLFW.GLFW_MOD_CONTROL);key(GLFW.GLFW_KEY_C,GLFW.GLFW_MOD_CONTROL);
                key(GLFW.GLFW_KEY_X,GLFW.GLFW_MOD_CONTROL);check(input.getText().isEmpty(),"Cut");
                key(GLFW.GLFW_KEY_Z,GLFW.GLFW_MOD_CONTROL);check(input.getText().equals("中文Abc"),"Undo cut");
                key(GLFW.GLFW_KEY_Y,GLFW.GLFW_MOD_CONTROL);check(input.getText().isEmpty(),"Redo cut");
                key(GLFW.GLFW_KEY_V,GLFW.GLFW_MOD_CONTROL);check(input.getText().equals("中文Abc"),"Paste");
                key(GLFW.GLFW_KEY_ESCAPE,0);check(!input.isFocused()&&view.getWindow().isAlive(),"Esc first blurs");
                view.select("textarea");phase=4;at=ticks;
            }else if(phase==4&&ticks-at>5){
                input=inputs(view.getSample()).get(0).getWidget();click(inputs(view.getSample()).get(0));
                mc.keyboardHandler.setClipboard("中文第一行\nSecond line\n".repeat(4));key(GLFW.GLFW_KEY_V,GLFW.GLFW_MOD_CONTROL);
                check(input.getText().contains("\nSecond line"),"Multiline paste");
                key(GLFW.GLFW_KEY_A,GLFW.GLFW_MOD_CONTROL);key(GLFW.GLFW_KEY_X,GLFW.GLFW_MOD_CONTROL);
                key(GLFW.GLFW_KEY_Z,GLFW.GLFW_MOD_CONTROL);check(input.getText().contains("\nSecond line"),"Multiline undo");
                view.select("form");phase=5;at=ticks;
            }else if(phase==5&&ticks-at>5){
                capture();
                form=find(view.getSample(),Form.class);
                InputControl number=inputs(view.getSample()).stream().filter(node->node.getWidget() instanceof NumberInput).findFirst().orElseThrow();
                input=number.getWidget();click(number);key(GLFW.GLFW_KEY_A,GLFW.GLFW_MOD_CONTROL);text("2+3");
                check(input.getText().equals("2+3"),"Numeric draft preserved during live validation");
                var result=form.validate();
                check(!result.getValid()&&input.getText().equals("5.00"),"All fields prepare even when earlier text fails");
                form.setValuesNoSignal(FormValues.builder().setValue(NAME,"Ore").build());
                result=form.validate();
                check(result.getValid()&&result.getValues().getValue(AMOUNT).compareTo(new BigDecimal("5"))==0,"Typed successful submit");
                check(form.getDirty(),"Form tracks edit");
                form.reset();check(!form.getDirty()&&!form.getTouched(),"Form resets baseline");
                form.setValuesNoSignal(FormValues.builder().setValue(NAME,"Ore").build());
                Control validate = named(view.getSample(), "Gallery action validate");
                check(view.getContentScroll().getMaxScrollY()>0,"Long samples have their own vertical scrolling");
                view.getWindow().getFocusManager().requestFocus(validate,view.getWindow().getContext().getMui());
                clickControl(validate);
                check(form.validate().getValid(),"Scrolled submit button and form remain interactive");
                key(GLFW.GLFW_KEY_F8,0);phase=6;at=ticks;
            }else if(phase==6&&ticks-at>30){
                check(io.github.luyingyilong.ore.debug.DebugBridge.active(),"F8 Inspector checks gallery");
                io.github.luyingyilong.ore.inspector.OreInspector.stop();
                view.getWindow().close();phase=7;at=ticks;
            }else if(phase==7&&ticks-at>4){
                key(GLFW.GLFW_KEY_F9,0);phase=8;at=ticks;
            }else if(phase==8&&ticks-at>8){
                check(OreGallery.getView()!=null&&OreGallery.getView()!=view,"F9 reopens after closing");
                OreGallery.stop();phase=99;
                System.out.println("ORE_GALLERY_CLIENT_PASS");mc.stop();
            }
        }catch(Throwable error){capture();phase=99;error.printStackTrace();System.out.println("ORE_GALLERY_CLIENT_FAIL");mc.stop();}
    }
    private static void slotThemePadding() {
        // Slot needs the real item registry; this belongs in the client fixture, not the headless suite.
        Slot slot=new Slot();var font=Minecraft.getInstance().font;
        Button small=new Button(net.minecraft.network.chat.Component.literal("Small")).setSize(Button.Size.SMALL);
        try {
            check(small.measureHeight(font,200)==Button.Size.SMALL.getHeight(),"Real font leading preserves small Button height");
            check(slot.measureSize(font,new Size(200,100)).equals(new Size(18,18)),"Slot keeps its default footprint");
            slot.setThemeOverride(Slot.PADDING_LEFT,10).setThemeOverride(Slot.PADDING_RIGHT,8)
                    .setThemeOverride(Slot.PADDING_TOP,6).setThemeOverride(Slot.PADDING_BOTTOM,9);
            check(slot.measureSize(font,new Size(200,100)).equals(new Size(34,31)),"Slot measures themed item padding");
            slot.setThemeOverride(Slot.COUNT_PADDING_LEFT,3).setThemeOverride(Slot.COUNT_PADDING_RIGHT,4)
                    .setThemeOverride(Slot.COUNT_PADDING_TOP,8).setThemeOverride(Slot.COUNT_PADDING_BOTTOM,2);
            slot.layout(new Rect(0,0,40,40),font);
            check(slot.getChild(0).getBounds().equals(new Rect(3,8,33,30)),"Slot count uses its own theme area");
            System.out.println("ORE_SLOT_THEME_PADDING_CLIENT_PASS");
        } finally {slot.queueFree();small.queueFree();}
    }
    private static Control named(Control root,String name){if(root.getDebugName().equals(name))return root;for(Control child:root.getChildren()){Control result=named(child,name);if(result!=null)return result;}return null;}
    private static void clickControl(Control control){
        var area=io.github.luyingyilong.ore.event.EventGeometry.displayed(control);var mc=Minecraft.getInstance();
        var clip=io.github.luyingyilong.ore.event.EventGeometry.clip(control);
        if(clip!=null)area=io.github.luyingyilong.ore.event.EventGeometry.intersect(area,clip);
        check(area.width()>0&&area.height()>0,"Pointer target must be visible");
        var target=OreGallery.getView().getWindow().getPointerTarget(area.x()+area.width()/2,area.y()+area.height()/2);
        System.out.println("ORE_GALLERY_POINTER "+control.getClass().getSimpleName()+" "+area+" target="+(target==null?"null":target.getClass().getSimpleName()));
        double x=(double)(area.x()+area.width()/2)*mc.getWindow().getScreenWidth()/mc.screen.width;
        double y=(double)(area.y()+area.height()/2)*mc.getWindow().getScreenHeight()/mc.screen.height;
        var move=GLFW.glfwSetCursorPosCallback(handle(),null);GLFW.glfwSetCursorPosCallback(handle(),move);GLFW.glfwSetCursorPos(handle(),x,y);move.invoke(handle(),x,y);
        var mouse=GLFW.glfwSetMouseButtonCallback(handle(),null);GLFW.glfwSetMouseButtonCallback(handle(),mouse);mouse.invoke(handle(),0,GLFW.GLFW_PRESS,0);mouse.invoke(handle(),0,GLFW.GLFW_RELEASE,0);
    }
    private static List<Radio> radios(Control root){List<Radio> out=new ArrayList<>();if(root instanceof Radio radio)out.add(radio);for(Control child:root.getChildren())out.addAll(radios(child));return out;}
    private static List<InputControl> inputs(Control root){List<InputControl> out=new ArrayList<>();if(root instanceof InputControl input)out.add(input);for(Control child:root.getChildren())out.addAll(inputs(child));return out;}
    private static <T> T find(Control root,Class<T> type){if(type.isInstance(root))return type.cast(root);for(Control child:root.getChildren()){T result=find(child,type);if(result!=null)return result;}return null;}
    private static void click(InputControl control){
        var area=control.getWidgetBounds();var mc=Minecraft.getInstance();
        double x=(double)(area.x()+4)*mc.getWindow().getScreenWidth()/mc.screen.width;
        double y=(double)(area.y()+area.height()/2)*mc.getWindow().getScreenHeight()/mc.screen.height;
        var move=GLFW.glfwSetCursorPosCallback(handle(),null);GLFW.glfwSetCursorPosCallback(handle(),move);GLFW.glfwSetCursorPos(handle(),x,y);move.invoke(handle(),x,y);
        var mouse=GLFW.glfwSetMouseButtonCallback(handle(),null);GLFW.glfwSetMouseButtonCallback(handle(),mouse);
        mouse.invoke(handle(),0,GLFW.GLFW_PRESS,0);mouse.invoke(handle(),0,GLFW.GLFW_RELEASE,0);
        check(control.getWidget().isFocused(),"Actual pointer focuses editor");
    }
    private static long handle(){return Minecraft.getInstance().getWindow().getWindow();}
    private static void capture(){java.io.File directory=new java.io.File("build/tmp/gallery-captures");directory.mkdirs();net.minecraft.client.Screenshot.grab(directory,Minecraft.getInstance().getMainRenderTarget(),message->System.out.println("ORE_GALLERY_CAPTURE "+message.getString()));}
    private static void key(int code,int modifiers){var cb=GLFW.glfwSetKeyCallback(handle(),null);GLFW.glfwSetKeyCallback(handle(),cb);cb.invoke(handle(),code,0,GLFW.GLFW_PRESS,modifiers);cb.invoke(handle(),code,0,GLFW.GLFW_RELEASE,modifiers);}
    private static void text(String value){var cb=GLFW.glfwSetCharModsCallback(handle(),null);GLFW.glfwSetCharModsCallback(handle(),cb);value.codePoints().forEach(cp->cb.invoke(handle(),cp,0));}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}

