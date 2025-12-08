package com.ryanhoegg.voronoi.sandbox.visualizations;

import com.ryanhoegg.voronoi.core.geometry.Point;
import processing.core.PApplet;
import processing.core.PVector;

/**
 * ThemeEngine - Centralized theming system for Voronoi visualizations.
 * Provides theme-aware color calculations, gradient backgrounds, and visual constants
 * for cohesive, projector-friendly aesthetics.
 *
 * Supports multiple themes:
 * - CHRISTMAS: Dark winter night forest with ornament-colored sites
 * - STYLE_B_CLASSIC: Original high-tech cool color scheme
 */
public class ThemeEngine {

    // ==================== STYLE_B_CLASSIC THEME CONSTANTS ====================

    // Background gradient colors - deepened for better contrast
    private static final int CLASSIC_BG_TOP_R = 228;
    private static final int CLASSIC_BG_TOP_G = 234;
    private static final int CLASSIC_BG_TOP_B = 242;

    private static final int CLASSIC_BG_BOTTOM_R = 182;
    private static final int CLASSIC_BG_BOTTOM_G = 190;
    private static final int CLASSIC_BG_BOTTOM_B = 205;

    // Sweep line colors - enhanced for visibility
    private static final int CLASSIC_SWEEP_MAIN_R = 70;
    private static final int CLASSIC_SWEEP_MAIN_G = 40;
    private static final int CLASSIC_SWEEP_MAIN_B = 180;
    private static final int CLASSIC_SWEEP_MAIN_ALPHA = 230;

    private static final int CLASSIC_SWEEP_GLOW_R = 120;
    private static final int CLASSIC_SWEEP_GLOW_G = 200;
    private static final int CLASSIC_SWEEP_GLOW_B = 255;
    private static final int CLASSIC_SWEEP_GLOW_ALPHA = 80;

    // ==================== CHRISTMAS THEME CONSTANTS ====================

    // Christmas background - dark winter night forest
    private static final int XMAS_BG_TOP_R = 15;
    private static final int XMAS_BG_TOP_G = 45;
    private static final int XMAS_BG_TOP_B = 58;

    private static final int XMAS_BG_BOTTOM_R = 8;
    private static final int XMAS_BG_BOTTOM_G = 25;
    private static final int XMAS_BG_BOTTOM_B = 32;

    // Christmas ornament site colors
    private static final int[][] XMAS_ORNAMENT_PALETTE = {
        {210, 70, 70},      // Warm red
        {235, 190, 90},     // Gold
        {150, 230, 190},    // Soft mint
        {240, 235, 220}     // Warm off-white
    };

    // Christmas sweep line - icy blue glow with golden core
    private static final int XMAS_SWEEP_GLOW_R = 140;
    private static final int XMAS_SWEEP_GLOW_G = 220;
    private static final int XMAS_SWEEP_GLOW_B = 255;
    private static final int XMAS_SWEEP_GLOW_ALPHA = 90;

    private static final int XMAS_SWEEP_CORE_R = 245;
    private static final int XMAS_SWEEP_CORE_G = 220;
    private static final int XMAS_SWEEP_CORE_B = 150;
    private static final int XMAS_SWEEP_CORE_ALPHA = 240;

    // ==================== COMMON CONSTANTS ====================

    // Line weights
    public static final float THIN_LINE = 1.0f;
    public static final float NORMAL_LINE = 2.0f;
    public static final float EMPHASIS_LINE = 4.0f;

    // Parabola styling
    public static final float PARABOLA_STROKE_WEIGHT = 1.2f;
    public static final int PARABOLA_ALPHA = 175;

    // Beach line styling - strengthened
    public static final float BEACH_LINE_STROKE_WEIGHT = 3.0f;
    public static final int BEACH_LINE_ALPHA = 245;

    // Circle event timing thresholds
    public static final float CIRCLE_EVENT_STAGE1_END = 0.4f;
    public static final float CIRCLE_EVENT_STAGE2_END = 1.0f;

    // ==================== THEME-AWARE BACKGROUND METHODS ====================

    /**
     * Get the background top color for the given theme.
     */
    public static int backgroundTop(PApplet app, Theme theme) {
        return switch (theme) {
            case CHRISTMAS -> app.color(XMAS_BG_TOP_R, XMAS_BG_TOP_G, XMAS_BG_TOP_B);
            case STYLE_B_CLASSIC -> app.color(CLASSIC_BG_TOP_R, CLASSIC_BG_TOP_G, CLASSIC_BG_TOP_B);
        };
    }

