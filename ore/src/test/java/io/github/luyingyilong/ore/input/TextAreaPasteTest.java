package io.github.luyingyilong.ore.input;

import brachy.modularui.utils.Point;
import brachy.modularui.widgets.textfield.TextFieldRenderer;
import io.github.luyingyilong.ore.util.TextLines;

import java.util.List;
import java.util.regex.Pattern;

/** Exercise the real TextArea insertion handler, without a running graphics context. */
public final class TextAreaPasteTest {
    static final String POEM = """
            水调歌头 末地
            游戏通关处，末影黑龙巢。荒凉幽静，闪光明亮照重霄。使者①瞬移惧水，船内收藏鞘翅，影贝令人飘。上次更新后，今已十年遥。
            问了吗？天天问，再问削。岛环修复，又见使者手相招②。难道更新将至，筛界却先来到，幽匿沟通桥。面尽锁烧断，末地获新潮

            注释：
            ①使者：终界使者，即末影人
            ②指26.3的宣传图中出现了众多与末影人相关的元素""";

    public static void main(String[] args) {
        Editor empty = new Editor("");
        empty.insert(POEM, true);
        check(empty.text().equals(POEM) && empty.getText().size() == 7);
        check(empty.getMainCursor().y == 6 && empty.getMainCursor().x == empty.getText().get(6).length());
        check(empty.changes == 1 && !empty.hasTextMarked());
        empty.markAll();
        check(empty.getSelectedText().equals(POEM));
        empty.insert("替换\n\n正文\n", true);
        check(empty.text().equals("替换\n\n正文\n"));
        check(empty.getMainCursor().y == 3 && empty.getMainCursor().x == 0);

        Editor middle = new Editor("前缀后缀\n保留这一行");
        middle.setCursor(new Point(2, 0), false);
        middle.insert(List.of("第一行", "第二行", "第三行", ""), true);
        check(middle.text().equals("前缀第一行\n第二行\n第三行\n后缀\n保留这一行"));
        check(middle.getMainCursor().y == 3 && middle.getMainCursor().x == 0);
        middle.newLine();
        check(middle.text().equals("前缀第一行\n第二行\n第三行\n\n后缀\n保留这一行"));

        Editor selection = new Editor("AA开头\n被替换\n结尾ZZ");
        selection.setCursor(new Point(2, 0), false);
        selection.setMainCursor(new Point(2, 2), false);
        check(selection.getSelectedText().equals("开头\n被替换\n结尾"));
        selection.insert(POEM.replace("\n", "\r\n") + "\r\n", true);
        check(selection.text().equals("AA" + POEM + "\nZZ"));
        check(selection.getMainCursor().y == 7 && selection.getMainCursor().x == 0);

        Editor bareCr = new Editor("");
        bareCr.insert("a\rb\r", true);
        check(bareCr.text().equals("a\nb\n"));
        Editor emoji = new Editor("A😀B");
        emoji.setCursor(new Point(3, 0), false);
        emoji.insert("中😀\n尾", true);
        check(emoji.text().equals("A😀中😀\n尾B"));
        check(emoji.getMainCursor().x == 1 && emoji.getMainCursor().y == 1);

        Editor capped = new Editor("abcdef");
        capped.setMaxCharacters(6);
        capped.setCursor(new Point(2, 0), false);
        capped.setMainCursor(new Point(4, 0), false);
        capped.insert("123\n456", true);
        check(capped.text().equals("abcdef") && capped.hasTextMarked() && capped.changes == 0);
        capped.insert("X\n", true);
        check(capped.text().equals("abX\nef"));
        capped.newLine();
        check(capped.text().equals("abX\nef"));

        Editor lines = new Editor("a");
        lines.setMaxLines(2);
        lines.insert("1\n2\n3", true);
        check(lines.text().equals("a") && lines.changes == 0);
        lines.setPattern(Pattern.compile("[a-z\\n]*"));
        lines.insert("1", true);
        check(lines.text().equals("a"));
        lines.fit = false;
        lines.insert("b", false);
        check(lines.text().equals("a") && lines.changes == 0);
    }

    private static final class Editor extends OreTextFieldHandler {
        private int changes;
        private boolean fit = true;

        private Editor(String text) {
            super(null);
            setMaxLines(2049);
            setMaxCharacters(4096);
            getText().addAll(TextLines.split(text));
            setRenderer(new TextFieldRenderer(this) {
                @Override public boolean wouldFit(List<String> text, boolean checkWidth) { return fit; }
            });
        }

        @Override protected void moveMainCursor(int line, int column, boolean animate) {
            getMainCursor().set(column, line);
        }
        @Override public void onChanged() { changes++; }
        private String text() { return String.join("\n", getText()); }
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("TextArea paste, selection or limit regression");
    }
}
