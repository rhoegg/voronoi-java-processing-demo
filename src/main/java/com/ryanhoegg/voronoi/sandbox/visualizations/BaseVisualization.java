package com.ryanhoegg.voronoi.sandbox.visualizations;

import com.ryanhoegg.voronoi.sandbox.Path;
import com.ryanhoegg.voronoi.sandbox.Visualization;
import processing.core.PApplet;
import processing.core.PVector;

import java.util.List;

import static processing.core.PConstants.CLOSE;

public abstract class BaseVisualization implements Visualization {
    protected final PApplet app;
    protected final List<PVector> sites;

    BaseVisualization(PApplet app, List<PVector> sites) {
        this.app = app;
        this.sites = sites;
    }

    @Override
    public void update(float dt) {}

    @Override
    public void keyPressed(char key, int keyCode) {}

    protected void draw(Path p) {
        app.beginShape();
        for (PVector point: p.getPoints()) {
            app.vertex(point.x, point.y);
        }
        app.endShape(CLOSE);
    }

    protected void drawSites() {
        app.background(app.color(240));
        app.fill(app.color(15));
        app.noStroke();
        for (PVector site : sites) {
            app.ellipse(site.x, site.y, 6, 6);
        }
    }

}
