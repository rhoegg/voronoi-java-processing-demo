package com.ryanhoegg.voronoi.sandbox.geometry;

import processing.core.PVector;

public record ScreenTransform(
        float a, float b,
        float c, float d,
        float tx, float ty
) {
    public PVector apply(PVector v) {
        return new PVector(
                a * v.x + b * v.y + tx,
                c * v.x + d * v.y + ty
        );
    }

    public static ScreenTransform identity() {
        return new ScreenTransform(1, 0, 0, 1, 0, 0);
    }

    public static ScreenTransform translate(float tx, float ty) {
        return new ScreenTransform(1, 0, 0, 1, tx, ty);
    }

    public static ScreenTransform scale(float sx, float sy) {
        return new ScreenTransform(sx, 0, 0, sy, 0, 0);
    }

    /** uniform scaling */
    public static ScreenTransform scale(float s) {
        return scale(s, s);
    }

    /**
     * Compose another transform with this one
     * (apply this transform, then apply the other one
     */
    public ScreenTransform andThen(ScreenTransform other) {
        return other.compose(this);
    }

    /**
     * Compose this transform with another.
     *
     * If you call: this.compose(other),
     * the result R satisfies:  R.apply(v) == this.apply(other.apply(v))
     *
     * i.e. other is applied FIRST, then this.
     */
    public ScreenTransform compose(ScreenTransform other) {
        // this = M, other = N
        float na = other.a, nb = other.b, ntx = other.tx;
        float nc = other.c, nd = other.d, nty = other.ty;

        float ra = a * na + b * nc;
        float rb = a * nb + b * nd;
        float rtx = a * ntx + b * nty + tx;

        float rc = c * na + d * nc;
        float rd = d * nb + d * nd;
        float rty = c * ntx + d * nty + ty;

        return new ScreenTransform(ra, rb, rc, rd, rtx, rty);
    }
}
