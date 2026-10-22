package io.github.luyingyilong.ore.gallery;

import io.github.luyingyilong.ore.control.base.*;
import io.github.luyingyilong.ore.control.input.InputControl;
import io.github.luyingyilong.ore.control.navigation.Tabs;
import io.github.luyingyilong.ore.gui.*;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import java.nio.file.*;

public final class GalleryXmlTest {
    public static void main(String[] args) throws Exception {
        var dist = net.minecraftforge.fml.loading.FMLLoader.class.getDeclaredField("dist"); dist.setAccessible(true); dist.set(null, net.minecraftforge.api.distmarker.Dist.DEDICATED_SERVER);
        var boot = net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped"); boot.setAccessible(true); boot.setBoolean(null, true);
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.core.registries.BuiltInRegistries.bootStrap();
        Font font = new Font(id -> null, false) { @Override public int width(FormattedText text) { return text.getString().length() * 6; } };
        Path resources = Path.of(System.getProperty("ore.resourceRoot")).getParent().getParent().resolve("inspector/resources/assets");
        int[] reads = {0};
        try (var source = UiLoader.getDefault().addResourceOverride(id -> {
            Path file = resources.resolve(id.getNamespace()).resolve(id.getPath());
            if (!Files.isRegularFile(file)) return null;
            reads[0]++; return Files.newBufferedReader(file);
        })) {
            GalleryView view = new GalleryView(); view.select("xml");
            try {
                Control sample = view.getSample();
                Control right = view.getContentScroll().getParent();
                Control statusArea = right.getChild(2);
                Control status = statusArea.getChild(0);
                Control root = sample;
                check(statusArea.getVisible() && right.getChild(3) == view.getContentScroll(),
                        "XML status is mounted between the description and Scroll");
                check(root.findControlFromId("xmlName", InputControl.class) != null && root.findControlFromId("xmlAmount", InputControl.class) != null
                        && root.findControlFromId("xmlDraft", InputControl.class) != null, "All native editor factories are represented");
                Tabs tabs = root.findControlFromId("xmlTabs", Tabs.class); check(tabs.getItems().size() == 3, "XML named item adapter builds both pages");
                tabs.setSelectedKeyNoSignal("controls");
                for (int width : new int[]{127, 128, 129, 480}) {
                    view.setPreviewWidth(width == 480 ? 0 : width); view.getWindow().layoutViewport(width, 260, font);
                    io.github.luyingyilong.ore.gui.dev.UiReloadStatus.tick();
                    check(view.getSample() == sample && root.isAlive() && statusArea.getChild(0) == status,
                            "Narrow layouts and status refresh reuse the document and status Label");
                    check(statusArea.getBounds().y() >= right.getChild(1).getBounds().bottom()
                            && view.getContentScroll().getBounds().y() >= statusArea.getBounds().bottom(),
                            "Fixed XML status remains below description and above preview");
                    var fixed = status.getAbsoluteBounds();
                    view.getContentScroll().setScroll(0, 40);
                    check(status.getAbsoluteBounds().equals(fixed), "Preview scrolling cannot move the XML status");
                }
                view.select("xml"); check(reads[0] == 1, "Ordinary layout and selection do not reread XML");
                check(UiSources.get(root.findControlFromId("xmlSubmit")).line() > 0, "Source location available to Inspector");
                view.setPreviewWidth(0);view.getWindow().layoutViewport(480,400,font);
                var inner=root.findControlFromId("xmlControlsScroll",io.github.luyingyilong.ore.control.layout.Scroll.class);
                inner.setScroll(0,Integer.MAX_VALUE);
                Control open=root.findControlFromId("xmlOpenModal");
                view.getWindow().getFocusManager().requestFocus(open,null);
                var area=io.github.luyingyilong.ore.event.EventGeometry.displayed(open);
                check(view.getWindow().getPointerTarget(area.x()+3,area.y()+3)==open,
                        "Open button visible after navigation: " + area + " inner=" + inner.getViewport() + " scroll=" + inner.getScrollY());
                io.github.luyingyilong.ore.event.WindowInputs.press(view.getWindow(),area.x()+3,area.y()+3,null);
                io.github.luyingyilong.ore.event.WindowInputs.release(view.getWindow(),area.x()+3,area.y()+3);
                check(view.getWindow().getPopupOverlay().getChildCount()==1,"XML button loads the popup document into the actual Window Overlay");
                Control popupRoot=view.getWindow().getPopupOverlay().getChild(0);
                check(view.getWindow().getFocusManager().getInputScope()==popupRoot.findControlFromId("xmlModal"),"XML modal blocks background input");
                view.select("button"); LifecycleScheduler.flush(); check(root.isDisposed(), "Changing sample closes document and all input sessions");
                check(popupRoot.isDisposed(),"Leaving XML example also frees its separate popup document");
                check(status.isDisposed(), "Status Label is cleaned with the example");
                check(!statusArea.getVisible() && statusArea.getChildCount() == 0, "Other examples hide and clear the status slot");
                io.github.luyingyilong.ore.gui.dev.UiReloadStatus.tick(); // Must not query a freed document.
            } finally { view.getWindow().queueFree(); UiRuntime.flush(); LifecycleScheduler.flush(); }
        }
        System.out.println("ORE_GALLERY_XML_PASS");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
