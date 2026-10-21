package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.event.*;
import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.theme.*;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/** Exercises actual Window routing, captures, clips, keyboard repeat and lifetime cancellation. */
public final class SliderInteractionTest {
    private static final Font FONT = new Font(id -> null, false) {
        @Override public int width(FormattedText text) { return text.getString().length() * 6; }
    };
    public static void main(String[] args) {
        geometry(); focusOutline(); draggingAndCrossing(); keyboard(); cancellations(); scrolledAndTransformed();
        System.out.println("ORE_SLIDER_INTERACTION_PASS");
    }

    private static void focusOutline() {
        Slider slider = new Slider();
        Window root = window(slider);
        root.getFocusManager().requestFocus(slider, null);
        root.getFocusManager().keyboardNavigation();
        for (boolean reverse : new boolean[]{false, true}) {
            slider.setReverse(reverse);
            for (double value : new double[]{0, 50, 100}) {
                slider.setValueNoSignal(value);
                checkOutline(root, slider);
            }
        }
        slider.setReverse(false).setStepNoSignal(25).setRangeNoSignal(true)
                .setRangeValueNoSignal(new Slider.RangeValue(25, 75));
        checkOutline(root, slider);
        key(root, KeyEvent.Action.PRESS, GLFW.GLFW_KEY_LEFT, GLFW.GLFW_MOD_SHIFT);
        key(root, KeyEvent.Action.RELEASE, GLFW.GLFW_KEY_LEFT, GLFW.GLFW_MOD_SHIFT);
        check(slider.getActiveThumb() == 0, "focus switches to lower thumb");
        checkOutline(root, slider);
        key(root, KeyEvent.Action.PRESS, GLFW.GLFW_KEY_RIGHT, 0);
        for (int i = 0; i < 2; i++) key(root, KeyEvent.Action.REPEAT, GLFW.GLFW_KEY_RIGHT, 0);
        check(slider.getRangeValue().equals(new Slider.RangeValue(75, 100)) && slider.getActiveThumb() == 1,
                "active physical thumb crosses to upper role");
        checkOutline(root, slider);
        key(root, KeyEvent.Action.RELEASE, GLFW.GLFW_KEY_RIGHT, 0);
        Rect thumb = slider.getFocusBounds();
        WindowInputs.press(root, centerX(thumb), centerY(thumb), null);
        check(root.getFocusManager().getOutlineBounds() == null, "mouse input hides keyboard focus outline");
        WindowInputs.release(root, centerX(thumb), centerY(thumb));
        key(root, KeyEvent.Action.PRESS, GLFW.GLFW_KEY_LEFT, 0);
        checkOutline(root, slider);
        key(root, KeyEvent.Action.RELEASE, GLFW.GLFW_KEY_LEFT, 0);
        slider.setDisabled(true);
        check(root.getFocusManager().getOutlineBounds() == null, "disabled slider has no outline");
        root.queueFree(); LifecycleScheduler.flush();
    }

    private static void checkOutline(Window root, Slider slider) {
        Rect thumb = EventGeometry.displayed(slider, slider.getFocusBounds());
        check(root.getFocusManager().getOutlineBounds().equals(new Rect(thumb.x() - 1, thumb.y() - 1, 16, 17)),
                "focus surrounds the current 14x15 thumb with exactly 1px on every side");
        var style = slider.getThemeItem(OreTheme.FOCUS);
        check(style instanceof StyleBoxFlat flat && flat.color() == 0 && flat.borderColor() == 0xFFFFFFFF
                && flat.borders().equals(new ThemeInsets(1, 1, 1, 1)), "transparent focus has a white 1px border");
    }

    private static Window window(Slider slider) {
        Control content = new Control();
        slider.setPosition(10, 20).setRequestedSize(114, 15);
        content.addChild(slider);
        Window root = new Window(true).setContent(content).setOverlay(new Overlay());
        root.layoutViewport(160, 100, FONT);
        return root;
    }
    private static int centerX(Rect rect) { return rect.x() + rect.width() / 2; }
    private static int centerY(Rect rect) { return rect.y() + rect.height() / 2; }
    private static void geometry() {
        Slider slider = new Slider().setValueNoSignal(50).setShowTicks(true);
        Window root = window(slider);
        for (int width : new int[]{28, 127, 128, 129, 400}) for (int height : new int[]{15, 22, 80}) {
            slider.layout(new Rect(10, 20, width, height), FONT);
            check(slider.getThumbBounds(1).width() == 14 && slider.getThumbBounds(1).height() == 15, "thumb is never stretched");
            check(slider.getRailBounds().height() == 6, "rail retains native height");
            check(slider.getThumbBounds(1).y() == 20 + (height - 15) / 2, "artwork vertically centered");
            slider.setValueNoSignal(0);
            check(slider.getThumbBounds(1).x() == 10, "minimum thumb is fully inside");
            slider.setValueNoSignal(100);
            check(slider.getThumbBounds(1).right() == 10 + width, "maximum thumb is fully inside");
            slider.setValueNoSignal(50);
            check(slider.getFocusBounds().equals(slider.getThumbBounds(1)), "focus follows thumb");
            List<Double> ticks = slider.getVisibleTicks();
            check(ticks.size() <= Math.max(2, slider.getRailBounds().width() / 3 + 2), "ticks bounded by pixels");
        }
        slider.setLimits(0, 1).setStep(1E-100);
        check(slider.getVisibleTicks().size() <= 132, "extremely dense steps do not enumerate numeric domain");
        slider.setMarks(Map.of(0.0, Component.literal("Min"), 1.0, Component.literal("Max")));
        check(slider.measureHeight(FONT, 128) >= 29, "labels reserve a separate line");
        var region = io.github.luyingyilong.ore.render.TextureRegion.full(
                net.minecraft.resources.ResourceLocation.parse("test:rail.png"), 6, 8);
        slider.setThemeOverride(Slider.SLIDER, new StyleBoxTexture(region, new ThemeInsets(2, 2, 2, 2),
                ThemeInsets.ZERO, ThemeInsets.ZERO, StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1));
        check(slider.getRailBounds().height() == 8 && slider.getThumbBounds(1).height() == 15, "theme reload dimensions do not stretch thumbs");
        check(slider.getSelectedBounds().height() == 6, "selected texture retains its own height after rail override");
        root.queueFree(); LifecycleScheduler.flush();
    }

