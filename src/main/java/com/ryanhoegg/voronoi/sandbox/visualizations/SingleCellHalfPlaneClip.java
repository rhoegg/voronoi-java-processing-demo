package com.ryanhoegg.voronoi.sandbox.visualizations;

import com.ryanhoegg.voronoi.sandbox.Path;
import com.ryanhoegg.voronoi.sandbox.geometry.HalfPlane;
import processing.core.PApplet;
import processing.core.PVector;

import java.util.ArrayList;
import java.util.List;

public class SingleCellHalfPlaneClip extends BaseVisualization {
    PVector focused;
    PVector neighborHighlight;
    List<PVector> others = new ArrayList<>();
    Path focusedRegion;

    int clipIndex = 0;
    boolean shouldRenderRegion = false;
    boolean shouldRenderBisector = false;

    public SingleCellHalfPlaneClip(PApplet app, List<PVector> sites, Theme theme) {
        super(app, sites, theme);

        focusCentralSite();
        this.focusedRegion = Path.rectangle(new PVector(0, 0), app.width, app.height);
    }

    @Override
    public void step() {
        if (clipIndex < others.size()) {
            PVector neighbor = others.get(clipIndex);
            neighborHighlight = neighbor;
            focusedRegion = HalfPlane.clipRegionAgainst(focused, neighbor, focusedRegion);
            clipIndex++;
            app.redraw();
        } else {
            neighborHighlight = null;
            app.redraw();
        }
    }

    @Override
    public void reset() {
        clipIndex = 0;
        neighborHighlight = null;
        focusedRegion = Path.rectangle(new PVector(0, 0), app.width, app.height);
        app.redraw();
    }

    @Override
    public void draw() {
        drawBackground();
        drawSites();
        drawFocusSite();
        drawHighlightedNeighbor();
        if (shouldRenderBisector) {
            drawBisector();
        }
        if (shouldRenderRegion) {
            drawFocusedRegion();
        }
    }

    /**
     * Draw the focus site with a bright color and subtle pulsing halo.
     * Theme handles pulsing animation.
     */
    void drawFocusSite() {
        drawSite(focused, true);
    }

    @Override
    public void keyPressed(char key, int keyCode) {
        switch (key) {
            case 'v':
                shouldRenderRegion = !shouldRenderRegion;
                app.redraw();
                break;
            case 'b':
                shouldRenderBisector = !shouldRenderBisector;
                app.redraw();
                break;
        }
    }

    void focusCentralSite() {
        PVector center = new PVector(app.width / 2, app.height / 2);
        float bestDistanceSquared = Float.MAX_VALUE;
        PVector best = null;
        for (PVector site : sites) {
            float d = PVector.sub(site, center).magSq();
            if (d < bestDistanceSquared) {
                bestDistanceSquared = d;
                best = site;
            }
        }
        this.focused = best;
        for (PVector site : sites) {
            if (! site.equals(focused)) {
                this.others.add(site);
            }
        }
    }

    void drawHighlightedNeighbor() {
        if (null != neighborHighlight) {
            // Draw highlighted neighbor using centralized method
            // Use custom colors via special highlight color in ThemeEngine
            // For now, just use standard highlighted drawing
            drawSite(neighborHighlight, true);
        }
    }

    void drawBisector() {
        // only draw if we're highlighting a neighbor
        if (null != neighborHighlight) {
            PVector site = focused;
            PVector neighbor = neighborHighlight;
            PVector SN = PVector.sub(neighbor, site);
            PVector perpendicularDirection = new PVector(-SN.y, SN.x).normalize(); // unit length
            PVector midpoint = PVector.add(site, neighbor).mult(0.5f);

            // Discarded half-plane fill (warm translucent on the "neighbor" side)
            Path box = Path.rectangle(new PVector(0, 0), app.width, app.height);
            Path shaded = HalfPlane.clipRegionAgainst(neighbor, site, box);
            if (null != shaded && ! shaded.getPoints().isEmpty()) {
                app.noStroke();
                app.fill(ThemeEngine.discardedHalfPlaneFill(app, theme));
                draw(shaded);
            }

            // Bisector line (strong, high-contrast)
            float bisectorLength = 1500; // longer than the screen diagonal
            PVector p1 = PVector.add(midpoint, PVector.mult(perpendicularDirection, bisectorLength));
            PVector p2 = PVector.add(midpoint, PVector.mult(perpendicularDirection, -1 * bisectorLength));

            app.stroke(ThemeEngine.clipLineStroke(app, theme));
            app.strokeWeight(3.0f);
            app.line(p1.x, p1.y, p2.x, p2.y);
        }
    }

    void drawFocusedRegion() {
        // Current clipping polygon
        app.fill(ThemeEngine.clippingPolygonFill(app, theme));
        app.stroke(ThemeEngine.clippingPolygonStroke(app, theme));
        app.strokeWeight(3.0f);
        drawRegion(focusedRegion);
    }

    void drawRegion(Path r) {
        if (r != null && !r.getPoints().isEmpty()) {
            draw(this.focusedRegion);
        }
    }

}
