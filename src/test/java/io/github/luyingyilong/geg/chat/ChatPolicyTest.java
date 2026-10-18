package io.github.luyingyilong.geg.chat;

public final class ChatPolicyTest {
    public static void main(String[] args) {
        check(ChatPolicy.valid("你好\nworld") && ChatPolicy.normalize(" \r\na\rb\r\n ").equals("a\nb"));
        check(ChatPolicy.valid("😀".repeat(512)) && !ChatPolicy.valid("😀".repeat(513)));
        for (String invalid : new String[]{"", " \n ", "x\t", "x\u0000", "§cFake", "\uD800", "\uDC00"}) check(!ChatPolicy.valid(invalid));
        check(!ChatPolicy.valid(ChatPolicy.normalize("x\t")));
        ChatPolicy.RateLimit quota = new ChatPolicy.RateLimit();
        for (int i = 0; i < 10; i++) check(quota.take(i));
        check(!quota.take(100) && !quota.take(9999));
        check(quota.take(10000)); check(!quota.take(10000)); check(quota.take(10001));
    }
    private static void check(boolean condition) { if (!condition) throw new AssertionError("Chat validation or rate limit regression"); }
}
