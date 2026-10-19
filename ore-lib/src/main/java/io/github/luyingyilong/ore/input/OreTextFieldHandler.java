package io.github.luyingyilong.ore.input;

import brachy.modularui.utils.Point;
import brachy.modularui.widgets.textfield.BaseTextFieldWidget;
import brachy.modularui.widgets.textfield.TextFieldHandler;
import brachy.modularui.widgets.textfield.TextFieldRenderer;
import io.github.luyingyilong.ore.util.TextLines;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Shared single/multiline edits and bounded history, avoiding MUI's insert/delete list bugs.
 */
class OreTextFieldHandler extends TextFieldHandler {
    private static final int HISTORY_LIMIT = 100;
    private final Deque<Snapshot> undo = new ArrayDeque<>();
    private final Deque<Snapshot> redo = new ArrayDeque<>();
    private TextFieldRenderer textRenderer;
    private Pattern textPattern;
    private Snapshot groupTail;
    private EditKind groupKind;
    private boolean changingDocument;
    OreTextFieldHandler(BaseTextFieldWidget<?> owner) {
        super(owner);
    }

    private static void push(Deque<Snapshot> history, Snapshot snapshot) {
        if (history.size() == HISTORY_LIMIT) history.removeFirst();
        history.addLast(snapshot);
    }

    private static Point pointAt(String text, int offset) {
        int line = (int) text.substring(0, offset).chars().filter(cp -> cp == '\n').count();
        return new Point(offset - (text.lastIndexOf('\n', offset - 1) + 1), line);
    }

    @Override
    public void setRenderer(TextFieldRenderer renderer) {
        super.setRenderer(renderer);
        textRenderer = renderer;
    }

    @Override
    public void setPattern(Pattern pattern) {
        super.setPattern(pattern);
        textPattern = pattern;
    }

    @Override
    public boolean test(String text) {
        return (getMaxCharacters() < 0 || text.length() <= getMaxCharacters())
                && (textPattern == null || textPattern.matcher(text).matches());
    }

    @Override
    public void insert(String text, boolean canScroll) {
        insert(TextLines.split(text), canScroll);
    }

    @Override
    public void insert(List<String> insertedLines, boolean canScroll) {
        endEditGroup();
        insert(insertedLines, canScroll, false);
    }

    void type(char character) {
        insert(List.of(String.valueOf(character)), true, true);
    }

    private void insert(List<String> insertedLines, boolean canScroll, boolean typing) {
        if (insertedLines.isEmpty()) return;
        String inserted = String.join("\n", TextLines.split(String.join("\n", insertedLines)));
        if (inserted.isEmpty() && !hasTextMarked()) return;
        String original = String.join("\n", getText());
        int start = offset(original, getStartCursor());
        int end = offset(original, getEndCursor());
        String result = original.substring(0, start) + inserted + original.substring(end);
        List<String> lines = TextLines.split(result);
        // Validate a complete candidate before changing either the document or selection.
        if (lines.size() > getMaxLines() || !test(result)
                || textRenderer != null && !textRenderer.wouldFit(lines, !canScroll)) return;
        apply(result, start + inserted.length(), typing ? EditKind.TYPING : EditKind.DISCRETE);
    }

    private void apply(String result, int cursor, EditKind kind) {
        Snapshot before = snapshot();
        boolean changed = !result.equals(before.text());
        if (changed) {
            if (kind == EditKind.DISCRETE || kind != groupKind || !before.equals(groupTail)) push(undo, before);
            redo.clear();
        }
        updateDocument(result, cursor, cursor, true);
        groupKind = kind;
        groupTail = kind != EditKind.DISCRETE && changed ? snapshot() : null;
        if (changed) onChanged();
    }

    @Override
    public void newLine() {
        insert(List.of("", ""), true, true);
    }

    /**
     * A successful numeric calculation is one edit, not a programmatic history reset.
     */
    boolean replaceAll(String text) {
        endEditGroup();
        List<String> lines = TextLines.split(text);
        String result = String.join("\n", lines);
        if (lines.size() > getMaxLines() || !test(result)
                || textRenderer != null && !textRenderer.wouldFit(lines, false)) return false;
        if (!result.equals(String.join("\n", getText()))) apply(result, result.length(), EditKind.DISCRETE);
        return true;
    }

