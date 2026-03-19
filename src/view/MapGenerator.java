package view;

import model.*;

import java.util.*;

/**
 * ═══════════════════════════════════════════════════
 *  MapGenerator — lives in view package.
 *
 *  Responsibility:
 *    Build a random city Graph with realistic-looking
 *    street layout and return it.
 *
 *  Rules:
 *    - Only builds the Graph (nodes + edges).
 *    - Does NOT decide where the restaurant goes.
 *    - Does NOT create Orders, Couriers, or Restaurant.
 *    - All those decisions belong to the Controller.
 *    - This class is "dumb" — it just draws a city shape.
 * ═══════════════════════════════════════════════════
 */
public class MapGenerator {

    private final int    cols;
    private final int    rows;
    private final double pixelW;
    private final double pixelH;
    private final double jitter;        // max random offset per node (px)
    private final double removalChance; // probability to remove an edge
    private final Random rng;

    public MapGenerator(int cols, int rows, double pixelW, double pixelH) {
        this.cols          = cols;
        this.rows          = rows;
        this.pixelW        = pixelW;
        this.pixelH        = pixelH;
        this.jitter        = Math.min(pixelW / cols, pixelH / rows) * 0.18;
        this.removalChance = 0.20;
        this.rng           = new Random();
    }

    /**
     * Build and return a city Graph.
     * The Controller will receive this graph and decide
     * what to place on it (restaurant, couriers, customers).
     */
    public Graph buildGraph() {
        Graph    graph = new Graph();
        Node[][] grid  = new Node[rows][cols];

        double cellW = (pixelW - 80) / (cols - 1);
        double cellH = (pixelH - 80) / (rows - 1);

        // 1. Create nodes with slight random offset (jitter)
        //    so the grid looks like a real city, not a chessboard
        int id = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                double x = 40 + c * cellW + (rng.nextDouble() - 0.5) * 2 * jitter;
                double y = 40 + r * cellH + (rng.nextDouble() - 0.5) * 2 * jitter;
                Node node = new Node(id++, x, y);
                grid[r][c] = node;
                graph.addNode(node);
            }
        }

        // 2. Connect each node to its right and bottom neighbours
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++) {
                if (c + 1 < cols) addEdgePair(graph, grid[r][c], grid[r][c + 1]);
                if (r + 1 < rows) addEdgePair(graph, grid[r][c], grid[r + 1][c]);
            }

        // 3. Remove ~20% of edges — but never leave a node with fewer
        //    than 2 connections so there are no dead-end streets
        pruneEdges(graph, grid);

        return graph;
    }

    // ── Private helpers ───────────────────────────────────────────

    private void addEdgePair(Graph graph, Node a, Node b) {
        double dist = Math.hypot(a.getX() - b.getX(), a.getY() - b.getY());
        graph.addUndirectedEdge(a.getId(), b.getId(), dist / 40.0);
    }

    private void pruneEdges(Graph graph, Node[][] grid) {
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols - 1; c++)
                if (rng.nextDouble() < removalChance) {
                    int fromId = grid[r][c].getId();
                    int toId   = grid[r][c + 1].getId();
                    if (graph.getEdges(fromId).size() > 2 &&
                            graph.getEdges(toId).size()   > 2)
                        graph.removeUndirectedEdge(fromId, toId);
                }
    }
}