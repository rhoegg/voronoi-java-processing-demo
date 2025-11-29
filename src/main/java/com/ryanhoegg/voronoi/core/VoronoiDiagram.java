package com.ryanhoegg.voronoi.core;

import com.ryanhoegg.voronoi.core.geometry.Bounds;

import java.util.List;

public final class VoronoiDiagram {

    private final List<Cell> cells;
    private final List<Edge> edges;
    private final List<Vertex> vertices;
    private final Bounds bounds;

    public VoronoiDiagram(List<Cell> cells,
                          List<Edge> edges,
                          List<Vertex> vertices,
                          Bounds bounds) {
        this.cells = List.copyOf(cells);
        this.edges = List.copyOf(edges);
        this.vertices = List.copyOf(vertices);
        this.bounds = bounds;
    }

    public List<Cell> cells() {
        return cells;
    }

    public List<Edge> edges() {
        return edges;
    }

    public List<Vertex> vertices() {
        return vertices;
    }

    public Bounds bounds() {
        return bounds;
    }
}