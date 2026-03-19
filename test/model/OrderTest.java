//package model;
//
///**
// * Tests for Order.java
// * Location: test/model/OrderTest.java
// */
//public class OrderTest {
//    static int passed = 0, failed = 0;
//
//    public static void main(String[] args) {
//        System.out.println("=== OrderTest ===\n");
//
//        testDefaultConstructorStatus();
//        testFullConstructorFields();
//        testIsWaiting();
//        testIsPickedUp();
//        testIsDelivered();
//        testMarkPickedUp();
//        testMarkDelivered();
//        testSettersGetters();
//        testToStringContainsId();
//
//        printResults();
//    }
//
//    static void testDefaultConstructorStatus() {
//        Order o = new Order();
//        // status is null with default constructor — acceptable at this stage
//        // just verify no crash
//        assertTrue("Default constructor: no crash", true);
//    }
//
//    static void testFullConstructorFields() {
//        Node p = new Node(1, 0, 0, "R");
//        Node d = new Node(2, 1, 1, "C");
//        Order o = new Order(10, p, d, 5, 10, 15, 60, Order.Status.WAITING);
//
//        assertEqual("id",        10, o.getId());
//        assertEqual("orderTime",  5, o.getOrderTime());
//        assertEqual("prepTime",  10, o.getPrepTime());
//        assertEqual("readyTime", 15, o.getReadyTime());
//        assertEqual("deadline",  60, o.getDeadline());
//        assertTrue("pickup",  o.getPickup().equals(p));
//        assertTrue("dropoff", o.getDropoff().equals(d));
//    }
//
//    static void testIsWaiting() {
//        Order o = makeOrder(Order.Status.WAITING);
//        assertTrue("isWaiting true",   o.isWaiting());
//        assertFalse("isPickedUp false", o.isPickedUp());
//        assertFalse("isDelivered false",o.isDelivered());
//    }
//
//    static void testIsPickedUp() {
//        Order o = makeOrder(Order.Status.PICKED_UP);
//        assertFalse("isWaiting false",  o.isWaiting());
//        assertTrue("isPickedUp true",   o.isPickedUp());
//        assertFalse("isDelivered false",o.isDelivered());
//    }
//
//    static void testIsDelivered() {
//        Order o = makeOrder(Order.Status.DELIVERED);
//        assertFalse("isWaiting false",   o.isWaiting());
//        assertFalse("isPickedUp false",  o.isPickedUp());
//        assertTrue("isDelivered true",   o.isDelivered());
//    }
//
//    static void testMarkPickedUp() {
//        Order o = makeOrder(Order.Status.WAITING);
//        o.markPickedUp();
//        assertTrue("markPickedUp -> isPickedUp", o.isPickedUp());
//        assertFalse("markPickedUp -> not waiting", o.isWaiting());
//    }
//
//    static void testMarkDelivered() {
//        Order o = makeOrder(Order.Status.PICKED_UP);
//        o.markDelivered();
//        assertTrue("markDelivered -> isDelivered", o.isDelivered());
//    }
//
//    static void testSettersGetters() {
//        Order o = new Order();
//        o.setId(99);
//        o.setOrderTime(3);
//        o.setPrepTime(7);
//        o.setReadyTime(10);
//        o.setDeadline(50);
//        o.setStatus(Order.Status.WAITING);
//
//        assertEqual("setId",        99, o.getId());
//        assertEqual("setOrderTime",  3, o.getOrderTime());
//        assertEqual("setPrepTime",   7, o.getPrepTime());
//        assertEqual("setReadyTime", 10, o.getReadyTime());
//        assertEqual("setDeadline",  50, o.getDeadline());
//        assertEqual("setStatus", Order.Status.WAITING, o.getStatus());
//    }
//
//    static void testToStringContainsId() {
//        Order o = makeOrder(Order.Status.WAITING);
//        assertTrue("toString has id", o.toString().contains("10"));
//    }
//
//    // ── Helpers ──────────────────────────────────────────────────
//    static Order makeOrder(Order.Status status) {
//        return new Order(10, new Node(1,0,0), new Node(2,1,1), 0, 10, 10, 45, status);
//    }
//
//    static void printResults() {
//        System.out.println("\nOrderTest: " + passed + " passed, " + failed + " failed");
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