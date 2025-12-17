package com.ryanhoegg.voronoi.sandbox.story;

import com.ryanhoegg.voronoi.core.ChosenCircleEvent;
import com.ryanhoegg.voronoi.core.FortuneContext;
import com.ryanhoegg.voronoi.core.geometry.Bounds;
import com.ryanhoegg.voronoi.core.geometry.Geometry2D;
import com.ryanhoegg.voronoi.core.geometry.Point;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Selects story-usable circle events for CircleEventZoom visualization.
 *
 * <p><b>Uses rendered segmentation measurement</b> to ensure selection matches what
 * CircleEventZoom will draw on screen. Pre-computes WAKE and APPROACH Y positions
 * during selection time to eliminate runtime searching and measurement drift.</p>
 *
 * <p><b>Selection Policy:</b> Scans circle events and chooses the best eligible event
 * based on arc visibility (max arc length at preview Y).</p>
 */
public class CircleEventSelector {

    // WAKE scene threshold: all 3 arcs (prev/doomed/next) must be >= this visible
    private static final double MIN_WAKE_PX = 40.0;

    // APPROACH scene target: doomed arc should be ~6px (tiny but visible)
    private static final double TARGET_TINY_PX = 6.0;
    private static final double TINY_TOL_PX = 2.0;

    // Fallback: if exact target not found, doomed must shrink below this
    private static final double FALLBACK_TINY_PX = 20.0;

    private final CircleEventSelectorConfig config;
    private final List<String> rejectionReasons = new ArrayList<>();
    private boolean verboseLogging = false;

    public CircleEventSelector(CircleEventSelectorConfig config) {
        this.config = config;
    }

    /**
     * Enable verbose logging of rejection reasons (useful for debugging).
     */
    public CircleEventSelector withVerboseLogging(boolean enabled) {
        this.verboseLogging = enabled;
        return this;
    }

    /**
     * Find the first eligible circle event from the given sites.
     * Returns empty if no eligible event exists.
     *
     * <p><b>Note:</b> Current policy selects the earliest eligible event by Y coordinate.
     * This may not be the most visually prominent. See class javadoc for alternatives.</p>
     *
     * @param points The sites to analyze
     * @param bounds The bounding box for Fortune's algorithm
     * @return Optional containing the chosen event, or empty if none eligible
     */
    public Optional<ChosenCircleEvent> findEligibleEvent(List<Point> points, Bounds bounds) {
        return findEligibleEvent(points, bounds, null);
    }

    /**
     * Find the best eligible circle event from the given sites, optionally filtering
     * to only include events involving a specific site (typically the first/topmost site).
     *
     * Selection policy: Scans up to maxCircleEventsToScan candidates and chooses the BEST
     * eligible event (max arc length in pixels; tie-breaker: larger yEvent - previewY).
     *
     * @param points The sites to analyze
     * @param bounds The bounding box for Fortune's algorithm
     * @param requiredSite If non-null, only events involving this site are considered
     * @return Optional containing the chosen event, or empty if none eligible
     */
    public Optional<ChosenCircleEvent> findEligibleEvent(List<Point> points, Bounds bounds, Point requiredSite) {
        rejectionReasons.clear();

        FortuneContext fortune = new FortuneContext(points, bounds);
        int eventsScanned = 0;
        List<ChosenCircleEvent> eligibleEvents = new ArrayList<>();

        while (fortune.step() && eventsScanned < config.maxCircleEventsToScan()) {
            FortuneContext.Event event = fortune.lastEvent();

            if (event instanceof FortuneContext.CircleEvent circleEvent) {
                // Filter for required site if specified
                if (requiredSite != null && !circleEvent.sites().contains(requiredSite)) {
                    continue; // Skip events that don't involve the required site
                }

                eventsScanned++;

                Optional<ChosenCircleEvent> chosen = evaluateCircleEvent(circleEvent, points, bounds, requiredSite);
                if (chosen.isPresent()) {
                    eligibleEvents.add(chosen.get());
                    if (verboseLogging) {
                        System.out.printf("[CircleEventSelector] Found eligible event #%d: y=%.2f arcLen=%.2f%n",
                                eligibleEvents.size(), chosen.get().yEvent(), chosen.get().arcChordLength());
                    }
                }
            }
        }

        if (eligibleEvents.isEmpty()) {
            String finalReason = String.format("No eligible events found after scanning %d circle events", eventsScanned);
            rejectionReasons.add(finalReason);
            if (verboseLogging) {
                System.out.println("[CircleEventSelector] " + finalReason);
            }
            return Optional.empty();
        }

        // Select best: max arc length, tie-break by adjacency to firstSite, then by max (yEvent - previewY)
        ChosenCircleEvent best = eligibleEvents.stream()
                .max((a, b) -> {
                    int cmp = Double.compare(a.arcChordLength(), b.arcChordLength());
                    if (cmp != 0) return cmp;

                    // Tie-breaker 1: prefer events where firstSite is adjacent (prev/next) to doomed arc at previewY
                    // This makes the top arc visually "near" the disappearance
                    // Note: We don't have adjacency info stored in ChosenCircleEvent, so this comparison is equal
                    // Future enhancement: store adjacency flag in ChosenCircleEvent if needed

                    // Tie-breaker 2: prefer larger distance between event and preview
                    double distA = a.yEvent() - a.previewSweepY();
                    double distB = b.yEvent() - b.previewSweepY();
                    return Double.compare(distA, distB);
                })
                .orElseThrow();

        System.out.printf("[CircleEventSelector] FINAL chosen event (best of %d): y=%.2f previewY=%.2f arcLenPx=%.2f%n",
                eligibleEvents.size(), best.yEvent(), best.previewSweepY(), best.arcChordLength());
        return Optional.of(best);
    }

