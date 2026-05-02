package model;

import java.util.ArrayList;
import java.util.List;

public class Courier {

    private int          id;
    private String       controlArea;
    private String       currentLocation;
    private Node         currentNode;
    private Node         previousNode;
    private int          estimatedAvailableTimeMinutes;
    private List<String> routePlan;
    private int          capacity;
    private int          currentCapacity;
    private Status       status;

    // The raw Dijkstra path (one node per edge hop, no repetition).
    private List<Node>    currentPath   = new ArrayList<>();

    // Parallel list: edgeTicksList.get(i) = ticks required to reach currentPath.get(i)
    // from the previous node.  Set by Controller alongside currentPath.
    private List<Integer> edgeTicksList = new ArrayList<>();

    // ── Edge-traversal animation state ───────────────────────────
    // These fields let the View render smooth movement along any edge,
    // regardless of how many ticks that edge takes.
    //
    // Invariant while a courier is MOVING:
    //   edgeOriginNode  = the node the courier started this edge from
    //   edgeDestNode    = the node the courier is heading toward (currentPath[0])
    //   edgeTicksTotal  = total ticks this edge requires  (≥ 1)
    //   edgeTicksDone   = ticks spent on this edge so far (1 … edgeTicksTotal)
    //
    // Visual position formula (used by MapView):
    //   fraction = (edgeTicksDone - 1 + animProgress) / edgeTicksTotal
    //   x = edgeOriginNode.x + (edgeDestNode.x - edgeOriginNode.x) * fraction
    //
    // This gives continuous, smooth movement:
    //   • At animProgress=0 the courier is exactly where last tick ended.
    //   • At animProgress=1 the courier is where this tick ends.
    private Node    edgeOriginNode = null;
    private Node    edgeDestNode   = null;
    private int     edgeTicksTotal = 1;
    private int     edgeTicksDone  = 0;

    // Set to true when an edge completes so the NEXT stepForward() call
    // starts the following edge cleanly without losing the animation data
    // needed for the completion tick.
    private boolean edgeCompleted  = false;

    private Order activeOrder;

    public enum Status {
        AVAILABLE,
        HEADING_TO_RESTAURANT,
        WAITING_AT_RESTAURANT,
        DELIVERING
    }

    // ── Full constructor ─────────────────────────────────────────
    public Courier(int id, String controlArea, String currentLocation,
                   int estimatedAvailableTimeMinutes, List<String> routePlan,
                   int capacity, int currentCapacity, Status status) {
        setId(id);
        setControlArea(controlArea);
        setCurrentLocation(currentLocation);
        setEstimatedAvailableTimeMinutes(estimatedAvailableTimeMinutes);
        setRoutePlan(routePlan);
        setCapacity(capacity);
        setCurrentCapacity(currentCapacity);
        setStatus(status);
    }

    // ── Convenience constructor (currentCapacity defaults to 0) ──
    public Courier(int id, String controlArea, String currentLocation,
                   int estimatedAvailableTimeMinutes, List<String> routePlan,
                   int capacity, Status status) {
        this(id, controlArea, currentLocation, estimatedAvailableTimeMinutes,
                routePlan, capacity, 0, status);
    }

    // ── Getters / Setters ────────────────────────────────────────
    public int          getId()                                 { return id; }
    public void         setId(int id)                           { this.id = id; }
    public String       getControlArea()                        { return controlArea; }
    public void         setControlArea(String s)                { this.controlArea = s; }
    public String       getCurrentLocation()                    { return currentLocation; }
    public void         setCurrentLocation(String s)            { this.currentLocation = s; }
    public Node         getCurrentNode()                        { return currentNode; }
    public void         setCurrentNode(Node n)                  { this.currentNode = n; }
    public Node         getPreviousNode()                       { return previousNode; }
    public void         setPreviousNode(Node n)                 { this.previousNode = n; }
    public int          getEstimatedAvailableTimeMinutes()      { return estimatedAvailableTimeMinutes; }
    public void         setEstimatedAvailableTimeMinutes(int t) { this.estimatedAvailableTimeMinutes = t; }
    public List<String> getRoutePlan()                          { return routePlan; }
    public void         setRoutePlan(List<String> r)            { this.routePlan = r; }
    public int          getCapacity()                           { return capacity; }
    public void         setCapacity(int c)                      { this.capacity = c; }
    public int          getCurrentCapacity()                    { return currentCapacity; }
    public void         setCurrentCapacity(int c)               { this.currentCapacity = c; }
    public Status       getStatus()                             { return status; }
    public void         setStatus(Status s)                     { this.status = s; }
    public Order        getActiveOrder()                        { return activeOrder; }
    public void         setActiveOrder(Order o)                 { this.activeOrder = o; }

    // ── Path setters ─────────────────────────────────────────────

