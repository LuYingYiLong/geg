package io.github.luyingyilong.ore.gallery;

import io.github.luyingyilong.ore.control.base.Container;
import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.control.base.Size;
import io.github.luyingyilong.ore.control.base.SizeFlags;
import io.github.luyingyilong.ore.control.data.PlayerIdentityCell;
import io.github.luyingyilong.ore.control.data.Table;
import io.github.luyingyilong.ore.control.data.Tree;
import io.github.luyingyilong.ore.control.display.Badge;
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
import io.github.luyingyilong.ore.control.input.RadioGroup;
import io.github.luyingyilong.ore.control.input.Segmented;
import io.github.luyingyilong.ore.control.input.Slider;
import io.github.luyingyilong.ore.control.input.Switch;
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
import io.github.luyingyilong.ore.control.navigation.MenuItem;
import io.github.luyingyilong.ore.control.navigation.Navbar;
import io.github.luyingyilong.ore.control.overlay.Dialog;
import io.github.luyingyilong.ore.control.overlay.Drawer;
import io.github.luyingyilong.ore.control.overlay.Modal;
import io.github.luyingyilong.ore.control.overlay.Toast;
import io.github.luyingyilong.ore.control.progress.Spin;
import io.github.luyingyilong.ore.window.Window;

import io.github.luyingyilong.ore.control.form.FieldKey;
import io.github.luyingyilong.ore.control.form.FormFields;
import io.github.luyingyilong.ore.control.form.FormValues;
import io.github.luyingyilong.ore.control.form.validation.Validators;
import io.github.luyingyilong.ore.control.input.Input;
import io.github.luyingyilong.ore.control.input.InputControl;
import io.github.luyingyilong.ore.control.input.NumberInput;
import io.github.luyingyilong.ore.control.input.TextArea;
import io.github.luyingyilong.ore.render.TextureRegion;
import io.github.luyingyilong.ore.render.TextureSource;
import io.github.luyingyilong.ore.control.overlay.toast.OreToasts;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * Factories execute only on selection, never in a draw callback. All data is local.
 */
final class GallerySamples {
    private static final TextureSource LOGO = TextureSource.region(TextureRegion.full(
            ResourceLocation.fromNamespaceAndPath("ore", "textures/icons/logo.png"), 128, 128));

    private GallerySamples() {
    }

    private static Component text(String key, Object... args) {
        return GalleryView.tr("sample." + key, args);
    }

    private static Label label(String key, Object... args) {
        return new Label(text(key, args)).setWrap(Label.Wrap.WORD);
    }

    private static Button button(String key, Object... args) {
        Button button = new Button(text(key, args));
        button.setDebugName("Gallery action " + key);
        return button;
    }

    private static Button button(String key, Runnable run) {
        Button button = button(key);
        button.pressed().connect(button, run);
        return button;
    }

    private static Box column() {
        return new Box().setGap(6);
    }

    private static Box row() {
        return new Box().setOrientation(Box.Orientation.HORIZONTAL).setWrap(true).setGap(4);
    }

