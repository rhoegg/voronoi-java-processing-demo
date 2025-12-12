package com.ryanhoegg.voronoi.sandbox;

import org.junit.jupiter.api.Test;
import processing.core.PVector;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Geometry-based tests for parabola path generation.
 * These tests verify mathematical properties that should hold regardless of implementation.
 */
public class PathParabolaTest {

    private static final float EPSILON = 0.01f;

    @Test
    public void testMonotonicXOrdering() {
        // Points should have strictly increasing x-coordinates (left to right)
        PVector focus = new PVector(500, 300);
        float directrix = 400;

        Path path = Path.parabola(focus, directrix, 0, 1000, 2.5f);
        List<PVector> points = path.getPoints();

        assertTrue(points.size() > 0, "Path should contain points");

        for (int i = 1; i < points.size(); i++) {
            assertTrue(points.get(i).x >= points.get(i - 1).x,
                    "X-coordinates should be monotonically increasing at index " + i +
                            ": " + points.get(i - 1).x + " -> " + points.get(i).x);
        }
    }

    @Test
    public void testNoDuplicateXValues() {
        // No two points should have the same (or nearly same) x-coordinate
        PVector focus = new PVector(500, 300);
        float directrix = 400;

        Path path = Path.parabola(focus, directrix, 0, 1000, 2.5f);
        List<PVector> points = path.getPoints();

        for (int i = 1; i < points.size(); i++) {
            float dx = points.get(i).x - points.get(i - 1).x;
            assertTrue(dx >= EPSILON,
                    "Points should not have duplicate x-values at index " + i +
                            ": dx = " + dx);
        }
    }

    @Test
    public void testParabolaEquation() {
        // Each point should satisfy the parabola definition:
        // distance to focus = distance to directrix
        PVector focus = new PVector(500, 300);
        float directrix = 250;

        Path path = Path.parabola(focus, directrix, 200, 800, 1.0f);
        List<PVector> points = path.getPoints();

        for (int i = 0; i < points.size(); i++) {
            PVector p = points.get(i);

            // Distance to focus
            float distToFocus = PVector.dist(p, focus);

            // Distance to directrix (vertical distance to horizontal line)
            float distToDirectrix = Math.abs(p.y - directrix);

            assertEquals(distToFocus, distToDirectrix, 0.5f,
                    "Point " + i + " at (" + p.x + ", " + p.y + ") should satisfy parabola equation");
        }
    }

    @Test
    public void testVertexPosition() {
        // Vertex should be at x = focus.x and y = midpoint between focus.y and directrix
        PVector focus = new PVector(500, 300);
        float directrix = 200;

        Path path = Path.parabola(focus, directrix, 0, 1000, 2.5f);
        List<PVector> points = path.getPoints();

        // Find point closest to focus.x (should be the vertex)
        PVector vertex = points.stream()
                .min((p1, p2) -> Float.compare(
                        Math.abs(p1.x - focus.x),
                        Math.abs(p2.x - focus.x)))
                .orElseThrow();

        // Vertex x should be at focus.x
        assertEquals(focus.x, vertex.x, 1.0f, "Vertex x should be at focus.x");

        // Vertex y should be at midpoint between focus and directrix
        float expectedY = (focus.y + directrix) / 2;
        assertEquals(expectedY, vertex.y, 1.0f, "Vertex y should be midpoint between focus and directrix");
    }

    @Test
    public void testPointsWithinBounds() {
        // All points should have x-coordinates within [min, max]
        PVector focus = new PVector(500, 300);
        float directrix = 200;
        int min = 100;
        int max = 900;

        Path path = Path.parabola(focus, directrix, min, max, 1.5f);
        List<PVector> points = path.getPoints();

        for (PVector p : points) {
            assertTrue(p.x >= min, "Point x=" + p.x + " should be >= min=" + min);
            assertTrue(p.x <= max, "Point x=" + p.x + " should be <= max=" + max);
        }
    }

    @Test
    public void testSymmetryAroundAxis() {
        // For a parabola centered in the range, points should be roughly symmetric
        // around x = focus.x
        PVector focus = new PVector(500, 300);
        float directrix = 200;

        Path path = Path.parabola(focus, directrix, 0, 1000, 2.0f);
        List<PVector> points = path.getPoints();

        // Find points equidistant from vertex on left and right
        PVector vertex = points.stream()
                .min((p1, p2) -> Float.compare(
                        Math.abs(p1.x - focus.x),
                        Math.abs(p2.x - focus.x)))
                .orElseThrow();

        int vertexIndex = points.indexOf(vertex);

        // Compare points at similar distances from vertex
        int samplesToCheck = Math.min(10, Math.min(vertexIndex, points.size() - vertexIndex - 1));

        for (int offset = 1; offset <= samplesToCheck; offset++) {
            if (vertexIndex - offset >= 0 && vertexIndex + offset < points.size()) {
                PVector leftPoint = points.get(vertexIndex - offset);
                PVector rightPoint = points.get(vertexIndex + offset);

                // Distance from vertex should be similar on both sides
                float leftDist = Math.abs(leftPoint.x - vertex.x);
                float rightDist = Math.abs(rightPoint.x - vertex.x);

                // Y-coordinates should be similar for symmetric x-distances
                assertEquals(leftPoint.y, rightPoint.y, 5.0f,
                        "Y-coordinates should be symmetric at offset " + offset +
                                ": left=" + leftPoint.y + ", right=" + rightPoint.y);
            }
        }
    }

