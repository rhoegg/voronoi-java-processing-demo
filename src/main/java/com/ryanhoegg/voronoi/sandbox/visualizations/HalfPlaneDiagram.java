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
    }


    void drawRegions() {
        for (Path region : regions) {
            drawRegion(region);
        }
    }

    void drawRegion(Path r) {
        if (r != null && !r.getPoints().isEmpty()) {
            app.fill(StyleB.highlightedRegionFill(app));
            app.stroke(StyleB.regionStrokeColor(app));
            app.strokeWeight(StyleB.EMPHASIS_LINE);

            draw(r);
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
