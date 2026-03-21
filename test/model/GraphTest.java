package model;

import java.util.List;

/**
 * Tests for Graph.java — Dijkstra, shortest path, edge cases
 * Location: test/model/GraphTest.java
 */
public class GraphTest {
    static int passed = 0, failed = 0;

    public static void main(String[] args) {
        System.out.println("=== GraphTest ===\n");

        // Basic structure
        testAddNode();
        testAddEdge_directed();
        testAddUndirectedEdge_bothDirections();
        testRemoveEdge();
        testRemoveUndirectedEdge();
        testGetEdges_unknownNode_emptyList();

        // Dijkstra
        testDijkstra_sourceToItself_isZero();
        testDijkstra_directEdge();
        testDijkstra_choosesShortestPath();
        testDijkstra_unreachableNode();
        testDijkstra_singleNode();
        testDijkstra_symmetricOnUndirected();

        // shortestTime
        testShortestTime_direct();
        testShortestTime_indirect();
        testShortestTime_sameNode();

        // getShortestPath
        testGetShortestPath_sameNode_empty();
        testGetShortestPath_directEdge_oneNode();
        testGetShortestPath_indirect_correctNodes();
        testGetShortestPath_unreachable_empty();
        testGetShortestPath_endsAtDestination();
        testGetShortestPath_doesNotIncludeSource();

        // Edge cases
        testDirectedEdge_notReverseReachable();
        testParallelEdges_choosesLighter();

        printResults();
    }

    static void testAddNode() {
        Graph g = new Graph();
        g.addNode(new Node(1, 0, 0));
        assertEqual("nodeCount after add", 1, g.nodeCount());
        assertTrue("getNode returns node", g.getNode(1) != null);
    }

    static void testAddEdge_directed() {
        Graph g = twoNodeGraph();
        g.addEdge(0, 1, 5.0);
        assertEqual("edges from 0", 1, g.getEdges(0).size());
        assertEqual("no edges from 1", 0, g.getEdges(1).size());
    }

    static void testAddUndirectedEdge_bothDirections() {
        Graph g = twoNodeGraph();
        g.addUndirectedEdge(0, 1, 3.0);
        assertEqual("edges from 0", 1, g.getEdges(0).size());
        assertEqual("edges from 1", 1, g.getEdges(1).size());
    }

    static void testRemoveEdge() {
        Graph g = twoNodeGraph();
        g.addEdge(0, 1, 5.0);
        g.removeEdge(0, 1);
        assertEqual("no edges after remove", 0, g.getEdges(0).size());
    }

    static void testRemoveUndirectedEdge() {
        Graph g = twoNodeGraph();
        g.addUndirectedEdge(0, 1, 5.0);
        g.removeUndirectedEdge(0, 1);
        assertEqual("0 edges from 0", 0, g.getEdges(0).size());
        assertEqual("0 edges from 1", 0, g.getEdges(1).size());
    }

    static void testGetEdges_unknownNode_emptyList() {
        Graph g = new Graph();
        assertTrue("unknown node returns empty list", g.getEdges(99).isEmpty());
    }

    static void testDijkstra_sourceToItself_isZero() {
        Graph g = threeNodeLine();
        assertDoubleEqual("dist to self = 0", 0.0, g.dijkstra(0)[0]);
    }

    static void testDijkstra_directEdge() {
        Graph g = threeNodeLine(); // 0-5->1-4->2
        assertDoubleEqual("0→1 = 5", 5.0, g.dijkstra(0)[1]);
    }

    static void testDijkstra_choosesShortestPath() {
        // 0→2 direct=10, via 1=5+4=9 → should choose 9
        Graph g = threeNodeLine();
        g.addEdge(0, 2, 10.0);
        assertDoubleEqual("0→2 via 1 = 9", 9.0, g.dijkstra(0)[2]);
    }

    static void testDijkstra_unreachableNode() {
        Graph g = threeNodeLine();
        g.addNode(new Node(99, 0, 0)); // isolated
        double[] dist = g.dijkstra(0);
        assertTrue("unreachable = MAX/2", dist[99] >= Double.MAX_VALUE / 2 - 1);
    }

    static void testDijkstra_singleNode() {
        Graph g = new Graph();
        g.addNode(new Node(0, 0, 0));
        assertDoubleEqual("single node dist to self = 0", 0.0, g.dijkstra(0)[0]);
    }

