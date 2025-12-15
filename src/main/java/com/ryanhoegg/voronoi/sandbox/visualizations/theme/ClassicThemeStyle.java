package com.ryanhoegg.voronoi.sandbox.visualizations.theme;

import com.ryanhoegg.voronoi.sandbox.Path;
import processing.core.PApplet;
import processing.core.PVector;

/**
 * ClassicThemeStyle - High-tech cool color scheme with multi-hue sites.
 * Features:
 * - Cool grey-blue background gradient
 * - Multi-hue deterministic palette (cyan, teal, blue, violet, lime, magenta)
 * - Cyan glow sweep line with violet core and shadow
 * - Smooth pulsing for highlights
 */
public class ClassicThemeStyle implements ThemeStyle {

    // ==================== BACKGROUND CONSTANTS ====================
    private static final int BG_TOP_R = 228;
    private static final int BG_TOP_G = 234;
    private static final int BG_TOP_B = 242;

    private static final int BG_BOTTOM_R = 182;
    private static final int BG_BOTTOM_G = 190;
    private static final int BG_BOTTOM_B = 205;

    // ==================== SITE CONSTANTS ====================
    private static final float SITE_SIZE = 6f;
    private static final float HIGHLIGHTED_SITE_SIZE = 14f;

    // ==================== SWEEP LINE CONSTANTS ====================
    private static final int SWEEP_GLOW_R = 120;
    private static final int SWEEP_GLOW_G = 200;
    private static final int SWEEP_GLOW_B = 255;
    private static final int SWEEP_GLOW_ALPHA = 80;

    private static final int SWEEP_MAIN_R = 70;
    private static final int SWEEP_MAIN_G = 40;
    private static final int SWEEP_MAIN_B = 180;
    private static final int SWEEP_MAIN_ALPHA = 230;

    private static final int SWEEP_SHADOW_R = 40;
    private static final int SWEEP_SHADOW_G = 30;
    private static final int SWEEP_SHADOW_B = 80;
    private static final int SWEEP_SHADOW_ALPHA = 140;

    private static final float SWEEP_GLOW_WEIGHT = 7.0f;
    private static final float SWEEP_CORE_WEIGHT = 5.0f;
    private static final float SWEEP_SHADOW_WEIGHT = 2.0f;

    // ==================== PULSE CONSTANTS ====================
    private static final float PULSE_PERIOD = 2.8f; // Slightly faster than Christmas
    private static final float PULSE_SIZE_MIN = 0.95f;
    private static final float PULSE_SIZE_MAX = 1.10f;
    private static final float PULSE_GLOW_ALPHA_MAX = 60f;

    // ==================== BACKGROUND ====================

    @Override
    public int backgroundTop(PApplet app) {
        return app.color(BG_TOP_R, BG_TOP_G, BG_TOP_B);
    }

    @Override
    public int backgroundBottom(PApplet app) {
        return app.color(BG_BOTTOM_R, BG_BOTTOM_G, BG_BOTTOM_B);
    }

    @Override
    public void drawBackground(PApplet app) {
        int topColor = backgroundTop(app);
        int bottomColor = backgroundBottom(app);

        // Draw vertical gradient using thin horizontal lines
        for (int y = 0; y < app.height; y++) {
            float inter = (float) y / app.height;
            int c = app.lerpColor(topColor, bottomColor, inter);
            app.stroke(c);
            app.line(0, y, app.width, y);
        }
    }

    // ==================== SITES ====================

