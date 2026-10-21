package io.github.luyingyilong.ore.control.input;

import brachy.modularui.utils.Point;
import brachy.modularui.widgets.textfield.TextFieldRenderer;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.regex.Pattern;

/** Exercise the common Input/NumberInput handler and real shortcut dispatch without a window. */
public final class InputEditingTest {
    private static final int CTRL = GLFW.GLFW_MOD_CONTROL;

    public static void main(String[] args) {
        graphemeEdits();
        textClipboard();
        atomicTypingAndLimits();
        numberExpressions();
        rejectedNumberInput();
        historyResetAndBranch();
    }

    private static void graphemeEdits() {
        Editor committed = new Editor("");
        check(InputShortcuts.type(committed, "👩🏽‍💻") && committed.text().equals("👩🏽‍💻") && committed.changes == 1);
        check(committed.undo() && committed.text().isEmpty());
        check(!InputShortcuts.type(committed, "\uD83D") && committed.text().isEmpty());
        for (String cluster : List.of("e\u0301", "👩🏽‍💻", "🇨🇳")) {
            Editor input = new Editor("a" + cluster + "b");
            input.setCursor(new Point(1 + cluster.length(), 0), false);
            input.delete(false);
            check(input.text().equals("ab"));
            check(input.undo() && input.text().equals("a" + cluster + "b"));
            input.setCursor(new Point(1, 0), false);
            input.delete(true);
            check(input.text().equals("ab"));
            check(input.undo());
            input.setCursor(new Point(2, 0), false);
            check(input.getMainCursor().x == 1);
        }
    }

    private static void textClipboard() {
        Editor input = new Editor("你好😀ABC");
        Clipboard clipboard = new Clipboard();
        key(input, GLFW.GLFW_KEY_A, clipboard);
        key(input, GLFW.GLFW_KEY_C, clipboard);
        check(clipboard.text.equals("你好😀ABC") && !input.undo());
        key(input, GLFW.GLFW_KEY_X, clipboard);
        check(input.text().isEmpty() && !input.hasTextMarked());
        key(input, GLFW.GLFW_KEY_Z, clipboard);
        check(input.text().equals(clipboard.text) && input.getSelectedText().equals(clipboard.text));
        key(input, GLFW.GLFW_KEY_Y, clipboard);
        check(input.text().isEmpty());
        key(input, GLFW.GLFW_KEY_V, clipboard);
        check(input.text().equals("你好😀ABC"));
        key(input, GLFW.GLFW_KEY_Z, clipboard);
        check(input.text().isEmpty());
        InputShortcuts.dispatch(input, GLFW.GLFW_KEY_Z, CTRL | GLFW.GLFW_MOD_SHIFT, clipboard);
        check(input.text().equals("你好😀ABC"));
        input.setCursor(new Point(5, 0), false);
        input.setMainCursor(new Point(2, 0), false);
        key(input, GLFW.GLFW_KEY_X, clipboard);
        check(clipboard.text.equals("😀A") && input.text().equals("你好BC"));
        key(input, GLFW.GLFW_KEY_Z, clipboard);
        check(input.getMainCursor().x == 2 && input.getOffsetCursor().x == 5);
    }

    private static void atomicTypingAndLimits() {
        Editor input = new Editor("12345");
        Clipboard clipboard = new Clipboard();
        input.setMaxCharacters(5);
        input.markAll();
        check(InputShortcuts.type(input, '字') && input.text().equals("字"));
        key(input, GLFW.GLFW_KEY_Z, clipboard);
        check(input.text().equals("12345") && input.getSelectedText().equals("12345"));
        check(!input.undo()); // Typing over a selection must not create a separate deletion step.
        clipboard.text = "太长的输入文本";
        key(input, GLFW.GLFW_KEY_V, clipboard);
        check(input.text().equals("12345") && input.getSelectedText().equals("12345"));
        clipboard.text = "第一行\r\n第二行";
        key(input, GLFW.GLFW_KEY_V, clipboard);
        check(input.text().equals("12345") && input.hasTextMarked());
        check(!InputShortcuts.type(input, '\n') && !InputShortcuts.type(input, '\u00a7'));
        check(input.text().equals("12345") && input.hasTextMarked());
        clipboard.text = "abcde";
        key(input, GLFW.GLFW_KEY_V, clipboard);
        check(input.text().equals("abcde"));
        key(input, GLFW.GLFW_KEY_Z, clipboard);
        check(input.getSelectedText().equals("12345"));
    }