    @Test
    public void testDegenerateCase_FocusNearDirectrix() {
        // When focus and directrix are very close, parabola opens rapidly
        // Should still produce valid geometry without vertical artifacts
        PVector focus = new PVector(500, 300.0f);
        float directrix = 300.1f; // Very close to focus

        Path path = Path.parabola(focus, directrix, 0, 1000, 2.5f);
        List<PVector> points = path.getPoints();

        assertTrue(points.size() > 0, "Should produce points even for degenerate case");

        // Verify monotonic ordering still holds
        for (int i = 1; i < points.size(); i++) {
            assertTrue(points.get(i).x >= points.get(i - 1).x,
                    "X-ordering should be monotonic even for degenerate case");
        }
    }

    @Test
    public void testPowerSpacingConcentratesNearVertex() {
        // Higher power should concentrate more points near vertex
        PVector focus = new PVector(500, 300);
        float directrix = 200;

        Path pathLowPower = Path.parabola(focus, directrix, 0, 1000, 1.0f);
        Path pathHighPower = Path.parabola(focus, directrix, 0, 1000, 2.5f);

        // Count points within 50px of vertex
        int countLowPower = countPointsNearVertex(pathLowPower.getPoints(), focus.x, 50);
        int countHighPower = countPointsNearVertex(pathHighPower.getPoints(), focus.x, 50);

        assertTrue(countHighPower > countLowPower,
                "Higher power should concentrate more points near vertex: " +
                        "power=1.0 has " + countLowPower + " points, " +
                        "power=2.5 has " + countHighPower + " points");
    }

    private int countPointsNearVertex(List<PVector> points, float vertexX, float radius) {
        return (int) points.stream()
                .filter(p -> Math.abs(p.x - vertexX) <= radius)
                .count();
    }

    @Test
    public void testMinimumPointCount() {
        // Should produce a reasonable number of points for smooth rendering
        PVector focus = new PVector(500, 300);
        float directrix = 200;

        Path path = Path.parabola(focus, directrix, 0, 1000, 2.0f);
        List<PVector> points = path.getPoints();

        assertTrue(points.size() >= 20,
                "Should produce at least 20 points for smooth rendering, got " + points.size());
    }

    @Test
    public void testNoExtremeOffScreenYValues() {
        // When focus and directrix are very close, y-values can explode at the edges.
        // This causes rendering artifacts (vertical lines with curveVertex, or lines
        // going way off-screen with regular vertex).
        // Clipping should prevent y-values from going more than ~10x the range width.
        PVector focus = new PVector(500, 300.0f);
        float directrix = 300.1f; // Very close - only 0.1 apart

        int min = 0;
        int max = 1000;
        int rangeWidth = max - min;

        Path path = Path.parabola(focus, directrix, min, max, 2.5f);
        List<PVector> points = path.getPoints();

        // Y-values should be reasonable (not millions of pixels off-screen)
        // Allow up to 10x the range width as a generous bound
        float maxReasonableY = focus.y + 10 * rangeWidth;
        float minReasonableY = focus.y - 10 * rangeWidth;

        for (int i = 0; i < points.size(); i++) {
            PVector p = points.get(i);
            assertTrue(p.y >= minReasonableY && p.y <= maxReasonableY,
                    "Point " + i + " at (" + p.x + ", " + p.y + ") has extreme y-value. " +
                            "Expected y in [" + minReasonableY + ", " + maxReasonableY + "]. " +
                            "This causes rendering artifacts.");
        }
    }

    @Test
    public void testStrictlyIncreasingXValues() {
        // This test specifically catches the bug where left points were added
        // before being reversed, causing x-values to decrease then increase.
        // Example of broken output: 664.30 → 664.29 → 664.28 → ... → 686.81 → 688.21
        PVector focus = new PVector(640, 300);
        float directrix = 300.1f;

        Path path = Path.parabola(focus, directrix, 0, 1280, 2.5f);
        List<PVector> points = path.getPoints();

        assertTrue(points.size() >= 2, "Need at least 2 points to test ordering");

        // Check STRICT monotonic increase (not just non-decreasing)
        for (int i = 1; i < points.size(); i++) {
            float prevX = points.get(i - 1).x;
            float currX = points.get(i).x;

            assertTrue(currX > prevX,
                    "X must strictly increase (no duplicates, no backtracking). " +
                            "At index " + i + ": " + prevX + " -> " + currX + ". " +
                            "This causes vertical line artifacts in rendering.");
        }
    }
}
