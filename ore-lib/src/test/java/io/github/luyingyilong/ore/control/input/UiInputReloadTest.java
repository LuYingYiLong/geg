package io.github.luyingyilong.ore.control.input;

import brachy.modularui.utils.Point;
import io.github.luyingyilong.ore.control.base.*;
import io.github.luyingyilong.ore.control.layout.Scroll;
import io.github.luyingyilong.ore.control.navigation.Tabs;
import io.github.luyingyilong.ore.gui.*;
import io.github.luyingyilong.ore.signal.SignalScope;
import io.github.luyingyilong.ore.window.Window;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import java.io.StringReader;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public final class UiInputReloadTest {
    public static final class ComposingInput extends Input { boolean composing; @Override public boolean getComposing() { return composing; } }
    private record Snapshot(String text, int x, int y, int anchorX, int anchorY) { }
    public static void main(String[] args) throws Exception {
        var dist = net.minecraftforge.fml.loading.FMLLoader.class.getDeclaredField("dist"); dist.setAccessible(true); dist.set(null, net.minecraftforge.api.distmarker.Dist.DEDICATED_SERVER);
        var boot = net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped"); boot.setAccessible(true); boot.setBoolean(null, true);
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.core.registries.BuiltInRegistries.bootStrap();
        Font font = new Font(id -> null, false) { @Override public int width(FormattedText text) { return text.getString().length() * 6; } };
        AtomicReference<String> xml = new AtomicReference<>("""
                <Box id="root" gap="4">
                  <Tabs id="tabs"><item key="edit" text="Editor"><Box id="edit">
                    <Input id="input" text="initial" placeholder="Hint"/>
                    <NumberInput id="number" text="12" decimalPlaces="2" minimumValue="0.01" maximumValue="100"/>
                    <TextArea id="area" text="line1&#10;line2" rows="3"/>
                    <test:composing id="ime" text="draft"/>
                  </Box></item><item key="other" text="Other"><Box id="other"/></item></Tabs>
                  <Scroll id="scroll" vertical="ALWAYS" scrollY="24" minimum="40,30"><slot name="content"><Control minimum="40,300"/></slot></Scroll>
                </Box>
                """);
        UiTypeRegistry registry = UiTypeRegistry.createDefault().register("test:composing", node -> new InputControl(new ComposingInput()), node -> "", Map.of());
        UiLoader loader = new UiLoader(registry, id -> new StringReader(xml.get()), Runnable::run);
        try (SignalScope owner = new SignalScope(); UiDocument document = loader.open(ResourceLocation.parse("test:gui/inputs.xml"), new UiBindings(), owner)) {
            Window window = Window.embedded().setContent(document.getRoot());
            try {
                Input input = field(document, "input"), number = field(document, "number"), area = field(document, "area");
                List<Input> fields = List.of(input, number, area);
                for (Input field : fields) installHeadlessCaret(field);
                List<Snapshot> states = new ArrayList<>();
                for (Input field : fields) {
                    var editor = field.editingHandler(); editor.setCursor(new Point(field instanceof TextArea ? 3 : field.getText().length(), 0), false);
                    check(InputShortcuts.type(editor, field instanceof NumberInput ? "+" : "!"), "Edit accepted");
                    editor.setCursor(new Point(2, 0), true); editor.setOffsetCursor(new Point(1, 0));
                    states.add(snapshot(field));
                }
                Tabs tabs = document.getRoot().findControlFromId("tabs", Tabs.class);
                tabs.setSelectedKeyNoSignal("other");
                for (int width : new int[]{127, 128, 129}) window.layoutViewport(width, 220, font);
                Scroll scroll = document.getRoot().findControlFromId("scroll", Scroll.class); check(scroll.getScrollY() == 24, "XML initial scroll applies after extent measurement"); scroll.setScroll(0, 40);
                ComposingInput composing = (ComposingInput) field(document, "ime"); composing.composing = true;
                xml.set(xml.get().replace("gap=\"4\"", "gap=\"9\"").replace("text=\"initial\"", "text=\"replacement\"").replace("placeholder=\"Hint\"", "placeholder=\"New hint\""));
                document.reload(); UiRuntime.flush();
                check(document.getStatus() == UiDocument.Status.WAITING_FOR_IME && document.getVersion() == 1, "Preedit defers tree mutation");
                composing.composing = false; UiRuntime.flush();
                check(document.getStatus() == UiDocument.Status.READY, document.getError());
                check(tabs == document.getRoot().findControlFromId("tabs") && tabs.getSelectedKey().equals("other"), "Tabs instance and active selection retained");
                for (int i = 0; i < fields.size(); i++) {
                    Input field = fields.get(i); check(field == field(document, List.of("input", "number", "area").get(i)) && snapshot(field).equals(states.get(i)), "Editor instance, draft and selection retained");
                    check(field.editingHandler().undo(), "Undo remains available after reload: " + field.getClass().getSimpleName());
                    check(field.editingHandler().redo() && field.getText().equals(states.get(i).text()), "Redo remains available");
                }
                window.layoutViewport(129, 220, font); check(scroll.getScrollY() == 40, "Live scroll state wins over XML initial value on reload");
                check(number.getText().equals("12+") && ((NumberInput) number).getError() == null, "Unfinished number expression is not committed");
                check(input.getPlaceholder().getString().equals("New hint"), "Configuration updates without resetting editor text");
                xml.set(xml.get().replace("decimalPlaces=\"2\" minimumValue=\"0.01\" maximumValue=\"100\"", "decimalPlaces=\"0\" minimumValue=\"1\" maximumValue=\"10\""));
                document.reload(); UiRuntime.flush(); check(document.getStatus() == UiDocument.Status.READY && ((NumberInput) number).getDecimalPlaces() == 0, "Precision and bounds change atomically");
                document.reset(); UiRuntime.flush(); window.layoutViewport(129, 220, font);
                check(input.getText().equals("replacement") && number.getText().equals("12") && scroll.getScrollY() == 24, "Explicit reset reapplies XML initial state");
            } finally { window.queueFree(); }
        } finally { UiRuntime.flush(); LifecycleScheduler.flush(); }
        hiddenInitialScroll(font);
        formFields(font);
        System.out.println("ORE_XML_INPUT_RELOAD_PASS");
    }
    private static void hiddenInitialScroll(Font font) {
        String xml = "<Tabs id='tabs'><item key='first' text='First'><Control/></item><item key='second' text='Second'><Scroll id='scroll' scrollY='45'><slot name='content'><Control minimum='100,1000'/></slot></Scroll></item></Tabs>";
        UiLoader loader = new UiLoader(UiTypeRegistry.createDefault(), resource -> new StringReader(xml), Runnable::run);
        try (SignalScope owner = new SignalScope(); UiDocument document = loader.open(ResourceLocation.parse("test:gui/hidden.xml"), new UiBindings(), owner)) {
            Window window = Window.embedded().setContent(document.getRoot());
            window.layoutViewport(128, 150, font);
            document.reload(); UiRuntime.flush(); // A still-hidden reused node must retain its pending initial scroll.
            Tabs tabs = document.getRoot().findControlFromId("tabs", Tabs.class); tabs.setSelectedKeyNoSignal("second");
            window.layoutViewport(128, 150, font); window.layoutViewport(128, 150, font);
            check(document.getRoot().findControlFromId("scroll", Scroll.class).getScrollY() == 45, "Hidden-page XML initial scroll waits for its first actual layout, including across reload");
            window.queueFree();
        } finally { LifecycleScheduler.flush(); }
    }

    private static void formFields(Font font) throws Exception {
        AtomicReference<String> xml=new AtomicReference<>("<Form id='form'><FormItem id='field' name='amount' label='Amount'><NumberInput id='number' text='12' decimalPlaces='2'/><validate rule='range' min='0.01' max='100'/></FormItem></Form>");
        UiLoader loader=new UiLoader(UiTypeRegistry.createDefault(),id -> new StringReader(xml.get()),Runnable::run);
        try(SignalScope owner=new SignalScope();UiDocument document=loader.open(ResourceLocation.parse("test:gui/form.xml"),new UiBindings(),owner)) {
            Window window=Window.embedded().setContent(document.getRoot());
            try {
                Input number=field(document,"number");installHeadlessCaret(number);
                var form=document.getRoot().findControlFromId("form",io.github.luyingyilong.ore.control.form.Form.class);
                var item=document.getRoot().findControlFromId("field",io.github.luyingyilong.ore.control.form.FormItem.class);
                var editor=number.editingHandler();editor.setCursor(new Point(2,0),false);InputShortcuts.type(editor,"+");
                editor.setCursor(new Point(1,0),true);Snapshot previous=snapshot(number);
                xml.set(xml.get().replace("label='Amount'","label='Updated amount'").replace("max='100'","max='50'"));
                document.reload();UiRuntime.flush();window.layoutViewport(128,180,font);
                check(document.getStatus()==UiDocument.Status.READY,document.getError());
                check(document.getRoot().findControlFromId("field")==item && field(document,"number")==number,"Form item and existing editor identity preserved");
                check(snapshot(number).equals(previous) && item.getDirty(),"Label and validator updates retain unfinished expression, selection and dirty baseline");
                check(item.getLabel().getString().equals("Updated amount"),"Form label configuration applies without replacing its input");
                check(editor.undo() && editor.redo() && number.getText().equals("12+"),"Form input undo/redo survives reload");
                check(form==document.getRoot().findControlFromId("form"),"Form identity preserved");
            } finally {window.queueFree();}
        } finally {UiRuntime.flush();LifecycleScheduler.flush();}
    }

    /** Replace only MUI's native-graphics caret scrolling, matching InputEditingTest. */
    private static void installHeadlessCaret(Input input) throws Exception {
        String text = input.getText();
        OreTextFieldHandler editor = new OreTextFieldHandler(null) {
            @Override protected void moveMainCursor(int line, int column, boolean animate) { getMainCursor().set(column, line); }
            @Override public void onChanged() { }
        };
        editor.setMaxLines(input instanceof TextArea ? 100 : 1);
        editor.getText().addAll(io.github.luyingyilong.ore.text.TextLines.split(text));
        editor.setRenderer(new brachy.modularui.widgets.textfield.TextFieldRenderer(editor) {
            @Override public boolean wouldFit(List<String> lines, boolean checkWidth) { return true; }
        });
        java.lang.reflect.Field handler = null;
        for (Class<?> type = Input.class; type != null && handler == null; type = type.getSuperclass())
            try { handler = type.getDeclaredField("handler"); } catch (NoSuchFieldException ignored) { }
        java.util.Objects.requireNonNull(handler).setAccessible(true); handler.set(input, editor);
    }

    private static Input field(UiDocument document, String id) { return document.getRoot().findControlFromId(id, InputControl.class).getWidget(); }
    private static Snapshot snapshot(Input field) { var editor = field.editingHandler(); return new Snapshot(field.getText(), editor.getMainCursor().x, editor.getMainCursor().y, editor.getOffsetCursor().x, editor.getOffsetCursor().y); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
