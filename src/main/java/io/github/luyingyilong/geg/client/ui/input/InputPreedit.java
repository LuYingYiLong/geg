package io.github.luyingyilong.geg.client.ui.input;

/** Uncommitted IME text. Cursor offsets use UTF-16, matching Windows IMM and MUI's handler. */
public record InputPreedit(String text, int cursor) {
    public static final InputPreedit EMPTY = new InputPreedit("", 0);
    public static final int MAX_LENGTH = 4096;

    public InputPreedit {
        text = text == null ? "" : text;
        if (text.length() > MAX_LENGTH) text = text.substring(0, boundary(text, MAX_LENGTH));
        cursor = boundary(text, cursor);
    }

    public boolean active() { return !text.isEmpty(); }

    /** The replacement is only a presentation; callers retain their original committed value. */
    public Presentation present(String committed, int selectionStart, int selectionEnd) {
        int start = boundary(committed, Math.min(selectionStart, selectionEnd));
        int end = boundary(committed, Math.max(selectionStart, selectionEnd));
        if (!active()) return new Presentation(committed, start, start, start);
        return new Presentation(committed.substring(0, start) + text + committed.substring(end),
                start, start + cursor, start + text.length());
    }

    private static int boundary(String text, int index) {
        int result = Math.max(0, Math.min(text.length(), index));
        if (result > 0 && result < text.length() && Character.isLowSurrogate(text.charAt(result))
                && Character.isHighSurrogate(text.charAt(result - 1))) result--;
        return result;
    }

    public record Presentation(String text, int start, int cursor, int end) { }
}
