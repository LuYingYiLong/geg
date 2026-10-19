package io.github.luyingyilong.ore.compat.recipeviewer;

import brachy.modularui.integration.recipeviewer.handlers.RecipeViewerHandler;
import io.github.luyingyilong.ore.Button;
import io.github.luyingyilong.ore.Drawer;
import io.github.luyingyilong.ore.Dropdown;
import io.github.luyingyilong.ore.Modal;
import io.github.luyingyilong.ore.event.EventGeometry;
import io.github.luyingyilong.ore.input.Input;
import io.github.luyingyilong.ore.input.InputControl;
import io.github.luyingyilong.ore.input.NumberInput;
import io.github.luyingyilong.ore.input.TextArea;
import io.github.luyingyilong.ore.layout.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Real GLFW -> viewer hooks -> Forge -> MUI -> Window, not direct widget method calls. */
@Mod.EventBusSubscriber(modid = "geg", value = Dist.CLIENT)
public final class RecipeViewerInputClientTest {
    private record Step(String name, Runnable action) { }
    private static final List<Step> STEPS = new ArrayList<>();
    private static int ticks, step, changedAt, leaked, activated, textChanges, selections;
    private static boolean started, finished, probing;
    private static Window root;
    private static Overlay overlay;
    private static Box form;
    private static Drawer drawer;
    private static Modal modal;
    private static Input input;
    private static NumberInput number;
    private static TextArea area;
    private static InputControl inputNode, numberNode, areaNode;
    private static Dropdown<String> dropdown;
    private static String viewer;
    private static String previousClipboard;

    @SubscribeEvent public static void observe(ScreenEvent event) {
        if (probing && (event instanceof ScreenEvent.KeyPressed.Pre || event instanceof ScreenEvent.KeyReleased.Pre
                || event instanceof ScreenEvent.CharacterTyped.Pre || event instanceof ScreenEvent.MouseButtonPressed.Pre
                || event instanceof ScreenEvent.MouseButtonReleased.Pre || event instanceof ScreenEvent.MouseScrolled.Pre
                || event instanceof ScreenEvent.MouseDragged.Pre)) leaked++;
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        viewer = System.getProperty("ore.recipe.input.smoke", "");
        if (viewer.isEmpty() || event.phase != TickEvent.Phase.END || finished) return;
        Minecraft mc = Minecraft.getInstance(); if (mc.player == null) return;
        try {
            if (++ticks > 1200) throw new AssertionError("Input shield timeout step=" + step);
            if (!started) {
                RecipeViewerClientTest.checkInstalled(viewer);
                if (!RecipeViewerClientTest.viewersReady(viewer)) return;
                started = true; initialize(); changedAt = ticks; return;
            }
            if (ticks <= changedAt + 3 || root.getContext().getMui() == null || !input.isValid()) return;
            if (step == STEPS.size()) {
                finished = true; restoreClipboard();
                System.out.println("ORE_" + viewer.replace('+', '_').toUpperCase(java.util.Locale.ROOT) + "_INPUT_CLIENT_PASS"); mc.stop(); return;
            }
            Step next = STEPS.get(step); next.action().run(); step++; changedAt = ticks;
        } catch (Throwable error) {
            finished = true; System.err.println("Input shield step=" + step + " " + (step < STEPS.size() ? STEPS.get(step).name() : "finish"));
            if (root != null && root.getContext().getMui() != null)
                System.err.println("input=" + (inputNode == null ? null : inputNode.getWidgetBounds())
                        + " drawer=" + (drawer == null ? null : drawer.getPanelBounds())
                        + " mouse=" + root.getContext().getMui().getAbsMouseX() + "," + root.getContext().getMui().getAbsMouseY()
                        + " focus=" + root.getFocusManager().getFocused() + " nativeFocus=" + (input != null && input.isFocused()));
            try {
                var output = mc.gameDirectory.toPath().resolve("screenshots/recipe-input-failure.png");
                java.nio.file.Files.createDirectories(output.getParent());
                try (var image = net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(output); }
            } catch (Exception captureError) { captureError.printStackTrace(); }
            error.printStackTrace(); restoreClipboard(); System.out.println("ORE_RECIPE_VIEWER_INPUT_CLIENT_FAIL"); mc.stop();
        }
    }

