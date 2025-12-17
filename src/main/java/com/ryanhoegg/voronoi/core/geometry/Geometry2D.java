package com.ryanhoegg.voronoi.core.geometry;

import java.util.function.DoublePredicate;
import java.util.function.DoubleUnaryOperator;

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

    /**
     * Diagnostic result for parabola intersection analysis.
     * Contains both roots, contract verification, and convergence metrics.
     */
    public static class IntersectionDiagnostic {
        public final double chosenX;
        public final double root1;
        public final double root2;
        public final boolean contractValid; // |yA - yB| < 1e-6 at chosenX
        public final double contractError;  // |yA - yB| at chosenX
        public final double root1_yA;
        public final double root1_yB;
        public final double root2_yA;
        public final double root2_yB;

        public IntersectionDiagnostic(double chosenX, double root1, double root2,
                                     boolean contractValid, double contractError,
                                     double root1_yA, double root1_yB,
                                     double root2_yA, double root2_yB) {
            this.chosenX = chosenX;
            this.root1 = root1;
            this.root2 = root2;
            this.contractValid = contractValid;
            this.contractError = contractError;
            this.root1_yA = root1_yA;
            this.root1_yB = root1_yB;
            this.root2_yA = root2_yA;
            this.root2_yB = root2_yB;
        }

        public void printDiagnostic(String label) {
            System.out.printf("[IntersectionDiagnostic] %s%n", label);
            System.out.printf("  Chosen X: %.6f%n", chosenX);
            System.out.printf("  Root 1: %.6f → yA=%.6f, yB=%.6f, |yA-yB|=%.9f%n",
                root1, root1_yA, root1_yB, Math.abs(root1_yA - root1_yB));
            System.out.printf("  Root 2: %.6f → yA=%.6f, yB=%.6f, |yA-yB|=%.9f%n",
                root2, root2_yA, root2_yB, Math.abs(root2_yA - root2_yB));
            System.out.printf("  Contract valid at chosen X: %s (error=%.9f)%n",
                contractValid, contractError);
        }
    }

    /**
     * Diagnostic version of parabolaIntersectionX that returns detailed analysis.
     * Use this to verify that the chosen root actually satisfies the intersection contract.
     */
    public static IntersectionDiagnostic parabolaIntersectionXDiagnostic(
            Point focus1, Point focus2, double directrixY) {

        // Call the main method to get the chosen X
        double chosenX = parabolaIntersectionX(focus1, focus2, directrixY);

        // Verify contract at chosen X
        double yA_chosen = parabolaY(focus1, chosenX, directrixY);
        double yB_chosen = parabolaY(focus2, chosenX, directrixY);
        double errorChosen = Math.abs(yA_chosen - yB_chosen);
        boolean contractValid = errorChosen < 1e-6;

        // Now compute BOTH roots explicitly (duplicating logic from main method)
        Point f1 = focus1, f2 = focus2;
        if (focus1.x() > focus2.x()) {
            f1 = focus2;
            f2 = focus1;
        }

        final double fx1 = f1.x(), fy1 = f1.y();
        final double fx2 = f2.x(), fy2 = f2.y();
        final double d = directrixY;

        double A1 = 1.0 / (2.0 * (fy1 - d));
        double B1 = -fx1 / (fy1 - d);
        double C1 = (fx1*fx1 + fy1*fy1 - d*d) / (2.0 * (fy1 - d));

        double A2 = 1.0 / (2.0 * (fy2 - d));
        double B2 = -fx2 / (fy2 - d);
        double C2 = (fx2*fx2 + fy2*fy2 - d*d) / (2.0 * (fy2 - d));

        double a = (A1 - A2);
        double b = (B1 - B2);
        double c = (C1 - C2);

        double root1 = Double.NaN, root2 = Double.NaN;
        if (Math.abs(a) >= 1e-12) {
            double disc = b*b - 4.0*a*c;
            if (disc >= 0.0) {
                double sqrt = Math.sqrt(disc);
                root1 = (-b - sqrt) / (2.0*a);
                root2 = (-b + sqrt) / (2.0*a);
            }
        }

        // Verify both roots
        double r1_yA = Double.isFinite(root1) ? parabolaY(focus1, root1, directrixY) : Double.NaN;
        double r1_yB = Double.isFinite(root1) ? parabolaY(focus2, root1, directrixY) : Double.NaN;
        double r2_yA = Double.isFinite(root2) ? parabolaY(focus1, root2, directrixY) : Double.NaN;
        double r2_yB = Double.isFinite(root2) ? parabolaY(focus2, root2, directrixY) : Double.NaN;

        return new IntersectionDiagnostic(chosenX, root1, root2,
            contractValid, errorChosen, r1_yA, r1_yB, r2_yA, r2_yB);
    }

    /**
     * Compute parabola intersection X coordinate near a circle event.
     * Uses the root CLOSEST to the circle center X to ensure correct convergence behavior.
     *
     * CRITICAL: Near circle events, the standard leftToRightBoundary heuristic chooses
     * the WRONG root (the outer intersection far from center). This method chooses the
     * root that minimizes |rootX - circleCenterX|, ensuring breakpoints converge toward
     * the circle center as sweepY approaches the event.
     *
     * @param focus1 First parabola focus
     * @param focus2 Second parabola focus
     * @param directrixY The sweep line / directrix Y coordinate
     * @param circleCenterX The X coordinate of the circle center (convergence target)
     * @return The intersection X coordinate closest to circleCenterX
     */
    public static double parabolaIntersectionXNearCircleEvent(
            Point focus1, Point focus2, double directrixY, double circleCenterX) {

        // Ensure consistent ordering
        if (focus1.x() > focus2.x()) {
            Point tmp = focus1;
            focus1 = focus2;
            focus2 = tmp;
        }

        final double fx1 = focus1.x(), fy1 = focus1.y();
        final double fx2 = focus2.x(), fy2 = focus2.y();
        final double d = directrixY;

        // Handle degenerate cases
        final double EPS = 1e-9;
        double den1 = (fy1 - d);
        double den2 = (fy2 - d);

        if (Math.abs(den1) < EPS && Math.abs(den2) < EPS) {
            return 0.5 * (fx1 + fx2);
        }
        if (Math.abs(den1) < EPS) return fx1;
        if (Math.abs(den2) < EPS) return fx2;

        // Solve quadratic equation for intersection
        double A1 = 1.0 / (2.0 * den1);
        double B1 = -fx1 / den1;
        double C1 = (fx1*fx1 + fy1*fy1 - d*d) / (2.0 * den1);

        double A2 = 1.0 / (2.0 * den2);
        double B2 = -fx2 / den2;
        double C2 = (fx2*fx2 + fy2*fy2 - d*d) / (2.0 * den2);

        double a = (A1 - A2);
        double b = (B1 - B2);
        double c = (C1 - C2);

        // Linear case
        if (Math.abs(a) < 1e-12) {
            if (Math.abs(b) < 1e-12) {
                return 0.5 * (fx1 + fx2);
            }
            return -c / b;
        }

        // Quadratic case - compute both roots
        double disc = b*b - 4.0*a*c;
        if (disc < 0.0) {
            return 0.5 * (fx1 + fx2);
        }

        double sqrt = Math.sqrt(disc);
        double r1 = (-b - sqrt) / (2.0*a);
        double r2 = (-b + sqrt) / (2.0*a);

        // Check finiteness
        boolean r1ok = Double.isFinite(r1);
        boolean r2ok = Double.isFinite(r2);
        if (!r1ok && !r2ok) return 0.5 * (fx1 + fx2);
        if (r1ok && !r2ok) return r1;
        if (!r1ok && r2ok) return r2;

        // CRITICAL: Choose the root CLOSEST to circle center X
        // This ensures breakpoints converge toward the circle center as sweep → event
        double dist1 = Math.abs(r1 - circleCenterX);
        double dist2 = Math.abs(r2 - circleCenterX);

        return (dist1 < dist2) ? r1 : r2;
    }

    public static double parabolaIntersectionX(Point focus1, Point focus2, double directrixY) {
        // Ensure consistent "left arc then right arc" behavior
        if (focus1.x() > focus2.x()) {
            Point tmp = focus1;
            focus1 = focus2;
            focus2 = tmp;
        }

        final double fx1 = focus1.x(), fy1 = focus1.y();
        final double fx2 = focus2.x(), fy2 = focus2.y();
        final double d = directrixY;

        // In Fortune, foci should be above the sweep/directrix (fy <= d).
        // If a focus is on/too close to the directrix, the parabola becomes numerically nasty.
        final double EPS = 1e-9;
        double den1 = (fy1 - d);
        double den2 = (fy2 - d);

        if (Math.abs(den1) < EPS && Math.abs(den2) < EPS) {
            // Both degenerate; best effort fallback
            return 0.5 * (fx1 + fx2);
        }
        if (Math.abs(den1) < EPS) {
            // focus1 essentially on directrix: treat breakpoint as focus1.x-ish
            return fx1;
        }
        if (Math.abs(den2) < EPS) {
            return fx2;
        }

        // Parabola y(x) for focus (fx,fy) and directrix y=d:
        // y = ((x-fx)^2 + fy^2 - d^2) / (2*(fy-d))
        // Expand into quadratic: A x^2 + B x + C
        double A1 = 1.0 / (2.0 * den1);
        double B1 = -fx1 / den1;
        double C1 = (fx1*fx1 + fy1*fy1 - d*d) / (2.0 * den1);

        double A2 = 1.0 / (2.0 * den2);
        double B2 = -fx2 / den2;
        double C2 = (fx2*fx2 + fy2*fy2 - d*d) / (2.0 * den2);

        // Solve (A1-A2)x^2 + (B1-B2)x + (C1-C2) = 0
        double a = (A1 - A2);
        double b = (B1 - B2);
        double c = (C1 - C2);

        // If same fy (or effectively same), a ~ 0 => linear.
        if (Math.abs(a) < 1e-12) {
            if (Math.abs(b) < 1e-12) {
                // Parabolas nearly identical in this regime; fallback to midpoint
                return 0.5 * (fx1 + fx2);
            }
            return -c / b;
        }

        double disc = b*b - 4.0*a*c;
        if (disc < 0.0) {
            // No real intersection (can happen with numeric issues); fallback
            return 0.5 * (fx1 + fx2);
        }

        double sqrt = Math.sqrt(disc);
        double r1 = (-b - sqrt) / (2.0*a);
        double r2 = (-b + sqrt) / (2.0*a);

        // If only one is finite, use it
        boolean r1ok = Double.isFinite(r1);
        boolean r2ok = Double.isFinite(r2);
        if (!r1ok && !r2ok) return 0.5 * (fx1 + fx2);
        if (r1ok && !r2ok) return r1;
        if (!r1ok && r2ok) return r2;

        // Choose the correct breakpoint for "focus1 arc to the left, focus2 arc to the right"
        // by probing which parabola is lower just left/right of the candidate root.
        // This avoids the “pick the +sqrt root” trap.
        double span = Math.max(1.0, Math.abs(fx2 - fx1));
        double dx = 1e-4 * span; // tiny in world units; tune if needed

        DoubleUnaryOperator y1 = (x) ->
                ((x - fx1)*(x - fx1) + fy1*fy1 - d*d) / (2.0*(fy1 - d));
        DoubleUnaryOperator y2 = (x) ->
                ((x - fx2)*(x - fx2) + fy2*fy2 - d*d) / (2.0*(fy2 - d));

        // Predicate: left side prefers 1, right side prefers 2 (i.e., boundary between arcs)
        DoublePredicate isLeftToRightBoundary = (r) -> {
            double xl = r - dx;
            double xr = r + dx;
            double dl = y1.applyAsDouble(xl) - y2.applyAsDouble(xl); // <0 => 1 lower
            double dr = y1.applyAsDouble(xr) - y2.applyAsDouble(xr); // >0 => 2 lower
            return (dl <= 0.0 && dr >= 0.0);
        };

        boolean r1Boundary = isLeftToRightBoundary.test(r1);
        boolean r2Boundary = isLeftToRightBoundary.test(r2);

        if (r1Boundary && !r2Boundary) return r1;
        if (r2Boundary && !r1Boundary) return r2;

        // If both/neither match (rare), pick the root that lies between the foci if possible.
        double lo = fx1, hi = fx2;
        boolean r1Between = (r1 >= lo && r1 <= hi);
        boolean r2Between = (r2 >= lo && r2 <= hi);
        if (r1Between && !r2Between) return r1;
        if (r2Between && !r1Between) return r2;

        // Final fallback: closest to midpoint
        double mid = 0.5 * (fx1 + fx2);
        return (Math.abs(r1 - mid) < Math.abs(r2 - mid)) ? r1 : r2;
    }
}
