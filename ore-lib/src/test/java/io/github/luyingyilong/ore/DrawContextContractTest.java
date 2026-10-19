package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.Window;
import io.github.luyingyilong.ore.render.DrawContext;
import java.lang.reflect.Method;

/** Protect the portable public drawing boundary, rather than implementation-specific call order. */
public final class DrawContextContractTest {
    public static void main(String[] args) throws Exception {
        for (Class<?> control : new Class<?>[]{Control.class, Window.class}) {
            control.getDeclaredMethod("draw", DrawContext.class, int.class, int.class);
            for (Method method : control.getDeclaredMethods())
                if (method.getName().equals("draw") || method.getName().equals("render") || method.getName().equals("renderTooltip"))
                    for (Class<?> parameter : method.getParameterTypes())
                        check(!parameter.getName().contains("ModularGuiContext"), "Legacy Control drawing overload returned");
        }
        for (Method method : DrawContext.class.getDeclaredMethods())
            for (Class<?> parameter : method.getParameterTypes())
                check(!parameter.getName().contains("ModularGuiContext"), "Drawing surface exposes a MUI host callback");
        check(Table.Cell.class.getDeclaredMethod("draw", DrawContext.class, Object.class,
                io.github.luyingyilong.ore.layout.Rect.class) != null, "Cell drawing shares the same context");
        int[] shortcuts = {0};
        io.github.luyingyilong.ore.debug.DebugBridge.setHostShortcut((key, scan) -> { shortcuts[0]++; return true; });
        try {
            Window root = Window.embedded();
            check(root.dispatchInput(new io.github.luyingyilong.ore.event.KeyEvent(root.getContext(),
                    io.github.luyingyilong.ore.event.KeyEvent.Action.PRESS, 297, 0, 0)) && shortcuts[0] == 1,
                    "Development shortcut works through Window, before text/viewer interception");
        } finally { io.github.luyingyilong.ore.debug.DebugBridge.setHostShortcut(null); }
        System.out.println("ORE_DRAW_CONTEXT_CONTRACT_PASS");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
