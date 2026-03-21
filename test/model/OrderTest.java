package model;

/**
 * Tests for Order.java — including new ASSIGNED status
 * Location: test/model/OrderTest.java
 */
public class OrderTest {
    static int passed = 0, failed = 0;

    public static void main(String[] args) {
        System.out.println("=== OrderTest ===\n");

        testFullConstructor();
        testDefaultConstructor();
        testSetters();

        // Status transitions — correct flow
        testStatus_initialWaiting();
        testStatus_markAssigned();
        testStatus_markPickedUp();
        testStatus_markDelivered();

        // Status helpers
        testIsWaiting_trueOnlyWhenWaiting();
        testIsAssigned_trueOnlyWhenAssigned();
        testIsPickedUp_trueOnlyWhenPickedUp();
        testIsDelivered_trueOnlyWhenDelivered();

        // Edge cases
        testDeadline_sameAsReadyTime();       // deadline == readyTime (very tight)
        testOrderTime_zero();
        testPrepTime_zero();                  // instant food
        testNegativeOrderTime();              // shouldn't crash

        printResults();
    }

    static void testFullConstructor() {
        Node p = new Node(1,0,0), d = new Node(2,1,1);
        Order o = new Order(7, p, d, 5, 10, 15, 60, Order.Status.WAITING);
        assertEqual("id",        7,  o.getId());
        assertEqual("orderTime", 5,  o.getOrderTime());
        assertEqual("prepTime",  10, o.getPrepTime());
        assertEqual("readyTime", 15, o.getReadyTime());
        assertEqual("deadline",  60, o.getDeadline());
        assertTrue("pickup",  o.getPickup().equals(p));
        assertTrue("dropoff", o.getDropoff().equals(d));
    }

    static void testDefaultConstructor() {
        Order o = new Order();
        assertTrue("default: no crash", true);
        // status is null — acceptable with default constructor
    }

    static void testSetters() {
        Order o = new Order();
        o.setId(3); o.setOrderTime(1); o.setPrepTime(5);
        o.setReadyTime(6); o.setDeadline(30);
        assertEqual("setId",        3,  o.getId());
        assertEqual("setOrderTime", 1,  o.getOrderTime());
        assertEqual("setPrepTime",  5,  o.getPrepTime());
        assertEqual("setReadyTime", 6,  o.getReadyTime());
        assertEqual("setDeadline",  30, o.getDeadline());
    }

    static void testStatus_initialWaiting() {
        assertEqual("initial WAITING", Order.Status.WAITING, makeOrder().getStatus());
    }

    static void testStatus_markAssigned() {
        Order o = makeOrder();
        o.markAssigned();
        assertEqual("after markAssigned", Order.Status.ASSIGNED, o.getStatus());
    }

    static void testStatus_markPickedUp() {
        Order o = makeOrder();
        o.markAssigned();
        o.markPickedUp();
        assertEqual("after markPickedUp", Order.Status.PICKED_UP, o.getStatus());
    }

    static void testStatus_markDelivered() {
        Order o = makeOrder();
        o.markAssigned();
        o.markPickedUp();
        o.markDelivered();
        assertEqual("after markDelivered", Order.Status.DELIVERED, o.getStatus());
    }

    static void testIsWaiting_trueOnlyWhenWaiting() {
        Order o = makeOrder();
        assertTrue("isWaiting when WAITING", o.isWaiting());
        o.markAssigned();
        assertFalse("isWaiting when ASSIGNED", o.isWaiting());
        o.markPickedUp();
        assertFalse("isWaiting when PICKED_UP", o.isWaiting());
    }

    static void testIsAssigned_trueOnlyWhenAssigned() {
        Order o = makeOrder();
        assertFalse("isAssigned when WAITING", o.isAssigned());
        o.markAssigned();
        assertTrue("isAssigned when ASSIGNED", o.isAssigned());
        o.markPickedUp();
        assertFalse("isAssigned when PICKED_UP", o.isAssigned());
    }

    static void testIsPickedUp_trueOnlyWhenPickedUp() {
        Order o = makeOrder();
        assertFalse("not pickedUp initially", o.isPickedUp());
        o.markAssigned();
        o.markPickedUp();
        assertTrue("isPickedUp", o.isPickedUp());
        o.markDelivered();
        assertFalse("not pickedUp when delivered", o.isPickedUp());
    }

    static void testIsDelivered_trueOnlyWhenDelivered() {
        Order o = makeOrder();
        assertFalse("not delivered initially", o.isDelivered());
        o.markAssigned(); o.markPickedUp(); o.markDelivered();
        assertTrue("isDelivered", o.isDelivered());
    }

    static void testDeadline_sameAsReadyTime() {
        // Edge: deadline == readyTime means zero delivery time allowed
        Order o = new Order(1, null, null, 0, 10, 10, 10, Order.Status.WAITING);
        assertEqual("deadline == readyTime", 10, o.getDeadline());
        assertEqual("readyTime", 10, o.getReadyTime());
    }

    static void testOrderTime_zero() {
        Order o = new Order(1, null, null, 0, 5, 5, 30, Order.Status.WAITING);
        assertEqual("orderTime zero", 0, o.getOrderTime());
    }

    static void testPrepTime_zero() {
        Order o = new Order(1, null, null, 10, 0, 10, 40, Order.Status.WAITING);
        assertEqual("prepTime zero", 0, o.getPrepTime());
        assertEqual("readyTime == orderTime when prepTime=0", 10, o.getReadyTime());
    }

    static void testNegativeOrderTime() {
        // Should not crash even with unusual input
        try {
            Order o = new Order(1, null, null, -5, 10, 5, 30, Order.Status.WAITING);
            assertTrue("negative orderTime no crash", true);
        } catch (Exception e) {
            assertTrue("negative orderTime no crash", false);
        }
    }

    static Order makeOrder() {
        return new Order(1, new Node(1,0,0), new Node(2,1,1), 0, 10, 10, 45, Order.Status.WAITING);
    }

    static void printResults() {
        System.out.println("\nOrderTest: " + passed + " passed, " + failed + " failed");
    }
    static void assertTrue(String n, boolean c) {
        if (c) { System.out.println("  [PASS] " + n); passed++; }
        else   { System.out.println("  [FAIL] " + n); failed++; }
    }
    static void assertFalse(String n, boolean c) { assertTrue(n, !c); }
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