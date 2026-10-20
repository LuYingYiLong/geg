package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.theme.*;
import net.minecraft.resources.ResourceLocation;

public final class ButtonThemeVariationTest {
    public static void main(String[] args) {
        ThemeDB db=ThemeDB.getDefault();Button button=new Button();
        check(button.getPresentationStyle(false,false)==Button.NORMAL.defaultValue(),"default is secondary");
        for(String variation:Button.BUILTIN_VARIATIONS) {
            button.setThemeTypeVariation(variation);
            check(db.getTypeChain(variation).equals(java.util.List.of(variation,"Button","Control")),"registered inheritance");
            boolean raised=variation.equals("SecondaryButton")||variation.equals("PrimaryButton")||variation.equals("DestructiveButton");
            check(button.getPresentationStyle(false,true).getDrawInset().top()==(raised?2:0),"state offsets are purely theme data");
        }
        db.registerVariation("test:CustomButton","PrimaryButton");
        Theme theme=db.getTheme(ResourceLocation.fromNamespaceAndPath("test","buttons"));
        var style=new StyleBoxFlat(0xFF123456,ThemeInsets.ZERO,0,ThemeInsets.ZERO,ThemeInsets.ZERO);
        var primary=db.getKey("PrimaryButton","normal",StyleBox.class);theme.set(primary,style);
        button.setTheme(theme);button.setThemeTypeVariation("test:CustomButton");
        check(button.getPresentationStyle(false,false)==style,"third-party variant inherits without a Button branch");
        button.setThemeOverride(Button.NORMAL,StyleBoxEmpty.INSTANCE);
        check(button.getPresentationStyle(false,false)==StyleBoxEmpty.INSTANCE,"local override");
        button.removeThemeOverride(Button.NORMAL);
        check(button.getPresentationStyle(false,false)==style,"restore inherited value");
        theme.set(primary,StyleBoxEmpty.INSTANCE);
        check(button.getPresentationStyle(false,false)==StyleBoxEmpty.INSTANCE,"live theme invalidation");
        try{button.setThemeTypeVariation("LabelHero");throw new AssertionError("unrelated variation accepted");}catch(IllegalArgumentException expected){}
        check(button.getThemeTypeVariation().equals("test:CustomButton"),"invalid value retained previous style");
        button.queueFree();LifecycleScheduler.flush();
        System.out.println("ORE_BUTTON_THEME_VARIATIONS_PASS");
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
