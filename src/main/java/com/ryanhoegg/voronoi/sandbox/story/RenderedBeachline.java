package com.ryanhoegg.voronoi.sandbox.story;

import com.ryanhoegg.voronoi.core.FortuneContext;
import com.ryanhoegg.voronoi.core.geometry.Bounds;
import com.ryanhoegg.voronoi.core.geometry.Geometry2D;
import com.ryanhoegg.voronoi.core.geometry.Point;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared beachline segmentation and measurement logic.
 *
 * <p><b>SINGLE SOURCE OF TRUTH</b> for arc segmentation and rendered-length measurement.
 * Both CircleEventSelector (selection-time) and CircleEventZoom (render-time) use this
 * helper to ensure perfect measurement alignment.</p>
 *
 * <p><b>Zero Processing dependencies</b> - works with pure geometry and explicit transforms.</p>
 */
public class RenderedBeachline {

    /**
     * Arc-instance-tagged segment: associates rendered points with specific BeachArc identity.
     * The arc field preserves object identity for triple-aware measurement.
     */
    public record ArcSegment(
        FortuneContext.BeachArc arc,  // Identity-preserving arc reference (use == not .equals())
        Point site,                    // Site coordinates (for reference)
        List<Point> points             // World-space polyline points
    ) {}

    /**
     * Transform parameters for world→screen conversion.
     * Matches rendering transform: (world - focus) * zoom + screenCenter
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

        public double screenToWorldX(double sx) {
            return (sx - screenCenter.x()) / zoom + focus.x();
        }
    }

    /**
     * Compute beachline segments using rendered X-sampling.
     *
     * <p><b>Critical invariant:</b> Samples world X positions uniformly across the visible
     * screen region (~2px per sample), finds the "best" (lowest/highest-Y) arc at each X,
     * and groups consecutive samples with the same arc identity into segments.</p>
     *
     * <p>This matches the exact segmentation used by BaseVisualization rendering, ensuring
     * measurements match what the user sees on screen.</p>
     *
     * @param fortune The FortuneContext (already advanced to the desired sweepY)
     * @param sweepLineY The sweep line Y position
     * @param bounds World bounds for clipping
     * @param transform Camera transform (zoom, focus, screen center)
     * @return List of arc-instance-tagged segments
     */
    public static List<ArcSegment> computeSegments(
        FortuneContext fortune,
        double sweepLineY,
        Bounds bounds,
        Transform transform
    ) {
        List<ArcSegment> segments = new ArrayList<>();

        FortuneContext.BeachArc head = fortune.beachLine();
        if (head == null) {
            return segments;
        }

        double directrix = sweepLineY;

        // Compute world X range from screen bounds (screen X: 0 to bounds.maxX())
        double zoom = transform.zoom;
        double worldLeft = transform.screenToWorldX(0);
        double worldRight = transform.screenToWorldX(bounds.maxX());
        double worldStep = 2.0 / zoom; // ~2 screen pixels per sample

        FortuneContext.BeachArc currentArc = null;
        List<Point> currentPoints = null;

        // Sample across screen width to find which arc is "on top" (highest Y) at each X
        for (double x = worldLeft; x < worldRight; x += worldStep) {
            // Find lowest position (highest y) arc at this x
            FortuneContext.BeachArc best = null;
            double bestY = Double.NEGATIVE_INFINITY;

            for (FortuneContext.BeachArc arc = head; arc != null; arc = arc.next) {
                double y = Geometry2D.parabolaY(arc.site, x, directrix);
                if (Double.isNaN(y)) {
                    continue;
                }

                if (y > bestY) {
                    bestY = y;
                    best = arc;
                }
            }

            if (best == null) {
                // No arcs here - close current segment
                if (currentArc != null && currentPoints != null) {
                    segments.add(new ArcSegment(currentArc, currentArc.site, currentPoints));
                    currentArc = null;
                    currentPoints = null;
                }
                continue;
            }

            // Check if arc changed (use identity comparison)
            if (best != currentArc) {
                // Arc changed - close previous segment, start new one
                if (currentArc != null && currentPoints != null) {
                    segments.add(new ArcSegment(currentArc, currentArc.site, currentPoints));
                }
                currentArc = best;
                currentPoints = new ArrayList<>();
            }

            currentPoints.add(new Point(x, bestY));
        }

        // Flush final segment
        if (currentArc != null && currentPoints != null) {
            segments.add(new ArcSegment(currentArc, currentArc.site, currentPoints));
        }

        return segments;
    }

    /**
     * Measure rendered length of a specific arc instance in SCREEN PIXELS.
     *
     * <p><b>Arc-instance aware:</b> Uses identity matching (==) to find segments belonging
     * to the specific BeachArc node, not just any arc with matching site coordinates.</p>
     *
     * <p>This is critical for measuring the staged triple (prev/doomed/next) where the
     * same site may appear in multiple disjoint arcs on the beachline.</p>
     *
     * @param segments Pre-computed segments from computeSegments()
     * @param targetArc The specific BeachArc instance to measure
     * @param transform Camera transform for world→screen conversion
     * @return Total rendered length in screen pixels, or Double.NaN if arc not found
     */
    public static double measureArcInstancePixels(
        List<ArcSegment> segments,
        FortuneContext.BeachArc targetArc,
        Transform transform
    ) {
        double totalLen = 0.0;
        boolean found = false;

        for (ArcSegment seg : segments) {
            // CRITICAL: Use identity match (==), not .equals()
            // We need to measure the specific arc instance, not just matching site coords
            if (seg.arc != targetArc) {
                continue;
            }
            found = true;

            List<Point> points = seg.points;
            if (points.size() < 2) {
                continue;
            }

            // Sum polyline length in screen space
            for (int i = 1; i < points.size(); i++) {
                Point p0 = points.get(i - 1);
                Point p1 = points.get(i);

                double sx0 = transform.worldToScreenX(p0.x());
                double sy0 = transform.worldToScreenY(p0.y());
                double sx1 = transform.worldToScreenX(p1.x());
                double sy1 = transform.worldToScreenY(p1.y());

                double dx = sx1 - sx0;
                double dy = sy1 - sy0;
                totalLen += Math.sqrt(dx * dx + dy * dy);
            }
        }

        return found ? totalLen : Double.NaN;
    }
}