    /**
     * Get the background bottom color for the given theme.
     */
    public static int backgroundBottom(PApplet app, Theme theme) {
        return switch (theme) {
            case CHRISTMAS -> app.color(XMAS_BG_BOTTOM_R, XMAS_BG_BOTTOM_G, XMAS_BG_BOTTOM_B);
            case STYLE_B_CLASSIC -> app.color(CLASSIC_BG_BOTTOM_R, CLASSIC_BG_BOTTOM_G, CLASSIC_BG_BOTTOM_B);
        };
    }

    /**
     * Draw the gradient background for the given theme.
     * Call this at the beginning of each frame.
     */
    public static void drawGradientBackground(PApplet app, Theme theme) {
        int topColor = backgroundTop(app, theme);
        int bottomColor = backgroundBottom(app, theme);

        // Draw vertical gradient using thin horizontal lines
        for (int y = 0; y < app.height; y++) {
            float inter = (float) y / app.height;
            int c = app.lerpColor(topColor, bottomColor, inter);
            app.stroke(c);
            app.line(0, y, app.width, y);
        }
    }

    /**
     * Get the unseen area overlay color (below sweep line).
     */
    public static int unseenAreaColor(PApplet app, Theme theme) {
        return switch (theme) {
            case CHRISTMAS -> app.color(10, 35, 45, 170); // Deep blue-green-grey
            case STYLE_B_CLASSIC -> app.color(60, 65, 78, 180); // Cool dark blue-grey
        };
    }

    // ==================== SITE COLORING ====================

    /**
     * Get site fill color based on position and theme.
     */
    public static int siteFill(PApplet app, Theme theme, double x, double y) {
        return switch (theme) {
            case CHRISTMAS -> christmasOrnamentColor(app, x, y);
            case STYLE_B_CLASSIC -> classicSiteColor(app, x, y);
        };
    }

    public static int siteFill(PApplet app, Theme theme, PVector site) {
        return siteFill(app, theme, site.x, site.y);
    }

    public static int siteFill(PApplet app, Theme theme, Point site) {
        return siteFill(app, theme, site.x(), site.y());
    }

    /**
     * Get site shadow/halo color for improved contrast.
     */
    public static int siteShadow(PApplet app, Theme theme) {
        return switch (theme) {
            case CHRISTMAS -> app.color(8, 20, 28, 150); // Very dark blue-green
            case STYLE_B_CLASSIC -> app.color(40, 45, 55, 120); // Dark grey-blue
        };
    }

    /**
     * Get site stroke color (optional outline).
     */
    public static int siteStroke(PApplet app, Theme theme, double x, double y) {
        return switch (theme) {
            case CHRISTMAS -> app.color(245, 240, 230, 200); // Warm off-white outline
            case STYLE_B_CLASSIC -> {
                // Slightly darker version of the fill
                int fill = siteFill(app, theme, x, y);
                int r = ((fill >> 16) & 0xFF) * 3 / 4;
                int g = ((fill >> 8) & 0xFF) * 3 / 4;
                int b = (fill & 0xFF) * 3 / 4;
                yield app.color(r, g, b, 220);
            }
        };
    }

    /**
     * Christmas ornament color - picks from a curated palette.
     */
    private static int christmasOrnamentColor(PApplet app, double x, double y) {
        int h = Double.hashCode(x) * 31 + Double.hashCode(y);
        int paletteIndex = Math.abs(h) % XMAS_ORNAMENT_PALETTE.length;
        int[] rgb = XMAS_ORNAMENT_PALETTE[paletteIndex];
        return app.color(rgb[0], rgb[1], rgb[2], 230);
    }

