package com.ryanhoegg.voronoi.sandbox.visualizations;

import com.ryanhoegg.voronoi.core.FortuneContext;
import com.ryanhoegg.voronoi.core.geometry.Geometry2D;
import com.ryanhoegg.voronoi.core.geometry.Point;
import com.ryanhoegg.voronoi.sandbox.Path;
import com.ryanhoegg.voronoi.sandbox.Visualization;
import com.ryanhoegg.voronoi.sandbox.visualizations.theme.ThemeStyle;
import processing.core.PApplet;
import processing.core.PVector;

import java.util.ArrayList;
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
     * Get the current camera zoom level for zoom-aware rendering.
     * Subclasses with zoom should override this.
     *
     * @return Current zoom factor (1.0 = no zoom, higher = zoomed in)
     */
    protected float currentZoom() {
        return 1.0f; // Default: no zoom
    }

    protected PVector currentFocus() {
        return new PVector(app.width / 2f, app.height / 2f);
    }

    protected float screenToWorldX(float sx) {
        float zoom = currentZoom();
        float screenCenterX = app.width / 2f;
        float focusX = currentFocus().x;
        return (sx - screenCenterX) / zoom + focusX;
    }

    /**
     * Convert world X coordinate to screen X coordinate.
     * Uses the same transform as rendering: (worldX - focusX) * zoom + screenCenterX
     */
    protected float worldToScreenX(float wx) {
        float zoom = currentZoom();
        float screenCenterX = app.width / 2f;
        float focusX = currentFocus().x;
        return (wx - focusX) * zoom + screenCenterX;
    }

    /**
     * Convert world Y coordinate to screen Y coordinate.
     * Uses the same transform as rendering: (worldY - focusY) * zoom + screenCenterY
     */
    protected float worldToScreenY(float wy) {
        float zoom = currentZoom();
        float screenCenterY = app.height / 2f;
        float focusY = currentFocus().y;
        return (wy - focusY) * zoom + screenCenterY;
    }

    /**
     * Convert world coordinates to screen coordinates.
     * Uses the same transform as rendering.
     */
    protected PVector worldToScreen(PVector world) {
        return new PVector(worldToScreenX(world.x), worldToScreenY(world.y));
    }

    protected void drawParabolaForSite(PVector site, float directrixY, boolean highlight) {
        Path path = Path.parabola(site, directrixY, 0, app.width);
        currentStyle().drawParabola(app, path, highlight, site, currentZoom());
    }

    protected void drawParabolaForSite(PVector site, float directrixY, boolean highlight, float power) {
        drawParabolaForSite(site, directrixY, highlight, power, 0f, app.width);
    }

    protected void drawParabolaForSite(PVector site, float directrixY, boolean highlight, float power, float min, float max) {
        Path path = Path.parabola(site, directrixY, min, max, power);
        currentStyle().drawParabola(app, path, highlight, site, currentZoom());
    }

    protected List<ArcPath> computeBeachLineSegments(FortuneContext fortune, float sweepLinePosition) {
        List<ArcPath> segments = new ArrayList<>();

        FortuneContext.BeachArc head = fortune.beachLine();
        if (null == head) return segments;

        float directrix = sweepLinePosition;

        float zoom = currentZoom();
        float worldLeft = screenToWorldX(0);
        float worldRight = screenToWorldX(app.width);
        float worldStep = 2f / zoom;

        FortuneContext.BeachArc currentArc = null;
        Path currentSegment = null;

        for (float x = worldLeft; x < worldRight; x += worldStep) {
            // lowest position (highest y) arc at this x
            FortuneContext.BeachArc best = null;
            double bestY = Double.NEGATIVE_INFINITY;

            for (FortuneContext.BeachArc arc = head; arc != null; arc = arc.next) {
                double y = Geometry2D.parabolaY(arc.site, x, directrix);
                if (Double.isNaN(y)) continue;

                if (y > bestY) {
                    bestY = y;
                    best = arc;
                }
            }

            if (null == best) {
                // no arcs here, end the the open segment
                if (null != currentArc && null != currentSegment) {
                    segments.add(new ArcPath(currentArc.site, currentSegment));
                    currentArc = null;
                    currentSegment = null;
                }
                continue;
            }
            if (best != currentArc) { // new segment
                if (null != currentArc && null != currentSegment) {
                    segments.add(new ArcPath(currentArc.site, currentSegment));
                }
                currentArc = best;
                currentSegment = new Path();
            }

            currentSegment.add(new PVector(x, (float) bestY));
        }
        // flush last segment
        if (currentArc != null && currentSegment != null) {
            segments.add(new ArcPath(currentArc.site, currentSegment));
        }

        return segments;
    }


    // ==================== COMMON UTILITIES ====================

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

    protected float parabolaY(PVector p, float x, float directrixY) {
        return Double.valueOf(Geometry2D.parabolaY(
                new Point(p.x, p.y),
                Float.valueOf(x).doubleValue(),
                Float.valueOf(directrixY).doubleValue())).floatValue();
    }

    protected boolean locationEquals(PVector v, Point p) {
        final float EPSILON = 0.0001f;
        float px = Double.valueOf(p.x()).floatValue();
        float py = Double.valueOf(p.y()).floatValue();
        return PApplet.abs(px - v.x) < EPSILON && PApplet.abs(py - v.y) < EPSILON;
    }
}