    static Control create(String id, Window window) {
        Box root = column();
        switch (id) {
            case "inventory" -> { return InventorySamples.inventory(); }
            case "progress" -> { return InventorySamples.progress(); }
            case "texture_progress" -> { return TextureProgressSamples.create(); }
            case "containers" -> { return InventorySamples.containers(); }
            case "tabs" -> { return TabsSamples.create(); }
            case "box" -> {
                Box flow = row().setGap(4).setLineGap(8);
                for (int i = 1; i <= 7; i++) {
                    Button item = button("button_number", i);
                    item.setMinimum(48, 22);
                    item.setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL);
                    flow.addChild(item);
                }
                Checkbox wrap = new Checkbox(text("wrap")).setCheckedNoSignal(true);
                wrap.toggled().connect(root, flow::setWrap);
                root.addChild(wrap);
                root.addChild(flow);
                Box vertical = new Box().setWrap(true).setGap(2).setLineGap(8);
                for (int i = 0; i < 6; i++)
                    vertical.addChild(new Tag(text("vertical_item", i)).setType(Tag.Type.INFORMATIVE));
                vertical.setPreferred(0, 60);
                // A fixed-height panel demonstrates vertical flow instead of unbounded vertical measurement.
                root.addChild(new FixedViewport(vertical, 0, 60));
            }
            case "padding" ->
                    root.addChild(new Panel().setChild(new Padding(new Padding.Insets(8, 16, 8, 16)).setChild(label("padding_insets", 8, 16, 8, 16))));
            case "panel" ->
                    root.addChild(new Panel().setChild(new Padding(new Padding.Insets(8, 8, 8, 8)).setChild(label("panel_content"))));
            case "scroll" -> {
                Box longContent = column();
                for (int i = 0; i < 40; i++) {
                    Label line = label("scroll_row", i);
                    line.setMinimum(240, 12);
                    longContent.addChild(line);
                }
                Scroll scroll = new Scroll().setContent(longContent).setEmbedded(false);
                root.addChild(new FixedViewport(scroll, 0, 160));
                Checkbox embedded = new Checkbox(text("embedded"));
                embedded.toggled().connect(root, scroll::setEmbedded);
                root.addChild(embedded);
            }
            case "splitter" -> root.addChild(new FixedViewport(new Splitter(new Panel().setChild(label("first_pane")),
                    new Panel().setChild(label("second_pane"))), 0, 120));
            case "grid" -> {
                Grid<Integer> grid = new Grid<Integer>().setColumns(48, 4, 4).setAdapter(new Grid.Adapter<>() {
                    public int getSize() {
                        return 300;
                    }

                    public Integer getItem(int index) {
                        return index;
                    }

                    public Object getKey(Integer item) {
                        return item;
                    }

                    public int measureHeight(Integer item, int width, net.minecraft.client.gui.Font font) {
                        return 24 + item % 3 * 12;
                    }

                    public Grid.View<Integer> createView() {
                        return new Card();
                    }
                });
                root.addChild(new FixedViewport(new Scroll().setContent(grid), 0, 180));
            }
            case "window" -> {
                root.addChild(label("window_content"));
                Control free = new Control();
                free.setPreferred(120, 60);
                Button moved = button("local_position");
                moved.setPosition(8, 8).setRequestedSize(100, 22);
                free.addChild(moved);
                root.addChild(free);
            }
            case "label" -> {
                root.addChild(TextSamples.create());
                root.addChild(label("mixed_text"));
                root.addChild(new Label(text("hero_title")).setHero(true));
                root.addChild(new Label(text("marquee_text"))
                        .setOverflow(Label.Overflow.MARQUEE));
                root.addChild(new Label(text("ellipsis_text")).setOverflow(Label.Overflow.ELLIPSIS));
            }
            case "texture" -> {
                for (TextureRect.StretchMode mode : TextureRect.StretchMode.values()) {
                    root.addChild(label("stretch_mode." + mode.name().toLowerCase(Locale.ROOT)));
                    TextureRect image = new TextureRect().setTexture(LOGO)
                            .setStretchMode(mode).setExpandMode(TextureRect.ExpandMode.IGNORE_SIZE).setOpacity(0.65F);
                    root.addChild(new FixedViewport(image, 160, 144));
                }
            }
            case "avatar" -> {
                Box avatars = row();
                avatars.addChild(new Avatar().setInitials("Alex"));
                avatars.addChild(new Avatar().setInitials(text("avatar_player").getString()));
                root.addChild(avatars);
            }
            case "slot" -> {
                Box slots = row();
                slots.addChild(new Slot().setItem(new ItemStack(Items.DIAMOND), 16));
                slots.addChild(new Slot().setBackgroundVisible(false).setItem(new ItemStack(Items.IRON_INGOT), 64));
                root.addChild(slots);
            }
            case "tag" -> {
                Box tags = row();
                for (Tag.Type type : Tag.Type.values())
                    tags.addChild(new Tag(text("tag_type." + type.name().toLowerCase(Locale.ROOT))).setType(type).setOutlined(true));
                root.addChild(tags);
            }
            case "badge" -> {
                Box badges = row();
                for (long n : new long[]{0, 1, 9, 42, 100}) badges.addChild(new Badge(n));
                badges.addChild(new Badge().setType(Tag.Type.WARNING));
                root.addChild(badges);
            }
            case "spin" -> root.addChild(new Spin());
            case "slider" -> {
                root.addChild(label("slider_single"));
                Slider single = new Slider().setValueNoSignal(35).setShowTicks(true).setStepNoSignal(5);
                root.addChild(single);
                root.addChild(label("slider_range"));
                root.addChild(new Slider().setRangeNoSignal(true).setRangeValueNoSignal(new Slider.RangeValue(25, 75))
                        .setMarks(java.util.Map.of(0.0, text("slider_min"), 50.0, text("slider_middle"), 100.0, text("slider_max"))));
                root.addChild(label("slider_reverse"));
                root.addChild(new Slider().setValueNoSignal(65).setReverse(true));
                root.addChild(label("slider_decimal"));
                root.addChild(new Slider().setLimitsNoSignal(-1, 1).setStepNoSignal(0.1).setValueNoSignal(0.3).setShowTicks(true));
                root.addChild(label("slider_disabled"));
                root.addChild(new Slider().setRangeNoSignal(true).setRangeValueNoSignal(new Slider.RangeValue(20, 80)).setDisabled(true));
                FieldKey<Double> volume = new FieldKey<>("volume", Double.class);
                FieldKey<Slider.RangeValue> interval = new FieldKey<>("interval", Slider.RangeValue.class);
                Form form = new Form();
                form.addItem(new FormItem<>(volume, text("slider_form_value"), FormFields.slider(new Slider().setValueNoSignal(40)))
                        .addValidator(value -> value >= 20 ? java.util.Optional.empty() : java.util.Optional.of(text("slider_form_error"))));
                form.addItem(new FormItem<>(interval, text("slider_form_range"), FormFields.sliderRange(new Slider().setRangeNoSignal(true)
                        .setRangeValueNoSignal(new Slider.RangeValue(20, 60)))));
                form.setVisible(true);
                root.addChild(form);
                root.addChild(button("validate", () -> form.validate()));
                root.addChild(button("reset", form::reset));
            }
            case "divider" -> {
                root.addChild(label("horizontal_divider"));
                root.addChild(new Divider());
                Box split = row();
                split.addChild(label("left"));
                split.addChild(new FixedViewport(new Divider().setLayout(Divider.Layout.VERTICAL), 1, 24));
                split.addChild(label("right"));
                root.addChild(split);
            }
            case "descriptions" -> root.addChild(new Descriptions().setColumns(2)
                    .addItem("name", text("name"), text("library_name"))
                    .addItem("description", text("description"), text("library_description"))
                    .addItem("policy", text("policy"), text("local_demo")));
            case "button" -> {
                Box buttons = row();
                for (String type : Button.BUILTIN_VARIATIONS) {
                    Button button = button("button_type." + type.replace("Button", "").toLowerCase(Locale.ROOT)).setThemeTypeVariation(type);
                    buttons.addChild(button);
                }
                root.addChild(buttons);
                root.addChild(new Button(text("disabled")).setDisabled(true));
                Button toggle = new Button(text("toggle")).setToggleButton(true).setBadge(new Badge(3));
                root.addChild(toggle);
                Button icon = button("icon").setIcon(LOGO);
                icon.getIconControl().setExpandMode(TextureRect.ExpandMode.IGNORE_SIZE)
                        .setStretchMode(TextureRect.StretchMode.KEEP_ASPECT_CENTERED).setMinimum(16, 16);
                root.addChild(icon);
            }
            case "collapse" -> {
                Box contents=column();
                contents.addChild(label("collapse_body"));
                contents.addChild(new InputControl(new Input().setPlaceholder(GalleryView.tr("input_hint"))));
                contents.addChild(button("button_type.secondary"));
                Collapse normal=new Collapse(text("collapse_title")).setSecondaryText(text("collapse_secondary")).setContent(contents);
                normal.setDebugName("Gallery Collapse");root.addChild(normal);
                root.addChild(new Collapse(text("disabled")).setContent(label("collapse_body")).setExpandedNoSignal(true).setDisabled(true));
                root.addChild(new Collapse(text("collapse_long_title")).setSecondaryText(text("collapse_secondary")).setContent(label("collapse_body")).setShowArrow(false));
                root.addChild(new Collapse(text("collapse_nested")).setContent(new Collapse(text("collapse_title")).setContent(label("collapse_body"))));
            }
            case "checkbox" -> {
                Checkbox box = new Checkbox(text("checkbox_option"));
                root.addChild(box);
                root.addChild(new Checkbox(text("disabled")).setEnabled(false));
            }
            case "radio" -> {
                root.addChild(new Radio(text("radio_standalone")));
                Radio disabled=new Radio(text("disabled")).setCheckedNoSignal(true).setDisabled(true);
                root.addChild(disabled);
                Box options=row();
                RadioGroup<Flavor> group=new RadioGroup<>(root,Flavor.class);
                Radio first=new Radio(text("option_one")),second=new Radio(text("option_two"));
                group.addRadio(Flavor.FIRST,first).addRadio(Flavor.SECOND,second);
                options.addChild(first);options.addChild(second);
                Form form=new Form().addItem(new FormItem<>(new FieldKey<>("radio",Flavor.class),text("radio_choice"),
                        FormFields.radioGroup(Flavor.class,options,group)).addValidator(Validators.required()));
                form.setVisible(true);root.addChild(form);
                Box actions=row();actions.addChild(button("validate",form::validate));actions.addChild(button("reset",form::reset));root.addChild(actions);
                Box angles=row();
                for(float angle:new float[]{0,45,90}) {
                    Box sample=column();sample.addChild(label("radio_angle",(int)angle));
                    Radio radio=new Radio();radio.setThemeOverride(Radio.ICON_ROTATION_DEGREES,angle);
                    sample.addChild(radio);angles.addChild(sample);
                }
                root.addChild(angles);
            }
            case "switch" -> {
                Switch control = new Switch();
                root.addChild(control);
                root.addChild(new Switch(true).setDisabled(true));
            }
            case "segmented" -> {
                Segmented segmented = new Segmented(List.of(text("option_one"), text("option_two"), text("option_three")));
                root.addChild(segmented);
                root.addChild(new Segmented(List.of(text("option_one"), text("option_two"), text("option_three")))
                        .setIcon(0, LOGO).setIcon(1, LOGO).setIcon(2, LOGO));
            }
            case "dropdown" -> {
                Dropdown<Integer> dropdown = new Dropdown<>(0, 5);
                dropdown.setOptions(IntStream.range(0, 12)
                        .mapToObj(i -> new Dropdown.Option<>(i, text("option_number", i))).toList());
                root.addChild(dropdown);
            }
            case "menu" -> {
                Menu menu = new Menu().setItems(List.of(MenuItem.group("group", text("menu_group"), List.of(
                        MenuItem.item("one", text("option_one")), MenuItem.item("two", text("option_two")),
                        MenuItem.item("disabled", text("disabled")).disabled(true))), MenuItem.empty("empty", text("empty_state"))));
                root.addChild(menu);
            }
            case "navbar" -> root.addChild(new Navbar(text("centered_title")).setExtra(button("extra")));
            case "input" -> {
                Input input = new Input().setPlaceholder(GalleryView.tr("input_hint"));
                root.addChild(new InputControl(input));
            }
            case "textarea" -> {
                TextArea area = new TextArea(0).setRows(4);
                area.setPlaceholder(GalleryView.tr("textarea_hint"));
                root.addChild(new InputControl(area));
            }
            case "number" -> {
                NumberInput number = new NumberInput().setDecimalPlaces(2).setMinimumValue("0.01").setMaximumValue(100)
                        .setBoundaryStrategy(NumberInput.BoundaryStrategy.CLAMP);
                number.setPlaceholder(text("number_hint"));
                root.addChild(new InputControl(number));
            }
            case "form" -> {
                FieldKey<String> name = new FieldKey<>("name", String.class);
                FieldKey<BigDecimal> amount = new FieldKey<>("amount", BigDecimal.class);
                FieldKey<Boolean> enabled = new FieldKey<>("enabled", Boolean.class);
                Input input = new Input().setPlaceholder(GalleryView.tr("input_hint"));
                NumberInput number = new NumberInput().setDecimalPlaces(2).setMinimumValue(0).setMaximumValue(100)
                        .setBoundaryStrategy(NumberInput.BoundaryStrategy.CLAMP);
                number.setPlaceholder(text("amount_hint"));
                Form form = new Form().addItem(new FormItem<>(name, text("name"), FormFields.text(input)).addValidator(Validators.required()).addValidator(Validators.length(1, 12)))
                        .addItem(new FormItem<>(amount, text("amount"), FormFields.number(number)))
                        .addItem(new FormItem<>(enabled, text("enabled"), FormFields.checkbox(new Checkbox(text("enabled")))));
                form.setVisible(true);
                form.setInitialValues(FormValues.builder().setValue(name, "").setValue(amount, BigDecimal.ONE).setValue(enabled, false).build());
                TextArea notes = new TextArea(0).setRows(2);
                notes.setPlaceholder(text("notes_hint"));
                Switch power = new Switch();
                Dropdown<Flavor> choice = new Dropdown<>(Flavor.FIRST, 3);
                choice.setOptions(List.of(new Dropdown.Option<>(Flavor.FIRST, text("option_one")), new Dropdown.Option<>(Flavor.SECOND, text("option_two"))));
                Segmented channels = new Segmented(List.of(text("option_one"), text("option_two")));
                form.addItem(new FormItem<>(new FieldKey<>("notes", String.class), text("notes"), FormFields.text(notes)))
                        .addItem(new FormItem<>(new FieldKey<>("power", Boolean.class), text("power"), FormFields.switchField(power)))
                        .addItem(new FormItem<>(new FieldKey<>("choice", Flavor.class), text("choice"), FormFields.dropdown(Flavor.class, choice)))
                        .addItem(new FormItem<>(new FieldKey<>("channel", Flavor.class), text("channel"), FormFields.segmented(Flavor.class, channels, List.of(Flavor.FIRST, Flavor.SECOND))));
                root.addChild(form);
                Box actions = row();
                actions.addChild(button("validate", form::validate));
                actions.addChild(button("reset", form::reset));
                actions.addChild(button("fill", () -> form.setValuesNoSignal(FormValues.builder().setValue(name, "Ore").setValue(amount, BigDecimal.TEN).setValue(enabled, true).build())));
                root.addChild(actions);
            }
            case "tree" -> {
                Tree<Integer> tree = new Tree<Integer>().setItems(IntStream.range(0, 300).mapToObj(i -> new Tree.Item<>(
                        "node" + i, i == 0 ? "" : "node" + ((i - 1) / 5), text("tree_node", i), i)).toList()).setDefaultExpandAll(true);
                root.addChild(new FixedViewport(new Scroll().setContent(tree), 0, 180));
            }
            case "table" -> {
                Table<Integer> table = new Table<Integer>().setRowKey(i -> i);
                table.addColumn(new Table.Column<>("player", text("player"), 60, 100, 1, w -> 24, new PlayerIdentityCell<>(i -> null, i -> text("player_number", i).getString())));
                table.addColumn(new Table.Column<>("value", text("value"), 32, 48, 0, w -> 24, new Table.TextCell<>(i -> text("row_number", i))));
                table.addColumn(new Table.Column<>("action", text("action"), 60, 80, 0, w -> 24, new Table.ControlCell<>(
                        () -> {
                            Dropdown<Integer> drop = new Dropdown<>(0, 3);
                            drop.setOptions(List.of(new Dropdown.Option<>(0, text("option_one")), new Dropdown.Option<>(1, text("option_two"))));
                            return drop;
                        },
                        (control, value) -> control.setTriggerLabel(text("row_number", value)))));
                table.setRows(IntStream.range(0, 200).boxed().toList());
                root.addChild(new FixedViewport(new Scroll().setContent(table), 0, 180));
            }
            case "dialog", "modal", "drawer" -> {
                if (id.equals("drawer")) {
                    Drawer drawer = new Drawer(text("drawer_title")).setContent(label("drawer_body")).setWidth(220);
                    window.getPopupOverlay().addChild(drawer);
                    root.getSignalScope().own(drawer::queueFree);
                    root.addChild(button("open_drawer", drawer::open));
                    drawer.addAction(button("close", drawer::close));
                } else if (id.equals("modal")) {
                    Modal modal = new Modal(text("modal_title")).setContent(label("modal_body"));
                    modal.setVisible(false);
                    window.getPopupOverlay().addChild(modal);
                    root.getSignalScope().own(modal::queueFree);
                    modal.closeRequested().connect(root, () -> modal.setVisible(false));
                    modal.addAction(button("close", () -> modal.setVisible(false)));
                    root.addChild(button("open_modal", () -> modal.setVisible(true)));
                } else {
                    Dialog dialog = new Dialog(text("dialog_title")).setContent(label("dialog_body"));
                    dialog.setVisible(false);
                    window.getPopupOverlay().addChild(dialog);
                    root.getSignalScope().own(dialog::queueFree);
                    dialog.closeRequested().connect(root, () -> dialog.setVisible(false));
                    dialog.addAction(button("close", () -> dialog.setVisible(false)));
                    root.addChild(button("open_dialog", () -> dialog.setVisible(true)));
                }
            }
            case "toast" ->
                    root.addChild(button("show_toast", () -> OreToasts.show(new Toast(GalleryView.tr("toast_example")).setType(Toast.Type.PRIMARY))));
            default -> throw new IllegalArgumentException("Unknown gallery sample: " + id);
        }
        return root;
    }

    private enum Flavor {FIRST, SECOND}

    private static final class FixedViewport extends Container {
        private final Control content;
        private final int width, height;

        FixedViewport(Control content, int width, int height) {
            this.content = content;
            this.width = width;
            this.height = height;
            addChild(content);
        }

        @Override
        public Size measureSize(net.minecraft.client.gui.Font font, Size available) {
            return new Size(width == 0 ? available.width() : width, height);
        }

        @Override
        public void layout(Rect bounds, net.minecraft.client.gui.Font font) {
            super.layout(bounds, font);
            fitChild(content, new Rect(0, 0, bounds.width(), height), font);
        }
    }

    private static final class Card extends Container implements Grid.View<Integer> {
        private final Label text = new Label();

        Card() {
            addChild(new Panel().setChild(text));
        }

        public void bind(Integer item) {
            text.setText(GallerySamples.text("card_number", item));
        }

        @Override
        public void layout(Rect bounds, net.minecraft.client.gui.Font font) {
            super.layout(bounds, font);
            fitChild(getChild(0), new Rect(0, 0, bounds.width(), bounds.height()), font);
        }

        public void draw(io.github.luyingyilong.ore.render.DrawContext context, Rect bounds, boolean hovered) {
        }
    }
}

