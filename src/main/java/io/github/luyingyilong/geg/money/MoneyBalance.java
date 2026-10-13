package io.github.luyingyilong.geg.money;

public record MoneyBalance(long coinMinorUnits, long gem) {
    public static final MoneyBalance ZERO = new MoneyBalance(0L, 0L);

    public MoneyBalance {
        if (coinMinorUnits < 0L || gem < 0L) {
            throw new IllegalArgumentException("Money balances cannot be negative");
        }
    }

    public long amount(MoneyCurrency currency) {
        return currency == MoneyCurrency.COIN ? coinMinorUnits : gem;
    }

    public MoneyBalance with(MoneyCurrency currency, long amount) {
        return currency == MoneyCurrency.COIN ? new MoneyBalance(amount, gem) : new MoneyBalance(coinMinorUnits, amount);
    }
}
