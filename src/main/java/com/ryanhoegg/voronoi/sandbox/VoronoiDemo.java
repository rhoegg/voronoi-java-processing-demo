package com.ryanhoegg.voronoi.sandbox;

import com.ryanhoegg.voronoi.core.FortuneContext;
import com.ryanhoegg.voronoi.core.geometry.Bounds;
import com.ryanhoegg.voronoi.core.geometry.Point;
import com.ryanhoegg.voronoi.sandbox.geometry.ScreenTransform;
import com.ryanhoegg.voronoi.sandbox.visualizations.CircleEventZoom;
import com.ryanhoegg.voronoi.sandbox.visualizations.FortuneSweepLine;
import com.ryanhoegg.voronoi.sandbox.visualizations.HalfPlaneDiagram;
import com.ryanhoegg.voronoi.sandbox.visualizations.SingleCellHalfPlaneClip;
import com.ryanhoegg.voronoi.sandbox.visualizations.Theme;
import processing.core.PApplet;
import processing.core.PVector;

import java.util.*;
import java.util.stream.Collectors;

public class VoronoiDemo extends PApplet {
    final int SITE_COUNT = 50;
    final int WIDTH = 1280;
    final int HEIGHT = 720;

    final float centralWidth = 0.22f;
    final float centralHeight = 0.26f;

    List<PVector> sites = new ArrayList<>();
    List<PVector> circleDemoSites = new ArrayList<>();
    Visualization visualization;

    // Theme selection - Change this to switch themes!
    Theme currentTheme = Theme.CHRISTMAS;

    boolean auto = false;
    float stepInterval = 0.25f; // how often to call step in auto mode
    float accumulatedTime = 0f;
    long lastMillis;

    public static void main(String[] args) {
        PApplet.main(VoronoiDemo.class);
    }

    @Override
    public void settings() {
        size(WIDTH, HEIGHT);
    }

    @Override
    public void setup() {
        // Always use loop() for smooth animations (pulsing, zoom easing, etc.)
        // Manual vs auto only controls whether step() is called automatically
        Random r = new Random();

        circleDemoSites = generateNiceCluster(7, r);

        sites.addAll(circleDemoSites);
        PVector center = new PVector(width / 2, height / 2);
        while (sites.size() < SITE_COUNT) {
            PVector candidate = new PVector(r.nextFloat() * width, r.nextFloat() * height);
            if (abs(center.x - candidate.x) > (centralWidth) * width / 2
                && abs(center.y - candidate.y) > (centralHeight) * height / 2) {
                sites.add(candidate);
            }
        }
        Collections.shuffle(sites);
        visualization = new SingleCellHalfPlaneClip(this, sites, currentTheme);
        lastMillis = millis();
    }

    @Override
    public void draw() {
        long now = millis();
        float dt = (now - lastMillis) / 1000f;
        lastMillis = now;

        // Clamp dt to avoid huge jumps (e.g., after window drag or switching modes)
        dt = Math.min(dt, 0.05f);

        visualization.update(dt);
        accumulatedTime += dt;

        if (auto) { // advance steps automatically
            while (accumulatedTime >= stepInterval) {
                accumulatedTime -= stepInterval;
                visualization.step();
            }
        }
        visualization.draw();
    }

    @Override
    public void keyPressed() {
        // note: ESC quits so don't use that one
        switch (key) {
            case '1':
                visualization = new SingleCellHalfPlaneClip(this, sites, currentTheme);
                auto = false;
                resetTiming(); // Reset timing to avoid dt jumps
                break;
            case '2':
                visualization = new HalfPlaneDiagram(this, sites, currentTheme);
                auto = false;
                resetTiming();
                break;
            case '3':
                visualization = new CircleEventZoom(this, circleDemoSites, currentTheme);
                auto = false;
                resetTiming();
                break;
            case '4':
                visualization = new FortuneSweepLine(this, sites, currentTheme);
                auto = false;
                resetTiming();
                break;
            case 't':
                // Toggle theme
                currentTheme = (currentTheme == Theme.CHRISTMAS) ? Theme.STYLE_B_CLASSIC : Theme.CHRISTMAS;
                System.out.println("Switched to theme: " + currentTheme);
                // Recreate current visualization with new theme
                if (visualization instanceof SingleCellHalfPlaneClip) {
                    visualization = new SingleCellHalfPlaneClip(this, sites, currentTheme);
                } else if (visualization instanceof HalfPlaneDiagram) {
                    visualization = new HalfPlaneDiagram(this, sites, currentTheme);
                } else if (visualization instanceof CircleEventZoom) {
                    visualization = new CircleEventZoom(this, circleDemoSites, currentTheme);
                } else if (visualization instanceof FortuneSweepLine) {
                    visualization = new FortuneSweepLine(this, sites, currentTheme);
                }
                resetTiming();
                break;
            case ' ':
                // Manual step: call step() once but keep animating
                auto = false;
                visualization.step();
                break;
            case 'a':
                // Toggle auto mode
                auto = !auto;
                resetTiming(); // Reset timing to avoid dt jumps when toggling
                System.out.println(auto ? "auto mode" : "manual mode");
                break;
            case 'r':
                visualization.reset();
                auto = false;
                resetTiming();
                break;
            default:
                System.out.println("Key pressed: " + (int) key);
        }
        visualization.keyPressed(key, keyCode);
    }

    /**
     * Reset timing state to avoid huge dt jumps when switching modes/visualizations.
     */
    private void resetTiming() {
        lastMillis = millis();
        accumulatedTime = 0f;
    }