    /**
     * Classic site color - deterministic neon/cool colors.
     * Returns colors in cyan, teal, blue, violet, lime, or soft magenta families.
     */
    private static int classicSiteColor(PApplet app, double x, double y) {
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

    // ==================== SWEEP LINE ====================

    /**
     * Get sweep line glow color.
     */
    public static int sweepLineGlowColor(PApplet app, Theme theme) {
        return switch (theme) {
            case CHRISTMAS -> app.color(XMAS_SWEEP_GLOW_R, XMAS_SWEEP_GLOW_G, XMAS_SWEEP_GLOW_B, XMAS_SWEEP_GLOW_ALPHA);
            case STYLE_B_CLASSIC -> app.color(CLASSIC_SWEEP_GLOW_R, CLASSIC_SWEEP_GLOW_G, CLASSIC_SWEEP_GLOW_B, CLASSIC_SWEEP_GLOW_ALPHA);
        };
    }

    /**
     * Get sweep line core color.
     */
    public static int sweepLineCoreColor(PApplet app, Theme theme) {
        return switch (theme) {
            case CHRISTMAS -> app.color(XMAS_SWEEP_CORE_R, XMAS_SWEEP_CORE_G, XMAS_SWEEP_CORE_B, XMAS_SWEEP_CORE_ALPHA);
            case STYLE_B_CLASSIC -> app.color(CLASSIC_SWEEP_MAIN_R, CLASSIC_SWEEP_MAIN_G, CLASSIC_SWEEP_MAIN_B, CLASSIC_SWEEP_MAIN_ALPHA);
        };
    }

    /**
     * Get sweep line glow weight.
     */
    public static float sweepLineGlowWeight(Theme theme) {
        return switch (theme) {
            case CHRISTMAS -> 7.0f;
            case STYLE_B_CLASSIC -> 7.0f;
        };
    }

    /**
     * Get sweep line core weight.
     */
    public static float sweepLineCoreWeight(Theme theme) {
        return switch (theme) {
            case CHRISTMAS -> 4.0f;
            case STYLE_B_CLASSIC -> 5.0f;
        };
    }

    /**
     * Get sweep line shadow color (used in classic theme).
     */
    public static int sweepLineShadowColor(PApplet app, Theme theme) {
        return switch (theme) {
            case CHRISTMAS -> app.color(0, 0, 0, 0); // No shadow in Christmas theme
            case STYLE_B_CLASSIC -> app.color(40, 30, 80, 140);
        };
    }

    public static float sweepLineShadowWeight() {
        return 2.0f;
    }

    // ==================== PARABOLAS AND BEACH LINE ====================

    /**
     * Get site color with custom alpha for parabolas.
     */
    public static int parabolaColor(PApplet app, Theme theme, double x, double y) {
        int baseColor = siteFill(app, theme, x, y);
        int r = (baseColor >> 16) & 0xFF;
        int g = (baseColor >> 8) & 0xFF;
        int b = baseColor & 0xFF;

        // Adjust alpha based on theme
        int alpha = switch (theme) {
            case CHRISTMAS -> 160; // Slightly lower for Christmas
            case STYLE_B_CLASSIC -> PARABOLA_ALPHA;
        };

        return app.color(r, g, b, alpha);
    }

    public static int parabolaColor(PApplet app, Theme theme, PVector site) {
        return parabolaColor(app, theme, site.x, site.y);
    }

    public static int parabolaColor(PApplet app, Theme theme, Point site) {
        return parabolaColor(app, theme, site.x(), site.y());
    }

    /**
     * Get site color with beach line alpha.
     */
    public static int beachLineColor(PApplet app, Theme theme, double x, double y) {
        int baseColor = siteFill(app, theme, x, y);
        int r = (baseColor >> 16) & 0xFF;
        int g = (baseColor >> 8) & 0xFF;
        int b = baseColor & 0xFF;
        return app.color(r, g, b, BEACH_LINE_ALPHA);
    }

    public static int beachLineColor(PApplet app, Theme theme, Point site) {
        return beachLineColor(app, theme, site.x(), site.y());
    }

    /**
     * Get faint parabola color for background parabolas (PARABOLAS stage).
     */
    public static int parabolaColorFaint(PApplet app, Theme theme, PVector site) {
        int baseColor = siteFill(app, theme, site);
        int r = (baseColor >> 16) & 0xFF;
        int g = (baseColor >> 8) & 0xFF;
        int b = baseColor & 0xFF;
        return app.color(r, g, b, 60);
    }

    /**
     * Get highlighted parabola color for example parabola (PARABOLAS stage).
     */
    public static int parabolaColorHighlight(PApplet app, Theme theme, PVector site) {
        int baseColor = siteFill(app, theme, site);
        int r = (baseColor >> 16) & 0xFF;
        int g = (baseColor >> 8) & 0xFF;
        int b = baseColor & 0xFF;
        return app.color(r, g, b, 220);
    }

    // ==================== CIRCLE EVENTS ====================

    /**
     * Get triangle edge color for circle events (Stage 1).
     */
    public static int triangleEdgeColor(PApplet app, Theme theme, int alpha) {
        return switch (theme) {
            case CHRISTMAS -> app.color(150, 230, 200, alpha); // Mint-ish
            case STYLE_B_CLASSIC -> app.color(60, 196, 255, alpha);
        };
    }

    /**
     * Get pastel marker color for sites A and C in circle events.
     */
    public static int pastelMarkerColor(PApplet app, Theme theme, int alpha) {
        return switch (theme) {
            case CHRISTMAS -> app.color(200, 220, 240, alpha); // Icy white
            case STYLE_B_CLASSIC -> app.color(180, 180, 255, alpha);
        };
    }

    /**
     * Get warm marker color for disappearing arc (site B) in circle events.
     */
    public static int disappearingArcMarkerColor(PApplet app, Theme theme, int alpha) {
        return switch (theme) {
            case CHRISTMAS -> app.color(210, 70, 70, alpha); // Christmas red
            case STYLE_B_CLASSIC -> app.color(255, 142, 110, alpha);
        };
    }

    /**
     * Get circle glow outer halo color (Stage 2).
     */
    public static int circleGlowOuterColor(PApplet app, Theme theme, int alpha) {
        return switch (theme) {
            case CHRISTMAS -> app.color(140, 220, 255, alpha); // Icy blue
            case STYLE_B_CLASSIC -> app.color(120, 220, 255, alpha);
        };
    }

    /**
     * Get circle glow inner ring color (Stage 2).
     */
    public static int circleGlowInnerColor(PApplet app, Theme theme, int alpha) {
        return app.color(255, 255, 255, alpha); // Pure white for both themes
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
    public static int starburstFillColor(PApplet app, Theme theme, int alpha) {
        return switch (theme) {
            case CHRISTMAS -> app.color(235, 200, 100, alpha); // Golden
            case STYLE_B_CLASSIC -> app.color(255, 240, 0, alpha);
        };
    }

    /**
     * Get guide line color (dashed vertical line from site to parabola vertex).
     */
    public static int guideLineColor(PApplet app, Theme theme) {
        return switch (theme) {
            case CHRISTMAS -> app.color(160, 200, 210, 150); // Icy blue-grey
            case STYLE_B_CLASSIC -> app.color(180, 180, 200, 150);
        };
    }

    /**
     * Get label text color for annotations.
     */
    public static int labelColor(PApplet app, Theme theme) {
        return switch (theme) {
            case CHRISTMAS -> app.color(200, 220, 230, 200); // Light icy blue
            case STYLE_B_CLASSIC -> app.color(80, 100, 140, 200);
        };
    }

    /**
     * Get circle event overlay color (full-screen subtle darkening).
     */
    public static int circleEventOverlayColor(PApplet app, Theme theme) {
        return switch (theme) {
            case CHRISTMAS -> app.color(5, 15, 20, 50); // Very dark blue-green
            case STYLE_B_CLASSIC -> app.color(40, 45, 55, 40);
        };
    }

    /**
     * Get circle fill color (pale cyan for "empty circle" phase).
     */
    public static int circleFillColor(PApplet app, Theme theme, int alpha) {
        return switch (theme) {
            case CHRISTMAS -> app.color(120, 200, 220, alpha); // Icy cyan
            case STYLE_B_CLASSIC -> app.color(140, 220, 255, alpha);
        };
    }

    /**
     * Get vertex dot color (Voronoi vertex in Phase 3).
     */
    public static int vertexDotColor(PApplet app, int alpha) {
        return app.color(255, 255, 255, alpha); // Bright white for both themes
    }

    /**
     * Get spoke color (edges radiating from vertex in Phase 3).
     */
    public static int spokeColor(PApplet app, Theme theme, int alpha) {
        return switch (theme) {
            case CHRISTMAS -> app.color(140, 220, 240, alpha); // Icy blue
            case STYLE_B_CLASSIC -> app.color(100, 220, 255, alpha);
        };
    }

    // ==================== HALF-PLANE VISUALIZATION ====================

    /**
     * Get focus site color (bright, stands out from regular sites).
     */
    public static int focusSiteColor(PApplet app, Theme theme) {
        return switch (theme) {
            case CHRISTMAS -> app.color(235, 190, 90, 240); // Gold ornament
            case STYLE_B_CLASSIC -> app.color(255, 220, 60, 240);
        };
    }

    /**
     * Get focus site halo color (subtle pulse effect).
     */
    public static int focusSiteHaloColor(PApplet app, Theme theme, int alpha) {
        return switch (theme) {
            case CHRISTMAS -> app.color(245, 210, 120, alpha); // Golden glow
            case STYLE_B_CLASSIC -> app.color(255, 230, 100, alpha);
        };
    }

    /**
     * Get bisector/clip line stroke color (strong, high-contrast).
     */
    public static int clipLineStroke(PApplet app, Theme theme) {
        return switch (theme) {
            case CHRISTMAS -> app.color(150, 230, 210, 220); // Mint/icy
            case STYLE_B_CLASSIC -> app.color(80, 160, 240, 220);
        };
    }

    /**
     * Get discarded half-plane fill (translucent to show what's being clipped away).
     */
    public static int discardedHalfPlaneFill(PApplet app, Theme theme) {
        return switch (theme) {
            case CHRISTMAS -> app.color(200, 80, 70, 50); // Dark red
            case STYLE_B_CLASSIC -> app.color(255, 120, 100, 60);
        };
    }

    /**
     * Get current clipping polygon fill.
     */
    public static int clippingPolygonFill(PApplet app, Theme theme) {
        return switch (theme) {
            case CHRISTMAS -> app.color(120, 200, 190, 90); // Pale mint
            case STYLE_B_CLASSIC -> app.color(160, 200, 240, 100);
        };
    }

    /**
     * Get current clipping polygon stroke.
     */
    public static int clippingPolygonStroke(PApplet app, Theme theme) {
        return switch (theme) {
            case CHRISTMAS -> app.color(140, 220, 210, 220); // Bright mint
            case STYLE_B_CLASSIC -> app.color(70, 130, 200, 220);
        };
    }

    /**
     * Get Voronoi cell fill for completed cells.
     */
    public static int voronoiCellFill(PApplet app, Theme theme) {
        return switch (theme) {
            case CHRISTMAS -> app.color(100, 180, 170, 60); // Subtle mint
            case STYLE_B_CLASSIC -> app.color(150, 190, 230, 70);
        };
    }

    /**
     * Get Voronoi cell stroke for completed cells.
     */
    public static int voronoiCellStroke(PApplet app, Theme theme) {
        return switch (theme) {
            case CHRISTMAS -> app.color(130, 210, 200, 200); // Mint edge
            case STYLE_B_CLASSIC -> app.color(80, 140, 200, 200);
        };
    }

    /**
     * Get active/highlighted Voronoi cell fill (stronger emphasis).
     */
    public static int voronoiCellFillActive(PApplet app, Theme theme) {
        return switch (theme) {
            case CHRISTMAS -> app.color(140, 220, 210, 110); // Brighter mint
            case STYLE_B_CLASSIC -> app.color(140, 200, 255, 120);
        };
    }

    /**
     * Get highlighted neighbor color (warm accent).
     */
    public static int highlightedNeighborColor(PApplet app, Theme theme) {
        return switch (theme) {
            case CHRISTMAS -> app.color(210, 70, 70, 230); // Christmas red
            case STYLE_B_CLASSIC -> app.color(255, 100, 80, 220);
        };
    }

    /**
     * Get highlighted neighbor fill with transparency.
     */
    public static int highlightedNeighborFill(PApplet app, Theme theme) {
        return switch (theme) {
            case CHRISTMAS -> app.color(210, 70, 70, 50); // Transparent red
            case STYLE_B_CLASSIC -> app.color(255, 100, 80, 60);
        };
    }

    /**
     * Get bisector edge color.
     */
    public static int bisectorEdgeColor(PApplet app, Theme theme) {
        return switch (theme) {
            case CHRISTMAS -> app.color(130, 200, 190, 200); // Mint-teal
            case STYLE_B_CLASSIC -> app.color(100, 120, 150, 200);
        };
    }

    /**
     * Get highlighted region fill.
     */
    public static int highlightedRegionFill(PApplet app, Theme theme) {
        return switch (theme) {
            case CHRISTMAS -> app.color(140, 210, 200, 70); // Pale mint
            case STYLE_B_CLASSIC -> app.color(160, 200, 240, 80);
        };
    }

    /**
     * Get region stroke color.
     */
    public static int regionStrokeColor(PApplet app, Theme theme) {
        return switch (theme) {
            case CHRISTMAS -> app.color(130, 210, 200, 200); // Mint
            case STYLE_B_CLASSIC -> app.color(80, 140, 200, 200);
        };
    }

    /**
     * Get bounding box stroke.
     */
    public static int boundingBoxStroke(PApplet app, Theme theme) {
        return switch (theme) {
            case CHRISTMAS -> app.color(100, 150, 160, 100); // Subtle dark teal
            case STYLE_B_CLASSIC -> app.color(120, 140, 170, 120);
        };
    }

    /**
     * Get dark grey color (replacement for pure black).
     */
    public static int darkGrey(PApplet app) {
        return app.color(51, 51, 51);
    }

    /**
     * Get circle event contrast overlay (optional darkening during events).
     */
    public static int circleEventOverlay(PApplet app) {
        return app.color(0, 0, 0, 30);
    }
}
