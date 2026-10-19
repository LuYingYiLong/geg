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
}
