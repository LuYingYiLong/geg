package io.github.luyingyilong.ore.input;

import brachy.modularui.widgets.textfield.TextFieldRenderer;

import java.math.BigDecimal;
import java.util.List;

/** Commit rules used by the actual widget, without requiring a live ModularUI screen. */
public final class NumberInputRulesTest {
    public static void main(String[] args) {
        NumberInputRules unrestricted = new NumberInputRules(0);
        equal("-999999999999999999999", unrestricted.resolve("-999999999999999999999"));
        check(unrestricted.minimum() == null && unrestricted.maximum() == null);
        unrestricted.decimalPlaces(4);
        equal("0.0625", unrestricted.resolve("1/16"));
        invalid(() -> unrestricted.resolve("0.00001"));
        unrestricted.minimum(BigDecimal.ZERO);
        outside(() -> unrestricted.resolve("-1"));
        unrestricted.minimum(null);
        equal("-1.0000", unrestricted.resolve("-1"));
        invalid(() -> unrestricted.decimalPlaces(-1));
        invalid(() -> unrestricted.decimalPlaces(19));
        NumberInputRules quantity = new NumberInputRules(0);
        quantity.minimum(BigDecimal.ONE);
        check(quantity.strategy() == NumberInput.BoundaryStrategy.WARNING);
        quantity.maximum(new BigDecimal("64"));
        equal("14", quantity.resolve("2*(3+4)"));
        outside(() -> quantity.resolve("65"));
        outside(() -> quantity.resolve("0"));
        quantity.strategy(NumberInput.BoundaryStrategy.CLAMP);
        equal("64", quantity.resolve("2*40"));
        equal("1", quantity.resolve("-5"));
        equal("64", quantity.resolve("99999999999999999999"));
        equal("1", quantity.resolve("1/2"));
        invalid(() -> quantity.resolve("1.5")); // In-range fractional quantities are never rounded.
        quantity.availableMaximum(BigDecimal.valueOf(0));
        outside(() -> quantity.resolve("1")); // A zero stock/affordability limit cannot create an item.
        quantity.availableMaximum(BigDecimal.valueOf(8));
        equal("8", quantity.resolve("16"));
        quantity.minimum(new BigDecimal("-10"));
        equal("-10", quantity.resolve("-100"));
        equal("-2", quantity.resolve("-2"));
        invalid(() -> quantity.maximum(new BigDecimal("1.5")));
        invalid(() -> quantity.minimum(new BigDecimal("9")));

        NumberInputRules coin = new NumberInputRules(2);
        coin.minimum(new BigDecimal("0.01"));
        coin.maximum(BigDecimal.valueOf(Long.MAX_VALUE, 2));
        equal("0.25", coin.resolve("1/4"));
        outside(() -> coin.resolve("0"));
        coin.strategy(NumberInput.BoundaryStrategy.CLAMP);
        equal("0.01", coin.resolve("0"));
        equal("0.01", coin.resolve("-1"));
        equal("0.01", coin.resolve("0.001"));
        equal("92233720368547758.07", coin.resolve("92233720368547758.08"));
        equal("9007199254740993.00", coin.resolve("9007199254740993")); // No double conversion.
        invalid(() -> coin.resolve("0.015"));
        invalid(() -> coin.resolve("1/3"));
        invalid(() -> coin.resolve("1/0"));
        invalid(() -> coin.resolve("2*(3+4"));
        invalid(() -> coin.resolve("x"));
        invalid(() -> coin.resolve("1".repeat(65)));
        invalid(() -> coin.minimum(new BigDecimal("0.001")));
        coin.maximum(new BigDecimal("92233720368547758.08")); // No built-in currency storage limit.
        coin.minimum(BigDecimal.ZERO);
        equal("0.00", coin.resolve("0")); // Existing zero-attachment mail composers retain their behavior.
        coin.maximum(new BigDecimal("10.00"));
        equal("10.00", coin.resolve("20"));
        coin.strategy(NumberInput.BoundaryStrategy.WARNING);
        outside(() -> coin.resolve("20"));

        Editor editor = new Editor("2*40");
        quantity.minimum(BigDecimal.ONE);
        quantity.availableMaximum(BigDecimal.valueOf(64));
        check(editor.replaceAll(quantity.resolve(editor.text())));
        equal("64", editor.text());
        check(editor.undo());
        equal("2*40", editor.text());
        check(editor.redo());
        equal("64", editor.text());
        check(!editor.redo());
    }

    private static final class Editor extends OreTextFieldHandler {
        private Editor(String text) {
            super(null);
            setMaxCharacters(DecimalExpression.MAX_LENGTH);
            setPattern(DecimalExpression.INPUT_PATTERN);
            getText().add(text);
            setRenderer(new TextFieldRenderer(this) {
                @Override public boolean wouldFit(List<String> text, boolean checkWidth) { return true; }
            });
        }
        @Override protected void moveMainCursor(int line, int column, boolean animate) { getMainCursor().set(column, line); }
        @Override public void onChanged() { }
        private String text() { return String.join("\n", getText()); }
    }
    private static void outside(Runnable action) {
        try { action.run(); throw new AssertionError("Expected out-of-bounds warning"); }
        catch (NumberInputRules.OutOfBounds expected) { }
    }
    private static void invalid(Runnable action) {
        try { action.run(); throw new AssertionError("Invalid numeric value accepted"); }
        catch (ArithmeticException | IllegalArgumentException expected) { }
    }
    private static void equal(String expected, String actual) { check(expected.equals(actual)); }
    private static void check(boolean value) { if (!value) throw new AssertionError("NumberInput bound or commit regression"); }
}
