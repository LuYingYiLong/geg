package io.github.luyingyilong.ore.gallery;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.LifecycleScheduler;
import io.github.luyingyilong.ore.control.navigation.Tabs;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

import java.nio.file.Files;
import java.nio.file.Path;

public final class GalleryTabsTest {
    public static void main(String[] args) throws Exception {
        // No Minecraft instance exists here: MUI must use its default font height, not query a client singleton.
        var dist = net.minecraftforge.fml.loading.FMLLoader.class.getDeclaredField("dist");
        dist.setAccessible(true); dist.set(null, net.minecraftforge.api.distmarker.Dist.DEDICATED_SERVER);
        // MUI's input widgets initialize vanilla registries even in this snapshot-only test process.
        var boot = net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");
        boot.setAccessible(true); boot.setBoolean(null, true);
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.core.registries.BuiltInRegistries.bootStrap();
        Font font = new Font(id -> null, false) {
            @Override public int width(FormattedText text) { return text.getString().length() * 6; }
        };
        GalleryView view = new GalleryView(); view.select("tabs");
        try {
            var original = view.getSample(); int count = count(original);
            check(count == 3, "Tabs gallery includes retained editors, overflow and all-disabled examples");
            for (int width : new int[]{127, 128, 129, 480}) {
                view.setPreviewWidth(width == 480 ? 0 : width); view.getWindow().layoutViewport(width, 240, font);
                check(view.getSample() == original && count(original) == count, "Resizing reuses every Tabs example");
            }
            Path languages = Path.of(System.getProperty("ore.resourceRoot")).getParent().getParent()
                    .resolve("inspector/resources/assets/ore_gallery/lang");
            for (String language : new String[]{"zh_cn", "en_us"}) {
                var json = com.google.gson.JsonParser.parseString(Files.readString(languages.resolve(language + ".json"))).getAsJsonObject();
                check(json.has("gallery.ore.description.tabs") && json.has("gallery.ore.sample.tabs_area_hint")
                        && json.has("gallery.ore.sample.tabs_remove"), "Localized description and action text");
            }
            view.select("tabs"); check(view.getSample() == original, "Selecting the same example does not rebuild its pages");
            view.select("button"); check(original.isQueuedForDeletion(), "Replacing the sample queues the complete old subtree");
            LifecycleScheduler.flush(); check(original.isDisposed(), "The old page set and parts are permanently cleaned");
            view.select("tabs"); check(view.getSample() != original && count(view.getSample()) == 3, "Returning builds a fresh example session");
        } finally { view.getWindow().queueFree(); LifecycleScheduler.flush(); }
        System.out.println("ORE_GALLERY_TABS_PASS");
    }
    private static int count(Control root) { return (root instanceof Tabs ? 1 : 0) + root.getChildren().stream().mapToInt(GalleryTabsTest::count).sum(); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
