package io.github.luyingyilong.ore.render;

import io.github.luyingyilong.ore.control.base.Rect;

/** Presentation transform. Layout and Offset remain independent of this value. */
public record Transform2D(double getTranslationX, double getTranslationY, double getRotationDegrees,
                          double getScale, double getPivotX, double getPivotY) {
    public static final Transform2D IDENTITY = new Transform2D(0,0,0,1,0.5,0.5);
    public Transform2D {
        if (!Double.isFinite(getTranslationX) || !Double.isFinite(getTranslationY)
                || !Double.isFinite(getRotationDegrees) || !Double.isFinite(getScale) || getScale<=0
                || !Float.isFinite((float)getScale) || (float)getScale==0 || !Float.isFinite((float)(1/getScale))
                || !Float.isFinite((float)getTranslationX) || !Float.isFinite((float)getTranslationY)
                || !Double.isFinite(getPivotX) || !Double.isFinite(getPivotY)
                || getPivotX<0 || getPivotX>1 || getPivotY<0 || getPivotY>1)
            throw new IllegalArgumentException("Transform requires finite values, positive scale and a normalized pivot");
    }
    public static Transform2D rotation(double degrees) { return new Transform2D(0,0,degrees,1,0.5,0.5); }
    public Affine2D toAffine(Rect bounds) {
        double radians=Math.toRadians(getRotationDegrees%360), c=Math.cos(radians)*getScale, s=Math.sin(radians)*getScale;
        double x=bounds.x()+bounds.width()*getPivotX, y=bounds.y()+bounds.height()*getPivotY;
        return Affine2D.translation(x+getTranslationX,y+getTranslationY)
                .multiply(new Affine2D(c,s,-s,c,0,0)).multiply(Affine2D.translation(-x,-y));
    }
}
