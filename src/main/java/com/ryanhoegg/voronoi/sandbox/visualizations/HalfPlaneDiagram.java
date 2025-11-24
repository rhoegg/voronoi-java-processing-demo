package com.ryanhoegg.voronoi.sandbox.visualizations;

import com.ryanhoegg.voronoi.sandbox.Visualization;
import processing.core.PApplet;
import processing.core.PVector;

import java.util.List;

public class HalfPlaneDiagram extends BaseVisualization {
    public HalfPlaneDiagram(PApplet app, List<PVector> sites) {
        super(app, sites);
    }

    @Override
    public void step() {
    }

    @Override
    public void reset() {
    }

    @Override
    public void draw() {
        drawSites();
    }

    void drawSites() {
        app.background(app.color(240));
        app.fill(app.color(15));
        app.noStroke();
        for (PVector site : sites) {
            app.ellipse(site.x, site.y, 6, 6);
        }
    }
}
