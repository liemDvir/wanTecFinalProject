package model;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for Model.java
 * Location: test/model/ModelTest.java
 */
public class ModelTest {
    static int passed = 0, failed = 0;

    public static void main(String[] args) {
        System.out.println("=== ModelTest ===\n");

        // Construction
        testDefaultConstructor_listsInitialised();
        testDefaultConstructor_timeZero();

        // Time
        testAdvanceTime();
        testSetCurrentTime();

        // Orders
        testAddOrder();
        testGetWaitingOrders_onlyWaiting();
        testGetWaitingOrders_excludesAssigned();
        testGetWaitingOrders_excludesPickedUp();
        testGetWaitingOrders_excludesDelivered();
        testGetWaitingOrders_empty();

        // Couriers
        testAddCourier();
        testGetAvailableCouriers_onlyAvailable();
        testGetAvailableCouriers_excludesHeadingToRestaurant();
        testGetAvailableCouriers_excludesWaitingAtRestaurant();
        testGetAvailableCouriers_excludesDelivering();
        testGetAvailableCouriers_empty();

        // Graph / Restaurant
        testSetGetGraph();
        testSetGetRestaurant();

        // Edge cases
        testMultipleOrdersOfEachStatus();
        testSetOrders_replacesList();
        testSetCouriers_replacesList();

        printResults();
    }

    static void testDefaultConstructor_listsInitialised() {
        Model m = new Model();
        assertTrue("orders not null",   m.getOrders()   != null);
        assertTrue("couriers not null", m.getCouriers() != null);
    }

    static void testDefaultConstructor_timeZero() {
        assertEqual("initial time = 0", 0, new Model().getCurrentTime());
    }

    static void testAdvanceTime() {
        Model m = new Model();
        m.advanceTime();
        assertEqual("time after 1 advance", 1, m.getCurrentTime());
        m.advanceTime(); m.advanceTime();
        assertEqual("time after 3 advances", 3, m.getCurrentTime());
    }

    static void testSetCurrentTime() {
        Model m = new Model();
        m.setCurrentTime(42);
        assertEqual("setCurrentTime", 42, m.getCurrentTime());
    }

    static void testAddOrder() {
        Model m = new Model();
        m.addOrder(makeOrder(Order.Status.WAITING));
        assertEqual("1 order after add", 1, m.getOrders().size());
    }

    static void testGetWaitingOrders_onlyWaiting() {
        Model m = new Model();
        m.addOrder(makeOrder(Order.Status.WAITING));
        assertEqual("getWaiting = 1", 1, m.getWaitingOrders().size());
    }

    static void testGetWaitingOrders_excludesAssigned() {
        Model m = new Model();
        Order o = makeOrder(Order.Status.WAITING);
        o.markAssigned();
        m.addOrder(o);
        assertEqual("ASSIGNED excluded from waiting", 0, m.getWaitingOrders().size());
    }

    static void testGetWaitingOrders_excludesPickedUp() {
        Model m = new Model();
        Order o = makeOrder(Order.Status.WAITING);
        o.markAssigned(); o.markPickedUp();
        m.addOrder(o);
        assertEqual("PICKED_UP excluded from waiting", 0, m.getWaitingOrders().size());
    }

    static void testGetWaitingOrders_excludesDelivered() {
        Model m = new Model();
        Order o = makeOrder(Order.Status.WAITING);
        o.markAssigned(); o.markPickedUp(); o.markDelivered();
        m.addOrder(o);
        assertEqual("DELIVERED excluded from waiting", 0, m.getWaitingOrders().size());
    }

    static void testGetWaitingOrders_empty() {
        assertTrue("no waiting orders when empty", new Model().getWaitingOrders().isEmpty());
    }

    static void testAddCourier() {
        Model m = new Model();
        m.addCourier(makeCourier(Courier.Status.AVAILABLE));
        assertEqual("1 courier after add", 1, m.getCouriers().size());
    }

