package com.ryanhoegg.voronoi.sandbox.visualizations;

import com.ryanhoegg.voronoi.sandbox.Path;
import com.ryanhoegg.voronoi.sandbox.Visualization;
import processing.core.PApplet;
import processing.core.PVector;

import java.util.ArrayList;
import java.util.List;

public class FortuneSweepLine extends BaseVisualization implements Visualization {

    final float SWEEP_SPEED = 120; // pixels per second
    int sweepLinePosition = 0;
    private Stage stage = Stage.SEEN_SITES;

    public FortuneSweepLine(PApplet app, List<PVector> sites) {
        super(app, sites);
    }

    @Override
    public void reset() {
        sweepLinePosition = 0;
        stage = Stage.SEEN_SITES;
    }

    @Override
    public void update(float dt) {
        if (sweepLinePosition < app.height) {
            sweepLinePosition += SWEEP_SPEED * dt;
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
    }

    @Override
    public void step() {
    }

    @Override
    public void keyPressed(char key, int keyCode) {
        if (app.RIGHT == keyCode) {
            changeStage(1);
        } else if (app.LEFT == keyCode) {
            changeStage(-1);
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
        app.stroke(app.color(255, 10, 0));
        app.strokeWeight(2);
        app.fill(app.color(255, 10, 0, 40));
        sites.stream().filter(s -> s.y < sweepLinePosition).forEach(s -> {
            app.ellipse(s.x, s.y, 12, 12);
        });
    }

    private void drawParabolas() {
        app.stroke(app.color(0, 0, 128, 128));
        app.strokeWeight(0.8f);
        app.noFill();
        sites.stream().filter(s -> s.y < sweepLinePosition).forEach(s -> {
            draw(Path.parabola(s, sweepLinePosition, 0, app.width));
        });
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
}
