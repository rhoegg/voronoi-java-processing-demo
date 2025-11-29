package com.ryanhoegg.voronoi.sandbox;

import com.ryanhoegg.voronoi.sandbox.visualizations.FortuneSweepLine;
import com.ryanhoegg.voronoi.sandbox.visualizations.HalfPlaneDiagram;
import com.ryanhoegg.voronoi.sandbox.visualizations.SingleCellHalfPlaneClip;
import processing.core.PApplet;
import processing.core.PVector;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class VoronoiDemo extends PApplet {
    final int SITE_COUNT = 50;

    List<PVector> sites = new ArrayList<>();
    Visualization visualization;

    boolean auto = false;
    float stepInterval = 0.25f; // how often to call step in auto mode
    float accumulatedTime = 0f;
    long lastMillis;

    public static void main(String[] args) {
        PApplet.main(VoronoiDemo.class);
    }

    @Override
    public void settings() {
        size(800, 800);
    }

    @Override
    public void setup() {
        noLoop();
        Random r = new Random();
        for (int i = 0; i < SITE_COUNT; i++) {
            sites.add(new PVector(r.nextFloat() * width, r.nextFloat() * height));
        }
        visualization = new SingleCellHalfPlaneClip(this, sites);
        lastMillis = millis();
    }

    @Override
    public void draw() {
        long now = millis();
        float dt = (now - lastMillis) / 1000f;
        lastMillis = now;

        if (auto) { // advance steps automatically
            visualization.update(dt);
            accumulatedTime += dt;
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
                visualization = new SingleCellHalfPlaneClip(this, sites);
                auto = false;
                noLoop();
                redraw();
                break;
            case '2':
                visualization = new HalfPlaneDiagram(this, sites);
                auto = false;
                noLoop();
                redraw();
                break;
            case '3':
                visualization = new FortuneSweepLine(this, sites);
                auto = false;
                noLoop();
                redraw();
                break;
            case ' ':
                auto = false;
                noLoop();
                visualization.step();
                redraw();
                break;
            case 'a':
                auto = !auto;
                if (auto) {
                    lastMillis = millis();
                    loop();
                    System.out.println("auto mode");
                } else {
                    noLoop();
                }
                break;
            case 'r':
                visualization.reset();
                auto = false;
                noLoop();
                redraw();
                break;
            default:
                System.out.println("Key pressed: " + (int) key);
        }
        visualization.keyPressed(key, keyCode);
    }


}

