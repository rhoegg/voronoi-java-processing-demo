package com.ryanhoegg.voronoi.sandbox.visualizations.theme;

import com.ryanhoegg.voronoi.sandbox.Path;
import processing.core.PApplet;
import processing.core.PVector;

/**
 * ThemeStyle defines the visual appearance and behavior of a theme.
 * Each theme (CHRISTMAS, CLASSIC, etc.) implements this interface to control:
 * - Background gradients and colors
 * - Site rendering (normal and highlighted, with optional pulsing)
 * - Sweep line appearance
 * - Unseen area shading
 * - Parabola styling
 *
 * Themes are responsible for all stylistic decisions including:
 * - Colors, sizes, stroke weights
 * - Pulse shaping (interpreting time parameter)
 * - Layering effects (glows, shadows, etc.)
 */
public interface ThemeStyle {

    // ==================== BACKGROUND ====================

    /**
     * Get the top color of the background gradient.
     *
     * @param app PApplet for color creation
     * @return Color int (ARGB)
     */
    int backgroundTop(PApplet app);

    /**
     * Get the bottom color of the background gradient.
     *
     * @param app PApplet for color creation
     * @return Color int (ARGB)
     */
    int backgroundBottom(PApplet app);

    /**
     * Draw the background gradient for this theme.
     *
     * @param app PApplet for drawing
     */
    void drawBackground(PApplet app);

    // ==================== SITES ====================

    /**
     * Draw a site (Voronoi generator point) with complete theme-specific styling.
     * Theme controls size, colors, shadows, glows, and highlighting.
     *
     * @param app PApplet for drawing
     * @param pos Site position in current coordinate system
     * @param highlighted Whether this site should be emphasized (e.g., focus site)
     * @param time Running time in seconds - theme shapes this for pulsing/animation
     */
    void drawSite(PApplet app, PVector pos, boolean highlighted, float time);

    // ==================== SWEEP LINE ====================

    /**
     * Draw the sweep line at the given y-coordinate.
     * Theme controls layering (glow, core, shadow), colors, and weights.
     *
     * @param app PApplet for drawing
     * @param y Y-coordinate of the sweep line
     * @param time Running time in seconds (for potential animation effects)
     */
    void drawSweepLine(PApplet app, float y, float time);

    /**
     * Draw the unseen/swept area (typically below the sweep line).
     * Separate from drawSweepLine for flexibility in different visualizations.
     *
     * @param app PApplet for drawing
     * @param sweepY Y-coordinate of the sweep line
     */
    void drawUnseenArea(PApplet app, float sweepY);

    // ==================== PARABOLAS ====================

    /**
     * Draw a parabola with theme-specific stroke styling.
     * Theme controls stroke weight, layering, and colors.
     * Stroke weight should be compensated by zoom to maintain constant screen-space thickness.
     *
     * @param app PApplet for drawing
     * @param path The parabola path to draw
     * @param highlight Whether this parabola should be emphasized
     * @param site The parabola's focus site (for color determination)
     * @param zoom Current camera zoom level (1.0 = no zoom, higher = zoomed in)
     */
    void drawParabola(PApplet app, Path path, boolean highlight, PVector site, float zoom);

    // ==================== BEACH LINE ====================

    /**
     * Get beach line color for a site (used in FortuneSweepLine BEACH_LINE stage).
     * Theme determines the color based on site position and applies appropriate alpha.
     *
     * @param app PApplet for color creation
     * @param x Site x-coordinate
     * @param y Site y-coordinate
     * @return Color int (ARGB) for beach line segment
     */
    int beachLineColorForSite(PApplet app, double x, double y);

    void drawBeachArc(PApplet app, Path path, PVector site, boolean highlight, float zoom, float alpha);

    // ==================== WITNESS ====================
    void drawWitness(PApplet app, PVector pos, float alpha);
    void drawWitnessSegments(PApplet app, PVector witness, PVector site, float directrixY, float alpha);
    void drawWitnessDistanceHelpers(PApplet app, PVector witness, PVector site, float directrixY, float alpha);
}
