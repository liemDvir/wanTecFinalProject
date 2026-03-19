package model;

import java.util.List;
import java.util.ArrayList;

/**
 * ═══════════════════════════════════════════════════
 *  Model — the M in MVC.
 *
 *  Responsibilities:
 *    - Hold all simulation data.
 *    - Provide generic operations on that data
 *      (add order, get couriers, advance time, etc.)
 *
 *  Rules:
 *    - NO decision making.
 *    - NO "which courier is best" logic.
 *    - NO knowledge of View or Controller.
 *    - Every method here is a simple data operation.
 * ═══════════════════════════════════════════════════
 */
public class Model {

    private Graph          graph;
    private Restaurant     restaurant;
    private List<Order>    orders;
    private List<Courier>  couriers;
    private int            currentTime;

    public Model() {
        this.orders      = new ArrayList<>();
        this.couriers    = new ArrayList<>();
        this.currentTime = 0;
    }

    // ── Graph ────────────────────────────────────────────────────
    public Graph      getGraph()              { return graph; }
    public void       setGraph(Graph g)       { this.graph = g; }

    // ── Restaurant ───────────────────────────────────────────────
    public Restaurant getRestaurant()         { return restaurant; }
    public void       setRestaurant(Restaurant r) { this.restaurant = r; }

    // ── Orders ───────────────────────────────────────────────────
    public List<Order> getOrders()            { return orders; }
    public void        addOrder(Order o)      { orders.add(o); }
    public void        setOrders(List<Order> o) { this.orders = o; }

    // ── Couriers ─────────────────────────────────────────────────
    public List<Courier> getCouriers()              { return couriers; }
    public void          addCourier(Courier c)      { couriers.add(c); }
    public void          setCouriers(List<Courier> c) { this.couriers = c; }

    // ── Time ─────────────────────────────────────────────────────
    public int  getCurrentTime()              { return currentTime; }
    public void setCurrentTime(int t)         { this.currentTime = t; }
    public void advanceTime()                 { this.currentTime++; }

    // ── Generic queries (no decisions — just data) ────────────────

    /** Orders that are available for assignment — WAITING only, not ASSIGNED. */
    public List<Order> getWaitingOrders() {
        List<Order> result = new ArrayList<>();
        for (Order o : orders)
            if (o.isWaiting()) result.add(o);
        return result;
    }

    /** All couriers that are idle and waiting for a new order. */
    public List<Courier> getAvailableCouriers() {
        List<Courier> result = new ArrayList<>();
        for (Courier c : couriers)
            if (c.getStatus() == Courier.Status.AVAILABLE) result.add(c);
        return result;
    }
}