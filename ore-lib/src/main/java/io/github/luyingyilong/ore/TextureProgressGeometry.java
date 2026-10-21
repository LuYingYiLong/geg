package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.render.TexturePainter;
import io.github.luyingyilong.ore.render.TextureRegion;
import java.util.ArrayList;
import java.util.List;

/** CPU-only clipping in progress-image coordinates. UVs remain tied to the original image. */
final class TextureProgressGeometry {
    private record Point(double x, double y) { }
    private static final double EPSILON = 1.0e-9;

    static List<TexturePainter.Triangle> build(TextureRegion image, TextureProgressBar.FillMode mode,
            double fraction, double angle, double degrees, double cx, double cy,
            double x, double y, double sx, double sy) {
        if (image == null || fraction <= 0 || sx <= 0 || sy <= 0) return List.of();
        double w = image.getWidth(), h = image.getHeight();
        List<TexturePainter.Triangle> out = new ArrayList<>();
        if (mode.ordinal() < TextureProgressBar.FillMode.CLOCKWISE.ordinal()) {
            // Keep fractional UV boundaries: a 1px source stretched into a long bar must still
            // support intermediate progress. Rasterization resolves the final pixel boundary.
            double width = w * fraction, height = h * fraction;
            List<Point> polygon = switch (mode) {
                case LEFT_TO_RIGHT -> rectangle(0, 0, width, h);
                case RIGHT_TO_LEFT -> rectangle(w - width, 0, width, h);
                case TOP_TO_BOTTOM -> rectangle(0, 0, w, height);
                case BOTTOM_TO_TOP -> rectangle(0, h - height, w, height);
                case CENTER_HORIZONTAL -> rectangle((w - width) / 2, 0, width, h);
                case CENTER_VERTICAL -> rectangle(0, (h - height) / 2, w, height);
                default -> throw new AssertionError(mode);
            };
            triangulate(out, polygon, image, x, y, sx, sy);
        } else {
            double sweep = degrees * fraction;
            if (sweep <= 0) return List.of();
            if (sweep >= 360) triangulate(out, rectangle(0, 0, w, h), image, x, y, sx, sy);
            else {
                double start = switch (mode) {
                    case CLOCKWISE -> angle;
                    case COUNTER_CLOCKWISE -> angle - sweep;
                    case BIDIRECTIONAL_RADIAL -> angle - sweep / 2;
                    default -> throw new AssertionError(mode);
                };
                // Convex wedges of at most 90 degrees cover the sweep without overlapping interiors.
                for (double consumed = 0; consumed < sweep; consumed += 90) {
                    double a = Math.toRadians(start + consumed);
                    double b = Math.toRadians(start + Math.min(sweep, consumed + 90));
                    List<Point> polygon = clip(rectangle(0, 0, w, h), cx, cy, Math.cos(a), Math.sin(a), 1);
                    polygon = clip(polygon, cx, cy, Math.cos(b), Math.sin(b), -1);
                    triangulate(out, polygon, image, x, y, sx, sy);
                }
            }
        }
        return List.copyOf(out);
    }

    private static List<Point> rectangle(double x, double y, double w, double h) {
        if (w <= 0 || h <= 0) return List.of();
        return List.of(new Point(x, y), new Point(x + w, y), new Point(x + w, y + h), new Point(x, y + h));
    }

    private static List<Point> clip(List<Point> polygon, double cx, double cy, double dx, double dy, int side) {
        if (polygon.isEmpty()) return polygon;
        List<Point> out = new ArrayList<>();
        Point previous = polygon.get(polygon.size() - 1);
        double before = side * (dx * (previous.y - cy) - dy * (previous.x - cx));
        for (Point current : polygon) {
            double after = side * (dx * (current.y - cy) - dy * (current.x - cx));
            if ((before >= -EPSILON) != (after >= -EPSILON)) {
                double t = before / (before - after);
                out.add(new Point(previous.x + (current.x - previous.x) * t, previous.y + (current.y - previous.y) * t));
            }
            if (after >= -EPSILON) out.add(current);
            previous = current;
            before = after;
        }
        return out;
    }

    private static void triangulate(List<TexturePainter.Triangle> out, List<Point> polygon, TextureRegion image,
            double x, double y, double sx, double sy) {
        if (polygon.size() < 3) return;
        Point a = polygon.get(0);
        for (int i = 1; i + 1 < polygon.size(); i++) {
            Point b = polygon.get(i), c = polygon.get(i + 1);
            double cross = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
            if (Math.abs(cross) < EPSILON) continue;
            // Match the existing image quad winding, including when GL face culling is enabled.
            out.add(cross < 0 ? new TexturePainter.Triangle(vertex(a, image, x, y, sx, sy), vertex(b, image, x, y, sx, sy), vertex(c, image, x, y, sx, sy))
                    : new TexturePainter.Triangle(vertex(a, image, x, y, sx, sy), vertex(c, image, x, y, sx, sy), vertex(b, image, x, y, sx, sy)));
        }
    }

    private static TexturePainter.Vertex vertex(Point p, TextureRegion image, double x, double y, double sx, double sy) {
        double px = Math.max(0, Math.min(image.getWidth(), p.x)), py = Math.max(0, Math.min(image.getHeight(), p.y));
        return new TexturePainter.Vertex((float) (x + px * sx), (float) (y + py * sy),
                (float) (image.getX() + px), (float) (image.getY() + py));
    }
}
