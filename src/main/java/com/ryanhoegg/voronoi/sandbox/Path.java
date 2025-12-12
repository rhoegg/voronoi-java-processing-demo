package com.ryanhoegg.voronoi.sandbox;

import processing.core.PVector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class Path {

    private List<PVector> points = new LinkedList<>();

    public List<PVector> getPoints() {
        return Collections.unmodifiableList(points);
    }

    public void add(PVector point) {
        points.add(point);
    }

    public static Path rectangle(PVector origin, float width, float height) {
        Path p = new Path();
        p.points.add(origin);
        p.points.add(new PVector(origin.x + width, origin.y));
        p.points.add(new PVector(origin.x + width, origin.y + height));
        p.points.add(new PVector(origin.x, origin.y + height));
        return p;
    }

    public static Path star(PVector center, float radius) {
        Path p = new Path();
        float angle = (float) Math.PI * 2 / 5 ;
        float halfAngle = angle / 2;
        float startAngle = -1f * (float) Math.PI / 2;
        for (int i = 0; i <= 5; i++) {
            float outerTheta = startAngle + i * angle;
            float outerX = center.x + (float) Math.cos(outerTheta) * radius;
            float outerY = center.y + (float) Math.sin(outerTheta) * radius;
            p.points.add(new PVector(outerX, outerY));

            float innerTheta = outerTheta + halfAngle;
            float innerX = center.x + (float) Math.cos(innerTheta) * radius / 2;
            float innerY = center.y + (float) Math.sin(innerTheta) * radius / 2;
            p.points.add(new PVector(innerX, innerY));
        }
        return p;
    }

    /**
     * Generate parabola path with uniform spacing (legacy wrapper).
     * Calls power-spaced version with power=1 for backward compatibility.
     */
    public static Path parabola(PVector focus, float directrix, int min, int max) {
        return parabola(focus, directrix, min, max, 1.0f);
    }

    /**
     * Generate parabola path with power-spaced sampling for smooth curves near vertex.
     *
     * @param focus The focus point of the parabola
     * @param directrix The directrix y-coordinate
     * @param min Left bound for x sampling
     * @param max Right bound for x sampling
     * @param power Sampling density control (power > 1 concentrates points near vertex)
     *              power = 1.0: uniform spacing (legacy behavior)
     *              power = 2.0-2.5: good for zoomed close-ups
     * @return Path with smooth parabola curve
     */
    public static Path parabola(PVector focus, float directrix, int min, int max, float power) {
        Path p = new Path();

        // Reasonable y-bounds for clipping (prevent extreme off-screen points)
        float maxReasonableY = 10000f;
        float minReasonableY = -10000f;

        // More points for smooth curves with regular vertex() rendering
        int numSamples = 120;

        // Focus x is the vertex - sample symmetrically around it
        float focusX = focus.x;
        float leftSpan = focusX - min;
        float rightSpan = max - focusX;


        // Collect left side points (will be reversed for monotonic x-ordering)
        java.util.List<PVector> leftPoints = new ArrayList<>();
        int leftSamples = (int) (numSamples * leftSpan / (leftSpan + rightSpan));

        for (int i = 1; i <= leftSamples; i++) { // Start at i=1 to avoid duplicate at vertex
            float t = i / (float) leftSamples; // 0..1
            float shaped = (float) Math.pow(t, power);
            float x = focusX - shaped * leftSpan;
            float y = parabolaY(focus, directrix, x);

            if (y < minReasonableY || y > maxReasonableY) {
                break;
            }
            leftPoints.add(new PVector(x, y));
        }

        // Reverse left points for monotonic x-ordering (left→right)
        java.util.Collections.reverse(leftPoints);
        for (PVector pt : leftPoints) {
            p.add(pt);
        }

        // Add vertex point
        float vertexY = parabolaY(focus, directrix, focusX);
        p.add(new PVector(focusX, vertexY));

        // Sample right side
        int rightSamples = numSamples - leftSamples;
        for (int i = 1; i <= rightSamples; i++) { // Start at i=1 to avoid duplicate at vertex
            float t = i / (float) rightSamples; // 0..1
            float shaped = (float) Math.pow(t, power);
            float x = focusX + shaped * rightSpan;
            float y = parabolaY(focus, directrix, x);

            if (y < minReasonableY || y > maxReasonableY) {
                break;
            }
            p.add(new PVector(x, y));
        }

        return p;
    }

    /**
     * Compute y-coordinate of parabola at given x.
     */
    private static float parabolaY(PVector focus, float directrix, float x) {
        return ((x - focus.x) * (x - focus.x) + focus.y * focus.y - directrix * directrix) /
                (2 * (focus.y - directrix));
    }
}

