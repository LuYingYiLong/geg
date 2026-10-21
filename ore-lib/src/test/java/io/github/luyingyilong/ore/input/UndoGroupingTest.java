package io.github.luyingyilong.ore.input;

import brachy.modularui.utils.Point;
import brachy.modularui.widgets.textfield.TextFieldRenderer;
import io.github.luyingyilong.ore.util.TextLines;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Real typing/clipboard dispatch with the native caret-scrolling boundary replaced for tests. */
public final class UndoGroupingTest {
    public static void main(String[] args) {
        continuousTyping();
        caretAndSelectionBoundaries();
        selectedTextReplacement();
        clipboardBoundaries();
        calculationBoundary();
        redoAndNewBranch();
        rejectedTyping();
        focusAndDeletionBoundaries();
        repeatedDeletion();
        deletionDirectionAndNavigation();
        multilineDeletionAndSelection();
        cutIsIndependentOfDeletion();
    }

    private static void continuousTyping() {
        Editor input = new Editor("", false);
        type(input, "你好abc😀");
        check(input.text().equals("你好abc😀"));
        check(input.undo() && input.text().isEmpty() && !input.undo());
        check(input.redo() && input.text().equals("你好abc😀") && !input.redo());
        Editor area = new Editor("", true);
        type(area, "第一行");
        area.newLine();
        type(area, "第二行");
        check(area.undo() && area.text().isEmpty() && !area.undo());
        check(area.redo() && area.text().equals("第一行\n第二行"));
    }

    private static void caretAndSelectionBoundaries() {
        Editor input = new Editor("", false);
        type(input, "abc");
        input.setCursor(new Point(1, 0), false);
        input.setCursor(new Point(3, 0), false); // Returning to the old caret still starts a new group.
        type(input, "de");
        check(input.undo() && input.text().equals("abc"));
        check(input.undo() && input.text().isEmpty());

        Editor selection = new Editor("", false);
        type(selection, "abc");
        selection.setOffsetCursor(new Point(1, 0));
        selection.setOffsetCursor(new Point(3, 0)); // Change then collapse only the anchor.
        type(selection, "xy");
        check(selection.undo() && selection.text().equals("abc"));
        check(selection.undo() && selection.text().isEmpty());
    }

    private static void selectedTextReplacement() {
        Editor area = new Editor("第一行\n\n最后一行\n", true);
        area.markAll();
        type(area, "替换文本");
        check(area.undo() && area.text().equals("第一行\n\n最后一行\n"));
        check(area.getSelectedText().equals(area.text()) && !area.undo());
        check(area.redo() && area.text().equals("替换文本"));
    }

    private static void clipboardBoundaries() {
        Clipboard clipboard = new Clipboard();
        Editor area = new Editor("", true);
        type(area, "ab");
        clipboard.text = "多\n行\n";
        key(area, GLFW.GLFW_KEY_V, clipboard);
        type(area, "cd");
        check(area.undo() && area.text().equals("ab多\n行\n"));
        check(area.undo() && area.text().equals("ab"));
        check(area.undo() && area.text().isEmpty());
        check(area.redo() && area.text().equals("ab"));
        check(area.redo() && area.text().equals("ab多\n行\n"));
        check(area.redo() && area.text().equals("ab多\n行\ncd"));
        area.markAll();
        key(area, GLFW.GLFW_KEY_X, clipboard);
        type(area, "ef");
        check(area.undo() && area.text().isEmpty());
        check(area.undo() && area.text().equals("ab多\n行\ncd") && area.hasTextMarked());
        check(area.undo() && area.text().equals("ab多\n行\n"));

        Editor invalidPaste = new Editor("", false);
        type(invalidPaste, "a");
        clipboard.text = "不允许\n多行";
        key(invalidPaste, GLFW.GLFW_KEY_V, clipboard);
        type(invalidPaste, "b");
        check(invalidPaste.undo() && invalidPaste.text().equals("a"));
        check(invalidPaste.undo() && invalidPaste.text().isEmpty());
    }

