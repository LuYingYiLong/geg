package io.github.luyingyilong.ore.input;

import brachy.modularui.utils.Point;
import brachy.modularui.widgets.textfield.TextFieldRenderer;
import io.github.luyingyilong.ore.util.TextLines;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Random;

/** Test the same shortcuts used by TextArea, replacing only OS clipboard and graphics access. */
public final class TextAreaEditingTest {
    private static final int CTRL = GLFW.GLFW_MOD_CONTROL;
    private static final int SHIFT = GLFW.GLFW_MOD_SHIFT;

    public static void main(String[] args) {
        clipboardAndHistory();
        backwardSelectionAndAdjacentLines();
        rejectedPasteAndHistoryBranch();
        unicodeDeletionAndEmptyText();
        boundedHistory();
        randomEdits();
    }

    private static void clipboardAndHistory() {
        Editor editor = new Editor(TextAreaPasteTest.POEM + "\n");
        Clipboard clipboard = new Clipboard();
        key(editor, GLFW.GLFW_KEY_A, clipboard);
        key(editor, GLFW.GLFW_KEY_C, clipboard);
        String original = editor.text();
        check(clipboard.text.equals(original) && !editor.undo());
        key(editor, GLFW.GLFW_KEY_X, clipboard);
        check(editor.text().isEmpty() && editor.getText().equals(List.of("")));
        check(!editor.hasTextMarked());
        key(editor, GLFW.GLFW_KEY_Z, clipboard);
        check(editor.text().equals(original) && editor.getSelectedText().equals(original));
        check(editor.getMainCursor().y == 7 && editor.getMainCursor().x == 0);
        key(editor, GLFW.GLFW_KEY_Y, clipboard);
        check(editor.text().isEmpty());
        key(editor, GLFW.GLFW_KEY_Z, clipboard);
        check(InputShortcuts.dispatch(editor, GLFW.GLFW_KEY_Z, CTRL | SHIFT, clipboard));
        check(editor.text().isEmpty());
        key(editor, GLFW.GLFW_KEY_V, clipboard);
        check(editor.text().equals(original) && !editor.hasTextMarked());
        key(editor, GLFW.GLFW_KEY_Z, clipboard);
        check(editor.text().isEmpty()); // The entire seven-line paste was one edit.
        check(editor.changes == 7);
    }

    private static void backwardSelectionAndAdjacentLines() {
        Editor editor = new Editor("AB第一行\n\n第三行YZ");
        Clipboard clipboard = new Clipboard();
        editor.setCursor(new Point(3, 2), false);
        editor.setMainCursor(new Point(2, 0), false); // Active caret precedes the anchor.
        key(editor, GLFW.GLFW_KEY_C, clipboard);
        check(clipboard.text.equals("第一行\n\n第三行"));
        key(editor, GLFW.GLFW_KEY_X, clipboard);
        check(editor.text().equals("ABYZ"));
        key(editor, GLFW.GLFW_KEY_Z, clipboard);
        check(editor.text().equals("AB第一行\n\n第三行YZ"));
        check(editor.getMainCursor().x == 2 && editor.getMainCursor().y == 0);
        check(editor.getOffsetCursor().x == 3 && editor.getOffsetCursor().y == 2);
        check(editor.getSelectedText().equals(clipboard.text));

        Editor adjacent = new Editor("abc\ndef\nghi");
        adjacent.setCursor(new Point(1, 0), false);
        adjacent.setMainCursor(new Point(2, 1), false);
        key(adjacent, GLFW.GLFW_KEY_X, clipboard);
        check(clipboard.text.equals("bc\nde") && adjacent.text().equals("af\nghi"));
        key(adjacent, GLFW.GLFW_KEY_Z, clipboard);
        check(adjacent.text().equals("abc\ndef\nghi") && adjacent.getSelectedText().equals("bc\nde"));
        clipboard.text = "甲\n\n乙\n";
        key(adjacent, GLFW.GLFW_KEY_V, clipboard);
        check(adjacent.text().equals("a甲\n\n乙\nf\nghi"));
        key(adjacent, GLFW.GLFW_KEY_Z, clipboard);
        check(adjacent.getSelectedText().equals("bc\nde"));
    }

    private static void rejectedPasteAndHistoryBranch() {
        Editor editor = new Editor("abc\ndef");
        Clipboard clipboard = new Clipboard();
        editor.setMaxCharacters(7);
        editor.setCursor(new Point(1, 0), false);
        editor.setMainCursor(new Point(2, 1), false);
        clipboard.text = "too long\n\nfor this field";
        key(editor, GLFW.GLFW_KEY_V, clipboard);
        check(editor.text().equals("abc\ndef") && editor.getSelectedText().equals("bc\nde"));
        check(!editor.undo() && editor.changes == 0);
        clipboard.text = "";
        key(editor, GLFW.GLFW_KEY_V, clipboard);
        check(editor.getSelectedText().equals("bc\nde"));
        clipboard.text = "中";
        key(editor, GLFW.GLFW_KEY_V, clipboard);
        check(editor.text().equals("a中f"));
        key(editor, GLFW.GLFW_KEY_Z, clipboard);
        editor.insert("x", true);
        check(editor.text().equals("axf") && !editor.redo());
        editor.clearHistory();
        check(!editor.undo() && !editor.redo());
        check(!InputShortcuts.dispatch(editor, GLFW.GLFW_KEY_X, 0, clipboard));
        check(!InputShortcuts.dispatch(editor, GLFW.GLFW_KEY_X, CTRL | GLFW.GLFW_MOD_ALT, clipboard));
    }

