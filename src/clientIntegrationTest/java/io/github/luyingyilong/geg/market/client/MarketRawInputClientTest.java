package io.github.luyingyilong.geg.market.client;

import io.github.luyingyilong.ore.Form;
import io.github.luyingyilong.ore.FormItem;
import io.github.luyingyilong.ore.input.NumberInput;
import io.github.luyingyilong.ore.input.InputGeometryProbe;
import io.github.luyingyilong.ore.layout.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Full registered GLFW callback path; never seeds MUI mouse state or posts Forge input events. */
@Mod.EventBusSubscriber(modid="geg",value=Dist.CLIENT)
public final class MarketRawInputClientTest {
    private static int phase,ticks;
    private static Window window;
    private static NumberInput number;
    private static int x,y;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(!Boolean.getBoolean("geg.market.raw.smoke")||event.phase!=TickEvent.Phase.END||phase==99)return;
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
                Form form=(Form)field(ClientMarketScreen.class,null,"detailForm");
                var node=form.children().stream().filter(FormItem.class::isInstance).map(FormItem.class::cast)
                        .filter(item -> item.name().equals("quantity")).findFirst().orElseThrow().inputNode();
                check(!number.canHover() && number.canClickThrough(), "Tree editor must not block Canvas in native MUI hit testing");
                var a=node.widgetBounds();x=a.x()+8;y=a.y()+a.height()/2;
                move(x,y);phase=3;
            }else if(phase==3&&ticks>48){
                mouse(GLFW.GLFW_PRESS);state("PRESS");phase=4;
            }else if(phase==4&&ticks>51){
                mouse(GLFW.GLFW_RELEASE);state("RELEASE");
                check(number.isFocused(),"GLFW press keeps NumberInput focus");
                key(GLFW.GLFW_KEY_A,GLFW.GLFW_MOD_CONTROL);
                text("2+3");state("TEXT");
                check(number.getText().equals("2+3"),"GLFW characters accepted: "+number.getText());
                phase=5;
            }else if(phase==5&&ticks>55){
                state("AFTER_FRAMES");check(number.getText().equals("2+3")&&number.isFocused(),"Text and focus persist after frames");
                key(GLFW.GLFW_KEY_LEFT,0);text("9");
                check(number.getText().equals("2+93"),"GLFW caret key edits at correct position: "+number.getText());
                key(GLFW.GLFW_KEY_A,GLFW.GLFW_MOD_CONTROL);text("12+34");
                move(InputGeometryProbe.clickX(number,2),y);phase=6;
            }else if(phase==6&&ticks>62){
                InputGeometryProbe.resetClickSequence(number);mouse(GLFW.GLFW_PRESS);phase=7;
            }else if(phase==7&&ticks>65){
                mouse(GLFW.GLFW_RELEASE);state("CARET");
                check(InputGeometryProbe.cursor(number).x==2,"GLFW click at column 2, got "+InputGeometryProbe.cursor(number).x);
                text("1");check(number.getText().equals("121+34"),"GLFW insert at clicked caret");
                number=(NumberInput)field(ClientMarketScreen.class,null,"price");
                var a=number.getArea();move(a.x+8,a.y+a.height/2);phase=8;
            }else if(phase==8&&ticks>70){
                mouse(GLFW.GLFW_PRESS);phase=9;
            }else if(phase==9&&ticks>73){
                mouse(GLFW.GLFW_RELEASE);check(number.isFocused(),"Price focus under real mouse");
                key(GLFW.GLFW_KEY_A,GLFW.GLFW_MOD_CONTROL);text("0.1+0.2");phase=10;
            }else if(phase==10&&ticks>77){
                check(number.getText().equals("0.1+0.2"),"Price text persists with mouse over field");
                key(GLFW.GLFW_KEY_ENTER,0);check(number.getText().equals("0.30"),"Price arithmetic commits");
                Object canvas=field(ClientMarketScreen.class,null,"canvas");
                var menu=MarketCanvasWidget.class.getDeclaredField("quantityMenu");menu.setAccessible(true);menu.setBoolean(canvas,true);
                ClientMarketScreen.showTradeQuantity(true);
                var configure=MarketCanvasWidget.class.getDeclaredMethod("configureQuantityActions");configure.setAccessible(true);configure.invoke(canvas);
                phase=11;
            }else if(phase==11&&ticks>81){
                number=(NumberInput)field(ClientMarketScreen.class,null,"tradeQuantity");
                var a=number.getArea();move(a.x+8,a.y+a.height/2);phase=12;
            }else if(phase==12&&ticks>84){
                mouse(GLFW.GLFW_PRESS);phase=13;
            }else if(phase==13&&ticks>87){
                mouse(GLFW.GLFW_RELEASE);check(number.isFocused(),"Third-level quantity focus");
                key(GLFW.GLFW_KEY_A,GLFW.GLFW_MOD_CONTROL);text("2+3");phase=14;
            }else if(phase==14&&ticks>91){
                check(number.getText().equals("2+3"),"Third-level input receives text while hovered");
                key(GLFW.GLFW_KEY_LEFT,0);text("9");
                check(number.getText().equals("2+93"),"Third-level caret editing");
                phase=99;System.out.println("GEG_MARKET_RAW_CLIENT_PASS");mc.stop();
            }
        }catch(Throwable error){phase=99;error.printStackTrace();System.out.println("GEG_MARKET_RAW_CLIENT_FAIL");mc.stop();}
    }
    private static long handle(){return Minecraft.getInstance().getWindow().getWindow();}
    private static void move(int x,int y){
        var mc=Minecraft.getInstance();double px=(double)x*mc.getWindow().getScreenWidth()/mc.screen.width;
        double py=(double)y*mc.getWindow().getScreenHeight()/mc.screen.height;
        var callback=GLFW.glfwSetCursorPosCallback(handle(),null);GLFW.glfwSetCursorPosCallback(handle(),callback);
        GLFW.glfwSetCursorPos(handle(),px,py);callback.invoke(handle(),px,py);
    }
    private static void mouse(int action){var cb=GLFW.glfwSetMouseButtonCallback(handle(),null);GLFW.glfwSetMouseButtonCallback(handle(),cb);cb.invoke(handle(),0,action,0);}
    private static void key(int code,int modifiers){var cb=GLFW.glfwSetKeyCallback(handle(),null);GLFW.glfwSetKeyCallback(handle(),cb);cb.invoke(handle(),code,0,GLFW.GLFW_PRESS,modifiers);cb.invoke(handle(),code,0,GLFW.GLFW_RELEASE,modifiers);}
    private static void text(String value){var cb=GLFW.glfwSetCharModsCallback(handle(),null);GLFW.glfwSetCharModsCallback(handle(),cb);check(cb!=null,"Minecraft character/modifier callback present");value.codePoints().forEach(cp->cb.invoke(handle(),cp,0));}
    private static void state(String label){System.out.println("RAW_STATE "+label+" focused="+number.isFocused()+" enabled="+number.isEnabled()+" ore="+window.focusManager().focused()+" text="+number.getText()+" mouse="+window.context().mui().getAbsMouseX()+","+window.context().mui().getAbsMouseY()+" cursor="+InputGeometryProbe.cursor(number).x);}
    private static Object field(Class<?> type,Object owner,String name)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