    /**
     * Create a compact event ID for logging: "y=XXX c=(X,Y) sites=[...]"
     */
    private String eventId(FortuneContext.CircleEvent event) {
        return String.format("y=%.2f c=(%.1f,%.1f) sites=[a=(%.1f,%.1f) b=(%.1f,%.1f) c=(%.1f,%.1f)]",
                event.y(),
                event.center().x(), event.center().y(),
                event.sites().a().x(), event.sites().a().y(),
                event.sites().b().x(), event.sites().b().y(),
                event.sites().c().x(), event.sites().c().y());
    }

    /**
     * Result of triple-aware beachline measurement at a specific sweep Y.
     * Contains rendered lengths for the staged triple (prev/doomed/next arc instances).
     *
     * <p><b>CRITICAL:</b> The "doomed" arc is identified dynamically by which arc is shrinking fastest,
     * not by position in the Sites record. Fortune's CircleEvent.Sites identifies three sites
     * geometrically but doesn't specify which BeachArc instance is disappearing.</p>
     */
    private static class TripleMeasurement {
        final boolean tripleExists;
        final double prevLenPx;
        final double doomedLenPx;
        final double nextLenPx;
        final FortuneContext.BeachArc prevArc;   // Identity of prev arc
        final FortuneContext.BeachArc doomedArc; // Identity of doomed arc (shrinking fastest)
        final FortuneContext.BeachArc nextArc;   // Identity of next arc

        private TripleMeasurement(boolean tripleExists,
                                  double prevLenPx, double doomedLenPx, double nextLenPx,
                                  FortuneContext.BeachArc prevArc,
                                  FortuneContext.BeachArc doomedArc,
                                  FortuneContext.BeachArc nextArc) {
            this.tripleExists = tripleExists;
            this.prevLenPx = prevLenPx;
            this.doomedLenPx = doomedLenPx;
            this.nextLenPx = nextLenPx;
            this.prevArc = prevArc;
            this.doomedArc = doomedArc;
            this.nextArc = nextArc;
        }

        static TripleMeasurement notFound() {
            return new TripleMeasurement(false, 0.0, 0.0, 0.0, null, null, null);
        }

        static TripleMeasurement of(double prevLenPx, double doomedLenPx, double nextLenPx,
                                   FortuneContext.BeachArc prevArc,
                                   FortuneContext.BeachArc doomedArc,
                                   FortuneContext.BeachArc nextArc) {
            return new TripleMeasurement(true, prevLenPx, doomedLenPx, nextLenPx,
                                        prevArc, doomedArc, nextArc);
        }

        boolean allFinite() {
            return Double.isFinite(prevLenPx) && Double.isFinite(doomedLenPx) && Double.isFinite(nextLenPx);
        }
    }

    /**
     * Identify which arc in the triple is actually disappearing (doomed).
     *
     * <p><b>Strategy:</b> Measure near the circle event where the doomed arc is nearly gone.
     * The SMALLEST arc of the three is the doomed one. Store which index (0=prev, 1=middle, 2=next)
     * is doomed, and use that for all subsequent measurements.</p>
     */
    private static class DoomedArcIdentity {
        final int doomedIndex;  // 0=prev, 1=middle, 2=next
        final Point doomedSite;

        DoomedArcIdentity(int doomedIndex, Point doomedSite) {
            this.doomedIndex = doomedIndex;
            this.doomedSite = doomedSite;
        }
    }

