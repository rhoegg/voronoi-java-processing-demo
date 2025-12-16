package com.ryanhoegg.voronoi.core;

import com.ryanhoegg.voronoi.core.geometry.Bounds;
import com.ryanhoegg.voronoi.core.geometry.Geometry2D;
import com.ryanhoegg.voronoi.core.geometry.Point;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Selects an eligible circle event from a set of sites for visualization purposes.
 * Uses world coordinates throughout - no dependency on zoom or screen pixels.
 *
 * <p><b>Selection Policy:</b> Returns the first (earliest Y) eligible circle event.
 * This is often the smallest arc. To prefer larger/better arcs, modify this class
 * to collect all eligible events and select by max chord length or other criteria.</p>
 */
public class CircleEventSelector {

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

        // Check for intermediate events between y3 and yEvent
        FortuneContext tempCtx = new FortuneContext(points, bounds);
        advanceFortuneToY(tempCtx, y3 + config.epsilon());
        Double nextEventY = tempCtx.nextEventY();

        // B) Determine yMax: cap at first intermediate event if it exists
        double yMin = y3 + config.epsilon();
        double yMax;
        if (nextEventY != null && nextEventY < yEvent) {
            yMax = nextEventY - config.epsilon();
            rejectionReasons.add(String.format("[%s] Intermediate event at %.2f", evId, nextEventY));

            // If intermediate event makes range invalid, reject
            if (yMin >= yMax) {
                rejectionReasons.add(String.format("[%s] Range too small after capping (yMin=%.2f >= yMax=%.2f)",
                        evId, yMin, yMax));
                return Optional.empty();
            }
        } else {
            yMax = yEvent - config.epsilon();
        }

        // C) Find the preview Y position (midpoint of available range)
        double yPreview = (yMin + yMax) / 2.0;

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
        double arcLenPx = computeArcLengthScreenPixels(doomedArc, yPreview);
        if (!Double.isFinite(arcLenPx) || arcLenPx < config.minArcLenPx()) {
            String reason = String.format("[%s] Arc length %.2fpx < %.2fpx (threshold)",
                    evId, arcLenPx, config.minArcLenPx());
            rejectionReasons.add(reason);
            if (verboseLogging) {
                System.out.println("[CircleEventSelector] REJECT: " + reason);
            }
            return Optional.empty();
        }

        // E) Validity check near event: verify the doomed triple is still alive just before event
        double yCheck = Math.min(yEvent - config.epsilon(), yMax);
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

        return Optional.of(ChosenCircleEvent.from(event, yPreview, arcLenPx));
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
    private double computeArcLengthScreenPixels(FortuneContext.BeachArc arc, double sweepY) {
        // Compute breakpoint X coordinates
        double xLeft = Geometry2D.parabolaIntersectionX(arc.prev.site, arc.site, sweepY);
        double xRight = Geometry2D.parabolaIntersectionX(arc.site, arc.next.site, sweepY);

        if (!Double.isFinite(xLeft) || !Double.isFinite(xRight)) {
            return 0.0;
        }

        // Ensure left < right
        double x0 = Math.min(xLeft, xRight);
        double x1 = Math.max(xLeft, xRight);
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
