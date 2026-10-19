package io.github.luyingyilong.ore.render;

import io.github.luyingyilong.ore.layout.Rect;
import org.joml.Matrix4f;

/** Translation and positive uniform scale, in Minecraft GUI pixels. */
public record CanvasTransform(double x, double y, double scale) {
    public static final CanvasTransform IDENTITY = new CanvasTransform(0, 0, 1);

    public CanvasTransform { validate(x, y, scale); }

    public static void validate(double x, double y, double scale) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(scale) || scale <= 0)
            throw new IllegalArgumentException("Canvas requires finite translation and positive scale");
    }

    public static CanvasTransform fromPose(Matrix4f pose) {
        float s = pose.m00();
        if (Math.abs(pose.m01()) > 0.0001F || Math.abs(pose.m10()) > 0.0001F
                || Math.abs(s - pose.m11()) > 0.0001F || pose.m02() != 0 || pose.m12() != 0)
            throw new IllegalArgumentException("Canvas does not support rotated, sheared or nonuniform poses");
        return new CanvasTransform(pose.m30(), pose.m31(), s);
    }

    public double toLocalX(double screenX) { return (screenX - x) / scale; }
    public double toLocalY(double screenY) { return (screenY - y) / scale; }
    public double toScreenX(double localX) { return x + localX * scale; }
    public double toScreenY(double localY) { return y + localY * scale; }
    public Rect toScreen(Rect local) {
        int left = (int) Math.floor(toScreenX(local.x())), top = (int) Math.floor(toScreenY(local.y()));
        return new Rect(left, top, (int) Math.ceil(toScreenX(local.right())) - left,
                (int) Math.ceil(toScreenY(local.bottom())) - top);
    }
}