    private DoomedArcIdentity identifyDoomedArc(
        List<Point> points,
        Bounds bounds,
        FortuneContext.CircleEvent event,
        RenderedBeachline.Transform transform
    ) {
        // Measure at Y very close to event where doomed arc is nearly gone
        double referenceY = event.y() - 0.5;

        FortuneContext tempCtx = new FortuneContext(points, bounds);
        advanceFortuneToY(tempCtx, referenceY);

        FortuneContext.BeachArc candidateArc = findDoomedArcInBeachline(tempCtx, event.sites());
        if (candidateArc == null || candidateArc.prev == null || candidateArc.next == null) {
            return null;
        }

        List<RenderedBeachline.ArcSegment> segments =
            RenderedBeachline.computeSegments(tempCtx, referenceY, bounds, transform);

        double len0 = RenderedBeachline.measureArcInstancePixels(segments, candidateArc.prev, transform);
        double len1 = RenderedBeachline.measureArcInstancePixels(segments, candidateArc, transform);
        double len2 = RenderedBeachline.measureArcInstancePixels(segments, candidateArc.next, transform);

        if (Double.isNaN(len0) || Double.isNaN(len1) || Double.isNaN(len2)) {
            return null;
        }

        if (verboseLogging) {
            System.out.printf("[DoomedID] At y=%.2f (%.2f from event): " +
                "prev=(%.1f,%.1f)→%.1fpx middle=(%.1f,%.1f)→%.1fpx next=(%.1f,%.1f)→%.1fpx%n",
                referenceY, event.y() - referenceY,
                candidateArc.prev.site.x(), candidateArc.prev.site.y(), len0,
                candidateArc.site.x(), candidateArc.site.y(), len1,
                candidateArc.next.site.x(), candidateArc.next.site.y(), len2);
        }

        // The SMALLEST arc is the doomed one (it's nearly disappeared)
        if (len0 <= len1 && len0 <= len2) {
            if (verboseLogging) {
                System.out.printf("[DoomedID] → Identified: prev (%.1f,%.1f) with len=%.1fpx%n",
                    candidateArc.prev.site.x(), candidateArc.prev.site.y(), len0);
            }
            return new DoomedArcIdentity(0, candidateArc.prev.site);  // prev is doomed
        } else if (len1 <= len0 && len1 <= len2) {
            if (verboseLogging) {
                System.out.printf("[DoomedID] → Identified: middle (%.1f,%.1f) with len=%.1fpx%n",
                    candidateArc.site.x(), candidateArc.site.y(), len1);
            }
            return new DoomedArcIdentity(1, candidateArc.site);  // middle is doomed
        } else {
            if (verboseLogging) {
                System.out.printf("[DoomedID] → Identified: next (%.1f,%.1f) with len=%.1fpx%n",
                    candidateArc.next.site.x(), candidateArc.next.site.y(), len2);
            }
            return new DoomedArcIdentity(2, candidateArc.next.site);  // next is doomed
        }
    }

    /**
     * Measure triple using RENDERED SEGMENTATION.
     *
     * <p><b>Critical:</b> Uses RenderedBeachline.computeSegments() to ensure measurements
     * match what CircleEventZoom draws on screen. The doomedSite parameter specifies which
     * site is the doomed one (always the middle site from Fortune's algorithm).</p>
     */
    private TripleMeasurement measureTripleAtRendered(
        List<Point> points,
        Bounds bounds,
        FortuneContext.CircleEvent.Sites circleEventSites,
        double sweepY,
        RenderedBeachline.Transform transform,
        Point doomedSite
    ) {
        // 1. Create temp Fortune snapshot
        FortuneContext tempCtx = new FortuneContext(points, bounds);
        advanceFortuneToY(tempCtx, sweepY);

        // 2. Find the triple in beachline (to verify it still exists)
        FortuneContext.BeachArc candidateArc = findDoomedArcInBeachline(tempCtx, circleEventSites);
        if (candidateArc == null || candidateArc.prev == null || candidateArc.next == null) {
            if (verboseLogging) {
                System.out.printf("[MeasureTriple] At y=%.2f: triple not found (candidateArc=%s)%n",
                    sweepY, candidateArc == null ? "null" : "missing prev/next");
            }
            return TripleMeasurement.notFound();
        }

        if (verboseLogging) {
            System.out.printf("[MeasureTriple] At y=%.2f: found triple prev=(%.1f,%.1f) middle=(%.1f,%.1f) next=(%.1f,%.1f) | target sites: a=(%.1f,%.1f) b=(%.1f,%.1f) c=(%.1f,%.1f)%n",
                sweepY,
                candidateArc.prev.site.x(), candidateArc.prev.site.y(),
                candidateArc.site.x(), candidateArc.site.y(),
                candidateArc.next.site.x(), candidateArc.next.site.y(),
                circleEventSites.a().x(), circleEventSites.a().y(),
                circleEventSites.b().x(), circleEventSites.b().y(),
                circleEventSites.c().x(), circleEventSites.c().y());
        }

        // 3. Get RENDERED segments
        List<RenderedBeachline.ArcSegment> segments =
            RenderedBeachline.computeSegments(tempCtx, sweepY, bounds, transform);

        // 4. Measure all three arcs in order: prev, middle, next
        double len0 = RenderedBeachline.measureArcInstancePixels(segments, candidateArc.prev, transform);
        double len1 = RenderedBeachline.measureArcInstancePixels(segments, candidateArc, transform);
        double len2 = RenderedBeachline.measureArcInstancePixels(segments, candidateArc.next, transform);

        if (Double.isNaN(len0) || Double.isNaN(len1) || Double.isNaN(len2)) {
            return TripleMeasurement.notFound();
        }

        // 5. Match measurements to doomed site (site-based matching, not arc identity)
        // The three arcs are candidateArc.prev, candidateArc, candidateArc.next
        // Their measurements are len0, len1, len2
        // We need to figure out which one matches doomedSite

        FortuneContext.BeachArc prevArc = candidateArc.prev;
        FortuneContext.BeachArc middleArc = candidateArc;
        FortuneContext.BeachArc nextArc = candidateArc.next;

        // Find which arc has the doomed site
        boolean prevIsDoomed = siteEquals(prevArc.site, doomedSite);
        boolean middleIsDoomed = siteEquals(middleArc.site, doomedSite);
        boolean nextIsDoomed = siteEquals(nextArc.site, doomedSite);

        double prevLen, doomedLen, nextLen;
        FortuneContext.BeachArc doomedArc;

        if (prevIsDoomed) {
            prevLen = len0;
            doomedLen = len0;
            nextLen = len1;
            doomedArc = prevArc;
        } else if (middleIsDoomed) {
            prevLen = len0;
            doomedLen = len1;
            nextLen = len2;
            doomedArc = middleArc;
        } else if (nextIsDoomed) {
            prevLen = len1;
            doomedLen = len2;
            nextLen = len2;
            doomedArc = nextArc;
        } else {
            // None of the arcs match the doomed site - this shouldn't happen
            return TripleMeasurement.notFound();
        }

        return TripleMeasurement.of(prevLen, doomedLen, nextLen, prevArc, doomedArc, nextArc);
    }

