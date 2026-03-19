//package model;
//
//import java.util.ArrayList;
//
///**
// * Tests for Courier.java
// * Location: test/model/CourierTest.java
// */
//public class CourierTest {
//    static int passed = 0, failed = 0;
//
//    public static void main(String[] args) {
//        System.out.println("=== CourierTest ===\n");
//
//        testInitialState();
//        testHasCapacity_empty();
//        testHasCapacity_atLimit();
//        testHasCapacity_overLimit();
//        testAssignOrder_incrementsCapacity();
//        testAssignOrder_addsToRoutePlan();
//        testAssignOrder_routePlanFormat();
//        testAssignOrder_setsStatusWaiting();
//        testSettersGetters();
//
//        printResults();
//    }
//
//    static void testInitialState() {
//        Courier c = makeCourier(2, Courier.Status.AVAILABLE);
//        assertEqual("id",              1,                       c.getId());
//        assertEqual("capacity",        2,                       c.getCapacity());
//        assertEqual("currentCapacity", 0,                       c.getCurrentCapacity());
//        assertEqual("status",          Courier.Status.AVAILABLE, c.getStatus());
//        assertTrue("routePlan empty",  c.getRoutePlan().isEmpty());
//    }
//
//    static void testHasCapacity_empty() {
//        assertTrue("hasCapacity when empty", makeCourier(2, Courier.Status.AVAILABLE).hasCapacity());
//    }
//
//    static void testHasCapacity_atLimit() {
//        Courier c = makeCourier(1, Courier.Status.AVAILABLE);
//        c.assignOrder(makeOrder(1));
//        assertFalse("no capacity when full", c.hasCapacity());
//    }
//
//    static void testHasCapacity_overLimit() {
//        Courier c = makeCourier(1, Courier.Status.AVAILABLE);
//        c.assignOrder(makeOrder(1));
//        // currentCapacity (1) >= capacity (1) → no capacity
//        assertFalse("no capacity over limit", c.hasCapacity());
//    }
//
//    static void testAssignOrder_incrementsCapacity() {
//        Courier c = makeCourier(3, Courier.Status.AVAILABLE);
//        c.assignOrder(makeOrder(1));
//        assertEqual("currentCapacity after 1 assign", 1, c.getCurrentCapacity());
//        c.assignOrder(makeOrder(2));
//        assertEqual("currentCapacity after 2 assigns", 2, c.getCurrentCapacity());
//    }
//
//    static void testAssignOrder_addsToRoutePlan() {
//        Courier c = makeCourier(2, Courier.Status.AVAILABLE);
//        c.assignOrder(makeOrder(5));
//        assertEqual("routePlan size after 1 order", 2, c.getRoutePlan().size());
//    }
//
//    static void testAssignOrder_routePlanFormat() {
//        Courier c = makeCourier(2, Courier.Status.AVAILABLE);
//        c.assignOrder(makeOrder(42));
//        assertTrue("PICKUP entry",  c.getRoutePlan().get(0).startsWith("PICKUP:"));
//        assertTrue("DROPOFF entry", c.getRoutePlan().get(1).startsWith("DROPOFF:"));
//        assertTrue("PICKUP has order id",  c.getRoutePlan().get(0).contains("42"));
//        assertTrue("DROPOFF has order id", c.getRoutePlan().get(1).contains("42"));
//    }
//
//    static void testAssignOrder_setsStatusWaiting() {
//        Courier c = makeCourier(2, Courier.Status.AVAILABLE);
//        c.assignOrder(makeOrder(1));
//        assertEqual("status WAITING after assign", Courier.Status.WAITING, c.getStatus());
//    }
//
//    static void testSettersGetters() {
//        Courier c = makeCourier(2, Courier.Status.AVAILABLE);
//        c.setControlArea("South");
//        c.setCurrentLocation("Hub-Z");
//        c.setEstimatedAvailableTimeMinutes(15);
//        c.setCurrentCapacity(1);
//
//        assertEqual("controlArea",    "South", c.getControlArea());
//        assertEqual("currentLocation","Hub-Z",  c.getCurrentLocation());
//        assertEqual("availableTime",  15,       c.getEstimatedAvailableTimeMinutes());
//        assertEqual("currentCapacity",1,        c.getCurrentCapacity());
//    }
//
//    // ── Helpers ──────────────────────────────────────────────────
//    static Courier makeCourier(int capacity, Courier.Status status) {
//        return new Courier(1, "North", "Hub-A", 0, new ArrayList<>(), capacity, status);
//    }
//
//    static Order makeOrder(int id) {
//        return new Order(id, new Node(1,0,0), new Node(2,1,1), 0, 10, 10, 45, Order.Status.WAITING);
//    }
//
//    static void printResults() {
//        System.out.println("\nCourierTest: " + passed + " passed, " + failed + " failed");
//    }
//
//    static void assertTrue(String n, boolean c) {
//        if (c) { System.out.println("  [PASS] "+n); passed++; }
//        else   { System.out.println("  [FAIL] "+n); failed++; }
//    }
//    static void assertFalse(String n, boolean c) { assertTrue(n, !c); }
//    static void assertEqual(String n, int e, int g) {
//        if (e==g) { System.out.println("  [PASS] "+n); passed++; }
//        else { System.out.println("  [FAIL] "+n+" — expected="+e+" got="+g); failed++; }
//    }
//    static void assertEqual(String n, Object e, Object g) {
//        boolean ok = e==null ? g==null : e.equals(g);
//        if (ok) { System.out.println("  [PASS] "+n); passed++; }
//        else { System.out.println("  [FAIL] "+n+" — expected="+e+" got="+g); failed++; }
//    }
//}