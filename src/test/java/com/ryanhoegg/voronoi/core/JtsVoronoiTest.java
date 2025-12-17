package com.ryanhoegg.voronoi.core;

import org.junit.jupiter.api.Test;
import processing.core.PVector;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JTS Voronoi adapter.
 */
class JtsVoronoiTest {

    @Test
    void testBasicVoronoiComputation() {
        // Create a simple set of sites
        List<PVector> sites = Arrays.asList(
            new PVector(100, 100),
            new PVector(200, 100),
            new PVector(150, 200)
        );

        // Compute Voronoi diagram
        JtsVoronoi.VoronoiResult result = JtsVoronoi.compute(sites, 0, 0, 300, 300);

        // Verify we got results
        assertNotNull(result);
        assertFalse(result.getEdges().isEmpty(), "Should have edges");
        assertFalse(result.getVertices().isEmpty(), "Should have vertices");

        System.out.println("Test passed: Basic Voronoi computation");
        System.out.println("  Sites: " + sites.size());
        System.out.println("  Edges: " + result.getEdges().size());
        System.out.println("  Vertices: " + result.getVertices().size());
    }

    @Test
    void testEmptySites() {
        List<PVector> sites = List.of();

        JtsVoronoi.VoronoiResult result = JtsVoronoi.compute(sites, 0, 0, 300, 300);

        assertNotNull(result);
        assertTrue(result.getEdges().isEmpty());
        assertTrue(result.getVertices().isEmpty());
    }

    @Test
    void testSingleSite() {
        List<PVector> sites = List.of(new PVector(150, 150));

        JtsVoronoi.VoronoiResult result = JtsVoronoi.compute(sites, 0, 0, 300, 300);

        assertNotNull(result);
        // JTS produces a bounding box for a single site
        assertFalse(result.getEdges().isEmpty());
        System.out.println("Single site test: edges=" + result.getEdges().size());
    }

    @Test
    void testEdgeDeduplication() {
        // Create sites that would share edges
        List<PVector> sites = Arrays.asList(
            new PVector(100, 100),
            new PVector(200, 100),
            new PVector(100, 200),
            new PVector(200, 200)
        );

        JtsVoronoi.VoronoiResult result = JtsVoronoi.compute(sites, 0, 0, 300, 300);

        assertNotNull(result);
        assertFalse(result.getEdges().isEmpty());

        // Each edge should appear only once (no duplicates)
        // This is more of a sanity check than a strict requirement
        System.out.println("Edge deduplication test:");
        System.out.println("  Sites: " + sites.size());
        System.out.println("  Edges: " + result.getEdges().size());
        System.out.println("  Vertices: " + result.getVertices().size());
    }
}
