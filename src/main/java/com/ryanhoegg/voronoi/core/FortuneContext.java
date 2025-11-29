package com.ryanhoegg.voronoi.core;

import com.ryanhoegg.voronoi.core.geometry.Bounds;
import com.ryanhoegg.voronoi.core.geometry.Point;

import java.util.*;

public final class FortuneContext {
    private final List<Point> sites;
    private final Bounds bounds;

    private final List<Cell> cells =  new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();
    private final List<Vertex> vertices = new ArrayList<>();
    private final Map<Point, Cell> cellMap = new HashMap<>();

    private BeachArc beachLine;
    private double sweepLineY;

    private final PriorityQueue<Event> eventQueue;

    private Event lastEvent;

    public FortuneContext(List<Point> sites, Bounds bounds) {
        this.sites = List.copyOf(sites);
        this.bounds = bounds;

        // sort sites vertically
        List<Point> sortedSites = new ArrayList<>(this.sites);
        sortedSites.sort(Comparator.comparingDouble(Point::y));

        for (Point site : sortedSites) {
            Cell cell = new Cell(site);
            cells.add(cell);
            cellMap.put(site, cell);
        }

        this.eventQueue = new PriorityQueue<>(Comparator.comparingDouble(Event::y));
        for (Point site: sortedSites) {
            eventQueue.add(new SiteEvent(site));
        }
    }

    /** Process a single event if any remain. Returns true if something was processed. */
    public boolean step() {
        if (eventQueue.isEmpty()) {
            lastEvent = null;
            return false;
        }
        Event e = eventQueue.poll();
        lastEvent = e;
        sweepLineY = e.y();

        switch(e) {
            case SiteEvent siteEvent -> handleSiteEvent(siteEvent);
            case CircleEvent circleEvent -> handleCircleEvent(circleEvent);
        }

        return true;
    }

    public Bounds bounds() {
        return bounds;
    }

    public BeachArc beachLine() {
        return beachLine;
    }

    public Event lastEvent() {
        return lastEvent;
    }

    public Double nextEventY() {
        Event e = eventQueue.peek();
        return (e != null) ? e.y() : null;
    }

    private void handleSiteEvent(SiteEvent siteEvent) {
        Point site = siteEvent.site();

        // first site
        if (null == beachLine) {
            beachLine = new BeachArc(site);
            return;
        }

        // from second site on
        BeachArc arc = findArcAbove(site.x());

        // split it up
        BeachArc newArc = new BeachArc(site);
        BeachArc leftArc = new BeachArc(arc.site);
        BeachArc rightArc = new BeachArc(arc.site);

        // hook them up to each other
        newArc.prev = leftArc;
        leftArc.next = newArc;

        newArc.next = rightArc;
        rightArc.prev = newArc;

        // hook them up to the beach line
        // - left side
        leftArc.prev = arc.prev;
        if (null == arc.prev) { // arc was the head
            beachLine = leftArc;
        } else {
            leftArc.prev.next = leftArc;
        }
        // - right side
        rightArc.next = arc.next;
        if (null != arc.next) {
            arc.next.prev = rightArc;
        }

        // old arc is no longer part of the beach line
        arc.next = null;
        arc.prev = null;

        // TODO: circle events?
    }

    private void handleCircleEvent(CircleEvent circleEvent) {}

    private BeachArc findArcAbove(double x) {
        BeachArc best = null;
        double bestY = Double.POSITIVE_INFINITY;
        for (BeachArc arc = beachLine; arc != null; arc = arc.next) {
            double y = parabolaY(arc.site, x, sweepLineY);
            if (y < bestY) {
                bestY = y;
                best = arc;
            }
        }
        return best;
    }

    private double parabolaY(Point focus, double x, double directrixY) {
        double fx = focus.x();
        double fy = focus.y();
        double d = directrixY;

        return ((x - fx) * (x - fx) + fy * fy - d * d) / (2.0 * (fy - d));
    }

    public static final class BeachArc {

        public final Point site;         // focus of this parabola / arc
        BeachArc prev;
        public BeachArc next;

        Edge leftEdge;            // Voronoi edge between prev.site and this.site
        Edge rightEdge;           // Voronoi edge between this.site and next.site

        BeachArc(Point site) {
            this.site = site;
        }
    }

    public sealed interface Event permits SiteEvent, CircleEvent {
        double y();
    }

    public static final class SiteEvent implements Event {
        private final Point site;
        SiteEvent(Point site) {
            this.site = site;
        }

        public Point site() {
            return site;
        }

        @Override
        public double y() {
            return site.y();
        }
    }

    public static final class CircleEvent implements Event {
        final Point center;
        final double y;
        final BeachArc disappearingArc;
        boolean valid = true;

        CircleEvent(Point center, double y, BeachArc arc) {
            this.center = center;
            this.y = y;
            this.disappearingArc = arc;
        }

        @Override
        public double y() {
            return y;
        }
    }
}
