package com.ryanhoegg.voronoi.sandbox.visualizations;

import com.ryanhoegg.voronoi.core.geometry.Point;
import processing.core.PApplet;
import processing.core.PVector;

/**
 * Style B - Cool High-Tech visual styling for Voronoi visualizations.
 * Provides centralized color calculations, gradient backgrounds, and visual constants
 * for a cohesive, projector-friendly high-tech aesthetic.
 */
public class StyleB {

    // Background gradient colors - deepened for better contrast
    private static final int BG_TOP_R = 228;
    private static final int BG_TOP_G = 234;
    private static final int BG_TOP_B = 242;

    private static final int BG_BOTTOM_R = 182;
    private static final int BG_BOTTOM_G = 190;
    private static final int BG_BOTTOM_B = 205;

    // Base colors
    private static final int DARK_GREY_R = 51;
    private static final int DARK_GREY_G = 51;
    private static final int DARK_GREY_B = 51;

    // Sweep line colors - enhanced for visibility
    private static final int SWEEP_LINE_MAIN_R = 70;
    private static final int SWEEP_LINE_MAIN_G = 40;
    private static final int SWEEP_LINE_MAIN_B = 180;
    private static final int SWEEP_LINE_MAIN_ALPHA = 230;

    private static final int SWEEP_LINE_GLOW_R = 120;
    private static final int SWEEP_LINE_GLOW_G = 200;
    private static final int SWEEP_LINE_GLOW_B = 255;
    private static final int SWEEP_LINE_GLOW_ALPHA = 80;

    private static final int SWEEP_LINE_SHADOW_R = 40;
    private static final int SWEEP_LINE_SHADOW_G = 30;
    private static final int SWEEP_LINE_SHADOW_B = 80;
    private static final int SWEEP_LINE_SHADOW_ALPHA = 140;

    // Line weights
    public static final float THIN_LINE = 1.0f;
    public static final float NORMAL_LINE = 2.0f;
    public static final float EMPHASIS_LINE = 4.0f;
    public static final float SWEEP_LINE_WEIGHT_MAIN = 5.0f;
    public static final float SWEEP_LINE_WEIGHT_GLOW = 7.0f;
    public static final float SWEEP_LINE_WEIGHT_SHADOW = 2.0f;

    // Parabola styling
    public static final float PARABOLA_STROKE_WEIGHT = 1.2f;
    public static final int PARABOLA_ALPHA = 175;

    // Beach line styling - strengthened
    public static final float BEACH_LINE_STROKE_WEIGHT = 3.0f;
    public static final int BEACH_LINE_ALPHA = 245;

    // Circle event colors
    private static final int TRIANGLE_R = 60;
    private static final int TRIANGLE_G = 196;
    private static final int TRIANGLE_B = 255;

    private static final int DISAPPEARING_ARC_R = 255;
    private static final int DISAPPEARING_ARC_G = 142;
    private static final int DISAPPEARING_ARC_B = 110;

    // Circle event timing thresholds
    public static final float CIRCLE_EVENT_STAGE1_END = 0.4f;
    public static final float CIRCLE_EVENT_STAGE2_END = 1.0f;

    /**
     * Draw the gradient background.
     * Call this at the beginning of each frame.
     */
    public static void drawGradientBackground(PApplet app) {
        int topColor = app.color(BG_TOP_R, BG_TOP_G, BG_TOP_B);
        int bottomColor = app.color(BG_BOTTOM_R, BG_BOTTOM_G, BG_BOTTOM_B);

        // Draw vertical gradient using thin horizontal lines
        for (int y = 0; y < app.height; y++) {
            float inter = (float) y / app.height;
            int c = app.lerpColor(topColor, bottomColor, inter);
            app.stroke(c);
            app.line(0, y, app.width, y);
        }
    }

