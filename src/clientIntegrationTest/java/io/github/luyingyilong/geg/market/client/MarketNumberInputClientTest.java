package io.github.luyingyilong.geg.market.client;

import io.github.luyingyilong.ore.window.Window;

import io.github.luyingyilong.ore.control.form.Form;
import io.github.luyingyilong.ore.control.input.NumberInput;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Opt-in market fields test, no transaction requests or player inventory changes. */
@Mod.EventBusSubscriber(modid="geg",value=Dist.CLIENT)
public final class MarketNumberInputClientTest {
    private static int phase,ticks;
    private static Window window;
    private static NumberInput number;
    private static int x,y;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(!Boolean.getBoolean("geg.market.number.smoke")||event.phase!=TickEvent.Phase.END||phase==99)return;
        var mc=Minecraft.getInstance();if(mc.player==null)return;
        try {
            if(++ticks>700)throw new AssertionError("Timeout phase="+phase);
            if(phase==0){ClientMarketScreen.open();phase=1;}
            else if(phase==1&&ticks>25){
                Object canvas=field(ClientMarketScreen.class,null,"canvas");
                Class<?> kind=Class.forName(MarketCanvasWidget.class.getName()+"$Kind");
                Class<?> card=Class.forName(MarketCanvasWidget.class.getName()+"$Card");
                var constructor=card.getDeclaredConstructor(kind,ItemStack.class);constructor.setAccessible(true);
                Object inventory=java.util.Arrays.stream(kind.getEnumConstants()).filter(k->k.toString().equals("INVENTORY")).findFirst().orElseThrow();
                Object sample=constructor.newInstance(inventory,new ItemStack(Items.STONE,64));
                var quantity=card.getDeclaredField("quantity");quantity.setAccessible(true);quantity.setLong(sample,64);
                var show=MarketCanvasWidget.class.getDeclaredMethod("openModal",card);show.setAccessible(true);show.invoke(canvas,sample);
                window=(Window)field(MarketCanvasWidget.class,canvas,"window");
                number=(NumberInput)field(ClientMarketScreen.class,null,"quantity");phase=2;
            }else if(phase==2&&ticks>45){
                var form=(Form)field(ClientMarketScreen.class,null,"detailForm");
                var node=form.getChildren().stream().filter(io.github.luyingyilong.ore.control.form.FormItem.class::isInstance).map(io.github.luyingyilong.ore.control.form.FormItem.class::cast).filter(item -> item.getName().equals("quantity")).findFirst().orElseThrow().getInputNode();
                var a=node.getWidgetBounds();x=a.x()+8;y=a.y()+a.height()/2;
                window.getContext().getMui().updateState(x,y,0);
                check(net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ScreenEvent.MouseButtonPressed.Pre(mc.screen,x,y,0)),"Number press accepted");
                phase=3;
            }else if(phase==3&&ticks>48){
                window.getContext().getMui().updateState(x,y,0);
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ScreenEvent.MouseButtonReleased.Pre(mc.screen,x,y,0));
                check(number.isFocused(),"Market NumberInput holds focus across frames");
                key(GLFW.GLFW_KEY_A,GLFW.GLFW_MOD_CONTROL);
                for(char c:"2+3".toCharArray())net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ScreenEvent.CharacterTyped.Pre(mc.screen,c,0));
                check(number.getText().equals("2+3"),"Market NumberInput receives numeric expression: "+number.getText());
                key(GLFW.GLFW_KEY_ENTER,0);
                check(number.getText().equals("5"),"Enter commits expression");
                number.getStringValue().setStringValue("12+34");number.setText("12+34");phase=4;
            }else if(phase==4&&ticks>60){
                io.github.luyingyilong.ore.control.input.InputGeometryProbe.verifyLineHit(number);
                x=io.github.luyingyilong.ore.control.input.InputGeometryProbe.clickX(number,2);
                var a=number.getArea();y=a.y+1;
                window.getContext().getMui().updateState(x,y,0);
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ScreenEvent.MouseButtonPressed.Pre(mc.screen,x,y,0));
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ScreenEvent.MouseButtonReleased.Pre(mc.screen,x,y,0));
                var cursor=io.github.luyingyilong.ore.control.input.InputGeometryProbe.cursor(number);
                check(cursor.x==2,"Clicked column 2, got "+cursor.x+","+cursor.y);
                var form=(Form)field(ClientMarketScreen.class,null,"detailForm");
                var priceNode=form.getChildren().stream().filter(io.github.luyingyilong.ore.control.form.FormItem.class::isInstance)
                        .map(io.github.luyingyilong.ore.control.form.FormItem.class::cast).filter(item -> item.getName().equals("price")).findFirst().orElseThrow().getInputNode();
                verifyField(priceNode,(NumberInput)field(ClientMarketScreen.class,null,"price"));
                Object canvas=field(ClientMarketScreen.class,null,"canvas");
                var menu=MarketCanvasWidget.class.getDeclaredField("quantityMenu");menu.setAccessible(true);menu.setBoolean(canvas,true);
                ClientMarketScreen.showTradeQuantity(true);
                var configure=MarketCanvasWidget.class.getDeclaredMethod("configureQuantityActions");configure.setAccessible(true);configure.invoke(canvas);
                phase=5;
            }else if(phase==5&&ticks>75){
                var form=(Form)field(ClientMarketScreen.class,null,"tradeForm");
                var node=((io.github.luyingyilong.ore.control.form.FormItem)form.getChildren().get(0)).getInputNode();
                verifyField(node,(NumberInput)field(ClientMarketScreen.class,null,"tradeQuantity"));
                phase=99;System.out.println("GEG_MARKET_NUMBER_CLIENT_PASS");mc.stop();
            }
        }catch(Throwable error){phase=99;error.printStackTrace();System.out.println("GEG_MARKET_NUMBER_CLIENT_FAIL");mc.stop();}
    }
    private static void verifyField(io.github.luyingyilong.ore.control.input.InputControl node, NumberInput input)throws Exception {
        input.setAvailableMaximum(java.math.BigDecimal.valueOf(64));
        input.getStringValue().setStringValue("12+34");input.setText("12+34");
        // Move after the last paint: MUI's deferred resize must not leave stale local coordinates.
        node.setOffset(2,0);
        int px=io.github.luyingyilong.ore.control.input.InputGeometryProbe.clickX(input,2);
        int py=node.getWidgetBounds().y()+node.getBounds().height()-1;
        io.github.luyingyilong.ore.control.input.InputGeometryProbe.resetClickSequence(input);
        var mc=Minecraft.getInstance();window.getContext().getMui().updateState(px,py,0);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ScreenEvent.MouseButtonPressed.Pre(mc.screen,px,py,0));
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ScreenEvent.MouseButtonReleased.Pre(mc.screen,px,py,0));
        check(input.isFocused(),"Moved NumberInput focuses");
        var point=io.github.luyingyilong.ore.control.input.InputGeometryProbe.cursor(input);
        check(point.x==2,"Moved NumberInput caret at column 2, got "+point.x);
        var expected=node.getWidgetBounds();var actual=input.getArea();
        check(actual.x==expected.x() && actual.y==expected.y() && actual.rx==actual.x-input.getParent().getArea().x && actual.ry==actual.y-input.getParent().getArea().y,"Native area and relative transform follow Ore immediately");
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ScreenEvent.CharacterTyped.Pre(mc.screen,'1',0));
        check(input.getText().equals("121+34"),"Insert at clicked caret");
        key(GLFW.GLFW_KEY_A,GLFW.GLFW_MOD_CONTROL);
        for(char c:"1+1".toCharArray())net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ScreenEvent.CharacterTyped.Pre(mc.screen,c,0));
        key(GLFW.GLFW_KEY_ENTER,0);
        check(input.getText().equals(input.getDecimalPlaces()==0?"2":"2.00"),"Moved field commits arithmetic");
        node.setOffset(0,0);
    }
    private static Object field(Class<?> type,Object owner,String name)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private static void key(int code,int modifiers){var mc=Minecraft.getInstance();net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ScreenEvent.KeyPressed.Pre(mc.screen,code,0,modifiers));net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ScreenEvent.KeyReleased.Pre(mc.screen,code,0,modifiers));}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
