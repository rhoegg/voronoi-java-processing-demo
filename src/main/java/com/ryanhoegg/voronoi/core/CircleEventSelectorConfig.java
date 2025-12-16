package com.ryanhoegg.voronoi.core;

import com.ryanhoegg.voronoi.core.geometry.Point;

/**
 * Configuration for circle event eligibility selection.
 * Arc length is measured in SCREEN PIXELS using the provided transform parameters.
 */
public record CircleEventSelectorConfig(
        double minArcLenPx,         // Minimum arc length in screen pixels
        double epsilon,              // Small value for Y position offsets
        double minEventDy,           // Minimum distance between y3 and event Y (guards numerical instability)
        float zoom,                  // Zoom factor for world-to-screen conversion
        Point screenCenter,          // Screen center point (typically width/2, height/2)
        Point focus,                 // World focus point (camera center in world coordinates)
        int maxCircleEventsToScan    // Maximum events to evaluate before giving up
) {
    /**
     * Default config for CircleEventZoom which uses zoom=3.0.
     */
    public static CircleEventSelectorConfig defaultConfig(float zoom, Point screenCenter, Point focus) {
        return new CircleEventSelectorConfig(
                20.0,    // minArcLenPx - minimum visible arc in screen pixels
                0.001,   // epsilon
                5.0,     // minEventDy - guard against numerically tight events
                zoom,    // zoom factor for world->screen conversion
                screenCenter, // screen center (width/2, height/2)
                focus,   // world focus point
                50       // maxCircleEventsToScan
        );
    }

    /**
     * Config for testing with more lenient requirements.
     */
    public static CircleEventSelectorConfig lenientConfig(float zoom, Point screenCenter, Point focus) {
        return new CircleEventSelectorConfig(
                15.0,    // minArcLenPx - slightly more lenient
                0.001,   // epsilon
                2.0,     // minEventDy - very small, mostly numerical guard
                zoom,    // zoom factor
                screenCenter, // screen center (width/2, height/2)
                focus,   // world focus point
                100      // maxCircleEventsToScan - scan more events
        );
    }
}
