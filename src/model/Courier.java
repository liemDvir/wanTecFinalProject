package model;

import java.util.ArrayList;
import java.util.List;

public class Courier {

    private int          id;
    private String       controlArea;
    private String       currentLocation;
    private int          estimatedAvailableTimeMinutes;
    private List<String> routePlan;
    private int          capacity;        // max simultaneous orders
    private int          currentCapacity; // how many orders courier currently carries
    private Status       status;

    public enum Status { AVAILABLE, EN_ROUTE, WAITING }

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
    public int          getId()                              { return id; }
    public void         setId(int id)                        { this.id = id; }
    public String       getControlArea()                     { return controlArea; }
    public void         setControlArea(String s)             { this.controlArea = s; }
    public String       getCurrentLocation()                 { return currentLocation; }
    public void         setCurrentLocation(String s)         { this.currentLocation = s; }
    public int          getEstimatedAvailableTimeMinutes()   { return estimatedAvailableTimeMinutes; }
    public void         setEstimatedAvailableTimeMinutes(int t) { this.estimatedAvailableTimeMinutes = t; }
    public List<String> getRoutePlan()                       { return routePlan; }
    public void         setRoutePlan(List<String> r)         { this.routePlan = r; }
    public int          getCapacity()                        { return capacity; }
    public void         setCapacity(int c)                   { this.capacity = c; }
    public int          getCurrentCapacity()                 { return currentCapacity; }
    public void         setCurrentCapacity(int c)            { this.currentCapacity = c; }
    public Status       getStatus()                          { return status; }
    public void         setStatus(Status s)                  { this.status = s; }

    // ── Business logic ───────────────────────────────────────────

    /**
     * Assign an order to this courier.
     * FIX: was empty (TODO) — now adds pickup+dropoff to routePlan
     * and increments currentCapacity.
     */
    public void assignOrder(Order order) {
        if (routePlan == null) routePlan = new ArrayList<>();
        routePlan.add("PICKUP:"  + order.getId());
        routePlan.add("DROPOFF:" + order.getId());
        currentCapacity++;
        setStatus(Status.WAITING); // courier heads to restaurant to wait/pick up
    }

    /** True when the courier can still take another order. */
    public boolean hasCapacity() {
        return currentCapacity < capacity;
    }

    @Override public String toString() {
        return "Courier{id=" + id + ", status=" + status +
                ", capacity=" + currentCapacity + "/" + capacity +
                ", availableAt=" + estimatedAvailableTimeMinutes + "}";
    }
}