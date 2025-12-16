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

public class CircleEventZoom extends BaseVisualization implements Visualization {
    private final FortuneContext fortunePlan;
    private final FortuneContext fortuneShow;
    private final PVector firstSite;

    private enum Scene {
        INTRO_ZOOM,
        APPROACH_FIRST_SITE,
        SETTLE_BELOW_FIRST_SITE,
        EQUAL_DISTANCE_REVEAL,
        WAKE_CIRCLE_EVENT_SITES,
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

    // beach line
    private List<ArcPath> beachLine = new ArrayList<>();
    private float beachLineA = 0f;

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
        super(app, clusterSites, theme);
        this.chosenEvent = chosenEvent;
        fortunePlan = initFortune();
        fortuneShow = initFortune();
        worldCenter = computeCenter(clusterSites);
        firstSite = clusterSites.stream().min(Comparator.comparingDouble(s -> s.y)).orElseThrow();
        this.sweepY = firstSite.y - 80f;
        this.scene = Scene.INTRO_ZOOM;
        this.sceneT = 0f;
    }

    private FortuneContext initFortune() {
        List<Point> fortuneSites = sites.stream().map(v -> new Point(v.x, v.y)).toList();
        Bounds bounds = new Bounds(0, 0, app.width, app.height);
        return new FortuneContext(fortuneSites, bounds);
    }

    private PVector computeCenter(List<PVector> sites) {
        float sx = 0, sy = 0;
        for (PVector site : sites) {
            sx += site.x;
            sy += site.y;
        }
        return new PVector(sx / sites.size(), sy / sites.size());
    }

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
                    System.exit(1);
                }

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

                sweepStartY = sweepY;
                sweepTargetY = (float) chosenEvent.previewSweepY(); // Use precomputed preview Y
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
                if (fadingOut && fadeT >= 1f) {
                    scene = Scene.WAKE_CIRCLE_EVENT_SITES;
                    sceneT = 0f;
                }

                break;
            case WAKE_CIRCLE_EVENT_SITES:
                if (sceneT < 1.8f) {
                    sweepY = PApplet.lerp(sweepStartY, sweepTargetY, easeInOutCubic(sceneT / 1.8f));
                    fadeT = 0;
                }
                if (sceneT >= 1.8f && sceneT < 2.4f) {
                    beachLineA = smoothStep((sceneT - 1.8f) / 0.6f);
                }
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

    private void drawWakeCircleEventSites() {
        drawClusterSites();
        drawSweepLine(sweepY);
        drawBeachLine();
        drawUnseenArea(sweepY);
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
        beachLine.forEach(arcPath -> {
            PVector site = findSite(arcPath.site());
            currentStyle().drawBeachArc(app, arcPath.path(), site, circleEvent.sites().contains(arcPath.site()), currentZoom(), 1.0f);
        });
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

            FortuneContext.BeachArc d = findDoomedArcInCurrentBeachline(ctx, circleEvent.sites());
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
            FortuneContext.CircleEvent.Sites sites
    ) {
        for (FortuneContext.BeachArc b = fortune.beachLine(); b != null; b = b.next) {
            if (b.prev == null || b.next == null) continue;

            var trip = new FortuneContext.CircleEvent.Sites(b.prev.site, b.site, b.next.site);
            if (trip.matches(sites)) {
                return b; // b is the disappearing arc (the middle one)
            }
        }
        return null;
    }

    /**
     * Match the chosen circle event in the Fortune context.
     * Advances fortunePlan until it finds a circle event matching the chosenEvent.
     * Returns null if no match is found (indicating a serious error).
     */
    private FortuneContext.CircleEvent matchChosenEventInFortune() {
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
