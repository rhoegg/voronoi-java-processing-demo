package com.ryanhoegg.voronoi.sandbox.visualizations;

import com.ryanhoegg.voronoi.core.FortuneContext;
import com.ryanhoegg.voronoi.core.geometry.Bounds;
import com.ryanhoegg.voronoi.core.geometry.Point;
import com.ryanhoegg.voronoi.sandbox.Visualization;
import com.ryanhoegg.voronoi.sandbox.geometry.ScreenTransform;
import processing.core.PApplet;
import processing.core.PVector;

import java.util.List;

public class CircleEventZoom extends BaseVisualization implements Visualization {
    private final FortuneContext fortune;

    // camera / zoom
    private final PVector worldCenter;
    private final float targetZoom = 3.5f;
    private final float zoomDuration = 1.8f;

    private float zoomT = 0.0f;    // zoom progress from 0 to 1
    private boolean zoomDone = false;

    private ScreenTransform camera; // world to screen transform

    public CircleEventZoom(PApplet app, List<PVector> clusterSites) {
        super(app, clusterSites);
        fortune = initFortune();
        worldCenter = computeCenter(clusterSites);
        camera = ScreenTransform.identity();
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

    }

    @Override
    public void update(float dt) {
        if (!zoomDone) {
            zoomT += dt / zoomDuration;
            if (zoomT > 1f) {
                zoomT = 1f;
                zoomDone = true;
            }
        }

        camera = buildCameraTransform();
    }

    @Override
    public void draw() {
        StyleB.drawGradientBackground(app);
        float siteSize = PApplet.lerp(6, 16, smoothStep(zoomT));
        app.noStroke();
        sites.forEach(worldSite -> {
            PVector cameraSite = camera.apply(worldSite);
            app.fill(StyleB.siteColor(app, worldSite));
            app.ellipse(cameraSite.x, cameraSite.y, siteSize, siteSize);
        });
    }

    private float smoothStep(float t) {
        return t * t * (3f - 2f * t);
    }

    private ScreenTransform buildCameraTransform() {
        PVector cameraCenter = new PVector(app.width / 2.0f, app.height / 2.0f);
        float easing = smoothStep(zoomT); // eased progress between 0 and 1
        // eased zoom
        float zoom = PApplet.lerp(1f, targetZoom, easing);
        // eased position along path from camera center to world center
        PVector focus = PVector.lerp(cameraCenter, worldCenter, easing);
        return ScreenTransform.translate(-focus.x, -focus.y)
                .andThen(ScreenTransform.scale(zoom))
                .andThen(ScreenTransform.translate(cameraCenter.x, cameraCenter.y));
    }
}