    /**
     * Check if two sites are equal (within epsilon tolerance).
     */
    private boolean siteEquals(Point a, Point b) {
        final double EPSILON = 0.0001;
        return Math.abs(a.x() - b.x()) < EPSILON && Math.abs(a.y() - b.y()) < EPSILON;
    }

    /**
     * Identify the doomed arc by measuring near the event - smallest arc is doomed.
     */
    private Point identifyDoomedArcDynamically(
        List<Point> points,
        Bounds bounds,
        FortuneContext.CircleEvent event,
        RenderedBeachline.Transform transform
    ) {
        double referenceY = event.y() - 0.5;
        FortuneContext tempCtx = new FortuneContext(points, bounds);
        advanceFortuneToY(tempCtx, referenceY);

        FortuneContext.BeachArc candidateArc = findDoomedArcInBeachline(tempCtx, event.sites());
        if (candidateArc == null || candidateArc.prev == null || candidateArc.next == null) {
            return null;
        }

        List<RenderedBeachline.ArcSegment> segments =
            RenderedBeachline.computeSegments(tempCtx, referenceY, bounds, transform);

        double len0 = RenderedBeachline.measureArcInstancePixels(segments, candidateArc.prev, transform);
        double len1 = RenderedBeachline.measureArcInstancePixels(segments, candidateArc, transform);
        double len2 = RenderedBeachline.measureArcInstancePixels(segments, candidateArc.next, transform);

        if (Double.isNaN(len0) || Double.isNaN(len1) || Double.isNaN(len2)) {
            return null;
        }

        if (verboseLogging) {
            System.out.printf("[DoomedID] At y=%.2f: prev=(%.1f,%.1f)→%.1fpx middle=(%.1f,%.1f)→%.1fpx next=(%.1f,%.1f)→%.1fpx%n",
                referenceY,
                candidateArc.prev.site.x(), candidateArc.prev.site.y(), len0,
                candidateArc.site.x(), candidateArc.site.y(), len1,
                candidateArc.next.site.x(), candidateArc.next.site.y(), len2);
        }

        // Return the site of the SMALLEST arc (it's nearly disappeared)
        if (len0 <= len1 && len0 <= len2) {
            return candidateArc.prev.site;
        } else if (len1 <= len0 && len1 <= len2) {
            return candidateArc.site;
        } else {
            return candidateArc.next.site;
        }
    }

    /**
     * Find earliest Y where all 3 arcs (prev/doomed/next) are >= MIN_WAKE_PX.
     *
     * <p>CRITICAL: Search range [yStart, yEnd) is computed once in evaluateCircleEvent.
     * Do NOT recompute or adjust the range here. The ONLY early stopping condition
     * is when tripleExists becomes false (triple disappears from rendered beachline).</p>
     *
     * @param yStart Lower bound for search (already computed: y3 + yGuard)
     * @param yEnd Upper bound for search (already computed: yEvent - epsilon)
     * @param doomedSite Site of the doomed arc (from Fortune's algorithm)
     */
    private double findWakeY(
        FortuneContext.CircleEvent event,
        List<Point> points,
        Bounds bounds,
        RenderedBeachline.Transform transform,
        double yStart,
        double yEnd,
        Point doomedSite
    ) {
        String evId = eventId(event);
        System.out.printf("[StorySelect] %s: Finding WAKE Y in [%.2f, %.2f) where all 3 arcs >= %.1fpx%n",
            evId, yStart, yEnd, MIN_WAKE_PX);

        if (yStart >= yEnd) {
            System.out.printf("[StorySelect] %s: REJECT - range invalid (yStart >= yEnd)%n", evId);
            return Double.NaN;
        }

        // Coarse scan upward
        final double COARSE_STEP = 4.0;
        Double yBracket = null;

        for (double y = yStart; y <= yEnd; y += COARSE_STEP) {
            TripleMeasurement m = measureTripleAtRendered(points, bounds, event.sites(), y, transform, doomedSite);

            if (!m.tripleExists) {
                System.out.printf("[StorySelect] %s: Triple no longer exists at y=%.2f (ONLY valid stop condition)%n", evId, y);
                break;
            }

            if (m.allFinite() &&
                m.prevLenPx >= MIN_WAKE_PX &&
                m.doomedLenPx >= MIN_WAKE_PX &&
                m.nextLenPx >= MIN_WAKE_PX) {
                yBracket = y;
                System.out.printf("[StorySelect] %s: WAKE bracket found at y=%.2f%n", evId, y);
                break;
            }
        }

        if (yBracket == null) {
            System.out.printf("[StorySelect] %s: REJECT - no WAKE Y found%n", evId);
            return Double.NaN;
        }

        // Binary search refinement (0.02 world units precision)
        double yLow = Math.max(yStart, yBracket - COARSE_STEP);
        double yHigh = yBracket;

        while (yHigh - yLow > 0.02) {
            double yMid = (yLow + yHigh) / 2.0;
            TripleMeasurement m = measureTripleAtRendered(points, bounds, event.sites(), yMid, transform, doomedSite);

            if (!m.tripleExists || !m.allFinite() ||
                m.prevLenPx < MIN_WAKE_PX ||
                m.doomedLenPx < MIN_WAKE_PX ||
                m.nextLenPx < MIN_WAKE_PX) {
                yLow = yMid;
            } else {
                yHigh = yMid;
            }
        }

        double wakeY = yHigh;
        TripleMeasurement finalM = measureTripleAtRendered(points, bounds, event.sites(), wakeY, transform, doomedSite);

        System.out.printf("[StorySelect] %s: WAKE Y=%.2f | prev=%.1fpx doomed=%.1fpx next=%.1fpx%n",
            evId, wakeY, finalM.prevLenPx, finalM.doomedLenPx, finalM.nextLenPx);

        return wakeY;
    }