    private static void initialize() {
        GLFW.glfwFocusWindow(handle());
        if (viewer.equals("jei+emi")) {
            check(RecipeViewerHandler.getCurrent().getClass().getName().endsWith(".EmiScreenHandler"),
                    "MUI selects only the EMI input integration when both mods are installed");
            check(getJeiOverlay().getClass().getName().startsWith("dev.emi.emi.jemi."), "JEI runtime uses EMI's JEMI overlay");
        }
        input = new Input().setPlaceholder(Component.literal("Input"));
        number = new NumberInput();
        area = new TextArea(0).setRows(2);
        inputNode = new InputControl(input); numberNode = new InputControl(number); areaNode = new InputControl(area);
        input.textChanged().connect(inputNode, value -> textChanges++);
        dropdown = new Dropdown<>("a", 4);
        dropdown.setOptions(List.of(new Dropdown.Option<>("a", Component.literal("A")), new Dropdown.Option<>("b", Component.literal("B"))));
        dropdown.selectionChanged().connect(dropdown, value -> selections++);
        form = new Box().setGap(4);
        for (Control node : List.of(inputNode, numberNode, areaNode, dropdown)) {
            node.setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN); form.addChild(node);
        }
        Button action = new Button(Component.literal("Action"));
        action.pressed().connect(action, () -> activated++); form.addChild(action);
        Button underneath = new Button(Component.literal("Behind"));
        underneath.setPosition(2, 2).setRequestedSize(70, 22);
        underneath.pressed().connect(underneath, () -> activated++);
        Control page = new Control(); page.addChild(underneath);
        overlay = new Overlay(); root = Window.embedded().setContent(page).setOverlay(overlay).onBack(() -> { });
        drawer = new Drawer(Component.literal("Protected Drawer")).setAnimationDuration(0).setContent(form);
        overlay.addChild(drawer); drawer.open(); root.open();
        previousClipboard = root.getContext().getClipboard();

