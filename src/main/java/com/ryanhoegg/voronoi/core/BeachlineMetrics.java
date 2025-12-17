package com.ryanhoegg.voronoi.core;

import com.ryanhoegg.voronoi.core.geometry.Bounds;
import com.ryanhoegg.voronoi.core.geometry.Geometry2D;
import com.ryanhoegg.voronoi.core.geometry.Point;

import java.util.List;

/**
 * Utility for measuring beachline arc properties at a specific sweep Y position.
 * Uses a temporary FortuneContext for measurement without affecting visualization state.
 *
 * <p>This class is shared between CircleEventSelector and CircleEventZoom to ensure
 * consistent arc-length measurement and triple detection across the codebase.</p>
 *
 * @deprecated This class is not currently used. The codebase uses RenderedBeachline
 * for segmentation and measurement instead. Kept for reference and potential future use.
 */
@Deprecated
public class BeachlineMetrics {

    /**
     * Transform parameters for world→screen conversion.
     * Matches the rendering transform: (world - focus) * zoom + screenCenter
     */
    public static class Transform {
        public final Point screenCenter;
        public final Point focus;
        public final float zoom;

        public Transform(Point screenCenter, Point focus, float zoom) {
            this.screenCenter = screenCenter;
            this.focus = focus;
            this.zoom = zoom;
        }

        public double worldToScreenX(double wx) {
            return (wx - focus.x()) * zoom + screenCenter.x();
        }

        public double worldToScreenY(double wy) {
            return (wy - focus.y()) * zoom + screenCenter.y();
        }
    }

    /**
     * Result of beachline analysis at a specific Y position.
     */
    public static class TripleMetrics {
        public final boolean tripleExists;
        public final FortuneContext.BeachArc prevArc;
        public final FortuneContext.BeachArc doomedArc;
        public final FortuneContext.BeachArc nextArc;
        public final double prevArcLengthPx;
        public final double doomedArcLengthPx;
        public final double nextArcLengthPx;

        private TripleMetrics(boolean tripleExists,
                              FortuneContext.BeachArc prevArc,
                              FortuneContext.BeachArc doomedArc,
                              FortuneContext.BeachArc nextArc,
                              double prevArcLengthPx,
                              double doomedArcLengthPx,
                              double nextArcLengthPx) {
            this.tripleExists = tripleExists;
            this.prevArc = prevArc;
            this.doomedArc = doomedArc;
            this.nextArc = nextArc;
            this.prevArcLengthPx = prevArcLengthPx;
            this.doomedArcLengthPx = doomedArcLengthPx;
            this.nextArcLengthPx = nextArcLengthPx;
        }

        public static TripleMetrics tripleNotFound() {
            return new TripleMetrics(false, null, null, null, 0.0, 0.0, 0.0);
        }

        public static TripleMetrics of(FortuneContext.BeachArc prevArc,
                                       FortuneContext.BeachArc doomedArc,
                                       FortuneContext.BeachArc nextArc,
                                       double prevArcLengthPx,
                                       double doomedArcLengthPx,
                                       double nextArcLengthPx) {
            return new TripleMetrics(true, prevArc, doomedArc, nextArc,
                    prevArcLengthPx, doomedArcLengthPx, nextArcLengthPx);
        }

        public boolean allFinite() {
            return Double.isFinite(prevArcLengthPx) &&
                   Double.isFinite(doomedArcLengthPx) &&
                   Double.isFinite(nextArcLengthPx);
        }
    }

    /**
     * Analyze the beachline at a specific sweep Y position for the doomed triple.
     *
     * <p><b>IMPORTANT:</b> Creates a temporary FortuneContext and advances it to sweepY.
     * Does not mutate any visualization state.</p>
     *
     * @param sites The cluster sites
     * @param bounds The Fortune algorithm bounds
     * @param circleEventSites The circle event triple to find (identifies doomed arc)
     * @param sweepY The Y position to analyze
     * @param transform The world→screen transform parameters
     * @param epsilon Small value for numerical comparisons
     * @return TripleMetrics containing arc lengths and existence info
     */
    public static TripleMetrics analyzeTripleAt(List<Point> sites,
                                                Bounds bounds,
                                                FortuneContext.CircleEvent.Sites circleEventSites,
                                                double sweepY,
                                                Transform transform,
                                                double epsilon) {
        // Build temporary FortuneContext and advance to sweepY
        FortuneContext tempCtx = new FortuneContext(sites, bounds);
        advanceFortuneToY(tempCtx, sweepY);

        // Find the doomed arc in the beachline
        FortuneContext.BeachArc doomedArc = findDoomedArcInBeachline(tempCtx, circleEventSites);

        if (doomedArc == null || doomedArc.prev == null || doomedArc.next == null) {
            return TripleMetrics.tripleNotFound();
        }

        // Measure arc lengths in screen pixels for all three arcs
        // Pass bounds to clip measurements to visible world region
        double prevLenPx = computeArcLengthScreenPixels(doomedArc.prev, sweepY, bounds, transform, epsilon);
        double doomedLenPx = computeArcLengthScreenPixels(doomedArc, sweepY, bounds, transform, epsilon);
        double nextLenPx = computeArcLengthScreenPixels(doomedArc.next, sweepY, bounds, transform, epsilon);

        return TripleMetrics.of(doomedArc.prev, doomedArc, doomedArc.next, prevLenPx, doomedLenPx, nextLenPx);
    }

