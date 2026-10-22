package io.github.luyingyilong.ore.gallery;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.gui.UiBindings;
import io.github.luyingyilong.ore.gui.UiDocument;
import io.github.luyingyilong.ore.gui.UiLoader;
import io.github.luyingyilong.ore.gui.dev.UiReloadStatus;
import io.github.luyingyilong.ore.control.form.Form;
import io.github.luyingyilong.ore.control.overlay.Drawer;
import io.github.luyingyilong.ore.window.Window;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.resources.ResourceLocation;

/**
 * Real resource + typed bindings; the example has no business requests.
 */
final class XmlSamples {
    private XmlSamples() {
    }

    static GallerySamples.Example create(Window window) {
        Control owner = new Control();
        AtomicReference<UiDocument> component = new AtomicReference<>();
        AtomicReference<UiDocument> popups = new AtomicReference<>();
        java.util.function.Supplier<UiDocument> popupDocument = () -> {
            if (popups.get() == null || popups.get().isClosed()) {
                UiBindings popupBindings = new UiBindings()
                        .action("closeModal", () -> popups.get().getRoot().findControlFromId("xmlModal").setVisible(false))
                        .action("closeDialog", () -> popups.get().getRoot().findControlFromId("xmlDialog").setVisible(false));
                UiDocument loaded = UiLoader.load(ResourceLocation.parse("ore_gallery:gui/overlays.xml"), popupBindings, owner);
                popups.set(loaded);
                window.getPopupOverlay().addChild(loaded.getRoot());
            }
            return popups.get();
        };
        UiBindings bindings = new UiBindings().action("submit", () -> {
                })
                .handler("selection", String.class, ignored -> {
                })
                .action("validateForm", () -> component.get().getRoot().findControlFromId("xmlForm", Form.class).validate())
                .action("resetForm", () -> component.get().getRoot().findControlFromId("xmlForm", Form.class).reset())
                .action("openModal", () -> popupDocument.get().getRoot().findControlFromId("xmlModal").setVisible(true))
                .action("openDialog", () -> popupDocument.get().getRoot().findControlFromId("xmlDialog").setVisible(true))
                .action("openDrawer", () -> popupDocument.get().getRoot().findControlFromId("xmlDrawer", Drawer.class).open())
                .action("showToast", () -> component.get().getRoot().findControlFromId("xmlToast",
                        io.github.luyingyilong.ore.control.overlay.Toast.class).open());
        UiDocument document;
        try {
            document = UiLoader.load(ResourceLocation.parse("ore_gallery:gui/components.xml"), bindings, owner);
            component.set(document);
        } catch (RuntimeException failure) {
            owner.queueFree();
            throw failure;
        }
        Control root = document.getRoot();
        root.getSignalScope().own(() -> owner.queueFree("XML Gallery example closed"));
        // Gallery mounts status above its Scroll, separately from the reloadable document.
        return new GallerySamples.Example(root, new UiReloadStatus(document).getControl());
    }
}