    private List<PVector> generateNiceCluster(int count, Random r) {
        for (int i = 0; i < 50; i++) {
            List<PVector> cluster = randomClusterInCenter(7, r);
            cluster.sort(Comparator.comparingDouble(p -> p.y));
            fixHighestSitePosition(cluster);
            fixSecondSitePosition(cluster);
            fixRemainingSites(cluster);
            if (clusterLooksNice(cluster)) {
                return cluster;
            }
        }
        System.out.println("WARNING: no nice cluster found, punting");
        return randomClusterInCenter(7, r);
    }

    private void fixHighestSitePosition(List<PVector> cluster) {
        float clusterCenterY = cluster.stream().collect(Collectors.averagingDouble(p -> (double) p.y)).floatValue();
        float adjustFirstSiteY = 0f;
        if (cluster.get(0).y < clusterCenterY - 0.15f * height) {
            System.out.println("lowering first site y by " + (clusterCenterY - 0.15f * height - cluster.get(0).y));
            adjustFirstSiteY = clusterCenterY - 0.15f * height - cluster.get(0).y;
        } else if (cluster.get(0).y > clusterCenterY - 0.142f * height) {
            System.out.println("raising first site y by " + -1 * (clusterCenterY - 0.142f * height - cluster.get(0).y));
            adjustFirstSiteY = clusterCenterY - 0.142f * height - cluster.get(0).y;
        }
        if (adjustFirstSiteY != 0f) {
            for (int i = 0; i < cluster.size(); i++) {
                if (i == 0) {
                    cluster.get(i).y += adjustFirstSiteY;
                } else {
                    cluster.get(i).y -= (adjustFirstSiteY / (cluster.size() - 1));
                }
            }
        }
    }

    private void fixSecondSitePosition(List<PVector> cluster) {
        float clusterCenterY = cluster.stream().collect(Collectors.averagingDouble(p -> (double) p.y)).floatValue();
        float adjustSiteY = 0f;
        if (cluster.get(1).y < clusterCenterY - 0.135f * height) {
            System.out.println("lowering second site y by " + (clusterCenterY - 0.135f * height - cluster.get(1).y));
            adjustSiteY = clusterCenterY - 0.135f * height - cluster.get(1).y;
        }
        if (adjustSiteY != 0f) {
            for (int i = 1; i < cluster.size(); i++) {
                if (i == 1) {
                    cluster.get(i).y += adjustSiteY;
                } else {
                    cluster.get(i).y -= (adjustSiteY / (cluster.size() - 2));
                }
            }
        }
    }

    private void fixRemainingSites(List<PVector> cluster) {
        float clusterCenterY = cluster.stream().collect(Collectors.averagingDouble(p -> (double) p.y)).floatValue();
        float minimum = cluster.get(1).y;
        for (int i = 2; i < cluster.size(); i++) {
            float catchup = 0f;
            if (cluster.get(i).y <= minimum) {
                catchup = minimum - cluster.get(i).y + 0.01f * height;
                minimum = cluster.get(i).y + catchup;
            }
            if (catchup > 0f) {
                cluster.get(i).y += catchup;
                for (int j = i + 1; j < cluster.size(); j++) {
                    cluster.get(j).y -= catchup / (cluster.size() - i - 1);
                }
            }
        }
    }


    private boolean clusterLooksNice(List<PVector> cluster) {

        // not too close together
        float minDistance = 0.15f * Math.min(width * centralWidth, height * centralHeight);
        for (PVector v : cluster) {
            for (PVector v2 : cluster) {
                if (! v2.equals(v)) {
                    if (v.dist(v2) < minDistance) {
                        return false;
                    }
                }
            }
        }

        List<Point> points = cluster.stream().map(v -> new Point(v.x, v.y)).toList();
        Bounds bounds = new Bounds(0, 0, width, height);
        FortuneContext fortune = new  FortuneContext(points, bounds);
        List<FortuneContext.CircleEvent> circleEvents = new ArrayList<>();
        while (fortune.step()) {
            FortuneContext.Event e = fortune.lastEvent();
            if (e instanceof FortuneContext.CircleEvent) {
                circleEvents.add((FortuneContext.CircleEvent) e);
            }
        }
        if (circleEvents.size() < 3 || circleEvents.size() > 10) {
            // reasonable number of circle events
            return false;
        }
        float maxRadius = Math.min(width, height) * 0.45f;
        float paddingX = width * 0.05f;
        float paddingY = height * 0.05f;
        for (FortuneContext.CircleEvent e : circleEvents) {
            float cx = (float) e.center().x();
            float cy = (float) e.center().y();
            float r = (float) e.radius();

            // not too big
            if (r > maxRadius) {
                return false;
            }
            // not on the edge
            if (cx < paddingX || cx > width - paddingX) {
                return false;
            }
            if (cy < paddingY || cy > height - paddingY) {
                return false;
            }
        }
        System.out.println("Top cluster site at " + cluster.get(0).y + " (" + cluster.get(0).y / height + ")");
        return true;
    }

    private List<PVector> randomClusterInCenter(int count, Random r) {

        List<PVector> cluster = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            cluster.add(new PVector(r.nextFloat(), r.nextFloat()));
        }
        ScreenTransform transform = ScreenTransform.translate(-0.5f, -0.5f)
                        .andThen(ScreenTransform.scale(width * centralWidth, height * centralHeight))
                        .andThen(ScreenTransform.translate(width / 2, height / 2));

        return cluster.stream().map(transform::apply).collect(Collectors.toCollection(ArrayList::new));
    }
}

