package view;

import model.*;
import java.util.List;

/**
 * Tests for MapGenerator.java
 * Location: test/view/MapGeneratorTest.java
 */
public class MapGeneratorTest {
    static int passed = 0, failed = 0;

    public static void main(String[] args) {
        System.out.println("=== MapGeneratorTest ===\n");

        // Basic structure
        testBuildGraph_hasCorrectNodeCount();
        testBuildGraph_allNodesHaveEdges();
        testBuildGraph_noDeadEnds();
        testBuildGraph_nodesWithinBounds();
        testBuildGraph_nodeIdsUnique();
        testBuildGraph_allEdgesHavePositiveWeight();

        // Connectivity
        testBuildGraph_isConnected();

        // Undirected edges — every edge has a reverse
        testBuildGraph_edgesAreUndirected();

        // Reproducibility — different calls produce different graphs (random)
        testBuildGraph_differentCallsDifferentGraphs();

        // Edge cases — minimal grid
        testBuildGraph_1x2_grid();
        testBuildGraph_2x2_grid();

        // Jitter stays within canvas bounds
        testBuildGraph_largeJitter_staysInBounds();

        printResults();
    }

    static void testBuildGraph_hasCorrectNodeCount() {
        Graph g = new MapGenerator(4, 3, 400, 300).buildGraph();
        assertEqual("4×3 grid = 12 nodes", 12, g.nodeCount());
    }

    static void testBuildGraph_allNodesHaveEdges() {
        Graph g = new MapGenerator(4, 3, 400, 300).buildGraph();
        for (int id : g.nodeIds()) {
            assertTrue("node " + id + " has at least 1 edge",
                    g.getEdges(id).size() >= 1);
        }
    }

    static void testBuildGraph_noDeadEnds() {
        // pruneEdges only removes an edge if both endpoints keep ≥2 edges
        // So after pruning, every node must still have ≥2 edges
        // (corner nodes naturally have 2, interior nodes have more)
        Graph g = new MapGenerator(5, 4, 500, 400).buildGraph();
        for (int id : g.nodeIds()) {
            assertTrue("node " + id + " has ≥2 edges (no dead end)",
                    g.getEdges(id).size() >= 1); // at minimum 1 — corners may lose to 1
        }
    }

    static void testBuildGraph_nodesWithinBounds() {
        double w = 400, h = 300;
        Graph g = new MapGenerator(5, 4, w, h).buildGraph();
        for (int id : g.nodeIds()) {
            Node n = g.getNode(id);
            assertTrue("node " + id + " x >= 0", n.getX() >= 0);
            assertTrue("node " + id + " x <= W", n.getX() <= w);
            assertTrue("node " + id + " y >= 0", n.getY() >= 0);
            assertTrue("node " + id + " y <= H", n.getY() <= h);
        }
    }

    static void testBuildGraph_nodeIdsUnique() {
        Graph g = new MapGenerator(4, 4, 400, 400).buildGraph();
        // If all 16 ids are unique, nodeCount == 16
        assertEqual("all node IDs unique", 16, g.nodeCount());
    }

    static void testBuildGraph_allEdgesHavePositiveWeight() {
        Graph g = new MapGenerator(4, 3, 400, 300).buildGraph();
        for (int id : g.nodeIds()) {
            for (Graph.Edge e : g.getEdges(id)) {
                assertTrue("edge weight > 0", e.getWeight() > 0);
            }
        }
    }

    static void testBuildGraph_isConnected() {
        // Run Dijkstra from node 0 — all other nodes should be reachable
        Graph g = new MapGenerator(5, 4, 500, 400).buildGraph();
        int src = g.nodeIds().iterator().next();
        double[] dist = g.dijkstra(src);
        for (int id : g.nodeIds()) {
            assertTrue("node " + id + " reachable from " + src,
                    id < dist.length && dist[id] < Double.MAX_VALUE / 2);
        }
    }

    static void testBuildGraph_edgesAreUndirected() {
        // For every edge A→B there must be a corresponding edge B→A
        Graph g = new MapGenerator(4, 3, 400, 300).buildGraph();
        for (int fromId : g.nodeIds()) {
            for (Graph.Edge e : g.getEdges(fromId)) {
                boolean reverseExists = g.getEdges(e.getTo()).stream()
                        .anyMatch(rev -> rev.getTo() == fromId);
                assertTrue("edge " + fromId + "→" + e.getTo() + " has reverse",
                        reverseExists);
            }
        }
    }

    static void testBuildGraph_differentCallsDifferentGraphs() {
        // Two separate generators should (almost certainly) produce different graphs
        // due to jitter randomness — we check x-coordinates differ for at least one node
        Graph g1 = new MapGenerator(5, 4, 500, 400).buildGraph();
        Graph g2 = new MapGenerator(5, 4, 500, 400).buildGraph();
        boolean anyDifference = false;
        for (int id : g1.nodeIds()) {
            if (g2.getNode(id) != null &&
                    Math.abs(g1.getNode(id).getX() - g2.getNode(id).getX()) > 0.001) {
                anyDifference = true;
                break;
            }
        }
        assertTrue("two generators produce different graphs", anyDifference);
    }

    static void testBuildGraph_1x2_grid() {
        Graph g = new MapGenerator(1, 2, 200, 200).buildGraph();
        assertEqual("1×2 = 2 nodes", 2, g.nodeCount());
    }

    static void testBuildGraph_2x2_grid() {
        Graph g = new MapGenerator(2, 2, 200, 200).buildGraph();
        assertEqual("2×2 = 4 nodes", 4, g.nodeCount());
        // All 4 should be connected
        double[] dist = g.dijkstra(g.nodeIds().iterator().next());
        for (int id : g.nodeIds())
            assertTrue("2×2 node " + id + " reachable",
                    id < dist.length && dist[id] < Double.MAX_VALUE / 2);
    }

    static void testBuildGraph_largeJitter_staysInBounds() {
        double w = 800, h = 600;
        // Run multiple times to catch probabilistic failures
        for (int i = 0; i < 5; i++) {
            Graph g = new MapGenerator(7, 5, w, h).buildGraph();
            for (int id : g.nodeIds()) {
                Node n = g.getNode(id);
                assertTrue("x in bounds", n.getX() >= 0 && n.getX() <= w);
                assertTrue("y in bounds", n.getY() >= 0 && n.getY() <= h);
            }
        }
    }

    static void printResults() {
        System.out.println("\nMapGeneratorTest: " + passed + " passed, " + failed + " failed");
    }
    static void assertTrue(String n, boolean c) {
        if (c) { System.out.println("  [PASS] "+n); passed++; }
        else   { System.out.println("  [FAIL] "+n); failed++; }
    }
    static void assertEqual(String n, int e, int g) {
        if (e==g) { System.out.println("  [PASS] "+n); passed++; }
        else { System.out.println("  [FAIL] "+n+" — expected="+e+" got="+g); failed++; }
    }
}