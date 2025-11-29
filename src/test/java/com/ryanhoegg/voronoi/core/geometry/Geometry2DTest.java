package com.ryanhoegg.voronoi.core.geometry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class Geometry2DTest {

    @Test
    public void orientation_basic() {
        Point a =  new Point(1, 2);
        Point b = new Point(3, 4);
        assertAll(
                // clockwise
                () -> assertTrue(Geometry2D.orientation(a, b, new Point(4, 0)) < 0),
                // counterclockwise
                () -> assertTrue(Geometry2D.orientation(a, b, new Point(0, 10)) > 0),
                // ~linear
                () -> assertEquals(0.0, Geometry2D.orientation(a, b, new Point(5, 6)))
        );
    }

    @Test
    public void circumcenter_rightTriangle() {
        // Right triangle at origin: A(0,0), B(2,0), C(0,2)
        // Hypotenuse is BC; circumcenter is midpoint of BC: (1,1)
        Point a = new Point(0, 0);
        Point b = new Point(2, 0);
        Point c = new Point(0, 2);

        Point o = Geometry2D.circumcenter(a, b, c);
        assertNotNull(o);

        assertAll(
                () -> assertEquals(1.0, o.x(), 1e-9),
                () -> assertEquals(1.0, o.y(), 1e-9)
        );
    }

    @Test
    public void circumcenter_equalDistances() {
        // Generic scalene triangle
        Point a = new Point(1, 2);
        Point b = new Point(4, 3);
        Point c = new Point(-2, 5);

        Point o = Geometry2D.circumcenter(a, b, c);
        assertNotNull(o);

        double da = dist2(o, a);
        double db = dist2(o, b);
        double dc = dist2(o, c);

        assertAll( // all three should be equal
                () -> assertEquals(da, db, 1e-6),
                () -> assertEquals(db, dc, 1e-6)
        );
    }

    @Test
    public void circumcenter_collinearIsNull() {
        Point a = new Point(0, 0);
        Point b = new Point(2, 2);
        Point c = new Point(4, 4); // clearly on same line

        assertNull(Geometry2D.circumcenter(a, b, c));
    }

    // helper for tests
    private static double dist2(Point p, Point q) {
        double dx = p.x() - q.x();
        double dy = p.y() - q.y();
        return dx * dx + dy * dy;
    }
}
