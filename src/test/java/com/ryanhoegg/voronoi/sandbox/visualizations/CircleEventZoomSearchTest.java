package com.ryanhoegg.voronoi.sandbox.visualizations;

import com.ryanhoegg.voronoi.core.ChosenCircleEvent;
import com.ryanhoegg.voronoi.sandbox.story.CircleEventSelector;
import com.ryanhoegg.voronoi.sandbox.story.CircleEventSelectorConfig;
import com.ryanhoegg.voronoi.core.FortuneContext;
import com.ryanhoegg.voronoi.core.geometry.Bounds;
import com.ryanhoegg.voronoi.core.geometry.Point;
import org.junit.jupiter.api.Test;
import processing.core.PApplet;
import processing.core.PVector;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke test that exercises CircleEventZoom's WAKE and APPROACH search logic headlessly.
 * The test instantiates the visualization with a deterministic cluster, matches the chosen
 * circle event inside Fortune, and invokes the private search methods via reflection.
 * Logs produced by CircleEventZoom appear in the test output (used for QA evidence).
 */
public class CircleEventZoomSearchTest {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final float TARGET_ZOOM = 3.0f;
    private static final int CLUSTER_SIZE = 7;

    @Test
    public void runWakeAndApproachSearches() throws Exception {
        long seedBase = 42L;
        Optional<ChosenCircleEvent> chosenOpt = Optional.empty();
        List<Point> clusterPoints = null;
        Point firstSite = null;
        Bounds bounds = new Bounds(0, 0, WIDTH, HEIGHT);
        Point screenCenter = new Point(WIDTH / 2.0, HEIGHT / 2.0);
        Point focus = null;

        for (int attempt = 0; attempt < 200 && chosenOpt.isEmpty(); attempt++) {
            long seed = seedBase + attempt;
            Random random = new Random(seed);
            clusterPoints = generateNiceCluster(CLUSTER_SIZE, random);
            assertNotNull(clusterPoints, "Cluster generation failed");

            firstSite = clusterPoints.stream()
                    .min(Comparator.comparingDouble(Point::y))
                    .orElseThrow();
            focus = computeCenter(clusterPoints);

            CircleEventSelector selector = new CircleEventSelector(
                    CircleEventSelectorConfig.defaultConfig(TARGET_ZOOM, screenCenter, focus));
            chosenOpt = selector.findEligibleEvent(clusterPoints, bounds, firstSite);
            if (chosenOpt.isPresent()) {
                System.out.printf("[CircleEventZoomSearchTest] Selected seed %d for smoke test%n", seed);
                break;
            }
        }

        assertTrue(chosenOpt.isPresent(), "No eligible circle event found in 200 attempts");
        assertNotNull(clusterPoints);
        assertNotNull(firstSite);
        assertNotNull(focus);

        List<PVector> clusterVectors = clusterPoints.stream()
                .map(p -> new PVector((float) p.x(), (float) p.y()))
                .collect(Collectors.toList());

        PApplet app = new StubPApplet(WIDTH, HEIGHT);
        CircleEventZoom zoom = new CircleEventZoom(app, clusterVectors, clusterPoints, chosenOpt.get(), Theme.STYLE_B_CLASSIC);

        FortuneContext.CircleEvent matchedEvent = recreateEvent(clusterPoints, bounds, chosenOpt.get());
        assertNotNull(matchedEvent, "Could not recreate chosen circle event");
        zoom.circleEvent = matchedEvent;

        // NEW: Test the X-extent metric computation
        double approachY = invokeDoubleMethod(zoom, "computeApproachYForEvent");

        assertTrue(Double.isFinite(approachY), "approachY should be finite");
        assertTrue(approachY > 0, "approachY should be positive");

        // Verify the pre-computed values from selector are reasonable
        ChosenCircleEvent chosen = chosenOpt.get();
        assertTrue(Double.isFinite(chosen.wakeY()), "wakeY should be finite");
        assertTrue(Double.isFinite(chosen.approachY()), "selector approachY should be finite");
        assertTrue(chosen.approachY() >= chosen.wakeY(), "approachY should be >= wakeY");

        System.out.printf("[CircleEventZoomSearchTest] Selector: wakeY=%.2f approachY=%.2f%n",
            chosen.wakeY(), chosen.approachY());
        System.out.printf("[CircleEventZoomSearchTest] Computed: approachY=%.2f (using X-extent metric)%n",
            approachY);
    }

    private static FortuneContext.CircleEvent recreateEvent(List<Point> clusterPoints,
                                                            Bounds bounds,
                                                            ChosenCircleEvent target) {
        FortuneContext ctx = new FortuneContext(clusterPoints, bounds);
        final double yTolerance = 0.01;
        final double centerTolerance = 1.0;
        while (ctx.step()) {
            if (ctx.lastEvent() instanceof FortuneContext.CircleEvent circleEvent) {
                if (Math.abs(circleEvent.y() - target.yEvent()) <= yTolerance &&
                        circleEvent.sites().matches(target.sites())) {
                    double dx = circleEvent.center().x() - target.center().x();
                    double dy = circleEvent.center().y() - target.center().y();
                    if (Math.hypot(dx, dy) <= centerTolerance) {
                        return circleEvent;
                    }
                }
            }
        }
        return null;
    }

    private static double invokeDoubleMethod(CircleEventZoom zoom, String methodName)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method method = CircleEventZoom.class.getDeclaredMethod(methodName);
        method.setAccessible(true);
        return ((Double) method.invoke(zoom)).doubleValue();
    }

    private static Point computeCenter(List<Point> points) {
        double sx = 0.0;
        double sy = 0.0;
        for (Point point : points) {
            sx += point.x();
            sy += point.y();
        }
        return new Point(sx / points.size(), sy / points.size());
    }

    private static List<Point> generateNiceCluster(int count, Random r) {
        for (int attempt = 0; attempt < 50; attempt++) {
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
        return null;
    }

    private static List<Point> randomClusterInCenter(int count, Random r) {
        List<Point> cluster = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double x = (r.nextDouble() - 0.5) * WIDTH * 0.22 + WIDTH / 2.0;
            double y = (r.nextDouble() - 0.5) * HEIGHT * 0.26 + HEIGHT / 2.0;
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
        double clusterCenterX = cluster.stream()
                .collect(Collectors.averagingDouble(Point::x));

        for (int i = 2; i < cluster.size(); i++) {
            Point p = cluster.get(i);
            double t = (i - 2) / (double) (cluster.size() - 3);
            double offsetX = (t - 0.5) * WIDTH * 0.25;
            cluster.set(i, new Point(clusterCenterX + offsetX, p.y()));
        }
    }

    private static boolean clusterLooksNice(List<Point> cluster) {
        Point first = cluster.get(0);
        Point last = cluster.get(cluster.size() - 1);
        return Math.abs(first.x() - last.x()) > 0.1 * WIDTH;
    }

    private static class StubPApplet extends PApplet {
        StubPApplet(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }
}
