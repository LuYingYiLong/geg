package io.github.luyingyilong.ore.layout;

/** Container size hints. EXPAND allocates a slot; FILL controls how its child uses that slot. */
public final class SizeFlags {
    public static final int SIZE_SHRINK_BEGIN = 0;
    public static final int SIZE_FILL = 1;
    public static final int SIZE_EXPAND = 2;
    public static final int SIZE_EXPAND_FILL = SIZE_FILL | SIZE_EXPAND;
    public static final int SIZE_SHRINK_CENTER = 4;
    public static final int SIZE_SHRINK_END = 8;

    private SizeFlags() { }

    public static int validate(int flags) {
        int alignment = flags & (SIZE_FILL | SIZE_SHRINK_CENTER | SIZE_SHRINK_END);
        if ((flags & ~15) != 0 || Integer.bitCount(alignment) > 1)
            throw new IllegalArgumentException("Unknown or mutually exclusive size flags: " + flags);
        return flags;
    }

    public static boolean fills(int flags) { return (flags & SIZE_FILL) != 0; }
    public static boolean expands(int flags) { return (flags & SIZE_EXPAND) != 0; }

    static int offset(int flags, int available, int actual) {
        int remaining = Math.max(0, available - actual);
        if ((flags & SIZE_SHRINK_END) != 0) return remaining;
        if ((flags & SIZE_SHRINK_CENTER) != 0) return remaining / 2;
        return 0;
    }
}
