package io.github.luyingyilong.ore.gui;

import io.github.luyingyilong.ore.control.base.*;
import io.github.luyingyilong.ore.control.display.Label;
import io.github.luyingyilong.ore.control.layout.Box;
import io.github.luyingyilong.ore.control.navigation.Tabs;
import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.signal.*;
import net.minecraft.resources.ResourceLocation;
import java.io.StringReader;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public final class UiReloadTest {
    private static final ResourceLocation RESOURCE = ResourceLocation.parse("test:gui/reload.xml");
    public static final class Guard extends Control {
        boolean locked;
        int amount;
        @UiProperty public int getAmount() { return amount; }
        public Guard setAmount(int next) { if (locked && next == 2) throw new IllegalArgumentException("attached guard rejected"); amount = next; return this; }
    }
    public static void main(String[] args) { rollback(); configuration(); latest(); typedSignals(); detachedAndQueued(); compositeItems(); System.out.println("ORE_XML_RELOAD_PASS"); }
    private static void rollback() {
        AtomicReference<String> xml = new AtomicReference<>("<Box id='root'><Label id='label' text='old'/><test:guard id='guard' amount='1'/><Tabs id='tabs'><item key='a' text='A'><Box id='page'/></item></Tabs></Box>");
        UiTypeRegistry registry = UiTypeRegistry.createDefault().register("test:guard", node -> new Guard(), node -> "", Map.of());
        UiLoader loader = new UiLoader(registry, id -> new StringReader(xml.get()), Runnable::run);
        try (SignalScope owner = new SignalScope(); UiDocument doc = loader.open(RESOURCE, new UiBindings(), owner)) {
            Guard guard = doc.getRoot().findControlFromId("guard", Guard.class); guard.locked = true;
            Control page = doc.getRoot().findControlFromId("page"); Tabs tabs = doc.getRoot().findControlFromId("tabs", Tabs.class);
            xml.set(xml.get().replace("text='old'", "text='new'").replace("amount='1'", "amount='2'"));
            doc.reload(); UiRuntime.flush();
            check(doc.getStatus() == UiDocument.Status.ERROR && guard.amount == 1, "Setter failure restores previous configuration");
            check(doc.getRoot().findControlFromId("label", Label.class).getText().getString().equals("old"), "Previously written properties roll back");
            check(tabs.getItem("a").getContent() == page && page.isAlive() && page.getParent() != null, "Composite state and page ownership roll back");
        } finally { LifecycleScheduler.flush(); }
    }
    private static void configuration() {
        AtomicReference<String> xml = new AtomicReference<>("<Box id='root' gap='7'><Label id='label' text='old'><theme key='Label:font_color' value='#123456'/></Label></Box>");
        UiLoader loader = new UiLoader(UiTypeRegistry.createDefault(), id -> new StringReader(xml.get()), Runnable::run);
        try (SignalScope owner = new SignalScope(); UiDocument doc = loader.open(RESOURCE, new UiBindings(), owner)) {
            Box root = doc.getRoot().findControlFromId("root", Box.class); Label label = doc.getRoot().findControlFromId("label", Label.class);
            DebugBridge.install(new DebugBridge.Backend() { @Override public Object value(Control control, String key, Object base) { return control == root && key.equals("gap") ? 19 : base; } });
            xml.set("<Box id='root'><Label id='label' text='next'/></Box>"); doc.reload(); UiRuntime.flush();
            check(doc.getStatus() == UiDocument.Status.READY, doc.getError());
            check(root.getGap() == 19 && DebugBridge.readBase(root::getGap) == 0, "Removing XML configuration restores construction default below Inspector override");
            check(label.getThemeOverride(Label.FONT_COLOR) == null, "Removing theme override restores inheritance");
            check(doc.getRoot().findControlFromId("root") == root, "Inspector identity survives reuse");
        } finally { DebugBridge.install(null); LifecycleScheduler.flush(); }
    }
    private static void latest() {
        AtomicReference<String> xml = new AtomicReference<>("<Label id='label' text='first'/>");
        List<Runnable> jobs = new ArrayList<>();
        UiLoader loader = new UiLoader(UiTypeRegistry.createDefault(), id -> new StringReader(xml.get()), jobs::add);
        try (SignalScope owner = new SignalScope(); UiDocument doc = loader.open(RESOURCE, new UiBindings(), owner)) {
            doc.reload(); doc.reload(); xml.set("<Label id='label' text='latest'/>");
            jobs.get(1).run(); UiRuntime.flush(); jobs.get(0).run(); UiRuntime.flush();
            check(doc.getRoot().findControlFromId("label", Label.class).getText().getString().equals("latest") && doc.getVersion() == 2, "Stale results cannot overwrite latest commit");
            doc.reload(); doc.close(); jobs.get(2).run(); UiRuntime.flush(); check(doc.isClosed(), "Closed documents ignore queued results");
        } finally { LifecycleScheduler.flush(); }
    }
    private static void typedSignals() {
        AtomicReference<String> xml = new AtomicReference<>("<Switch><connect signal='toggled' handler='boolean'/></Switch>");
        UiLoader loader = new UiLoader(UiTypeRegistry.createDefault(), id -> new StringReader(xml.get()), Runnable::run);
        try (SignalScope owner = new SignalScope()) {
            UiBindings valid = new UiBindings().handler("boolean", Boolean.class, value -> { });
            try (UiDocument ignored = loader.open(RESOURCE, valid, owner)) { }
            reject(() -> loader.open(RESOURCE, new UiBindings().handler("boolean", String.class, value -> { }), owner));
            reject(() -> loader.open(RESOURCE, new UiBindings().action("boolean", () -> { }), owner));
            xml.set("<Dropdown id='drop'><item value='a' text='A'/><connect signal='selectionChanged' handler='string'/></Dropdown>");
            try (UiDocument ignored = loader.open(RESOURCE, new UiBindings().handler("string", String.class, value -> { }), owner)) { }
        } finally { LifecycleScheduler.flush(); }
    }
    private static void detachedAndQueued() {
        AtomicReference<String> xml = new AtomicReference<>("<Box id='root'><Label id='label' text='old'/></Box>");
        UiLoader loader = new UiLoader(UiTypeRegistry.createDefault(), id -> new StringReader(xml.get()), Runnable::run);
        try (SignalScope owner = new SignalScope(); UiDocument document = loader.open(RESOURCE, new UiBindings(), owner)) {
            Control label = document.getRoot().findControlFromId("label"); Control parking = new Control();
            label.getParent().removeChild(label); parking.addChild(label);
            document.reload(); UiRuntime.flush();
            check(document.getStatus() == UiDocument.Status.READY && document.getRoot().findControlFromId("label") != label, "Reload does not steal a node moved outside its document scope");
            check(label.getParent() == parking && label.isAlive(), "External ownership is preserved");
            Control orphan = new Control(); document.getRoot().findControlFromId("root").addChild(orphan);
            Control queued = document.getRoot().findControlFromId("label"); queued.queueFree();
            document.reload(); UiRuntime.flush();
            check(document.getStatus() == UiDocument.Status.READY && document.getRoot().findControlFromId("label") != queued, "Queued nodes are replaced without reviving or silencing closed scopes");
            check(orphan.isQueuedForDeletion(), "XML replacement explicitly queues discarded Java-added branches");
            parking.queueFree();
        } finally { LifecycleScheduler.flush(); }
    }
    private static void compositeItems() {
        AtomicReference<String> xml = new AtomicReference<>("<Box id='root'><Menu id='menu'><item key='one' text='Old'/><item key='two' text='Two'/></Menu><Descriptions id='descriptions'><item key='a' label='A' text='old'/><item key='b' label='B' text='value'/></Descriptions><test:guard id='guard' amount='1'/></Box>");
        UiLoader loader = new UiLoader(UiTypeRegistry.createDefault().register("test:guard", node -> new Guard(), node -> "", Map.of()), id -> new StringReader(xml.get()), Runnable::run);
        try (SignalScope owner = new SignalScope(); UiDocument document = loader.open(RESOURCE, new UiBindings(), owner)) {
            var menu = document.getRoot().findControlFromId("menu", io.github.luyingyilong.ore.control.navigation.Menu.class);
            var descriptions = document.getRoot().findControlFromId("descriptions", io.github.luyingyilong.ore.control.display.Descriptions.class);
            Control button = menu.getButton("one"), item = descriptions.getItem("a"); menu.setSelectedNoSignal("two");
            xml.set(xml.get().replace("text='Old'", "text='New'").replace("text='old'", "text='new'"));
            document.reload(); UiRuntime.flush();
            check(document.getStatus() == UiDocument.Status.READY, document.getError());
            check(document.getRoot().findControlFromId("menu") == menu && menu.getButton("one") == button && menu.getSelectedKey().equals("two"), "Menu reuses its root and keyed buttons");
            check(document.getRoot().findControlFromId("descriptions") == descriptions && descriptions.getItem("a") == item, "Descriptions retains keyed row controls");
            Guard guard = document.getRoot().findControlFromId("guard", Guard.class); guard.locked = true;
            xml.set(xml.get().replace("amount='1'", "amount='2'").replace("text='New'", "text='Rejected'")); document.reload(); UiRuntime.flush();
            check(document.getStatus() == UiDocument.Status.ERROR && menu.getButton("one") == button && menu.getButton("one").getLabel().getString().equals("New"), "Composite rollback retains live keyed button state");
            check(menu.getButton("one").pressed().connectionCount() == 1, "Rollback restores exactly one Menu binding");
        } finally { LifecycleScheduler.flush(); }
    }

    private static void reject(Runnable action) { try { action.run(); } catch (IllegalArgumentException expected) { return; } throw new AssertionError("Expected rejection"); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
