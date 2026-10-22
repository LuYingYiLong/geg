package io.github.luyingyilong.ore.control.input;

import brachy.modularui.utils.Point;
import io.github.luyingyilong.ore.control.base.*;
import io.github.luyingyilong.ore.control.layout.Scroll;
import io.github.luyingyilong.ore.gui.*;
import io.github.luyingyilong.ore.gui.dev.UiSourceWatcher;
import io.github.luyingyilong.ore.signal.SignalScope;
import io.github.luyingyilong.ore.window.Window;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import java.io.StringReader;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Source save -> watcher -> safe-point commit, using the actual editor document/history. */
public final class UiWatchedInputTest {
    public static final class ComposingInput extends Input { boolean composing; @Override public boolean getComposing() { return composing; } }
    private record Snapshot(String text, int x, int y, int anchorX, int anchorY) { }
    public static void main(String[] args) throws Exception {
        var dist = net.minecraftforge.fml.loading.FMLLoader.class.getDeclaredField("dist"); dist.setAccessible(true); dist.set(null, net.minecraftforge.api.distmarker.Dist.DEDICATED_SERVER);
        var boot = net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped"); boot.setAccessible(true); boot.setBoolean(null, true);
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.core.registries.BuiltInRegistries.bootStrap();
        Font font = new Font(id -> null, false) { @Override public int width(FormattedText text) { return text.getString().length() * 6; } };
        Path parent = Path.of("build/tmp").toAbsolutePath().normalize(); Files.createDirectories(parent);
        Path root = Files.createTempDirectory(parent, "watched-input-");
        Path file = root.resolve("assets/example/gui/state.xml"); Files.createDirectories(file.getParent());
        String xml = """
                <Box id="root" gap="4">
                  <Input id="input" text="initial" placeholder="Hint"/>
                  <NumberInput id="number" text="12" decimalPlaces="2"/>
                  <TextArea id="area" text="line1&#10;line2" rows="3"/>
                  <test:composing id="ime"/>
                  <Scroll id="scroll" vertical="ALWAYS" scrollY="24" minimum="40,30" preferred="40,40" sizeFlagsVertical="SIZE_EXPAND_FILL"><slot name="content"><Control minimum="40,1000"/></slot></Scroll>
                </Box>
                """;
        Files.writeString(file, xml);
        UiLoader loader = new UiLoader(UiTypeRegistry.createDefault().register("test:composing", node -> new InputControl(new ComposingInput()), node -> "", Map.of()),
                id -> new StringReader("<Control/>"), Runnable::run);
        try (UiSourceWatcher watcher = new UiSourceWatcher(root, loader); SignalScope owner = new SignalScope();
             UiDocument document = loader.open(ResourceLocation.parse("example:gui/state.xml"), new UiBindings(), owner)) {
            Window window = Window.embedded().setContent(document.getRoot());
            try {
                List<Input> fields = List.of(field(document, "input"), field(document, "number"), field(document, "area"));
                List<Snapshot> states = new ArrayList<>();
                for (Input field : fields) {
                    installHeadlessCaret(field);
                    var editor = field.editingHandler();
                    editor.setCursor(new Point(field instanceof TextArea ? 3 : field.getText().length(), 0), false);
                    check(InputShortcuts.type(editor, field instanceof NumberInput ? "+" : "!"), "Draft edit accepted");
                    editor.setCursor(new Point(2, 0), true); editor.setOffsetCursor(new Point(1, 0));
                    states.add(snapshot(field));
                }
                for (int pass = 0; pass < 3; pass++) window.layoutViewport(129, 240, font);
                Scroll scroll = document.getRoot().findControlFromId("scroll", Scroll.class);
                check(scroll.getScrollY() == 24, "Initial XML offset applied before user scrolling");
                scroll.setScroll(0, 60);
                check(scroll.getScrollY() == 60, "Fixture has a genuine scroll range: " + scroll.getMaxScrollY());
                ComposingInput ime = (ComposingInput) field(document, "ime"); ime.composing = true;
                Files.writeString(file, xml.replace("gap=\"4\"", "gap=\"7\"").replace("text=\"initial\"", "text=\"changed\"").replace("placeholder=\"Hint\"", "placeholder=\"Updated hint\""));
                await(() -> document.getStatus() == UiDocument.Status.WAITING_FOR_IME);
                check(document.getVersion() == 1, "Source save waits for IME without changing tree");
                ime.composing = false; await(() -> document.getVersion() == 2);
                for (int i = 0; i < fields.size(); i++) {
                    Input field = fields.get(i);
                    check(field == field(document, List.of("input", "number", "area").get(i)) && snapshot(field).equals(states.get(i)), "Save preserves editor identity, draft and selection");
                    check(field.editingHandler().undo() && field.editingHandler().redo() && snapshot(field).text().equals(states.get(i).text()), "Undo/redo survives source reload");
                }
                window.layoutViewport(129, 240, font);
                check(scroll == document.getRoot().findControlFromId("scroll") && scroll.getScrollY() == 60, "Same Scroll and scroll offset retained: " + scroll.getScrollY() + " / " + scroll.getMaxScrollY());
                check(field(document, "number").getText().equals("12+"), "Incomplete number expression is not committed");
                check(field(document, "input").getPlaceholder().getString().equals("Updated hint"), "Configuration updates while XML initial text does not overwrite draft");
            } finally { window.queueFree(); }
        } finally {
            UiRuntime.flush(); LifecycleScheduler.flush();
            if (!root.startsWith(parent)) throw new AssertionError("Cleanup escaped test root");
            try (var files = Files.walk(root)) { for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
        }
        System.out.println("ORE_XML_WATCHED_INPUT_PASS");
    }
    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + 6_000_000_000L;
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) { Thread.sleep(30); UiRuntime.flush(); }
        check(condition.getAsBoolean(), "Timed out waiting for source save");
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
