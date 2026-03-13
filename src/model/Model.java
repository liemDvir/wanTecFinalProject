package model;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

public class Model {

    private static final Logger LOGGER = Logger.getLogger(Model.class.getName());

    private List<Order>   orders;
    private List<Courier> couriers;
    private int           currentTime;


    public Model() {
        LOGGER.info("creating new model");
        orders   = new ArrayList<>();   // FIX: must initialise BEFORE add()
        couriers = new ArrayList<>();
        currentTime = 0;

        orders.add(createRandomOrder());
        couriers.add(createRandomCourier());
    }

    public Model(List<Order> orders, List<Courier> couriers) {
        this.orders   = orders;
        this.couriers = couriers;
        this.currentTime = 0;
    }

    public Model(List<Order> orders, List<Courier> couriers, int currentTime) {
        this.orders      = orders;
        this.couriers    = couriers;
        this.currentTime = currentTime;
    }

    // ── Getters / Setters ────────────────────────────────────────
    public List<Order>   getOrders()               { return orders; }
    public void          setOrders(List<Order> o)  { this.orders = o; }
    public List<Courier> getCouriers()             { return couriers; }
    public void          setCouriers(List<Courier> c) { this.couriers = c; }
    public int           getCurrentTime()          { return currentTime; }
    public void          setCurrentTime(int t)     { this.currentTime = t; }

    // ── Factory helpers ──────────────────────────────────────────


    public Order createRandomOrder() {
        Random random  = new Random();
        Node pickup  = new Node(random.nextInt(100), random.nextDouble() * 10, random.nextDouble() * 10, "Pickup");
        Node dropoff = new Node(random.nextInt(100) + 100, random.nextDouble() * 10, random.nextDouble() * 10, "Dropoff");

        int orderTime = random.nextInt(101);
        int prepTime  = 15;
        int readyTime = orderTime + prepTime;
        int deadline  = readyTime + 30;

        return new Order(random.nextInt(1000) + 1,
                pickup, dropoff,
                orderTime, prepTime, readyTime, deadline,
                Order.Status.WAITING);
    }


    public Courier createRandomCourier() {
        Random random   = new Random();
        List<String> routePlan = new ArrayList<>();
        int id          = random.nextInt(1000) + 1;
        String area     = "Central-" + random.nextInt(10);
        String location = "Hub-"     + random.nextInt(20);
        int availTime   = random.nextInt(61);
        int capacity    = random.nextInt(5) + 1;
        return new Courier(id, area, location, availTime, routePlan,
                capacity, 0, Courier.Status.AVAILABLE);
    }
}