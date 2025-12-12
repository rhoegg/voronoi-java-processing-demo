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
    private FortuneContext fortune;
    private boolean done = false;

    final float SWEEP_SPEED = 80; // pixels per second

    int sweepLinePosition = 0;
    private Stage stage = Stage.SEEN_SITES;
    private List<ArcPath> beachLineSegments = new ArrayList<>();


    public FortuneSweepLine(PApplet app, List<PVector> sites, Theme theme) {
        super(app, sites, theme);

        rebuildFortuneContext();
    }

    private void rebuildFortuneContext() {
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
        beachLineSegments.clear();
        rebuildFortuneContext();
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

        if (stage == Stage.BEACH_LINE) {
            this.beachLineSegments = computeBeachLineSegments();
        }
    }

    @Override
    public void draw() {
        drawBackground();
        drawSites();
        drawSweepLine(sweepLinePosition);
        drawUnseenArea(sweepLinePosition);
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

    private void drawSeenSites() {
        // PART A: Distinguish sites above (present) vs below (past) the sweep line
        for (PVector site : sites) {
            if (site.y < sweepLinePosition) {
                // Sites above sweep line - use highlighted styling (present/active)
                drawSite(site, true);
            } else {
                // Sites below sweep line - use normal styling (past/processed)
                drawSite(site, false);
            }
        }
    }

    private void drawBeachLineSites() {
        drawColoredSites(this.beachLineSegments.stream().map(seg -> {
            return new PVector( (float) seg.site.x(), (float) seg.site.y());
        }).toList());
    }

    private void drawColoredSites(List<PVector> sites) {
        sites.stream()
                .filter(s -> s.y < sweepLinePosition)
                .forEach(s -> {
                    drawSite(s, false);
                });
    }

    private void drawParabolas() {
        // PART B: Draw parabolas with one highlighted example
        List<PVector> activeSites = sites.stream()
                .filter(s -> s.y < sweepLinePosition)
                .toList();

        if (activeSites.isEmpty()) return;

        // Find example focus: site closest to sweep line (highest y)
        PVector exampleSite = findExampleFocusSite(activeSites);

        // Draw all parabolas faintly as background
        app.noFill();
        for (PVector s : activeSites) {
            if (s.equals(exampleSite)) continue; // Skip example, draw it separately

            // Background parabolas: thinner, very low alpha
            drawParabolaForSite(s, sweepLinePosition, false);
        }

        // Draw example parabola and site highlighted
        if (exampleSite != null) {
            // Example parabola: thicker, higher alpha
            drawParabolaForSite(exampleSite, sweepLinePosition, true);

            // Draw vertical guide line from site to parabola vertex
            float vertexY = (float) parabolaY(new Point(exampleSite.x, exampleSite.y), exampleSite.x, sweepLinePosition);
            if (!Float.isNaN(vertexY)) {
                app.stroke(ThemeEngine.guideLineColor(app, theme));
                app.strokeWeight(1.0f);

                // Dashed line effect
                float y = exampleSite.y;
                while (y < vertexY) {
                    float segmentEnd = Math.min(y + 5, vertexY);
                    app.line(exampleSite.x, y, exampleSite.x, segmentEnd);
                    y += 10; // 5px dash, 5px gap
                }

                // Small label near vertex
                app.fill(ThemeEngine.labelColor(app, theme));
                app.textSize(10);
                app.textAlign(PApplet.CENTER);
                app.text("equal distance", exampleSite.x, vertexY + 15);
            }

            // Draw the example site on top as highlighted
            drawSite(exampleSite, true);
        }
    }

    /**
     * PART B helper: Find the site closest to the sweep line (highest y among active sites)
     */
    private PVector findExampleFocusSite(List<PVector> activeSites) {
        if (activeSites.isEmpty()) return null;

        PVector closest = activeSites.get(0);
        float maxY = closest.y;

        for (PVector site : activeSites) {
            if (site.y > maxY) {
                maxY = site.y;
                closest = site;
            }
        }

        return closest;
    }

    private void drawBeachLine() {
        if (this.beachLineSegments.isEmpty()) return;

        app.noFill();
        app.strokeWeight(ThemeEngine.BEACH_LINE_STROKE_WEIGHT);

        for (ArcPath seg : this.beachLineSegments) {
            // Get beach line color from theme
            int c = currentStyle().beachLineColorForSite(app, seg.site().x(), seg.site().y());
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

    private void changeStage(int delta) {
        int currentStage = stage.ordinal();
        int next = (currentStage + delta + Stage.values().length) % Stage.values().length;
        stage = Stage.values()[next];
    }

    enum Stage {
        SEEN_SITES,
        PARABOLAS,
        BEACH_LINE
    }

    private record ArcPath(Point site, Path path) {}
}
