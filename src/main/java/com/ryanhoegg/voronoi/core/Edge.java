package com.ryanhoegg.voronoi.core;

import com.ryanhoegg.voronoi.core.geometry.Point;

public final class Edge {
    public final Point leftSite;
    public final Point rightSite;

    public Vertex start;
    public Vertex end;

    public Edge(Point leftSite, Point rightSite) {
        this.leftSite = leftSite;
        this.rightSite = rightSite;
    }
}
