package io.github.luyingyilong.ore.input;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Exact numeric bounds shared by live warnings and commit-time normalization. */
final class NumberInputRules {
    static final class OutOfBounds extends IllegalArgumentException {
        private OutOfBounds() { super("Value is outside the input range"); }
    }

    private int decimalPlaces;
    private BigDecimal minimum;
    private BigDecimal maximum;
    private NumberInput.BoundaryStrategy strategy = NumberInput.BoundaryStrategy.WARNING;

    NumberInputRules(int decimalPlaces) { decimalPlaces(decimalPlaces); }
    void decimalPlaces(int value) {
        if (value < 0 || value > 18) throw new IllegalArgumentException("Precision must be between 0 and 18");
        if (minimum != null) minimum.setScale(value, RoundingMode.UNNECESSARY);
        if (maximum != null) maximum.setScale(value, RoundingMode.UNNECESSARY);
        decimalPlaces = value;
    }
    int decimalPlaces() { return decimalPlaces; }

    BigDecimal minimum() { return minimum; }
    BigDecimal maximum() { return maximum; }
    NumberInput.BoundaryStrategy strategy() { return strategy; }
    void strategy(NumberInput.BoundaryStrategy strategy) { this.strategy = Objects.requireNonNull(strategy); }

    void minimum(BigDecimal value) {
        BigDecimal exact = value == null ? null : exact(value);
        if (exact != null && maximum != null && exact.compareTo(maximum) > 0) throw new IllegalArgumentException("Minimum exceeds maximum");
        minimum = exact;
    }

    void maximum(BigDecimal value) {
        BigDecimal exact = value == null ? null : exact(value);
        if (exact != null && minimum != null && exact.compareTo(minimum) < 0) throw new IllegalArgumentException("Maximum is below minimum");
        maximum = exact;
    }

    /** Dynamic availability may make a range empty; callers must then reject submission. */
    void availableMaximum(BigDecimal available) { maximum = exact(Objects.requireNonNull(available)); }
    boolean empty() { return minimum != null && maximum != null && minimum.compareTo(maximum) > 0; }

    String resolve(String expression) {
        BigDecimal value = DecimalExpression.evaluate(expression);
        if (empty()) throw new OutOfBounds();
        if ((minimum != null && value.compareTo(minimum) < 0) || (maximum != null && value.compareTo(maximum) > 0)) {
            if (strategy == NumberInput.BoundaryStrategy.WARNING) throw new OutOfBounds();
            if (minimum != null) value = value.max(minimum);
            if (maximum != null) value = value.min(maximum);
        }
        return exact(value).toPlainString();
    }

    private BigDecimal exact(BigDecimal value) {
        return value.setScale(decimalPlaces, RoundingMode.UNNECESSARY);
    }
}