    private static void draggingAndCrossing() {
        Slider slider = new Slider().setValueNoSignal(50);
        Window root = window(slider);
        List<Slider.Change> completed = new ArrayList<>();
        slider.changeCompleted().connect(slider, completed::add);
        Rect thumb = slider.getThumbBounds(1);
        int x = thumb.x() + 1, y = centerY(thumb);
        check(WindowInputs.press(root, x, y, null) && root.getPointerCaptured(0), "Window captures actual slider");
        WindowInputs.move(root, x, y);
        check(slider.getValue() == 50, "off-center grab does not jump");
        WindowInputs.move(root, x + 1, y);
        check(slider.getValue() == 51, "off-center grab retains offset");
        slider.setValueNoSignal(51);
        check(slider.getDragging(), "unchanged controlled refill retains active drag");
        WindowInputs.release(root, 500, 200);
        check(slider.getValue() == 100 && completed.size() == 1 && !root.getPointerCaptured(0), "outside release commits clamped continuous value");
        WindowInputs.release(root, 500, 200);
        check(completed.size() == 1, "release does not duplicate completion");
        slider.setRangeNoSignal(true).setRangeValueNoSignal(new Slider.RangeValue(25, 75));
        List<Slider.RangeValue> changes = new ArrayList<>();
        slider.rangeChanged().connect(slider, changes::add);
        thumb = slider.getThumbBounds(0); y = centerY(thumb);
        WindowInputs.press(root, centerX(thumb), y, null);
        WindowInputs.move(root, 107, y);
        check(slider.getRangeValue().equals(new Slider.RangeValue(75, 90)) && slider.getActiveThumb() == 1, "lower physical thumb crosses without changing captured control");
        slider.setRangeValueNoSignal(slider.getRangeValue());
        check(slider.getDragging() && slider.getActiveThumb() == 1, "sorted refill preserves crossed physical identity");
        WindowInputs.move(root, 97, y);
        check(slider.getRangeValue().equals(new Slider.RangeValue(75, 80)), "same physical thumb continues moving");
        WindowInputs.move(root, 47, y);
        check(slider.getRangeValue().equals(new Slider.RangeValue(30, 75)) && slider.getActiveThumb() == 0, "crossing back restores role");
        WindowInputs.release(root, 47, y);
        check(completed.size() == 2 && changes.size() == 3, "one sorted notification per move and one completion");
        slider.setRangeValueNoSignal(new Slider.RangeValue(50, 50));
        thumb = slider.getThumbBounds(0);
        WindowInputs.press(root, centerX(thumb), centerY(thumb), null);
        WindowInputs.move(root, 77, centerY(thumb));
        WindowInputs.release(root, 77, centerY(thumb));
        check(slider.getRangeValue().equals(new Slider.RangeValue(50, 60)), "overlapping thumbs retain last active physical identity");
        slider.setRangeValueNoSignal(new Slider.RangeValue(20, 80));
        WindowInputs.press(root, 67, 27, null); WindowInputs.release(root, 67, 27);
        check(slider.getRangeValue().equals(new Slider.RangeValue(50, 80)), "equidistant track click chooses previously active thumb");
        slider.setRangeNoSignal(false).setValueNoSignal(50).setReverse(true);
        WindowInputs.press(root, 37, 27, null); WindowInputs.release(root, 37, 27);
        check(slider.getValue() == 80, "reverse pointer uses inverse numeric direction");
        root.queueFree(); LifecycleScheduler.flush();
    }