    /**
     * Set the courier's path together with the per-edge tick counts.
     *
     * @param path      raw Dijkstra path (list of destination nodes)
     * @param ticksList parallel list; ticksList.get(i) = ticks to reach path.get(i)
     *                  from the previous node.  May be null / empty (defaults to 1/edge).
     */
    public void setCurrentPath(List<Node> path, List<Integer> ticksList) {
        this.currentPath    = (path      != null) ? path      : new ArrayList<>();
        this.edgeTicksList  = (ticksList != null) ? ticksList : new ArrayList<>();
        this.edgeDestNode   = null;
        this.edgeOriginNode = null;
        this.edgeCompleted  = false;
        this.edgeTicksDone  = 0;
        this.edgeTicksTotal = 1;
    }

    /** Backward-compatible single-argument setter (no weight info — 1 tick/edge). */
    public void setCurrentPath(List<Node> path) {
        setCurrentPath(path, new ArrayList<>());
    }

    public List<Node> getCurrentPath() { return currentPath; }

    // ── Animation-state getters (read by MapView) ─────────────────
    public Node getEdgeOriginNode() { return edgeOriginNode; }
    public Node getEdgeDestNode()   { return edgeDestNode;   }
    public int  getEdgeTicksDone()  { return edgeTicksDone;  }
    public int  getEdgeTicksTotal() { return edgeTicksTotal; }

    // ── Movement helpers ──────────────────────────────────────────

    /**
     * Advance the courier by one simulation tick along its current path.
     *
     * How it works
     * ────────────
     * Rather than moving one node per tick (which ignores edge weights), the
     * courier tracks an ongoing edge traversal:
     *
     *   • On each tick edgeTicksDone is incremented.
     *   • The courier only moves to the next node when edgeTicksDone reaches
     *     edgeTicksTotal (the rounded edge weight in ticks).
     *   • edgeCompleted is set to true on the arrival tick so that the
     *     completion tick's animation can still show the final leg of the
     *     journey (edgeDestNode / edgeTicksTotal are preserved for that tick).
     *     On the NEXT stepForward() call the new edge initialises cleanly.
     *
     * Animation formula used by MapView:
     *   fraction = (edgeTicksDone - 1 + animProgress) / edgeTicksTotal
     *   where animProgress ∈ [0,1] across the 600ms tick window.
     *
     * @return true when the courier has reached the end of its path.
     */
    public boolean stepForward() {
        if (currentPath == null || currentPath.isEmpty()) return true;

        // ── Start a new edge ──────────────────────────────────────
        // Triggered on the very first call, and after each edge completes.
        if (edgeCompleted || edgeDestNode == null) {
            edgeCompleted  = false;
            edgeOriginNode = currentNode;
            edgeDestNode   = currentPath.get(0);
            edgeTicksTotal = edgeTicksList.isEmpty() ? 1 : edgeTicksList.get(0);
            edgeTicksDone  = 0;
        }

        edgeTicksDone++;

        if (edgeTicksDone < edgeTicksTotal) {
            // Still traversing this edge — don't move currentNode yet.
            return false;
        }

        // ── Edge complete — advance to destination node ───────────
        previousNode = currentNode;
        currentNode  = currentPath.remove(0);
        if (!edgeTicksList.isEmpty()) edgeTicksList.remove(0);

        // Mark completed so next call initialises the following edge.
        // We intentionally keep edgeDestNode and edgeTicksDone unchanged
        // so that the MapView can render the final 1-tick leg of the journey
        // during this tick's 600ms animation window.
        edgeCompleted = true;

        return currentPath.isEmpty();
    }

    /** True if the courier has no more steps to walk. */
    public boolean hasReachedDestination() {
        return currentPath == null || currentPath.isEmpty();
    }

    // ── Order management ─────────────────────────────────────────

    public void assignOrder(Order order) {
        if (routePlan == null) routePlan = new ArrayList<>();
        routePlan.add("PICKUP:"  + order.getId());
        routePlan.add("DROPOFF:" + order.getId());
        currentCapacity++;
        activeOrder = order;
    }

    public void completeDelivery() {
        if (routePlan != null) {
            routePlan.remove("PICKUP:"  + (activeOrder != null ? activeOrder.getId() : ""));
            routePlan.remove("DROPOFF:" + (activeOrder != null ? activeOrder.getId() : ""));
        }
        currentCapacity = 0;
        activeOrder     = null;
        currentPath     = new ArrayList<>();
        edgeTicksList   = new ArrayList<>();
        edgeOriginNode  = null;
        edgeDestNode    = null;
        edgeTicksDone   = 0;
        edgeTicksTotal  = 1;
        edgeCompleted   = false;
        setStatus(Status.AVAILABLE);
    }

    public boolean hasCapacity() { return currentCapacity < capacity; }

    @Override public String toString() {
        return "Courier{id=" + id + ", status=" + status +
                ", pos=" + (currentNode != null ? currentNode.getId() : "?") +
                ", capacity=" + currentCapacity + "/" + capacity + "}";
    }
}