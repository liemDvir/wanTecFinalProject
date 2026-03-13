package model;

/**
 * A vertex in the city road-network graph.
 * Replaces org.w3c.dom.Node which was used incorrectly.
 */
public class Node {
    private int    id;
    private double x;
    private double y;
    private String label;

    public Node(int id, double x, double y)               { this(id, x, y, ""); }
    public Node(int id, double x, double y, String label) {
        this.id = id; this.x = x; this.y = y; this.label = label;
    }

    public int    getId()            { return id;    }
    public void   setId(int id)      { this.id = id; }
    public double getX()             { return x;  }
    public void   setX(double x)     { this.x = x; }
    public double getY()             { return y;  }
    public void   setY(double y)     { this.y = y; }
    public String getLabel()         { return label; }
    public void   setLabel(String l) { this.label = l; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Node)) return false;
        return id == ((Node) o).id;
    }
    @Override public int hashCode() { return Integer.hashCode(id); }
    @Override public String toString() {
        return "Node{id=" + id + ", label='" + label + "'}";
    }
}