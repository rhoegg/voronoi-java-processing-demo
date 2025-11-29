package com.ryanhoegg.voronoi.core.geometry;

public record Bounds(double minX, double minY, double maxX, double maxY) {
    public Bounds {
        if (minX > maxX) {
            throw new IllegalArgumentException(
                    "Invalid bounds: minX > maxX (" + minX + " > " + maxX + ")"
            );
        }
        if (minY > maxY) {
            throw new IllegalArgumentException(
                    "Invalid bounds: minY > maxY (" + minY + " > " + maxY + ")"
            );
        }
    }

    /**
     * Returns true if the given point lies within the inclusive
     * rectangular bounds.
     */
    public boolean contains(Point p) {
        return p.x() >= minX &&
                p.x() <= maxX &&
                p.y() >= minY &&
                p.y() <= maxY;
    }
}
