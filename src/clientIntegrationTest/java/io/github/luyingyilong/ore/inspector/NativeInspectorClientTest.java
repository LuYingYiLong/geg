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
    private static Button signalButton;
    private static Window originalRoot, marketRoot, organizationRoot, mailRoot;
    private static float retainedFontSize;
    private static io.github.luyingyilong.ore.input.Input hintInput;
    private static io.github.luyingyilong.ore.input.TextArea hintArea;
    private static java.util.List<Long> hintSignalIds;
    private static io.github.luyingyilong.ore.input.Input currentHint;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("geg.native.inspector.smoke") || event.phase!=TickEvent.Phase.END || phase==99) return;
        Minecraft mc=Minecraft.getInstance(); if(mc.player==null)return;
        try {
            if(++ticks>2400)throw new AssertionError("Native Inspector timeout phase "+phase);
            if(phase==0) {
                io.github.luyingyilong.ore.input.PlaceholderReuseTest.verify();
                check(java.util.Arrays.asList(mc.options.keyMappings).contains(OreInspector.OPEN),
                        "Inspector service registers F8 without a GEG development bootstrap");
                preview=new Label(Component.literal("Inspector preview / 中文基线")); preview.setDebugName("Native Inspector preview");
                signalButton = new Button(Component.literal("Business button"));
                signalButton.setDebugName("Signal diagnostics button");
                for (int i = 0; i < 2; i++) subscribeSignalButton();
                var childContainer104 = new Box().setGap(2);
                childContainer104.addChild(preview);
                childContainer104.addChild(signalButton);
                Box content=childContainer104;
                hintInput = new io.github.luyingyilong.ore.input.Input().setPlaceholder(Component.literal("Stable single-line placeholder"));
                hintArea = new io.github.luyingyilong.ore.input.TextArea(0);
                hintArea.setRows(2).setPlaceholder(Component.literal("Stable multiline placeholder / 自动换行的占位文本 ".repeat(4)));
                var inputNode = new io.github.luyingyilong.ore.input.InputControl(hintInput); inputNode.setDebugName("Retained input probe");
                var areaNode = new io.github.luyingyilong.ore.input.InputControl(hintArea); areaNode.setDebugName("Retained area probe");
                content.addChild(inputNode);
                content.addChild(areaNode);
                content.addChild(new Segmented(java.util.List.of(Component.literal("One"), Component.literal("Two"))));
                Box rows=new Box();for(int i=0;i<350;i++){ rows.addChild(new Label(Component.literal("Node "+i))); }
                Scroll scroll=new Scroll().setContent(rows);scroll.setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);content.addChild(scroll);
                originalRoot = Window.embedded().setContent(content); originalRoot.setDebugName("Original Window"); originalRoot.open();var screen=mc.screen;OreInspector.open();
                check(mc.screen==screen,"Inspector never replaces main screen"); phase=1;
            } else if(phase==1&&OreInspector.backend().snapshot().nodes().size()>350) {
                var backend=OreInspector.backend();var node=backend.snapshot().nodes().stream().filter(n->n.name().equals("Native Inspector preview")).findFirst().orElseThrow();
                nodeId=node.id();property=node.properties().stream().filter(p->p.name().equals("fontSize")).findFirst().orElseThrow().index();
                backend.execute(InspectorCommand.select(nodeId));backend.execute(InspectorCommand.edit(nodeId,property,"17"));
                check(preview.getFontSize()==17,"Real reflective edit: " + backend.snapshot().error() + " / " + originalRoot.getLifecycleInfo());preview.setFontSize(13);check(preview.getFontSize()==17,"Application refresh preserves override");
                backend.execute(new InspectorCommand(InspectorCommand.Operation.RESET,nodeId,property,"","",false));check(preview.getFontSize()==13,"Latest base restored");
                backend.execute(InspectorCommand.action(InspectorCommand.Operation.UNDO));check(preview.getFontSize()==17,"Undo");
                backend.execute(InspectorCommand.action(InspectorCommand.Operation.REDO));check(preview.getFontSize()==13,"Redo");
                backend.execute(InspectorCommand.edit(nodeId,property,"NaN"));check(preview.getFontSize()==13&&!backend.lastError().isEmpty(),"Invalid edit rejected");
                backend.execute(InspectorCommand.select(nodeId)); phase=2;
            } else if(phase==2&&ticks>35) {
                check(OreInspector.view().window().getHost().isOpen(),"Native frontend remains open after editor rebuild");
                FormItem field=(FormItem)find(OreInspector.view().window(),c->c instanceof FormItem item&&item.getName().equals("fontSize"));
                liveInput=field.getInput();
                editDraft("18");check(preview.getFontSize()==18,"Typing applies without Enter or Apply");
                editDraft("NaN");check(preview.getFontSize()==18&&!OreInspector.backend().lastError().isEmpty(),"Invalid draft retains previous effective value");
                String error=OreInspector.backend().lastError();
                check(find(OreInspector.view().window(),c->c instanceof Label label&&label.getVisible()&&label.getText().getString().equals(error))!=null,"Inline validation is visible");
                invalidAt=ticks;phase=10;
            } else if(phase==10&&ticks>invalidAt+8) {
                check(liveInput.getText().equals("NaN")&&preview.getFontSize()==18,"Snapshots preserve invalid draft and last valid value");
                editDraft("19");check(preview.getFontSize()==19&&OreInspector.backend().lastError().isEmpty(),"Correcting draft applies and clears error");
                OreInspector.backend().execute(InspectorCommand.action(InspectorCommand.Operation.UNDO));OreInspector.view().update();
                check(preview.getFontSize()==18&&liveInput.getText().equals("18.0"),"Live edit undo refreshes input");
                OreInspector.backend().execute(InspectorCommand.action(InspectorCommand.Operation.REDO));OreInspector.view().update();
                check(preview.getFontSize()==19,"Live edit redo");
                OreInspector.backend().execute(new InspectorCommand(InspectorCommand.Operation.RESET,nodeId,property,"","",false));OreInspector.view().update();
                check(preview.getFontSize()==13,"Live edits still restore latest base");
                Tree<?> nativeTree=(Tree<?>)find(OreInspector.view().window(),c->c instanceof Tree<?>);
                Scroll treeScroll=(Scroll)nativeTree.getParent();
                check(treeScroll.getHorizontalVisible()&&treeScroll.getMaxScrollX()>0,"Native tree exposes horizontal overflow");
                treeScroll.setScroll(treeScroll.getMaxScrollX(),0);check(treeScroll.getScrollX()>0,"Native tree horizontal scrolling");treeScroll.setScroll(0,0);
                System.out.println("GEG_NATIVE_INSPECTOR_LIVE_EDIT_PASS");
                diagnose(OreInspector.view().window());
                var signalNode = OreInspector.backend().snapshot().nodes().stream().filter(n -> n.name().equals("Signal diagnostics button")).findFirst().orElseThrow();
                OreInspector.backend().execute(InspectorCommand.select(signalNode.id()));
                OreInspector.view().update();
                Segmented views = (Segmented)find(OreInspector.view().window(), c -> c instanceof Segmented && c.getDebugName().equals("Inspector views"));
                clickViewIndex(views,2);
                check(OreInspector.backend().snapshot().themes().items().stream().anyMatch(i->i.key().equals("Button:secondary_normal") && i.value().contains("StyleBoxTexture")),"Native theme view resolves actual button texture");
                check(io.github.luyingyilong.ore.theme.ThemeDB.getDefault().getDiagnostics().isEmpty(),"Shipped themes loaded without MUI or resource errors");
                System.out.println("GEG_NATIVE_INSPECTOR_THEMES_PASS");
                clickView(views, true);
                check(OreInspector.backend().snapshot().signals().data().signals().stream().anyMatch(s -> s.name().equals("pressed")
                        && s.connections().size() == 2 && s.connections().stream().allMatch(io.github.luyingyilong.ore.signal.SignalDiagnostics.ConnectionInfo::possibleDuplicate)),
                        "Native signal snapshot includes duplicate subscriptions");
                invalidAt = ticks;
                phase = 20;
            } else if (phase == 20 && ticks > invalidAt + 6) {
                check(find(OreInspector.view().window(), c -> c instanceof Label label && label.getVisible()
                        && label.getText().getString().contains("NativeInspectorClientTest.java:")) != null,
                        "Read-only signal view displays the original subscription file and line");
                check(clicks == 0, "Inspecting signals does not activate business actions");
                io.github.luyingyilong.ore.window.NativeWindowClientTest.capture(OreInspector.view().window(), mc.gameDirectory.toPath().resolve("screenshots/native-inspector-signals-active.png"));
                signalButton.queueFree();
                io.github.luyingyilong.ore.layout.LifecycleScheduler.flush();
                invalidAt = ticks;
                phase = 21;
            } else if (phase == 21 && ticks > invalidAt + 6) {
                var signals = OreInspector.backend().snapshot().signals();
                check(signals.state().equals("disposed") && signals.data().signals().stream().allMatch(s -> s.connections().isEmpty()),
                        "Removed selection shows disposed signals with zero live connections");
                check(signals.data().history().stream().anyMatch(e -> e.kind() == io.github.luyingyilong.ore.signal.SignalDiagnostics.Kind.SCOPE_CLOSED
                        && e.remainingConnections() == 0), "Native signal view retains cleanup evidence");
                io.github.luyingyilong.ore.window.NativeWindowClientTest.capture(OreInspector.view().window(), mc.gameDirectory.toPath().resolve("screenshots/native-inspector-signals.png"));
                Segmented views = (Segmented)find(OreInspector.view().window(), c -> c instanceof Segmented && c.getDebugName().equals("Inspector views"));
                clickView(views, false);
                System.out.println("GEG_NATIVE_INSPECTOR_SIGNALS_PASS");
                startHintProbe("Retained input probe", hintInput);
                phase = 22;
            } else if (phase == 22 && ticks > invalidAt + 20) {
                verifyHintProbe();
                startHintProbe("Retained area probe", hintArea);
                phase = 23;
            } else if (phase == 23 && ticks > invalidAt + 20) {
                verifyHintProbe();
                check(placeholder(hintArea).getWrap() == Label.Wrap.WORD && placeholder(hintArea).lines(mc.font, 100).size() > 1,
                        "TextArea placeholder keeps real multiline wrapping");
                System.out.println("GEG_PLACEHOLDER_STABLE_RENDER_PASS");
                Segmented views = (Segmented)find(OreInspector.view().window(), c -> c instanceof Segmented && c.getDebugName().equals("Inspector views"));
                clickView(views, false);
                OreInspector.backend().execute(InspectorCommand.action(InspectorCommand.Operation.PICK));
                var press=new ScreenEvent.MouseButtonPressed.Pre(mc.screen,10,32,0);MinecraftForge.EVENT_BUS.post(press);
                var release=new ScreenEvent.MouseButtonReleased.Pre(mc.screen,10,32,0);MinecraftForge.EVENT_BUS.post(release);
                check(press.isCanceled()&&release.isCanceled()&&clicks==0,"Picker consumes business press/release");
                retainedFontSize = preview.getFontSize();
                ClientMarketScreen.open(); phase=3;
            } else if(phase==3&&target("Market Window")) {
                check(!placeholder(hintInput).isDisposed() && !placeholder(hintArea).isDisposed(), "Temporary MUI detach keeps both cached hints alive");
                marketRoot = OreInspector.backend().inspectedRoot();
                ClientOrganizationScreen.open();phase=4;
            }
            else if(phase==4&&target("Organization Window")) { organizationRoot = OreInspector.backend().inspectedRoot(); ClientMailScreen.open();phase=5; }
            else if(phase==5&&target("Mail Window")) { mailRoot = OreInspector.backend().inspectedRoot(); ClientChatScreen.open("");phase=6; }
            else if(phase==6&&target("Chat Window")) {
                check(OreInspector.backend().snapshot().targets().size()>=4,"All business roots registered separately");
                check(marketRoot.isSuspended() && organizationRoot.isSuspended() && mailRoot.isSuspended(), "Retained business roots are suspended, not detached");
                io.github.luyingyilong.ore.screen.OreScreen.back(); phase = 30;
            } else if (phase == 30 && target("Mail Window")) {
                check(OreInspector.backend().inspectedRoot() == mailRoot && mailRoot.isReady() && !mailRoot.isSuspended(), "Return resumes the original mail tree");
                io.github.luyingyilong.ore.screen.OreScreen.back(); phase = 31;
            } else if (phase == 31 && target("Organization Window")) {
                check(OreInspector.backend().inspectedRoot() == organizationRoot && !organizationRoot.isSuspended(), "Return resumes the original organization tree");
                io.github.luyingyilong.ore.screen.OreScreen.back(); phase = 32;
            } else if (phase == 32 && target("Market Window")) {
                check(OreInspector.backend().inspectedRoot() == marketRoot && !marketRoot.isSuspended(), "Return resumes the original market tree");
                io.github.luyingyilong.ore.screen.OreScreen.back(); phase = 33;
            } else if (phase == 33 && target("Original Window")) {
                check(OreInspector.backend().inspectedRoot() == originalRoot && !originalRoot.isSuspended() && hintInput.isValid(), "Retained native inputs attach again");
                check(preview.getFontSize() == retainedFontSize && !placeholder(hintArea).isDisposed(), "Return preserves effective properties and cached controls");
                System.out.println("GEG_LIFECYCLE_RETURN_CHAIN_PASS");
                originalRoot.queueFree();
                io.github.luyingyilong.ore.layout.LifecycleScheduler.flush();
                check(placeholder(hintInput).isDisposed() && placeholder(hintArea).isDisposed(), "Permanent root disposal releases both cached hints");
                System.out.println("GEG_PLACEHOLDER_LIFETIME_PASS");
                closeNativeInspector();phase=7;
            } else if(phase==7&&io.github.luyingyilong.ore.window.WindowManager.nativeWindows()==0) {
                check(OreInspector.view()==null&&OreInspector.backend()==null,"Native close clears Inspector references");
                var key=new ScreenEvent.KeyPressed.Pre(mc.screen,org.lwjgl.glfw.GLFW.GLFW_KEY_F8,0,0);
                MinecraftForge.EVENT_BUS.post(key);
                check(key.isCanceled()&&OreInspector.view()!=null&&OreInspector.view().window().getHost().isOpen(),"F8 reopens closed native Inspector");
                check(OreInspector.view().window()!=previousWindow&&OreInspector.backend()!=previousBackend,"Reopen creates a fresh session");
                check(mc.options.pauseOnLostFocus==false,"Reopened Inspector coordinates pause setting");
                phase=8;
            } else if(phase==8&&io.github.luyingyilong.ore.window.NativeWindowClientTest.frames(OreInspector.view().window())>=2) {
                if(++reopenCount<3){closeNativeInspector();phase=7;}
                else {OreInspector.stop();phase=9;}
            } else if(phase==9&&io.github.luyingyilong.ore.window.WindowManager.nativeWindows()==0) {
                System.out.println("GEG_NATIVE_INSPECTOR_REOPEN_PASS");
                System.out.println("GEG_NATIVE_INSPECTOR_CLIENT_PASS");phase=99;mc.stop();
            }
        }catch(Throwable error){error.printStackTrace();System.out.println("GEG_NATIVE_INSPECTOR_CLIENT_FAIL");phase=99;mc.stop();}
    }
    private static void subscribeSignalButton() {
        signalButton.pressed().connect(preview, "test purchase", () -> clicks++);
    }
    private static Label placeholder(io.github.luyingyilong.ore.input.Input input) throws ReflectiveOperationException {
        var field = io.github.luyingyilong.ore.input.Input.class.getDeclaredField("placeholderLabel");
        field.setAccessible(true);
        return (Label)field.get(input);
    }
    private static void startHintProbe(String name, io.github.luyingyilong.ore.input.Input input) throws ReflectiveOperationException {
        var backend = OreInspector.backend();
        var parent = backend.snapshot().nodes().stream().filter(n -> n.name().equals(name)).findFirst().orElseThrow();
        var hint = backend.snapshot().nodes().stream().filter(n -> n.parent().equals(parent.id()) && n.name().equals("[internal] placeholder")).findFirst().orElseThrow();
        backend.execute(InspectorCommand.select(hint.id()));
        OreInspector.view().update();
        Segmented views = (Segmented)find(OreInspector.view().window(), c -> c instanceof Segmented && c.getDebugName().equals("Inspector views"));
        if (views.getSelected() != 1) clickView(views, true);
        currentHint = input;
        hintSignalIds = backend.snapshot().signals().data().signals().stream().map(io.github.luyingyilong.ore.signal.SignalDiagnostics.SignalInfo::id).toList();
        check(hintSignalIds.equals(io.github.luyingyilong.ore.signal.SignalDiagnostics.inspect(placeholder(input)).signals().stream()
                .map(io.github.luyingyilong.ore.signal.SignalDiagnostics.SignalInfo::id).toList()), "Inspector observes the actual cached placeholder");
        invalidAt = ticks;
    }
    private static void verifyHintProbe() throws ReflectiveOperationException {
        check(hintSignalIds.equals(OreInspector.backend().snapshot().signals().data().signals().stream()
                .map(io.github.luyingyilong.ore.signal.SignalDiagnostics.SignalInfo::id).toList()), "Real repeated placeholder drawing keeps Inspector signal identities stable");
        check(!placeholder(currentHint).isDisposed(), "Visible placeholder is still live");
    }
    private static void clickView(Segmented views, boolean signals) {
        clickViewIndex(views,signals?1:0);
    }
    private static void clickViewIndex(Segmented views,int expected) {
        Window window = OreInspector.view().window();
        Rect area = io.github.luyingyilong.ore.event.EventGeometry.displayed(views);
        int y=area.y()+area.height()/2, x=area.x();
        while(x<area.right() && views.hitIndex(area.x(),area.y(),area.width(),x,y)!=expected) x++;
        check(x<area.right(),"Requested Inspector view is laid out");
        int end=x; while(end<area.right() && views.hitIndex(area.x(),area.y(),area.width(),end,y)==expected) end++;
        x=(x+end)/2;
        int previous=views.getSelected();
        check(io.github.luyingyilong.ore.event.WindowInputs.press(window, x, y, window.getContext().getMui()), "View press is routed through Window");
        check(views.getSelected() == previous, "View changes only after release");
        check(io.github.luyingyilong.ore.event.WindowInputs.release(window, x, y) && views.getSelected() == expected,
                "Segmented release switches the Inspector view");
    }
    private static void editDraft(String text) {
        liveInput.getStringValue().setStringValue(text);liveInput.setText(text);OreInspector.view().update();
    }
    private static Control find(Control node,java.util.function.Predicate<Control> predicate) {
        if(predicate.test(node))return node;
        for(Control child:node.getChildren()){Control found=find(child,predicate);if(found!=null)return found;}
        return null;
    }
    private static void closeNativeInspector() {
        previousWindow=OreInspector.view().window(); previousBackend=OreInspector.backend();
        long handle=((io.github.luyingyilong.ore.window.NativeWindowHost)previousWindow.getHost()).nativeHandle();
        var callback=org.lwjgl.glfw.GLFW.glfwSetWindowCloseCallback(handle,null);
        check(callback!=null,"Native close callback registered");
        org.lwjgl.glfw.GLFW.glfwSetWindowCloseCallback(handle,callback);
        callback.invoke(handle);
    }
    private static void diagnose(Control control) {
        if (control instanceof Button button) {
            var properties=new java.util.ArrayList<io.github.luyingyilong.ore.debug.DebugProperty>();button.debugProperties(properties);
            properties.stream().filter(p->p.target() instanceof Label).map(p->(Label)p.target()).distinct()
                    .filter(label->label.getText().getString().contains("Restore node")).forEach(label->{ check(label.getBounds().right() <= OreInspector.view().window().getContext().getGuiWidth(),"Property button stays within viewport"); });
        }
        if(control instanceof io.github.luyingyilong.ore.input.InputControl input)
            System.out.println("NATIVE_EDITOR_VALUE "+input.getWidget().getText()+" "+input.getDisplayedBounds());
        control.getChildren().forEach(NativeInspectorClientTest::diagnose);
    }
    private static boolean target(String name) {
        var backend=OreInspector.backend();var target=backend.snapshot().targets().stream().filter(t->t.name().equals(name)).findFirst();
        if(target.isEmpty())return false;
        if (backend.inspectedRoot() == null || !backend.inspectedRoot().getDebugName().equals(name)) {
            backend.execute(new InspectorCommand(InspectorCommand.Operation.TARGET,target.get().id(),-1,"","",false)); return false;
        }
        return !backend.snapshot().nodes().isEmpty() && !backend.snapshot().waiting();
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
