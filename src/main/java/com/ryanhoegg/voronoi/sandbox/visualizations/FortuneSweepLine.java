package com.ryanhoegg.voronoi.sandbox.visualizations;

import com.ryanhoegg.voronoi.core.FortuneContext;
import com.ryanhoegg.voronoi.core.geometry.Bounds;
import com.ryanhoegg.voronoi.core.geometry.Point;
import com.ryanhoegg.voronoi.sandbox.Path;
import com.ryanhoegg.voronoi.sandbox.Visualization;
import processing.core.PApplet;
import processing.core.PVector;

import java.util.ArrayList;
import java.util.List;

public class FortuneSweepLine extends BaseVisualization implements Visualization {
    private final FortuneContext fortune;
    private boolean done = false;

    final float SWEEP_SPEED = 120; // pixels per second
    int sweepLinePosition = 0;
    private Stage stage = Stage.SEEN_SITES;

    public FortuneSweepLine(PApplet app, List<PVector> sites) {
        super(app, sites);

        List<Point> coreSites = new ArrayList<>();
        for (PVector v : sites) {
            coreSites.add(new Point(v.x, v.y));
        }
        Bounds bounds = new Bounds(0, 0, app.width, app.height);
        this.fortune = new FortuneContext(coreSites, bounds);
    }

    @Override
    public void reset() {
        sweepLinePosition = 0;
        stage = Stage.SEEN_SITES;
    }

    @Override
    public void update(float dt) {
        if (sweepLinePosition < app.height + 50) {
            sweepLinePosition += SWEEP_SPEED * dt;
        }
        if (done) return;

        // trigger Fortune events as we cross their y
        while (true) {
            Double nextY = fortune.nextEventY();
            if (null == nextY) {
                done = true;
                break;
            }
            if (sweepLinePosition >= nextY) {
                fortune.step();
            } else {
                break;
            }
        }
    }

    @Override
    public void draw() {
        drawSites();
        drawSweepLine();
        if (stage == Stage.SEEN_SITES) {
            drawSeenSites();
        }
        if (stage == Stage.PARABOLAS) {
            drawParabolas();
        }
        if (stage == Stage.BEACH_LINE) {
            drawBeachLine();
        }
    }

    @Override
    public void step() {
    }

    @Override
    public void keyPressed(char key, int keyCode) {
        if (app.RIGHT == keyCode) {
            changeStage(1);
            app.redraw();
        } else if (app.LEFT == keyCode) {
            changeStage(-1);
            app.redraw();
        }
    }

    private void drawSweepLine() {
        app.stroke(app.color(50, 10, 0, 200));
        app.strokeWeight(4);
        app.line(0, sweepLinePosition, app.width, sweepLinePosition);

        Path unseenAreaPath = new Path();
        unseenAreaPath.add(new PVector(0, sweepLinePosition));
        unseenAreaPath.add(new PVector(app.width, sweepLinePosition));
        unseenAreaPath.add(new PVector(app.width, app.height));
        unseenAreaPath.add(new PVector(0, app.height));
        app.noStroke();
        app.fill(app.color(0, 0, 0, 100));
        draw(unseenAreaPath);
    }

    private void drawSeenSites() {
        app.strokeWeight(2);
        sites.stream()
                .filter(s -> s.y < sweepLinePosition)
                .forEach(s -> {
                    int c = colorForSite(s);

                    app.stroke(c);
                    app.fill(c, 150);
                    app.ellipse(s.x, s.y, 12, 12);
                });
    }

    private void drawParabolas() {
        app.strokeWeight(0.8f);
        app.noFill();
        sites.stream()
                .filter(s -> s.y < sweepLinePosition)
                .forEach(s -> {
                    app.stroke(colorForSite(s));
                    draw(Path.parabola(s, sweepLinePosition, 0, app.width));
                });
    }

    private void drawBeachLine() {
        List<ArcPath> segments = computeBeachLineSegments();
        if (segments.isEmpty()) return;

        app.noFill();
        app.strokeWeight(1.8f);

        for (ArcPath seg : segments) {
            int c = colorForSite(seg.site());
            app.stroke(c);
            app.beginShape();
            for (PVector p : seg.path.getPoints()) {
                app.vertex(p.x, p.y);
            }
            app.endShape();
        }
    }

    private List<ArcPath> computeBeachLineSegments() {
        List<ArcPath> segments =  new ArrayList<>();

        FortuneContext.BeachArc head = fortune.beachLine();
        if (null == head) return segments;

        float directrix = sweepLinePosition;
        FortuneContext.BeachArc currentArc = null;
        Path currentSegment = null;

        for (int x = 0; x < app.width; x += 2) {
            // lowest position (highest y) arc at this x
            FortuneContext.BeachArc best = null;
            double bestY = Double.NEGATIVE_INFINITY;

            for (FortuneContext.BeachArc arc = head; arc != null; arc = arc.next) {
                double y = parabolaY(arc.site, x, directrix);
                if (Double.isNaN(y)) continue;

                if (y > bestY) {
                    bestY = y;
                    best = arc;
                }
            }

            if (null == best) {
                // no arcs here, end the the open segment
                if (null != currentArc && null != currentSegment) {
                    segments.add(new ArcPath(currentArc.site, currentSegment));
                    currentArc = null;
                    currentSegment = null;
                }
                continue;
            }
            if (best != currentArc) { // new segment
                if (null != currentArc && null != currentSegment) {
                    segments.add(new ArcPath(currentArc.site, currentSegment));
                }
                currentArc = best;
                currentSegment = new Path();
            }

            currentSegment.add(new PVector(x, (float) bestY));
        }
        // flush last segment
        if (currentArc != null && currentSegment != null) {
            segments.add(new ArcPath(currentArc.site, currentSegment));
        }

        return segments;
    }

    private double parabolaY(Point focus, double x, double directrixY) {
        double fx = focus.x();
        double fy = focus.y();
        double d  = directrixY;

        double denom = 2.0 * (fy - d);
        if (Math.abs(denom) < 1e-6) {
            // Degenerate case: sweep line basically at the focus.
            // we'll skip NaNs.
            return Double.NaN;
        }

        return ((x - fx) * (x - fx) + fy * fy - d * d) / denom;
    }

    private int colorForSite(PVector site) {
        return colorForSite(new Point(site.x, site.y));
    }

    private int colorForSite(Point site) {
        // deterministic hash
        int h = Double.hashCode(site.x()) * 31 + Double.hashCode(site.y());
        int r = (h >> 16) & 0xFF;
        int g = (h >> 8)  & 0xFF;
        int b = h         & 0xFF;
        return app.color(r, g, b, 220);
    }

    private void changeStage(int delta) {
        int currentStage = stage.ordinal();
        int next = (currentStage + delta + Stage.values().length) % Stage.values().length;
        stage = Stage.values()[next];
    }

    enum Stage {
        SEEN_SITES,
        PARABOLAS,
        BEACH_LINE,
        WITH_EDGES
    }

    class CircleFlash {
        PVector center;
        float radius;
        float age; // in seconds
    }

    private record ArcPath(Point site, Path path) {}
}
