package io.github.luyingyilong.geg.money;

import java.math.BigDecimal;
import java.util.regex.Pattern;


public enum MoneyCurrency {
    COIN("coin", Pattern.compile("(?:0|[1-9][0-9]*)(?:\\.[0-9]{1,2})?")), GEM("gem", Pattern.compile("(?:0|[1-9][0-9]*)")),
    ;

    private final String id;
    private final Pattern amountPattern;

    MoneyCurrency(String id, Pattern amountPattern) {
        this.id = id;
        this.amountPattern = amountPattern;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "currency.geg." + id;
    }

    public long parseAmount(String text, boolean allowZero) {
        if (text.length() > 30 || !amountPattern.matcher(text).matches()) {
            throw new IllegalArgumentException("Invalid " + id + " amount");
        }

        try {
            long amount = this == COIN ? new BigDecimal(text).movePointRight(2).longValueExact() : Long.parseLong(text);
            if (amount < 0L || (!allowZero && amount == 0L)) {
                throw new IllegalArgumentException("Amount must be positive");
            }
            return amount;
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException("Amount exceeds the supported range", exception);
        }
    }

    public String format(long amount) {
        return this == COIN ? BigDecimal.valueOf(amount, 2).toPlainString() : Long.toString(amount);
    }
}