    static void testGetAvailableCouriers_onlyAvailable() {
        Model m = new Model();
        m.addCourier(makeCourier(Courier.Status.AVAILABLE));
        assertEqual("1 available courier", 1, m.getAvailableCouriers().size());
    }

    static void testGetAvailableCouriers_excludesHeadingToRestaurant() {
        Model m = new Model();
        m.addCourier(makeCourier(Courier.Status.HEADING_TO_RESTAURANT));
        assertEqual("HEADING excluded", 0, m.getAvailableCouriers().size());
    }

    static void testGetAvailableCouriers_excludesWaitingAtRestaurant() {
        Model m = new Model();
        m.addCourier(makeCourier(Courier.Status.WAITING_AT_RESTAURANT));
        assertEqual("WAITING_AT_REST excluded", 0, m.getAvailableCouriers().size());
    }

    static void testGetAvailableCouriers_excludesDelivering() {
        Model m = new Model();
        m.addCourier(makeCourier(Courier.Status.DELIVERING));
        assertEqual("DELIVERING excluded", 0, m.getAvailableCouriers().size());
    }

    static void testGetAvailableCouriers_empty() {
        assertTrue("no available when empty", new Model().getAvailableCouriers().isEmpty());
    }

    static void testSetGetGraph() {
        Model m = new Model();
        Graph g = new Graph();
        m.setGraph(g);
        assertTrue("graph set", m.getGraph() == g);
    }

    static void testSetGetRestaurant() {
        Model m = new Model();
        Restaurant r = new Restaurant(1, "Pizza", new Node(0,0,0), 10);
        m.setRestaurant(r);
        assertTrue("restaurant set", m.getRestaurant() == r);
    }

    static void testMultipleOrdersOfEachStatus() {
        Model m = new Model();
        Order w  = makeOrder(Order.Status.WAITING);
        Order a  = makeOrder(Order.Status.WAITING); a.markAssigned();
        Order p  = makeOrder(Order.Status.WAITING); a.markAssigned(); p.markAssigned(); p.markPickedUp();
        Order d  = makeOrder(Order.Status.WAITING); d.markAssigned(); d.markPickedUp(); d.markDelivered();
        m.addOrder(w); m.addOrder(a); m.addOrder(p); m.addOrder(d);
        assertEqual("total orders", 4, m.getOrders().size());
        assertEqual("waiting orders = 1", 1, m.getWaitingOrders().size());
    }

    static void testSetOrders_replacesList() {
        Model m = new Model();
        m.addOrder(makeOrder(Order.Status.WAITING));
        List<Order> newList = new ArrayList<>();
        m.setOrders(newList);
        assertEqual("orders replaced", 0, m.getOrders().size());
    }

    static void testSetCouriers_replacesList() {
        Model m = new Model();
        m.addCourier(makeCourier(Courier.Status.AVAILABLE));
        m.setCouriers(new ArrayList<>());
        assertEqual("couriers replaced", 0, m.getCouriers().size());
    }

    // ── Helpers ──────────────────────────────────────────────────
    static Order makeOrder(Order.Status status) {
        return new Order(1, new Node(1,0,0), new Node(2,1,1), 0, 10, 10, 45, status);
    }
    static Courier makeCourier(Courier.Status status) {
        return new Courier(1,"Z","Hub",0,new ArrayList<>(),2,status);
    }

    static void printResults() {
        System.out.println("\nModelTest: " + passed + " passed, " + failed + " failed");
    }
    static void assertTrue(String n, boolean c) {
        if (c) { System.out.println("  [PASS] "+n); passed++; }
        else   { System.out.println("  [FAIL] "+n); failed++; }
    }
    static void assertFalse(String n, boolean c) { assertTrue(n, !c); }
    static void assertEqual(String n, int e, int g) {
        if (e==g) { System.out.println("  [PASS] "+n); passed++; }
        else { System.out.println("  [FAIL] "+n+" — expected="+e+" got="+g); failed++; }
    }
}