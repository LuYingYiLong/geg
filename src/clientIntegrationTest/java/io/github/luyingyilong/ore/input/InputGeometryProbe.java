package io.github.luyingyilong.ore.input;

import brachy.modularui.utils.Point;

/** Test-only access to the exact renderer and document caret used by native editing. */
public final class InputGeometryProbe {
    private InputGeometryProbe() { }
    private static OreTextFieldRenderer renderer(Input input) throws Exception {
        for(Class<?> type=input.getClass(); type!=null; type=type.getSuperclass()) {
            try { var field=type.getDeclaredField("renderer");field.setAccessible(true);return (OreTextFieldRenderer)field.get(input); }
            catch(NoSuchFieldException ignored) { }
        }
        throw new AssertionError("Renderer not found");
    }
    public static void resetClickSequence(Input input)throws Exception {
        for(Class<?> type=input.getClass();type!=null;type=type.getSuperclass()) {
            try {var field=type.getDeclaredField("lastClickTime");field.setAccessible(true);field.setLong(input,0);return;}
            catch(NoSuchFieldException ignored) { }
        }
        throw new AssertionError("Click sequence not found");
    }
    public static Point cursor(Input input) { return input.editingHandler().getMainCursor(); }
    public static int clickX(Input input,int column)throws Exception {
        var renderer=renderer(input);
        var point=renderer.getPosOf(renderer.measureStringLines(input.editingHandler().getText()),new Point(column,0));
        return input.inputMethodBounds().x()+Math.round(point.x)-input.getScrollX();
    }
    public static void verifyLineHit(Input input)throws Exception {
        var renderer=renderer(input);
        var lines=renderer.measureStringLines(input.editingHandler().getText());
        for(int column=0;column<=input.getText().length();column++) {
            var point=renderer.getPosOf(lines,new Point(column,0));
            for(int mouseY:new int[]{0,input.getArea().height/2,input.getArea().height-1}) {
                var hit=renderer.getCursorPos(input.editingHandler().getText(),Math.round(point.x),mouseY);
                if(hit.x!=column||hit.y!=0)throw new AssertionError("Caret/hit mismatch column="+column+" hit="+hit.x+","+hit.y+" mouseY="+mouseY+" point="+point+" bounds="+input.inputMethodBounds());
            }
        }
    }

    public static void verifyUnicodeLayout(Input input, TextArea area, io.github.luyingyilong.ore.layout.Window root) throws Exception {
        String oldInput = input.getText(), oldArea = area.getText();
        var bounds = input.inputMethodBounds();
        int x = bounds.x() + 8, y = bounds.y() + bounds.height() / 2;
        io.github.luyingyilong.ore.event.WindowInputs.press(root, x, y, root.getContext().getMui());
        io.github.luyingyilong.ore.event.WindowInputs.release(root, x, y);
        if (!input.isFocused()) throw new AssertionError("Unicode probe uses actual Window focus");
        try (var scope = new io.github.luyingyilong.ore.signal.SignalScope()) {
            int[] changes = {0};
            input.textChanged().connect(scope, text -> changes[0]++);
            input.setTextNoSignal("");
            io.github.luyingyilong.ore.event.WindowInputs.text(root, "👩🏽‍💻", 0, root.getContext().getMui());
            if (!input.getText().equals("👩🏽‍💻") || changes[0] != 1)
                throw new AssertionError("A Unicode string commits once: " + input.getText() + " / " + changes[0]);
            input.setTextNoSignal(""); changes[0] = 0;
            io.github.luyingyilong.ore.event.WindowInputs.text(root, "\uD83D", 0, root.getContext().getMui());
            if (!input.getText().isEmpty() || changes[0] != 0) throw new AssertionError("No half-surrogate document");
            io.github.luyingyilong.ore.event.WindowInputs.text(root, "\uDE00", 0, root.getContext().getMui());
            if (!input.getText().equals("😀") || changes[0] != 1) throw new AssertionError("Legacy char adapter combines surrogate pair");
        }
        area.setTextNoSignal("中文（English）é 👩🏽‍💻 ".repeat(12));
        var renderer = renderer(area);
        var lines = renderer.measureStringLines(area.editingHandler().getText());
        if (lines.size() < 2) throw new AssertionError("Probe text must soft-wrap");
        for (int offset : io.github.luyingyilong.ore.text.TextBoundaries.graphemes(area.getText())) {
            var position = renderer.getPosOf(lines, new Point(offset, 0));
            var hit = renderer.getCursorPos(area.editingHandler().getText(), Math.round(position.x),
                    (int)(position.y + renderer.getFontHeight() / 2));
            if (hit.x != offset || hit.y != 0)
                throw new AssertionError("Soft-wrap caret/hit mismatch " + offset + " -> " + hit.x + "," + hit.y
                        + " at " + position.x + "," + position.y);
        }
        input.setTextNoSignal("重载");
        input.editingHandler().setCursor(new Point(2, 0), false);
        input.editingHandler().insert("保留", true);
        long generation = io.github.luyingyilong.ore.text.OreText.getEngine().getFonts().getGeneration();
        io.github.luyingyilong.ore.text.OreText.reload(net.minecraft.client.Minecraft.getInstance().getResourceManager());
        io.github.luyingyilong.ore.theme.OreFonts.resourcesReloaded();
        if (io.github.luyingyilong.ore.text.OreText.getEngine().getFonts().getGeneration() <= generation
                || !input.getText().equals("重载保留") || !input.editingHandler().undo() || !input.getText().equals("重载"))
            throw new AssertionError("Font generation replacement preserves document and undo history");
        input.setTextNoSignal(oldInput); area.setTextNoSignal(oldArea);
        root.getContext().getMui().removeFocus();
        System.out.println("ORE_UNICODE_INPUT_LAYOUT_CLIENT_PASS");
    }
}
