package com.ryanhoegg.voronoi.sandbox;

import com.ryanhoegg.voronoi.sandbox.visualizations.SingleCellHalfPlaneClip;
import processing.core.PApplet;
import processing.core.PVector;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class VoronoiDemo extends PApplet {
    final int SITE_COUNT = 20;

    List<PVector> sites = new ArrayList<>();
    Visualization visualization;


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
    }

    @Override
    public void draw() {
        visualization.draw();
    }

    @Override
    public void keyPressed() {
        // note: ESC quits so don't use that one
        visualization.keyPressed(key, keyCode);
        switch (key) {
            case ' ':
                visualization.step();
                break;
            case 'r':
                visualization.reset();
                break;
            default:
                System.out.println("Key pressed: " + (int) key);
        }
    }


}

