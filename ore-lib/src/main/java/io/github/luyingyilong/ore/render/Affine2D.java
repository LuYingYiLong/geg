package io.github.luyingyilong.ore.render;

import io.github.luyingyilong.ore.layout.Rect;
import org.joml.Matrix4f;

/** Immutable double precision geometry; multiply applies the right operand first. */
public record Affine2D(double a, double b, double c, double d, double tx, double ty) {
    public static final Affine2D IDENTITY = new Affine2D(1, 0, 0, 1, 0, 0);
    public record Point(double x, double y) { }
    public static Affine2D translation(double x, double y) { return new Affine2D(1, 0, 0, 1, x, y); }
    public Affine2D multiply(Affine2D r) {
        return new Affine2D(a*r.a+c*r.b, b*r.a+d*r.b, a*r.c+c*r.d, b*r.c+d*r.d,
                a*r.tx+c*r.ty+tx, b*r.tx+d*r.ty+ty);
    }
    public Point transform(double x, double y) { return new Point(a*x+c*y+tx, b*x+d*y+ty); }
    public Affine2D inverse() {
        double det=a*d-b*c;
        if (!Double.isFinite(det) || det == 0) throw new IllegalArgumentException("Non-invertible transform");
        return new Affine2D(d/det,-b/det,-c/det,a/det,(c*ty-d*tx)/det,(b*tx-a*ty)/det);
    }
    public Rect bounds(Rect rect) {
        Point p=transform(rect.x(),rect.y()), q=transform(rect.right(),rect.y());
        Point r=transform(rect.right(),rect.bottom()), s=transform(rect.x(),rect.bottom());
        int x=floor(Math.min(Math.min(p.x,q.x),Math.min(r.x,s.x)));
        int y=floor(Math.min(Math.min(p.y,q.y),Math.min(r.y,s.y)));
        int right=ceil(Math.max(Math.max(p.x,q.x),Math.max(r.x,s.x)));
        int bottom=ceil(Math.max(Math.max(p.y,q.y),Math.max(r.y,s.y)));
        return new Rect(x,y,right-x,bottom-y);
    }
    private static int floor(double n) { return (int)Math.floor(snap(n)); }
    private static int ceil(double n) { return (int)Math.ceil(snap(n)); }
    private static double snap(double n) { return Math.abs(n-Math.rint(n))<1e-9?Math.rint(n):n; }
    public Matrix4f matrix() {
        for(double v:new double[]{a,b,c,d,tx,ty})if(!Float.isFinite((float)v))throw new IllegalArgumentException("Transform exceeds render precision");
        return new Matrix4f().m00((float)a).m01((float)b).m10((float)c).m11((float)d).m30((float)tx).m31((float)ty);
    }
}
