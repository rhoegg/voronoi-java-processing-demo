package com.ryanhoegg.voronoi.sandbox.visualizations.theme;

import com.ryanhoegg.voronoi.sandbox.Path;
import processing.core.PApplet;
import processing.core.PVector;

/**
 * ChristmasThemeStyle - Dark winter night forest theme with ornament-colored sites.
 * Features:
 * - Dark blue-green background gradient
 * - Ornament palette (red, gold, mint, off-white)
 * - Icy blue sweep line glow with golden core
 * - Smooth breathing pulse (~3 second period)
 */
public class ChristmasThemeStyle implements ThemeStyle {

    // ==================== BACKGROUND CONSTANTS ====================
    private static final int BG_TOP_R = 15;
    private static final int BG_TOP_G = 45;
    private static final int BG_TOP_B = 58;

    private static final int BG_BOTTOM_R = 8;
    private static final int BG_BOTTOM_G = 25;
    private static final int BG_BOTTOM_B = 32;

    // ==================== SITE CONSTANTS ====================
    private static final float SITE_SIZE = 6.5f;
    private static final float HIGHLIGHTED_SITE_SIZE = 15f;

    // Ornament palette
    private static final int[][] ORNAMENT_PALETTE = {
        {210, 70, 70},      // Warm red
        {235, 190, 90},     // Gold
        {150, 230, 190},    // Soft mint
        {240, 235, 220}     // Warm off-white
    };

    // ==================== SWEEP LINE CONSTANTS ====================
    private static final int SWEEP_GLOW_R = 140;
    private static final int SWEEP_GLOW_G = 220;
    private static final int SWEEP_GLOW_B = 255;
    private static final int SWEEP_GLOW_ALPHA = 90;

    private static final int SWEEP_CORE_R = 245;
    private static final int SWEEP_CORE_G = 220;
    private static final int SWEEP_CORE_B = 150;
    private static final int SWEEP_CORE_ALPHA = 240;

    private static final float SWEEP_GLOW_WEIGHT = 7.0f;
    private static final float SWEEP_CORE_WEIGHT = 4.0f;

    // ==================== PULSE CONSTANTS ====================
    private static final float PULSE_PERIOD = 3.0f; // seconds
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
        app.pushStyle();
        // Get base size from theme
        float baseSize = highlighted ? HIGHLIGHTED_SITE_SIZE : SITE_SIZE;

        // Compute pulse if highlighted
        float sizeMultiplier = 1.0f;
        float glowAlpha = 0f;

        if (highlighted) {
            float pulse = computePulse(time); // 0..1
            sizeMultiplier = PULSE_SIZE_MIN + pulse * (PULSE_SIZE_MAX - PULSE_SIZE_MIN);
            glowAlpha = pulse * PULSE_GLOW_ALPHA_MAX;
        }

        float siteSize = baseSize * sizeMultiplier;

        // Get ornament color for this site
        int siteFillColor = getOrnamentColor(app, pos.x, pos.y);

        // Extract RGB for manipulation
        int fillR = (siteFillColor >> 16) & 0xFF;
        int fillG = (siteFillColor >> 8) & 0xFF;
        int fillB = siteFillColor & 0xFF;

        // LAYER 1: Warm emissive glow (highlighted ornaments only)
        // Uses golden/warm off-white color to simulate light emission
        // Layered with cubic falloff for soft, diffuse appearance
        if (glowAlpha > 1f && highlighted) {
            app.noStroke();
            int glowLayers = 8;

            // Warm glow color (golden off-white, not pure ornament color)
            int glowR = 255;
            int glowG = 240;
            int glowB = 210;

            // Blend ornament color into glow (30% ornament tint)
            glowR = (int) (glowR * 0.7f + fillR * 0.3f);
            glowG = (int) (glowG * 0.7f + fillG * 0.3f);
            glowB = (int) (glowB * 0.7f + fillB * 0.3f);

            for (int i = 0; i < glowLayers; i++) {
                float t = i / (float) (glowLayers - 1); // 0..1
                // Cubic falloff for softer edges
                float falloff = (1f - t) * (1f - t) * (1f - t);
                float size = siteSize + 8f + t * 24f; // Larger, more diffuse
                int a = (int) (glowAlpha * falloff * 1.2f);
                app.fill(app.color(glowR, glowG, glowB, a));
                app.ellipse(pos.x, pos.y, size, size);
            }
        }

        // LAYER 2: Soft shadow (offset slightly down-right)
        // Layered shadow with quadratic falloff for depth
        float shadowCore = siteSize * (highlighted ? 1.20f : 1.12f);
        float spread = siteSize * (highlighted ? 1.10f : 0.85f);
        float off = siteSize * 0.10f;

        app.noStroke();
        drawSoftShadow(app, pos.x + off, pos.y + off, shadowCore, spread, 8, 20, 28, 120);

        // LAYER 3a: Rim-light (highlighted sites only)
        // Subtle bright edge behind main fill for soft definition without harsh outline
        if (highlighted) {
            float rimSize = siteSize * 1.08f; // Slightly larger than main ornament
            // Warm rim light (golden-white edge glow)
            app.noStroke();
            app.fill(app.color(255, 245, 220, 60)); // Soft golden-white with low alpha
            app.ellipse(pos.x, pos.y, rimSize, rimSize);
        }

