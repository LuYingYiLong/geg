package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.event.WindowInputs;
import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.theme.*;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** State geometry is theme data, shared by paint, contents and focus, never by hit testing. */
public final class StateStyleTest {
    private static final Font FONT=new Font(id -> null,false) {
        @Override public int width(FormattedText text) { return text.getString().length()*6; }
    };
    public static void main(String[] args) {
        geometry(); buttons(); segmented();
        System.out.println("ORE_STATE_STYLE_PASS");
    }

    private static void geometry() {
        ThemeInsets margin=new ThemeInsets(8,4,8,4), inset=new ThemeInsets(1,5,3,0), expand=new ThemeInsets(2,1,4,3);
        Offset offset=new Offset(-2,4);
        StyleBoxTexture texture=(StyleBoxTexture)OreTheme.BUTTON_SECONDARY_NORMAL.defaultValue();
        var variants=List.<StyleBox>of(new StyleBoxEmpty(margin,expand,inset,offset),
                new StyleBoxFlat(0xFF123456,ThemeInsets.ZERO,0,margin,expand,inset,offset),
                new StyleBoxTexture(texture.location(),texture.imageWidth(),texture.imageHeight(),texture.x(),texture.y(),
                        texture.width(),texture.height(),texture.textureBorders(),margin,expand,texture.horizontalMode(),
                        texture.verticalMode(),true,1F,inset,offset));
        Rect area=new Rect(10,20,100,20);
        for(StyleBox style:variants) {
            check(style.getBackgroundRect(area).equals(new Rect(11,25,96,15)),"Background insets are independent per side");
            check(style.getDrawRect(area).equals(new Rect(9,24,102,19)),"Expansion is applied after the inset");
            check(style.getContentRect(area).equals(new Rect(18,24,84,12)),"Layout content does not shrink with the background");
            check(style.getContentDrawRect(area).equals(new Rect(16,28,84,12)),"Content shifts by the full offset, without recentering");
            check(style.getMinimumSize().equals(new Size(16,8)),"Presentation geometry does not reserve layout space");
            check(style.getBackgroundRect(new Rect(0,0,1,1)).equals(new Rect(1,1,0,0)),"Tiny targets are clamped");
        }
    }

    private static void buttons() {
        for(Button.Type type:Button.Type.values()) {
            Button button=new Button(cue -> { }).setType(type).setLabel(Component.literal("A"));
            Badge badge=new Badge(); button.setBadge(badge);
            var style=new StyleBoxEmpty(new ThemeInsets(8,4,8,4),ThemeInsets.ZERO,
                    new ThemeInsets(1,5,3,0),new Offset(-2,4));
            button.setThemeOverride(Button.getStyleKey(type,false,true,false),style);
            Window window=new Window(true).setContent(button); window.layoutViewport(100,20,FONT);
            Size natural=button.measureSize(FONT,new Size(100,20));
            Rect before=badge.getBounds();
            int[] activations={0}; button.pressed().connect(button,()->activations[0]++);
            window.getFocusManager().requestFocus(button,null);
            WindowInputs.keyDown(window,GLFW.GLFW_KEY_ENTER,0,null);
            check(button.getPresentationStyle(false,true)==style,"Every Button type uses its themed state, including TEXT/MENU");
            check(button.getFocusBounds().equals(style.getBackgroundRect(button.getAbsoluteBounds())),"Focus shares state background geometry");
            button.layout(button.getBounds(),FONT);
            check(badge.getBounds().equals(before.translated(-2,4)),"Badge follows the same content offset");
            check(button.measureSize(FONT,new Size(100,20)).equals(natural),"Pressing does not change the button's measured size");
            Button.Icon icon=new Button.Icon(OreIcons.SMALL_CLOSE,7,7);
            var normal=new StyleBoxEmpty(style.getContentMargin(),ThemeInsets.ZERO);
            var resting=Button.contentLayout(button.getBounds(),normal,6,true,icon,icon,Button.TextAlignment.CENTER);
            var held=Button.contentLayout(button.getBounds(),style,6,true,icon,icon,Button.TextAlignment.CENTER);
            check(held.label().equals(resting.label().translated(-2,4)) && held.icon().equals(resting.icon().translated(-2,4))
                    && held.trailingIcon().equals(resting.trailingIcon().translated(-2,4)),"Text and both icons share full offsets");
            WindowInputs.keyUp(window,GLFW.GLFW_KEY_ENTER,0,null);
            check(activations[0]==1,"Theme geometry does not change release activation");
            WindowInputs.press(window,0,0,null); // Inside the layout, outside the inset paint.
            WindowInputs.release(window,0,0); check(activations[0]==2,"Inset pixels retain the original hit area");
            button.setThemeOverride(Button.getStyleKey(type,false,true,false),normal);
            WindowInputs.press(window,0,0,null);
            check(button.getFocusBounds().equals(button.getAbsoluteBounds()),"An empty state style removes all hardcoded sinking");
            WindowInputs.move(window,110,25); WindowInputs.release(window,110,25);
            check(activations[0]==2,"Dragging out still cancels");
            window.queueFree(); LifecycleScheduler.flush();
        }
    }

