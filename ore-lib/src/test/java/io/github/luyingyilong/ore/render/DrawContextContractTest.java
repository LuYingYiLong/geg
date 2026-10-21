package io.github.luyingyilong.ore.render;

import io.github.luyingyilong.ore.control.data.Table;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.window.Window;
import java.lang.reflect.Method;

/** Protect the portable public drawing boundary, rather than implementation-specific call order. */
public final class DrawContextContractTest {
    public static void main(String[] args) throws Exception {
        for (Class<?> control : new Class<?>[]{io.github.luyingyilong.ore.control.display.Label.class,
                io.github.luyingyilong.ore.control.layout.Panel.class, io.github.luyingyilong.ore.control.display.TextureRect.class})
            for (Method method : control.getDeclaredMethods())
                check(!(java.lang.reflect.Modifier.isPublic(method.getModifiers()) && method.getName().matches("(?:draw|paint|render).*")),
                        "Control exposes an alternate public drawing entry: " + method);
        for (Method method : io.github.luyingyilong.ore.control.display.Label.class.getDeclaredMethods())
            check(!(method.getName().equals("draw") && java.lang.reflect.Modifier.isStatic(method.getModifiers())), "Static Label drawing bypass returned");
        for (Method method : io.github.luyingyilong.ore.control.layout.Panel.class.getDeclaredMethods())
            check(!method.getName().equals("drawAt"), "Panel drawing bypass returned");
        for (Method method : io.github.luyingyilong.ore.window.WindowContext.class.getDeclaredMethods())
            check(!method.getName().equals("getTextPainter"), "Shared immediate Label painter returned");
        for (Class<?> control : new Class<?>[]{Control.class, Window.class}) {
            control.getMethod("draw", DrawContext.class, int.class, int.class);
            for (Method method : control.getDeclaredMethods())
                if (method.getName().equals("draw") || method.getName().equals("render") || method.getName().equals("renderTooltip"))
                    for (Class<?> parameter : method.getParameterTypes())
                        check(!parameter.getName().contains("ModularGuiContext"), "Legacy Control drawing overload returned");
        }
        for (Method method : DrawContext.class.getDeclaredMethods())
            for (Class<?> parameter : method.getParameterTypes())
                check(!parameter.getName().contains("ModularGuiContext"), "Drawing surface exposes a MUI host callback");
        check(Table.Cell.class.getMethod("draw", DrawContext.class, Object.class,
                io.github.luyingyilong.ore.control.base.Rect.class) != null, "Cell drawing shares the same context");
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
