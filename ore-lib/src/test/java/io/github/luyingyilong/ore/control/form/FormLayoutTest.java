package io.github.luyingyilong.ore.control.form;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.control.base.Size;
import io.github.luyingyilong.ore.control.form.validation.Validators;
import io.github.luyingyilong.ore.signal.SignalSource;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.*;
import net.minecraft.util.FormattedCharSequence;

public final class FormLayoutTest {
    public static void main(String[] args) {
        Font font=new Font(id->null,false){
            @Override public java.util.List<FormattedCharSequence> split(FormattedText text,int width){
                java.util.List<FormattedCharSequence> lines=new java.util.ArrayList<>();
                int[] points=text.getString().codePoints().toArray();int count=Math.max(1,width/6);
                for(int from=0;from<points.length;from+=count)
                    lines.add(Component.literal(new String(points,from,Math.min(count,points.length-from))).getVisualOrderText());
                return lines.isEmpty()?java.util.List.of(FormattedCharSequence.EMPTY):lines;
            }
            @Override public int width(String text){return text.codePointCount(0,text.length())*6;}
            @Override public int width(FormattedText text){return width(text.getString());}
            @Override public int width(FormattedCharSequence text){int[] n={0};text.accept((i,s,c)->{n[0]++;return true;});return n[0]*6;}
        };
        class Field extends Control{
            String value="";
            final SignalSource<String> changed=new SignalSource<>(this,"changed","String");
        }
        Field field=new Field();field.setMinimum(40,22).setFocusable(true);
        FormItem<String> item=new FormItem<>(new FieldKey<>("value",String.class),Component.literal("Field 标签"),
                FormFields.custom(String.class,field,()->field.value,value->field.value=value,field.changed.signal()))
                .addValidator(Validators.predicate(value->!value.isEmpty(),Component.literal("Error 错误提示 needs several lines in a narrow view")));
        Form form=new Form().setLayout(Form.Layout.HORIZONTAL).addItem(item);form.setVisible(true);
        for(int width:new int[]{127,128,129,360}){
            int clean=form.measureSize(font,new Size(width,240)).height();
            form.layout(new Rect(0,0,width,clean),font);
            check(width<=128?field.getBounds().y()>0:field.getBounds().x()>0,"Responsive field layout "+width);
            form.validate();
            int invalid=form.measureSize(font,new Size(width,240)).height();
            form.layout(new Rect(0,0,width,invalid),font);
            Control error=item.getChild(2);
            check(invalid>clean && error.getBounds().height()>=10,"Error contributes measured height");
            check(error.getBounds().y()>=field.getBounds().bottom(),"Error never overlaps field");
            check(error.getBounds().bottom()<=item.getBounds().height(),"Full error remains inside item");
            field.value="valid";field.changed.emit(field.value);
            check(item.getError()==null,"Correction clears error without submission");
            item.captureInitialValue();field.value="";
        }
        System.out.println("ORE_FORM_LAYOUT_PASS");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}

