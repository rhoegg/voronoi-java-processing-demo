package com.ryanhoegg.voronoi.core;

import org.locationtech.jts.geom.*;
import org.locationtech.jts.triangulate.VoronoiDiagramBuilder;
import processing.core.PVector;

import java.util.*;

/**
 * JTS-based Voronoi diagram computation adapter.
 *
 * This class uses the JTS Topology Suite to compute a complete Voronoi diagram
 * (not Fortune's algorithm sweep visualization). It returns the final diagram's
 * edges and vertices, suitable for rendering a static Voronoi diagram.
 *
 * NOTE: This is NOT a Fortune's algorithm implementation. It uses JTS's
 * VoronoiDiagramBuilder which computes the complete diagram all at once.
 */
public class JtsVoronoi {

    /**
     * Result of Voronoi computation containing edges and vertices.
     */
    public static class VoronoiResult {
        private final List<Edge> edges;
        private final List<PVector> vertices;

        public VoronoiResult(List<Edge> edges, List<PVector> vertices) {
            this.edges = edges;
            this.vertices = vertices;
        }

        public List<Edge> getEdges() {
            return edges;
        }

        public List<PVector> getVertices() {
            return vertices;
        }
    }

    /**
     * An edge in the Voronoi diagram.
     */
    public static class Edge {
        private final PVector start;
        private final PVector end;

        public Edge(PVector start, PVector end) {
            this.start = start;
            this.end = end;
        }

        public PVector getStart() {
            return start;
        }

        public PVector getEnd() {
            return end;
        }
    }

    /**
     * Compute Voronoi diagram using JTS.
     *
     * @param sites List of input sites
     * @param minX Minimum X coordinate for clipping
     * @param minY Minimum Y coordinate for clipping
     * @param maxX Maximum X coordinate for clipping
     * @param maxY Maximum Y coordinate for clipping
     * @return VoronoiResult containing edges and vertices
     */
    public static VoronoiResult compute(List<PVector> sites, float minX, float minY, float maxX, float maxY) {
        if (sites.isEmpty()) {
            return new VoronoiResult(List.of(), List.of());
        }

        // Convert sites to JTS Coordinates
        List<Coordinate> coordinates = new ArrayList<>();
        for (PVector site : sites) {
            coordinates.add(new Coordinate(site.x, site.y));
        }

        // Build Voronoi diagram
        GeometryFactory factory = new GeometryFactory();
        VoronoiDiagramBuilder builder = new VoronoiDiagramBuilder();
        builder.setSites(coordinates);
        builder.setClipEnvelope(new Envelope(minX, maxX, minY, maxY));

        Geometry diagram = builder.getDiagram(factory);

        // Extract edges and vertices
        Set<EdgeKey> edgeSet = new HashSet<>();
        List<Edge> edges = new ArrayList<>();
        Set<VertexKey> vertexSet = new HashSet<>();
        List<PVector> vertices = new ArrayList<>();

        // Process each polygon (Voronoi cell) in the diagram
        for (int i = 0; i < diagram.getNumGeometries(); i++) {
            Geometry cell = diagram.getGeometryN(i);
            if (cell instanceof Polygon) {
                Polygon polygon = (Polygon) cell;
                extractEdgesAndVertices(polygon.getBoundary(), edgeSet, edges, vertexSet, vertices);
            }
        }

        return new VoronoiResult(edges, vertices);
    }

    /**
     * Extract edges and vertices from a geometry's boundary.
     */
    private static void extractEdgesAndVertices(Geometry boundary,
                                                Set<EdgeKey> edgeSet,
                                                List<Edge> edges,
                                                Set<VertexKey> vertexSet,
                                                List<PVector> vertices) {
        if (boundary instanceof LineString) {
            extractFromLineString((LineString) boundary, edgeSet, edges, vertexSet, vertices);
        } else if (boundary instanceof MultiLineString) {
            MultiLineString multi = (MultiLineString) boundary;
            for (int i = 0; i < multi.getNumGeometries(); i++) {
                extractFromLineString((LineString) multi.getGeometryN(i), edgeSet, edges, vertexSet, vertices);
            }
        }
    }

    /**
     * Extract edges and vertices from a LineString.
     */
    private static void extractFromLineString(LineString lineString,
                                             Set<EdgeKey> edgeSet,
                                             List<Edge> edges,
                                             Set<VertexKey> vertexSet,
                                             List<PVector> vertices) {
        Coordinate[] coords = lineString.getCoordinates();

        for (int i = 0; i < coords.length - 1; i++) {
            Coordinate c1 = coords[i];
            Coordinate c2 = coords[i + 1];

            // Add vertices (deduplicated)
            addVertex(c1, vertexSet, vertices);
            addVertex(c2, vertexSet, vertices);

            // Add edge (deduplicated using canonical ordering)
            EdgeKey key = new EdgeKey(c1, c2);
            if (!edgeSet.contains(key)) {
                edgeSet.add(key);
                PVector start = new PVector((float) c1.x, (float) c1.y);
                PVector end = new PVector((float) c2.x, (float) c2.y);
                edges.add(new Edge(start, end));
            }
        }
    }

    /**
     * Add a vertex if not already present (using epsilon rounding for deduplication).
     */
    private static void addVertex(Coordinate coord, Set<VertexKey> vertexSet, List<PVector> vertices) {
        VertexKey key = new VertexKey(coord);
        if (!vertexSet.contains(key)) {
            vertexSet.add(key);
            vertices.add(new PVector((float) coord.x, (float) coord.y));
        }
    }

    /**
     * Key for edge deduplication using canonical ordering.
     */
    private static class EdgeKey {
        private final double x1, y1, x2, y2;
        private static final double EPSILON = 1e-3;

        EdgeKey(Coordinate c1, Coordinate c2) {
            // Canonical ordering: ensure (x1,y1) < (x2,y2) lexicographically
            if (c1.x < c2.x || (Math.abs(c1.x - c2.x) < EPSILON && c1.y < c2.y)) {
                this.x1 = round(c1.x);
                this.y1 = round(c1.y);
                this.x2 = round(c2.x);
                this.y2 = round(c2.y);
            } else {
                this.x1 = round(c2.x);
                this.y1 = round(c2.y);
                this.x2 = round(c1.x);
                this.y2 = round(c1.y);
            }
        }

        private double round(double v) {
            return Math.round(v / EPSILON) * EPSILON;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof EdgeKey)) return false;
            EdgeKey key = (EdgeKey) o;
            return Double.compare(key.x1, x1) == 0 &&
                   Double.compare(key.y1, y1) == 0 &&
                   Double.compare(key.x2, x2) == 0 &&
                   Double.compare(key.y2, y2) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x1, y1, x2, y2);
        }
    }

    /**
     * Key for vertex deduplication using epsilon rounding.
     */
    private static class VertexKey {
        private final double x, y;
        private static final double EPSILON = 1e-3;

        VertexKey(Coordinate c) {
            this.x = Math.round(c.x / EPSILON) * EPSILON;
            this.y = Math.round(c.y / EPSILON) * EPSILON;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof VertexKey)) return false;
            VertexKey key = (VertexKey) o;
            return Double.compare(key.x, x) == 0 &&
                   Double.compare(key.y, y) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }
}