    @Override
    public String getSelectedText() {
        String text = String.join("\n", getText());
        return text.substring(offset(text, getStartCursor()), offset(text, getEndCursor()));
    }

    @Override
    public void delete() {
        delete(false);
    }

    @Override
    public void delete(boolean forward) {
        delete(forward, forward ? EditKind.DELETE : EditKind.BACKSPACE);
    }

    /**
     * Cutting must stay independent of adjacent Backspace/Delete key repetitions.
     */
    void deleteSelection() {
        endEditGroup();
        if (hasTextMarked()) delete(false, EditKind.DISCRETE);
    }

    private void delete(boolean forward, EditKind kind) {
        // Switching direction ends the old group even when this key is already at an edge.
        if (groupKind != kind) endEditGroup();
        String text = String.join("\n", getText());
        int start = offset(text, getStartCursor());
        int end = offset(text, getEndCursor());
        if (start == end) {
            if (forward && end < text.length()) end = text.offsetByCodePoints(end, 1);
            else if (!forward && start > 0) start = text.offsetByCodePoints(start, -1);
            else return;
        }
        apply(text.substring(0, start) + text.substring(end), start, kind);
    }

    @Override
    public void clear() {
        endEditGroup();
        apply("", 0, EditKind.DISCRETE);
    }

    @Override
    public void markAll() {
        endEditGroup();
        String text = String.join("\n", getText());
        setCursor(pointAt(text, text.length()), false);
        setOffsetCursor(new Point(0, 0));
    }

    boolean undo() {
        return restore(undo, redo);
    }

    boolean redo() {
        return restore(redo, undo);
    }

    void clearHistory() {
        endEditGroup();
        undo.clear();
        redo.clear();
    }

    void endEditGroup() {
        groupTail = null;
        groupKind = null;
    }

    @Override
    public final void setMainCursor(int line, int column, boolean animate) {
        Point current = getMainCursor();
        if (!changingDocument && (current.x != column || current.y != line)) endEditGroup();
        moveMainCursor(line, column, animate);
    }

    /**
     * MUI owns caret scrolling; headless tests replace only that native graphics dependency.
     */
    protected void moveMainCursor(int line, int column, boolean animate) {
        super.setMainCursor(line, column, animate);
    }

    @Override
    public void setOffsetCursor(int line, int column) {
        Point current = getOffsetCursor();
        if (!changingDocument && (current.x != column || current.y != line)) endEditGroup();
        super.setOffsetCursor(line, column);
    }

    @Override
    public void switchCursors() {
        if (!changingDocument && hasTextMarked()) endEditGroup();
        super.switchCursors();
    }

    private boolean restore(Deque<Snapshot> from, Deque<Snapshot> to) {
        endEditGroup();
        if (from.isEmpty()) return false;
        push(to, snapshot());
        Snapshot previous = from.removeLast();
        updateDocument(previous.text(), previous.caret(), previous.anchor(), false);
        onChanged();
        return true;
    }

    private void updateDocument(String text, int caret, int anchor, boolean animate) {
        changingDocument = true;
        try {
            getText().clear();
            getText().addAll(TextLines.split(text));
            setCursor(pointAt(text, caret), animate);
            setOffsetCursor(pointAt(text, anchor));
        } finally {
            changingDocument = false;
        }
    }

    private Snapshot snapshot() {
        String text = String.join("\n", getText());
        return new Snapshot(text, offset(text, getMainCursor()), offset(text, getOffsetCursor()));
    }

    private int offset(String text, Point cursor) {
        int offset = Math.max(0, Math.min(text.length(), TextLines.offset(getText(), cursor.x, cursor.y)));
        if (offset > 0 && offset < text.length() && Character.isHighSurrogate(text.charAt(offset - 1))
                && Character.isLowSurrogate(text.charAt(offset))) offset--;
        return offset;
    }

    private enum EditKind {TYPING, BACKSPACE, DELETE, DISCRETE}

    private record Snapshot(String text, int caret, int anchor) {
    }
}