    private static void key(Window root, KeyEvent.Action action, int code, int modifiers) {
        root.dispatchInput(new KeyEvent(root.getContext(), action, code, 0, modifiers));
    }
    private static void keyboard() {
        Slider slider = new Slider().setMax(10).setStep(3).setValueNoSignal(10);
        Window root = window(slider);
        root.getFocusManager().requestFocus(slider, null);
        int[] done = {0}; slider.changeCompleted().connect(slider, ignored -> done[0]++);
        key(root, KeyEvent.Action.PRESS, GLFW.GLFW_KEY_LEFT, 0);
        check(slider.getValue() == 9 && done[0] == 0, "left from non-divisible max goes to preceding grid point");
        key(root, KeyEvent.Action.REPEAT, GLFW.GLFW_KEY_LEFT, 0);
        check(slider.getValue() == 6, "repeat edits value");
        key(root, KeyEvent.Action.RELEASE, GLFW.GLFW_KEY_LEFT, 0);
        check(done[0] == 1, "one completion for key repeat group");
        slider.setReverse(true);
        key(root, KeyEvent.Action.PRESS, GLFW.GLFW_KEY_RIGHT, 0);
        key(root, KeyEvent.Action.RELEASE, GLFW.GLFW_KEY_RIGHT, 0);
        check(slider.getValue() == 3, "reverse affects arrow direction");
        key(root, KeyEvent.Action.PRESS, GLFW.GLFW_KEY_END, 0);
        key(root, KeyEvent.Action.RELEASE, GLFW.GLFW_KEY_END, 0);
        check(slider.getValue() == 10, "End reaches exact maximum");
        slider.setRangeNoSignal(true).setRangeValueNoSignal(new Slider.RangeValue(3, 9));
        key(root, KeyEvent.Action.PRESS, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_MOD_SHIFT);
        key(root, KeyEvent.Action.RELEASE, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_MOD_SHIFT);
        check(slider.getActiveThumb() == 0, "Shift+Right selects visual right thumb under reverse");
        slider.setKeyboard(false);
        key(root, KeyEvent.Action.PRESS, GLFW.GLFW_KEY_HOME, 0);
        check(slider.getRangeValue().equals(new Slider.RangeValue(3, 9)), "keyboard can be disabled");
        root.queueFree(); LifecycleScheduler.flush();
    }

    private static void cancellations() {
        for (int action = 0; action < 6; action++) {
            Slider slider = new Slider().setValueNoSignal(25);
            Window root = window(slider);
            int[] done = {0}; slider.changeCompleted().connect(slider, ignored -> done[0]++);
            WindowInputs.press(root, 42, 27, null); WindowInputs.move(root, 67, 27);
            check(slider.getValue() == 50, "drag updates before cancellation");
            switch (action) {
                case 0 -> slider.setDisabled(true);
                case 1 -> slider.setVisible(false);
                case 2 -> root.cancelInteractions();
                case 3 -> slider.queueFree();
                case 4 -> {
                    Control modal = new Control() { @Override public boolean getFocusScope() { return true; } };
                    modal.setRequestedSize(160, 100);
                    root.getPopupOverlay().addChild(modal); root.layoutViewport(160, 100, FONT);
                }
                default -> root.getFocusManager().requestFocus(null, null);
            }
            WindowInputs.release(root, 117, 27);
            check(slider.getValue() == 50 && done[0] == 0 && !slider.getDragging(), "cancel preserves last valid value without stale release");
            root.queueFree(); LifecycleScheduler.flush();
        }
    }

    private static void scrolledAndTransformed() {
        for (boolean embedded : new boolean[]{false, true}) {
            Slider slider = new Slider().setRangeNoSignal(true).setRangeValueNoSignal(new Slider.RangeValue(20, 80));
            slider.setPosition(80, 100).setRequestedSize(114, 15);
            Control content = new Control().setMinimum(320, 220); content.addChild(slider);
            Scroll scroll = new Scroll().setEmbedded(embedded).setContent(content);
            Window root = new Window(true).setContent(scroll).setOverlay(new Overlay());
            root.layoutViewport(160, 100, FONT); scroll.setScroll(50, 60);
            Rect thumb = EventGeometry.displayed(slider, slider.getThumbBounds(0));
            int x = centerX(thumb), y = centerY(thumb);
            check(WindowInputs.press(root, x, y, null), "nested Scroll routes press using displayed geometry");
            WindowInputs.move(root, x + 15, y); WindowInputs.release(root, x + 15, y);
            check(slider.getRangeValue().equals(new Slider.RangeValue(35, 80)), "Scroll offset applied once, including outside thumb");
            WindowInputs.move(root, x + 15, y);
            check(root.tooltipTarget(x + 15, y) == slider, "Window selects Slider tooltip after scroll clipping");
            root.getFocusManager().requestFocus(slider, null);
            root.getFocusManager().keyboardNavigation();
            checkOutline(root, slider);
            Control modal = new Control() { @Override public boolean getFocusScope() { return true; } };
            modal.setRequestedSize(160, 100);
            root.getPopupOverlay().addChild(modal); root.layoutViewport(160, 100, FONT);
            check(root.tooltipTarget(x + 15, y) != slider, "higher scope blocks underlying tooltip");
            root.queueFree(); LifecycleScheduler.flush();
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
