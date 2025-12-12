package com.ryanhoegg.voronoi.sandbox.visualizations;

import com.ryanhoegg.voronoi.sandbox.Path;
import com.ryanhoegg.voronoi.sandbox.Visualization;
import com.ryanhoegg.voronoi.sandbox.visualizations.theme.ThemeStyle;
import processing.core.PApplet;
import processing.core.PVector;

import java.util.List;

import static processing.core.PConstants.CLOSE;

/**
 * BaseVisualization - Common mechanics for all Voronoi visualizations.
 *
 * Responsibilities:
 * - Time tracking (pulseTime)
 * - Helper methods that delegate to ThemeStyle
 * - Common drawing utilities (paths, stars, etc.)
 *
 * Does NOT contain:
 * - Theme-specific styling (colors, sizes, stroke weights)
 * - Pulse shaping (that's in ThemeStyle)
 */
public abstract class BaseVisualization implements Visualization {
    protected final PApplet app;
    protected final List<PVector> sites;
    protected final Theme theme;
    protected float pulseTime = 0f; // Running time in seconds for animations

    BaseVisualization(PApplet app, List<PVector> sites, Theme theme) {
        this.app = app;
        this.sites = sites;
        this.theme = theme;
    }

    @Override
    public void update(float dt) {
        pulseTime += dt; // Update running time
    }

    @Override
    public void keyPressed(char key, int keyCode) {}

    // ==================== THEME DELEGATION ====================

    /**
     * Get the current ThemeStyle implementation.
     */
    protected ThemeStyle currentStyle() {
        return ThemeEngine.style(theme);
    }

    /**
     * Get unshaped pulse parameter (0-1) for themes to shape as they wish.
     * Returns a linear ramp repeating every ~2.5 seconds.
     * ThemeStyle implementations apply their own shaping (sin, ease, etc.).
     */
    protected float getPulseParam() {
        float period = 2.5f;
        return (pulseTime % period) / period;
    }

    // ==================== BACKGROUND ====================

    /**
     * Draw the theme's background gradient.
     */
    protected void drawBackground() {
        currentStyle().drawBackground(app);
    }

    // ==================== SITES ====================

    /**
     * Draw all sites using the theme's default styling.
     */
    protected void drawSites() {
        for (PVector site : sites) {
            currentStyle().drawSite(app, site, false, pulseTime);
        }
    }

    /**
     * Draw a site with highlighting control.
     */
    protected void drawSite(PVector pos, boolean highlighted) {
        currentStyle().drawSite(app, pos, highlighted, pulseTime);
    }

    /**
     * Draw a normal (non-highlighted) site.
     */
    protected void drawSite(PVector pos) {
        currentStyle().drawSite(app, pos, false, pulseTime);
    }

    // ==================== SWEEP LINE ====================

    /**
     * Draw the sweep line at the given y-coordinate.
     */
    protected void drawSweepLine(float y) {
        currentStyle().drawSweepLine(app, y, pulseTime);
    }

    /**
     * Draw the unseen area below the sweep line.
     */
    protected void drawUnseenArea(float sweepY) {
        currentStyle().drawUnseenArea(app, sweepY);
    }

    // ==================== PARABOLAS ====================

    /**
     * Draw a parabola for a site with theme-specific styling.
     */
    protected void drawParabolaForSite(PVector site, float directrixY, boolean highlight) {
        Path path = Path.parabola(site, directrixY, 0, app.width);
        currentStyle().drawParabola(app, path, highlight, site);
    }

    // ==================== COMMON UTILITIES ====================

    /**
     * Draw a closed path.
     */
    protected void draw(Path p) {
        app.beginShape();
        for (PVector point: p.getPoints()) {
            app.vertex(point.x, point.y);
        }
        app.endShape(CLOSE);
    }

    /**
     * Draw a star shape at a location.
     */
    void drawStar(PVector location) {
        drawStar(location, 15f);
    }

    /**
     * Draw a star shape with custom radius.
     */
    void drawStar(PVector location, float radius) {
        app.fill(ThemeEngine.starburstFillColor(app, theme, 225));
        app.stroke(ThemeEngine.starburstStrokeColor(app, 225));
        app.strokeWeight(ThemeEngine.THIN_LINE);
        draw(Path.star(location, radius));
    }
}