    /**
     * Advance a FortuneContext to the specified sweep Y position.
     * Steps through events until nextEventY > sweepY or queue is empty.
     */
    private static void advanceFortuneToY(FortuneContext ctx, double sweepY) {
        while (true) {
            Double nextY = ctx.nextEventY();
            if (nextY == null || nextY > sweepY) {
                break;
            }
            ctx.step();
        }
    }

    /**
     * Find the doomed arc in the beachline that matches the circle event sites.
     * The doomed arc is identified by matching its prev/site/next triple against the event sites.
     */
    private static FortuneContext.BeachArc findDoomedArcInBeachline(FortuneContext ctx,
                                                                     FortuneContext.CircleEvent.Sites sites) {
        FortuneContext.BeachArc arc = ctx.beachLine();
        while (arc != null) {
            if (arc.prev != null && arc.next != null) {
                FortuneContext.CircleEvent.Sites arcTriple =
                    new FortuneContext.CircleEvent.Sites(arc.prev.site, arc.site, arc.next.site);
                if (arcTriple.matches(sites)) {
                    return arc;
                }
            }
            arc = arc.next;
        }
        return null;
    }

    /**
     * Compute the arc length in SCREEN PIXELS using polyline sampling.
     * Samples points along the parabola between breakpoints, converts to screen space
     * using the provided transform, and sums distances.
     * Clips the arc measurement to the visible world bounds.
     *
     * @param arc The beach arc to measure
     * @param sweepY The sweep line Y position
     * @param bounds The world bounds for clipping (only measure visible portion)
     * @param transform The world→screen transform parameters
     * @param epsilon Small value for numerical comparisons
     * @return Arc length in screen pixels, or 0.0 if not computable
     */
    private static double computeArcLengthScreenPixels(FortuneContext.BeachArc arc,
                                                       double sweepY,
                                                       Bounds bounds,
                                                       Transform transform,
                                                       double epsilon) {
        if (arc.prev == null || arc.next == null) {
            return 0.0;
        }

        // Compute breakpoint X coordinates
        double xLeft = Geometry2D.parabolaIntersectionX(arc.prev.site, arc.site, sweepY);
        double xRight = Geometry2D.parabolaIntersectionX(arc.site, arc.next.site, sweepY);

        if (!Double.isFinite(xLeft) || !Double.isFinite(xRight)) {
            return 0.0;
        }

        // Ensure left < right
        double x0 = Math.min(xLeft, xRight);
        double x1 = Math.max(xLeft, xRight);

        // CRITICAL: Clip breakpoints to visible world bounds
        // When sweep is near site Y, breakpoints can be at x=±infinity or far off-screen
        x0 = Math.max(x0, bounds.minX());
        x1 = Math.min(x1, bounds.maxX());

        // Check if arc is visible at all
        if (x1 <= bounds.minX() || x0 >= bounds.maxX()) {
            return 0.0; // Arc is entirely off-screen
        }

        double xSpan = x1 - x0;

        if (xSpan < epsilon) {
            return 0.0; // Too narrow to measure
        }

        // Sample based on screen pixels: use ~2-3 pixels per sample in screen space
        double screenXSpan = Math.abs(transform.worldToScreenX(x1) - transform.worldToScreenX(x0));
        int numSamples = Math.max(10, (int) (screenXSpan / 2.5)); // at least 10 samples
        numSamples = Math.min(numSamples, 200); // cap at 200 to avoid excessive computation

        // Sample points along the arc and convert to screen space
        double totalScreenLength = 0.0;
        double prevY = Geometry2D.parabolaY(arc.site, x0, sweepY);
        if (!Double.isFinite(prevY)) {
            return 0.0;
        }
        double prevScreenX = transform.worldToScreenX(x0);
        double prevScreenY = transform.worldToScreenY(prevY);

        for (int i = 1; i <= numSamples; i++) {
            double t = i / (double) numSamples;
            double worldX = x0 + t * xSpan;
            double worldY = Geometry2D.parabolaY(arc.site, worldX, sweepY);

            if (!Double.isFinite(worldY)) {
                continue; // Skip non-finite points
            }

            double screenX = transform.worldToScreenX(worldX);
            double screenY = transform.worldToScreenY(worldY);

            double dx = screenX - prevScreenX;
            double dy = screenY - prevScreenY;
            totalScreenLength += Math.sqrt(dx * dx + dy * dy);

            prevScreenX = screenX;
            prevScreenY = screenY;
        }

        return totalScreenLength;
    }

    /**
     * Check if a point is in the triple (prev/site/next) of an arc.
     */
    public static boolean isInTriple(Point site, FortuneContext.BeachArc arc) {
        if (arc == null || arc.prev == null || arc.next == null) {
            return false;
        }
        return pointsMatch(site, arc.prev.site) ||
               pointsMatch(site, arc.site) ||
               pointsMatch(site, arc.next.site);
    }

    /**
     * Check if two points match within epsilon tolerance.
     */
    private static boolean pointsMatch(Point a, Point b) {
        final double eps = 1e-6;
        return Math.abs(a.x() - b.x()) < eps && Math.abs(a.y() - b.y()) < eps;
    }
}
