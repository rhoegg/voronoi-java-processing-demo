package com.ryanhoegg.voronoi.core;

import com.ryanhoegg.voronoi.core.geometry.Point;

/**
 * Represents a circle event that has been validated as eligible for visualization.
 * Contains all necessary information to identify and reproduce this event.
 *
 * <p><b>Story-usable positions:</b> wakeY and approachY are pre-computed during event
 * selection using rendered segmentation measurement, ensuring CircleEventZoom scenes
 * work reliably without runtime searching.</p>
 */
public record ChosenCircleEvent(
        FortuneContext.CircleEvent.Sites sites,  // The three sites forming the circle
        double yEvent,                            // Y coordinate where the event occurs
        Point center,                             // Center of the circle
        double radius,                            // Radius of the circle
        double previewSweepY,                     // Computed preview sweep Y position
        double arcChordLength,                    // Chord length of the disappearing arc at preview Y
        // Story-usable scene positions (pre-computed using rendered segmentation)
        double wakeY,                             // Y where all 3 arcs (prev/doomed/next) >= 40px
        double approachY,                         // Y where doomed arc is closest to 6px (tiny but visible)
        double wakeMinArcLenPx,                   // Debug: smallest of 3 arc lengths at wakeY
        double approachDoomedLenPx                // Debug: doomed arc length at approachY
) {
    /**
     * Factory method to create from a FortuneContext.CircleEvent and computed values.
     */
    public static ChosenCircleEvent from(
            FortuneContext.CircleEvent event,
            double previewSweepY,
            double arcChordLength,
            double wakeY,
            double approachY,
            double wakeMinArcLenPx,
            double approachDoomedLenPx
    ) {
        return new ChosenCircleEvent(
                event.sites(),
                event.y(),
                event.center(),
                event.radius(),
                previewSweepY,
                arcChordLength,
                wakeY,
                approachY,
                wakeMinArcLenPx,
                approachDoomedLenPx
        );
    }

    @Override
    public String toString() {
        return String.format(
                "ChosenCircleEvent[y=%.2f, center=(%.2f, %.2f), radius=%.2f, " +
                "wakeY=%.2f, approachY=%.2f, wakeMinArc=%.1fpx, approachDoomed=%.1fpx]",
                yEvent, center.x(), center.y(), radius,
                wakeY, approachY, wakeMinArcLenPx, approachDoomedLenPx
        );
    }
}
