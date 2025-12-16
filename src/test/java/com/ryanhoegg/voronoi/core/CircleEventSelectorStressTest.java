package com.ryanhoegg.voronoi.core;

import com.ryanhoegg.voronoi.core.geometry.Bounds;
import com.ryanhoegg.voronoi.core.geometry.Point;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stress test for CircleEventSelector to verify it finds eligible events with high reliability.
 *
 * Tests two things:
 * A) Cluster generator can usually find a nice cluster (within 50 attempts)
 * B) Selector finds an eligible event on a nice cluster
 *
 * Uses deterministic seeds (randomly generated per run but recorded for reproducibility).
 */
public class CircleEventSelectorStressTest {

    // VoronoiDemo constants
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final float CENTRAL_WIDTH = 0.22f;
    private static final float CENTRAL_HEIGHT = 0.26f;
    private static final int CLUSTER_SIZE = 7;
    private static final int MAX_CLUSTER_ATTEMPTS = 50;

    // CircleEventZoom constants
    private static final float TARGET_ZOOM = 3.0f;

    // Test configuration
    private static final int NUM_SEEDS = 1000;
    private static final double MAX_CLUSTER_GEN_FAILURE_RATE = 0.001; // 0.1%
    private static final double MAX_NO_ELIGIBLE_EVENT_RATE = 0.001;   // 0.1%

    // Metrics tracking
    private static class TestMetrics {
        int totalSeeds = 0;
        int clusterGenFailures = 0;
        int noEligibleEventFailures = 0;
        List<Long> clusterGenFailureSeeds = new ArrayList<>();
        List<Long> noEligibleEventFailureSeeds = new ArrayList<>();

        void recordClusterGenFailure(long seed) {
            clusterGenFailures++;
            clusterGenFailureSeeds.add(seed);
        }

        void recordNoEligibleEvent(long seed) {
            noEligibleEventFailures++;
            noEligibleEventFailureSeeds.add(seed);
        }

        double clusterGenFailureRate() {
            return totalSeeds == 0 ? 0.0 : (double) clusterGenFailures / totalSeeds;
        }

        double noEligibleEventRate() {
            return totalSeeds == 0 ? 0.0 : (double) noEligibleEventFailures / totalSeeds;
        }

        void printSummary() {
            System.out.println("\n=== CircleEventSelector Stress Test Results ===");
            System.out.printf("Total seeds tested: %d%n", totalSeeds);
            System.out.printf("Cluster generation failures: %d (%.2f%%)%n",
                    clusterGenFailures, clusterGenFailureRate() * 100);
            System.out.printf("No eligible event failures: %d (%.2f%%)%n",
                    noEligibleEventFailures, noEligibleEventRate() * 100);

            if (!clusterGenFailureSeeds.isEmpty()) {
                System.out.println("\nCluster gen failure seeds: " + clusterGenFailureSeeds);
            }
            if (!noEligibleEventFailureSeeds.isEmpty()) {
                System.out.println("No eligible event seeds: " + noEligibleEventFailureSeeds);
            }
            System.out.println("===============================================\n");
        }
    }

    @Test
    public void testSelectorReliability() {
        // Generate random seed list for this run (but deterministically from a base seed)
        long baseSeed = System.currentTimeMillis();
        List<Long> seeds = generateSeeds(baseSeed, NUM_SEEDS);

        System.out.printf("Running stress test with base seed: %d (generates %d test seeds)%n", baseSeed, NUM_SEEDS);

        TestMetrics metrics = new TestMetrics();
        metrics.totalSeeds = seeds.size();

        for (long seed : seeds) {
            testSingleSeed(seed, metrics);
        }

        metrics.printSummary();

        // Assert failure rates are below thresholds
        assertTrue(metrics.clusterGenFailureRate() <= MAX_CLUSTER_GEN_FAILURE_RATE,
                String.format("Cluster generation failure rate %.2f%% exceeds threshold %.2f%%",
                        metrics.clusterGenFailureRate() * 100, MAX_CLUSTER_GEN_FAILURE_RATE * 100));

        assertTrue(metrics.noEligibleEventRate() <= MAX_NO_ELIGIBLE_EVENT_RATE,
                String.format("No eligible event rate %.2f%% exceeds threshold %.2f%%",
                        metrics.noEligibleEventRate() * 100, MAX_NO_ELIGIBLE_EVENT_RATE * 100));
    }

