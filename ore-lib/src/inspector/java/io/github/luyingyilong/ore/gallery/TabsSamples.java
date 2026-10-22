package io.github.luyingyilong.ore.gallery;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.display.Label;
import io.github.luyingyilong.ore.control.input.*;
import io.github.luyingyilong.ore.control.layout.Box;
import io.github.luyingyilong.ore.control.layout.Scroll;
import io.github.luyingyilong.ore.control.navigation.TabItem;
import io.github.luyingyilong.ore.control.navigation.Tabs;
import io.github.luyingyilong.ore.render.TextureRegion;
import io.github.luyingyilong.ore.render.TextureSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Retained editors and scroll areas; factories run only when a sample/page is added. */
final class TabsSamples {
    private TabsSamples() { }
    private static Component text(String key, Object... args) { return GalleryView.tr("sample.tabs_" + key, args); }
    private static Label label(String key) { return new Label(text(key)).setWrap(Label.Wrap.WORD); }

    static Control create() {
        Box root = new Box().setGap(6);
        Tabs tabs = new Tabs(); tabs.setPreferred(0, 180);
        tabs.addItem(new TabItem("overview", text("overview"), label("overview_body"),
                TextureSource.region(TextureRegion.full(ResourceLocation.fromNamespaceAndPath("ore", "textures/icons/logo.png"), 128, 128)), false));
        // Keep the sample image at icon scale while still using the gallery logo.
        Input input = new Input().setPlaceholder(text("input_hint"));
        input.setTextNoSignal(text("draft_text").getString());
        TextArea area = new TextArea(0); area.setRows(3); area.setPlaceholder(text("area_hint"));
        NumberInput number = new NumberInput(); number.setTextNoSignal("12+3");
        Box editors = new Box().setGap(4);
        editors.addChild(new InputControl(input)); editors.addChild(new InputControl(area)); editors.addChild(new InputControl(number));
        tabs.addItem(new TabItem("draft", text("draft"), editors));
        Box rows = new Box().setGap(4);
        for (int i = 1; i <= 30; i++) rows.addChild(new Label(text("row", i)));
        tabs.addItem(new TabItem("scroll", text("scroll"), new Scroll().setContent(rows)));
        tabs.addItem(new TabItem("disabled", text("disabled"), label("overview_body"), null, true));
        root.addChild(tabs);

        Box actions = new Box().setOrientation(Box.Orientation.HORIZONTAL).setWrap(true).setGap(4);
        int[] next = {0};
        Button add = new Button(text("add"));
        add.pressed().connect(root, () -> {
            String key = "dynamic_" + ++next[0];
            tabs.addItem(new TabItem(key, text("dynamic", next[0]), label("overview_body")));
            tabs.setSelectedKey(key);
        });
        Button remove = new Button(text("remove"));
        remove.pressed().connect(root, () -> {
            TabItem item = tabs.removeItem("dynamic_" + next[0]);
            if (item != null) {
                item.getContent().queueFree("gallery page removed");
                next[0]--;
            }
        });
        Button move = new Button(text("move"));
        move.pressed().connect(root, () -> { if (tabs.getSelectedKey() != null) tabs.moveItem(tabs.getSelectedKey(), 0); });
        actions.addChild(add); actions.addChild(remove); actions.addChild(move); root.addChild(actions);

        Tabs overflow = new Tabs().setMaxTabWidth(80); overflow.setPreferred(0, 70);
        for (int i = 1; i <= 12; i++) overflow.addItem(new TabItem("long_" + i, text("long_title", i), label("overview_body")));
        root.addChild(overflow);
        Tabs disabled = new Tabs();
        disabled.addItem(new TabItem("locked", text("disabled"), label("overview_body"), null, true));
        root.addChild(disabled);
        return root;
    }
}
