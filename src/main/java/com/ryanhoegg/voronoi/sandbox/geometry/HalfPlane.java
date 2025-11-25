package com.ryanhoegg.voronoi.sandbox.geometry;

import com.ryanhoegg.voronoi.sandbox.Path;
import processing.core.PApplet;
import processing.core.PVector;

public class HalfPlane {

    public static Path clipRegionAgainst(PVector local, PVector neighbor, Path region) {
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
    public static boolean isInsideHalfPlane(PVector target, PVector midpoint, PVector localToNeighbor) {
        PVector midpointToTarget = PVector.sub(target, midpoint);
        float dot = midpointToTarget.dot(localToNeighbor);
        return dot < 0;
    }

    public static PVector intersectWithBisector(PVector p1, PVector p2, PVector midpoint, PVector localToNeighbor) {
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
        if (PApplet.abs(denominator) < 1e-6) {
            return null;
        }

        float t = -1 * MA.dot(localToNeighbor) / denominator;
        t = PApplet.constrain(t, 0, 1); // clean up roundings at extreme edges
        return PVector.add(p1, PVector.mult(AB, t));
    }

}