package com.ryanhoegg.voronoi.sandbox.visualizations;

import com.ryanhoegg.voronoi.core.FortuneContext;
import com.ryanhoegg.voronoi.core.geometry.Bounds;
import com.ryanhoegg.voronoi.core.geometry.Point;
import com.ryanhoegg.voronoi.sandbox.Visualization;
import processing.core.PApplet;
import processing.core.PVector;

import java.util.Comparator;
import java.util.List;

public class CircleEventZoom extends BaseVisualization implements Visualization {
    private final FortuneContext fortune;

    private enum Scene {
        INTRO_ZOOM,
        APPROACH_FIRST_SITE,
        SETTLE_BELOW_FIRST_SITE,
        EQUAL_DISTANCE_REVEAL
    }

    private Scene scene = Scene.INTRO_ZOOM;
    private float sceneT = 0f;

    private float sweepY = 0f;

    // scene transition and sweep settle
    private float sweepStartY;
    private float sweepTargetY;
    private float sweepSettleT = 0f;
    private boolean sweepSettling = false;

    private PVector witness = new PVector(0f, 0f);
    private float witnessA;
    private float witnessEndpointX1, witnessEndpointX2;
    private static final float WITNESS_FADE_TIME = 0.4f;
    private static final float WITNESS_PERIOD = 10f;
    private static final float WITNESS_SWING = 47f;

    private final float sweepSettleDuration = 2.6f;

    private final PVector firstSite;

    // camera / zoom
    private final PVector worldCenter;
    private final float targetZoom = 3.0f;
    private final float zoomDuration = 1.8f;

    private float zoomT = 0.0f;    // zoom progress from 0 to 1
    private boolean zoomDone = false;

    public CircleEventZoom(PApplet app, List<PVector> clusterSites, Theme theme) {
        super(app, clusterSites, theme);
        fortune = initFortune();
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
                float firstSiteOffset = (app.width / 2 - firstSite.x);
                float oscillateCenterX = firstSite.x + (0.05f * firstSiteOffset);
                witnessEndpointX1 = oscillateCenterX + ((firstSiteOffset > 0) ? -WITNESS_SWING : WITNESS_SWING);
                witnessEndpointX2 = oscillateCenterX + ((firstSiteOffset > 0) ? WITNESS_SWING : -WITNESS_SWING);
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
                witnessA = 1f;
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
            }
            drawSite(s, shouldHighlight);
        });
    }

    private void drawWitnessSegments() {
        float fade = (sceneT < 0.4f) ? sceneT / 0.4f : 1f;
        currentStyle().drawWitnessSegments(app, witness, firstSite, sweepY, fade);
    }

    private void drawWitnessDistanceHelpers() {
        float fade = (sceneT < 2.4f) ? sceneT / 2.4f : 1f;
        currentStyle().drawWitnessDistanceHelpers(app, witness, firstSite, sweepY, easeInOutCubic(fade));
    }

    @Override
    protected float currentZoom() {
        float easing = smoothStep(zoomT);
        return PApplet.lerp(1f, targetZoom, easing);
    }

    private void applyCamera() {
        PVector screenCenter = new PVector(app.width / 2.0f, app.height / 2.0f);
        float zoom = currentZoom(); // Use centralized zoom calculation

        PVector focus = PVector.lerp(screenCenter, worldCenter, smoothStep(zoomT));
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
