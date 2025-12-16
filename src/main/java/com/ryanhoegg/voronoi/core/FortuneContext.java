package com.ryanhoegg.voronoi.core;

import com.ryanhoegg.voronoi.core.geometry.Bounds;
import com.ryanhoegg.voronoi.core.geometry.Geometry2D;
import com.ryanhoegg.voronoi.core.geometry.Point;

import java.util.*;

import static com.ryanhoegg.voronoi.core.geometry.Geometry2D.parabolaY;

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

    // for observability
    private Event lastEvent;
    private final List<CircleEvent> recentCircleEvents = new ArrayList<>();

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

    public Event nextEvent() {
        return eventQueue.peek();
    }

    public List<CircleEvent> drainCircleEvents() {
        List<CircleEvent> events = Collections.unmodifiableList(List.copyOf(recentCircleEvents));
        // I'm glad this isn't multithreaded
        recentCircleEvents.clear();
        return events;
    }

    private void handleSiteEvent(final SiteEvent siteEvent) {
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

        // old arc circle event is now invalid
        if (null != arc.circleEvent) {
            arc.circleEvent.valid = false;
            arc.circleEvent = null;
        }

        // check all three new triples for circle events
        maybeCreateCircleEvent(leftArc);
        maybeCreateCircleEvent(newArc);
        maybeCreateCircleEvent(rightArc);
    }

    private void handleCircleEvent(final CircleEvent circleEvent) {
        if (!circleEvent.valid) {
            return;
        }
        BeachArc doomed = circleEvent.disappearingArc;
        if (null == doomed || doomed.circleEvent != circleEvent) {
            return;
        }
        // we have a voronoi vertex!
        vertices.add(new Vertex(circleEvent.center));
        BeachArc leftArc = doomed.prev;
        BeachArc rightArc = doomed.next;
        if (null != leftArc) {
            leftArc.next = rightArc;
        }
        if (null != rightArc) {
            rightArc.prev = leftArc;
        }
        if (null == leftArc) {
            beachLine = rightArc;
        }
        doomed.prev = null;
        doomed.next = null;
        doomed.circleEvent = null;
        if (null != leftArc && null != leftArc.circleEvent) {
            leftArc.circleEvent.valid = false;
            leftArc.circleEvent = null;
        }
        if (null != rightArc && null != rightArc.circleEvent) {
            rightArc.circleEvent.valid = false;
            rightArc.circleEvent = null;
        }
        maybeCreateCircleEvent(leftArc);
        maybeCreateCircleEvent(rightArc);
        recordCircleEvent(circleEvent);
    }

    private void maybeCreateCircleEvent(BeachArc middle) {
        if (null == middle) {
            return;
        }
        if (null == middle.prev ||  null == middle.next) {
            return;
        }
        BeachArc a = middle.prev;
        BeachArc b = middle;
        BeachArc c = middle.next;

        Point pa = a.site;
        Point pb = b.site;
        Point pc = c.site;

        // For a circle event, three consecutive beach arcs A-B-C (left to right)
        // must form a configuration where the circumcenter lies "ahead" of the
        // sweep line (in the direction of increasing y in this coordinate system).
        // In this setup, that condition corresponds to orientation(A,B,C) < 0.
        // If orientation >= 0, the circumcenter would be behind the sweep or
        // the points are collinear, so no circle event.
        if (Geometry2D.orientation(pa, pb, pc) >= 0) {
            // if clockwise, no circle event!
            return;
        }

        Point center = Geometry2D.circumcenter(pa, pb, pc);
        if (null == center) {
            // colinear, no circle event
            return;
        }

        double dx = center.x() - pb.x();
        double dy = center.y() - pb.y();
        double radius = Math.sqrt(dx * dx + dy * dy);
        double eventY = center.y() + radius;
        if (eventY <= sweepLineY) {
            // in the past?! floating point issue or something
            return;
        }

        if (null != middle.circleEvent) {
            middle.circleEvent.valid = false;
        }

        CircleEvent event = new CircleEvent(center, eventY, radius, middle);
        middle.circleEvent = event;
        eventQueue.offer(event);
    }

    private void recordCircleEvent(CircleEvent circleEvent) {
        recentCircleEvents.add(circleEvent);
    }

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


    public static final class BeachArc {

        public final Point site;         // focus of this parabola / arc
        public BeachArc prev;
        public BeachArc next;

        Edge leftEdge;            // Voronoi edge between prev.site and this.site
        Edge rightEdge;           // Voronoi edge between this.site and next.site

        CircleEvent circleEvent;

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
        final double radius;
        boolean valid = true;
        public BeachArc disappearingArc;
        private final Sites sites;

        CircleEvent(Point center, double y, double radius, BeachArc arc) {
            this.center = center;
            this.y = y;
            this.radius = radius;
            this.disappearingArc = arc;
            this.sites = new Sites(arc.prev.site, arc.site, arc.next.site);
        }

        @Override
        public double y() {
            return y;
        }

        public Point center() {
            return this.center;
        }

        public double radius() {
            return this.radius;
        }

        public Sites sites() {
            return this.sites;
        }

        public record Sites (Point a, Point b, Point c) {
            private static final double EPSILON = 0.00001;
            public boolean contains(Point p) {
                return a.equals(p) || b.equals(p) || c.equals(p);
            }

            public boolean matches(Sites other) {
                List<Point> thisPoints = new ArrayList<>(List.of(a, b, c));
                List<Point> otherPoints = new ArrayList<>(List.of(other.a, other.b, other.c));
                thisPoints.sort(Comparator.comparingDouble(Point::y).thenComparingDouble(Point::x));
                otherPoints.sort(Comparator.comparingDouble(Point::y).thenComparingDouble(Point::x));
                for (int i = 0; i < 3; i++) {
                    if (Math.abs(thisPoints.get(i).x() - otherPoints.get(i).x()) > EPSILON) {
                        return false;
                    }
                    if (Math.abs(thisPoints.get(i).y() - otherPoints.get(i).y()) > EPSILON) {
                        return false;
                    }
                }
                return true;
            }
        }

    }
}