    /**
     * Find Y where doomed arc has MINIMUM rendered length (as close to disappearing as possible).
     *
     * <p><b>Critical:</b> The doomed arc MUST shrink toward ~0px as sweepY approaches yEvent.
     * If measurements don't show this behavior, we have a measurement bug (wrong segment identity).</p>
     *
     * <p>CRITICAL: Search range [wakeY, yEnd) uses yEnd computed in evaluateCircleEvent.
     * Do NOT recompute or adjust the range here. The ONLY early stopping condition
     * is when tripleExists becomes false (triple disappears from rendered beachline).</p>
     *
     * @param wakeY Lower bound for search (already found by findWakeY)
     * @param yEnd Upper bound for search (already computed: yEvent - epsilon)
     * @param doomedSite Site of the doomed arc (from Fortune's algorithm)
     */
    private double findApproachY(
        FortuneContext.CircleEvent event,
        List<Point> points,
        Bounds bounds,
        RenderedBeachline.Transform transform,
        double wakeY,
        double yEnd,
        Point doomedSite
    ) {
        double yMin = wakeY;
        double yEventActual = event.y();

        String evId = eventId(event);
        System.out.printf("[StorySelect] %s: Finding APPROACH Y where doomed arc is MINIMAL (argmin strategy)%n", evId);

        // DIAGNOSTIC: Sample at key positions to verify measurement correctness
        // Expectation: doomedLenPx should decrease dramatically as we approach yEvent
        double[] diagnosticYs = {
            wakeY,
            (wakeY + yEnd) / 2.0,  // midpoint
            Math.max(yMin, yEventActual - 5.0),
            Math.max(yMin, yEventActual - 1.0),
            Math.max(yMin, yEventActual - 0.2),
            Math.max(yMin, yEventActual - 0.05)  // Very close!
        };

        System.out.printf("[StorySelect] %s: === DIAGNOSTIC SAMPLES (verify doomedLen→0) ===%n", evId);
        System.out.printf("[StorySelect] %s:   Circle event sites: a=(%.1f,%.1f) b=(%.1f,%.1f) c=(%.1f,%.1f)%n",
            evId, event.sites().a().x(), event.sites().a().y(),
            event.sites().b().x(), event.sites().b().y(),
            event.sites().c().x(), event.sites().c().y());

        for (double yDiag : diagnosticYs) {
            if (yDiag >= yMin && yDiag < yEnd) {
                TripleMeasurement mDiag = measureTripleAtRendered(points, bounds, event.sites(), yDiag, transform, doomedSite);

                if (mDiag.tripleExists && mDiag.doomedArc != null) {
                    System.out.printf("[StorySelect] %s:   y=%.2f: triple=[prev=(%.1f,%.1f) DOOMED=(%.1f,%.1f) next=(%.1f,%.1f)] " +
                        "lengths=[prev=%.1fpx doomed=%.1fpx next=%.1fpx]%n",
                        evId, yDiag,
                        mDiag.prevArc.site.x(), mDiag.prevArc.site.y(),
                        mDiag.doomedArc.site.x(), mDiag.doomedArc.site.y(),
                        mDiag.nextArc.site.x(), mDiag.nextArc.site.y(),
                        mDiag.prevLenPx, mDiag.doomedLenPx, mDiag.nextLenPx);
                } else {
                    System.out.printf("[StorySelect] %s:   y=%.2f: tripleExists=%s doomedLen=%.1fpx%n",
                        evId, yDiag, mDiag.tripleExists, mDiag.tripleExists ? mDiag.doomedLenPx : Double.NaN);
                }
            }
        }
        System.out.printf("[StorySelect] %s: === END DIAGNOSTICS ===%n", evId);

        // Scan to find Y with MINIMUM doomedLenPx (argmin)
        final double FINE_STEP = 0.5;
        double bestY = Double.NaN;
        double minDoomedLen = Double.POSITIVE_INFINITY;

        for (double y = yMin; y < yEnd; y += FINE_STEP) {
            TripleMeasurement m = measureTripleAtRendered(points, bounds, event.sites(), y, transform, doomedSite);

            if (!m.tripleExists) {
                System.out.printf("[StorySelect] %s: Triple no longer exists at y=%.2f (ONLY valid stop condition)%n", evId, y);
                break;
            }

            // Find Y that MINIMIZES doomedLenPx (closest to disappearing)
            if (m.doomedLenPx < minDoomedLen) {
                minDoomedLen = m.doomedLenPx;
                bestY = y;
            }
        }

        if (Double.isNaN(bestY)) {
            System.out.printf("[StorySelect] %s: REJECT - no APPROACH Y found (triple vanished)%n", evId);
            return Double.NaN;
        }

        // Log the minimum for analysis
        System.out.printf("[StorySelect] %s: APPROACH Y=%.2f | minDoomedLen=%.1fpx (yEvent=%.2f)%n",
            evId, bestY, minDoomedLen, yEventActual);

        // TEMPORARILY: Accept ANY minimum (remove threshold check to see what we actually measure)
        // TODO: Re-enable threshold after proving measurement correctness
        // if (minDoomedLen > FALLBACK_TINY_PX) {
        //     System.out.printf("[StorySelect] %s: REJECT - doomed never shrinks below %.1fpx%n",
        //         evId, FALLBACK_TINY_PX);
        //     return Double.NaN;
        // }

        return bestY;
    }

