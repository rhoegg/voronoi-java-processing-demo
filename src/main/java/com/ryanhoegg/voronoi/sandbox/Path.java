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
}