    @Override
    public void drawSite(PApplet app, PVector pos, boolean highlighted, float time) {
        // Get base size from theme
        float baseSize = highlighted ? HIGHLIGHTED_SITE_SIZE : SITE_SIZE;
        float baseShadowSize = baseSize + (highlighted ? 2f : 3f);

        // Compute pulse if highlighted
        float sizeMultiplier = 1.0f;
        float glowAlpha = 0f;

        if (highlighted) {
            float pulse = computePulse(time); // 0..1
            sizeMultiplier = PULSE_SIZE_MIN + pulse * (PULSE_SIZE_MAX - PULSE_SIZE_MIN);
            glowAlpha = pulse * PULSE_GLOW_ALPHA_MAX;
        }

        float siteSize = baseSize * sizeMultiplier;
        float shadowSize = baseShadowSize * sizeMultiplier;

        // Get multi-hue color for this site
        int siteFillColor = getMultiHueColor(app, pos.x, pos.y);
        int strokeColor = getDarkenedStroke(app, siteFillColor);
        int shadowColor = app.color(40, 45, 55, 120); // Dark grey-blue

        // Extract RGB for manipulation
        int fillR = (siteFillColor >> 16) & 0xFF;
        int fillG = (siteFillColor >> 8) & 0xFF;
        int fillB = siteFillColor & 0xFF;

        // Draw pulsing glow layer (if pulsing and highlighted)
        if (glowAlpha > 1f && highlighted) {
            app.noStroke();
            app.fill(app.color(fillR, fillG, fillB, (int) glowAlpha));
            float glowSize = siteSize + 8f;
            app.ellipse(pos.x, pos.y, glowSize, glowSize);
        }

        // Draw shadow
        app.noStroke();
        app.fill(shadowColor);
        app.ellipse(pos.x + 1, pos.y + 1, shadowSize, shadowSize);

        // Draw main site
        app.stroke(strokeColor);
        app.strokeWeight(highlighted ? 1.0f : 0.8f);
        int siteAlpha = highlighted ? 240 : 220;
        app.fill(app.color(fillR, fillG, fillB, siteAlpha));
        app.ellipse(pos.x, pos.y, siteSize, siteSize);
    }

    /**
     * Get multi-hue site color based on deterministic hash.
     * Returns colors in cyan, teal, blue, violet, lime, or soft magenta families.
     */
    private int getMultiHueColor(PApplet app, double x, double y) {
        // Deterministic hash from coordinates
        int h = Double.hashCode(x) * 31 + Double.hashCode(y);

        // Use different bits for color family selection
        int family = (h >>> 28) & 0x7; // 0-7

        // Generate variation within the family
        int variation = Math.abs(h) % 40; // 0-39 for subtle variation

        int r, g, b;
        switch (family % 6) {
            case 0: // Cyan
                r = 60 + variation;
                g = 180 + variation;
                b = 220 + variation / 2;
                break;
            case 1: // Teal
                r = 50 + variation;
                g = 160 + variation;
                b = 160 + variation;
                break;
            case 2: // Blue
                r = 70 + variation;
                g = 100 + variation;
                b = 200 + variation;
                break;
            case 3: // Violet
                r = 140 + variation;
                g = 80 + variation;
                b = 200 + variation;
                break;
            case 4: // Lime
                r = 100 + variation;
                g = 200 + variation;
                b = 100 + variation;
                break;
            default: // Soft magenta
                r = 180 + variation;
                g = 100 + variation;
                b = 180 + variation;
                break;
        }

        // Clamp to 90-230 range for visibility
        r = Math.max(90, Math.min(230, r));
        g = Math.max(90, Math.min(230, g));
        b = Math.max(90, Math.min(230, b));

        return app.color(r, g, b, 220);
    }

    /**
     * Get darkened stroke color (75% of fill RGB).
     */
    private int getDarkenedStroke(PApplet app, int fillColor) {
        int r = ((fillColor >> 16) & 0xFF) * 3 / 4;
        int g = ((fillColor >> 8) & 0xFF) * 3 / 4;
        int b = (fillColor & 0xFF) * 3 / 4;
        return app.color(r, g, b, 220);
    }

    /**
     * Compute smooth pulse value (0..1) using cosine breathing.
     */
    private float computePulse(float time) {
        float t = (time % PULSE_PERIOD) / PULSE_PERIOD; // 0..1 linear
        return (float) (0.5 - 0.5 * Math.cos(2.0 * Math.PI * t)); // 0..1 smooth
    }

    // ==================== SWEEP LINE ====================

