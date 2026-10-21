package io.github.luyingyilong.ore.control.input;

import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.TextFieldTheme;
import io.github.luyingyilong.ore.signal.Signal;
import io.github.luyingyilong.ore.signal.SignalSource;
import io.github.luyingyilong.ore.theme.OreTheme;
import net.minecraft.network.chat.Component;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * An Input whose committed value is an exact integer or fixed-precision decimal.
 */
public final class NumberInput extends Input {
    public static void registerTheme(io.github.luyingyilong.ore.theme.ThemeDB db) { io.github.luyingyilong.ore.theme.OreTheme.registerType(db,"NumberInput"); }
    private final SignalSource<BigDecimal> valueCommitted = new SignalSource<>(this, "valueCommitted", "BigDecimal");
    private final NumberInputRules rules;
    private Component error;

    public NumberInput(float depth) {
        super(depth);
        rules = new NumberInputRules(0);
        setMaxLength(DecimalExpression.MAX_LENGTH);
        setPattern(DecimalExpression.INPUT_PATTERN);
        autoUpdateOnChange(false);
    }
    public NumberInput() {
        this(0);
    }

    public Signal<BigDecimal> valueCommitted() {
        return valueCommitted.signal();
    }

    public int getDecimalPlaces() {
        return rules.decimalPlaces();
    }

    public NumberInput setDecimalPlaces(int places) {
        rules.decimalPlaces(places);
        refreshRangeWarning();
        return this;
    }

    public BigDecimal getMinimumValue() {
        return rules.minimum();
    }

    public NumberInput setMinimumValue(BigDecimal minimum) {
        BigDecimal previous = rules.minimum();
        rules.minimum(minimum);
        if (!Objects.equals(previous, rules.minimum())) refreshRangeWarning();
        return this;
    }

    public NumberInput setMinimumValue(long minimum) {
        return setMinimumValue(BigDecimal.valueOf(minimum));
    }

    public NumberInput setMinimumValue(String minimum) {
        return setMinimumValue(new BigDecimal(minimum));
    }

    public BigDecimal getMaximumValue() {
        return rules.maximum();
    }

    public NumberInput setMaximumValue(BigDecimal maximum) {
        BigDecimal previous = rules.maximum();
        rules.maximum(maximum);
        if (!Objects.equals(previous, rules.maximum())) refreshRangeWarning();
        return this;
    }

    public NumberInput setMaximumValue(long maximum) {
        return setMaximumValue(BigDecimal.valueOf(maximum));
    }

    public NumberInput setMaximumValue(String maximum) {
        return setMaximumValue(new BigDecimal(maximum));
    }

    public BoundaryStrategy getBoundaryStrategy() {
        return rules.strategy();
    }

    public NumberInput setBoundaryStrategy(BoundaryStrategy strategy) {
        BoundaryStrategy previous = rules.strategy();
        rules.strategy(strategy);
        if (previous != strategy) refreshRangeWarning();
        return this;
    }

    public Component getError() {
        return error;
    }

    private void setError(Component error) {
        if (Objects.equals(this.error, error)) return;
        this.error = error;
        invalidateInputLayout();
    }

    @Override
    protected boolean supportsInputMethod() {
        return false;
    }

    public void setAvailableMaximum(BigDecimal maximum) {
        BigDecimal previous = rules.maximum();
        rules.availableMaximum(maximum);
        if (!Objects.equals(previous, rules.maximum())) refreshRangeWarning();
    }

    public void clearError() {
        setError(null);
    }

    private Component rangeError() {
        if (rules.empty()) return Component.translatable("ore.number.empty_range");
        return Component.translatable("ore.number.out_of_bounds",
                rules.minimum() == null ? "−∞" : rules.minimum().toPlainString(),
                rules.maximum() == null ? "+∞" : rules.maximum().toPlainString());
    }

    private void refreshRangeWarning() {
        try {
            rules.resolve(getText());
            setError(null);
        } catch (NumberInputRules.OutOfBounds exception) {
            setError(rangeError());
        } catch (ArithmeticException | IllegalArgumentException exception) {
            setError(null);
        }
    }

    public boolean commit() {
        // Normalization remains an explicit edit; live form validation only reads the preview.
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

    public BigDecimal getPreviewValue() { return new BigDecimal(rules.resolve(getText())); }

    @Override
    protected void onTextChanged() {
        if (rules != null) refreshRangeWarning();
        super.onTextChanged();
    }

    @Override
    protected void drawText(ModularGuiContext context, TextFieldTheme theme) {
        int color = renderer.getColor();
        if (error != null) renderer.setColor(resolveTheme(OreTheme.TEXT_ERROR_KEY));
        try {
            super.drawText(context, theme);
        } finally {
            renderer.setColor(color);
        }
    }

    @Override
    public void onRemoveFocus(ModularGuiContext context) {
        if (!isHostFocusSuspending()) commit();
        super.onRemoveFocus(context);
    }

    /**
     * WARNING keeps invalid text; CLAMP applies bounds on Enter, focus loss or Form submission.
     */
    public enum BoundaryStrategy {WARNING, CLAMP}
}