        // LAYER 3b: Main ornament body (fill, no stroke for clean glossy look)
        app.noStroke(); // Remove cartoony outline
        int siteAlpha = highlighted ? 240 : 220;
        app.fill(app.color(fillR, fillG, fillB, siteAlpha));
        app.ellipse(pos.x, pos.y, siteSize, siteSize);

        // LAYER 4: Specular highlight (glossy reflection)
        // Two-layer highlight: outer soft + inner bright "hot spot"
        // Positioned top-left to simulate overhead light source
        drawSpecularHighlight(app, pos.x, pos.y, siteSize);

        app.popStyle();
    }

    /**
     * Draw specular highlight to simulate glossy ornament surface.
     * Two layered ellipses: soft outer glow + bright inner hot spot.
     * Positioned at top-left to suggest overhead/angled light.
     */
    private void drawSpecularHighlight(PApplet app, float cx, float cy, float siteSize) {
        app.noStroke();

        // Highlight offset: top-left at ~30% of radius
        float offsetX = -siteSize * 0.20f;
        float offsetY = -siteSize * 0.22f;

        float hx = cx + offsetX;
        float hy = cy + offsetY;

        // Outer soft highlight (larger, fainter)
        float outerSize = siteSize * 0.45f;
        app.fill(app.color(255, 255, 255, 35));
        app.ellipse(hx, hy, outerSize, outerSize);

        // Inner bright hot spot (smaller, brighter)
        float innerSize = siteSize * 0.22f;
        app.fill(app.color(255, 255, 255, 85));
        app.ellipse(hx, hy, innerSize, innerSize);

        // Tiny sparkle (optional, very small)
        float sparkleSize = siteSize * 0.10f;
        app.fill(app.color(255, 255, 245, 120));
        app.ellipse(hx - siteSize * 0.05f, hy - siteSize * 0.05f, sparkleSize, sparkleSize);
    }

    /**
     * Get ornament color based on position (deterministic hash).
     */
    private int getOrnamentColor(PApplet app, double x, double y) {
        int h = Double.hashCode(x) * 31 + Double.hashCode(y);
        int paletteIndex = Math.abs(h) % ORNAMENT_PALETTE.length;
        int[] rgb = ORNAMENT_PALETTE[paletteIndex];
        return app.color(rgb[0], rgb[1], rgb[2], 230);
    }

    /**
     * Compute smooth pulse value (0..1) using cosine breathing.
     * Period: ~3 seconds, shape: 0.5 - 0.5*cos(2π*t)
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

        // Layer 2: Main/core sweep line (golden, on top)
        app.stroke(app.color(SWEEP_CORE_R, SWEEP_CORE_G, SWEEP_CORE_B, SWEEP_CORE_ALPHA));
        app.strokeWeight(SWEEP_CORE_WEIGHT);
        app.line(0, y, app.width, y);
    }

    @Override
    public void drawUnseenArea(PApplet app, float sweepY) {
        // Dark blue-green overlay for unseen area below sweep line
        app.noStroke();
        app.fill(app.color(10, 35, 45, 170));
        app.rect(0, sweepY, app.width, app.height - sweepY);
    }

    // ==================== PARABOLAS ====================

    @Override
    public void drawParabola(PApplet app, Path path, boolean highlight, PVector site) {
        app.pushStyle();
        // Get site color from first point (approximate)
        PVector firstPoint = path.getPoints().isEmpty() ? new PVector(0, 0) : path.getPoints().get(0);
        int baseColor = getOrnamentColor(app, site.x, site.y);

        int r = (baseColor >> 16) & 0xFF;
        int g = (baseColor >> 8) & 0xFF;
        int b = baseColor & 0xFF;

        if (highlight) {
            app.stroke(app.color(r, g, b, 220));
            app.strokeWeight(2.2f);
        } else {
            app.stroke(app.color(r, g, b, 60));
            app.strokeWeight(0.8f);
        }

        app.noFill();
        app.beginShape();
        for (PVector p : path.getPoints()) {
            app.vertex(p.x, p.y);
        }
        app.endShape();
        app.popStyle();
    }

    private void drawSoftShadow(PApplet app, float cx, float cy, float coreDiameter, float spread, int r, int g, int b, int alpha) {
        int layers = 6;
        for (int i = 0; i < layers; i++) {
            float t = i / (float)(layers - 1);
            float size = coreDiameter + t * spread;
            int a = (int)(alpha * (1f - t) * (1f - t));
            app.fill(app.color(r, g, b, a));
            app.ellipse(cx, cy, size, size);
        }
    }

    // ==================== BEACH LINE ====================

    @Override
    public int beachLineColorForSite(PApplet app, double x, double y) {
        // Get base ornament color for this site
        int baseColor = getOrnamentColor(app, x, y);
        int r = (baseColor >> 16) & 0xFF;
        int g = (baseColor >> 8) & 0xFF;
        int b = baseColor & 0xFF;

        // Apply beach line alpha (245 for strong visibility)
        return app.color(r, g, b, 245);
    }
}