    @Override
    public void drawSweepLine(PApplet app, float y, float time) {
        // Layer 1: Glow/halo (drawn first as background)
        app.stroke(app.color(SWEEP_GLOW_R, SWEEP_GLOW_G, SWEEP_GLOW_B, SWEEP_GLOW_ALPHA));
        app.strokeWeight(SWEEP_GLOW_WEIGHT);
        app.line(0, y, app.width, y);

        // Layer 2: Shadow (1px offset for depth)
        app.stroke(app.color(SWEEP_SHADOW_R, SWEEP_SHADOW_G, SWEEP_SHADOW_B, SWEEP_SHADOW_ALPHA));
        app.strokeWeight(SWEEP_SHADOW_WEIGHT);
        app.line(0, y + 1, app.width, y + 1);

        // Layer 3: Main/core sweep line (violet, on top)
        app.stroke(app.color(SWEEP_MAIN_R, SWEEP_MAIN_G, SWEEP_MAIN_B, SWEEP_MAIN_ALPHA));
        app.strokeWeight(SWEEP_CORE_WEIGHT);
        app.line(0, y, app.width, y);
    }

    @Override
    public void drawUnseenArea(PApplet app, float sweepY) {
        // Cool dark blue-grey overlay for unseen area below sweep line
        app.noStroke();
        app.fill(app.color(60, 65, 78, 180));
        app.rect(0, sweepY, app.width, app.height - sweepY);
    }

    // ==================== PARABOLAS ====================

    @Override
    public void drawParabola(PApplet app, Path path, boolean highlight, PVector site, float zoom) {
        app.pushStyle();

        // Get multi-hue color for this site
        int baseColor = getMultiHueColor(app, site.x, site.y);
        int r = (baseColor >> 16) & 0xFF;
        int g = (baseColor >> 8) & 0xFF;
        int b = baseColor & 0xFF;

        // Target screen-space thicknesses
        float normalBaseWeight = 0.8f;
        float highlightedBaseWeight = 6.6f; // Ensures ~2.2px at zoom=3.0
        float targetScreenThickness = 2.2f;

        // Compute zoom-compensated weight
        float baseWeight = highlight ? highlightedBaseWeight : normalBaseWeight;
        float compensatedWeight = baseWeight / zoom;

        // Clamp highlighted parabolas to avoid excessive thickness at low zoom
        if (highlight) {
            compensatedWeight = Math.min(compensatedWeight, targetScreenThickness);
        }

        // Glow layer for highlighted parabolas
        if (highlight) {
            app.noFill();
            float glowWeight = compensatedWeight + 2.5f;
            app.stroke(app.color(r, g, b, 60)); // Alpha ~60
            app.strokeWeight(glowWeight);
            app.strokeCap(PApplet.ROUND);
            app.strokeJoin(PApplet.ROUND);
            drawCurveVertexPath(app, path);
        }

        // Main parabola curve
        app.noFill();
        app.stroke(app.color(r, g, b, highlight ? 220 : 60));
        app.strokeWeight(compensatedWeight);
        app.strokeCap(PApplet.ROUND);
        app.strokeJoin(PApplet.ROUND);
        drawCurveVertexPath(app, path);

        app.popStyle();
    }

    /**
     * Draw a smooth curve through the path points.
     * Uses regular vertex with dense sampling (120 points) and rounded caps/joins.
     * Avoids curveVertex oscillation artifacts from concentrated points.
     */
    private void drawCurveVertexPath(PApplet app, Path path) {
        java.util.List<PVector> points = path.getPoints();
        if (points.isEmpty()) return;

        app.beginShape();
        for (PVector p : points) {
            app.vertex(p.x, p.y);
        }
        app.endShape();
    }

    // ==================== BEACH LINE ====================


    @Override
    public void drawBeachArc(PApplet app, Path path, PVector site, boolean highlight, float zoom, float alpha) {

    }

    @Override
    public int beachLineColorForSite(PApplet app, double x, double y) {
        // Get base multi-hue color for this site
        int baseColor = getMultiHueColor(app, x, y);
        int r = (baseColor >> 16) & 0xFF;
        int g = (baseColor >> 8) & 0xFF;
        int b = baseColor & 0xFF;

        // Apply beach line alpha (245 for strong visibility)
        return app.color(r, g, b, 245);
    }

    // ==================== WITNESS ====================

    @Override
    public void drawWitness(PApplet app, PVector pos, float alpha) {

    }

    @Override
    public void drawWitnessSegments(PApplet app, PVector witness, PVector site, float directrixY, float alpha) {

    }

    @Override
    public void drawWitnessDistanceHelpers(PApplet app, PVector witness, PVector site, float directrixY, float alpha) {

    }
}
