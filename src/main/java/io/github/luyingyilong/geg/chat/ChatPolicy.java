package io.github.luyingyilong.geg.chat;

import java.util.ArrayDeque;

public final class ChatPolicy {
    public static final int HISTORY_LIMIT = 100;
    public static final int PAGE_SIZE = 50;
    public static final int TEXT_LIMIT = 512;
    private ChatPolicy() { }

    public static String normalize(String text) {
        String value = text.replace("\r\n", "\n").replace('\r', '\n');
        int start = 0, end = value.length();
        // Do not strip illegal controls such as tabs before the validator can reject them.
        while (start < end && (value.charAt(start) == ' ' || value.charAt(start) == '\n')) start++;
        while (end > start && (value.charAt(end - 1) == ' ' || value.charAt(end - 1) == '\n')) end--;
        return value.substring(start, end);
    }
    public static boolean valid(String text) {
        return text != null && !text.isBlank() && text.codePointCount(0, text.length()) <= TEXT_LIMIT
                && text.codePoints().noneMatch(c -> c == '\u00a7' || c == 0x7F
                || Character.isISOControl(c) && c != '\n' || c >= 0xD800 && c <= 0xDFFF);
    }
    /** Shared quota across all channels; a monotonic clock may be supplied in tests. */
    public static final class RateLimit {
        private final ArrayDeque<Long> sends = new ArrayDeque<>();
        public boolean take(long now) {
            while (!sends.isEmpty() && now - sends.peekFirst() >= 10_000) sends.removeFirst();
            if (sends.size() >= 10) return false;
            sends.addLast(now); return true;
        }
    }
}
