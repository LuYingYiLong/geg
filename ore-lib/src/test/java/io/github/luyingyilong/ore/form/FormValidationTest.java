package io.github.luyingyilong.ore.form;

import io.github.luyingyilong.ore.*;
import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.signal.*;
import net.minecraft.network.chat.Component;
import java.math.BigDecimal;
import java.util.*;

public final class FormValidationTest {
    public static void main(String[] args) {
        FieldKey<String> name=new FieldKey<>("name",String.class);
        FieldKey<Boolean> checked=new FieldKey<>("checked",Boolean.class);
        class Editor extends Control {
            String text="";
            final SignalSource<String> changed=new SignalSource<>(this,"changed","String");
            void edit(String value){text=value;changed.emit(value);}
        }
        Editor editor=new Editor();
        FormItem<String> item=new FormItem<>(name,Component.literal("Name"),
                FormFields.custom(String.class,editor,()->editor.text,v->editor.text=v,editor.changed.signal()))
                .addValidator(Validators.required()).addValidator(Validators.length(1,3));
        Checkbox check=new Checkbox(Component.literal("Enabled"));
        FormItem<Boolean> booleanItem=new FormItem<>(checked,Component.empty(),FormFields.checkbox(check));
        Form form=new Form().addItem(item).addItem(booleanItem);
        form.setVisible(true);
        check(item.getError()==null && !form.getDirty() && !form.getTouched(),"Untouched does not show validation");
        editor.edit("long"); check(item.getError()==null && form.getDirty(),"Live edits stay quiet until blur");
        editor.notifyFocusChanged(true);editor.notifyFocusChanged(false);
        check(item.getTouched() && item.getError()!=null,"Blur validates");
        editor.edit("中😀"); check(item.getError()==null,"Unicode count and correction");
        FormValidation valid=form.validate();
        check(valid.getValid() && valid.getValues().getValue(name).equals("中😀") && !valid.getValues().getValue(checked),"Typed immutable values");
        editor.edit(""); FormValidation invalid=form.validate();
        check(!invalid.getValid() && invalid.getErrors().containsKey("name"),"Reject invalid form");
        rejects(IllegalStateException.class,invalid::getValues);
        check(valid.getValues().getValue(name).equals("中😀"),"Previous snapshot is immutable");
        rejects(IllegalArgumentException.class,()->form.getValue(new FieldKey<>("name",Integer.class)));
        FormItem<String> duplicate=new FormItem<>(name,Component.empty(),FormFields.readOnly(String.class,new Control(),()->"x"));
        rejects(IllegalArgumentException.class,()->form.addItem(duplicate));
        check(duplicate.getParent()==null && form.getChildCount()==2,"Duplicate rejection is atomic");
        form.setInitialValues(FormValues.builder().setValue(name,"abc").setValue(checked,true).build());
        check(!form.getDirty() && !form.getTouched() && check.getChecked(),"Initial values are silent and pristine");
        editor.edit("z"); check.setChecked(false); form.reset();
        check(editor.text.equals("abc") && check.getChecked() && !form.getTouched() && !form.getDirty(),"Reset");
        rejects(IllegalArgumentException.class,()->form.setValuesNoSignal(FormValues.builder().setValue(name,"bad")
                .setValue(new FieldKey<>("unknown",String.class),"x").build()));
        check(editor.text.equals("abc"),"Invalid batch leaves existing values untouched");
        editor.edit(""); check.setChecked(false);
        booleanItem.addValidator(Validators.predicate(Boolean::booleanValue,Component.literal("Must be enabled")));
        FormValidation both=form.validate();
        check(new ArrayList<>(both.getErrors().keySet()).equals(List.of("name","checked")),"Validate all in order");
        item.setActive(false); booleanItem.setVisible(false);
        check(form.validate().getValues().getKeys().isEmpty(),"Inactive and hidden values excluded");
        item.setActive(true);editor.edit("ok");booleanItem.setVisible(true);check.setChecked(true);
        int connections=editor.changed.signal().connectionCount();
        for(int i=0;i<20;i++){form.validate();form.captureInitialValues();}
        check(editor.changed.signal().connectionCount()==connections,"Validation and refill never resubscribe");
        item.queueFree();LifecycleScheduler.flush();
        check(editor.changed.signal().connectionCount()==0,"Field and source cleanup");
        check(!form.validate().getValues().getKeys().contains(name),"Removed fields are not submitted");
        Validators.range(BigDecimal.ZERO,BigDecimal.TEN).validate(new BigDecimal("11")).orElseThrow();
        System.out.println("ORE_TYPED_FORM_PASS");
    }
    private static void rejects(Class<? extends Exception> type,Runnable run){
        try{run.run();}catch(Exception e){if(type.isInstance(e))return;throw e;}throw new AssertionError("Expected "+type);
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}

