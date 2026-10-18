package io.github.luyingyilong.ore.inspector;

import io.github.luyingyilong.ore.*;
import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.geg.market.client.ClientMarketScreen;
import io.github.luyingyilong.geg.organization.client.ClientOrganizationScreen;
import io.github.luyingyilong.geg.mail.client.ClientMailScreen;
import io.github.luyingyilong.geg.chat.client.ClientChatScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Real native frontend and in-process backend; opt-in isolated world only. */
@Mod.EventBusSubscriber(modid="geg",value=Dist.CLIENT)
public final class NativeInspectorClientTest {
    private static int phase, ticks, clicks;
    private static Label preview;
    private static String nodeId;
    private static int property;
    private static Window previousWindow;
    private static InspectorBackend previousBackend;
    private static int reopenCount;
    private static io.github.luyingyilong.ore.input.Input liveInput;
    private static int invalidAt;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("geg.native.inspector.smoke") || event.phase!=TickEvent.Phase.END || phase==99) return;
        Minecraft mc=Minecraft.getInstance(); if(mc.player==null)return;
        try {
            if(++ticks>2400)throw new AssertionError("Native Inspector timeout phase "+phase);
            if(phase==0) {
                preview=new Label(Component.literal("Inspector preview / 中文基线")); preview.debugName("Native Inspector preview");
                Box content=new Box().gap(2).add(preview).add(new Button(Component.literal("Business button")).onPress(()->clicks++));
                Box rows=new Box();for(int i=0;i<350;i++)rows.add(new Label(Component.literal("Node "+i)));
                Scroll scroll=new Scroll().content(rows);scroll.sizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);content.add(scroll);
                Window.embedded().content(content).open();var screen=mc.screen;OreInspector.open();
                check(mc.screen==screen,"Inspector never replaces main screen"); phase=1;
            } else if(phase==1&&OreInspector.backend().snapshot().nodes().size()>350) {
                var backend=OreInspector.backend();var node=backend.snapshot().nodes().stream().filter(n->n.name().equals("Native Inspector preview")).findFirst().orElseThrow();
                nodeId=node.id();property=node.properties().stream().filter(p->p.name().equals("fontSize")).findFirst().orElseThrow().index();
                backend.execute(InspectorCommand.select(nodeId));backend.execute(InspectorCommand.edit(nodeId,property,"17"));
                check(preview.fontSize()==17,"Real reflective edit");preview.fontSize(13);check(preview.fontSize()==17,"Application refresh preserves override");
                backend.execute(new InspectorCommand(InspectorCommand.Operation.RESET,nodeId,property,"","",false));check(preview.fontSize()==13,"Latest base restored");
                backend.execute(InspectorCommand.action(InspectorCommand.Operation.UNDO));check(preview.fontSize()==17,"Undo");
                backend.execute(InspectorCommand.action(InspectorCommand.Operation.REDO));check(preview.fontSize()==13,"Redo");
                backend.execute(InspectorCommand.edit(nodeId,property,"NaN"));check(preview.fontSize()==13&&!backend.lastError().isEmpty(),"Invalid edit rejected");
                backend.execute(InspectorCommand.select(nodeId)); phase=2;
            } else if(phase==2&&ticks>35) {
                check(OreInspector.view().window().host().isOpen(),"Native frontend remains open after editor rebuild");
                FormItem field=(FormItem)find(OreInspector.view().window(),c->c instanceof FormItem item&&item.name().equals("fontSize"));
                liveInput=field.input();
                editDraft("18");check(preview.fontSize()==18,"Typing applies without Enter or Apply");
                editDraft("NaN");check(preview.fontSize()==18&&!OreInspector.backend().lastError().isEmpty(),"Invalid draft retains previous effective value");
                String error=OreInspector.backend().lastError();
                check(find(OreInspector.view().window(),c->c instanceof Label label&&label.visible()&&label.text().getString().equals(error))!=null,"Inline validation is visible");
                invalidAt=ticks;phase=10;
            } else if(phase==10&&ticks>invalidAt+8) {
                check(liveInput.getText().equals("NaN")&&preview.fontSize()==18,"Snapshots preserve invalid draft and last valid value");
                editDraft("19");check(preview.fontSize()==19&&OreInspector.backend().lastError().isEmpty(),"Correcting draft applies and clears error");
                OreInspector.backend().execute(InspectorCommand.action(InspectorCommand.Operation.UNDO));OreInspector.view().update();
                check(preview.fontSize()==18&&liveInput.getText().equals("18.0"),"Live edit undo refreshes input");
                OreInspector.backend().execute(InspectorCommand.action(InspectorCommand.Operation.REDO));OreInspector.view().update();
                check(preview.fontSize()==19,"Live edit redo");
                OreInspector.backend().execute(new InspectorCommand(InspectorCommand.Operation.RESET,nodeId,property,"","",false));OreInspector.view().update();
                check(preview.fontSize()==13,"Live edits still restore latest base");
                Tree<?> nativeTree=(Tree<?>)find(OreInspector.view().window(),c->c instanceof Tree<?>);
                Scroll treeScroll=(Scroll)nativeTree.parent();
                check(treeScroll.horizontalVisible()&&treeScroll.maxScrollX()>0,"Native tree exposes horizontal overflow");
                treeScroll.setScroll(treeScroll.maxScrollX(),0);check(treeScroll.scrollX()>0,"Native tree horizontal scrolling");treeScroll.setScroll(0,0);
                System.out.println("GEG_NATIVE_INSPECTOR_LIVE_EDIT_PASS");
                diagnose(OreInspector.view().window());
                io.github.luyingyilong.ore.window.NativeWindowClientTest.capture(OreInspector.view().window(), mc.gameDirectory.toPath().resolve("screenshots/native-inspector.png"));
                OreInspector.backend().execute(InspectorCommand.action(InspectorCommand.Operation.PICK));
                var press=new ScreenEvent.MouseButtonPressed.Pre(mc.screen,10,32,0);MinecraftForge.EVENT_BUS.post(press);
                var release=new ScreenEvent.MouseButtonReleased.Pre(mc.screen,10,32,0);MinecraftForge.EVENT_BUS.post(release);
                check(press.isCanceled()&&release.isCanceled()&&clicks==0,"Picker consumes business press/release");
                ClientMarketScreen.open(); phase=3;
            } else if(phase==3&&target("Market Window")) { ClientOrganizationScreen.open();phase=4; }
            else if(phase==4&&target("Organization Window")) { ClientMailScreen.open();phase=5; }
            else if(phase==5&&target("Mail Window")) { ClientChatScreen.open("");phase=6; }
            else if(phase==6&&target("Chat Window")) {
                check(OreInspector.backend().snapshot().targets().size()>=4,"All business roots registered separately");
                closeNativeInspector();phase=7;
            } else if(phase==7&&io.github.luyingyilong.ore.window.WindowManager.nativeWindows()==0) {
                check(OreInspector.view()==null&&OreInspector.backend()==null,"Native close clears Inspector references");
                var key=new ScreenEvent.KeyPressed.Pre(mc.screen,org.lwjgl.glfw.GLFW.GLFW_KEY_F8,0,0);
                MinecraftForge.EVENT_BUS.post(key);
                check(key.isCanceled()&&OreInspector.view()!=null&&OreInspector.view().window().host().isOpen(),"F8 reopens closed native Inspector");
                check(OreInspector.view().window()!=previousWindow&&OreInspector.backend()!=previousBackend,"Reopen creates a fresh session");
                check(mc.options.pauseOnLostFocus==false,"Reopened Inspector coordinates pause setting");
                phase=8;
            } else if(phase==8&&!OreInspector.backend().snapshot().nodes().isEmpty()) {
                if(++reopenCount<3){closeNativeInspector();phase=7;}
                else {OreInspector.stop();phase=9;}
            } else if(phase==9&&io.github.luyingyilong.ore.window.WindowManager.nativeWindows()==0) {
                System.out.println("GEG_NATIVE_INSPECTOR_REOPEN_PASS");
                System.out.println("GEG_NATIVE_INSPECTOR_CLIENT_PASS");phase=99;mc.stop();
            }
        }catch(Throwable error){error.printStackTrace();System.out.println("GEG_NATIVE_INSPECTOR_CLIENT_FAIL");phase=99;mc.stop();}
    }
    private static void editDraft(String text) {
        liveInput.getStringValue().setStringValue(text);liveInput.setText(text);OreInspector.view().update();
    }
    private static Control find(Control node,java.util.function.Predicate<Control> predicate) {
        if(predicate.test(node))return node;
        for(Control child:node.children()){Control found=find(child,predicate);if(found!=null)return found;}
        return null;
    }
    private static void closeNativeInspector() {
        previousWindow=OreInspector.view().window(); previousBackend=OreInspector.backend();
        long handle=((io.github.luyingyilong.ore.window.NativeWindowHost)previousWindow.host()).nativeHandle();
        var callback=org.lwjgl.glfw.GLFW.glfwSetWindowCloseCallback(handle,null);
        check(callback!=null,"Native close callback registered");
        org.lwjgl.glfw.GLFW.glfwSetWindowCloseCallback(handle,callback);
        callback.invoke(handle);
    }
    private static void diagnose(Control control) {
        if (control instanceof Button button) {
            var properties=new java.util.ArrayList<io.github.luyingyilong.ore.debug.DebugProperty>();button.debugProperties(properties);
            properties.stream().filter(p->p.target() instanceof Label).map(p->(Label)p.target()).distinct()
                    .filter(label->label.text().getString().contains("Restore node")).forEach(label->{ check(label.bounds().right() <= OreInspector.view().window().context().guiWidth(),"Property button stays within viewport"); });
        }
        if(control instanceof io.github.luyingyilong.ore.input.InputControl input)
            System.out.println("NATIVE_EDITOR_VALUE "+input.widget().getText()+" "+input.displayedBounds());
        control.children().forEach(NativeInspectorClientTest::diagnose);
    }
    private static boolean target(String name) {
        var backend=OreInspector.backend();var target=backend.snapshot().targets().stream().filter(t->t.name().equals(name)).findFirst();
        if(target.isEmpty())return false;
        if (backend.inspectedRoot() == null || !backend.inspectedRoot().debugName().equals(name)) {
            backend.execute(new InspectorCommand(InspectorCommand.Operation.TARGET,target.get().id(),-1,"","",false)); return false;
        }
        return !backend.snapshot().nodes().isEmpty() && !backend.snapshot().waiting();
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
