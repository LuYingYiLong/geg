package io.github.luyingyilong.geg.chat.client;

/** GUI-pixel breakpoints, shared by the live tree and its headless layout tests. */
public final class ChatLayout {
    public static final int MENU_MIN = 72, CHAT_MIN = 96, GAP = 4;
    private ChatLayout() { }
    public static int drawerWidth(int viewport) {
        int normal = Math.max(1, (int) Math.round(viewport * 0.7));
        return normal < MENU_MIN + CHAT_MIN + GAP + 8 ? Math.max(1, viewport) : normal;
    }
    public static boolean collapsed(int contentWidth) { return contentWidth < MENU_MIN + CHAT_MIN + GAP; }
}
