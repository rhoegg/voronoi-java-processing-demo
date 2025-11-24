package com.ryanhoegg.voronoi.sandbox.visualizations;

import com.ryanhoegg.voronoi.sandbox.Path;
import com.ryanhoegg.voronoi.sandbox.Visualization;
import processing.core.PApplet;
import processing.core.PVector;

import java.util.ArrayList;
import java.util.List;

import static processing.core.PConstants.CLOSE;

public class SingleCellHalfPlaneClip extends BaseVisualization {
    PVector focused;
    PVector neighborHighlight;
    List<PVector> others = new ArrayList<>();
    Path focusedRegion;

    int clipIndex = 0;
    boolean shouldRenderRegion = false;
    boolean shouldRenderBisector = false;

    public SingleCellHalfPlaneClip(PApplet app, List<PVector> sites) {
        super(app, sites);

        focusCentralSite();
        this.focusedRegion = Path.rectangle(new PVector(0, 0), app.width, app.height);
    }

    @Override
    public void step() {
        if (clipIndex < others.size()) {
            PVector neighbor = others.get(clipIndex);
            neighborHighlight = neighbor;
            focusedRegion = clipRegionAgainst(focused, neighbor, focusedRegion);
            clipIndex++;
            app.redraw();
        } else {
            neighborHighlight = null;
            app.redraw();
        }
    }

    @Override
    public void reset() {
        clipIndex = 0;
        neighborHighlight = null;
        focusedRegion = Path.rectangle(new PVector(0, 0), app.width, app.height);
        app.redraw();
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
    public void keyPressed(char key, int keyCode) {
        switch (key) {
            case 'v':
                shouldRenderRegion = !shouldRenderRegion;
                app.redraw();
                break;
            case 'b':
                shouldRenderBisector = !shouldRenderBisector;
                app.redraw();
                break;
        }
    }

    void focusCentralSite() {
        PVector center = new PVector(app.width / 2, app.height / 2);
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

    void drawSites() {
        app.background(app.color(240));
        app.fill(app.color(15));
        app.noStroke();
        for (PVector site : sites) {
            app.ellipse(site.x, site.y, 6, 6);
        }
    }

    void drawHighlightedNeighbor() {
        if (null != neighborHighlight) {
            app.stroke(app.color(255, 10, 0));
            app.strokeWeight(2);
            app.fill(app.color(255, 10, 0, 40));
            app.ellipse(neighborHighlight.x, neighborHighlight.y, 12, 12);
        }
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
            Path box = Path.rectangle(new PVector(0, 0), app.width, app.height);
            Path shaded = clipRegionAgainst(neighbor, site, box);
            if (null != shaded && ! shaded.getPoints().isEmpty()) {
                app.noStroke();
                app.fill(app.color(255, 10, 0, 40));
                draw(shaded);
            }

            // line
            float bisectorLength = 1500; // longer than the screen diagonal
            PVector p1 = PVector.add(midpoint, PVector.mult(perpendicularDirection, bisectorLength));
            PVector p2 = PVector.add(midpoint, PVector.mult(perpendicularDirection, -1 * bisectorLength));

            app.stroke(app.color(255, 0, 0, 180));
            app.strokeWeight(2);
            app.line(p1.x, p1.y, p2.x, p2.y);
        }
    }

    void drawFocusedRegion() {
        app.fill(0, 0, 240, 40);
        app.stroke(0, 0, 180);
        app.strokeWeight(3);
        drawRegion(focusedRegion);
    }

    void drawRegion(Path r) {
        if (r != null && !r.getPoints().isEmpty()) {
            draw(this.focusedRegion);
        }
    }

    void drawStar(PVector location) {
        app.fill(255, 200, 0, 225);
        draw(Path.star(location, 15f));
    }

    // geometry

    Path clipRegionAgainst(PVector local, PVector neighbor, Path region) {
        if (region == null || region.getPoints().isEmpty()) return region;
        PVector midpoint = PVector.add(local, neighbor).mult(0.5f);
        PVector toNeighbor = PVector.sub(neighbor, local);

        Path clipped = new Path();
        int n = region.getPoints().size();
        for (int i = 0; i < n; i++) {
            PVector thisVertex = region.getPoints().get(i);
            PVector nextVertex = region.getPoints().get((i + 1) % n);

            boolean thisVertexInside = isInsideHalfPlane(thisVertex, midpoint, toNeighbor);
            boolean nextVertexInside = isInsideHalfPlane(nextVertex, midpoint, toNeighbor);
            // add segments for this vertex pair
            if (thisVertexInside && nextVertexInside) {
                clipped.add(nextVertex.copy());
            } else if (thisVertexInside && !nextVertexInside) {
                // add the intersection of this edge with the perpendicular bisector
                PVector I = intersectWithBisector(thisVertex, nextVertex, midpoint, toNeighbor);
                if (null != I) {
                    clipped.add(I);
                }
            } else if (!thisVertexInside && nextVertexInside) {
                // add the intersection and the next vertex
                PVector I = intersectWithBisector(thisVertex, nextVertex, midpoint, toNeighbor);
                if (null != I) {
                    clipped.add(I);
                }
                clipped.add(nextVertex.copy());
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
        if (app.abs(denominator) < 1e-6) {
            return null;
        }

        float t = -1 * MA.dot(localToNeighbor) / denominator;
        t = app.constrain(t, 0, 1); // clean up roundings at extreme edges
        return PVector.add(p1, PVector.mult(AB, t));
    }

}
