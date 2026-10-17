package io.github.luyingyilong.geg.client.ui.input;

/** Standalone parser regression checks; run with java -ea. */
public final class DecimalExpressionTest {
    private DecimalExpressionTest() {
    }

    public static void main(String[] args) {
        equal("2", DecimalExpression.quantity("1+1"));
        equal("14", DecimalExpression.quantity("2*(3+4)"));
        equal("0.25", DecimalExpression.coin("1/4"));
        equal("2", DecimalExpression.quantity("-(-2)"));
        equal("1.00", DecimalExpression.coin("+1"));
        invalid(() -> DecimalExpression.quantity("1/4"));
        invalid(() -> DecimalExpression.coin("1/3"));
        invalid(() -> DecimalExpression.coin("1/8"));
        invalid(() -> DecimalExpression.coin("1/0"));
        invalid(() -> DecimalExpression.quantity("2*(3+4"));
        invalid(() -> DecimalExpression.quantity("2a"));
        invalid(() -> DecimalExpression.quantity("0"));
        invalid(() -> DecimalExpression.coin("92233720368547758.08"));
        invalid(() -> DecimalExpression.quantity("2147483648"));
        invalid(() -> DecimalExpression.quantity("(((((((((1)))))))))"));
        invalid(() -> DecimalExpression.quantity("1".repeat(65)));
    }

    private static void equal(String expected, String actual) {
        if (!expected.equals(actual)) throw new AssertionError(expected + " != " + actual);
    }

    private static void invalid(Runnable check) {
        try {
            check.run();
        } catch (IllegalArgumentException | ArithmeticException expected) {
            return;
        }
        throw new AssertionError("Expected invalid expression");
    }
}
