package io.github.luyingyilong.ore.control.display;

/** Test-only baseline observation across control packages. */
public final class LabelTestAccess {
    private LabelTestAccess() { }
    public static float firstLineOrigin(Label label, int height, int lines) { return label.firstLineOrigin(height, lines); }
}
