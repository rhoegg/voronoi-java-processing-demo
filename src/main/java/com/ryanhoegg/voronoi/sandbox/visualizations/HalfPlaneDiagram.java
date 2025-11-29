package com.ryanhoegg.voronoi.sandbox.visualizations;

import com.ryanhoegg.voronoi.sandbox.Path;
import com.ryanhoegg.voronoi.sandbox.Visualization;
import com.ryanhoegg.voronoi.sandbox.geometry.HalfPlane;
import processing.core.PApplet;
import processing.core.PVector;

import java.util.LinkedList;
import java.util.List;

public class HalfPlaneDiagram extends BaseVisualization {

    private int siteIndex = 0;
    private List<Path> regions = new LinkedList<>();

    public HalfPlaneDiagram(PApplet app, List<PVector> sites) {
        super(app, sites);
    }

    @Override
    public void step() {
        if (siteIndex < sites.size()) {
            PVector site = sites.get(siteIndex);
            List<PVector> others = sites.stream()
                    .filter(s -> ! s.equals(site))
                    .toList();
            regions.add(getClippedRegion(site, others));
            siteIndex++;
            app.redraw();
        }
    }

    @Override
    public void reset() {
    }

    @Override
    public void draw() {
        drawSites();
        drawRegions();
        drawActiveSite();
    }

    void drawRegions() {
        for (int i = 0; i < regions.size(); i++) {
            Path region = regions.get(i);
            boolean isActive = (i == siteIndex - 1); // Most recently added region
            drawRegion(region, isActive);
        }
    }

    void drawRegion(Path r, boolean isActive) {
        if (r != null && !r.getPoints().isEmpty()) {
            // Use different styling for active vs completed cells
            if (isActive) {
                app.fill(StyleB.voronoiCellFillActive(app));
                app.stroke(StyleB.voronoiCellStroke(app));
                app.strokeWeight(3.0f);
            } else {
                app.fill(StyleB.voronoiCellFill(app));
                app.stroke(StyleB.voronoiCellStroke(app));
                app.strokeWeight(2.5f);
            }

            draw(r);
        }
    }

    /**
     * Draw the currently active site (being processed) with emphasis.
     */
    void drawActiveSite() {
        if (siteIndex > 0 && siteIndex <= sites.size()) {
            PVector activeSite = sites.get(siteIndex - 1);

            // Draw subtle glow around active site
            app.noStroke();
            app.fill(StyleB.focusSiteHaloColor(app, 60));
            app.ellipse(activeSite.x, activeSite.y, 18, 18);

            // Draw active site dot (slightly larger and brighter)
            int siteColor = StyleB.siteColor(app, activeSite);
            int r = (siteColor >> 16) & 0xFF;
            int g = (siteColor >> 8) & 0xFF;
            int b = siteColor & 0xFF;
            app.fill(app.color(r, g, b, 255)); // Full opacity
            app.ellipse(activeSite.x, activeSite.y, 14, 14);
        }
    }

    Path getClippedRegion(PVector site, List<PVector> others) {
        Path region = Path.rectangle(new PVector(0, 0), app.width, app.height);
        for (PVector other : others) {
            region = HalfPlane.clipRegionAgainst(site, other, region);
        }
        return region;
    }
}
