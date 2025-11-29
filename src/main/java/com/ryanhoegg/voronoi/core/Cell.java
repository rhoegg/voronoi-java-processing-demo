package com.ryanhoegg.voronoi.core;

import com.ryanhoegg.voronoi.core.geometry.Point;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Cell {
    private final Point site;
    private final List<Edge> edges = new ArrayList<>();

    public Cell(Point site) {
        this.site = site;
    }

    public Point site() {
        return site;
    }

    public List<Edge> edges() {
        return Collections.unmodifiableList(edges);
    }

    void addEdge(Edge edge) {
        edges.add(edge);
    }
}
