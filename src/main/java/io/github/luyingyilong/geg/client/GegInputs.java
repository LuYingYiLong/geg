package io.github.luyingyilong.geg.client;

import io.github.luyingyilong.ore.control.input.NumberInput;
import java.math.BigDecimal;

/** Economic bounds belong to GEG; Ore only knows numeric precision and optional bounds. */
public final class GegInputs {
    private GegInputs() { }
    public static NumberInput quantity(float depth) {
        return new NumberInput(depth).setMinimumValue(1).setMaximumValue(Integer.MAX_VALUE);
    }
    public static NumberInput coin(float depth, boolean allowZero) {
        return new NumberInput(depth).setDecimalPlaces(2).setMinimumValue(allowZero ? "0" : "0.01")
                .setMaximumValue(BigDecimal.valueOf(Long.MAX_VALUE, 2));
    }
}
