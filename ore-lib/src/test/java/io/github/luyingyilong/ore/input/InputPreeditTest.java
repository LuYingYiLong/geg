package io.github.luyingyilong.ore.input;

/** Preedit must not change committed values or split a Unicode character at its cursor. */
public final class InputPreeditTest {
    private InputPreeditTest() { }

    public static void main(String[] args) {
        String committed = "组织名称";
        InputPreedit preedit = new InputPreedit("xiang'mu", 5);
        var display = preedit.present(committed, 2, 4);
        check(display.text().equals("组织xiang'mu"));
        check(display.start() == 2 && display.cursor() == 7 && display.end() == 10);
        check(committed.equals("组织名称"));
        check(preedit.present(committed, 4, 2).equals(display));
        check(InputPreedit.EMPTY.present(committed, 2, 4).text().equals(committed));
        check(new InputPreedit(null, 20).equals(InputPreedit.EMPTY));
        check(new InputPreedit("nihao", -1).cursor() == 0);
        check(new InputPreedit("nihao", 100).cursor() == 5);
        String supplementary = new String(Character.toChars(0x20000));
        check(new InputPreedit(supplementary + "a", 1).cursor() == 0);
        check(new InputPreedit(supplementary + "a", 2).cursor() == 2);
        var unicode = new InputPreedit("mu", 2).present(supplementary + "木", 1, 1);
        check(unicode.text().equals("mu" + supplementary + "木"));
        String longText = "a".repeat(InputPreedit.MAX_LENGTH - 1) + supplementary;
        InputPreedit bounded = new InputPreedit(longText, Integer.MAX_VALUE);
        check(bounded.text().length() == InputPreedit.MAX_LENGTH - 1);
        check(bounded.cursor() == bounded.text().length());
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Preedit changed committed text or a Unicode boundary");
    }
}
