package com.ryanhoegg.voronoi.core.geometry;

public final class Geometry2D {
    private Geometry2D() {}

    /** Signed orientation of triangle ABC.
     *  > 0 => one turning direction, < 0 => the other, 0 => collinear.
     *  In math coords (y up): >0 means CCW.
     */
    public static double orientation(Point a, Point b, Point c) {
        return (b.x() - a.x()) * (c.y() - a.y())
                - (b.y() - a.y()) * (c.x() - a.x());
    }

    public static double parabolaY(Point focus, double x, double directrixY) {
        double fx = focus.x();
        double fy = focus.y();
        double d = directrixY;

        return ((x - fx) * (x - fx) + fy * fy - d * d) / (2.0 * (fy - d));
    }

    /**
     * Circumcenter of triangle ABC, or null if the points are (almost) collinear.
     *
     * Works in normal math coordinates (y up). In screen coords (y down) the
     * circle is the same; only how you *interpret* orientation changes.
     */
    public static Point circumcenter(Point a, Point b, Point c) {
        double ax = a.x(), ay = a.y();
        double bx = b.x(), by = b.y();
        double cx = c.x(), cy = c.y();

        // Denominator is 2 * signed area of the triangle.
        double d = 2.0 * (ax * (by - cy)
                + bx * (cy - ay)
                + cx * (ay - by));

        if (Math.abs(d) < 1e-9) {
            // Collinear or numerically degenerate: no unique circumcircle.
            return null;
        }

        double a2 = ax * ax + ay * ay;
        double b2 = bx * bx + by * by;
        double c2 = cx * cx + cy * cy;

        double ux = (a2 * (by - cy)
                + b2 * (cy - ay)
                + c2 * (ay - by)) / d;

        double uy = (a2 * (cx - bx)
                + b2 * (ax - cx)
                + c2 * (bx - ax)) / d;

        return new Point(ux, uy);
    }
}
