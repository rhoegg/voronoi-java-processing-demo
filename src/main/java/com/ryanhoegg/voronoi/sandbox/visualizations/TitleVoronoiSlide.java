package com.ryanhoegg.voronoi.sandbox.visualizations;

import com.ryanhoegg.voronoi.core.JtsVoronoi;
import com.ryanhoegg.voronoi.sandbox.Visualization;
import processing.core.PApplet;
import processing.core.PVector;

import java.util.ArrayList;
import java.util.List;

/**
 * Title slide visualization using JTS-computed Voronoi diagram.
 *
 * This visualization displays a complete Voronoi diagram (edges and vertices)
 * computed via JTS (Java Topology Suite), NOT Fortune's algorithm sweep visuals.
 * It includes a mouse-following site and displays "OKCJUG" as the title text.
 *
 * NOTE: This scene uses JTS VoronoiDiagramBuilder to compute the final diagram
 * all at once, not a step-by-step Fortune's algorithm animation.
 */
public class TitleVoronoiSlide extends BaseVisualization implements Visualization {

    // Cached Voronoi computation result
    private JtsVoronoi.VoronoiResult cachedResult;

    // Mouse tracking for recomputation trigger
    private PVector lastMousePos = new PVector(-1000, -1000);
    private float timeSinceLastCompute = 0f;

    // Recomputation policy
    private static final float MIN_MOUSE_MOVEMENT = 10f; // pixels
    private static final float MAX_TIME_BETWEEN_COMPUTES = 0.033f; // ~30 fps max

    // Sites including mouse follower
    private List<PVector> allSites;

    public TitleVoronoiSlide(PApplet app, List<PVector> sites, Theme theme) {
        super(app, sites, theme);
        this.allSites = new ArrayList<>(sites);

        // Initial computation
        recomputeDiagram();
    }

    @Override
    public void reset() {
        recomputeDiagram();
    }

    @Override
    public void update(float dt) {
        super.update(dt);

        timeSinceLastCompute += dt;

        // Get current mouse position in world coordinates
        PVector mousePos = new PVector(app.mouseX, app.mouseY);

        // Recompute if mouse moved significantly OR time elapsed
        boolean mouseMoved = mousePos.dist(lastMousePos) > MIN_MOUSE_MOVEMENT;
        boolean timeElapsed = timeSinceLastCompute >= MAX_TIME_BETWEEN_COMPUTES;

        if (mouseMoved || timeElapsed) {
            lastMousePos = mousePos.copy();
            recomputeDiagram();
            timeSinceLastCompute = 0f;
        }
    }

    @Override
    public void draw() {
        drawBackground();

        if (cachedResult != null) {
            // Draw Voronoi edges
            drawEdges();

            // Draw Voronoi vertices
            drawVertices();
        }

        // Draw all sites (including mouse follower)
        drawAllSites();

        // Draw title text
        drawTitle();
    }

    @Override
    public void step() {
        // No stepping in this visualization - it's a static diagram
    }

    /**
     * Recompute the Voronoi diagram including the mouse-following site.
     */
    private void recomputeDiagram() {
        // Create site list with mouse follower
        allSites = new ArrayList<>(sites);

        // Add mouse site if it's within bounds
        if (app.mouseX >= 0 && app.mouseX <= app.width &&
            app.mouseY >= 0 && app.mouseY <= app.height) {
            allSites.add(new PVector(app.mouseX, app.mouseY));
        }

        // Compute Voronoi diagram using JTS
        if (allSites.size() >= 2) {
            cachedResult = JtsVoronoi.compute(allSites, 0, 0, app.width, app.height);

            // Debug output on first computation
            if (timeSinceLastCompute == 0f && lastMousePos.x == -1000) {
                System.out.println("TitleVoronoiSlide initialized:");
                System.out.println("  Sites: " + allSites.size());
                System.out.println("  Edges: " + cachedResult.getEdges().size());
                System.out.println("  Vertices: " + cachedResult.getVertices().size());
            }
        }
    }

    /**
     * Draw Voronoi edges as thin lines.
     */
    private void drawEdges() {
        app.pushStyle();

        // Use theme styling for edges
        app.stroke(currentStyle().voronoiEdgeColor(app));
        app.strokeWeight(currentStyle().voronoiEdgeWeight());

        for (JtsVoronoi.Edge edge : cachedResult.getEdges()) {
            app.line(edge.getStart().x, edge.getStart().y,
                    edge.getEnd().x, edge.getEnd().y);
        }

        app.popStyle();
    }

    /**
     * Draw Voronoi vertices as small points.
     */
    private void drawVertices() {
        app.pushStyle();

        // Use theme styling for vertices
        app.fill(currentStyle().voronoiVertexColor(app));
        app.noStroke();

        float size = currentStyle().voronoiVertexSize();

        for (PVector vertex : cachedResult.getVertices()) {
            app.ellipse(vertex.x, vertex.y, size, size);
        }

        app.popStyle();
    }

    /**
     * Draw all sites including the mouse follower.
     */
    private void drawAllSites() {
        app.pushStyle();

        for (int i = 0; i < allSites.size(); i++) {
            PVector site = allSites.get(i);
            boolean isMouseSite = (i == allSites.size() - 1) &&
                                 (allSites.size() > sites.size());

            if (isMouseSite) {
                // Draw mouse site as a star for visual distinction
                drawStar(site);
            } else {
                // Draw regular sites normally
                drawSite(site, false);
            }
        }

        app.popStyle();
    }

    /**
     * Draw the "OKCJUG" title text centered on screen.
     */
    private void drawTitle() {
        app.pushStyle();

        // Use theme styling for title
        float textSize = currentStyle().titleTextSize(app);
        app.textSize(textSize);
        app.textAlign(PApplet.CENTER, PApplet.CENTER);

        float x = app.width / 2f;
        float y = app.height / 2f;

        // Draw text outline/shadow for readability
        app.fill(currentStyle().titleOutlineColor(app));
        app.text("OKCJUG", x - 3, y - 3);
        app.text("OKCJUG", x + 3, y - 3);
        app.text("OKCJUG", x - 3, y + 3);
        app.text("OKCJUG", x + 3, y + 3);

        // Draw main text
        app.fill(currentStyle().titleTextColor(app));
        app.text("OKCJUG", x, y);

        app.popStyle();
    }
}
