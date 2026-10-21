package io.github.luyingyilong.ore.control.input;
import java.math.BigDecimal;
public final class NumericExpressionFixture {
    public static String quantity(String source) {
        int value = DecimalExpression.evaluate(source).intValueExact();
        if (value <= 0) throw new IllegalArgumentException();
        return Integer.toString(value);
    }
    public static String coin(String source) { return coin(source, false); }
    public static String coin(String source, boolean allowZero) {
        long minor = DecimalExpression.evaluate(source).movePointRight(2).longValueExact();
        if (minor < 0 || minor == 0 && !allowZero) throw new IllegalArgumentException();
        return BigDecimal.valueOf(minor, 2).toPlainString();
    }
}
