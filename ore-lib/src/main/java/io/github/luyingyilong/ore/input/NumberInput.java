package io.github.luyingyilong.ore.input;

import io.github.luyingyilong.ore.signal.*;

import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.TextFieldTheme;
import io.github.luyingyilong.ore.theme.OreTheme;
import net.minecraft.network.chat.Component;

import java.math.BigDecimal;
import java.util.Objects;

/** An Input whose committed value is an exact integer or fixed-precision decimal. */
public final class NumberInput extends Input {
    private final SignalSource<BigDecimal> valueCommitted = new SignalSource<>(this, "valueCommitted", "BigDecimal");
    public Signal<BigDecimal> valueCommitted() { return valueCommitted.signal(); }
    /** WARNING keeps invalid text; CLAMP applies bounds on Enter, focus loss or Form submission. */
    public enum BoundaryStrategy { WARNING, CLAMP }

    private final NumberInputRules rules;
    private Component error;

    public NumberInput(float depth) {
        super(depth);
        rules = new NumberInputRules(0);
        setMaxLength(DecimalExpression.MAX_LENGTH);
        setPattern(DecimalExpression.INPUT_PATTERN);
        autoUpdateOnChange(false);
    }
    public NumberInput() { this(0); }
    public NumberInput decimalPlaces(int places) { rules.decimalPlaces(places); refreshRangeWarning(); return this; }
    public int decimalPlaces() { return rules.decimalPlaces(); }

    public NumberInput minimumValue(BigDecimal minimum) {
        BigDecimal previous = rules.minimum();
        rules.minimum(minimum);
        if (!Objects.equals(previous, rules.minimum())) refreshRangeWarning();
        return this;
    }
    public NumberInput minimumValue(long minimum) { return minimumValue(BigDecimal.valueOf(minimum)); }
    public NumberInput minimumValue(String minimum) { return minimumValue(new BigDecimal(minimum)); }
    public BigDecimal minimumValue() { return rules.minimum(); }
    public NumberInput maximumValue(BigDecimal maximum) {
        BigDecimal previous = rules.maximum();
        rules.maximum(maximum);
        if (!Objects.equals(previous, rules.maximum())) refreshRangeWarning();
        return this;
    }
    public NumberInput maximumValue(long maximum) { return maximumValue(BigDecimal.valueOf(maximum)); }
    public NumberInput maximumValue(String maximum) { return maximumValue(new BigDecimal(maximum)); }
    public BigDecimal maximumValue() { return rules.maximum(); }
    public NumberInput boundaryStrategy(BoundaryStrategy strategy) {
        BoundaryStrategy previous = rules.strategy();
        rules.strategy(strategy);
        if (previous != strategy) refreshRangeWarning();
        return this;
    }
    public BoundaryStrategy boundaryStrategy() { return rules.strategy(); }

    public Component error() {
        return error;
    }

    @Override protected boolean supportsInputMethod() { return false; }

    public void availableMaximum(BigDecimal maximum) {
        BigDecimal previous = rules.maximum();
        rules.availableMaximum(maximum);
        if (!Objects.equals(previous, rules.maximum())) refreshRangeWarning();
    }

    public void clearError() {
        setError(null);
    }

    private void setError(Component error) {
        if (Objects.equals(this.error, error)) return;
        this.error = error;
        invalidateInputLayout();
    }

    private Component rangeError() {
        if (rules.empty()) return Component.translatable("ore.number.empty_range");
        return Component.translatable("ore.number.out_of_bounds",
                rules.minimum() == null ? "−∞" : rules.minimum().toPlainString(),
                rules.maximum() == null ? "+∞" : rules.maximum().toPlainString());
    }

    private void refreshRangeWarning() {
        try { rules.resolve(getText()); setError(null); }
        catch (NumberInputRules.OutOfBounds exception) { setError(rangeError()); }
        catch (ArithmeticException | IllegalArgumentException exception) { setError(null); }
    }

    public boolean commit() {
        try {
            String formatted = rules.resolve(getText());
            if (!editingHandler().replaceAll(formatted)) throw new IllegalArgumentException("Invalid formatted value");
            clearError();
            valueCommitted.emit(new BigDecimal(formatted));
            return true;
        } catch (NumberInputRules.OutOfBounds exception) {
            setError(rangeError());
            return false;
        } catch (ArithmeticException | IllegalArgumentException exception) {
            setError(Component.translatable("ore.number.invalid", rules.decimalPlaces()));
            return false;
        }
    }

    @Override protected void onTextChanged() {
        if (rules != null) refreshRangeWarning();
        super.onTextChanged();
    }

    @Override protected void drawText(ModularGuiContext context, TextFieldTheme theme) {
        int color = renderer.getColor();
        if (error != null) renderer.setColor(OreTheme.TEXT_ERROR);
        try { super.drawText(context, theme); }
        finally { renderer.setColor(color); }
    }

    @Override
    public void onRemoveFocus(ModularGuiContext context) {
        commit();
        super.onRemoveFocus(context);
    }
}