    private static void unicodeDeletionAndEmptyText() {
        Editor editor = new Editor("A😀\n中B\n");
        editor.setCursor(new Point(3, 0), false);
        editor.delete();
        check(editor.text().equals("A\n中B\n"));
        check(editor.undo() && editor.text().equals("A😀\n中B\n"));
        editor.setCursor(new Point(1, 0), false);
        editor.delete(true);
        check(editor.text().equals("A\n中B\n"));
        editor.setCursor(new Point(0, 1), false);
        editor.delete();
        check(editor.text().equals("A中B\n"));
        check(editor.undo() && editor.text().equals("A\n中B\n"));
        editor.setCursor(new Point(1, 0), false);
        editor.delete(true);
        check(editor.text().equals("A中B\n"));

        Editor empty = new Editor("");
        Clipboard clipboard = new Clipboard();
        clipboard.text = "保持剪贴板";
        key(empty, GLFW.GLFW_KEY_A, clipboard);
        key(empty, GLFW.GLFW_KEY_C, clipboard);
        key(empty, GLFW.GLFW_KEY_X, clipboard);
        empty.delete();
        empty.delete(true);
        check(clipboard.text.equals("保持剪贴板") && empty.getText().equals(List.of("")));
        check(!empty.undo() && !empty.redo() && empty.changes == 0);
        empty.newLine();
        check(empty.getText().equals(List.of("", "")) && empty.undo() && empty.text().isEmpty());
    }

    private static void boundedHistory() {
        Editor editor = new Editor("");
        for (int index = 0; index < 130; index++) editor.insert("字", true);
        int undone = 0;
        while (editor.undo()) undone++;
        check(undone == 100 && editor.text().length() == 30);
        int redone = 0;
        while (editor.redo()) redone++;
        check(redone == 100 && editor.text().length() == 130);
    }

    private static void randomEdits() {
        Random random = new Random(230326);
        Editor editor = new Editor("中😀\n\n初始\n");
        String[] inserted = {"字", "😀", "\n", "多\n行\n", "\n\n", "abc"};
        for (int step = 0; step < 400; step++) {
            String before = editor.text();
            List<Integer> boundaries = new java.util.ArrayList<>();
            for (int offset = 0; offset <= before.length();) {
                boundaries.add(offset);
                if (offset == before.length()) break;
                offset += Character.charCount(before.codePointAt(offset));
            }
            int anchor = boundaries.get(random.nextInt(boundaries.size()));
            int caret = boundaries.get(random.nextInt(boundaries.size()));
            editor.setCursor(point(before, anchor), false);
            editor.setMainCursor(point(before, caret), false);
            if (random.nextBoolean()) editor.switchCursors();
            Point mainBefore = new Point(editor.getMainCursor().x, editor.getMainCursor().y);
            Point anchorBefore = new Point(editor.getOffsetCursor().x, editor.getOffsetCursor().y);
            String addition = inserted[random.nextInt(inserted.length)];
            String expected = before.substring(0, Math.min(anchor, caret)) + addition
                    + before.substring(Math.max(anchor, caret));
            editor.insert(addition, true);
            check(editor.text().equals(expected));
            if (!expected.equals(before)) {
                check(editor.undo() && editor.text().equals(before));
                check(editor.getMainCursor().x == mainBefore.x && editor.getMainCursor().y == mainBefore.y);
                check(editor.getOffsetCursor().x == anchorBefore.x && editor.getOffsetCursor().y == anchorBefore.y);
                check(editor.getSelectedText().equals(before.substring(Math.min(anchor, caret), Math.max(anchor, caret))));
                check(editor.redo() && editor.text().equals(expected));
            }
        }
    }

    private static Point point(String text, int offset) {
        int line = (int) text.substring(0, offset).chars().filter(cp -> cp == '\n').count();
        return new Point(offset - text.lastIndexOf('\n', offset - 1) - 1, line);
    }

    private static void key(Editor editor, int key, Clipboard clipboard) {
        check(InputShortcuts.dispatch(editor, key, CTRL, clipboard));
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
            setMaxLines(2049);
            setMaxCharacters(4096);
            getText().addAll(TextLines.split(text));
            setRenderer(new TextFieldRenderer(this) {
                @Override public boolean wouldFit(List<String> text, boolean checkWidth) { return true; }
            });
        }
        @Override protected void moveMainCursor(int line, int column, boolean animate) { getMainCursor().set(column, line); }
        @Override public void onChanged() { changes++; }
        private String text() { return String.join("\n", getText()); }
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("TextArea clipboard or history regression");
    }
}