    static void testDijkstra_symmetricOnUndirected() {
        Graph g = twoNodeGraph();
        g.addUndirectedEdge(0, 1, 7.0);
        assertDoubleEqual("0→1 = 7", 7.0, g.dijkstra(0)[1]);
        assertDoubleEqual("1→0 = 7", 7.0, g.dijkstra(1)[0]);
    }

    static void testShortestTime_direct() {
        assertDoubleEqual("shortestTime direct", 5.0, threeNodeLine().shortestTime(0, 1));
    }

    static void testShortestTime_indirect() {
        assertDoubleEqual("shortestTime indirect", 9.0, threeNodeLine().shortestTime(0, 2));
    }

    static void testShortestTime_sameNode() {
        assertDoubleEqual("shortestTime same node = 0", 0.0, threeNodeLine().shortestTime(0, 0));
    }

    static void testGetShortestPath_sameNode_empty() {
        assertTrue("path to self = empty", threeNodeLine().getShortestPath(0, 0).isEmpty());
    }

    static void testGetShortestPath_directEdge_oneNode() {
        List<Node> path = threeNodeLine().getShortestPath(0, 1);
        assertEqual("path length = 1", 1, path.size());
        assertEqual("path[0] = node 1", 1, path.get(0).getId());
    }

    static void testGetShortestPath_indirect_correctNodes() {
        List<Node> path = threeNodeLine().getShortestPath(0, 2);
        assertEqual("path length = 2", 2, path.size());
        assertEqual("path[0] = node 1", 1, path.get(0).getId());
        assertEqual("path[1] = node 2", 2, path.get(1).getId());
    }

    static void testGetShortestPath_unreachable_empty() {
        Graph g = twoNodeGraph(); // no edges
        assertTrue("unreachable path = empty", g.getShortestPath(0, 1).isEmpty());
    }

    static void testGetShortestPath_endsAtDestination() {
        List<Node> path = threeNodeLine().getShortestPath(0, 2);
        assertFalse("path not empty", path.isEmpty());
        assertEqual("last node = destination", 2, path.get(path.size()-1).getId());
    }

    static void testGetShortestPath_doesNotIncludeSource() {
        List<Node> path = threeNodeLine().getShortestPath(0, 2);
        for (Node n : path) assertFalse("path has no source node", n.getId() == 0);
    }

    static void testDirectedEdge_notReverseReachable() {
        Graph g = twoNodeGraph();
        g.addEdge(0, 1, 5.0); // directed: only 0→1
        double[] fromOne = g.dijkstra(1);
        assertTrue("1→0 unreachable", fromOne[0] >= Double.MAX_VALUE / 2 - 1);
    }

    static void testParallelEdges_choosesLighter() {
        Graph g = twoNodeGraph();
        g.addEdge(0, 1, 10.0);
        g.addEdge(0, 1, 3.0);  // lighter parallel edge
        assertDoubleEqual("picks lighter edge", 3.0, g.shortestTime(0, 1));
    }

    // ── Graph builders ────────────────────────────────────────────
    static Graph twoNodeGraph() {
        Graph g = new Graph();
        g.addNode(new Node(0, 0, 0));
        g.addNode(new Node(1, 1, 0));
        return g;
    }

    /** 0 →5→ 1 →4→ 2 */
    static Graph threeNodeLine() {
        Graph g = new Graph();
        g.addNode(new Node(0, 0, 0));
        g.addNode(new Node(1, 1, 0));
        g.addNode(new Node(2, 2, 0));
        g.addEdge(0, 1, 5.0);
        g.addEdge(1, 2, 4.0);
        return g;
    }

    static void printResults() {
        System.out.println("\nGraphTest: " + passed + " passed, " + failed + " failed");
    }
    static void assertTrue(String n, boolean c) {
        if (c) { System.out.println("  [PASS] "+n); passed++; }
        else   { System.out.println("  [FAIL] "+n); failed++; }
    }
    static void assertFalse(String n, boolean c) { assertTrue(n, !c); }
    static void assertEqual(String n, int e, int g) {
        if (e==g) { System.out.println("  [PASS] "+n); passed++; }
        else { System.out.println("  [FAIL] "+n+" — expected="+e+" got="+g); failed++; }
    }
    static void assertDoubleEqual(String n, double e, double g) {
        if (Math.abs(e-g) < 1e-9) { System.out.println("  [PASS] "+n); passed++; }
        else { System.out.println("  [FAIL] "+n+" — expected="+e+" got="+g); failed++; }
    }
}