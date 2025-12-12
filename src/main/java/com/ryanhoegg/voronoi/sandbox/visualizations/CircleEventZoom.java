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
        SETTLE_BELOW_FIRST_SITE
    }

    private Scene scene = Scene.INTRO_ZOOM;
    private float sceneT = 0f;

    private float sweepY = 0f;
    private static final float SWEEP_SPEED = 55f;

    // scene transition and sweep settle
    private float sweepStartY;
    private float sweepTargetY;
    private float sweepSettleT = 0f;
    private boolean sweepSettling = false;

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
        }

        app.popMatrix();
    }

    private void drawIntroZoomScene() {
        drawClusterSites();
    }

    private void drawApproachFirstSiteScene() {
        drawClusterSites();
        drawSweepLine(sweepY);
        // Use power=2.5 for concentrated sampling near vertex during zoom close-up
        if (sweepY > firstSite.y) drawParabolaForSite(firstSite, sweepY, true, 2.5f);
        drawUnseenArea(sweepY);
    }

    private void drawClusterSites() {
        // Draw all sites using centralized drawing (theme handles pulsing)
        sites.forEach(s -> {
            boolean isFirstSite = s.equals(firstSite);
            // In FIRST_SITE_EVENT scene, highlight the first site (theme will pulse it)
            boolean shouldHighlight = scene == Scene.APPROACH_FIRST_SITE && isFirstSite;
            drawSite(s, shouldHighlight);
        });
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
}