    private static void numberExpressions() {
        Editor quantity = number("1+1");
        Clipboard clipboard = new Clipboard();
        quantity.setCursor(new Point(3, 0), false);
        check(quantity.replaceAll(NumericExpressionFixture.quantity(quantity.text())) && quantity.text().equals("2"));
        key(quantity, GLFW.GLFW_KEY_Z, clipboard);
        check(quantity.text().equals("1+1") && quantity.getMainCursor().x == 3);
        key(quantity, GLFW.GLFW_KEY_Y, clipboard);
        check(quantity.text().equals("2"));
        check(quantity.replaceAll(NumericExpressionFixture.quantity(quantity.text())));
        key(quantity, GLFW.GLFW_KEY_Z, clipboard);
        check(quantity.text().equals("1+1") && !quantity.undo()); // Revalidating 2 adds no history.

        Editor coin = number("1/4");
        check(coin.replaceAll(NumericExpressionFixture.coin(coin.text())) && coin.text().equals("0.25"));
        key(coin, GLFW.GLFW_KEY_Z, clipboard);
        check(coin.text().equals("1/4"));
        key(coin, GLFW.GLFW_KEY_A, clipboard);
        clipboard.text = "2*(3+4)";
        key(coin, GLFW.GLFW_KEY_V, clipboard);
        check(coin.text().equals("2*(3+4)"));
        check(coin.replaceAll(NumericExpressionFixture.coin(coin.text())) && coin.text().equals("14.00"));
        key(coin, GLFW.GLFW_KEY_Z, clipboard);
        check(coin.text().equals("2*(3+4)"));
        key(coin, GLFW.GLFW_KEY_Z, clipboard);
        check(coin.text().equals("1/4") && coin.getSelectedText().equals("1/4"));
    }

    private static void rejectedNumberInput() {
        Editor input = number("12.50");
        Clipboard clipboard = new Clipboard();
        input.markAll();
        for (String invalid : List.of("中文", "1e3", "2\n3", "1".repeat(65))) {
            clipboard.text = invalid;
            key(input, GLFW.GLFW_KEY_V, clipboard);
            check(input.text().equals("12.50") && input.getSelectedText().equals("12.50"));
            check(!input.undo());
        }
        InputShortcuts.type(input, '中');
        check(input.text().equals("12.50") && input.hasTextMarked());
        for (String invalid : List.of("1/0", "1/3", "0.001", "9223372036854775808")) {
            Editor expression = number(invalid);
            try {
                NumericExpressionFixture.coin(expression.text());
                throw new AssertionError("Invalid amount was accepted");
            } catch (ArithmeticException | IllegalArgumentException expected) {
                check(expression.text().equals(invalid) && !expression.undo());
            }
        }
        Editor integer = number("1/4");
        try { NumericExpressionFixture.quantity(integer.text()); throw new AssertionError("Fractional quantity accepted"); }
        catch (ArithmeticException | IllegalArgumentException expected) { check(!integer.undo()); }
    }

    private static void historyResetAndBranch() {
        Editor input = new Editor("original");
        Clipboard clipboard = new Clipboard();
        input.markAll();
        clipboard.text = "edited";
        key(input, GLFW.GLFW_KEY_V, clipboard);
        key(input, GLFW.GLFW_KEY_Z, clipboard);
        InputShortcuts.type(input, 'x');
        check(input.text().equals("x") && !input.redo());
        input.clearHistory();
        check(!input.undo() && !input.redo());
        input.setPattern(Pattern.compile("[a-z]*"));
        input.markAll();
        clipboard.text = "123";
        key(input, GLFW.GLFW_KEY_V, clipboard);
        check(input.text().equals("x") && input.hasTextMarked());
    }

    private static Editor number(String source) {
        Editor input = new Editor(source);
        input.setMaxCharacters(DecimalExpression.MAX_LENGTH);
        input.setPattern(DecimalExpression.INPUT_PATTERN);
        return input;
    }

    private static void key(Editor input, int key, Clipboard clipboard) {
        check(InputShortcuts.dispatch(input, key, CTRL, clipboard));
    }

    private static final class Clipboard implements InputShortcuts.Clipboard {
        private String text = "";
        @Override public String get() { return text; }
        @Override public void set(String text) { this.text = text; }
    }

    private static final class Editor extends OreTextFieldHandler {
        private int changes;
        private Editor(String text) {
            super(null);
            setMaxCharacters(80);
            getText().add(text);
            setRenderer(new TextFieldRenderer(this) {
                @Override public boolean wouldFit(List<String> text, boolean checkWidth) { return true; }
            });
        }
        @Override protected void moveMainCursor(int line, int column, boolean animate) { getMainCursor().set(column, line); }
        @Override public void onChanged() { changes++; }
        private String text() { return String.join("\n", getText()); }
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Input/NumberInput clipboard, validation or history regression");
    }
}
