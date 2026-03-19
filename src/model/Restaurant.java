package model;

/**
 * Represents a restaurant in the simulation.
 * The restaurant is the pickup point for all orders.
 */
public class Restaurant {

    private int    id;
    private String name;
    private Node   location;        // the graph node where this restaurant sits
    private int    defaultPrepTime; // default food prep time in simulation-minutes

    public Restaurant(int id, String name, Node location, int defaultPrepTime) {
        this.id              = id;
        this.name            = name;
        this.location        = location;
        this.defaultPrepTime = defaultPrepTime;
    }

    // ── Getters / Setters ────────────────────────────────────────

    public int    getId()                       { return id; }
    public void   setId(int id)                 { this.id = id; }

    public String getName()                     { return name; }
    public void   setName(String name)          { this.name = name; }

    public Node   getLocation()                 { return location; }
    public void   setLocation(Node location)    { this.location = location; }

    public int    getDefaultPrepTime()          { return defaultPrepTime; }
    public void   setDefaultPrepTime(int t)     { this.defaultPrepTime = t; }

    @Override
    public String toString() {
        return "Restaurant{id=" + id + ", name='" + name +
                "', location=" + location + ", prepTime=" + defaultPrepTime + "}";
    }
}