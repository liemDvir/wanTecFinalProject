package model;

import java.util.ArrayList;
import java.util.List;

public class Courier {

    private int          id;
    private String       controlArea;
    private String       currentLocation;   // human-readable label
    private Node         currentNode;       // actual position on the graph
    private Node         previousNode;      // position before last move — for smooth animation
    private int          estimatedAvailableTimeMinutes;
    private List<String> routePlan;
    private int          capacity;
    private int          currentCapacity;
    private Status       status;

    // The path the courier is currently walking, node by node.
    // index 0 = next node to move to, last = destination.
    // Set by Controller via Dijkstra before the courier starts moving.
    private List<Node>   currentPath;

    // The order the courier is currently carrying (null if none)
    private Order        activeOrder;

    public enum Status {
        AVAILABLE,              // idle, waiting for an assignment
        HEADING_TO_RESTAURANT,  // walking toward the restaurant to pick up
        WAITING_AT_RESTAURANT,  // at the restaurant, food not ready yet
        DELIVERING              // walking toward the customer
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
        this.currentPath = new ArrayList<>();
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
    public List<Node>   getCurrentPath()                        { return currentPath; }
    public void         setCurrentPath(List<Node> path)         { this.currentPath = path; }
    public Order        getActiveOrder()                        { return activeOrder; }
    public void         setActiveOrder(Order o)                 { this.activeOrder = o; }

    // ── Movement helpers (called by Controller each tick) ────────

    /**
     * Move one step forward along currentPath.
     * Saves the current node as previousNode before moving —
     * the View uses both to interpolate smooth animation.
     * Returns true if the courier reached the destination.
     */
    public boolean stepForward() {
        if (currentPath == null || currentPath.isEmpty()) return true;
        previousNode = currentNode;           // remember where we came from
        currentNode  = currentPath.remove(0); // move to next node
        return currentPath.isEmpty();
    }

    /** True if the courier has no more steps to walk. */
    public boolean hasReachedDestination() {
        return currentPath == null || currentPath.isEmpty();
    }

    // ── Order management ─────────────────────────────────────────

    /**
     * Assign an order — stores it as activeOrder and updates routePlan.
     * Status is set to HEADING_TO_RESTAURANT by the Controller.
     */
    public void assignOrder(Order order) {
        if (routePlan == null) routePlan = new ArrayList<>();
        routePlan.add("PICKUP:"  + order.getId());
        routePlan.add("DROPOFF:" + order.getId());
        currentCapacity++;
        activeOrder = order;
        // Controller will set status + path after calling this
    }

    /**
     * Complete the active delivery — remove it and become available again.
     */
    public void completeDelivery() {
        if (routePlan != null) {
            routePlan.remove("PICKUP:"  + (activeOrder != null ? activeOrder.getId() : ""));
            routePlan.remove("DROPOFF:" + (activeOrder != null ? activeOrder.getId() : ""));
        }
        currentCapacity = 0;
        activeOrder     = null;
        currentPath     = new ArrayList<>();
        setStatus(Status.AVAILABLE);
    }

    /** True when the courier can still take another order. */
    public boolean hasCapacity() { return currentCapacity < capacity; }

    @Override public String toString() {
        return "Courier{id=" + id + ", status=" + status +
                ", pos=" + (currentNode != null ? currentNode.getId() : "?") +
                ", capacity=" + currentCapacity + "/" + capacity + "}";
    }
}