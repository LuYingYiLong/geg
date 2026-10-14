package io.github.luyingyilong.geg.client.ui;

import brachy.modularui.screen.viewport.ModularGuiContext;
import net.minecraft.network.chat.Component;

import java.util.regex.Pattern;

/** An Input whose committed value is an exact integer or coin amount. */
public final class NumberInput extends Input {
    private static final Pattern ALLOWED = Pattern.compile("[0-9.\\s+*/()\\-]*");

    public enum Kind { QUANTITY, COIN }

    private final Kind kind;
    private Component error;
    private int maximumQuantity = Integer.MAX_VALUE;

    public NumberInput(float depth, Kind kind) {
        super(depth);
        this.kind = kind;
        setMaxLength(DecimalExpression.MAX_LENGTH);
        setPattern(ALLOWED);
        autoUpdateOnChange(false);
    }

    public Component error() {
        return error;
    }

    public void maximumQuantity(int maximum) {
        maximumQuantity = maximum;
    }

    public void clearError() {
        error = null;
    }

    public boolean commit() {
        try {
            String formatted = kind == Kind.QUANTITY
                    ? DecimalExpression.quantity(getText()) : DecimalExpression.coin(getText());
            if (kind == Kind.QUANTITY && Integer.parseInt(formatted) > maximumQuantity) {
                throw new IllegalArgumentException("Quantity exceeds available amount");
            }
            setText(formatted);
            error = null;
            return true;
        } catch (ArithmeticException | IllegalArgumentException exception) {
            error = Component.translatable(kind == Kind.QUANTITY
                    ? "market.geg.form.invalid_quantity" : "market.geg.form.invalid_coin");
            return false;
        }
    }

    @Override
    public void onRemoveFocus(ModularGuiContext context) {
        commit();
        super.onRemoveFocus(context);
    }
}
