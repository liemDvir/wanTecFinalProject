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

        testDefaultConstructor_noNullPointer();
        testDefaultConstructor_hasOneOrderAndCourier();
        testDefaultConstructor_timeIsZero();
        testConstructorWithData();
        testConstructorWithTime();
        testSetters();
        testCreateRandomOrder_isModelNode();
        testCreateRandomOrder_statusWaiting();
        testCreateRandomOrder_readyTimeConsistent();
        testCreateRandomCourier_validCapacity();
        testCreateRandomCourier_statusAvailable();

        printResults();
    }

    static void testDefaultConstructor_noNullPointer() {
        // FIX: used to crash — orders/couriers not initialised before .add()
        try {
            Model m = new Model();
            assertTrue("Default constructor: no NullPointerException", true);
        } catch (NullPointerException e) {
            assertTrue("Default constructor: no NullPointerException", false);
        }
    }

    static void testDefaultConstructor_hasOneOrderAndCourier() {
        Model m = new Model();
        assertEqual("orders size",   1, m.getOrders().size());
        assertEqual("couriers size", 1, m.getCouriers().size());
    }

    static void testDefaultConstructor_timeIsZero() {
        assertEqual("currentTime=0", 0, new Model().getCurrentTime());
    }

    static void testConstructorWithData() {
        List<Order>   os = new ArrayList<>();
        List<Courier> cs = new ArrayList<>();
        os.add(new Order());
        Model m = new Model(os, cs);
        assertEqual("orders from constructor",   1, m.getOrders().size());
        assertEqual("couriers from constructor", 0, m.getCouriers().size());
        assertEqual("time defaults to 0",        0, m.getCurrentTime());
    }

    static void testConstructorWithTime() {
        Model m = new Model(new ArrayList<>(), new ArrayList<>(), 42);
        assertEqual("currentTime set by constructor", 42, m.getCurrentTime());
    }

    static void testSetters() {
        Model m = new Model(new ArrayList<>(), new ArrayList<>());
        m.setCurrentTime(10);
        assertEqual("setCurrentTime", 10, m.getCurrentTime());

        List<Order> newOrders = new ArrayList<>();
        newOrders.add(new Order());
        m.setOrders(newOrders);
        assertEqual("setOrders", 1, m.getOrders().size());
    }

    static void testCreateRandomOrder_isModelNode() {
        Order o = new Model().createRandomOrder();
        // FIX: used to return org.w3c.dom.Node — now must be model.Node
        assertTrue("pickup is model.Node",  o.getPickup()  instanceof Node);
        assertTrue("dropoff is model.Node", o.getDropoff() instanceof Node);
    }

    static void testCreateRandomOrder_statusWaiting() {
        assertTrue("random order is WAITING",
                new Model().createRandomOrder().isWaiting());
    }

    static void testCreateRandomOrder_readyTimeConsistent() {
        Order o = new Model().createRandomOrder();
        assertEqual("readyTime = orderTime + prepTime",
                o.getOrderTime() + o.getPrepTime(), o.getReadyTime());
    }

    static void testCreateRandomCourier_validCapacity() {
        Courier c = new Model().createRandomCourier();
        assertTrue("capacity >= 1", c.getCapacity() >= 1);
        assertTrue("capacity <= 5", c.getCapacity() <= 5);
    }

    static void testCreateRandomCourier_statusAvailable() {
        // FIX: createRandomCourier used to pass wrong number of args — compile error
        Courier c = new Model().createRandomCourier();
        assertTrue("random courier no crash", c != null);
    }

    // ── Helpers ──────────────────────────────────────────────────
    static void printResults() {
        System.out.println("\nModelTest: " + passed + " passed, " + failed + " failed");
    }
    static void assertTrue(String n, boolean c) {
        if (c) { System.out.println("  [PASS] "+n); passed++; }
        else   { System.out.println("  [FAIL] "+n); failed++; }
    }
    static void assertEqual(String n, int e, int g) {
        if (e==g) { System.out.println("  [PASS] "+n); passed++; }
        else { System.out.println("  [FAIL] "+n+" — expected="+e+" got="+g); failed++; }
    }
}