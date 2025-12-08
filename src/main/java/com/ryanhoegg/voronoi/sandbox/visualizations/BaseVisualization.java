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
    protected final Theme theme;

    BaseVisualization(PApplet app, List<PVector> sites, Theme theme) {
        this.app = app;
        this.sites = sites;
        this.theme = theme;
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

    void drawStar(PVector location) {
        drawStar(location, 15f);
    }
    void drawStar(PVector location, float radius) {
        app.fill(ThemeEngine.starburstFillColor(app, theme, 225));
        app.stroke(ThemeEngine.starburstStrokeColor(app, 225));
        app.strokeWeight(ThemeEngine.THIN_LINE);
        draw(Path.star(location, radius));
    }

    protected void drawSites() {
        ThemeEngine.drawGradientBackground(app, theme);

        // Draw site shadows first for better contrast
        int shadowColor = ThemeEngine.siteShadow(app, theme);
        app.noStroke();
        for (PVector site : sites) {
            app.fill(shadowColor);
            // Draw shadow slightly larger and offset
            app.ellipse(site.x + 1, site.y + 1, 9, 9);
        }

        // Draw sites on top with optional stroke
        for (PVector site : sites) {
            int siteFill = ThemeEngine.siteFill(app, theme, site);
            int siteStroke = ThemeEngine.siteStroke(app, theme, site.x, site.y);

            app.stroke(siteStroke);
            app.strokeWeight(0.8f);
            app.fill(siteFill);
            app.ellipse(site.x, site.y, 6, 6);
        }
    }

}
