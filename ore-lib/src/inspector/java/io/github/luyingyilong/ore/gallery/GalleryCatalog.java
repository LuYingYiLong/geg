package io.github.luyingyilong.ore.gallery;

import io.github.luyingyilong.ore.*;
import io.github.luyingyilong.ore.input.Input;
import io.github.luyingyilong.ore.input.InputControl;
import io.github.luyingyilong.ore.input.NumberInput;
import io.github.luyingyilong.ore.input.TextArea;
import io.github.luyingyilong.ore.layout.*;

import java.util.List;
import java.util.Set;

/**
 * Explicit catalogue: adding a public component requires a sample or an exclusion reason.
 */
public final class GalleryCatalog {
    private static final List<Entry> ENTRIES = List.of(
            entry("box", "layout", "Box", Box.class), entry("padding", "layout", "Padding", Padding.class),
            entry("panel", "layout", "Panel", Panel.class), entry("scroll", "layout", "Scroll", Scroll.class),
            entry("grid", "layout", "Grid", Grid.class), entry("splitter", "layout", "Splitter", Splitter.class),
            entry("window", "layout", "Window / Control", Window.class, Control.class, Container.class),
            entry("label", "display", "Label", Label.class), entry("texture", "display", "TextureRect", TextureRect.class),
            entry("avatar", "display", "Avatar", Avatar.class), entry("slot", "display", "Slot", Slot.class),
            entry("tag", "display", "Tag", Tag.class), entry("badge", "display", "Badge", Badge.class),
            entry("spin", "display", "Spin", Spin.class), entry("divider", "display", "Divider", Divider.class),
            entry("descriptions", "display", "Descriptions", Descriptions.class, DescriptionItem.class),
            entry("button", "actions", "Button", Button.class), entry("checkbox", "actions", "Checkbox", Checkbox.class),
            entry("switch", "actions", "Switch", Switch.class), entry("segmented", "actions", "Segmented", Segmented.class),
            entry("dropdown", "actions", "Dropdown", Dropdown.class), entry("menu", "actions", "Menu", Menu.class),
            entry("navbar", "actions", "Navbar", Navbar.class),
            entry("input", "input", "Input", Input.class, InputControl.class), entry("number", "input", "NumberInput", NumberInput.class),
            entry("textarea", "input", "TextArea", TextArea.class), entry("form", "input", "Form", Form.class, FormItem.class),
            entry("table", "data", "Table / PlayerIdentityCell", Table.class, PlayerIdentityCell.class),
            entry("tree", "data", "Tree", Tree.class), entry("dialog", "overlays", "Dialog", Dialog.class),
            entry("modal", "overlays", "Modal / Overlay", Modal.class, Overlay.class), entry("drawer", "overlays", "Drawer", Drawer.class),
            entry("toast", "overlays", "Toast", Toast.class));

    private GalleryCatalog() {
    }

    private static Entry entry(String id, String group, String title, Class<?>... types) {
        return new Entry(id, group, title, Set.of(types));
    }

    public static List<Entry> getEntries() {
        return ENTRIES;
    }

    public record Entry(String id, String group, String title, Set<Class<?>> types) {
    }
}