    private static void calculationBoundary() {
        Editor number = new Editor("", false);
        number.setPattern(DecimalExpression.INPUT_PATTERN);
        type(number, "1+1");
        check(number.replaceAll(NumericExpressionFixture.quantity(number.text())) && number.text().equals("2"));
        type(number, "3");
        check(number.undo() && number.text().equals("2"));
        check(number.undo() && number.text().equals("1+1"));
        check(number.undo() && number.text().isEmpty() && !number.undo());
        check(number.redo() && number.text().equals("1+1"));
        check(number.redo() && number.text().equals("2"));
        check(number.redo() && number.text().equals("23"));
    }

    private static void redoAndNewBranch() {
        Editor input = new Editor("", false);
        type(input, "ab");
        check(input.undo() && input.redo());
        type(input, "cd");
        check(input.undo() && input.text().equals("ab"));
        type(input, "ef");
        check(!input.redo() && input.text().equals("abef"));
        check(input.undo() && input.text().equals("ab"));
        check(input.undo() && input.text().isEmpty());
    }

    private static void rejectedTyping() {
        Editor number = new Editor("", false);
        number.setPattern(DecimalExpression.INPUT_PATTERN);
        type(number, "12");
        type(number, "中文");
        type(number, "3");
        check(number.text().equals("123") && number.undo() && number.text().isEmpty() && !number.undo());
    }

    private static void focusAndDeletionBoundaries() {
        Editor input = new Editor("", false);
        type(input, "ab");
        input.endEditGroup(); // Mouse/focus boundary uses this same hook.
        type(input, "cd");
        input.delete();
        type(input, "xy");
        check(input.undo() && input.text().equals("abc"));
        check(input.undo() && input.text().equals("abcd"));
        check(input.undo() && input.text().equals("ab"));
        check(input.undo() && input.text().isEmpty());
        input.clearHistory();
        for (int index = 0; index < 130; index++) {
            type(input, "字");
            input.endEditGroup();
        }
        int groups = 0;
        while (input.undo()) groups++;
        check(groups == 100 && input.text().length() == 30);
    }

    private static void repeatedDeletion() {
        Editor backward = new Editor("abcdef", false);
        backward.setCursor(new Point(6, 0), false);
        for (int count = 0; count < 3; count++) backward.delete();
        check(backward.text().equals("abc"));
        check(backward.undo() && backward.text().equals("abcdef") && !backward.undo());
        check(backward.redo() && backward.text().equals("abc"));
        backward.delete();
        check(backward.undo() && backward.text().equals("abc"));

        Editor forward = new Editor("abcdef", false);
        forward.setCursor(new Point(1, 0), false);
        for (int count = 0; count < 3; count++) forward.delete(true);
        check(forward.text().equals("aef"));
        check(forward.undo() && forward.text().equals("abcdef") && !forward.undo());
        check(forward.redo() && forward.text().equals("aef"));
    }

    private static void deletionDirectionAndNavigation() {
        Editor input = new Editor("abcdef", false);
        input.setCursor(new Point(3, 0), false);
        input.delete(); input.delete();
        input.delete(true); input.delete(true);
        input.delete();
        check(input.text().equals("f"));
        check(input.undo() && input.text().equals("af"));
        check(input.undo() && input.text().equals("adef"));
        check(input.undo() && input.text().equals("abcdef") && !input.undo());
        check(input.redo() && input.text().equals("adef"));
        check(input.redo() && input.text().equals("af"));
        check(input.redo() && input.text().equals("f"));

        Editor edge = new Editor("abc", false);
        edge.setCursor(new Point(3, 0), false);
        edge.delete();
        edge.delete(true); // Switching direction at the end is a boundary, not an empty undo step.
        edge.delete();
        check(edge.undo() && edge.text().equals("ab"));
        check(edge.undo() && edge.text().equals("abc") && !edge.undo());

        Editor moved = new Editor("abcdef", false);
        moved.setCursor(new Point(6, 0), false);
        moved.delete(); moved.delete();
        moved.setCursor(new Point(1, 0), false);
        moved.setCursor(new Point(4, 0), false);
        moved.delete(); moved.delete();
        check(moved.undo() && moved.text().equals("abcd"));
        check(moved.undo() && moved.text().equals("abcdef"));

        Editor selected = new Editor("abcdef", false);
        selected.setCursor(new Point(6, 0), false);
        selected.delete();
        selected.setOffsetCursor(new Point(2, 0));
        selected.setOffsetCursor(new Point(5, 0));
        selected.delete(); selected.delete();
        check(selected.undo() && selected.text().equals("abcde"));
        check(selected.undo() && selected.text().equals("abcdef"));
    }

