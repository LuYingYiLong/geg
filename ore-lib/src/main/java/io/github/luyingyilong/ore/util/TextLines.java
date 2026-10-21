package io.github.luyingyilong.ore.util;

import java.util.Arrays;
import java.util.List;

/**
 * Both the editor and TextLayout use UTF-16 coordinates across line breaks.
 */
public final class TextLines {
    private TextLines() {
    }

    public static List<String> split(String text) {
        return Arrays.asList(text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1));
    }

    public static int offset(List<String> lines, int column, int row) {
        int result = 0;
        for (int line = 0; line < row && line < lines.size(); line++) result += lines.get(line).length() + 1;
        return result + column;
    }

    public static Position position(String text, int cursor) {
        int start = text.lastIndexOf('\n', Math.max(-1, cursor - 1)) + 1;
        int line = (int) text.substring(0, start).chars().filter(c -> c == '\n').count();
        return new Position(cursor - start, line);
    }

    public record Position(int column, int line) {
    }
}
