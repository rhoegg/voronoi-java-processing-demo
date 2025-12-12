package com.ryanhoegg.voronoi.sandbox.visualizations;

import com.ryanhoegg.voronoi.core.FortuneContext;
import com.ryanhoegg.voronoi.core.geometry.Bounds;
import com.ryanhoegg.voronoi.core.geometry.Point;
import com.ryanhoegg.voronoi.sandbox.Visualization;
import com.ryanhoegg.voronoi.sandbox.geometry.ScreenTransform;
import processing.core.PApplet;
import processing.core.PVector;

import java.util.Comparator;
import java.util.List;

public class CircleEventZoom extends BaseVisualization implements Visualization {
    private final FortuneContext fortune;

    private enum Scene {
        INTRO_ZOOM,
        FIRST_SITE_EVENT,
    }

    private Scene scene = Scene.INTRO_ZOOM;
    private float sceneT = 0f;
    private float sweepY = 0f;
    private static final float SWEEP_SPEED = 55f;

    private final PVector firstSite;

    // camera / zoom
    private final PVector worldCenter;
    private final float targetZoom = 3.5f;
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
                    scene = Scene.FIRST_SITE_EVENT;
                    sceneT = 0f;
                    sweepY = firstSite.y - 80f;
                }
                break;
            case FIRST_SITE_EVENT:
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
            case FIRST_SITE_EVENT:
                zoomT = 1f;

                float targetY = firstSite.y + 40f;
                if (sweepY < targetY) {
                    sweepY += SWEEP_SPEED * dt;
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
            case FIRST_SITE_EVENT -> drawFirstSiteEventScene();
        }

        app.popMatrix();
    }

    private void drawIntroZoomScene() {
        drawClusterSites();
    }

    private void drawFirstSiteEventScene() {
        drawClusterSites();
        drawSweepLine(sweepY);
    }

    private void drawClusterSites() {
        // Draw all sites using centralized drawing (theme handles pulsing)
        sites.forEach(s -> {
            boolean isFirstSite = s.equals(firstSite);
            // In FIRST_SITE_EVENT scene, highlight the first site (theme will pulse it)
            boolean shouldHighlight = scene == Scene.FIRST_SITE_EVENT && isFirstSite;
            drawSite(s, shouldHighlight);
        });
    }

    private float smoothStep(float t) {
        return t * t * (3f - 2f * t);
    }

    private void applyCamera() {
        PVector screenCenter = new PVector(app.width / 2.0f, app.height / 2.0f);
        float easing = smoothStep(zoomT); // eased progress between 0 and 1
        // eased zoom
        float zoom = PApplet.lerp(1f, targetZoom, easing);

        PVector focus = PVector.lerp(screenCenter, worldCenter, easing);
        app.translate(screenCenter.x, screenCenter.y);
        app.scale(zoom);
        app.translate(-focus.x, -focus.y);
    }
}
