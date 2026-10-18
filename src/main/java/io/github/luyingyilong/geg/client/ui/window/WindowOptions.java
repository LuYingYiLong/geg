package io.github.luyingyilong.geg.client.ui.window;

/**
 * Sizes are GLFW window/client logical units. GUI scale is DPI-adjusted, then rounded to
 * whole framebuffer pixels so pixel-art UI is never presented at a fractional scale.
 */
public record WindowOptions(String title, int width, int height, int minimumWidth, int minimumHeight,
                            double guiScale) {
    public WindowOptions {
        if (title == null || width < 1 || height < 1 || minimumWidth < 1 || minimumHeight < 1
                || !Double.isFinite(guiScale) || guiScale <= 0)
            throw new IllegalArgumentException("Invalid native window options");
    }

    public static WindowOptions defaults(String title) {
        return new WindowOptions(title, 1280, 800, 800, 480, 2);
    }
}
