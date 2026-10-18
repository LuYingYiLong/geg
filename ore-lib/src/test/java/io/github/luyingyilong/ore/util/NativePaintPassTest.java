package io.github.luyingyilong.ore.util;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class NativePaintPassTest {
    public static void main(String[] args) {
        NativePaintPass pass = new NativePaintPass();
        List<String> draws = new ArrayList<>();
        Runnable nativeWidget = () -> {
            if (!pass.accepts(true)) return;
            draws.add("input background");
            draws.add("stencil push");
            draws.add("text, placeholder, caret and selection");
            draws.add("stencil pop");
        };
        draws.add("drawer background");
        pass.paint(nativeWidget);
        draws.add("embedded scrollbar");
        draws.add("higher modal");
        // MUI visits this sibling after the Ore canvas: it must not paint or touch stencil again.
        nativeWidget.run();
        check(draws.equals(List.of("drawer background", "input background", "stencil push",
                "text, placeholder, caret and selection", "stencil pop", "embedded scrollbar", "higher modal")));
        check(!pass.active() && pass.accepts(false) && !pass.accepts(true));
        try {
            pass.paint(() -> { throw new IllegalArgumentException("draw failure"); });
            throw new AssertionError("Expected draw failure");
        } catch (IllegalArgumentException expected) {
            check(!pass.active() && !pass.accepts(true));
        }

        // The nested MUI traversal must apply its context only once, without dropping Drawer depth.
        Matrix4f context = new Matrix4f().translate(17, 23, 0).scale(1.5F, 1.5F, 1);
        Matrix4f pose = new Matrix4f(context).translate(0, 0, 300);
        pose.mul(new Matrix4f(context).invert());
        pose.mul(new Matrix4f(context).translate(70, 80, 0));
        Vector3f point = pose.transformPosition(new Vector3f());
        check(Math.abs(point.x - 122) < 0.001F && Math.abs(point.y - 143) < 0.001F);
        check(Math.abs(point.z - 300) < 0.001F);
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Native input paint order or transform regression");
    }
}