        click(inputNode);
        add("Drawer text and viewer shortcuts", () -> {
            check(input.isFocused(), "Click focuses the real MUI input"); input.setTextNoSignal(""); textChanges = 0;
            boolean viewerEnabled = viewer.equals("jei+emi") && getEmiEnabled();
            protectedAction(() -> { trap(); typeKey(GLFW.GLFW_KEY_R, "r"); typeKey(GLFW.GLFW_KEY_U, "u"); text("中"); tap(GLFW.GLFW_KEY_F, GLFW.GLFW_MOD_CONTROL); tap(GLFW.GLFW_KEY_O, GLFW.GLFW_MOD_CONTROL); });
            check(input.getText().equals("ru中") && textChanges == 3, "Committed text is delivered once, not to a second input chain");
            check(input.isFocused() && !RecipeViewerHandler.getCurrent().isSearchFocused(), "Recipe search cannot steal Input focus");
            if (viewer.equals("jei+emi")) check(getEmiEnabled() == viewerEnabled, "Protected Ctrl+O does not toggle EMI's overlay");
        });
        click(numberNode);
        add("NumberInput remains editable", () -> {
            check(number.isFocused(), "Numeric caret obtains native focus"); number.setTextNoSignal("");
            protectedAction(() -> { trap(); typeKey(GLFW.GLFW_KEY_7, "7"); tap(GLFW.GLFW_KEY_F, GLFW.GLFW_MOD_CONTROL); });
            check(number.getText().equals("7") && number.isFocused(), "Numeric input is neither consumed by the viewer nor duplicated");
        });
        click(areaNode);
        add("Multiline text, clipboard and undo", () -> {
            check(area.isFocused(), "TextArea retains native focus"); area.setTextNoSignal("");
            protectedAction(() -> { trap(); text("中"); tap(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_MOD_SHIFT); text("文"); });
            check(area.getText().equals("中\n文"), "Multiline editing survives protection");
            root.getContext().setClipboard("复制\n粘贴");
            protectedAction(() -> { tap(GLFW.GLFW_KEY_A, GLFW.GLFW_MOD_CONTROL); tap(GLFW.GLFW_KEY_V, GLFW.GLFW_MOD_CONTROL); });
            check(area.getText().equals("复制\n粘贴"), "Protected paste");
            protectedAction(() -> tap(GLFW.GLFW_KEY_Z, GLFW.GLFW_MOD_CONTROL)); check(area.getText().equals("中\n文"), "Protected undo");
            protectedAction(() -> tap(GLFW.GLFW_KEY_Y, GLFW.GLFW_MOD_CONTROL)); check(area.getText().equals("复制\n粘贴"), "Protected redo");
        });
        click(action);
        add("Protected button activates once", () -> { check(activated == 1, "A complete click still activates exactly once"); activated = 0; });
        add("Move to protected button", () -> move(action));
        add("Press protected button", () -> protectedAction(() -> mouse(GLFW.GLFW_PRESS, 0)));
        add("Drag outside protected button", () -> move(5, 5));
        add("Release outside cancels", () -> { protectedAction(() -> mouse(GLFW.GLFW_RELEASE, 0)); check(activated == 0, "Drag-out release does not activate either layer"); });
        add("Backdrop scrolling and secondary click", () -> protectedAction(() -> {
            trap(); scroll(-1); mouse(GLFW.GLFW_PRESS, 1); mouse(GLFW.GLFW_RELEASE, 1);
            check(drawer.isOpen(), "Right click is consumed without closing or interacting through the mask");
        }));
        add("Backdrop closes Drawer on press", () -> protectedAction(() -> mouse(GLFW.GLFW_PRESS, 0)));
        add("Move to an unprotected area while held", () -> move(90, 5));
        add("Release after Drawer closed", () -> {
            check(!drawer.getVisible() && !RecipeViewerInputGuard.getPointerProtected(Minecraft.getInstance().screen, 90, 5), "Drawer is closed and pointer is outside remaining Ore controls before release");
            protectedAction(() -> mouse(GLFW.GLFW_RELEASE, 0));
            check(activated == 0, "Closing press cannot release into the underlying button or viewer");
            drawer.setContent(new Control());
            modal = new Modal(Component.literal("Protected Modal")).setSize(240, 0).setContent(form);
            modal.closeRequested().connect(modal, () -> modal.setVisible(false)); overlay.addChild(modal);
        });
        click(inputNode);
        add("Modal retains text focus", () -> {
            input.setTextNoSignal(""); protectedAction(() -> { trap(); typeKey(GLFW.GLFW_KEY_R, "r"); });
            check(input.getText().equals("r") && input.isFocused(), "Modal input receives R instead of opening recipes");
        });
        click(dropdown);
        add("Dropdown masks viewer input", () -> {
            check(dropdown.isOpen() && root.getContext().getPopups().open(), "Dropdown Overlay opens on release");
            protectedAction(() -> { trap(); tap(GLFW.GLFW_KEY_R, 0); text("r"); });
            check(dropdown.isOpen() && !RecipeViewerHandler.getCurrent().isSearchFocused(), "Recipe/search shortcut cannot dismiss or steal Dropdown focus");
        });
        add("Move to popup option", () -> move(popupButtons(root.getFocusManager().getInputScope()).get(1)));
        add("Press popup option", () -> { protectedAction(() -> mouse(GLFW.GLFW_PRESS, 0)); check(dropdown.getSelected().equals("a"), "Menu does not activate on press"); });
        add("Release popup option", () -> {
            protectedAction(() -> mouse(GLFW.GLFW_RELEASE, 0));
            check(dropdown.getSelected().equals("b") && !dropdown.isOpen(), "Real menu Button activates on release");
            check(selections == 1, "A popup selection is delivered only once with all installed viewers");
        });
        click(dropdown);
        add("Esc closes only the top menu", () -> {
            protectedAction(() -> tap(GLFW.GLFW_KEY_ESCAPE, 0));
            check(!dropdown.isOpen() && modal.getVisible(), "Esc closes only the Dropdown");
        });
        click(inputNode);
        add("Esc first blurs then closes top layer", () -> {
            protectedAction(() -> tap(GLFW.GLFW_KEY_ESCAPE, 0)); check(!input.isFocused() && modal.getVisible(), "First Esc only blurs Input");
            protectedAction(() -> tap(GLFW.GLFW_KEY_ESCAPE, 0)); check(!modal.getVisible(), "Second Esc closes the Modal");
            check(!RecipeViewerInputGuard.getKeyboardProtected(Minecraft.getInstance().screen), "Protection ends with the last layer");
        });
        add("Normal input is restored", () -> {
            leaked = 0; probing = true;
            try { tap(GLFW.GLFW_KEY_F13, 0); } finally { probing = false; }
            check(leaked > 0, "Unprotected input can reach normal Forge listeners again");
        });
    }

    private static void click(Control node) {
        add("Move to " + node.getClass().getSimpleName(), () -> move(node));
        add("Press " + node.getClass().getSimpleName(), () -> protectedAction(() -> mouse(GLFW.GLFW_PRESS, 0)));
        add("Release " + node.getClass().getSimpleName(), () -> protectedAction(() -> mouse(GLFW.GLFW_RELEASE, 0)));
    }
    private static void add(String name, Runnable action) { STEPS.add(new Step(name, action)); }
    private static void protectedAction(Runnable action) {
        leaked = 0; probing = true;
        try {
            action.run();
            check(leaked == 0, "Protected input did not reach lower-priority viewer/Forge listeners (leaked=" + leaked + ")");
            if (viewer.equals("jei+emi")) {
                check(!RecipeViewerHandler.getCurrent().isSearchFocused(), "EMI search cannot retain keyboard focus");
                Object overlay = getJeiOverlay();
                check(!(boolean) Class.forName("mezz.jei.api.runtime.IIngredientListOverlay").getMethod("hasKeyboardFocus").invoke(overlay),
                        "JEI runtime cannot retain keyboard focus through the JEMI bridge");
            }
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        finally { probing = false; }
    }
    private static Object getJeiOverlay() {
        try {
            Object runtime = RecipeViewerClientTest.getJeiRuntime();
            return Class.forName("mezz.jei.api.runtime.IJeiRuntime").getMethod("getIngredientListOverlay").invoke(runtime);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static boolean getEmiEnabled() {
        try { return Class.forName("dev.emi.emi.config.EmiConfig").getField("enabled").getBoolean(null); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void restoreClipboard() {
        if (previousClipboard != null && root != null && root.getContext().getClipboard().equals("复制\n粘贴"))
            root.getContext().setClipboard(previousClipboard);
    }
    private static void trap() {
        RecipeViewerHandler.getCurrent().setSearchFocused(true);
        if (!viewer.equals("none")) check(RecipeViewerHandler.getCurrent().isSearchFocused(), "Viewer focus trap was armed");
        if (viewer.equals("jei+emi")) {
            try {
                check((boolean) Class.forName("mezz.jei.api.runtime.IIngredientListOverlay").getMethod("hasKeyboardFocus").invoke(getJeiOverlay()),
                        "JEI's runtime also observes the armed EMI search focus");
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        }
    }
    private static void move(Control node) {
        Rect bounds = EventGeometry.displayed(node).translated(root.getContext().getOriginX(), root.getContext().getOriginY());
        move(bounds.x() + Math.min(8, bounds.width() / 2), bounds.y() + bounds.height() / 2);
    }
    private static List<Button> popupButtons(Control node) {
        List<Button> buttons = new ArrayList<>();
        if (node instanceof Button button) buttons.add(button);
        for (Control child : node.getChildren()) buttons.addAll(popupButtons(child));
        return buttons;
    }
    private static void move(int x, int y) {
        var mc = Minecraft.getInstance();
        double px = (double) x * mc.getWindow().getScreenWidth() / mc.screen.width;
        double py = (double) y * mc.getWindow().getScreenHeight() / mc.screen.height;
        var callback = GLFW.glfwSetCursorPosCallback(handle(), null); GLFW.glfwSetCursorPosCallback(handle(), callback);
        GLFW.glfwSetCursorPos(handle(), px, py); callback.invoke(handle(), px, py);
    }
    private static void mouse(int action, int button) {
        var callback = GLFW.glfwSetMouseButtonCallback(handle(), null); GLFW.glfwSetMouseButtonCallback(handle(), callback);
        callback.invoke(handle(), button, action, 0);
    }
    private static void scroll(double delta) {
        var callback = GLFW.glfwSetScrollCallback(handle(), null); GLFW.glfwSetScrollCallback(handle(), callback);
        callback.invoke(handle(), 0, delta);
    }
    private static void tap(int key, int modifiers) {
        var callback = GLFW.glfwSetKeyCallback(handle(), null); GLFW.glfwSetKeyCallback(handle(), callback);
        callback.invoke(handle(), key, GLFW.glfwGetKeyScancode(key), GLFW.GLFW_PRESS, modifiers);
        callback.invoke(handle(), key, GLFW.glfwGetKeyScancode(key), GLFW.GLFW_RELEASE, modifiers);
    }
    private static void typeKey(int key, String value) {
        var callback = GLFW.glfwSetKeyCallback(handle(), null); GLFW.glfwSetKeyCallback(handle(), callback);
        callback.invoke(handle(), key, GLFW.glfwGetKeyScancode(key), GLFW.GLFW_PRESS, 0); text(value);
        callback.invoke(handle(), key, GLFW.glfwGetKeyScancode(key), GLFW.GLFW_RELEASE, 0);
    }
    private static void text(String value) {
        var callback = GLFW.glfwSetCharModsCallback(handle(), null); GLFW.glfwSetCharModsCallback(handle(), callback);
        value.codePoints().forEach(codepoint -> callback.invoke(handle(), codepoint, 0));
    }
    private static long handle() { return Minecraft.getInstance().getWindow().getWindow(); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