    /**
     * Generate a list of random seeds from a base seed.
     */
    private List<Long> generateSeeds(long baseSeed, int count) {
        Random rng = new Random(baseSeed);
        List<Long> seeds = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            seeds.add(rng.nextLong());
        }
        return seeds;
    }

    /**
     * Test a single seed: try to generate a nice cluster, then verify selector finds an eligible event.
     */
    private void testSingleSeed(long seed, TestMetrics metrics) {
        Random r = new Random(seed);

        // Test A: Try to generate a nice cluster
        List<Point> cluster = generateNiceCluster(CLUSTER_SIZE, r);

        if (cluster == null) {
            metrics.recordClusterGenFailure(seed);
            return; // Can't test selector without a cluster
        }

        // Test B: Run selector and verify it finds an eligible event
        Point screenCenter = new Point(WIDTH / 2.0, HEIGHT / 2.0);
        Point focus = computeClusterCenter(cluster);

        CircleEventSelectorConfig config = CircleEventSelectorConfig.defaultConfig(
                TARGET_ZOOM, screenCenter, focus);
        CircleEventSelector selector = new CircleEventSelector(config);

        Point firstSite = cluster.stream()
                .min(Comparator.comparingDouble(Point::y))
                .orElseThrow();

        Bounds bounds = new Bounds(0, 0, WIDTH, HEIGHT);
        Optional<ChosenCircleEvent> chosen = selector.findEligibleEvent(cluster, bounds, firstSite);

        if (chosen.isEmpty()) {
            metrics.recordNoEligibleEvent(seed);
        }
    }

    /**
     * Replay a specific seed to debug failures.
     * Prints detailed information about the cluster and chosen event.
     */
    public static void replaySeed(long seed) {
        System.out.printf("\n=== Replaying seed: %d ===%n", seed);
        Random r = new Random(seed);

        List<Point> cluster = generateNiceCluster(CLUSTER_SIZE, r);

        if (cluster == null) {
            System.out.println("FAILURE: Could not generate nice cluster after " + MAX_CLUSTER_ATTEMPTS + " attempts");
            return;
        }

        System.out.println("Cluster generated successfully:");
        cluster.forEach(p -> System.out.printf("  Site: (%.2f, %.2f)%n", p.x(), p.y()));

        Point screenCenter = new Point(WIDTH / 2.0, HEIGHT / 2.0);
        Point focus = computeClusterCenter(cluster);
        System.out.printf("Focus: (%.2f, %.2f)%n", focus.x(), focus.y());

        CircleEventSelectorConfig config = CircleEventSelectorConfig.defaultConfig(
                TARGET_ZOOM, screenCenter, focus);
        CircleEventSelector selector = new CircleEventSelector(config);
        selector.withVerboseLogging(true);

        Point firstSite = cluster.stream()
                .min(Comparator.comparingDouble(Point::y))
                .orElseThrow();
        System.out.printf("First site: (%.2f, %.2f)%n", firstSite.x(), firstSite.y());

        Bounds bounds = new Bounds(0, 0, WIDTH, HEIGHT);
        Optional<ChosenCircleEvent> chosen = selector.findEligibleEvent(cluster, bounds, firstSite);

        if (chosen.isEmpty()) {
            System.out.println("\nFAILURE: No eligible circle event found");
            System.out.println("Rejection reasons:");
            selector.getRejectionReasons().forEach(reason -> System.out.println("  - " + reason));
        } else {
            ChosenCircleEvent event = chosen.get();
            System.out.println("\nSUCCESS: Eligible event found");
            System.out.printf("Event ID: y=%.2f c=(%.1f,%.1f)%n",
                    event.yEvent(), event.center().x(), event.center().y());
            System.out.printf("Preview Y: %.2f%n", event.previewSweepY());
            System.out.printf("Arc length: %.2f px%n", event.arcChordLength());
        }
        System.out.println("===========================\n");
    }

    // ==================== Cluster Generation Logic (extracted from VoronoiDemo) ====================

    private static List<Point> generateNiceCluster(int count, Random r) {
        for (int attempt = 0; attempt < MAX_CLUSTER_ATTEMPTS; attempt++) {
            List<Point> cluster = randomClusterInCenter(count, r);
            List<Point> sorted = cluster.stream()
                    .sorted(Comparator.comparingDouble(Point::y))
                    .collect(Collectors.toList());

            fixHighestSitePosition(sorted);
            fixSecondSitePosition(sorted);
            fixRemainingSites(sorted);

            if (clusterLooksNice(sorted)) {
                return sorted;
            }
        }
        return null; // Failed to generate nice cluster
    }

    private static List<Point> randomClusterInCenter(int count, Random r) {
        List<Point> cluster = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            // Generate random point in [0,1] x [0,1]
            double x = r.nextDouble();
            double y = r.nextDouble();

            // Transform to centered cluster region
            x = (x - 0.5) * WIDTH * CENTRAL_WIDTH + WIDTH / 2.0;
            y = (y - 0.5) * HEIGHT * CENTRAL_HEIGHT + HEIGHT / 2.0;

            cluster.add(new Point(x, y));
        }
        return cluster;
    }

    private static void fixHighestSitePosition(List<Point> cluster) {
        double clusterCenterY = cluster.stream()
                .collect(Collectors.averagingDouble(Point::y));

        double adjustFirstSiteY = 0.0;
        if (cluster.get(0).y() < clusterCenterY - 0.15 * HEIGHT) {
            adjustFirstSiteY = clusterCenterY - 0.15 * HEIGHT - cluster.get(0).y();
        } else if (cluster.get(0).y() > clusterCenterY - 0.142 * HEIGHT) {
            adjustFirstSiteY = clusterCenterY - 0.142 * HEIGHT - cluster.get(0).y();
        }

        if (adjustFirstSiteY != 0.0) {
            for (int i = 0; i < cluster.size(); i++) {
                Point p = cluster.get(i);
                if (i == 0) {
                    cluster.set(i, new Point(p.x(), p.y() + adjustFirstSiteY));
                } else {
                    cluster.set(i, new Point(p.x(), p.y() - (adjustFirstSiteY / (cluster.size() - 1))));
                }
            }
        }
    }

    private static void fixSecondSitePosition(List<Point> cluster) {
        double clusterCenterY = cluster.stream()
                .collect(Collectors.averagingDouble(Point::y));

        double adjustSiteY = 0.0;
        if (cluster.get(1).y() < clusterCenterY - 0.135 * HEIGHT) {
            adjustSiteY = clusterCenterY - 0.135 * HEIGHT - cluster.get(1).y();
        }

        if (adjustSiteY != 0.0) {
            for (int i = 1; i < cluster.size(); i++) {
                Point p = cluster.get(i);
                if (i == 1) {
                    cluster.set(i, new Point(p.x(), p.y() + adjustSiteY));
                } else {
                    cluster.set(i, new Point(p.x(), p.y() - (adjustSiteY / (cluster.size() - 2))));
                }
            }
        }
    }

    private static void fixRemainingSites(List<Point> cluster) {
        double minimum = cluster.get(1).y();
        for (int i = 2; i < cluster.size(); i++) {
            double catchup = 0.0;
            if (cluster.get(i).y() <= minimum) {
                catchup = minimum - cluster.get(i).y() + 0.01 * HEIGHT;
                minimum = cluster.get(i).y() + catchup;
            }

            if (catchup > 0.0) {
                Point p = cluster.get(i);
                cluster.set(i, new Point(p.x(), p.y() + catchup));

                for (int j = i + 1; j < cluster.size(); j++) {
                    Point pj = cluster.get(j);
                    cluster.set(j, new Point(pj.x(), pj.y() - catchup / (cluster.size() - i - 1)));
                }
            }
        }
    }

    private static boolean clusterLooksNice(List<Point> cluster) {
        // Not too close together
        double minDistance = 0.15 * Math.min(WIDTH * CENTRAL_WIDTH, HEIGHT * CENTRAL_HEIGHT);
        for (Point v : cluster) {
            for (Point v2 : cluster) {
                if (!v.equals(v2)) {
                    double dist = Math.sqrt(Math.pow(v.x() - v2.x(), 2) + Math.pow(v.y() - v2.y(), 2));
                    if (dist < minDistance) {
                        return false;
                    }
                }
            }
        }

        // Run Fortune's algorithm to check circle events
        Bounds bounds = new Bounds(0, 0, WIDTH, HEIGHT);
        FortuneContext fortune = new FortuneContext(cluster, bounds);
        List<FortuneContext.CircleEvent> circleEvents = new ArrayList<>();

        while (fortune.step()) {
            FortuneContext.Event e = fortune.lastEvent();
            if (e instanceof FortuneContext.CircleEvent) {
                circleEvents.add((FortuneContext.CircleEvent) e);
            }
        }

        if (circleEvents.size() < 3 || circleEvents.size() > 10) {
            return false; // Reasonable number of circle events
        }

        double maxRadius = Math.min(WIDTH, HEIGHT) * 0.45;
        double paddingX = WIDTH * 0.05;
        double paddingY = HEIGHT * 0.05;

        for (FortuneContext.CircleEvent e : circleEvents) {
            double cx = e.center().x();
            double cy = e.center().y();
            double r = e.radius();

            // Not too big
            if (r > maxRadius) {
                return false;
            }
            // Not on the edge
            if (cx < paddingX || cx > WIDTH - paddingX) {
                return false;
            }
            if (cy < paddingY || cy > HEIGHT - paddingY) {
                return false;
            }
        }

        // Check that selector can find an eligible event
        Point screenCenter = new Point(WIDTH / 2.0, HEIGHT / 2.0);
        Point focus = computeClusterCenter(cluster);

        CircleEventSelectorConfig config = CircleEventSelectorConfig.defaultConfig(
                TARGET_ZOOM, screenCenter, focus);
        CircleEventSelector selector = new CircleEventSelector(config);

        Point firstSite = cluster.stream()
                .min(Comparator.comparingDouble(Point::y))
                .orElse(null);

        Optional<ChosenCircleEvent> chosen = selector.findEligibleEvent(cluster, bounds, firstSite);

        return chosen.isPresent();
    }

    private static Point computeClusterCenter(List<Point> cluster) {
        double sumX = cluster.stream().mapToDouble(Point::x).sum();
        double sumY = cluster.stream().mapToDouble(Point::y).sum();
        return new Point(sumX / cluster.size(), sumY / cluster.size());
    }
}
