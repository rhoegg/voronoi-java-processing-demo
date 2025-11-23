package com.ryanhoegg.voronoi.sandbox;

import processing.core.PApplet;
import processing.core.PVector;
import processing.event.KeyEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class VoronoiSketch extends PApplet {
    final int SITE_COUNT = 20;

    List<PVector> sites = new ArrayList<>();
    PVector focused;
    PVector neighborHighlight;
    List<PVector> others = new ArrayList<>();
    Path focusedRegion;

    int clipIndex = 0;
    boolean shouldRenderRegion = false;
    boolean shouldRenderBisector = false;

    public static void main(String[] args) {
        PApplet.main(VoronoiSketch.class);
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
        focusCentralSite();
        this.focusedRegion = Path.rectangle(new PVector(0, 0), width, height);
    }

    @Override
    public void draw() {
        drawSites();
        drawStar(focused);
        drawHighlightedNeighbor();
        if (shouldRenderRegion) {
            drawFocusedRegion();
        }
        if (shouldRenderBisector) {
            drawBisector();
        }
    }

    @Override
    public void keyPressed() {
        if ('r' == key) {
            this.shouldRenderRegion = ! this.shouldRenderRegion;
            redraw();
        }
        if ('b' == key) {
            this.shouldRenderBisector = ! this.shouldRenderBisector;
            redraw();
        }
        if (' ' == key) {
            System.out.println("space");
            if (clipIndex < others.size()) {
                PVector neighbor = others.get(clipIndex);
                neighborHighlight = neighbor;
                focusedRegion = clipRegionAgainst(focused, neighbor, focusedRegion);
                clipIndex++;
                redraw();
            } else {
                neighborHighlight = null;
                redraw();
            }
        }
    }

    void drawSites() {
        background(color(240));
        fill(color(15));
        noStroke();
        for (PVector site : sites) {
            ellipse(site.x, site.y, 6, 6);
        }
    }

    void drawHighlightedNeighbor() {
        System.out.println("Drawing neighbor " + neighborHighlight);
        if (null != neighborHighlight) {
            stroke(color(255, 10, 0));
            strokeWeight(2);
            fill(color(255, 10, 0, 40));
            ellipse(neighborHighlight.x, neighborHighlight.y, 12, 12);
        }
    }

    void drawFocusedRegion() {
        fill(0, 0, 240, 40);
        stroke(0, 0, 180);
        strokeWeight(3);
        drawRegion(focusedRegion);
    }

    void drawBisector() {
        // only draw if we're highlighting a neighbor
        if (null != neighborHighlight) {
            PVector site = focused;
            PVector neighbor = neighborHighlight;
            PVector SN = PVector.sub(neighbor, site);
            PVector perpendicularDirection = new PVector(-SN.y, SN.x).normalize(); // unit length
            PVector midpoint = PVector.add(site, neighbor).mult(0.5f);

            // shaded fill
            Path box = Path.rectangle(new PVector(0, 0), width, height);
            Path shaded = clipRegionAgainst(neighbor, site, box);
            if (null != shaded && ! shaded.points.isEmpty()) {
                noStroke();
                fill(color(255, 10, 0, 40));
                draw(shaded);
            }

            // line
            float bisectorLength = 1500; // longer than the screen diagonal
            PVector p1 = PVector.add(midpoint, PVector.mult(perpendicularDirection, bisectorLength));
            PVector p2 = PVector.add(midpoint, PVector.mult(perpendicularDirection, -1 * bisectorLength));

            stroke(color(255, 0, 0, 180));
            strokeWeight(2);
            line(p1.x, p1.y, p2.x, p2.y);

        }
    }

    void drawRegion(Path r) {
        if (r != null && !r.points.isEmpty()) {
            draw(this.focusedRegion);
        }
    }

    void drawStar(PVector location) {
        fill(255, 200, 0, 225);
        draw(Path.star(location, 15f));
    }

    void draw(Path p) {
        beginShape();
        for (PVector point: p.points) {
            vertex(point.x, point.y);
        }
        endShape(CLOSE);
    }

    // voronoi

    void focusCentralSite() {
        PVector center = new PVector(width / 2, height / 2);
        float bestDistanceSquared = Float.MAX_VALUE;
        PVector best = null;
        for (PVector site : sites) {
            float d = PVector.sub(site, center).magSq();
            if (d < bestDistanceSquared) {
                bestDistanceSquared = d;
                best = site;
            }
        }
        this.focused = best;
        for (PVector site : sites) {
            if (! site.equals(focused)) {
                this.others.add(site);
            }
        }
    }

    Path clipRegionAgainst(PVector local, PVector neighbor, Path region) {
        if (region == null || region.points.isEmpty()) return region;
        PVector midpoint = PVector.add(local, neighbor).mult(0.5f);
        PVector toNeighbor = PVector.sub(neighbor, local);

        Path clipped = new Path();
        int n = region.points.size();
        for (int i = 0; i < n; i++) {
            PVector thisVertex = region.points.get(i);
            PVector nextVertex = region.points.get((i + 1) % n);

            boolean thisVertexInside = isInsideHalfPlane(thisVertex, midpoint, toNeighbor);
            boolean nextVertexInside = isInsideHalfPlane(nextVertex, midpoint, toNeighbor);
            // add segments for this vertex pair
            if (thisVertexInside && nextVertexInside) {
                clipped.points.add(nextVertex.copy());
            } else if (thisVertexInside && !nextVertexInside) {
                // add the intersection of this edge with the perpendicular bisector
                PVector I = intersectWithBisector(thisVertex, nextVertex, midpoint, toNeighbor);
                if (null != I) {
                    clipped.points.add(I);
                }
            } else if (!thisVertexInside && nextVertexInside) {
                // add the intersection and the next vertex
                PVector I = intersectWithBisector(thisVertex, nextVertex, midpoint, toNeighbor);
                if (null != I) {
                    clipped.points.add(I);
                }
                clipped.points.add(nextVertex.copy());
            }
            // if both are out, nothing to add
        }
        return clipped;
    }

    /*
     *  true if the target is closer to local than neighbor
     *  equivalent to ((target - midpoint) dot localToNeighbor) < 0
     *  if this is positive, the neighbor is further away than the bisector, else it's nearer
     */
    boolean isInsideHalfPlane(PVector target, PVector midpoint, PVector localToNeighbor) {
        PVector midpointToTarget = PVector.sub(target, midpoint);
        float dot =  midpointToTarget.dot(localToNeighbor);
        return dot < 0;
    }

    PVector intersectWithBisector(PVector p1, PVector p2, PVector midpoint, PVector localToNeighbor) {
        // p1 = A
        // p2 = B
        // midpoint = M
        // localToNeighbor = N
        // (A + t*(B-A) - M) dot N = 0
        // solve for t
        // t = - ((A - M) dot N) / ((B - A) dot N)
        // intersection is A + t(AB)
        PVector MA = PVector.sub(p1, midpoint);
        PVector AB = PVector.sub(p2, p1);

        float denominator = AB.dot(localToNeighbor);
        // if it's very small, we're basically parallel and who cares
        if (abs(denominator) < 1e-6) {
            return null;
        }

        float t = -1 * MA.dot(localToNeighbor) / denominator;
        t = constrain(t, 0, 1); // clean up roundings at extreme edges
        return PVector.add(p1, PVector.mult(AB, t));
    }
}

// very useful for getting intuitive understanding of the vector math
// https://mathinsight.org/vector_introduction