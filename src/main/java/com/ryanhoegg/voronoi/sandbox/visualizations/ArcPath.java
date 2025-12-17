package com.ryanhoegg.voronoi.sandbox.visualizations;

import com.ryanhoegg.voronoi.core.FortuneContext;
import com.ryanhoegg.voronoi.core.geometry.Point;
import com.ryanhoegg.voronoi.sandbox.Path;

/**
 * Renderer-friendly bundle describing a single contiguous beach line segment.
 * The FortuneContext.BeachArc reference preserves arc identity so that callers
 * can distinguish disjoint segments belonging to the same site.
 */
public record ArcPath(FortuneContext.BeachArc arc, Point site, Path path) {}
