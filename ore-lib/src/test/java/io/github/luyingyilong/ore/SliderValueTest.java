package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.form.*;
import io.github.luyingyilong.ore.layout.LifecycleScheduler;
import net.minecraft.network.chat.Component;

import java.util.*;

public final class SliderValueTest {
    public static void main(String[] args) {
        numbers(); modesAndSignals(); forms(); marksAndLifetime();
        System.out.println("ORE_SLIDER_VALUE_PASS");
    }

    private static void numbers() {
        Slider slider = new Slider();
        check(slider.getMin() == 0 && slider.getMax() == 100 && slider.getStep() == 1 && slider.getValue() == 0, "defaults");
        check(slider.getKeyboard() && !slider.getRange() && !slider.getDisabled() && !slider.getReverse() && !slider.getShowTicks(), "flags");
        slider.setLimits(-1, 1).setStep(0.1);
        slider.setValue(0.30000000000000004);
        check(slider.getValue() == 0.3, "decimal grid has no accumulated binary error");
        slider.setValue(-100); check(slider.getValue() == -1, "lower clamp");
        slider.setValue(100); check(slider.getValue() == 1, "upper clamp");
        slider.setLimits(0, 10).setStep(3).setValue(9.7);
        check(slider.getValue() == 10, "non-divisible maximum remains reachable");
        slider.setValue(7); check(slider.getValue() == 6, "nearest step");
        slider.setValue(7.5); check(slider.getValue() == 9, "half step rounds upward");
        for (double invalid : new double[]{Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
            rejects(() -> slider.setValue(invalid)); rejects(() -> slider.setMin(invalid));
            rejects(() -> slider.setMax(invalid)); rejects(() -> slider.setStep(invalid));
        }
        rejects(() -> slider.setMin(10)); rejects(() -> slider.setMax(0));
        rejects(() -> slider.setStep(0)); rejects(() -> slider.setStep(-1));
        check(slider.getValue() == 9 && slider.getMin() == 0 && slider.getMax() == 10 && slider.getStep() == 3, "rejection is atomic");
        slider.setLimits(-Double.MAX_VALUE, Double.MAX_VALUE).setStep(Double.MAX_VALUE).setValue(0);
        check(slider.getValue() == 0, "finite endpoints can have a difference exceeding Double.MAX_VALUE");
        slider.queueFree(); LifecycleScheduler.flush();
    }

    private static void modesAndSignals() {
        Slider slider = new Slider();
        List<Double> scalar = new ArrayList<>(); List<Slider.RangeValue> intervals = new ArrayList<>();
        int[] complete = {0};
        slider.valueChanged().connect(slider, scalar::add);
        slider.rangeChanged().connect(slider, intervals::add);
        slider.changeCompleted().connect(slider, ignored -> complete[0]++);
        slider.setValue(20).setValue(20).setValueNoSignal(30);
        check(scalar.equals(List.of(20.0)) && complete[0] == 0, "programmatic setters, silent setters and repeated values");
        rejectsMode(() -> slider.getRangeValue());
        slider.setRange(true);
        check(slider.getRangeValue().equals(new Slider.RangeValue(0, 30)), "range conversion");
        rejectsMode(() -> slider.getValue()); rejectsMode(() -> slider.setValue(10));
        slider.setRangeValue(new Slider.RangeValue(25, 75)).setRangeValue(new Slider.RangeValue(25, 75));
        slider.setRangeValueNoSignal(new Slider.RangeValue(20, 80));
        check(intervals.equals(List.of(new Slider.RangeValue(0, 30), new Slider.RangeValue(25, 75))), "range notifications once");
        slider.setRange(false);
        check(slider.getValue() == 80 && scalar.equals(List.of(20.0, 80.0)), "keep upper on scalar conversion");
        slider.setRangeNoSignal(true).setMaxNoSignal(60);
        check(slider.getRangeValue().equals(new Slider.RangeValue(0, 60)) && intervals.size() == 2, "silent normalization");
        rejects(() -> new Slider.RangeValue(80, 20));
        slider.queueFree(); LifecycleScheduler.flush();
        check(slider.valueChanged().connectionCount() == 0, "sender lifetime clears connections");
    }

    private static void forms() {
        Slider scalar = new Slider().setValueNoSignal(40);
        Slider range = new Slider().setRangeNoSignal(true).setRangeValueNoSignal(new Slider.RangeValue(20, 70));
        FieldKey<Double> amount = new FieldKey<>("amount", Double.class);
        FieldKey<Slider.RangeValue> interval = new FieldKey<>("interval", Slider.RangeValue.class);
        Form form = new Form().addItem(new FormItem<>(amount, Component.literal("Amount"), FormFields.slider(scalar))
                .addValidator(value -> value >= 20 ? Optional.empty() : Optional.of(Component.literal("Too small"))))
                .addItem(new FormItem<>(interval, Component.literal("Range"), FormFields.sliderRange(range)));
        form.setVisible(true);
        check(form.validate().getValues().getValue(amount) == 40 && !form.getDirty(), "typed read and initial baseline");
        scalar.setValue(10);
        check(form.getDirty() && !form.validate().getValid(), "range validation uses actual changed value");
        range.setRangeValue(new Slider.RangeValue(30, 80));
        form.reset();
        check(scalar.getValue() == 40 && range.getRangeValue().equals(new Slider.RangeValue(20, 70)) && !form.getDirty(), "silent reset restores both types");
        form.setValuesNoSignal(FormValues.builder().setValue(amount, 50.0).setValue(interval, new Slider.RangeValue(10, 90)).build());
        check(form.validate().getValues().getValue(interval).equals(new Slider.RangeValue(10, 90)), "typed batch refill");
        rejects(() -> FormFields.slider(range)); rejects(() -> FormFields.sliderRange(scalar));
        form.queueFree(); LifecycleScheduler.flush();
        check(scalar.isDisposed() && range.isDisposed(), "Form owns controls");
    }

    private static void marksAndLifetime() {
        Slider slider = new Slider();
        slider.setMarks(Map.of(0.0, Component.literal("Low"), 100.0, Component.literal("High")));
        var previous = slider.getOwnedParts().values().stream().filter(Label.class::isInstance).toList();
        slider.setMarks(Map.of(0.0, Component.literal("Minimum"), 100.0, Component.literal("Maximum")));
        check(previous.stream().allMatch(control -> control.isAlive() && slider.getOwnedParts().containsValue(control)), "mark text updates reuse Label nodes");
        rejects(() -> slider.setMarks(Map.of(-1.0, Component.literal("Invalid"))));
        rejects(() -> slider.setMax(90));
        check(slider.getMax() == 100 && slider.getMarks().size() == 2, "invalid bounds retain marks and domain");
        try { slider.getMarks().put(25.0, Component.literal("Write")); throw new AssertionError("mutable marks"); }
        catch (UnsupportedOperationException expected) { }
        slider.setMarks(Map.of()); slider.setMarks(Map.of(0.0, Component.literal("Again")));
        LifecycleScheduler.flush();
        check(previous.stream().allMatch(control -> control.isDisposed()), "old labels free at safe boundary");
        var parts = List.copyOf(slider.getOwnedParts().values());
        slider.queueFree(); LifecycleScheduler.flush();
        check(parts.stream().allMatch(control -> control.isDisposed()), "all internal images and labels free");
    }
    private static void rejects(Runnable run) { try { run.run(); } catch (IllegalArgumentException expected) { return; } throw new AssertionError("Invalid input accepted"); }
    private static void rejectsMode(Runnable run) { try { run.run(); } catch (IllegalStateException expected) { return; } throw new AssertionError("Wrong mode accepted"); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
