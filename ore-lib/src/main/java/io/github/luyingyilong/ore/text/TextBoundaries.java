package io.github.luyingyilong.ore.text;

import com.ibm.icu.text.BreakIterator;
import java.util.ArrayList;
import java.util.Locale;

/** UTF-16 document coordinates; never split a user-perceived character. */
public final class TextBoundaries {
    private TextBoundaries() { }
    public static int[] graphemes(String text) {
        BreakIterator iterator = BreakIterator.getCharacterInstance(Locale.ROOT);
        iterator.setText(text);
        ArrayList<Integer> result = new ArrayList<>();
        for (int at = iterator.first(); at != BreakIterator.DONE; at = iterator.next()) result.add(at);
        return result.stream().mapToInt(Integer::intValue).toArray();
    }
    public static int floor(String text, int offset) {
        offset = Math.max(0, Math.min(text.length(), offset));
        int result = 0;
        for (int boundary : graphemes(text)) { if (boundary > offset) break; result = boundary; }
        return result;
    }
    public static int previous(String text, int offset) { return floor(text, Math.max(0, offset - 1)); }
    public static int next(String text, int offset) {
        for (int boundary : graphemes(text)) if (boundary > offset) return boundary;
        return text.length();
    }
}
