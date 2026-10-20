package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.event.*;
import io.github.luyingyilong.ore.form.*;
import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.theme.*;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.*;

public final class RadioTest {
    public static void main(String[] args) {
        Window window=Window.embedded();Control content=new Control();window.setContent(content);
        Radio first=new Radio(),second=new Radio(),third=new Radio();
        first.setPosition(10,10).setRequestedSize(20,18);
        second.setPosition(40,10).setRequestedSize(20,18);
        third.setPosition(70,10).setRequestedSize(20,18);
        content.addChild(first);content.addChild(second);content.addChild(third);
        RadioGroup<String> group=new RadioGroup<>(content,String.class);
        group.addRadio("a",first).addRadio("b",second).addRadio("c",third);
        List<String> events=new ArrayList<>();
        first.toggled().connect(content,value->{check(first.getChecked()==value && (!value||Objects.equals(group.getSelected(),"a")),"state published before callbacks");events.add("a="+value);});
        second.toggled().connect(content,value->events.add("b="+value));
        group.selectionChanged().connect(content,value->events.add("group="+value));
        int[] activated={0};first.activated().connect(content,()->activated[0]++);
        window.layoutViewport(100,60,null);
        check(first.getIconRotationDegrees()==45&&first.measureWidth(null,100)==12,"theme angle and rotated footprint");
        var geometry=first.getFocusGeometry();
        Rect local=first.getThemeItem(OreTheme.FOCUS).getDrawRect(geometry.getLocalBounds());
        check(local.width()==10&&local.height()==10,"local focus expands before rotation");
        check(geometry.getLocalToWindow().bounds(local).width()>10,"diamond focus envelope");
        press(window,15,15);check(!first.getChecked(),"press does not select");release(window,15,15);
        check(events.equals(List.of("a=true","group=a"))&&activated[0]==1,"release signal order");
        press(window,15,15);release(window,15,15);check(events.size()==2&&activated[0]==2,"repeat activation does not toggle off");
        press(window,45,15);release(window,99,59);check(group.getSelected().equals("a"),"drag-out cancels");
        group.setSelected("b");check(events.subList(2,5).equals(List.of("a=false","b=true","group=b")),"exclusive change order");
        int count=events.size();group.setSelectedNoSignal("a");
        check(events.size()==count&&first.getChecked()&&!second.getChecked(),"silent change updates the whole group");
        window.getFocusManager().requestFocus(second,null);
        window.dispatchInput(new KeyEvent(window.getContext(),KeyEvent.Action.PRESS,GLFW.GLFW_KEY_SPACE,0,0));
        check(group.getSelected().equals("a"),"keyboard waits for release");
        window.dispatchInput(new KeyEvent(window.getContext(),KeyEvent.Action.REPEAT,GLFW.GLFW_KEY_SPACE,0,0));
        window.dispatchInput(new KeyEvent(window.getContext(),KeyEvent.Action.RELEASE,GLFW.GLFW_KEY_SPACE,0,0));
        check(group.getSelected().equals("b"),"keyboard selects once on release");
        second.setDisabled(true);check(second.getChecked()&&!second.acceptsFocus(),"disabled preserves selection");
        first.setThemeOverride(Radio.ICON_ROTATION_DEGREES,0F);
        check(first.measureWidth(null,100)==8&&first.getFocusGeometry().getVisibleBounds(first).width()==10,"local theme angle changes measurement and focus");
        first.setThemeOverride(Radio.ICON_ROTATION_DEGREES,90F);check(first.measureWidth(null,100)==8,"quarter turn footprint");
        first.setThemeOverride(Radio.ICON_ROTATION_DEGREES,30F);first.getFocusGeometry();
        Rect imageBounds=EventGeometry.displayed(first.getIconControl());
        check(imageBounds.width()==11&&imageBounds.x()==first.getAbsoluteBounds().x(),
                "odd rotated footprint stays centered within its measured slot");
        first.removeThemeOverride(Radio.ICON_ROTATION_DEGREES);check(first.getIconRotationDegrees()==45,"theme restore");
        boolean[] reenter={true};
        var connection=first.toggled().connect(content,value->{if(value&&reenter[0]){reenter[0]=false;group.setSelected("c");}});
        group.setSelected("a");check(group.getSelected().equals("c")&&!first.getChecked()&&third.getChecked(),"reentrant selection wins");
        connection.disconnect();
        group.setSelectedNoSignal("a");content.removeChild(first);check(group.getSelected().equals("a"),"detach preserves membership");
        first.queueFree();LifecycleScheduler.flush();check(group.getSelected()==null&&group.getRadios().size()==2,"permanent free removes membership");
        group.close();check(second.isAlive()&&third.isAlive()&&group.getRadios().isEmpty(),"group close does not destroy controls");
        window.queueFree();LifecycleScheduler.flush();
        typedForm();
        checkedMembership();
        System.out.println("ORE_RADIO_PASS");
    }
    private static void checkedMembership(){
        Control owner=new Control();Radio first=new Radio(),second=new Radio();
        first.setChecked(true);second.setChecked(true);
        List<String> events=new ArrayList<>();
        first.toggled().connect(owner,value->events.add("a="+value));
        second.toggled().connect(owner,value->events.add("b="+value));
        RadioGroup<String> group=new RadioGroup<>(owner,String.class);
        group.selectionChanged().connect(owner,value->events.add("group="+value));
        group.addRadio("a",first);
        check(events.equals(List.of("group=a")),"joining checked member does not repeat toggled");
        events.clear();group.addRadio("b",second);
        check(events.equals(List.of("a=false","group=b"))&&!first.getChecked()&&second.getChecked(),
                "joining checked member clears previous selection without duplicate notification");
        owner.queueFree();first.queueFree();second.queueFree();LifecycleScheduler.flush();
    }
    private static void typedForm(){
        Box choices=new Box();Radio a=new Radio(),b=new Radio();choices.addChild(a);choices.addChild(b);
        RadioGroup<String> group=new RadioGroup<>(choices,String.class);group.addRadio("a",a).addRadio("b",b);
        FieldKey<String> key=new FieldKey<>("radio",String.class);
        Form form=new Form().addItem(new FormItem<>(key,Component.empty(),FormFields.radioGroup(String.class,choices,group)).addValidator(Validators.required()));
        form.setVisible(true);form.captureInitialValues();
        check(!form.validate().getValid(),"empty group fails required validation");
        group.setSelected("b");check(form.getDirty(),"group tracks dirty");
        check(form.validate().getValues().getValue(key).equals("b"),"typed values");
        form.reset();check(group.getSelected()==null&&!form.getDirty()&&!form.getTouched(),"reset clears silently");
        form.queueFree();LifecycleScheduler.flush();check(group.getRadios().isEmpty(),"owner cleanup detaches group");
    }
    private static void press(Window w,int x,int y){w.dispatchInput(new PointerEvent(w.getContext(),PointerEvent.Action.PRESS,x,y,0,0));}
    private static void release(Window w,int x,int y){w.dispatchInput(new PointerEvent(w.getContext(),PointerEvent.Action.RELEASE,x,y,0,0));}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
