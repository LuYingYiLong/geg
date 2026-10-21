package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.render.*;
import io.github.luyingyilong.ore.theme.*;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

public final class TextureProgressBarTest {
    private static final TextureRegion IMAGE = new TextureRegion(ResourceLocation.fromNamespaceAndPath("test", "atlas"), 64, 64, 3, 5, 24, 16);
    private static TextureProgressBar bar() { return new TextureProgressBar().setProgressTexture(TextureSource.region(IMAGE)); }
    public static void main(String[] args) {
        values(); sizing(); fills(); radial(); themesAndLifetime(); pointerTransparency();
        System.out.println("ORE_TEXTURE_PROGRESS_PASS");
    }
    private static void values() {
        TextureProgressBar p = bar(); int[] calls = {0}; p.valueChanged().connect(p, v -> calls[0]++);
        check(p.getMin() == 0 && p.getMax() == 100 && p.getValue() == 0 && p.getOpacity() == 1 && !p.getShowPercentage(), "defaults");
        check(!p.acceptsFocus() && !p.hit(0, 0), "display-only control");
        p.setValue(50).setValue(50).setValue(1000); check(calls[0] == 2 && p.getValue() == 100, "clamped change only");
        p.setLimitsNoSignal(-100, -10); check(p.getValue() == -10 && calls[0] == 2, "silent range clamp");
        p.setLimits(-100, -20); check(calls[0] == 3 && p.getValue() == -20, "range clamp notifies once");
        reject(() -> p.setLimits(1, 1)); reject(() -> p.setLimits(-Double.MAX_VALUE, Double.MAX_VALUE));
        reject(() -> p.setMin(Double.NaN)); reject(() -> p.setValue(Double.POSITIVE_INFINITY));
        check(p.getMin() == -100 && p.getMax() == -20 && p.getValue() == -20, "invalid mutation is atomic");
        reject(() -> p.setOpacity(-1)); reject(() -> p.setOpacity(Float.NaN)); reject(() -> p.setRadialFillDegrees(361));
        p.setRadialInitialAngle(-30); check(p.getRadialInitialAngle() == 330, "angle normalization");
        p.queueFree(); LifecycleScheduler.flush(); check(p.valueChanged().connectionCount() == 0, "source connections end with control");
    }
    private static void sizing() {
        TextureProgressBar p = bar().setValueNoSignal(50);
        check(p.getMinimumSize().equals(new Size(24, 16)), "original texture minimum");
        p.setProgressOffset(new Offset(-3, 4)); check(p.getMinimumSize().equals(new Size(24, 20)), "negative offset expands reference canvas");
        TextureRegion under = TextureRegion.full(IMAGE.getTexture(), 30, 20);
        p.setUnderTexture(TextureSource.region(under));
        check(p.getMinimumSize().equals(new Size(33, 20)), "all layers contribute to canvas");
        Rect area = new Rect(10, 20, 66, 40);
        var keep = p.getDrawGeometry(area);
        check(keep.underQuads().get(0).x() == 13 && keep.underQuads().get(0).width() == 30, "negative offset preserves layer relation");
        check(keep.progressTriangles().get(0).a().x() >= 10, "progress remains inside normalized reference");
        p.setStretchMode(TextureProgressBar.StretchMode.SCALE);
        var scale = p.getDrawGeometry(area); near(scale.underQuads().get(0).width(), 60, "uniform reference maps layers together");
        near(area(scale.progressTriangles()), 24 * 16 * .5 * 4, "fill geometry scales with reference");
        p.setStretchMode(TextureProgressBar.StretchMode.KEEP_ASPECT_CENTERED);
        check(p.getDrawGeometry(new Rect(0, 0, 100, 40)).underQuads().get(0).x() > 0, "aspect canvas is centered");
        p.setExpandMode(TextureRect.ExpandMode.IGNORE_SIZE); check(p.getMinimumSize().equals(new Size(0, 0)), "ignore intrinsic image size");
        p.setMinimum(5, 6); check(p.getMinimumSize().equals(new Size(5, 6)), "explicit minimum survives expand mode");
        check(p.getDrawGeometry(new Rect(0, 0, 0, 10)).progressTriangles().isEmpty(), "zero target skips drawing");
        p.setProgressTexture(null).setUnderTexture(null); check(p.getDrawGeometry(area).underQuads().isEmpty(), "missing images are transparent");
        p.setProgressTexture(TextureSource.region(TextureRegion.full(IMAGE.getTexture(), 1, 1)))
                .setProgressOffset(Offset.ZERO).setStretchMode(TextureProgressBar.StretchMode.SCALE).setValueNoSignal(25);
        near(area(p.getDrawGeometry(new Rect(0, 0, 100, 20)).progressTriangles()), 500, "small stretched sources preserve continuous progress");
        p.queueFree(); LifecycleScheduler.flush();
    }
    private static void fills() {
        TextureProgressBar p = bar(); Rect area = new Rect(0, 0, 24, 16);
        for (TextureProgressBar.FillMode mode : TextureProgressBar.FillMode.values()) {
            p.setFillMode(mode);
            for (int value : new int[]{0, 25, 50, 75, 100}) {
                p.setValueNoSignal(value); var geometry = p.getDrawGeometry(area);
                double expectedArea = mode == TextureProgressBar.FillMode.BIDIRECTIONAL_RADIAL
                        ? switch (value) { case 25 -> 128; case 75 -> 256; default -> 24 * 16 * value / 100.0; }
                        : 24 * 16 * value / 100.0;
                near(area(geometry.progressTriangles()), expectedArea, mode + " quarter-turn area");
                check(p.getDrawGeometry(area) == geometry, "unchanged frame reuses geometry");
                for (var triangle : geometry.progressTriangles()) {
                    check(cross(triangle.a(), triangle.b(), triangle.c()) < 0, "consistent front-face winding");
                    for (var v : List.of(triangle.a(), triangle.b(), triangle.c())) {
                        check(v.u() >= 3 && v.u() <= 27 && v.v() >= 5 && v.v() <= 21, "UVs stay inside atlas region");
                        near(v.u() - 3, v.x(), "UVs retain original image position"); near(v.v() - 5, v.y(), "vertical UVs are not compressed");
                    }
                }
            }
        }
        p.setFillMode(TextureProgressBar.FillMode.RIGHT_TO_LEFT).setValueNoSignal(50);
        check(p.getDrawGeometry(area).progressTriangles().stream().flatMap(t -> List.of(t.a(), t.b(), t.c()).stream()).allMatch(v -> v.x() >= 12), "right edge anchored");
        p.queueFree(); LifecycleScheduler.flush();
    }
    private static void radial() {
        TextureProgressBar p = bar(); Rect rect = new Rect(0, 0, 24, 16);
        for (var mode : List.of(TextureProgressBar.FillMode.CLOCKWISE, TextureProgressBar.FillMode.COUNTER_CLOCKWISE, TextureProgressBar.FillMode.BIDIRECTIONAL_RADIAL)) {
            p.setFillMode(mode);
            for (Offset offset : List.of(Offset.ZERO, new Offset(-7, 3), new Offset(99, -99))) {
                p.setRadialCenterOffset(offset);
                double cx = Math.max(0, Math.min(24, 12 + offset.x())), cy = Math.max(0, Math.min(16, 8 + offset.y()));
                for (int angle : new int[]{0, 31, 330}) for (int degrees : new int[]{0, 130, 360}) {
                    p.setRadialInitialAngle(angle).setRadialFillDegrees(degrees).setValueNoSignal(73);
                    var mesh = p.getDrawGeometry(rect).progressTriangles();
                    double sweep = degrees * .73;
                    double start = mode == TextureProgressBar.FillMode.CLOCKWISE ? angle : mode == TextureProgressBar.FillMode.COUNTER_CLOCKWISE ? angle - sweep : angle - sweep / 2;
                    for (int iy = 0; iy < 11; iy++) for (int ix = 0; ix < 13; ix++) {
                        double x = (ix + .371) * 24 / 13, y = (iy + .293) * 16 / 11;
                        double pointAngle = Math.toDegrees(Math.atan2(y - cy, x - cx));
                        double delta = ((pointAngle - start) % 360 + 360) % 360;
                        boolean expected = sweep > 0 && delta <= sweep;
                        long count = mesh.stream().filter(t -> contains(t, x, y)).count();
                        check((count > 0) == expected && count <= 1, "sector coverage without holes or alpha-overlap: " + mode + "/" + angle);
                    }
                }
            }
        }
        p.queueFree(); LifecycleScheduler.flush();
    }
    private static void themesAndLifetime() {
        TextureProgressBar p = new TextureProgressBar().setUnderTexture(TextureSource.themed(IconTheme.LIT_PROGRESS_UNDER))
                .setProgressTexture(TextureSource.themed(IconTheme.LIT_PROGRESS)).setValueNoSignal(80);
        check(p.getMinimumSize().equals(new Size(14, 14)), "built-in flame texture dimensions");
        var before = p.getDrawGeometry(new Rect(0, 0, 14, 14));
        p.setThemeOverride(IconTheme.LIT_PROGRESS, IMAGE);
        check(p.getMinimumSize().equals(new Size(24, 16)) && p.getDrawGeometry(new Rect(0, 0, 24, 16)) != before, "theme images invalidate measurement and geometry");
        p.setUnderTexture(null).setProgressTexture(TextureSource.themed(IconTheme.BURN_PROGRESS));
        check(p.getMinimumSize().equals(new Size(24, 16)), "built-in arrow texture dimensions");
        Control label = p.getChild(0);
        for (int i = 0; i < 50; i++) { p.setValueNoSignal(i); p.getDrawGeometry(new Rect(0, 0, 24, 16)); }
        check(p.getChildCount() == 1 && p.getChild(0) == label, "label is retained across frame and value changes");
        p.setRenderTransform(new Transform2D(0, 0, 45, 1, .5, .5));
        check(p.getRenderTransform().getRotationDegrees() == 45, "geometry fill does not impose axis-aligned scissors");
        p.queueFree(); LifecycleScheduler.flush(); check(label.isDisposed(), "internal label cleaned once with owner");
    }
    private static double area(List<TexturePainter.Triangle> mesh) { return mesh.stream().mapToDouble(t -> Math.abs(cross(t.a(), t.b(), t.c())) / 2).sum(); }
    private static void pointerTransparency() {
        Window root = new Window(true); Control content = new Control();
        Button button = new Button(net.minecraft.network.chat.Component.literal("Below"));
        button.setPosition(0, 0).setRequestedSize(30, 24);
        TextureProgressBar progress = bar().setShowPercentage(true);
        progress.setPosition(0, 0).setRequestedSize(30, 24);
        content.addChild(button); content.addChild(progress); root.setContent(content).setOverlay(new Overlay());
        root.layoutViewport(40, 30, InventoryTest.FONT);
        int[] calls = {0}; button.pressed().connect(root, () -> calls[0]++);
        io.github.luyingyilong.ore.event.WindowInputs.press(root, 5, 5, null);
        io.github.luyingyilong.ore.event.WindowInputs.release(root, 5, 5);
        check(calls[0] == 1, "percentage and image layers never intercept underlying controls");
        root.queueFree(); LifecycleScheduler.flush();
    }
    private static double cross(TexturePainter.Vertex a, TexturePainter.Vertex b, TexturePainter.Vertex c) { return (b.x() - a.x()) * (c.y() - a.y()) - (b.y() - a.y()) * (c.x() - a.x()); }
    private static boolean contains(TexturePainter.Triangle t, double x, double y) {
        var p = new TexturePainter.Vertex((float) x, (float) y, 0, 0);
        return cross(t.a(), t.b(), p) < -1.0e-6 && cross(t.b(), t.c(), p) < -1.0e-6 && cross(t.c(), t.a(), p) < -1.0e-6;
    }
    private static void near(double a, double b, String message) { check(Math.abs(a - b) < .001, message + ": " + a + " != " + b); }
    private static void reject(Runnable action) { try { action.run(); throw new AssertionError("Expected invalid value rejection"); } catch (IllegalArgumentException expected) { } }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
