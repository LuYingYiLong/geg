package io.github.luyingyilong.geg.client.ui;

import io.github.luyingyilong.geg.client.ui.util.DecimalExpression;

import brachy.modularui.screen.viewport.ModularGuiContext;
import net.minecraft.network.chat.Component;

/** An Input whose committed value is an exact integer or coin amount. */
public final class NumberInput extends Input {
    public enum Kind { QUANTITY, COIN }

    private final Kind kind;
    private Component error;
    private boolean allowZero;

    public NumberInput allowZero(boolean allowZero) { this.allowZero = allowZero; return this; }
    private int maximumQuantity = Integer.MAX_VALUE;

    public NumberInput(float depth, Kind kind) {
        super(depth);
        this.kind = kind;
        setMaxLength(DecimalExpression.MAX_LENGTH);
        setPattern(DecimalExpression.INPUT_PATTERN);
        autoUpdateOnChange(false);
    }

    public Component error() {
        return error;
    }

    @Override protected boolean supportsInputMethod() { return false; }

    public void maximumQuantity(int maximum) {
        maximumQuantity = maximum;
    }

    public void clearError() {
        error = null;
    }

    public boolean commit() {
        try {
            String formatted = kind == Kind.QUANTITY
                    ? DecimalExpression.quantity(getText()) : DecimalExpression.coin(getText(), allowZero);
            if (kind == Kind.QUANTITY && Integer.parseInt(formatted) > maximumQuantity) {
                throw new IllegalArgumentException("Quantity exceeds available amount");
            }
            if (!editingHandler().replaceAll(formatted)) throw new IllegalArgumentException("Invalid formatted value");
            error = null;
            return true;
        } catch (ArithmeticException | IllegalArgumentException exception) {
            error = Component.translatable(kind == Kind.QUANTITY
                    ? "market.geg.form.invalid_quantity" : "market.geg.form.invalid_coin");
            return false;
        }
    }

    @Override protected void onTextChanged() {
        super.onTextChanged();
        error = null;
    }

    @Override
    public void onRemoveFocus(ModularGuiContext context) {
        commit();
        super.onRemoveFocus(context);
    }
}
