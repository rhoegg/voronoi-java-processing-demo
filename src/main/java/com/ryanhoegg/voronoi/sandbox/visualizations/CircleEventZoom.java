package com.ryanhoegg.voronoi.sandbox.visualizations;

import com.ryanhoegg.voronoi.core.ChosenCircleEvent;
import com.ryanhoegg.voronoi.core.FortuneContext;
import com.ryanhoegg.voronoi.core.geometry.Bounds;
import com.ryanhoegg.voronoi.core.geometry.Geometry2D;
import com.ryanhoegg.voronoi.core.geometry.Point;
import com.ryanhoegg.voronoi.sandbox.Visualization;
import org.jetbrains.annotations.NotNull;
import processing.core.PApplet;
import processing.core.PVector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public class CircleEventZoom extends BaseVisualization implements Visualization {
    /**
     * Measurement of doomed arc width at a specific sweep Y.
     * Uses X-extent (breakpoint distance) metric: widthPx = abs(xR - xL) * zoom
     */
    private static class DoomedMeasurement {
        final boolean tripleExists;
        final FortuneContext.BeachArc doomedArc; // null if !tripleExists
        final double xLeft;
        final double xRight;
        final double widthPx;

        // Debug info
        final Point prevSite;
        final Point doomedSite;
        final Point nextSite;

        private DoomedMeasurement(boolean tripleExists, FortuneContext.BeachArc doomedArc,
                                  double xLeft, double xRight, double widthPx,
                                  Point prevSite, Point doomedSite, Point nextSite) {
            this.tripleExists = tripleExists;
            this.doomedArc = doomedArc;
            this.xLeft = xLeft;
            this.xRight = xRight;
            this.widthPx = widthPx;
            this.prevSite = prevSite;
            this.doomedSite = doomedSite;
            this.nextSite = nextSite;
        }

        static DoomedMeasurement notFound() {
            return new DoomedMeasurement(false, null, Double.NaN, Double.NaN, Double.NaN,
                    null, null, null);
        }

        static DoomedMeasurement of(FortuneContext.BeachArc doomedArc, double xLeft, double xRight,
                                     double widthPx) {
            return new DoomedMeasurement(true, doomedArc, xLeft, xRight, widthPx,
                    doomedArc.prev.site, doomedArc.site, doomedArc.next.site);
        }
    }

    private final FortuneContext fortunePlan;
    private final FortuneContext fortuneShow;
    private final PVector firstSite;
    private final List<Point> originalSites; // Original Point list for FortuneContext snapshots (preserves precision)

    private enum Scene {
        INTRO_ZOOM,
        APPROACH_FIRST_SITE,
        SETTLE_BELOW_FIRST_SITE,
        EQUAL_DISTANCE_REVEAL,
        WAKE_CIRCLE_EVENT_SITES,
        APPROACH_CIRCLE_EVENT, // Highlight disappearing arc and ease sweep to preview Y
    }

    private Scene scene = Scene.INTRO_ZOOM;
    private float sceneT = 0f;

    private float sweepY = 0f;

    // scene transition and sweep settle
    private float sweepStartY;
    private float sweepTargetY;
    private float sweepSettleT = 0f;
    private boolean sweepSettling = false;
    private final float sweepSettleDuration = 2.6f;

    // witness
    private PVector witness = new PVector(0f, 0f);
    private float witnessA;
    private float witnessEndpointX1, witnessEndpointX2;
    private static final float WITNESS_FADE_TIME = 0.4f;
    private static final float WITNESS_PERIOD = 10f;
    private static final float WITNESS_SWING = 47f;

    // scene target parameters (geometry-driven)
    private static final double EPS = 0.001;
    private static final double EPS_Y = 0.001; // Safety margin to keep yCap strictly BEFORE invalidation boundary
    private static final double MIN_ALIVE_PX = 3.0; // Minimum rendered length for "story-usable triple alive" (all 3 arcs)
    private static final double MIN_WAKE_LEN_PX = 45.0; // All 3 arcs must be at least this visible

    // NEW: X-extent (breakpoint distance) metric for doomed arc detection
    private static final double TARGET_WIDTH_PX = 5.0; // Target doomed arc width in screen pixels
    private static final double WIDTH_TOL_PX = 1.0; // Tolerance for width target (±1px)
    private static final double SEARCH_FINE_STEP_Y = 0.25; // Fine search step in world units
    private static final double SEARCH_COARSE_STEP_Y = 2.0; // Coarse search step in world units
    private static final int MAX_ITERS_BISECT = 25; // Max bisection iterations

    // beach line
    private List<ArcPath> beachLine = new ArrayList<>();
    private float beachLineA = 0f;
    private float wakeTargetY = Float.NaN;
    private float approachTargetY = Float.NaN;
    private Double tripleAliveStartY = null; // Cached Y where triple first comes alive

    // NEW: Cached doomed arc identification and approach Y (computed once at init)
    private FortuneContext.BeachArc cachedDoomedArcAtApproach = null;
    private double computedApproachY = Double.NaN;

    // circle event
    private final ChosenCircleEvent chosenEvent; // The event selected during cluster generation
    FortuneContext.CircleEvent circleEvent;
    private PVector circleSite2, circleSite3;

    // camera / zoom
    private final PVector worldCenter;
    private final float targetZoom = 3.0f;
    private final float zoomDuration = 1.8f;

    private float zoomT = 0.0f;    // zoom progress from 0 to 1
    private boolean zoomDone = false;

    private boolean fadingOut = false;
    private float fadeT = 0f;

    public CircleEventZoom(PApplet app, List<PVector> clusterSites, ChosenCircleEvent chosenEvent, Theme theme) {
        this(app, clusterSites, null, chosenEvent, theme);
    }

    /**
     * Constructor with explicit originalSites for precision preservation.
     * When originalSites is provided, it's used for FortuneContext snapshots to ensure
     * site coordinates match exactly with circleEvent.sites() (avoiding float precision loss).
     */
    public CircleEventZoom(PApplet app, List<PVector> clusterSites, List<Point> originalSites,
                           ChosenCircleEvent chosenEvent, Theme theme) {
        super(app, clusterSites, theme);
        this.chosenEvent = chosenEvent;
        this.originalSites = (originalSites != null)
                ? originalSites
                : sites.stream().map(v -> new Point(v.x, v.y)).toList();
        fortunePlan = initFortune();
        fortuneShow = initFortune();
        worldCenter = computeCenter(clusterSites);
        firstSite = clusterSites.stream().min(Comparator.comparingDouble(s -> s.y)).orElseThrow();
        this.sweepY = firstSite.y - 80f;
        this.scene = Scene.INTRO_ZOOM;
        this.sceneT = 0f;
    }

    private FortuneContext initFortune() {
        Bounds bounds = new Bounds(0, 0, app.width, app.height);
        return new FortuneContext(originalSites, bounds);
    }

    private PVector computeCenter(List<PVector> sites) {
        float sx = 0, sy = 0;
        for (PVector site : sites) {
            sx += site.x;
            sy += site.y;
        }
        return new PVector(sx / sites.size(), sy / sites.size());
    }

    // ==================== NEW: X-EXTENT (BREAKPOINT DISTANCE) MEASUREMENT ====================

    /**
     * Advance a temporary FortuneContext snapshot to target sweepY.
     * Creates a fresh context and steps through events STRICTLY BEFORE targetY.
     *
     * CRITICAL: Uses < (not <=) to ensure we stop BEFORE processing any event at targetY.
     * This gives us the beachline state just before the event.
     *
     * IMPORTANT: Uses originalSites (converted from PVector once at construction)
     * to ensure consistent precision with circleEvent.sites().
     */
    private FortuneContext advanceFortuneToY(double targetY) {
        Bounds bounds = new Bounds(0, 0, app.width, app.height);
        FortuneContext snapshot = new FortuneContext(originalSites, bounds);

        while (snapshot.nextEventY() < targetY && snapshot.step()) {
            // Step through events strictly before targetY
        }

        return snapshot;
    }

    /**
     * Measure doomed arc width at a specific sweepY using X-extent (breakpoint distance).
     * This metric converges rapidly to ~0px as sweep approaches circle event.
     *
     * CRITICAL: Uses parabolaIntersectionXNearCircleEvent to ensure breakpoints
     * converge toward the circle center (not diverge to outer intersections).
     *
     * @param sweepY The sweep line Y position to measure at
     * @param eventSites The three sites forming the circle event (for triple matching)
     * @return Measurement containing tripleExists, doomedArc instance, and widthPx
     */
    private DoomedMeasurement measureDoomedWidthPxAt(double sweepY, FortuneContext.CircleEvent.Sites eventSites) {
        // Create temporary snapshot at this Y
        FortuneContext snapshot = advanceFortuneToY(sweepY);

        // Find the doomed arc instance by matching the triple in beachline
        // Use the argmin strategy: the doomed arc is the one with smallest X-extent
        FortuneContext.BeachArc doomedArc = findDoomedArcInBeachline(snapshot, eventSites, sweepY);

        if (doomedArc == null || doomedArc.prev == null || doomedArc.next == null) {
            return DoomedMeasurement.notFound();
        }

        // Compute circle center for this event (needed for correct root selection)
        Point circleCenter = Geometry2D.circumcenter(eventSites.a(), eventSites.b(), eventSites.c());
        if (circleCenter == null) {
            return DoomedMeasurement.notFound(); // Degenerate circle
        }
        double circleCenterX = circleCenter.x();

        // CRITICAL: Use circle-event-aware intersection method to choose the root
        // CLOSEST to circle center X (not the outer "boundary" root)
        double xLeft = Geometry2D.parabolaIntersectionXNearCircleEvent(
                doomedArc.prev.site, doomedArc.site, sweepY, circleCenterX);
        double xRight = Geometry2D.parabolaIntersectionXNearCircleEvent(
                doomedArc.site, doomedArc.next.site, sweepY, circleCenterX);

        // Guard against non-finite values
        if (!Double.isFinite(xLeft) || !Double.isFinite(xRight)) {
            return DoomedMeasurement.notFound();
        }

        // Enforce left < right
        double left = Math.min(xLeft, xRight);
        double right = Math.max(xLeft, xRight);

        // Compute width in screen pixels
        double widthPx = (right - left) * currentZoom();

        return DoomedMeasurement.of(doomedArc, left, right, widthPx);
    }

    /**
     * Find the doomed arc in a beachline by EXACT TRIPLE ORDERING.
     *
     * CRITICAL FIX: Sites.matches() is order-independent, but the circle event
     * specifies EXACT roles: prev.site, doomed.site, next.site in a SPECIFIC order.
     * We must match the EXACT triple structure, not just the three sites.
     *
     * A circle event is defined by (prev, doomed, next) where the doomed arc
     * has prev on its left and next on its right in the beachline.
     */
    private FortuneContext.BeachArc findDoomedArcInBeachline(FortuneContext ctx,
                                                              FortuneContext.CircleEvent.Sites targetSites,
                                                              double sweepY) {
        // Extract the triple from Sites record
        // CircleEvent constructor stores: new Sites(arc.prev.site, arc.site, arc.next.site)
        // So Sites.a = prev, Sites.b = doomed, Sites.c = next
        Point doomedSite = circleEvent.disappearingArc.site;
        Point prevSite = targetSites.a();
        Point nextSite = targetSites.c();

        List<FortuneContext.BeachArc> matches = new ArrayList<>();

        for (FortuneContext.BeachArc arc = ctx.beachLine(); arc != null; arc = arc.next) {
            if (arc.prev == null || arc.next == null) {
                continue;
            }

            // EXACT match: prev.site, arc.site, next.site must match in THAT ORDER
            if (pointsEqual(arc.prev.site, prevSite) &&
                pointsEqual(arc.site, doomedSite) &&
                pointsEqual(arc.next.site, nextSite)) {
                matches.add(arc);
            }
        }

        if (matches.isEmpty()) {
            System.out.printf("[CircleEventZoom]     No arc matching EXACT triple ordering found%n");
            System.out.printf("[CircleEventZoom]     Looking for: prev=(%.1f,%.1f) doomed=(%.1f,%.1f) next=(%.1f,%.1f)%n",
                    prevSite.x(), prevSite.y(), doomedSite.x(), doomedSite.y(), nextSite.x(), nextSite.y());
            System.out.printf("[CircleEventZoom]     All triples in beachline:%n");
            int idx = 0;
            for (FortuneContext.BeachArc arc = ctx.beachLine(); arc != null; arc = arc.next) {
                if (arc.prev != null && arc.next != null) {
                    boolean exactMatch = pointsEqual(arc.prev.site, prevSite) &&
                                       pointsEqual(arc.site, doomedSite) &&
                                       pointsEqual(arc.next.site, nextSite);
                    System.out.printf("[CircleEventZoom]       [%d] prev=(%.1f,%.1f) arc=(%.1f,%.1f) next=(%.1f,%.1f) %s%n",
                            idx++, arc.prev.site.x(), arc.prev.site.y(),
                            arc.site.x(), arc.site.y(),
                            arc.next.site.x(), arc.next.site.y(),
                            exactMatch ? "EXACT_MATCH" : "");
                }
            }
            return null; // Triple not in beachline at this Y
        }

        if (matches.size() == 1) {
            // Perfect! Found exactly one arc with exact triple ordering
            return matches.get(0);
        }

        if (matches.size() > 1) {
            // Multiple arcs with same site - use argmin X-extent (NOW with corrected breakpoints!)
            // The arc that's actually shrinking will have the smallest width
            Point circleCenter = Geometry2D.circumcenter(targetSites.a(), targetSites.b(), targetSites.c());
            if (circleCenter == null) {
                return matches.get(0); // Degenerate, just return first
            }
            double circleCenterX = circleCenter.x();

            FortuneContext.BeachArc best = null;
            double minWidth = Double.POSITIVE_INFINITY;
            for (int i = 0; i < matches.size(); i++) {
                FortuneContext.BeachArc arc = matches.get(i);
                if (arc.prev == null || arc.next == null) continue;

                // Use CORRECTED breakpoint calculation (near circle center)
                double xL = Geometry2D.parabolaIntersectionXNearCircleEvent(
                        arc.prev.site, arc.site, sweepY, circleCenterX);
                double xR = Geometry2D.parabolaIntersectionXNearCircleEvent(
                        arc.site, arc.next.site, sweepY, circleCenterX);

                if (Double.isFinite(xL) && Double.isFinite(xR)) {
                    double w = Math.abs(xR - xL);
                    if (w < minWidth) {
                        minWidth = w;
                        best = arc;
                    }
                }
            }
            return best != null ? best : matches.get(0);
        }

        return matches.get(0);
    }

    /**
     * Compute approach Y where doomed arc width is approximately TARGET_WIDTH_PX (5px ± 1px).
     * Uses bisection search to find the Y position where the arc is tiny but still visible.
     *
     * @return The computed approach Y, or NaN if not found
     */
    private double computeApproachYForEvent() {
        if (circleEvent == null) {
            return Double.NaN;
        }

        FortuneContext.CircleEvent.Sites eventSites = circleEvent.sites();
        double yEvent = circleEvent.y();

        // Compute search range
        double y3 = Math.max(eventSites.a().y(), Math.max(eventSites.b().y(), eventSites.c().y()));
        double yStart = y3 + 2.0; // Guard: start 2 world units after highest site
        double yEnd = yEvent - EPS_Y;

        String evId = String.format("y=%.2f", yEvent);
        System.out.printf("[CircleEventZoom] Computing approachY for event %s%n", evId);
        System.out.printf("[CircleEventZoom]   Event sites: a=(%.1f,%.1f) b=(%.1f,%.1f) c=(%.1f,%.1f)%n",
                eventSites.a().x(), eventSites.a().y(),
                eventSites.b().x(), eventSites.b().y(),
                eventSites.c().x(), eventSites.c().y());
        System.out.printf("[CircleEventZoom]   y3=%.2f, yStart=%.2f, yEnd=%.2f%n", y3, yStart, yEnd);

        // CRITICAL CHECK: At yEvent - 0.001, which arc is actually shrinking?
        double yJustBefore = yEvent - 0.001;
        System.out.printf("[CircleEventZoom]   === CRITICAL: Beachline at y=%.3f (%.3f before event) ===%n",
                yJustBefore, yEvent - yJustBefore);
        FortuneContext snapshotJustBefore = advanceFortuneToY(yJustBefore);
        int arcIdx = 0;
        FortuneContext.BeachArc matchedArc = null;
        double minWidth = Double.POSITIVE_INFINITY;
        for (FortuneContext.BeachArc arc = snapshotJustBefore.beachLine(); arc != null; arc = arc.next) {
            if (arc.prev != null && arc.next != null) {
                double xL = Geometry2D.parabolaIntersectionX(arc.prev.site, arc.site, yJustBefore);
                double xR = Geometry2D.parabolaIntersectionX(arc.site, arc.next.site, yJustBefore);
                double w = Math.abs(xR - xL);
                FortuneContext.CircleEvent.Sites arcSites = new FortuneContext.CircleEvent.Sites(
                        arc.prev.site, arc.site, arc.next.site);
                boolean isMatch = arcSites.matches(eventSites);
                if (isMatch) {
                    matchedArc = arc;
                }
                if (w < minWidth) {
                    minWidth = w;
                }
                System.out.printf("[CircleEventZoom]     [%d] prev=(%.1f,%.1f) arc=(%.1f,%.1f) next=(%.1f,%.1f) width=%.2f%s%s%n",
                        arcIdx++, arc.prev.site.x(), arc.prev.site.y(),
                        arc.site.x(), arc.site.y(),
                        arc.next.site.x(), arc.next.site.y(), w,
                        isMatch ? " <-- TARGET_TRIPLE" : "",
                        w == minWidth ? " <-- SMALLEST" : "");
            }
        }
        System.out.printf("[CircleEventZoom]   Smallest width just before event: %.2f%n", minWidth);
        if (matchedArc != null) {
            System.out.printf("[CircleEventZoom]%n");
            System.out.printf("[CircleEventZoom]   === STEP 1: VERIFY INTERSECTION CONTRACT ===%n");
            System.out.printf("[CircleEventZoom]   Computing xLeft: parabolas for prev=(%.2f,%.2f) and arc=(%.2f,%.2f) at directrix=%.3f%n",
                    matchedArc.prev.site.x(), matchedArc.prev.site.y(),
                    matchedArc.site.x(), matchedArc.site.y(),
                    yJustBefore);

            Geometry2D.IntersectionDiagnostic diagLeft = Geometry2D.parabolaIntersectionXDiagnostic(
                    matchedArc.prev.site, matchedArc.site, yJustBefore);
            diagLeft.printDiagnostic("LEFT breakpoint diagnostic");
            double xL = diagLeft.chosenX;
            System.out.printf("[CircleEventZoom]     → xLeft = %.2f%n", xL);

            if (!diagLeft.contractValid) {
                System.out.printf("[CircleEventZoom]   *** ERROR: LEFT breakpoint does NOT satisfy intersection contract! ***%n");
                System.out.printf("[CircleEventZoom]   *** Contract error: %.9f (should be < 1e-6) ***%n", diagLeft.contractError);
            }

            System.out.printf("[CircleEventZoom]%n");
            System.out.printf("[CircleEventZoom]   Computing xRight: parabolas for arc=(%.2f,%.2f) and next=(%.2f,%.2f) at directrix=%.3f%n",
                    matchedArc.site.x(), matchedArc.site.y(),
                    matchedArc.next.site.x(), matchedArc.next.site.y(),
                    yJustBefore);

            Geometry2D.IntersectionDiagnostic diagRight = Geometry2D.parabolaIntersectionXDiagnostic(
                    matchedArc.site, matchedArc.next.site, yJustBefore);
            diagRight.printDiagnostic("RIGHT breakpoint diagnostic");
            double xR = diagRight.chosenX;
            System.out.printf("[CircleEventZoom]     → xRight = %.2f%n", xR);

            if (!diagRight.contractValid) {
                System.out.printf("[CircleEventZoom]   *** ERROR: RIGHT breakpoint does NOT satisfy intersection contract! ***%n");
                System.out.printf("[CircleEventZoom]   *** Contract error: %.9f (should be < 1e-6) ***%n", diagRight.contractError);
            }

            System.out.printf("[CircleEventZoom]%n");
            System.out.printf("[CircleEventZoom]   === STEP 2: ANALYZE BOTH ROOTS ===%n");
            double circleCenterX = circleEvent.center().x();
            System.out.printf("[CircleEventZoom]   Circle center X: %.6f%n", circleCenterX);

            // Analyze LEFT breakpoint roots
            if (Double.isFinite(diagLeft.root1) && Double.isFinite(diagLeft.root2)) {
                double distLeft1 = Math.abs(diagLeft.root1 - circleCenterX);
                double distLeft2 = Math.abs(diagLeft.root2 - circleCenterX);
                System.out.printf("[CircleEventZoom]   LEFT breakpoint:%n");
                System.out.printf("[CircleEventZoom]     Root1=%.6f, dist to center=%.6f%n", diagLeft.root1, distLeft1);
                System.out.printf("[CircleEventZoom]     Root2=%.6f, dist to center=%.6f%n", diagLeft.root2, distLeft2);
                System.out.printf("[CircleEventZoom]     Chosen X=%.6f, dist to center=%.6f%n", xL, Math.abs(xL - circleCenterX));

                if (distLeft1 < distLeft2) {
                    System.out.printf("[CircleEventZoom]     → Root1 is CLOSER to circle center%n");
                    if (Math.abs(diagLeft.root1 - xL) > 0.001) {
                        System.out.printf("[CircleEventZoom]     *** WARNING: Chosen X != Root1 (closer root) ***%n");
                    }
                } else {
                    System.out.printf("[CircleEventZoom]     → Root2 is CLOSER to circle center%n");
                    if (Math.abs(diagLeft.root2 - xL) > 0.001) {
                        System.out.printf("[CircleEventZoom]     *** WARNING: Chosen X != Root2 (closer root) ***%n");
                    }
                }
            }

            // Analyze RIGHT breakpoint roots
            if (Double.isFinite(diagRight.root1) && Double.isFinite(diagRight.root2)) {
                double distRight1 = Math.abs(diagRight.root1 - circleCenterX);
                double distRight2 = Math.abs(diagRight.root2 - circleCenterX);
                System.out.printf("[CircleEventZoom]   RIGHT breakpoint:%n");
                System.out.printf("[CircleEventZoom]     Root1=%.6f, dist to center=%.6f%n", diagRight.root1, distRight1);
                System.out.printf("[CircleEventZoom]     Root2=%.6f, dist to center=%.6f%n", diagRight.root2, distRight2);
                System.out.printf("[CircleEventZoom]     Chosen X=%.6f, dist to center=%.6f%n", xR, Math.abs(xR - circleCenterX));

                if (distRight1 < distRight2) {
                    System.out.printf("[CircleEventZoom]     → Root1 is CLOSER to circle center%n");
                    if (Math.abs(diagRight.root1 - xR) > 0.001) {
                        System.out.printf("[CircleEventZoom]     *** WARNING: Chosen X != Root1 (closer root) ***%n");
                    }
                } else {
                    System.out.printf("[CircleEventZoom]     → Root2 is CLOSER to circle center%n");
                    if (Math.abs(diagRight.root2 - xR) > 0.001) {
                        System.out.printf("[CircleEventZoom]     *** WARNING: Chosen X != Root2 (closer root) ***%n");
                    }
                }
            }

            double matchedWidth = Math.abs(xR - xL);
            System.out.printf("[CircleEventZoom]%n");
            System.out.printf("[CircleEventZoom]   === SUMMARY ===%n");
            System.out.printf("[CircleEventZoom]   Matched triple width: %.2f%n", matchedWidth);
            System.out.printf("[CircleEventZoom]   Breakpoints: xL=%.2f xR=%.2f (circle center x=%.2f)%n",
                    xL, xR, circleCenterX);
            System.out.printf("[CircleEventZoom]   Distance from xL to center: %.6f%n", Math.abs(xL - circleCenterX));
            System.out.printf("[CircleEventZoom]   Distance from xR to center: %.6f%n", Math.abs(xR - circleCenterX));
            System.out.printf("[CircleEventZoom]   Circle: center=(%.2f,%.2f) radius=%.2f%n",
                    circleEvent.center().x(), circleEvent.center().y(), circleEvent.radius());

            // Manual verification: compute distance from each site to circle center
            double d1 = Math.hypot(matchedArc.prev.site.x() - circleEvent.center().x(),
                    matchedArc.prev.site.y() - circleEvent.center().y());
            double d2 = Math.hypot(matchedArc.site.x() - circleEvent.center().x(),
                    matchedArc.site.y() - circleEvent.center().y());
            double d3 = Math.hypot(matchedArc.next.site.x() - circleEvent.center().x(),
                    matchedArc.next.site.y() - circleEvent.center().y());
            System.out.printf("[CircleEventZoom]   Distances from sites to circle center: %.2f, %.2f, %.2f (radius=%.2f)%n",
                    d1, d2, d3, circleEvent.radius());

            if (matchedWidth > 100.0) {
                System.out.printf("[CircleEventZoom]   ERROR: Matched triple has width=%.2f px, way too large for Δ=0.001!%n", matchedWidth);
            }

            // STEP 3: VALIDATE CONVERGENCE BEHAVIOR
            System.out.printf("[CircleEventZoom]%n");
            System.out.printf("[CircleEventZoom]   === STEP 3: VALIDATE CONVERGENCE BEHAVIOR ===%n");
            System.out.printf("[CircleEventZoom]   Testing OLD method (parabolaIntersectionX):%n");
            double[] convergenceDeltas = {1.0, 0.1, 0.01, 0.001};
            for (double delta : convergenceDeltas) {
                double testY = yEvent - delta;
                if (testY < eventSites.a().y() || testY < eventSites.b().y() || testY < eventSites.c().y()) {
                    continue; // Skip if sweep is above any site
                }

                FortuneContext testCtx = advanceFortuneToY(testY);
                FortuneContext.BeachArc testArc = findDoomedArcInBeachline(testCtx, eventSites, testY);

                if (testArc != null && testArc.prev != null && testArc.next != null) {
                    double testXL_old = Geometry2D.parabolaIntersectionX(testArc.prev.site, testArc.site, testY);
                    double testXR_old = Geometry2D.parabolaIntersectionX(testArc.site, testArc.next.site, testY);

                    if (Double.isFinite(testXL_old) && Double.isFinite(testXR_old)) {
                        double distL_old = Math.abs(testXL_old - circleCenterX);
                        double distR_old = Math.abs(testXR_old - circleCenterX);
                        System.out.printf("[CircleEventZoom]     yEvent−%.3f: xL=%.6f (dist=%.6f), xR=%.6f (dist=%.6f)%n",
                                delta, testXL_old, distL_old, testXR_old, distR_old);
                    } else {
                        System.out.printf("[CircleEventZoom]     yEvent−%.3f: breakpoints NOT finite%n", delta);
                    }
                } else {
                    System.out.printf("[CircleEventZoom]     yEvent−%.3f: triple not found%n", delta);
                }
            }
            System.out.printf("[CircleEventZoom]   Expected: Both distances should → 0 as delta → 0 (FAILS with old method)%n");

            System.out.printf("[CircleEventZoom]%n");
            System.out.printf("[CircleEventZoom]   Testing NEW method (parabolaIntersectionXNearCircleEvent):%n");
            for (double delta : convergenceDeltas) {
                double testY = yEvent - delta;
                if (testY < eventSites.a().y() || testY < eventSites.b().y() || testY < eventSites.c().y()) {
                    continue;
                }

                FortuneContext testCtx = advanceFortuneToY(testY);
                FortuneContext.BeachArc testArc = findDoomedArcInBeachline(testCtx, eventSites, testY);

                if (testArc != null && testArc.prev != null && testArc.next != null) {
                    double testXL_new = Geometry2D.parabolaIntersectionXNearCircleEvent(
                            testArc.prev.site, testArc.site, testY, circleCenterX);
                    double testXR_new = Geometry2D.parabolaIntersectionXNearCircleEvent(
                            testArc.site, testArc.next.site, testY, circleCenterX);

                    if (Double.isFinite(testXL_new) && Double.isFinite(testXR_new)) {
                        double distL_new = Math.abs(testXL_new - circleCenterX);
                        double distR_new = Math.abs(testXR_new - circleCenterX);
                        double width_new = Math.abs(testXR_new - testXL_new);
                        System.out.printf("[CircleEventZoom]     yEvent−%.3f: xL=%.6f (dist=%.6f), xR=%.6f (dist=%.6f), width=%.6f%n",
                                delta, testXL_new, distL_new, testXR_new, distR_new, width_new);
                    } else {
                        System.out.printf("[CircleEventZoom]     yEvent−%.3f: breakpoints NOT finite%n", delta);
                    }
                } else {
                    System.out.printf("[CircleEventZoom]     yEvent−%.3f: triple not found%n", delta);
                }
            }
            System.out.printf("[CircleEventZoom]   Expected: Both distances → 0 as delta → 0 (SHOULD WORK with new method)%n");

            // STEP 4: CONFIRM DIRECTRIX / COORDINATE CONVENTIONS
            System.out.printf("[CircleEventZoom]%n");
            System.out.printf("[CircleEventZoom]   === STEP 4: CONFIRM DIRECTRIX CONVENTIONS ===%n");
            System.out.printf("[CircleEventZoom]   Testing parabola definition at xLeft%n");
            Point testSite = matchedArc.prev.site;
            double testX = xL;
            double testY_parabola = Geometry2D.parabolaY(testSite, testX, yJustBefore);
            double distToSite = Math.hypot(testX - testSite.x(), testY_parabola - testSite.y());
            double distToDirectrix = Math.abs(testY_parabola - yJustBefore);
            System.out.printf("[CircleEventZoom]     Site: (%.2f,%.2f)%n", testSite.x(), testSite.y());
            System.out.printf("[CircleEventZoom]     Point on parabola: (%.6f,%.6f)%n", testX, testY_parabola);
            System.out.printf("[CircleEventZoom]     Directrix Y: %.6f%n", yJustBefore);
            System.out.printf("[CircleEventZoom]     Distance to site: %.9f%n", distToSite);
            System.out.printf("[CircleEventZoom]     Distance to directrix: %.9f%n", distToDirectrix);
            System.out.printf("[CircleEventZoom]     Difference: %.9f (should be ~0 if parabola definition is correct)%n",
                    Math.abs(distToSite - distToDirectrix));

            if (Math.abs(distToSite - distToDirectrix) > 1e-6) {
                System.out.printf("[CircleEventZoom]   *** ERROR: Parabola definition VIOLATED! ***%n");
                System.out.printf("[CircleEventZoom]   *** This indicates a coordinate convention or sign error ***%n");
            }
        } else {
            System.out.printf("[CircleEventZoom]   ERROR: Target triple not found in beachline!%n");
        }
        System.out.printf("[CircleEventZoom]   === END CRITICAL CHECK ===%n");

        // VERIFY: What is the NEXT event after advancing to yEvent?
        System.out.printf("[CircleEventZoom]   === VERIFY: Next event after advancing to yEvent ===%n");
        FortuneContext verify = advanceFortuneToY(yEvent);
        System.out.printf("[CircleEventZoom]     Last processed: %s%n", verify.lastEvent());
        if (verify.lastEvent() != null) {
            System.out.printf("[CircleEventZoom]     Last event Y: %.3f%n", verify.lastEvent().y());
        }
        System.out.printf("[CircleEventZoom]     Next event Y: %.3f%n", verify.nextEventY());
        System.out.printf("[CircleEventZoom]     Target event Y: %.3f%n", yEvent);

        // Step one more time to process the next event (should be our target)
        if (verify.step() && verify.lastEvent() instanceof FortuneContext.CircleEvent ce) {
            System.out.printf("[CircleEventZoom]     Processed next event: circle at y=%.3f%n", ce.y());
            System.out.printf("[CircleEventZoom]     Sites: a=(%.1f,%.1f) b=(%.1f,%.1f) c=(%.1f,%.1f)%n",
                    ce.sites().a().x(), ce.sites().a().y(),
                    ce.sites().b().x(), ce.sites().b().y(),
                    ce.sites().c().x(), ce.sites().c().y());
            System.out.printf("[CircleEventZoom]     Disappearing arc: %s%n", ce.disappearingArc.site);
            boolean matchesTarget = ce.sites().matches(eventSites);
            System.out.printf("[CircleEventZoom]     Matches target triple? %s%n", matchesTarget);
        }
        System.out.printf("[CircleEventZoom]   === END VERIFY ===%n");

        // SCAN: Log ALL events between yStart and yEvent to find what might invalidate the triple
        System.out.printf("[CircleEventZoom]   === SCAN: All events from yStart to yEvent ===%n");
        FortuneContext scan = new FortuneContext(originalSites, new Bounds(0, 0, app.width, app.height));
        int eventCount = 0;
        while (scan.nextEventY() < yEnd + 10.0 && scan.step()) {
            FortuneContext.Event evt = scan.lastEvent();
            if (evt != null && evt.y() >= yStart && evt.y() <= yEvent + 0.01) {
                eventCount++;
                String type = evt instanceof FortuneContext.CircleEvent ? "CIRCLE" : "SITE";
                System.out.printf("[CircleEventZoom]     [%d] %s at y=%.3f%n", eventCount, type, evt.y());
                if (evt instanceof FortuneContext.CircleEvent ce) {
                    System.out.printf("[CircleEventZoom]         Sites: (%.1f,%.1f) (%.1f,%.1f) (%.1f,%.1f)%n",
                            ce.sites().a().x(), ce.sites().a().y(),
                            ce.sites().b().x(), ce.sites().b().y(),
                            ce.sites().c().x(), ce.sites().c().y());
                    System.out.printf("[CircleEventZoom]         Disappearing: (%.1f,%.1f)%n",
                            ce.disappearingArc.site.x(), ce.disappearingArc.site.y());
                }
            }
        }
        System.out.printf("[CircleEventZoom]   Total events in range: %d%n", eventCount);
        System.out.printf("[CircleEventZoom]   === END SCAN ===%n");

        // Diagnostic: Sample at key positions to verify widthPx→0
        double[] diagnosticYs = {
                yStart,
                (yStart + yEnd) / 2.0,
                Math.max(yStart, yEvent - 5.0),
                Math.max(yStart, yEvent - 1.0),
                Math.max(yStart, yEvent - 0.2),
                Math.max(yStart, yEvent - 0.05)
        };

        System.out.printf("[CircleEventZoom]   === DIAGNOSTIC: widthPx samples ===%n");
        for (double yDiag : diagnosticYs) {
            if (yDiag >= yStart && yDiag < yEnd) {
                DoomedMeasurement m = measureDoomedWidthPxAt(yDiag, eventSites);
                if (m.tripleExists) {
                    System.out.printf("[CircleEventZoom]     y=%.2f: widthPx=%.2f%n", yDiag, m.widthPx);
                } else {
                    System.out.printf("[CircleEventZoom]     y=%.2f: triple not found%n", yDiag);
                }
            }
        }
        System.out.printf("[CircleEventZoom]   === END DIAGNOSTICS ===%n");

        // VALIDATION: Check that widthPx actually decreases toward the event
        System.out.printf("[CircleEventZoom]   === MONOTONE VALIDATION ===%n");
        double[] validationYs = {
                yEvent - 5.0,
                yEvent - 1.0,
                yEvent - 0.5,
                yEvent - 0.1,
                yEvent - 0.01
        };
        double prevWidth = Double.POSITIVE_INFINITY;
        boolean isMonotone = true;
        for (double yVal : validationYs) {
            if (yVal < yStart || yVal >= yEnd) continue;
            DoomedMeasurement m = measureDoomedWidthPxAt(yVal, eventSites);
            if (m.tripleExists) {
                System.out.printf("[CircleEventZoom]     y=%.2f (Δ=%.2f): widthPx=%.2f%s%n",
                        yVal, yEvent - yVal, m.widthPx,
                        m.widthPx > prevWidth ? " [INCREASING!]" : "");
                if (m.widthPx > prevWidth) {
                    isMonotone = false;
                }
                prevWidth = m.widthPx;
            }
        }
        if (!isMonotone) {
            System.out.printf("[CircleEventZoom]   WARNING: widthPx NOT monotone decreasing!%n");
        }
        System.out.printf("[CircleEventZoom]   === END VALIDATION ===%n");

        // Coarse scan to find crossing interval
        double yA = Double.NaN, yB = Double.NaN;
        double widthA = Double.NaN, widthB = Double.NaN;

        for (double y = yEnd; y >= yStart; y -= SEARCH_COARSE_STEP_Y) {
            DoomedMeasurement m = measureDoomedWidthPxAt(y, eventSites);

            if (!m.tripleExists) {
                continue; // Triple doesn't exist yet at this Y
            }

            if (m.widthPx <= TARGET_WIDTH_PX + WIDTH_TOL_PX && m.widthPx > 0) {
                // Found yB: inside or near target band
                yB = y;
                widthB = m.widthPx;

                // Look for yA (before yB with width > target)
                for (double y2 = yB - SEARCH_COARSE_STEP_Y; y2 >= yStart; y2 -= SEARCH_COARSE_STEP_Y) {
                    DoomedMeasurement m2 = measureDoomedWidthPxAt(y2, eventSites);
                    if (!m2.tripleExists) {
                        break; // Triple disappeared
                    }
                    if (m2.widthPx > TARGET_WIDTH_PX + WIDTH_TOL_PX) {
                        yA = y2;
                        widthA = m2.widthPx;
                        break;
                    }
                }
                break; // Found crossing
            }
        }

        // If found crossing interval, bisect to refine
        if (!Double.isNaN(yA) && !Double.isNaN(yB)) {
            System.out.printf("[CircleEventZoom]   Found crossing: yA=%.2f (%.2fpx), yB=%.2f (%.2fpx)%n",
                    yA, widthA, yB, widthB);

            // Bisect between yA and yB
            for (int iter = 0; iter < MAX_ITERS_BISECT; iter++) {
                double yMid = (yA + yB) / 2.0;
                DoomedMeasurement mMid = measureDoomedWidthPxAt(yMid, eventSites);

                if (!mMid.tripleExists) {
                    yB = yMid; // Move upper bound down
                    continue;
                }

                double diff = Math.abs(mMid.widthPx - TARGET_WIDTH_PX);
                if (diff <= WIDTH_TOL_PX) {
                    // Within tolerance!
                    System.out.printf("[CircleEventZoom]   Bisection converged: y=%.2f, widthPx=%.2f%n",
                            yMid, mMid.widthPx);
                    return yMid;
                }

                if (mMid.widthPx > TARGET_WIDTH_PX) {
                    yA = yMid; // Too wide, move forward
                } else {
                    yB = yMid; // Too narrow, move backward
                }
            }

            // Return best of yA or yB
            DoomedMeasurement mA = measureDoomedWidthPxAt(yA, eventSites);
            DoomedMeasurement mB = measureDoomedWidthPxAt(yB, eventSites);
            double diffA = Math.abs(mA.widthPx - TARGET_WIDTH_PX);
            double diffB = Math.abs(mB.widthPx - TARGET_WIDTH_PX);
            double chosenY = (diffA < diffB) ? yA : yB;
            double chosenWidth = (diffA < diffB) ? mA.widthPx : mB.widthPx;

            System.out.printf("[CircleEventZoom]   Bisection result: y=%.2f, widthPx=%.2f%n",
                    chosenY, chosenWidth);
            return chosenY;
        }

        // Fallback: fine scan for best achievable
        System.out.printf("[CircleEventZoom]   No crossing found, fallback to fine scan%n");
        double bestY = Double.NaN;
        double bestDiff = Double.POSITIVE_INFINITY;

        for (double y = yStart; y < yEnd; y += SEARCH_FINE_STEP_Y) {
            DoomedMeasurement m = measureDoomedWidthPxAt(y, eventSites);

            if (!m.tripleExists || m.widthPx <= 0) {
                continue;
            }

            double diff = Math.abs(m.widthPx - TARGET_WIDTH_PX);
            if (diff < bestDiff) {
                bestDiff = diff;
                bestY = y;
            }
        }

        if (!Double.isNaN(bestY)) {
            DoomedMeasurement mBest = measureDoomedWidthPxAt(bestY, eventSites);
            System.out.printf("[CircleEventZoom]   Fallback result: y=%.2f, widthPx=%.2f%n",
                    bestY, mBest.widthPx);
            return bestY;
        }

        System.out.printf("[CircleEventZoom]   FAILED to find suitable approachY%n");
        return Double.NaN;
    }

    // ==================== END NEW METHODS ====================

    @Override
    public void reset() {

    }

    @Override
    public void step() {
        switch(scene) {
            case INTRO_ZOOM:
                if (zoomDone) {
                    scene = Scene.APPROACH_FIRST_SITE;
                    sceneT = 0f;
                    sweepStartY = firstSite.y - 80f;
                    sweepTargetY = firstSite.y + 0.1f;
                    sweepSettleT = 0f;
                    sweepSettling = true;
                }
                break;
            case APPROACH_FIRST_SITE:
                scene = Scene.SETTLE_BELOW_FIRST_SITE;
                sceneT = 0f;
                sweepStartY = sweepY;
                sweepTargetY = firstSite.y + 42f;
                sweepSettleT = 0f;
                sweepSettling = true;
                witness.x = firstSite.x;
                witnessA = 0f;
                break;
            case SETTLE_BELOW_FIRST_SITE:
                scene = Scene.EQUAL_DISTANCE_REVEAL;
                sceneT = 0f;
                witnessA = 1f;
                float firstSiteOffset = (app.width / 2 - firstSite.x);
                float oscillateCenterX = firstSite.x + (0.05f * firstSiteOffset);
                witnessEndpointX1 = oscillateCenterX + ((firstSiteOffset > 0) ? -WITNESS_SWING : WITNESS_SWING);
                witnessEndpointX2 = oscillateCenterX + ((firstSiteOffset > 0) ? WITNESS_SWING : -WITNESS_SWING);
                break;
            case EQUAL_DISTANCE_REVEAL:
                fadingOut = true;
                fadeT = 0f;

                // Match the chosen event in fortunePlan (no reselection!)
                circleEvent = matchChosenEventInFortune();

                if (null == circleEvent) {
                    System.out.println("[CircleEventZoom] FATAL: Could not match chosen event in Fortune context!");
                    System.out.printf("  Expected: y=%.2f, sites=%s%n", chosenEvent.yEvent(), chosenEvent.sites());
                    System.out.println("[CircleEventZoom] This indicates the chosen event is not valid for this visualization.");
                    System.out.println("[CircleEventZoom] Please check the CircleEventSelector configuration and event selection logic.");
                    System.exit(1);
                }

                // Validation is now done in CircleEventSelector - no need to re-validate here
                System.out.printf("[CircleEventZoom] Using circle event: y=%.2f, sites=%s%n",
                        circleEvent.y(), circleEvent.sites());

                // Identify the other two sites involved in the circle event
                if (locationEquals(firstSite, circleEvent.sites().a())) {
                    circleSite2 = findSite(circleEvent.sites().b());
                    circleSite3 = findSite(circleEvent.sites().c());
                } else {
                    circleSite2 = findSite(circleEvent.sites().a());
                    if (locationEquals(firstSite, circleEvent.sites().b())) {
                        circleSite3 = findSite(circleEvent.sites().c());
                    } else {
                        circleSite3 = findSite(circleEvent.sites().b());
                    }
                }

                // NEW: Compute approachY using X-extent metric (breakpoint distance)
                // This replaces the old arc-length-based computation with a metric that
                // converges rapidly to ~0px as the sweep approaches the circle event
                System.out.printf("[CircleEventZoom] Computing approachY using X-extent metric...%n");
                computedApproachY = computeApproachYForEvent();

                if (Double.isNaN(computedApproachY)) {
                    System.out.println("[CircleEventZoom] FATAL: Could not compute suitable approachY!");
                    System.out.printf("  Event: y=%.2f, sites=%s%n", circleEvent.y(), circleEvent.sites());
                    System.exit(1);
                }

                System.out.printf("[CircleEventZoom] Computed approachY=%.2f (using widthPx metric)%n",
                        computedApproachY);

                // Transition to WAKE_CIRCLE_EVENT_SITES first (scene order change)
                // WAKE scene highlights the 3 sites and shows all three arcs in beach line
                scene = Scene.WAKE_CIRCLE_EVENT_SITES;
                sceneT = 0f;
                sweepStartY = sweepY;

                // Use pre-computed WAKE Y from selector (computed using rendered segmentation)
                wakeTargetY = (float) chosenEvent.wakeY();
                sweepTargetY = wakeTargetY;
                beachLineA = 1f; // Beach line should be fully visible immediately
                System.out.printf("[CircleEventZoom] Using pre-computed wakeY=%.2f (min arc %.1fpx)%n",
                    chosenEvent.wakeY(), chosenEvent.wakeMinArcLenPx());
                break;
            case WAKE_CIRCLE_EVENT_SITES:
                // After waking sites, transition to APPROACH_CIRCLE_EVENT
                // APPROACH scene animates sweep to show the doomed arc shrinking (almost gone)
                scene = Scene.APPROACH_CIRCLE_EVENT;
                sceneT = 0f;
                sweepStartY = sweepY;

                // Use NEW X-extent-based approach Y (computed at init using widthPx metric)
                approachTargetY = (float) computedApproachY;
                sweepTargetY = approachTargetY;

                // Measure final width at approach Y for verification
                DoomedMeasurement approachMeasurement = measureDoomedWidthPxAt(
                        computedApproachY, circleEvent.sites());
                System.out.printf("[CircleEventZoom] Using computed approachY=%.2f (widthPx=%.2f)%n",
                        computedApproachY, approachMeasurement.widthPx);

                // Enforce monotonicity: if we're already at or past the target, skip animation
                if (sweepStartY >= sweepTargetY) {
                    // Already at or past target - no animation needed
                    sweepSettleT = 1f;
                    sweepSettling = false;
                } else {
                    // Animate forward to almostGoneY (the dramatic final approach)
                    sweepSettleT = 0f;
                    sweepSettling = true;
                }
                break;
        }
    }

    @Override
    public void update(float dt) {
        super.update(dt); // Update pulseTime in BaseVisualization
        sceneT += dt;

        switch (scene) {
            case INTRO_ZOOM:
                if (!zoomDone) {
                    zoomT += dt / zoomDuration;
                    if (zoomT > 1f) {
                        zoomT = 1f;
                        zoomDone = true;
                    }
                }
                break;
            case APPROACH_FIRST_SITE:
                zoomT = 1f;
                if (sweepSettling) {
                    sweepSettleT += dt / sweepSettleDuration;
                    if (sweepSettleT >= 1f) {
                        sweepSettleT = 1f;
                        sweepSettling = false;
                    }
                    float u = easeOutCubic(sweepSettleT);
                    sweepY = PApplet.lerp(sweepStartY, sweepTargetY, u);
                }
                break;
            case SETTLE_BELOW_FIRST_SITE:
                if (sweepSettling) {
                    sweepSettleT += dt / sweepSettleDuration;
                    if (sweepSettleT >= 1f) {
                        sweepSettleT = 1f;
                        sweepSettling = false;
                    }
                    float u = smoothStep(sweepSettleT);
                    sweepY = PApplet.lerp(sweepStartY, sweepTargetY, u);
                    if (sweepSettleT > (1 - WITNESS_FADE_TIME) / sweepSettleDuration) {
                        float fadeStart = 1 - (WITNESS_FADE_TIME / sweepSettleDuration);
                        witnessA = PApplet.constrain((sweepSettleT - fadeStart) / (1f - fadeStart), 0f, 1f);
                        witness.y = parabolaY(firstSite, witness.x, sweepY);
                    }
                }
                break;
            case EQUAL_DISTANCE_REVEAL:
                if (sceneT > 3f) {
                    if (sceneT < WITNESS_PERIOD / 2 + 3f) {
                        witness.x = PApplet.lerp(firstSite.x, witnessEndpointX1, easeInOutCubic((sceneT - 3f) / (WITNESS_PERIOD / 2)));
                    } else {
                        float oscillateStart = 3f + WITNESS_PERIOD / 2;
                        float p = ((sceneT - oscillateStart) % WITNESS_PERIOD) / WITNESS_PERIOD;
                        float s = (p < 0.5f) ? (2 * p) : (2 - 2 * p);
                        witness.x = PApplet.lerp(witnessEndpointX1, witnessEndpointX2, easeInOutCubic(s));
                    }
                    witness.y = parabolaY(firstSite, witness.x, sweepY);
                }
                if (fadingOut) fadeT += dt / WITNESS_FADE_TIME;
                witnessA = PApplet.constrain(smoothStep(1f - fadeT), 0f, 1f);
                // Scene transition now handled in step() method
                break;
            case APPROACH_CIRCLE_EVENT:
                // Animate sweep line to previewSweepY
                final float approachDuration = 2.0f; // 2 seconds for sweep animation
                if (sweepSettling) {
                    sweepSettleT += dt / approachDuration;
                    if (sweepSettleT >= 1f) {
                        sweepSettleT = 1f;
                        sweepSettling = false;
                    }
                    float u = easeInOutCubic(sweepSettleT);
                    float newSweepY = PApplet.lerp(sweepStartY, sweepTargetY, u);

                    // Enforce monotonicity: sweep must only move forward (never backward)
                    sweepY = Math.max(sweepY, newSweepY);
                }

                // Update beachline as sweep progresses (DO NOT step fortune - just update display)
                while (true) {
                    Double nextY = fortuneShow.nextEventY();
                    if (null == nextY || sweepY < nextY) {
                        break;
                    }
                    fortuneShow.step();
                }
                beachLine = computeBeachLineSegments(fortuneShow, sweepY);
                break;
            case WAKE_CIRCLE_EVENT_SITES:
                if (sceneT < 1.8f) {
                    float newSweepY = PApplet.lerp(sweepStartY, sweepTargetY, easeInOutCubic(sceneT / 1.8f));
                    // Enforce monotonicity: sweep must only move forward (never backward)
                    sweepY = Math.max(sweepY, newSweepY);
                }
                beachLineA = 1f;
                // trigger Fortune events as we cross their y
                while (true) {
                    Double nextY = fortuneShow.nextEventY();
                    if (null == nextY) {
                        break;
                    }
                    if (sweepY >= nextY) {
                        fortuneShow.step();
                    } else {
                        break;
                    }
                }

                beachLine = computeBeachLineSegments(fortuneShow, sweepY);
                break;
        }
    }

    @Override
    public void draw() {
        drawBackground();
        app.pushMatrix();
        applyCamera();

        switch (scene) {
            case INTRO_ZOOM -> drawIntroZoomScene();
            case APPROACH_FIRST_SITE -> drawApproachFirstSiteScene();
            case SETTLE_BELOW_FIRST_SITE -> drawSettleBelowFirstSiteScene();
            case EQUAL_DISTANCE_REVEAL -> drawEqualDistanceReveal();
            case APPROACH_CIRCLE_EVENT -> drawApproachCircleEvent();
            case WAKE_CIRCLE_EVENT_SITES -> drawWakeCircleEventSites();
        }

        app.popMatrix();
    }

    private void drawIntroZoomScene() {
        drawClusterSites();
    }

    private void drawApproachFirstSiteScene() {
        drawClusterSites();
        drawSweepLine(sweepY);
        if (sweepY > firstSite.y) drawParabolaForSite(firstSite, sweepY, true, 2.5f);
        drawUnseenArea(sweepY);
    }

    private void drawSettleBelowFirstSiteScene() {
        drawClusterSites();
        drawSweepLine(sweepY);
        if (sweepY > firstSite.y) drawParabolaForSite(firstSite, sweepY, true, 2.5f);
        if (witnessA > 0f) {
            currentStyle().drawWitness(app, witness, witnessA);
        }
        drawUnseenArea(sweepY);
    }

    private void drawEqualDistanceReveal() {
        drawClusterSites();
        drawSweepLine(sweepY);
        drawParabolaForSite(firstSite, sweepY, true, 2.5f);
        currentStyle().drawWitness(app, witness, witnessA);
        drawWitnessDistanceHelpers();
        drawWitnessSegments();
        drawUnseenArea(sweepY);
    }

    private void drawApproachCircleEvent() {
        drawClusterSites();
        drawSweepLine(sweepY);
        // Background beach line rendered softly for context
        drawBeachLineWithGapFilling(beachLine, 0.35f);

        TripleArcs triple = currentTripleArcs();
        if (triple != null) {
            float progress = approachProgress();
            float highlightFactor = highlightReveal(progress);

            // APPROACH scene: only highlight the doomed arc when it becomes small
            // Do not highlight prev/next arcs - keep them at background opacity
            if (highlightFactor > 0f) {
                Set<FortuneContext.BeachArc> doomedOnly = Set.of(triple.doomed());
                float overlayAlpha = PApplet.lerp(0.0f, 1.0f, highlightFactor);
                drawBeachLineSubsetWithGapFilling(beachLine, overlayAlpha, doomedOnly, doomedOnly);
            }

            // Find the doomed arc directly from the rendered beachline
            // This uses the SAME BeachArc references that were rendered, ensuring correct match
            ArcPath doomedSegment = findDoomedArcPath(beachLine, circleEvent.sites());
            if (doomedSegment != null) {
                drawDoomedArcWhiteGlow(doomedSegment, highlightFactor);
            }
        }

        drawUnseenArea(sweepY);
    }

    private void drawWakeCircleEventSites() {
        drawClusterSites();
        drawSweepLine(sweepY);
        float alpha = PApplet.constrain(beachLineA, 0f, 1f);
        // WAKE scene: draw beachline normally, then highlight the three circle event arcs
        // No vivid white glow yet - just the normal theme highlighting
        drawBeachLineWithGapFilling(beachLine, alpha);

        TripleArcs triple = currentTripleArcs();
        if (triple != null) {
            Set<FortuneContext.BeachArc> tripleSet = Set.of(triple.prev(), triple.doomed(), triple.next());
            drawBeachLineSubsetWithGapFilling(beachLine, alpha, tripleSet, tripleSet);
        }
        drawUnseenArea(sweepY);
    }

    /**
     * Draw a white glow overlay on the doomed arc that intensifies near disappearance.
     */
    private void drawDoomedArcWhiteGlow(ArcPath doomedArc, float intensity) {
        List<PVector> points = doomedArc.path().getPoints();
        if (points.isEmpty()) return;

        float clamped = PApplet.constrain(intensity, 0f, 1f);
        if (clamped <= 0f) return;

        app.pushStyle();
        app.noFill();
        app.strokeCap(PApplet.ROUND);
        app.strokeJoin(PApplet.ROUND);

        float outerAlpha = 60f * clamped;
        float innerAlpha = 180f * clamped;

        float baseWeight = 8.0f / currentZoom(); // Compensate for zoom
        app.stroke(255, 255, 255, (int) outerAlpha);
        app.strokeWeight(baseWeight);
        app.beginShape();
        for (PVector p : points) {
            app.vertex(p.x, p.y);
        }
        app.endShape();

        // Inner core (narrower, brighter)
        float coreWeight = 4.0f / currentZoom();
        app.stroke(255, 255, 255, (int) innerAlpha);
        app.strokeWeight(coreWeight);
        app.beginShape();
        for (PVector p : points) {
            app.vertex(p.x, p.y);
        }
        app.endShape();

        app.popStyle();
    }

    private void drawClusterSites() {
        // Draw all sites using centralized drawing (theme handles pulsing)
        sites.forEach(s -> {
            boolean isFirstSite = s.equals(firstSite);
            boolean shouldHighlight = false;
            switch(scene) {
                case APPROACH_FIRST_SITE:
                case SETTLE_BELOW_FIRST_SITE:
                case EQUAL_DISTANCE_REVEAL:
                    shouldHighlight = isFirstSite;
                    break;
                case APPROACH_CIRCLE_EVENT:
                case WAKE_CIRCLE_EVENT_SITES:
                    shouldHighlight = circleEvent.sites().contains(new Point(s.x, s.y));
                    break;
            }
            drawSite(s, shouldHighlight);
        });
    }

    private void drawWitnessSegments() {
        float fade = (sceneT < 0.4f) ? sceneT / 0.4f : witnessA;
        currentStyle().drawWitnessSegments(app, witness, firstSite, sweepY, fade);
    }

    private void drawWitnessDistanceHelpers() {
        float fade = (sceneT < 2.4f) ? easeInOutCubic(sceneT / 2.4f) : witnessA;
        currentStyle().drawWitnessDistanceHelpers(app, witness, firstSite, sweepY, fade);
    }

    private void drawBeachLine() {
        float alpha = PApplet.constrain(beachLineA, 0f, 1f);
        drawBeachLineWithGapFilling(beachLine, alpha);
    }

    private void drawBeachLineWithGapFilling(List<ArcPath> segments, float alpha) {
        drawBeachLineWithGapFilling(segments, alpha, arcPath -> true, arcPath -> false);
    }

    private void drawBeachLineSubsetWithGapFilling(List<ArcPath> segments,
                                                   float alpha,
                                                   Set<FortuneContext.BeachArc> subset,
                                                   Set<FortuneContext.BeachArc> highlightedArcs) {
        Predicate<ArcPath> drawPredicate = arcPath -> subset.contains(arcPath.arc());
        Predicate<ArcPath> highlightPredicate = arcPath -> highlightedArcs.contains(arcPath.arc());
        drawBeachLineWithGapFilling(segments, alpha, drawPredicate, highlightPredicate);
    }

    /**
     * Draw beach line segments with gap filling to eliminate tiny visual discontinuities.
     * Callers may filter which arcs are rendered and which are highlighted.
     */
    private void drawBeachLineWithGapFilling(List<ArcPath> segments,
                                             float alpha,
                                             Predicate<ArcPath> drawPredicate,
                                             Predicate<ArcPath> highlightPredicate) {
        final float GAP_FILL_THRESHOLD_PX = 3.0f; // Maximum gap size to fill
        if (segments.isEmpty() || alpha <= 0f) {
            return;
        }

        for (int i = 0; i < segments.size(); i++) {
            ArcPath current = segments.get(i);
            if (!drawPredicate.test(current)) {
                continue;
            }

            PVector site = findSite(current.site());
            boolean highlighted = highlightPredicate.test(current);

            currentStyle().drawBeachArc(app, current.path(), site, highlighted, currentZoom(), alpha);

            // Fill gap to next segment if the next arc is also being drawn
            if (i < segments.size() - 1) {
                ArcPath next = segments.get(i + 1);
                if (!drawPredicate.test(next)) {
                    continue;
                }

                List<PVector> currentPoints = current.path().getPoints();
                List<PVector> nextPoints = next.path().getPoints();

                if (!currentPoints.isEmpty() && !nextPoints.isEmpty()) {
                    PVector lastPoint = currentPoints.get(currentPoints.size() - 1);
                    PVector firstPoint = nextPoints.get(0);

                    PVector lastScreen = worldToScreen(lastPoint);
                    PVector firstScreen = worldToScreen(firstPoint);
                    float gap = PApplet.dist(lastScreen.x, lastScreen.y, firstScreen.x, firstScreen.y);

                    if (gap > 0 && gap < GAP_FILL_THRESHOLD_PX) {
                        PVector midpoint = new PVector(
                                (lastPoint.x + firstPoint.x) * 0.5f,
                                (lastPoint.y + firstPoint.y) * 0.5f
                        );
                        PVector nextSite = findSite(next.site());
                        boolean nextHighlighted = highlightPredicate.test(next);

                        currentStyle().drawBeachLineGapFill(app, lastPoint, midpoint, site, highlighted, currentZoom());
                        currentStyle().drawBeachLineGapFill(app, midpoint, firstPoint, nextSite, nextHighlighted, currentZoom());
                    }
                }
            }
        }
    }

    @Override
    protected float currentZoom() {
        float easing = smoothStep(zoomT);
        return PApplet.lerp(1f, targetZoom, easing);
    }

    @Override
    protected PVector currentFocus() {
        PVector screenCenter = new PVector(app.width / 2f, app.height / 2f);
        return PVector.lerp(screenCenter, worldCenter, smoothStep(zoomT));
    }

    @NotNull
    private PVector findSite(Point location) {
        return sites.stream()
                .filter(s -> locationEquals(s, location))
                .findFirst()
                .orElseThrow();
    }

    /**
     * Check if two Points are equal within tolerance.
     */
    private boolean pointsEqual(Point a, Point b) {
        final double eps = 1e-6;
        return Math.abs(a.x() - b.x()) < eps && Math.abs(a.y() - b.y()) < eps;
    }

    private record TripleArcs(FortuneContext.BeachArc prev, FortuneContext.BeachArc doomed,
                              FortuneContext.BeachArc next) {}

    private TripleArcs currentTripleArcs() {
        FortuneContext.BeachArc doomed = findDoomedArcInBeachline(fortuneShow, circleEvent.sites(), sweepY);
        if (doomed == null || doomed.prev == null || doomed.next == null) {
            return null;
        }
        return new TripleArcs(doomed.prev, doomed, doomed.next);
    }

    private ArcPath findSegmentForArc(List<ArcPath> segments, FortuneContext.BeachArc target) {
        if (target == null) return null;
        for (ArcPath arcPath : segments) {
            if (arcPath.arc() == target) {
                return arcPath;
            }
        }
        return null;
    }

    /**
     * Find the doomed ArcPath in the rendered beachline.
     *
     * SIMPLIFIED: After intermediate events, the exact triple may no longer exist.
     * Just find the arc whose site matches the disappearing arc site.
     * This works because typically there's only one arc per site in the beachline
     * (or we take the first match which is good enough for highlighting).
     *
     * @param segments The rendered beachline segments (from fortuneShow context)
     * @param eventSites The circle event sites triple (not used in simplified version)
     * @return The ArcPath for the doomed arc, or null if not found
     */
    private ArcPath findDoomedArcPath(List<ArcPath> segments, FortuneContext.CircleEvent.Sites eventSites) {
        Point doomedSite = circleEvent.disappearingArc.site;

        // Simple approach: find first arc with matching site
        for (ArcPath arcPath : segments) {
            if (pointsEqual(arcPath.site(), doomedSite)) {
                return arcPath;
            }
        }
        return null; // Not found in rendered beachline
    }

    private float approachProgress() {
        if (!Float.isFinite(wakeTargetY) || !Float.isFinite(approachTargetY)) {
            return 0f;
        }
        float range = approachTargetY - wakeTargetY;
        if (range <= 0f) {
            return 0f;
        }
        float progress = (sweepY - wakeTargetY) / range;
        return PApplet.constrain(progress, 0f, 1f);
    }

    private float highlightReveal(float progress) {
        float start = 0.55f;
        float span = 0.45f;
        float normalized = (progress - start) / span;
        float clamped = PApplet.constrain(normalized, 0f, 1f);
        return smoothStep(clamped);
    }

    /**
     * Result of triple-aware beachline measurement at a specific sweep Y.
     * Contains lengths for the specific staged triple (prev/doomed/next arc instances).
     */
    private static class TripleMeasurement {
        final boolean tripleExists;
        final double prevLenPx;
        final double doomedLenPx;
        final double nextLenPx;

        private TripleMeasurement(boolean tripleExists, double prevLenPx, double doomedLenPx, double nextLenPx) {
            this.tripleExists = tripleExists;
            this.prevLenPx = prevLenPx;
            this.doomedLenPx = doomedLenPx;
            this.nextLenPx = nextLenPx;
        }

        static TripleMeasurement notFound() {
            return new TripleMeasurement(false, 0.0, 0.0, 0.0);
        }

        static TripleMeasurement of(double prevLenPx, double doomedLenPx, double nextLenPx) {
            return new TripleMeasurement(true, prevLenPx, doomedLenPx, nextLenPx);
        }

        boolean allFinite() {
            return Double.isFinite(prevLenPx) && Double.isFinite(doomedLenPx) && Double.isFinite(nextLenPx);
        }
    }

    /**
     * Triple-aware measurement: measure the rendered lengths of the specific staged triple
     * (prev/doomed/next arc instances) at the given sweepY.
     *
     * This is the SINGLE SOURCE OF TRUTH for arc length measurements.
     * Uses the same segmentation logic as rendering (computeBeachLineSegments) and measures
     * only the specific arc instances in the triple, not just any segment for those sites.
     *
     * @param sweepY The sweep line Y position to measure at
     * @param circleEventSites The triple identifying the doomed arc (a=prev.site, b=doomed.site, c=next.site)
     * @return TripleMeasurement with lengths in screen pixels, or tripleExists=false if not found
     */
    private TripleMeasurement measureTripleAt(double sweepY, FortuneContext.CircleEvent.Sites circleEventSites) {
        // Create temporary FortuneContext snapshot at this sweepY (never mutate fortuneShow)
        FortuneContext tempCtx = initFortune();

        // Advance temp context to sweepY
        while (true) {
            Double nextY = tempCtx.nextEventY();
            if (nextY == null || nextY > sweepY) {
                break;
            }
            tempCtx.step();
        }

        // Find the doomed arc instance in the beachline by matching the triple
        FortuneContext.BeachArc doomedArc = findDoomedArcInBeachline(tempCtx, circleEventSites, sweepY);

        if (doomedArc == null || doomedArc.prev == null || doomedArc.next == null) {
            return TripleMeasurement.notFound();
        }

        // Get rendered segments using the SAME segmentation that rendering uses
        List<ArcPath> segments = computeBeachLineSegments(tempCtx, (float) sweepY);

        // Measure the specific arc instances for prev/doomed/next
        // We need to match segments to the specific arc instances, not just any segment with that site
        double prevLen = measureArcInstanceSegments(segments, doomedArc.prev);
        double doomedLen = measureArcInstanceSegments(segments, doomedArc);
        double nextLen = measureArcInstanceSegments(segments, doomedArc.next);

        if (Double.isNaN(prevLen) || Double.isNaN(doomedLen) || Double.isNaN(nextLen)) {
            return TripleMeasurement.notFound();
        }

        return TripleMeasurement.of(prevLen, doomedLen, nextLen);
    }


    /**
     * Measure the rendered segments for a specific arc instance using identity-preserving segments.
     */
    private double measureArcInstanceSegments(List<ArcPath> segments, FortuneContext.BeachArc arc) {
        double totalLen = 0.0;
        boolean hasSegment = false;

        for (ArcPath arcPath : segments) {
            if (arcPath.arc() != arc) {
                continue;
            }
            totalLen += measureSegmentScreenPixels(arcPath.path());
            hasSegment = true;
        }

        return hasSegment ? totalLen : Double.NaN;
    }

    /**
     * Measure the polyline length of a Path in screen pixels.
     * Converts world coordinates to screen coordinates and sums distances.
     * Uses inherited worldToScreen() from BaseVisualization.
     */
    private double measureSegmentScreenPixels(com.ryanhoegg.voronoi.sandbox.Path path) {
        List<PVector> points = path.getPoints();
        if (points.size() < 2) {
            return 0.0;
        }

        double totalLength = 0.0;
        PVector prevWorld = points.get(0);
        PVector prevScreen = worldToScreen(prevWorld); // Inherited from BaseVisualization

        for (int i = 1; i < points.size(); i++) {
            PVector currWorld = points.get(i);
            PVector currScreen = worldToScreen(currWorld); // Inherited from BaseVisualization

            double dx = currScreen.x - prevScreen.x;
            double dy = currScreen.y - prevScreen.y;
            totalLength += Math.sqrt(dx * dx + dy * dy);

            prevScreen = currScreen;
        }

        return totalLength;
    }

    private float sweepLineJustBeforeCircleEvent() {
        final float MIN_DISTANCE_WORLD = 7f;
        final float EPSILON = 0.001f;
        final int ITERS = 30;

        float y3 = (float) Math.max(circleEvent.sites().a().y(),
                Math.max(circleEvent.sites().b().y(), circleEvent.sites().c().y()));
        float yEvent = (float) circleEvent.y();

        float yMin = y3 + EPSILON;
        float yMax = yEvent - EPSILON;

        if (!(yMin < yMax)) {
            return yMin + EPSILON;
        }

        // Helper: advance a context until its next event would be AFTER sweepY.
        // This makes ctx.beachLine() consistent with sweepY.
        java.util.function.BiConsumer<FortuneContext, Float> advanceToY = (ctx, sweepY) -> {
            while (true) {
                Double nextY = ctx.nextEventY();
                if (nextY == null) break;
                if (nextY <= sweepY) ctx.step();
                else break;
            }
        };

        FortuneContext ctx = initFortune();

        java.util.function.DoublePredicate wideEnough = (double sweepY) -> {
            advanceToY.accept(ctx, (float) sweepY);

            FortuneContext.BeachArc d = findDoomedArcInCurrentBeachline(ctx, circleEvent.sites(), sweepY);
            if (d == null || d.prev == null || d.next == null) return false;

            double xL = Geometry2D.parabolaIntersectionX(d.prev.site, d.site, sweepY);
            double xR = Geometry2D.parabolaIntersectionX(d.site, d.next.site, sweepY);
            if (!Double.isFinite(xL) || !Double.isFinite(xR)) return false;

            double leftX  = Math.min(xL, xR);
            double rightX = Math.max(xL, xR);

            double yLeft  = Geometry2D.parabolaY(d.site, leftX,  sweepY);
            double yRight = Geometry2D.parabolaY(d.site, rightX, sweepY);
            if (!Double.isFinite(yLeft) || !Double.isFinite(yRight)) return false;

            double dx = rightX - leftX;
            double dy = yRight - yLeft;
            double chord = Math.sqrt(dx * dx + dy * dy);

            return chord >= MIN_DISTANCE_WORLD;
        };

        float mid = app.lerp(yMin, yMax, 0.5f);
        mid = processing.core.PApplet.constrain(mid, yMin + EPSILON, yMax - EPSILON);

        // If midpoint already gives a visible doomed arc, keep it.
        if (wideEnough.test(mid)) return mid;

        // If even yMin isn't wide enough, we can't do better.
        if (!wideEnough.test(yMin)) return yMin + EPSILON;

        // Binary search for the largest y (closest to the event) that is still wide enough.
        float lo = yMin; // wideEnough(lo) == true
        float hi = mid;  // wideEnough(hi) == false

        for (int it = 0; it < ITERS; it++) {
            float m = app.lerp(lo, hi, 0.5f);
            if (wideEnough.test(m)) lo = m;
            else hi = m;
        }

        return processing.core.PApplet.constrain(lo, yMin + EPSILON, yMax - EPSILON);
    }

    private FortuneContext.BeachArc findDoomedArcInCurrentBeachline(
            FortuneContext fortune,
            FortuneContext.CircleEvent.Sites sites,
            double sweepY
    ) {
        // Delegate to the main method with argmin strategy
        return findDoomedArcInBeachline(fortune, sites, sweepY);
    }

    /**
     * Match the chosen circle event in the Fortune context.
     * Advances fortunePlan until it finds a circle event matching the chosenEvent.
     * Returns null if no match is found (indicating a serious error).
     */
    private FortuneContext.CircleEvent matchChosenEventInFortune() {
        if (chosenEvent == null) {
            System.err.println("[CircleEventZoom] ERROR: chosenEvent is null! Cannot match event in Fortune context.");
            return null;
        }

        final double Y_TOLERANCE = 0.01; // Tolerance for Y coordinate matching
        final double CENTER_TOLERANCE = 1.0; // Tolerance for center coordinate matching

        while (fortunePlan.step()) {
            if (fortunePlan.lastEvent() instanceof FortuneContext.CircleEvent candidate) {
                // Check if this matches the chosen event
                if (Math.abs(candidate.y() - chosenEvent.yEvent()) <= Y_TOLERANCE &&
                    candidate.sites().matches(chosenEvent.sites())) {

                    // Also verify center and radius match (within tolerance)
                    double centerDist = Math.sqrt(
                        Math.pow(candidate.center().x() - chosenEvent.center().x(), 2) +
                        Math.pow(candidate.center().y() - chosenEvent.center().y(), 2)
                    );

                    if (centerDist <= CENTER_TOLERANCE) {
                        // Log successful match with event details
                        System.out.printf("[CircleEventZoom] Matched chosen event: y=%.2f c=(%.1f,%.1f) sites=[a=(%.1f,%.1f) b=(%.1f,%.1f) c=(%.1f,%.1f)]%n",
                                candidate.y(),
                                candidate.center().x(), candidate.center().y(),
                                candidate.sites().a().x(), candidate.sites().a().y(),
                                candidate.sites().b().x(), candidate.sites().b().y(),
                                candidate.sites().c().x(), candidate.sites().c().y());
                        return candidate;
                    }
                }
            }
        }

        // No match found - this is a serious error
        return null;
    }

    // ==================== CAMERA AND RENDERING HELPERS ====================
    private void applyCamera() {
        PVector screenCenter = new PVector(app.width / 2.0f, app.height / 2.0f);
        float zoom = currentZoom(); // Use centralized zoom calculation

        PVector focus = currentFocus();
        app.translate(screenCenter.x, screenCenter.y);
        app.scale(zoom);
        app.translate(-focus.x, -focus.y);
    }

    private float smoothStep(float t) {
        return t * t * (3f - 2f * t);
    }

    private float easeOutCubic(float t) {
        t = PApplet.constrain(t, 0f, 1f);
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    private float easeInOutCubic(float t) {
        return (t < 0.5) ? 4 * t * t * t : 1 - Double.valueOf(Math.pow(-2 * t + 2, 3) / 2).floatValue();
    }
}
