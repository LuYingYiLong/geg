package io.github.luyingyilong.geg.client.ui.input;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Exact numeric bounds shared by live warnings and commit-time normalization. */
final class NumberInputRules {
    static final class OutOfBounds extends IllegalArgumentException {
        private OutOfBounds() { super("Value is outside the input range"); }
    }

    private final NumberInput.Kind kind;
    private BigDecimal minimum;
    private BigDecimal maximum;
    private NumberInput.BoundaryStrategy strategy = NumberInput.BoundaryStrategy.WARNING;

    NumberInputRules(NumberInput.Kind kind) {
        this.kind = Objects.requireNonNull(kind);
        minimum = kind == NumberInput.Kind.QUANTITY ? BigDecimal.ONE : new BigDecimal("0.01");
        maximum = kind == NumberInput.Kind.QUANTITY ? BigDecimal.valueOf(Integer.MAX_VALUE) : BigDecimal.valueOf(Long.MAX_VALUE, 2);
    }

    BigDecimal minimum() { return minimum; }
    BigDecimal maximum() { return maximum; }
    NumberInput.BoundaryStrategy strategy() { return strategy; }
    void strategy(NumberInput.BoundaryStrategy strategy) { this.strategy = Objects.requireNonNull(strategy); }

    void minimum(BigDecimal value) {
        BigDecimal exact = exact(Objects.requireNonNull(value));
        if (exact.compareTo(maximum) > 0) throw new IllegalArgumentException("Minimum exceeds maximum");
        minimum = exact;
    }

    void maximum(BigDecimal value) {
        BigDecimal exact = exact(Objects.requireNonNull(value));
        if (exact.compareTo(minimum) < 0) throw new IllegalArgumentException("Maximum is below minimum");
        maximum = exact;
    }

    /** Availability can be zero: an empty quantity range must reject, never manufacture one item. */
    void maximumQuantity(int available) {
        if (kind != NumberInput.Kind.QUANTITY) throw new IllegalStateException("Not a quantity field");
        maximum = BigDecimal.valueOf(Math.max(0, available));
    }

    boolean empty() { return minimum.compareTo(maximum) > 0; }

    String resolve(String expression) {
        BigDecimal value = DecimalExpression.evaluate(expression);
        if (empty()) throw new OutOfBounds();
        if (value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
            if (strategy == NumberInput.BoundaryStrategy.WARNING) throw new OutOfBounds();
            value = value.max(minimum).min(maximum);
        }
        return exact(value).toPlainString();
    }

    private BigDecimal exact(BigDecimal value) {
        BigDecimal scaled = value.setScale(kind == NumberInput.Kind.QUANTITY ? 0 : 2, RoundingMode.UNNECESSARY);
        if (kind == NumberInput.Kind.QUANTITY) scaled.intValueExact();
        else scaled.unscaledValue().longValueExact();
        return scaled;
    }
}
