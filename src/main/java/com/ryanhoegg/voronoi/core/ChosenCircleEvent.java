package com.ryanhoegg.voronoi.core;

import com.ryanhoegg.voronoi.core.geometry.Point;

/**
 * Represents a circle event that has been validated as eligible for visualization.
 * Contains all necessary information to identify and reproduce this event.
 */
public record ChosenCircleEvent(
        FortuneContext.CircleEvent.Sites sites,  // The three sites forming the circle
        double yEvent,                            // Y coordinate where the event occurs
        Point center,                             // Center of the circle
        double radius,                            // Radius of the circle
        double previewSweepY,                     // Computed preview sweep Y position
        double arcChordLength                     // Chord length of the disappearing arc at preview Y
) {
    /**
     * Factory method to create from a FortuneContext.CircleEvent and computed values.
     */
    public static ChosenCircleEvent from(
            FortuneContext.CircleEvent event,
            double previewSweepY,
            double arcChordLength
    ) {
        return new ChosenCircleEvent(
                event.sites(),
                event.y(),
                event.center(),
                event.radius(),
                previewSweepY,
                arcChordLength
        );
    }

    @Override
    public String toString() {
        return String.format("ChosenCircleEvent[y=%.2f, center=(%.2f, %.2f), radius=%.2f, previewY=%.2f, arcLen=%.2f]",
                yEvent, center.x(), center.y(), radius, previewSweepY, arcChordLength);
    }
}
