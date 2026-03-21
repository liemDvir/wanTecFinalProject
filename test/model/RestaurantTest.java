package model;

/**
 * Tests for Restaurant.java
 * Location: test/model/RestaurantTest.java
 */
public class RestaurantTest {
    static int passed = 0, failed = 0;

    public static void main(String[] args) {
        System.out.println("=== RestaurantTest ===\n");

        testConstructor();
        testSetters();
        testLocationReference();
        testToStringContainsName();
        testZeroPrepTime();        // edge: instant food
        testNullLocation();        // edge: no location set yet

        printResults();
    }

    static void testConstructor() {
        Node loc = new Node(5, 1, 2, "Center");
        Restaurant r = new Restaurant(1, "Pizza", loc, 15);
        assertEqual("id",       1,       r.getId());
        assertEqual("name",     "Pizza", r.getName());
        assertEqual("prepTime", 15,      r.getDefaultPrepTime());
        assertTrue("location",  r.getLocation().equals(loc));
    }

    static void testSetters() {
        Restaurant r = new Restaurant(1, "Old", new Node(1,0,0), 10);
        Node newLoc = new Node(2, 5, 5);
        r.setId(99);
        r.setName("New");
        r.setLocation(newLoc);
        r.setDefaultPrepTime(25);

        assertEqual("setId",       99,    r.getId());
        assertEqual("setName",     "New", r.getName());
        assertEqual("setPrepTime", 25,    r.getDefaultPrepTime());
        assertTrue("setLocation",  r.getLocation().equals(newLoc));
    }

    static void testLocationReference() {
        // getLocation should return the exact same Node object, not a copy
        Node loc = new Node(3, 0, 0);
        Restaurant r = new Restaurant(1, "R", loc, 10);
        assertTrue("same reference", r.getLocation() == loc);
    }

    static void testToStringContainsName() {
        Restaurant r = new Restaurant(1, "BurgerPlace", new Node(0,0,0), 10);
        assertTrue("toString has name", r.toString().contains("BurgerPlace"));
    }

    static void testZeroPrepTime() {
        Restaurant r = new Restaurant(1, "FastFood", new Node(0,0,0), 0);
        assertEqual("zero prepTime", 0, r.getDefaultPrepTime());
    }

    static void testNullLocation() {
        // Setting null location should not crash
        Restaurant r = new Restaurant(1, "R", null, 10);
        assertTrue("null location no crash", r.getLocation() == null);
    }

    static void printResults() {
        System.out.println("\nRestaurantTest: " + passed + " passed, " + failed + " failed");
    }
    static void assertTrue(String n, boolean c) {
        if (c) { System.out.println("  [PASS] "+n); passed++; }
        else   { System.out.println("  [FAIL] "+n); failed++; }
    }
    static void assertEqual(String n, int e, int g) {
        if (e==g) { System.out.println("  [PASS] "+n); passed++; }
        else { System.out.println("  [FAIL] "+n+" — expected="+e+" got="+g); failed++; }
    }
    static void assertEqual(String n, Object e, Object g) {
        boolean ok = e==null ? g==null : e.equals(g);
        if (ok) { System.out.println("  [PASS] "+n); passed++; }
        else { System.out.println("  [FAIL] "+n+" — expected="+e+" got="+g); failed++; }
    }
}