    private static void segmented() {
        Segmented segmented=new Segmented(List.of(Component.literal("A"),Component.literal("B")));
        var selected=new StyleBoxEmpty(new ThemeInsets(8,4,8,4),ThemeInsets.ZERO,new ThemeInsets(0,4,0,0),new Offset(0,3));
        var pressed=new StyleBoxFlat(0xFFFFFFFF,ThemeInsets.ZERO,0,new ThemeInsets(8,4,8,4),ThemeInsets.ZERO,
                new ThemeInsets(1,6,2,1),new Offset(-2,5));
        segmented.setThemeOverride(OreTheme.SEGMENTED_BUTTON_SELECTED,selected);
        segmented.setThemeOverride(OreTheme.BUTTON_SECONDARY_PRESSED,pressed);
        Window window=new Window(true).setContent(segmented); window.layoutViewport(100,20,FONT);
        int[] changes={0}; segmented.selectionChanged().connect(segmented,index->changes[0]++);
        window.getFocusManager().requestFocus(segmented,null);
        check(segmented.getFocusBounds().equals(selected.getBackgroundRect(new Rect(0,0,51,20))),"Selected geometry comes from its own style");
        WindowInputs.keyDown(window,GLFW.GLFW_KEY_RIGHT,0,null);
        WindowInputs.keyDown(window,GLFW.GLFW_KEY_ENTER,0,null);
        check(segmented.getFocusBounds().equals(pressed.getBackgroundRect(new Rect(50,0,51,20))),"Keyboard press focus uses the same pressed style");
        check(changes[0]==0 && segmented.getOptionStyle(1,false)==pressed,"Pressing has no premature selection change");
        WindowInputs.keyUp(window,GLFW.GLFW_KEY_ENTER,0,null);
        check(changes[0]==1 && segmented.getFocusBounds().equals(selected.getBackgroundRect(new Rect(50,0,51,20))),"Release changes to the selected style");
        WindowInputs.press(window,0,0,null);
        check(segmented.getOptionStyle(0,true)==pressed,"Pointer uses the pressed style even in the visually inset area");
        WindowInputs.move(window,101,25);
        check(segmented.getOptionStyle(0,false)!=pressed,"Dragging out restores normal presentation");
        WindowInputs.release(window,101,25); check(changes[0]==1,"Dragging out does not select");
        var flat=new StyleBoxEmpty(selected.getContentMargin(),ThemeInsets.ZERO);
        segmented.setThemeOverride(OreTheme.SEGMENTED_BUTTON_SELECTED,flat);
        check(segmented.getFocusBounds().equals(new Rect(50,0,51,20)),"Selected inset is configurable down to zero");
        window.queueFree(); LifecycleScheduler.flush();
    }

    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
