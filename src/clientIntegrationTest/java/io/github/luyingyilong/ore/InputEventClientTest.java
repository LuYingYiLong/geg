package io.github.luyingyilong.ore;

import brachy.modularui.api.widget.Interactable;
import io.github.luyingyilong.ore.input.*;
import io.github.luyingyilong.ore.layout.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Opt-in real MUI screen boundary, isolated from player worlds and normal launches. */
@Mod.EventBusSubscriber(modid="geg",value=Dist.CLIENT)
public final class InputEventClientTest {
    private static int phase,ticks,submissions;
    private static Window window;
    private static Input field;
    private static TextArea text;
    private static InputControl fieldNode,textNode;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(!Boolean.getBoolean("geg.input.event.smoke")||event.phase!=TickEvent.Phase.END||phase==99)return;
        var mc=Minecraft.getInstance();if(mc.player==null)return;
        try {
            if(++ticks>800)throw new AssertionError("Input event client timeout phase "+phase);
            if(phase==0) {
                field=new Input().placeholder(Component.literal("Input / 输入"));
                text=new TextArea(0).rows(3).onSubmit(()->submissions++);
                fieldNode=new InputControl(field);textNode=new InputControl(text);
                var box=new Box().gap(4).add(fieldNode).add(textNode).add(new Button(Component.literal("Click")));
                window=Window.embedded().content(box).onBack(()->{});window.open();phase=1;
            } else if(phase==1&&field.isValid()&&text.isValid()&&window.context().mui()!=null&&ticks>15) {
                var p=fieldNode.widgetBounds();
                // MUI ScreenWrapper receives input from Forge Pre events, not vanilla Screen callbacks.
                window.context().mui().updateState(p.x()+5,p.y()+8,0);
                check(net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ScreenEvent.MouseButtonPressed.Pre(mc.screen,p.x()+5,p.y()+8,0)),"Real MUI press accepted");
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ScreenEvent.MouseButtonReleased.Pre(mc.screen,p.x()+5,p.y()+8,0));
                check(field.isFocused(),"Window focuses native field");
                check(field.onCharTyped('x',0)==Interactable.Result.IGNORE,"MUI sibling cannot independently edit tree input");
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ScreenEvent.CharacterTyped.Pre(mc.screen,'中',0));net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ScreenEvent.CharacterTyped.Pre(mc.screen,'文',0));
                check(field.getText().equals("中文"),"Embedded text delivered exactly once");
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ScreenEvent.KeyPressed.Pre(mc.screen,GLFW.GLFW_KEY_A,0,GLFW.GLFW_MOD_CONTROL));
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ScreenEvent.KeyReleased.Pre(mc.screen,GLFW.GLFW_KEY_A,0,GLFW.GLFW_MOD_CONTROL));
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ScreenEvent.KeyPressed.Pre(mc.screen,GLFW.GLFW_KEY_X,0,GLFW.GLFW_MOD_CONTROL));
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ScreenEvent.KeyReleased.Pre(mc.screen,GLFW.GLFW_KEY_X,0,GLFW.GLFW_MOD_CONTROL));
                check(field.getText().isEmpty(),"Cut through Window");
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ScreenEvent.KeyPressed.Pre(mc.screen,GLFW.GLFW_KEY_Z,0,GLFW.GLFW_MOD_CONTROL));
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ScreenEvent.KeyReleased.Pre(mc.screen,GLFW.GLFW_KEY_Z,0,GLFW.GLFW_MOD_CONTROL));
                check(field.getText().equals("中文"),"Undo through Window");
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ScreenEvent.KeyPressed.Pre(mc.screen,GLFW.GLFW_KEY_ESCAPE,0,0));
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ScreenEvent.KeyReleased.Pre(mc.screen,GLFW.GLFW_KEY_ESCAPE,0,0));
                check(!field.isFocused()&&window.host().isOpen(),"Esc blurs without closing Window");
                phase=2;
            } else if(phase==2) {
                window.focusManager().requestFocus(textNode,window.context().mui());
                String value="第一行中文\n第二行自动换行测试\n第三行";
                window.context().clipboard(value);
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ScreenEvent.KeyPressed.Pre(mc.screen,GLFW.GLFW_KEY_V,0,GLFW.GLFW_MOD_CONTROL));
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ScreenEvent.KeyReleased.Pre(mc.screen,GLFW.GLFW_KEY_V,0,GLFW.GLFW_MOD_CONTROL));
                check(text.getText().equals(value),"Multiline paste exactly once");
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ScreenEvent.KeyPressed.Pre(mc.screen,GLFW.GLFW_KEY_ENTER,0,0));
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ScreenEvent.KeyReleased.Pre(mc.screen,GLFW.GLFW_KEY_ENTER,0,0));
                check(submissions==1,"Composer submit exactly once");
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ScreenEvent.KeyPressed.Pre(mc.screen,GLFW.GLFW_KEY_ENTER,0,GLFW.GLFW_MOD_SHIFT));
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ScreenEvent.KeyReleased.Pre(mc.screen,GLFW.GLFW_KEY_ENTER,0,GLFW.GLFW_MOD_SHIFT));
                check(submissions==1&&text.getText().endsWith("\n"),"Shift Enter remains a newline");
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ScreenEvent.KeyPressed.Pre(mc.screen,GLFW.GLFW_KEY_ESCAPE,0,0));
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ScreenEvent.KeyReleased.Pre(mc.screen,GLFW.GLFW_KEY_ESCAPE,0,0));
                check(!text.isFocused(),"TextArea Esc blur");
                window.close();phase=99;
                System.out.println("ORE_INPUT_EVENT_CLIENT_PASS");mc.stop();
            }
        } catch(Throwable error) {
            phase=99;error.printStackTrace();System.out.println("ORE_INPUT_EVENT_CLIENT_FAIL");
            if(window!=null)window.close();mc.stop();
        }
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
