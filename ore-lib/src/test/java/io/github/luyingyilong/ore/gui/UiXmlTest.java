package io.github.luyingyilong.ore.gui;

import io.github.luyingyilong.ore.control.base.*;
import io.github.luyingyilong.ore.control.display.Label;
import io.github.luyingyilong.ore.control.input.Button;
import io.github.luyingyilong.ore.control.layout.*;
import io.github.luyingyilong.ore.control.navigation.Tabs;
import io.github.luyingyilong.ore.event.WindowInputs;
import io.github.luyingyilong.ore.signal.*;
import io.github.luyingyilong.ore.window.Window;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import java.io.StringReader;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public final class UiXmlTest {
    private static final ResourceLocation RESOURCE = ResourceLocation.parse("test:gui/test.xml");
    private static final Font FONT = new Font(id -> null, false) {
        @Override public int width(FormattedText text) { return text.getString().length() * 6; }
    };
    public static void main(String[] args) {
        parser(); schema(); documents(); transactions();
        System.out.println("ORE_XML_PASS");
    }
    private static void parser() {
        var blueprint = UiLoader.parse(RESOURCE, new StringReader("<Box id='root'>\n<Label text='hello'/></Box>"));
        check(blueprint.root().children().get(0).source().line() == 2, "SAX retains line locations");
        expect(() -> blueprint.root().attributes().put("id", "other"));
        expect(() -> UiLoader.parse(RESOURCE, new StringReader("<!DOCTYPE x [<!ENTITY x SYSTEM 'file:///C:/secret'>]><Label text='&x;'/>")));
        expect(() -> UiLoader.parse(RESOURCE, new StringReader("<Box><Label></Box>")));
        // Parser only creates immutable descriptions; unregistered tags are rejected during construction.
    }
    public static class Custom extends Control {
        int amount = 3;
        @UiProperty public int getAmount() { return amount; }
        public Custom setAmount(int amount) { if (amount < 0) throw new IllegalArgumentException("negative amount"); this.amount = amount; return this; }
    }
    public static final class Child extends Custom { }
    private static void schema() {
        var properties = UiPropertySchema.getProperties(Child.class);
        Child child = new Child(); properties.get("amount").set(child, 4);
        check(child.amount == 4 && !properties.containsKey("class"), "Only annotated inherited properties are exposed");
        expect(() -> properties.get("amount").set(child, -1));
        check((int) UiCodecs.parse(int.class, "SIZE_EXPAND|SIZE_SHRINK_CENTER", "sizeFlagsHorizontal") == 6, "Flag combinations");
        expect(() -> UiCodecs.parse(int.class, "SIZE_FILL|SIZE_SHRINK_END", "sizeFlagsHorizontal"));
        expect(() -> UiCodecs.parse(boolean.class, "yes", "visible"));
        expect(() -> UiCodecs.parse(double.class, "NaN", "opacity"));
        expect(() -> UiCodecs.parse(io.github.luyingyilong.ore.render.TextureRegion.class,
                "{\"location\":\"test:image.png\",\"imageWidth\":1.5,\"imageHeight\":8}", "texture"));
        expect(() -> UiCodecs.parse(io.github.luyingyilong.ore.render.TextureRegion.class,
                "{\"location\":16,\"imageWidth\":16,\"imageHeight\":8}", "texture"));
        expect(() -> UiCodecs.parse(io.github.luyingyilong.ore.render.TextureRegion.class,
                "{\"location\":\"test:image.png\",\"imageWidth\":16,\"imageHeight\":8,\"borderX\":2}", "texture"));
        child.queueFree(); LifecycleScheduler.flush();
    }
    private static void documents() {
        AtomicReference<String> xml = new AtomicReference<>("""
                <Panel id="panel"><slot name="child"><Box id="box" gap="4">
                  <Label id="title" text="old"/>
                  <Button id="button" text="send"><connect signal="pressed" handler="send"/></Button>
                </Box></slot></Panel>
                """);
        UiLoader loader = new UiLoader(UiTypeRegistry.createDefault(), id -> new StringReader(xml.get()), Runnable::run);
        SignalScope owner = new SignalScope(); int[] clicks = {0};
        UiBindings bindings = new UiBindings().action("send", () -> clicks[0]++);
        UiDocument doc = loader.open(RESOURCE, bindings, owner);
        UiDocument second = loader.open(RESOURCE, bindings, owner);
        Window window = new Window(false); window.setContent(doc.getRoot());
        try {
            Label title = doc.getRoot().findControlFromId("title", Label.class);
            Button button = doc.getRoot().findControlFromId("button", Button.class);
            check(title != second.getRoot().findControlFromId("title"), "Document scopes allow duplicate IDs");
            check(window.findControlFromId("title") == null, "Outer queries cannot cross document scopes");
            check(button.pressed().connectionCount() == 1, "Exactly one XML subscription");
            window.layoutViewport(200, 100, FONT); click(window, button);
            check(clicks[0] == 1, "Signals connect through Window input");
            xml.set("""
                    <Panel id="panel"><slot name="child"><Box id="box" gap="9">
                      <Button id="button" text="send"><connect signal="pressed" handler="send"/></Button>
                      <Label id="title" text="new"/>
                    </Box></slot></Panel>
                    """);
            doc.reload(); UiRuntime.flush();
            check(doc.getStatus() == UiDocument.Status.READY, doc.getError());
            check(doc.getRoot().findControlFromId("title") == title && title.getText().getString().equals("new"), "Stable identities reuse and update configuration");
            check(doc.getRoot().findControlFromId("box").getChild(0) == button, "Reordering changes real child order");
            check(button.pressed().connectionCount() == 1, "Reload replaces subscriptions");
            window.layoutViewport(200, 100, FONT); click(window, button); check(clicks[0] == 2, "No duplicate dispatch after reload");
            long version = doc.getVersion();
            xml.set("<Box><Label id='same'/><Label id='same'/></Box>"); doc.reload(); UiRuntime.flush();
            check(doc.getStatus() == UiDocument.Status.ERROR && doc.getVersion() == version && doc.getRoot().findControlFromId("title") == title, "Duplicate IDs retain prior document");
            xml.set("<Box gap='bad'/>"); doc.reload(); UiRuntime.flush();
            check(doc.getStatus() == UiDocument.Status.ERROR && doc.getRoot().findControlFromId("title") == title, "Invalid setter preserves tree");
            xml.set("<Button><connect signal='pressed' handler='missing'/></Button>"); doc.reload(); UiRuntime.flush();
            check(doc.getError().contains("missing") && button.pressed().connectionCount() == 1, "Missing handler preserves old connections");
            xml.set("<Button><connect signal='pressed' handler='send'/><connect signal='pressed' handler='send'/></Button>"); doc.reload(); UiRuntime.flush();
            check(doc.getError().contains("Duplicate signal"), "Duplicate declarations rejected");
            xml.set("<Box id='box'/>"); doc.reload();
            try (var guard = DispatchGuard.enter()) { UiRuntime.flush(); check(doc.getRoot().findControlFromId("title") == title, "No mutation in input/signal dispatch"); }
            UiRuntime.flush(); check(title.isQueuedForDeletion(), "Removed content queued only after commit");
            check(doc.getRoot().findControlFromId("title") == null, "Queued IDs immediately disappear");
            check(io.github.luyingyilong.ore.debug.DebugBridge.xml(doc.getRoot()).version() == doc.getVersion(), "XML provenance tracks committed version");
            doc.close(); LifecycleScheduler.flush(); check(button.isDisposed(), "Document disposal clears old sources");
        } finally { owner.close(); window.queueFree(); UiRuntime.flush(); LifecycleScheduler.flush(); }
    }
    private static void transactions() {
        class TransactionRoot extends Container { void run(Runnable action) { transactChildren(action); } }
        TransactionRoot root = new TransactionRoot(); Box a = new Box(), b = new Box();
        Control child = new Control().setId("stable"); root.addChild(a); root.addChild(b); a.addChild(child);
        expect(() -> root.run(() -> { a.removeChild(child); b.addChild(child); child.queueFree(); throw new IllegalArgumentException("rollback"); }));
        check(child.getParent() == a && child.isAlive() && root.findControlFromId("stable") == child, "Rollback restores links, ID index and cancels deferred free");
        root.run(() -> { a.removeChild(child); b.addChild(child); });
        check(child.getParent() == b, "Successful transaction supports cross-parent move");
        root.queueFree(); LifecycleScheduler.flush();
    }
    private static void click(Window w, Control c) { Rect r = c.getAbsoluteBounds(); WindowInputs.press(w, r.x() + 1, r.y() + 1, null); WindowInputs.release(w, r.x() + 1, r.y() + 1); }
    private static void expect(Runnable action) { try { action.run(); } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException expected) { return; } throw new AssertionError("Expected rejection"); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
