package model;

/**
 * Tests for Node.java
 * Location: test/model/NodeTest.java
 */
public class NodeTest {
    static int passed = 0, failed = 0;

    public static void main(String[] args) {
        System.out.println("=== NodeTest ===\n");

        // Construction
        testConstructor_twoParam();
        testConstructor_withLabel();

        // Getters / Setters
        testSetters();

        // Equality
        testEquals_sameId_sameCoords();
        testEquals_sameId_differentCoords();   // edge: id is the only equality key
        testEquals_differentId();
        testEquals_null();
        testEquals_nonNodeObject();

        // HashCode
        testHashCode_consistentWithEquals();

        // Edge cases
        testNegativeCoordinates();
        testZeroId();
        testEmptyLabel();

        printResults();
    }

    static void testConstructor_twoParam() {
        Node n = new Node(1, 2.0, 3.0);
        assertEqual("id",    1,   n.getId());
        assertEqual("x",     2.0, n.getX());
        assertEqual("y",     3.0, n.getY());
        assertEqual("label default", "", n.getLabel());
    }

    static void testConstructor_withLabel() {
        Node n = new Node(5, 0, 0, "Hub");
        assertEqual("label", "Hub", n.getLabel());
    }

    static void testSetters() {
        Node n = new Node(1, 0, 0);
        n.setId(99); n.setX(4.4); n.setY(5.5); n.setLabel("Test");
        assertEqual("setId",    99,     n.getId());
        assertEqual("setX",     4.4,    n.getX());
        assertEqual("setY",     5.5,    n.getY());
        assertEqual("setLabel", "Test", n.getLabel());
    }

    static void testEquals_sameId_sameCoords() {
        assertTrue("same id+coords equal", new Node(3,1,1).equals(new Node(3,1,1)));
    }

    static void testEquals_sameId_differentCoords() {
        // CRITICAL: equality is by id only — coords don't matter
        assertTrue("same id diff coords still equal", new Node(3,0,0).equals(new Node(3,9,9)));
    }

    static void testEquals_differentId() {
        assertFalse("different id not equal", new Node(1,0,0).equals(new Node(2,0,0)));
    }

    static void testEquals_null() {
        assertFalse("not equal to null", new Node(1,0,0).equals(null));
    }

    static void testEquals_nonNodeObject() {
        assertFalse("not equal to String", new Node(1,0,0).equals("1"));
    }

    static void testHashCode_consistentWithEquals() {
        Node a = new Node(7, 0, 0);
        Node b = new Node(7, 9, 9);
        assertTrue("equal nodes same hashCode", a.hashCode() == b.hashCode());
    }

    static void testNegativeCoordinates() {
        Node n = new Node(1, -100.5, -200.5);
        assertEqual("negative x", -100.5, n.getX());
        assertEqual("negative y", -200.5, n.getY());
    }

    static void testZeroId() {
        Node n = new Node(0, 0, 0);
        assertEqual("zero id", 0, n.getId());
    }

    static void testEmptyLabel() {
        Node n = new Node(1, 0, 0, "");
        assertEqual("empty label", "", n.getLabel());
    }

    static void printResults() {
        System.out.println("\nNodeTest: " + passed + " passed, " + failed + " failed");
    }
    static void assertTrue(String name, boolean c) {
        if (c) { System.out.println("  [PASS] " + name); passed++; }
        else   { System.out.println("  [FAIL] " + name); failed++; }
    }
    static void assertFalse(String name, boolean c) { assertTrue(name, !c); }
    static void assertEqual(String name, int e, int g) {
        if (e == g) { System.out.println("  [PASS] " + name); passed++; }
        else { System.out.println("  [FAIL] " + name + " — expected=" + e + " got=" + g); failed++; }
    }
    static void assertEqual(String name, double e, double g) {
        if (Math.abs(e-g) < 1e-9) { System.out.println("  [PASS] " + name); passed++; }
        else { System.out.println("  [FAIL] " + name + " — expected=" + e + " got=" + g); failed++; }
    }
    static void assertEqual(String name, Object e, Object g) {
        boolean ok = e == null ? g == null : e.equals(g);
        if (ok) { System.out.println("  [PASS] " + name); passed++; }
        else { System.out.println("  [FAIL] " + name + " — expected=" + e + " got=" + g); failed++; }
    }
}