    /**
     * Evaluate a single circle event for eligibility.
     */
    private Optional<ChosenCircleEvent> evaluateCircleEvent(
            FortuneContext.CircleEvent event,
            List<Point> points,
            Bounds bounds,
            Point requiredSite
    ) {
        FortuneContext.CircleEvent.Sites sites = event.sites();
        double yEvent = event.y();
        String evId = eventId(event);

        // A) Timing sanity: event must be far enough below the lowest site
        double y3 = Math.max(sites.a().y(), Math.max(sites.b().y(), sites.c().y()));
        if (yEvent - y3 < config.minEventDy()) {
            String reason = String.format("[%s] yEvent - y3 = %.2f < minEventDy (%.2f) - likely numerically unstable",
                    evId, yEvent - y3, config.minEventDy());
            rejectionReasons.add(reason);
            if (verboseLogging) {
                System.out.println("[CircleEventSelector] REJECT: " + reason);
            }
            return Optional.empty();
        }

        // B) Compute search range: [yStart, yEnd)
        // yStart: y3 + yGuard to let neighbor arcs develop
        // yEnd: yEvent - epsilon (safety margin before event)
        // CRITICAL: Do NOT cap at intermediate events. The only valid stopping condition
        // is when tripleExists becomes false (staged triple disappears from beachline).
        double yGuard = config.yGuard();
        double yStart = y3 + yGuard;
        double yEnd = yEvent - config.epsilon();

        if (yStart >= yEnd) {
            rejectionReasons.add(String.format("[%s] Range invalid: yStart=%.2f >= yEnd=%.2f",
                    evId, yStart, yEnd));
            return Optional.empty();
        }

        // Informational: log if intermediate events exist (but don't cap search)
        FortuneContext tempCtx = new FortuneContext(points, bounds);
        advanceFortuneToY(tempCtx, y3 + config.epsilon());
        Double nextEventY = tempCtx.nextEventY();
        if (nextEventY != null && nextEventY < yEvent) {
            System.out.printf("[StorySelect] %s: INFO - Intermediate event at y=%.2f exists; " +
                    "continuing search anyway (will stop early only if triple dies)%n",
                    evId, nextEventY);
        }

        // C) Find the preview Y position (midpoint of available range)
        double yPreview = (yStart + yEnd) / 2.0;

        // Create a new Fortune context and advance to preview position
        FortuneContext previewCtx = new FortuneContext(points, bounds);
        advanceFortuneToY(previewCtx, yPreview);

        // Find the doomed arc in the beachline
        FortuneContext.BeachArc doomedArc = findDoomedArcInBeachline(previewCtx, sites);
        if (doomedArc == null || doomedArc.prev == null || doomedArc.next == null) {
            rejectionReasons.add(String.format("[%s] Arc doesn't exist at preview Y=%.2f", evId, yPreview));
            return Optional.empty();
        }

        // Gather evidence about site relationships
        boolean firstSiteInEventTriple = requiredSite != null && sites.contains(requiredSite);
        boolean firstSiteInDoomedTripleAtPreviewY = requiredSite != null && isInTriple(requiredSite, doomedArc);

        // RELAXED STORY CONSTRAINT: firstSite must be in the doomed triple (prev/site/next), not necessarily the center
        if (requiredSite != null && !firstSiteInDoomedTripleAtPreviewY) {
            String reason = String.format("[%s] firstSite (%.1f,%.1f) not in doomed triple [prev=(%.1f,%.1f) site=(%.1f,%.1f) next=(%.1f,%.1f)] at previewY",
                    evId, requiredSite.x(), requiredSite.y(),
                    doomedArc.prev.site.x(), doomedArc.prev.site.y(),
                    doomedArc.site.x(), doomedArc.site.y(),
                    doomedArc.next.site.x(), doomedArc.next.site.y());
            rejectionReasons.add(reason);
            if (verboseLogging) {
                System.out.println("[CircleEventSelector] REJECT: " + reason);
            }
            return Optional.empty();
        }

        // D) Check arc length in SCREEN PIXELS using polyline sampling
        double arcLenPx = computeArcLengthScreenPixels(doomedArc, yPreview, bounds);
        if (!Double.isFinite(arcLenPx) || arcLenPx < config.minArcLenPx()) {
            String reason = String.format("[%s] Arc length %.2fpx < %.2fpx (threshold)",
                    evId, arcLenPx, config.minArcLenPx());
            rejectionReasons.add(reason);
            if (verboseLogging) {
                System.out.println("[CircleEventSelector] REJECT: " + reason);
            }
            return Optional.empty();
        }

        // E) Validity check near event: verify the doomed triple is still alive just before yEnd
        double yCheck = yEnd;
        FortuneContext checkCtx = new FortuneContext(points, bounds);
        advanceFortuneToY(checkCtx, yCheck);
        FortuneContext.BeachArc checkArc = findDoomedArcInBeachline(checkCtx, sites);

        if (checkArc == null || checkArc.prev == null || checkArc.next == null) {
            String reason = String.format("[%s] Triple doesn't exist at yCheck=%.2f (near event)", evId, yCheck);
            rejectionReasons.add(reason);
            if (verboseLogging) {
                System.out.println("[CircleEventSelector] REJECT: " + reason);
            }
            return Optional.empty();
        }

        boolean firstSiteInDoomedTripleNearEventY = requiredSite != null && isInTriple(requiredSite, checkArc);

        if (requiredSite != null && !firstSiteInDoomedTripleNearEventY) {
            String reason = String.format("[%s] At yCheck=%.2f, firstSite not in doomed triple [prev=(%.1f,%.1f) site=(%.1f,%.1f) next=(%.1f,%.1f)]",
                    evId, yCheck,
                    checkArc.prev.site.x(), checkArc.prev.site.y(),
                    checkArc.site.x(), checkArc.site.y(),
                    checkArc.next.site.x(), checkArc.next.site.y());
            rejectionReasons.add(reason);
            if (verboseLogging) {
                System.out.println("[CircleEventSelector] REJECT: " + reason);
            }
            return Optional.empty();
        }

        // NOTE: Old checkTinyArcWindow and checkTripleBecomesConsecutive removed - those validations
        // are now done comprehensively in findWakeY/findApproachY using rendered segmentation

        // Success! Always log detailed evidence about site relationships (not just in verbose mode)
        // Determine firstSite's role in the doomed arc
        String firstSiteRole = "none";
        if (requiredSite != null) {
            if (pointsMatch(requiredSite, doomedArc.site)) {
                firstSiteRole = "doomed";
            } else if (pointsMatch(requiredSite, doomedArc.prev.site)) {
                firstSiteRole = "prev";
            } else if (pointsMatch(requiredSite, doomedArc.next.site)) {
                firstSiteRole = "next";
            }
        }

        System.out.printf("[CircleEventSelector] ELIGIBLE %s | firstSite=(%.1f,%.1f) role=%s | " +
                        "doomedArc.site=(%.1f,%.1f) prev=(%.1f,%.1f) next=(%.1f,%.1f) | " +
                        "flags: inEventTriple=%b inDoomedAtPreview=%b inDoomedNearEvent=%b | arcLen=%.2fpx%n",
                evId,
                requiredSite != null ? requiredSite.x() : 0, requiredSite != null ? requiredSite.y() : 0,
                firstSiteRole,
                doomedArc.site.x(), doomedArc.site.y(),
                doomedArc.prev.site.x(), doomedArc.prev.site.y(),
                doomedArc.next.site.x(), doomedArc.next.site.y(),
                firstSiteInEventTriple, firstSiteInDoomedTripleAtPreviewY, firstSiteInDoomedTripleNearEventY,
                arcLenPx);

        // G) Identify the truly doomed arc dynamically - the smallest arc near the event
        //    Fortune's CircleEvent.Sites labeling doesn't match beachline ordering
        RenderedBeachline.Transform transform = new RenderedBeachline.Transform(
            config.screenCenter(), config.focus(), config.zoom());

        Point doomedSite = identifyDoomedArcDynamically(points, bounds, event, transform);
        if (doomedSite == null) {
            String reason = String.format("[%s] Could not identify doomed arc near event", evId);
            rejectionReasons.add(reason);
            System.out.println("[CircleEventSelector] REJECT: " + reason);
            return Optional.empty();
        }

        System.out.printf("[StorySelect] %s: Identified doomed arc: site=(%.1f,%.1f)%n",
            evId, doomedSite.x(), doomedSite.y());

        // H) Compute WAKE and APPROACH Y positions using identified doomed site
        double wakeY = findWakeY(event, points, bounds, transform, yStart, yEnd, doomedSite);
        if (Double.isNaN(wakeY)) {
            String reason = String.format("[%s] No WAKE Y found", evId);
            rejectionReasons.add(reason);
            System.out.println("[CircleEventSelector] REJECT: " + reason);
            return Optional.empty();
        }

        double approachY = findApproachY(event, points, bounds, transform, wakeY, yEnd, doomedSite);
        if (Double.isNaN(approachY)) {
            String reason = String.format("[%s] No APPROACH Y found", evId);
            rejectionReasons.add(reason);
            System.out.println("[CircleEventSelector] REJECT: " + reason);
            return Optional.empty();
        }

        // Enforce monotonicity: wakeY < approachY < yEvent
        if (!(wakeY < approachY && approachY < yEvent)) {
            String reason = String.format("[%s] Monotonicity violated: wake=%.2f approach=%.2f event=%.2f",
                evId, wakeY, approachY, yEvent);
            rejectionReasons.add(reason);
            System.out.println("[CircleEventSelector] REJECT: " + reason);
            return Optional.empty();
        }

        // Measure at these positions for debug info
        TripleMeasurement wakeM = measureTripleAtRendered(points, bounds, event.sites(), wakeY, transform, doomedSite);
        TripleMeasurement approachM = measureTripleAtRendered(points, bounds, event.sites(), approachY, transform, doomedSite);

        double wakeMinArcLen = Math.min(wakeM.prevLenPx,
                                        Math.min(wakeM.doomedLenPx, wakeM.nextLenPx));

        return Optional.of(ChosenCircleEvent.from(
            event, yPreview, arcLenPx,
            wakeY, approachY, wakeMinArcLen, approachM.doomedLenPx
        ));
    }

