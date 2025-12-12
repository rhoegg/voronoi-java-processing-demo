package com.ryanhoegg.voronoi.sandbox.visualizations;

import com.ryanhoegg.voronoi.sandbox.visualizations.theme.ChristmasThemeStyle;
import com.ryanhoegg.voronoi.sandbox.visualizations.theme.ClassicThemeStyle;
import com.ryanhoegg.voronoi.sandbox.visualizations.theme.ThemeStyle;
import processing.core.PApplet;
import processing.core.PVector;

import java.util.Map;

/**
 * ThemeEngine - Theme registry for Voronoi visualizations.
 * Maps Theme enums to ThemeStyle implementations.
 *
 * For most styling (background, sites, sweep line), use ThemeStyle methods.
 * This class only provides:
 * - Theme registry (style(Theme))
 * - Common constants
 * - Special visualization-specific colors (bisectors, regions, etc.)
 */
public class ThemeEngine {

    // ==================== THEME REGISTRY ====================

    private static final Map<Theme, ThemeStyle> STYLES = Map.of(
        Theme.CHRISTMAS, new ChristmasThemeStyle(),
        Theme.STYLE_B_CLASSIC, new ClassicThemeStyle()
    );

    /**
     * Get the ThemeStyle implementation for the given theme.
     */
    public static ThemeStyle style(Theme theme) {
        return STYLES.get(theme);
    }

    // ==================== COMMON CONSTANTS ====================

    public static final float THIN_LINE = 1.0f;
    public static final float NORMAL_LINE = 2.0f;
    public static final float EMPHASIS_LINE = 4.0f;

    // Beach line styling
    public static final float BEACH_LINE_STROKE_WEIGHT = 3.0f;

    // ==================== SPECIAL VISUALIZATION COLORS ====================
    // These are used by specific visualizations for non-standard elements
    // (bisectors, regions, circle events, etc.)
    // TODO: Consider moving these to ThemeStyle in the future

    // ==================== CIRCLE EVENTS ====================

    public static int triangleEdgeColor(PApplet app, Theme theme, int alpha) {
        return switch (theme) {
            case CHRISTMAS -> app.color(150, 230, 200, alpha); // Mint-ish
            case STYLE_B_CLASSIC -> app.color(60, 196, 255, alpha);
        };
    }

    public static int pastelMarkerColor(PApplet app, Theme theme, int alpha) {
        return switch (theme) {
            case CHRISTMAS -> app.color(200, 220, 240, alpha); // Icy white
            case STYLE_B_CLASSIC -> app.color(180, 180, 255, alpha);
        };
    }

    public static int disappearingArcMarkerColor(PApplet app, Theme theme, int alpha) {
        return switch (theme) {
            case CHRISTMAS -> app.color(210, 70, 70, alpha); // Christmas red
            case STYLE_B_CLASSIC -> app.color(255, 142, 110, alpha);
        };
    }

    public static int circleGlowOuterColor(PApplet app, Theme theme, int alpha) {
        return switch (theme) {
            case CHRISTMAS -> app.color(140, 220, 255, alpha); // Icy blue
            case STYLE_B_CLASSIC -> app.color(120, 220, 255, alpha);
        };
    }

    public static int circleGlowInnerColor(PApplet app, Theme theme, int alpha) {
        return app.color(255, 255, 255, alpha); // Pure white for both themes
    }

    public static int starburstStrokeColor(PApplet app, int alpha) {
        return app.color(255, 255, 255, alpha);
    }

    public static int starburstFillColor(PApplet app, Theme theme, int alpha) {
        return switch (theme) {
            case CHRISTMAS -> app.color(235, 200, 100, alpha); // Golden
            case STYLE_B_CLASSIC -> app.color(255, 240, 0, alpha);
        };
    }

    public static int guideLineColor(PApplet app, Theme theme) {
        return switch (theme) {
            case CHRISTMAS -> app.color(160, 200, 210, 150); // Icy blue-grey
            case STYLE_B_CLASSIC -> app.color(180, 180, 200, 150);
        };
    }

    public static int labelColor(PApplet app, Theme theme) {
        return switch (theme) {
            case CHRISTMAS -> app.color(200, 220, 230, 200); // Light icy blue
            case STYLE_B_CLASSIC -> app.color(80, 100, 140, 200);
        };
    }

    public static int circleEventOverlayColor(PApplet app, Theme theme) {
        return switch (theme) {
            case CHRISTMAS -> app.color(5, 15, 20, 50); // Very dark blue-green
            case STYLE_B_CLASSIC -> app.color(40, 45, 55, 40);
        };
    }

    public static int circleFillColor(PApplet app, Theme theme, int alpha) {
        return switch (theme) {
            case CHRISTMAS -> app.color(120, 200, 220, alpha); // Icy cyan
            case STYLE_B_CLASSIC -> app.color(140, 220, 255, alpha);
        };
    }

    public static int vertexDotColor(PApplet app, int alpha) {
        return app.color(255, 255, 255, alpha); // Bright white for both themes
    }

    public static int spokeColor(PApplet app, Theme theme, int alpha) {
        return switch (theme) {
            case CHRISTMAS -> app.color(140, 220, 240, alpha); // Icy blue
            case STYLE_B_CLASSIC -> app.color(100, 220, 255, alpha);
        };
    }

    // ==================== HALF-PLANE VISUALIZATION ====================

    public static int clipLineStroke(PApplet app, Theme theme) {
        return switch (theme) {
            case CHRISTMAS -> app.color(150, 230, 210, 220); // Mint/icy
            case STYLE_B_CLASSIC -> app.color(80, 160, 240, 220);
        };
    }

    public static int discardedHalfPlaneFill(PApplet app, Theme theme) {
        return switch (theme) {
            case CHRISTMAS -> app.color(200, 80, 70, 50); // Dark red
            case STYLE_B_CLASSIC -> app.color(255, 120, 100, 60);
        };
    }

    public static int clippingPolygonFill(PApplet app, Theme theme) {
        return switch (theme) {
            case CHRISTMAS -> app.color(120, 200, 190, 90); // Pale mint
            case STYLE_B_CLASSIC -> app.color(160, 200, 240, 100);
        };
    }

    public static int clippingPolygonStroke(PApplet app, Theme theme) {
        return switch (theme) {
            case CHRISTMAS -> app.color(140, 220, 210, 220); // Bright mint
            case STYLE_B_CLASSIC -> app.color(70, 130, 200, 220);
        };
    }

    public static int voronoiCellFill(PApplet app, Theme theme) {
        return switch (theme) {
            case CHRISTMAS -> app.color(100, 180, 170, 60); // Subtle mint
            case STYLE_B_CLASSIC -> app.color(150, 190, 230, 70);
        };
    }

    public static int voronoiCellStroke(PApplet app, Theme theme) {
        return switch (theme) {
            case CHRISTMAS -> app.color(130, 210, 200, 200); // Mint edge
            case STYLE_B_CLASSIC -> app.color(80, 140, 200, 200);
        };
    }

    public static int voronoiCellFillActive(PApplet app, Theme theme) {
        return switch (theme) {
            case CHRISTMAS -> app.color(140, 220, 210, 110); // Brighter mint
            case STYLE_B_CLASSIC -> app.color(140, 200, 255, 120);
        };
    }
}
