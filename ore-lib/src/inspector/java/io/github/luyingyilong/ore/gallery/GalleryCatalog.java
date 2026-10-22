package io.github.luyingyilong.ore.gallery;

import io.github.luyingyilong.ore.control.base.Container;
import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.data.PlayerIdentityCell;
import io.github.luyingyilong.ore.control.data.Table;
import io.github.luyingyilong.ore.control.data.Tree;
import io.github.luyingyilong.ore.control.display.Badge;
import io.github.luyingyilong.ore.control.display.DescriptionItem;
import io.github.luyingyilong.ore.control.display.Descriptions;
import io.github.luyingyilong.ore.control.display.Label;
import io.github.luyingyilong.ore.control.display.Tag;
import io.github.luyingyilong.ore.control.display.TextureRect;
import io.github.luyingyilong.ore.control.display.avatar.Avatar;
import io.github.luyingyilong.ore.control.form.Form;
import io.github.luyingyilong.ore.control.form.FormItem;
import io.github.luyingyilong.ore.control.input.Button;
import io.github.luyingyilong.ore.control.input.Checkbox;
import io.github.luyingyilong.ore.control.input.Dropdown;
import io.github.luyingyilong.ore.control.input.Radio;
import io.github.luyingyilong.ore.control.input.Segmented;
import io.github.luyingyilong.ore.control.input.Slider;
import io.github.luyingyilong.ore.control.input.Switch;
import io.github.luyingyilong.ore.control.inventory.Inventory;
import io.github.luyingyilong.ore.control.inventory.Slot;
import io.github.luyingyilong.ore.control.layout.Box;
import io.github.luyingyilong.ore.control.layout.Collapse;
import io.github.luyingyilong.ore.control.layout.Divider;
import io.github.luyingyilong.ore.control.layout.Grid;
import io.github.luyingyilong.ore.control.layout.Padding;
import io.github.luyingyilong.ore.control.layout.Panel;
import io.github.luyingyilong.ore.control.layout.Scroll;
import io.github.luyingyilong.ore.control.layout.Splitter;
import io.github.luyingyilong.ore.control.navigation.Menu;
import io.github.luyingyilong.ore.control.navigation.Navbar;
import io.github.luyingyilong.ore.control.overlay.Dialog;
import io.github.luyingyilong.ore.control.overlay.Drawer;
import io.github.luyingyilong.ore.control.overlay.Modal;
import io.github.luyingyilong.ore.control.overlay.Overlay;
import io.github.luyingyilong.ore.control.overlay.Toast;
import io.github.luyingyilong.ore.control.progress.ProgressBar;
import io.github.luyingyilong.ore.control.progress.Spin;
import io.github.luyingyilong.ore.control.progress.TextureProgressBar;
import io.github.luyingyilong.ore.window.Window;

import io.github.luyingyilong.ore.control.input.Input;
import io.github.luyingyilong.ore.control.input.InputControl;
import io.github.luyingyilong.ore.control.input.NumberInput;
import io.github.luyingyilong.ore.control.input.TextArea;

import java.util.List;
import java.util.Set;

/**
 * Explicit catalogue: adding a public component requires a sample or an exclusion reason.
 */
public final class GalleryCatalog {
    private static final List<Entry> ENTRIES = List.of(
            entry("xml", "layout", "XML UI"),
            entry("box", "layout", "Box", Box.class), entry("padding", "layout", "Padding", Padding.class),
            entry("panel", "layout", "Panel", Panel.class), entry("scroll", "layout", "Scroll", Scroll.class),
            entry("grid", "layout", "Grid", Grid.class), entry("splitter", "layout", "Splitter", Splitter.class),
            entry("window", "layout", "Window / Control", Window.class, Control.class, Container.class),
            entry("label", "display", "Label", Label.class), entry("texture", "display", "TextureRect", TextureRect.class),
            entry("avatar", "display", "Avatar", Avatar.class), entry("slot", "display", "Slot", Slot.class),
            entry("progress", "display", "ProgressBar", ProgressBar.class),
            entry("texture_progress", "display", "TextureProgressBar", TextureProgressBar.class),
            entry("inventory", "data", "Inventory", Inventory.class),
            entry("containers", "data", "Chest / Furnace"),
            entry("tag", "display", "Tag", Tag.class), entry("badge", "display", "Badge", Badge.class),
            entry("spin", "display", "Spin", Spin.class), entry("divider", "display", "Divider", Divider.class),
            entry("descriptions", "display", "Descriptions", Descriptions.class, DescriptionItem.class),
            entry("button", "actions", "Button", Button.class), entry("checkbox", "actions", "Checkbox", Checkbox.class),
            entry("radio", "actions", "Radio / RadioGroup", Radio.class, io.github.luyingyilong.ore.gui.UiRadioGroup.class), entry("switch", "actions", "Switch", Switch.class), entry("segmented", "actions", "Segmented", Segmented.class),
            entry("slider", "input", "Slider", Slider.class),
            entry("dropdown", "actions", "Dropdown", Dropdown.class), entry("menu", "actions", "Menu", Menu.class),
            entry("navbar", "actions", "Navbar", Navbar.class), entry("collapse", "actions", "Collapse", Collapse.class),
            entry("tabs", "actions", "Tabs", io.github.luyingyilong.ore.control.navigation.Tabs.class,
                    io.github.luyingyilong.ore.control.navigation.TabItem.class),
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