    /**
     * Check if two points match within epsilon tolerance.
     */
    private boolean pointsMatch(Point a, Point b) {
        return Math.abs(a.x() - b.x()) < config.epsilon() && Math.abs(a.y() - b.y()) < config.epsilon();
    }

    /**
     * Check if a site is anywhere in the doomed triple (prev, site, or next).
     */
    private boolean isInTriple(Point site, FortuneContext.BeachArc arc) {
        if (arc == null || arc.prev == null || arc.next == null) {
            return false;
        }
        return pointsMatch(site, arc.prev.site) ||
               pointsMatch(site, arc.site) ||
               pointsMatch(site, arc.next.site);
    }

    /**
     * Advance Fortune's algorithm until the next event would be after the given sweepY.
     */
    private void advanceFortuneToY(FortuneContext ctx, double sweepY) {
        while (true) {
            Double nextY = ctx.nextEventY();
            if (nextY == null || nextY > sweepY) {
                break;
            }
            ctx.step();
        }
    }

    /**
     * Find the arc in the beachline whose (prev, self, next) sites match the given Sites triple.
     */
    private FortuneContext.BeachArc findDoomedArcInBeachline(
            FortuneContext ctx,
            FortuneContext.CircleEvent.Sites targetSites
    ) {
        for (FortuneContext.BeachArc arc = ctx.beachLine(); arc != null; arc = arc.next) {
            if (arc.prev == null || arc.next == null) {
                continue;
            }

            FortuneContext.CircleEvent.Sites arcSites = new FortuneContext.CircleEvent.Sites(
                    arc.prev.site, arc.site, arc.next.site
            );

            if (arcSites.matches(targetSites)) {
                return arc;
            }
        }
        return null;
    }