    /**
     * Get a deterministic neon/cool color for a site based on its coordinates.
     * Returns colors in cyan, teal, blue, violet, lime, or soft magenta families.
     */
    public static int siteColor(PApplet app, double x, double y) {
        // Deterministic hash from coordinates
        int h = Double.hashCode(x) * 31 + Double.hashCode(y);

        // Map to a cool color family
        // We want cyan, teal, blue, violet, lime, or soft magenta
        // RGB values should be between ~90 and 230 for visibility

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
     * Get site color from PVector coordinates.
     */
    public static int siteColor(PApplet app, PVector site) {
        return siteColor(app, site.x, site.y);
    }

    /**
     * Get site color from Point coordinates.
     */
    public static int siteColor(PApplet app, Point site) {
        return siteColor(app, site.x(), site.y());
    }

    /**
     * Get the dark grey color (replacement for pure black).
     */
    public static int darkGrey(PApplet app) {
        return app.color(DARK_GREY_R, DARK_GREY_G, DARK_GREY_B);
    }

    /**
     * Get the sweep line main color (deep violet).
     */
    public static int sweepLineMainColor(PApplet app) {
        return app.color(SWEEP_LINE_MAIN_R, SWEEP_LINE_MAIN_G, SWEEP_LINE_MAIN_B, SWEEP_LINE_MAIN_ALPHA);
    }

    /**
     * Get the sweep line glow/halo color (bright cyan-blue).
     */
    public static int sweepLineGlowColor(PApplet app) {
        return app.color(SWEEP_LINE_GLOW_R, SWEEP_LINE_GLOW_G, SWEEP_LINE_GLOW_B, SWEEP_LINE_GLOW_ALPHA);
    }

    /**
     * Get the sweep line shadow color (darker violet).
     */
    public static int sweepLineShadowColor(PApplet app) {
        return app.color(SWEEP_LINE_SHADOW_R, SWEEP_LINE_SHADOW_G, SWEEP_LINE_SHADOW_B, SWEEP_LINE_SHADOW_ALPHA);
    }

    /**
     * Get site color with custom alpha for parabolas.
     */
    public static int parabolaColor(PApplet app, double x, double y) {
        int baseColor = siteColor(app, x, y);
        // Extract RGB and apply parabola alpha
        int r = (baseColor >> 16) & 0xFF;
        int g = (baseColor >> 8) & 0xFF;
        int b = baseColor & 0xFF;
        return app.color(r, g, b, PARABOLA_ALPHA);
    }

    public static int parabolaColor(PApplet app, PVector site) {
        return parabolaColor(app, site.x, site.y);
    }

    public static int parabolaColor(PApplet app, Point site) {
        return parabolaColor(app, site.x(), site.y());
    }

    /**
     * Get site color with beach line alpha.
     */
    public static int beachLineColor(PApplet app, double x, double y) {
        int baseColor = siteColor(app, x, y);
        int r = (baseColor >> 16) & 0xFF;
        int g = (baseColor >> 8) & 0xFF;
        int b = baseColor & 0xFF;
        return app.color(r, g, b, BEACH_LINE_ALPHA);
    }

    public static int beachLineColor(PApplet app, Point site) {
        return beachLineColor(app, site.x(), site.y());
    }

    /**
     * Get triangle edge color for circle events (Stage 1).
     */
    public static int triangleEdgeColor(PApplet app, int alpha) {
        return app.color(TRIANGLE_R, TRIANGLE_G, TRIANGLE_B, alpha);
    }

    /**
     * Get pastel marker color for sites A and C in circle events.
     */
    public static int pastelMarkerColor(PApplet app, int alpha) {
        return app.color(180, 180, 255, alpha);
    }

    /**
     * Get warm marker color for disappearing arc (site B) in circle events.
     */
    public static int disappearingArcMarkerColor(PApplet app, int alpha) {
        return app.color(DISAPPEARING_ARC_R, DISAPPEARING_ARC_G, DISAPPEARING_ARC_B, alpha);
    }

    /**
     * Get circle glow outer halo color (Stage 2) - enhanced visibility.
     */
    public static int circleGlowOuterColor(PApplet app, int alpha) {
        return app.color(120, 220, 255, alpha);
    }

    /**
     * Get circle glow inner ring color (Stage 2) - pure white.
     */
    public static int circleGlowInnerColor(PApplet app, int alpha) {
        return app.color(255, 255, 255, alpha);
    }

    /**
     * Get starburst stroke color (Stage 3).
     */
    public static int starburstStrokeColor(PApplet app, int alpha) {
        return app.color(255, 255, 255, alpha);
    }

    /**
     * Get starburst fill color (Stage 3).
     */
    public static int starburstFillColor(PApplet app, int alpha) {
        return app.color(255, 240, 0, alpha);
    }

    /**
     * Get the unseen area overlay color (below sweep line) - cool dark blue-grey.
     */
    public static int unseenAreaColor(PApplet app) {
        return app.color(60, 65, 78, 180);
    }

    /**
     * Get cool grey color for half-plane bisector edges.
     */
    public static int bisectorEdgeColor(PApplet app) {
        return app.color(100, 120, 150, 200);
    }

    /**
     * Get cool blue fill for highlighted half-plane regions.
     */
    public static int highlightedRegionFill(PApplet app) {
        return app.color(160, 200, 240, 80);
    }

    /**
     * Get cool blue stroke for half-plane regions.
     */
    public static int regionStrokeColor(PApplet app) {
        return app.color(80, 140, 200, 200);
    }

    /**
     * Get highlighted neighbor color (warm accent for SingleCellHalfPlaneClip).
     */
    public static int highlightedNeighborColor(PApplet app) {
        return app.color(255, 100, 80, 220);
    }

    /**
     * Get highlighted neighbor fill with transparency.
     */
    public static int highlightedNeighborFill(PApplet app) {
        return app.color(255, 100, 80, 60);
    }

    /**
     * Get the circle event contrast overlay (optional darkening during events).
     */
    public static int circleEventOverlay(PApplet app) {
        return app.color(0, 0, 0, 30);
    }

    // ==================== TEACHING MODE ENHANCEMENTS ====================

    /**
     * Get faint parabola color for background parabolas (PARABOLAS stage).
     */
    public static int parabolaColorFaint(PApplet app, PVector site) {
        int baseColor = siteColor(app, site);
        int r = (baseColor >> 16) & 0xFF;
        int g = (baseColor >> 8) & 0xFF;
        int b = baseColor & 0xFF;
        return app.color(r, g, b, 60); // Very low alpha for background
    }

    /**
     * Get highlighted parabola color for example parabola (PARABOLAS stage).
     */
    public static int parabolaColorHighlight(PApplet app, PVector site) {
        int baseColor = siteColor(app, site);
        int r = (baseColor >> 16) & 0xFF;
        int g = (baseColor >> 8) & 0xFF;
        int b = baseColor & 0xFF;
        return app.color(r, g, b, 220); // High alpha for emphasis
    }

    /**
     * Get guide line color (dashed vertical line from site to parabola vertex).
     */
    public static int guideLineColor(PApplet app) {
        return app.color(180, 180, 200, 150); // Subtle cool grey
    }

    /**
     * Get label text color for annotations.
     */
    public static int labelColor(PApplet app) {
        return app.color(80, 100, 140, 200); // Cool dark blue-grey
    }

    /**
     * Get circle event overlay color (full-screen subtle darkening).
     */
    public static int circleEventOverlayColor(PApplet app) {
        return app.color(40, 45, 55, 40); // Very subtle cool dark overlay
    }

    /**
     * Get circle fill color (pale cyan for "empty circle" phase).
     */
    public static int circleFillColor(PApplet app, int alpha) {
        return app.color(140, 220, 255, alpha); // Pale cyan
    }

    /**
     * Get vertex dot color (Voronoi vertex in Phase 3).
     */
    public static int vertexDotColor(PApplet app, int alpha) {
        return app.color(255, 255, 255, alpha); // Bright white
    }

    /**
     * Get spoke color (edges radiating from vertex in Phase 3).
     */
    public static int spokeColor(PApplet app, int alpha) {
        return app.color(100, 220, 255, alpha); // Bright cyan
    }

    // ==================== HALF-PLANE VISUALIZATION ENHANCEMENTS ====================

    /**
     * Get focus site color (bright, stands out from regular sites).
     */
    public static int focusSiteColor(PApplet app) {
        return app.color(255, 220, 60, 240); // Bright yellow-gold
    }

    /**
     * Get focus site halo color (subtle pulse effect).
     */
    public static int focusSiteHaloColor(PApplet app, int alpha) {
        return app.color(255, 230, 100, alpha); // Soft golden glow
    }

    /**
     * Get bisector/clip line stroke color (strong, high-contrast).
     */
    public static int clipLineStroke(PApplet app) {
        return app.color(80, 160, 240, 220); // Strong cool blue
    }

    /**
     * Get discarded half-plane fill (warm translucent to show what's being clipped away).
     */
    public static int discardedHalfPlaneFill(PApplet app) {
        return app.color(255, 120, 100, 60); // Soft warm pink/coral
    }

    /**
     * Get current clipping polygon fill (cool pale blue).
     */
    public static int clippingPolygonFill(PApplet app) {
        return app.color(160, 200, 240, 100); // Pale cool blue
    }

    /**
     * Get current clipping polygon stroke (darker blue outline).
     */
    public static int clippingPolygonStroke(PApplet app) {
        return app.color(70, 130, 200, 220); // Darker cool blue
    }

    /**
     * Get Voronoi cell fill for completed cells (subtle cool tint).
     */
    public static int voronoiCellFill(PApplet app) {
        return app.color(150, 190, 230, 70); // Very subtle cool blue
    }

    /**
     * Get Voronoi cell stroke for completed cells (crisp edge).
     */
    public static int voronoiCellStroke(PApplet app) {
        return app.color(80, 140, 200, 200); // Cool blue edge
    }

    /**
     * Get active/highlighted Voronoi cell fill (stronger emphasis).
     */
    public static int voronoiCellFillActive(PApplet app) {
        return app.color(140, 200, 255, 120); // Brighter cool blue
    }

    /**
     * Get bounding box stroke (subtle, doesn't compete with cell edges).
     */
    public static int boundingBoxStroke(PApplet app) {
        return app.color(120, 140, 170, 120); // Subtle cool grey-blue
    }
}
