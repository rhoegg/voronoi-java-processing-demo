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
    final float CIRCLE_LIFETIME = 1.6f; // seconds

    int sweepLinePosition = 0;
    private Stage stage = Stage.SEEN_SITES;
    private List<ArcPath> beachLineSegments = new ArrayList<>();
    private final List<CircleFlash> circleFlashes = new ArrayList<>();


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
        done = false;
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

        updateCircleFlashes(dt);

        if (stage == Stage.BEACH_LINE) {
            this.beachLineSegments = computeBeachLineSegments();
        }
    }

    private void updateCircleFlashes(float dt) {
        // create new
        fortune.drainCircleEvents().forEach(e -> {
            if (null == e.sites().a() || null == e.sites().b() || null == e.sites().c()) {
                return;
            }
            System.out.println("adding circle flash");

            final float x = (float) e.center().x();
            final float y = (float) e.center().y();
            final float r = (float) e.radius();
            final PVector center = new PVector(x, y);
            PVector a = new PVector((float) e.sites().a().x(), (float) e.sites().a().y());
            PVector b = new PVector((float) e.sites().b().x(), (float) e.sites().b().y());
            PVector c = new PVector((float) e.sites().c().x(), (float) e.sites().c().y());
            circleFlashes.add(new CircleFlash(center, r, a, b, c));
        });

        // age circle flashes
        circleFlashes.forEach( flash -> flash.age += dt);

        // reap expired circle flashes
        circleFlashes.removeIf(flash -> flash.age >= CIRCLE_LIFETIME);
    }

    @Override
    public void draw() {
        drawSites();
        drawSweepLine();
        if (stage == Stage.SEEN_SITES) {
            drawSeenSites();
        }
        if (stage == Stage.PARABOLAS) {
            drawSeenSites();
            drawParabolas();
        }
        if (stage == Stage.BEACH_LINE) {
            drawBeachLineSites();
            drawBeachLine();
            drawCircleFlashes();
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
        drawColoredSites(this.sites);
    }

    private void drawBeachLineSites() {
        drawColoredSites(this.beachLineSegments.stream().map(seg -> {
            return new PVector( (float) seg.site.x(), (float) seg.site.y());
        }).toList());
    }

    private void drawColoredSites(List<PVector> sites) {
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
        if (this.beachLineSegments.isEmpty()) return;

        app.noFill();
        app.strokeWeight(1.8f);

        for (ArcPath seg : this.beachLineSegments) {
            int c = colorForSite(seg.site());
            app.stroke(c);
            app.beginShape();
            for (PVector p : seg.path.getPoints()) {
                app.vertex(p.x, p.y);
            }
            app.endShape();
        }
    }

    private void drawCircleFlashes() {
        System.out.println("drawing circle flashes " + circleFlashes.size());
        circleFlashes.forEach( flash -> {
            float t = flash.age / CIRCLE_LIFETIME;
            // clamp
            if (t < 0) t = 0;
            if (t > 1) t = 1;

            app.noFill();

            // Phase 1: triangle + highlighted sites (0.0 - 0.4)
            if (t < 0.4f) {
                float phase = t / 0.4f; // 0..1
                int alpha = (int)(phase * 200);

                // triangle edges
                app.stroke(app.color(100, 200, 255, alpha));
                app.strokeWeight(1.5f);
                app.beginShape();
                app.vertex(flash.a.x, flash.a.y);
                app.vertex(flash.b.x, flash.b.y);
                app.vertex(flash.c.x, flash.c.y);
                app.endShape(PApplet.CLOSE);

                // sites A and C small, B big + bright
                app.noStroke();
                app.fill(app.color(180, 180, 255, alpha));
                app.ellipse(flash.a.x, flash.a.y, 6, 6);
                app.ellipse(flash.c.x, flash.c.y, 6, 6);

                app.fill(app.color(255, 80, 80, alpha));
                app.ellipse(flash.b.x, flash.b.y, 10, 10);
            }

            // Phase 2: circle strong (0.4 - 1.0)
            if (t >= 0.2f && t < 1.0f) {
                float phase = PApplet.map(t, 0.2f, 1.0f, 0f, 1f); // soften in & out
                int alpha = (int)((1.0f - Math.abs(phase - 0.5f) * 2f) * 200);

                app.noFill();
                app.stroke(app.color(255, 255, 0, alpha));
                app.strokeWeight(2f);
                float d = flash.radius * 2;
                app.ellipse(flash.center.x, flash.center.y, d, d);
            }

            // Phase 3: starburst at center (1.0 - 1.6)
            if (t >= 1.0f) {
                float phase = PApplet.map(t, 1.0f, 1.6f, 0f, 1f);
                int alpha = (int)((1.0f - phase) * 220);
                float starR = 6 + 8 * (1.0f - phase); // shrink over time

                app.stroke(app.color(255, 255, 255, alpha));
                app.fill(app.color(255, 255, 0, alpha));
                drawStar(new PVector(flash.center.x, flash.center.y), starR);
            }
        });
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
        final PVector center;
        final float radius;
        final PVector a, b, c; // three sites that define the circle
        float age;       // mutable

        CircleFlash(PVector center, float radius, PVector a, PVector b, PVector c) {
            this.center = center;
            this.radius = radius;
            this.a = a;
            this.b = b;
            this.c = c;
            this.age = 0;
        }
    }

    private record ArcPath(Point site, Path path) {}
}
