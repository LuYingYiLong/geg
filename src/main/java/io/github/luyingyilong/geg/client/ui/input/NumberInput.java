package io.github.luyingyilong.geg.client.ui.input;

import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.TextFieldTheme;
import io.github.luyingyilong.geg.client.ui.theme.OreTheme;
import net.minecraft.network.chat.Component;

import java.math.BigDecimal;
import java.util.Objects;

/** An Input whose committed value is an exact integer or coin amount. */
public final class NumberInput extends Input {
    public enum Kind { QUANTITY, COIN }
    /** WARNING keeps invalid text; CLAMP applies bounds on Enter, focus loss or Form submission. */
    public enum BoundaryStrategy { WARNING, CLAMP }

    private final Kind kind;
    private final NumberInputRules rules;
    private Component error;

    /** Compatibility shortcut for coin fields; explicit minimumValue can also configure the lower bound. */
    public NumberInput allowZero(boolean allowZero) {
        return minimumValue(allowZero ? BigDecimal.ZERO : new BigDecimal("0.01"));
    }

    public NumberInput minimumValue(BigDecimal minimum) {
        BigDecimal previous = rules.minimum();
        rules.minimum(minimum);
        if (previous.compareTo(rules.minimum()) != 0) refreshRangeWarning();
        return this;
    }
    public NumberInput minimumValue(long minimum) { return minimumValue(BigDecimal.valueOf(minimum)); }
    public NumberInput minimumValue(String minimum) { return minimumValue(new BigDecimal(minimum)); }
    public BigDecimal minimumValue() { return rules.minimum(); }
    public NumberInput maximumValue(BigDecimal maximum) {
        BigDecimal previous = rules.maximum();
        rules.maximum(maximum);
        if (previous.compareTo(rules.maximum()) != 0) refreshRangeWarning();
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

    public NumberInput(float depth, Kind kind) {
        super(depth);
        this.kind = Objects.requireNonNull(kind);
        rules = new NumberInputRules(kind);
        setMaxLength(DecimalExpression.MAX_LENGTH);
        setPattern(DecimalExpression.INPUT_PATTERN);
        autoUpdateOnChange(false);
    }

    public Component error() {
        return error;
    }

    @Override protected boolean supportsInputMethod() { return false; }

    public void maximumQuantity(int maximum) {
        BigDecimal previous = rules.maximum();
        rules.maximumQuantity(maximum);
        if (previous.compareTo(rules.maximum()) != 0) refreshRangeWarning();
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
        return rules.empty() ? Component.translatable("market.geg.form.invalid_quantity")
                : Component.translatable("ore.geg.number.out_of_bounds", rules.minimum().toPlainString(), rules.maximum().toPlainString());
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
            return true;
        } catch (NumberInputRules.OutOfBounds exception) {
            setError(rangeError());
            return false;
        } catch (ArithmeticException | IllegalArgumentException exception) {
            setError(Component.translatable(kind == Kind.QUANTITY
                    ? "market.geg.form.invalid_quantity" : "market.geg.form.invalid_coin"));
            return false;
        }
    }

    @Override protected void onTextChanged() {
        super.onTextChanged();
        if (rules != null) refreshRangeWarning();
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