    private static void multilineDeletionAndSelection() {
        String original = "A😀\n中B\n";
        Editor area = new Editor(original, true);
        area.setCursor(new Point(0, 2), false);
        for (int count = 0; count < 5; count++) area.delete();
        check(area.text().equals("A"));
        check(area.undo() && area.text().equals(original) && !area.undo());
        area.setCursor(new Point(0, 0), false);
        for (int count = 0; count < 3; count++) area.delete(true);
        check(area.text().equals("中B\n"));
        check(area.undo() && area.text().equals(original));

        Editor reverse = new Editor("abc\ndef\nxyz", true);
        reverse.setCursor(new Point(1, 2), false);
        reverse.setMainCursor(new Point(1, 0), false);
        reverse.delete(); reverse.delete();
        check(reverse.text().equals("yz"));
        check(reverse.undo() && reverse.text().equals("abc\ndef\nxyz"));
        check(reverse.getSelectedText().equals("bc\ndef\nx"));
        check(reverse.getMainCursor().y == 0 && reverse.getOffsetCursor().y == 2);
    }

    private static void cutIsIndependentOfDeletion() {
        for (boolean forward : List.of(false, true)) {
            Editor input = new Editor("abcdef", false);
            Clipboard clipboard = new Clipboard();
            input.setCursor(new Point(2, 0), false);
            input.setMainCursor(new Point(4, 0), false);
            key(input, GLFW.GLFW_KEY_X, clipboard);
            input.delete(forward); input.delete(forward);
            check(input.text().equals(forward ? "ab" : "ef"));
            check(input.undo() && input.text().equals("abef"));
            check(input.undo() && input.text().equals("abcdef"));
            check(input.getSelectedText().equals("cd") && !input.undo());
        }
    }

    private static void type(Editor input, String text) {
        // GLFW commits Unicode codepoints; the legacy char adapter joins surrogate pairs first.
        text.codePoints().forEach(codepoint -> check(InputShortcuts.type(input, new String(Character.toChars(codepoint)))));
    }

    private static void key(Editor input, int key, Clipboard clipboard) {
        check(InputShortcuts.dispatch(input, key, GLFW.GLFW_MOD_CONTROL, clipboard));
    }

    private static final class Clipboard implements InputShortcuts.Clipboard {
        private String text = "";
        @Override public String get() { return text; }
        @Override public void set(String text) { this.text = text; }
    }

    private static final class Editor extends OreTextFieldHandler {
        private Editor(String source, boolean multiline) {
            super(null);
            setMaxLines(multiline ? 2049 : 1);
            setMaxCharacters(4096);
            getText().addAll(TextLines.split(source));
            setRenderer(new TextFieldRenderer(this) {
                @Override public boolean wouldFit(List<String> lines, boolean checkWidth) { return true; }
            });
        }
        @Override protected void moveMainCursor(int line, int column, boolean animate) { getMainCursor().set(column, line); }
        @Override public void onChanged() { }
        private String text() { return String.join("\n", getText()); }
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Typing undo group or edit boundary regression");
    }
}
