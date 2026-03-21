package model;

import java.util.*;

/**
 * Weighted directed graph — city road network.
 *
 * Generic data structure — no decisions, no simulation logic.
 * Just nodes, edges, and shortest-path computation.
 */
public class Graph {

    public static class Edge {
        private final int    to;
        private final double weight; // travel time in simulation-minutes
        public Edge(int to, double weight) { this.to = to; this.weight = weight; }
        public int    getTo()     { return to; }
        public double getWeight() { return weight; }
    }

    private final Map<Integer, Node>       nodes;
    private final Map<Integer, List<Edge>> adj;

    public Graph() {
        nodes = new HashMap<>();
        adj   = new HashMap<>();
    }

    // ── Mutation ─────────────────────────────────────────────────

    public void addNode(Node n) {
        nodes.put(n.getId(), n);
        adj.putIfAbsent(n.getId(), new ArrayList<>());
    }

    public void addEdge(int from, int to, double w) {
        adj.computeIfAbsent(from, k -> new ArrayList<>()).add(new Edge(to, w));
    }

    public void addUndirectedEdge(int a, int b, double w) {
        addEdge(a, b, w);
        addEdge(b, a, w);
    }

    public void removeEdge(int from, int to) {
        List<Edge> edges = adj.get(from);
        if (edges != null) edges.removeIf(e -> e.getTo() == to);
    }

    public void removeUndirectedEdge(int a, int b) {
        removeEdge(a, b);
        removeEdge(b, a);
    }

    // ── Query ─────────────────────────────────────────────────────

    public Node         getNode(int id)  { return nodes.get(id); }
    public int          nodeCount()      { return nodes.size(); }
    public Set<Integer> nodeIds()        { return nodes.keySet(); }
    public List<Edge>   getEdges(int id) {
        return adj.getOrDefault(id, Collections.emptyList());
    }

    // ── Dijkstra ─────────────────────────────────────────────────

    /**
     * Returns shortest travel time from src to every other node.
     * Unreachable nodes get Double.MAX_VALUE / 2.
     */
    public double[] dijkstra(int src) {
        List<Integer>        idList = new ArrayList<>(nodes.keySet());
        Map<Integer,Integer> idx    = new HashMap<>();
        for (int i = 0; i < idList.size(); i++) idx.put(idList.get(i), i);

        double[] dist = new double[idList.size()];
        Arrays.fill(dist, Double.MAX_VALUE / 2);
        dist[idx.get(src)] = 0;

        PriorityQueue<double[]> pq =
                new PriorityQueue<>(Comparator.comparingDouble(a -> a[0]));
        pq.offer(new double[]{0, src});

        while (!pq.isEmpty()) {
            double[] cur   = pq.poll();
            int      curId = (int) cur[1];
            int      ci    = idx.getOrDefault(curId, -1);
            if (ci < 0 || cur[0] > dist[ci]) continue;
            for (Edge e : getEdges(curId)) {
                int ni = idx.getOrDefault(e.getTo(), -1);
                if (ni < 0) continue;
                double nd = dist[ci] + e.getWeight();
                if (nd < dist[ni]) { dist[ni] = nd; pq.offer(new double[]{nd, e.getTo()}); }
            }
        }

        int      maxId = Collections.max(nodes.keySet());
        double[] result = new double[maxId + 1];
        Arrays.fill(result, Double.MAX_VALUE / 2);
        for (int i = 0; i < idList.size(); i++) result[idList.get(i)] = dist[i];
        return result;
    }

    public double shortestTime(int from, int to) {
        double[] d = dijkstra(from);
        return to < d.length ? d[to] : Double.MAX_VALUE / 2;
    }

    /**
     * Returns the ordered list of nodes on the shortest path from→to.
     * The list starts with the node AFTER 'from' and ends with 'to'.
     * Returns empty list if unreachable or from==to.
     */
    public List<Node> getShortestPath(int from, int to) {
        if (from == to) return new ArrayList<>();

        // Run Dijkstra while recording predecessors
        Map<Integer, Integer> prev = new HashMap<>();
        Map<Integer, Double>  dist = new HashMap<>();
        for (int id : nodes.keySet()) dist.put(id, Double.MAX_VALUE / 2);
        dist.put(from, 0.0);

        PriorityQueue<int[]> pq =
                new PriorityQueue<>(Comparator.comparingDouble(a -> dist.getOrDefault(a[0], Double.MAX_VALUE)));
        pq.offer(new int[]{from});

        while (!pq.isEmpty()) {
            int cur = pq.poll()[0];
            if (cur == to) break;
            for (Edge e : getEdges(cur)) {
                double nd = dist.get(cur) + e.getWeight();
                if (nd < dist.getOrDefault(e.getTo(), Double.MAX_VALUE / 2)) {
                    dist.put(e.getTo(), nd);
                    prev.put(e.getTo(), cur);
                    pq.offer(new int[]{e.getTo()});
                }
            }
        }

        // Reconstruct path from 'to' back to 'from'.
        // First check if 'to' is reachable — if prev doesn't contain it
        // and it's not the source, then no path exists.
        if (!prev.containsKey(to)) return new ArrayList<>();

        List<Node> path = new ArrayList<>();
        Integer cur = to;
        while (cur != null && cur != from) {
            path.add(0, nodes.get(cur));
            cur = prev.get(cur);
        }
        return path;
    }
}