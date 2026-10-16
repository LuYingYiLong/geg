package io.github.luyingyilong.geg.client.ui.util;

import java.math.BigDecimal;

/** Exact, deliberately small arithmetic grammar for market fields. */
public final class DecimalExpression {
    public static final int MAX_LENGTH = 64;
    public static final int MAX_DEPTH = 8;

    private final String source;
    private int offset;
    private int depth;

    private DecimalExpression(String source) {
        this.source = source;
    }

    public static BigDecimal evaluate(String source) {
        if (source == null || source.isBlank() || source.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Invalid expression length");
        }
        DecimalExpression parser = new DecimalExpression(source);
        BigDecimal result = parser.expression();
        parser.spaces();
        if (parser.offset != source.length()) {
            throw new IllegalArgumentException("Unexpected expression character");
        }
        return result;
    }

    public static String quantity(String source) {
        int value = evaluate(source).intValueExact();
        if (value <= 0) throw new IllegalArgumentException("Nonpositive quantity");
        return Integer.toString(value);
    }

    public static String coin(String source) {
        long minor = evaluate(source).movePointRight(2).longValueExact();
        if (minor <= 0) throw new IllegalArgumentException("Nonpositive coin amount");
        return BigDecimal.valueOf(minor, 2).toPlainString();
    }

    private BigDecimal expression() {
        BigDecimal value = term();
        while (true) {
            if (take('+')) value = value.add(term());
            else if (take('-')) value = value.subtract(term());
            else return value;
        }
    }

    private BigDecimal term() {
        BigDecimal value = factor();
        while (true) {
            if (take('*')) value = value.multiply(factor());
            else if (take('/')) {
                BigDecimal divisor = factor();
                if (divisor.signum() == 0) throw new IllegalArgumentException("Division by zero");
                try {
                    value = value.divide(divisor);
                } catch (ArithmeticException exception) {
                    throw new IllegalArgumentException("Nonterminating division", exception);
                }
            } else return value;
        }
    }

    private BigDecimal factor() {
        if (take('+')) return factor();
        if (take('-')) return factor().negate();
        if (take('(')) {
            if (++depth > MAX_DEPTH) throw new IllegalArgumentException("Expression too deep");
            BigDecimal value = expression();
            if (!take(')')) throw new IllegalArgumentException("Unclosed parenthesis");
            depth--;
            return value;
        }
        spaces();
        int start = offset;
        boolean digit = false;
        boolean dot = false;
        while (offset < source.length()) {
            char current = source.charAt(offset);
            if (current >= '0' && current <= '9') digit = true;
            else if (current == '.' && !dot) dot = true;
            else break;
            offset++;
        }
        if (!digit) throw new IllegalArgumentException("Expected number");
        try {
            return new BigDecimal(source.substring(start, offset));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid number", exception);
        }
    }

    private boolean take(char character) {
        spaces();
        if (offset < source.length() && source.charAt(offset) == character) {
            offset++;
            return true;
        }
        return false;
    }

    private void spaces() {
        while (offset < source.length() && Character.isWhitespace(source.charAt(offset))) offset++;
    }
}