    /**
     * Convert world X coordinate to screen X coordinate.
     * Uses the same transform as rendering: (worldX - focusX) * zoom + screenCenterX
     */
    private double worldToScreenX(double wx) {
        return (wx - config.focus().x()) * config.zoom() + config.screenCenter().x();
    }

    /**
     * Convert world Y coordinate to screen Y coordinate.
     * Uses the same transform as rendering: (worldY - focusY) * zoom + screenCenterY
     */
    private double worldToScreenY(double wy) {
        return (wy - config.focus().y()) * config.zoom() + config.screenCenter().y();
    }

    /**
     * Compute the arc length in SCREEN PIXELS using polyline sampling.
     * Samples points along the parabola between breakpoints, converts to screen space
     * using the exact same world→screen transform as rendering, and sums distances.
     *
     * @param arc The beach arc to measure
     * @param sweepY The sweep line Y position
     * @return Arc length in screen pixels, or 0.0 if not computable
     */
    private double computeArcLengthScreenPixels(FortuneContext.BeachArc arc, double sweepY, Bounds bounds) {
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

        if (xSpan < config.epsilon()) {
            return 0.0; // Too narrow to measure
        }

        // Sample based on screen pixels: use ~2-3 pixels per sample in screen space
        double screenXSpan = Math.abs(worldToScreenX(x1) - worldToScreenX(x0));
        int numSamples = Math.max(10, (int) (screenXSpan / 2.5)); // at least 10 samples
        numSamples = Math.min(numSamples, 200); // cap at 200 to avoid excessive computation

        // Sample points along the arc and convert to screen space using proper transform
        double totalScreenLength = 0.0;
        double prevY = Geometry2D.parabolaY(arc.site, x0, sweepY);
        if (!Double.isFinite(prevY)) {
            return 0.0;
        }
        double prevScreenX = worldToScreenX(x0);
        double prevScreenY = worldToScreenY(prevY);

        for (int i = 1; i <= numSamples; i++) {
            double t = i / (double) numSamples;
            double worldX = x0 + t * xSpan;
            double worldY = Geometry2D.parabolaY(arc.site, worldX, sweepY);

            if (!Double.isFinite(worldY)) {
                continue; // Skip non-finite points
            }

            double screenX = worldToScreenX(worldX);
            double screenY = worldToScreenY(worldY);

            double dx = screenX - prevScreenX;
            double dy = screenY - prevScreenY;
            totalScreenLength += Math.sqrt(dx * dx + dy * dy);

            prevScreenX = screenX;
            prevScreenY = screenY;
        }

        return totalScreenLength;
    }

    /**
     * Get the list of rejection reasons from the last findEligibleEvent call.
     * Useful for debugging why cluster generation is failing.
     */
    public List<String> getRejectionReasons() {
        return new ArrayList<>(rejectionReasons);
    }
}
