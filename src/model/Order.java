package model;

public class Order {

    private int    id;
    private Node   pickup;
    private Node   dropoff;
    private int    orderTime;
    private int    prepTime;
    private int    readyTime;
    private int    deadline;
    private Status status;

    public enum Status {
        WAITING,    // not yet assigned to any courier
        ASSIGNED,   // assigned to a courier — no other courier can take it
        PICKED_UP,  // courier physically picked up the food
        DELIVERED   // delivered to customer
    }

    public Order() {}

    public Order(int id, Node pickup, Node dropoff,
                 int orderTime, int prepTime, int readyTime, int deadline, Status status) {
        setId(id);
        setPickup(pickup);
        setDropoff(dropoff);
        setOrderTime(orderTime);
        setPrepTime(prepTime);
        setReadyTime(readyTime);
        setDeadline(deadline);
        setStatus(status);
    }

    // ── Getters / Setters ────────────────────────────────────────
    public int    getId()                  { return id; }
    public void   setId(int id)            { this.id = id; }
    public Node   getPickup()              { return pickup; }
    public void   setPickup(Node p)        { this.pickup = p; }
    public Node   getDropoff()             { return dropoff; }
    public void   setDropoff(Node d)       { this.dropoff = d; }
    public int    getOrderTime()           { return orderTime; }
    public void   setOrderTime(int t)      { this.orderTime = t; }
    public int    getPrepTime()            { return prepTime; }
    public void   setPrepTime(int t)       { this.prepTime = t; }
    public int    getReadyTime()           { return readyTime; }
    public void   setReadyTime(int t)      { this.readyTime = t; }
    public int    getDeadline()            { return deadline; }
    public void   setDeadline(int t)       { this.deadline = t; }
    public Status getStatus()              { return status; }
    public void   setStatus(Status s)      { this.status = s; }

    // ── Status helpers ───────────────────────────────────────────
    public boolean isWaiting()    { return status == Status.WAITING;   }
    public boolean isAssigned()   { return status == Status.ASSIGNED;  }
    public boolean isPickedUp()   { return status == Status.PICKED_UP; }
    public boolean isDelivered()  { return status == Status.DELIVERED; }

    public void markAssigned()   { this.status = Status.ASSIGNED;  }
    public void markPickedUp()   { this.status = Status.PICKED_UP; }
    public void markDelivered()  { this.status = Status.DELIVERED; }

    @Override public String toString() {
        return "Order{id=" + id + ", orderTime=" + orderTime +
                ", readyTime=" + readyTime + ", deadline=" + deadline +
                ", status=" + status + "}";
    }
}