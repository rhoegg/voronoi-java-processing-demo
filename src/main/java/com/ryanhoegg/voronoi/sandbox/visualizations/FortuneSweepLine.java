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

    final float SWEEP_SPEED = 120; // pixels per second
    final float CIRCLE_LIFETIME = 1.6f; // seconds

    int sweepLinePosition = 0;
    private Stage stage = Stage.SEEN_SITES;
    private List<ArcPath> beachLineSegments = new ArrayList<>();
    private final List<CircleFlash> circleFlashes = new ArrayList<>();


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
        circleFlashes.clear();
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
        // Layered sweep line for maximum visibility

        // Layer 1: Glow/halo (drawn first as background)
        app.stroke(ThemeEngine.sweepLineGlowColor(app, theme));
        app.strokeWeight(ThemeEngine.sweepLineGlowWeight(theme));
        app.line(0, sweepLinePosition, app.width, sweepLinePosition);

        // Layer 2: Optional shadow (1px offset for depth, classic theme only)
        app.stroke(ThemeEngine.sweepLineShadowColor(app, theme));
        app.strokeWeight(ThemeEngine.sweepLineShadowWeight());
        app.line(0, sweepLinePosition + 1, app.width, sweepLinePosition + 1);

        // Layer 3: Main/core sweep line (on top)
        app.stroke(ThemeEngine.sweepLineCoreColor(app, theme));
        app.strokeWeight(ThemeEngine.sweepLineCoreWeight(theme));
        app.line(0, sweepLinePosition, app.width, sweepLinePosition);

        // Unseen area overlay
        Path unseenAreaPath = new Path();
        unseenAreaPath.add(new PVector(0, sweepLinePosition));
        unseenAreaPath.add(new PVector(app.width, sweepLinePosition));
        unseenAreaPath.add(new PVector(app.width, app.height));
        unseenAreaPath.add(new PVector(0, app.height));
        app.noStroke();
        app.fill(ThemeEngine.unseenAreaColor(app, theme));
        draw(unseenAreaPath);
    }

    private void drawSeenSites() {
        // PART A: Distinguish sites above (present) vs below (past) the sweep line
        // Draw shadows first
        int shadowColor = ThemeEngine.siteShadow(app, theme);
        app.noStroke();
        for (PVector site : sites) {
            if (site.y < sweepLinePosition) {
                app.fill(shadowColor);
                app.ellipse(site.x + 1, site.y + 1, 14, 14);
            }
        }

        // Draw sites on top
        for (PVector site : sites) {
            int c = ThemeEngine.siteFill(app, theme, site);
            int r = (c >> 16) & 0xFF;
            int g = (c >> 8) & 0xFF;
            int b = c & 0xFF;

            if (site.y < sweepLinePosition) {
                // Sites above sweep line - normal size, full color (present/active)
                int strokeColor = ThemeEngine.siteStroke(app, theme, site.x, site.y);
                app.stroke(strokeColor);
                app.strokeWeight(0.8f);
                app.fill(app.color(r, g, b, 220));
                app.ellipse(site.x, site.y, 12, 12);
            } else {
                // Sites below sweep line - smaller, faded (past/processed)
                app.noStroke();
                app.fill(app.color(r, g, b, 80));
                app.ellipse(site.x, site.y, 6, 6);
            }
        }
    }

    private void drawBeachLineSites() {
        drawColoredSites(this.beachLineSegments.stream().map(seg -> {
            return new PVector( (float) seg.site.x(), (float) seg.site.y());
        }).toList());
    }

    private void drawColoredSites(List<PVector> sites) {
        // Draw shadows first
        int shadowColor = ThemeEngine.siteShadow(app, theme);
        app.noStroke();
        sites.stream()
                .filter(s -> s.y < sweepLinePosition)
                .forEach(s -> {
                    app.fill(shadowColor);
                    app.ellipse(s.x + 1, s.y + 1, 14, 14);
                });

        // Draw sites on top
        app.strokeWeight(ThemeEngine.NORMAL_LINE);
        sites.stream()
                .filter(s -> s.y < sweepLinePosition)
                .forEach(s -> {
                    int c = ThemeEngine.siteFill(app, theme, s);
                    int strokeColor = ThemeEngine.siteStroke(app, theme, s.x, s.y);

                    app.stroke(strokeColor);
                    // Extract RGB and apply custom alpha
                    int r = (c >> 16) & 0xFF;
                    int g = (c >> 8) & 0xFF;
                    int b = c & 0xFF;
                    app.fill(app.color(r, g, b, 150));
                    app.ellipse(s.x, s.y, 12, 12);
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
            app.stroke(ThemeEngine.parabolaColorFaint(app, theme, s));
            app.strokeWeight(0.8f);
            draw(Path.parabola(s, sweepLinePosition, 0, app.width));
        }

        // Draw example parabola highlighted
        if (exampleSite != null) {
            // Example parabola: thicker, higher alpha
            app.stroke(ThemeEngine.parabolaColorHighlight(app, theme, exampleSite));
            app.strokeWeight(2.0f);
            draw(Path.parabola(exampleSite, sweepLinePosition, 0, app.width));

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
            int c = ThemeEngine.beachLineColor(app, theme, seg.site());
            app.stroke(c);
            app.beginShape();
            for (PVector p : seg.path.getPoints()) {
                app.vertex(p.x, p.y);
            }
            app.endShape();
        }
    }

    private void drawCircleFlashes() {
        // PART D: Enhanced circle event storytelling with three distinct phases
        circleFlashes.forEach( flash -> {
            float t = flash.age / CIRCLE_LIFETIME;
            // clamp
            if (t < 0) t = 0;
            if (t > 1) t = 1;

            // Optional: Draw subtle full-screen overlay for emphasis
            if (t < ThemeEngine.CIRCLE_EVENT_STAGE2_END) {
                app.noStroke();
                app.fill(ThemeEngine.circleEventOverlayColor(app, theme));
                app.rect(0, 0, app.width, app.height);
            }

            // === PHASE 1: "Three Neighbors" (0.0 - 0.4) ===
            if (t < ThemeEngine.CIRCLE_EVENT_STAGE1_END) {
                float phase = t / ThemeEngine.CIRCLE_EVENT_STAGE1_END; // 0..1
                int alpha = (int)(phase * 220);

                // Triangle A-B-C with thick stroke
                app.noFill();
                app.stroke(ThemeEngine.triangleEdgeColor(app, theme, alpha));
                app.strokeWeight(3.0f);
                app.beginShape();
                app.vertex(flash.a.x, flash.a.y);
                app.vertex(flash.b.x, flash.b.y);
                app.vertex(flash.c.x, flash.c.y);
                app.endShape(PApplet.CLOSE);

                // Sites A and C - small cool-colored circles
                app.noStroke();
                app.fill(ThemeEngine.pastelMarkerColor(app, theme, alpha));
                app.ellipse(flash.a.x, flash.a.y, 8, 8);
                app.ellipse(flash.c.x, flash.c.y, 8, 8);

                // Site B (disappearing arc) - larger warm-colored circle
                app.fill(ThemeEngine.disappearingArcMarkerColor(app, theme, alpha));
                app.ellipse(flash.b.x, flash.b.y, 16, 16);
            }

            // === PHASE 2: "Empty Circle" (0.4 - 1.0) ===
            if (t >= ThemeEngine.CIRCLE_EVENT_STAGE1_END && t < ThemeEngine.CIRCLE_EVENT_STAGE2_END) {
                float phase = PApplet.map(t, ThemeEngine.CIRCLE_EVENT_STAGE1_END, ThemeEngine.CIRCLE_EVENT_STAGE2_END, 0f, 1f);
                float peakFactor = 1.0f - Math.abs(phase - 0.5f) * 2f;

                float d = flash.radius * 2;

                // Soft fill inside the circle
                int fillAlpha = (int)(peakFactor * 35);
                app.fill(ThemeEngine.circleFillColor(app, theme, fillAlpha));
                app.noStroke();
                app.ellipse(flash.center.x, flash.center.y, d, d);

                // Outer halo - thick translucent
                app.noFill();
                int outerAlpha = (int)(peakFactor * 110);
                app.stroke(ThemeEngine.circleGlowOuterColor(app, theme, outerAlpha));
                app.strokeWeight(7.0f);
                app.ellipse(flash.center.x, flash.center.y, d, d);

                // Inner ring - thin white stroke
                int innerAlpha = (int)(peakFactor * 220);
                app.stroke(ThemeEngine.circleGlowInnerColor(app, theme, innerAlpha));
                app.strokeWeight(2.5f);
                app.ellipse(flash.center.x, flash.center.y, d, d);

                // Visual cue at center
                int cueAlpha = (int)(phase * 200);
                app.fill(ThemeEngine.circleGlowInnerColor(app, theme, cueAlpha));
                app.noStroke();
                app.ellipse(flash.center.x, flash.center.y, 6, 6);
            }

            // === PHASE 3: "Vertex and Edges" (1.0 - 1.6) ===
            if (t >= ThemeEngine.CIRCLE_EVENT_STAGE2_END) {
                float phase = PApplet.map(t, ThemeEngine.CIRCLE_EVENT_STAGE2_END, 1f, 0f, 1f);
                int alpha = (int)((1.0f - phase) * 220);

                // Solid dot at circle center (Voronoi vertex)
                app.fill(ThemeEngine.vertexDotColor(app, alpha));
                app.noStroke();
                app.ellipse(flash.center.x, flash.center.y, 10, 10);

                // Three short spokes from center toward edge midpoints
                // Midpoint of AB
                PVector midAB = PVector.add(flash.a, flash.b).mult(0.5f);
                PVector dirAB = PVector.sub(midAB, flash.center).normalize().mult(flash.radius * 0.3f);

                // Midpoint of BC
                PVector midBC = PVector.add(flash.b, flash.c).mult(0.5f);
                PVector dirBC = PVector.sub(midBC, flash.center).normalize().mult(flash.radius * 0.3f);

                // Midpoint of CA
                PVector midCA = PVector.add(flash.c, flash.a).mult(0.5f);
                PVector dirCA = PVector.sub(midCA, flash.center).normalize().mult(flash.radius * 0.3f);

                // Draw spokes
                app.stroke(ThemeEngine.spokeColor(app, theme, alpha));
                app.strokeWeight(3.0f);

                PVector spokeEnd1 = PVector.add(flash.center, dirAB);
                app.line(flash.center.x, flash.center.y, spokeEnd1.x, spokeEnd1.y);

                PVector spokeEnd2 = PVector.add(flash.center, dirBC);
                app.line(flash.center.x, flash.center.y, spokeEnd2.x, spokeEnd2.y);

                PVector spokeEnd3 = PVector.add(flash.center, dirCA);
                app.line(flash.center.x, flash.center.y, spokeEnd3.x, spokeEnd3.y);

                // Optional: Emphasize disappearing arc B during first half of phase 3
                float phase3Duration = 1f - ThemeEngine.CIRCLE_EVENT_STAGE2_END;
                if (t < ThemeEngine.CIRCLE_EVENT_STAGE2_END + phase3Duration * 0.5f) {
                    float emphasisPhase = (t - ThemeEngine.CIRCLE_EVENT_STAGE2_END) / (phase3Duration * 0.5f);
                    int emphasisAlpha = (int)((1.0f - emphasisPhase) * 200);
                    app.noFill();
                    app.stroke(ThemeEngine.disappearingArcMarkerColor(app, theme, emphasisAlpha));
                    app.strokeWeight(4.0f);
                    app.ellipse(flash.b.x, flash.b.y, 20, 20);
                }
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
