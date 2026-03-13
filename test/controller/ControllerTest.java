package controller;

import model.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests for Controller.java
 * Location: test/controller/ControllerTest.java
 */
public class ControllerTest {
    static int passed = 0, failed = 0;

    public static void main(String[] args) {
        System.out.println("=== ControllerTest ===\n");

        testOrderAssignedAfterReadyTime();
        testOrderNotAssignedBeforeReadyTime();
        testEnRouteCourierSkipped();
        testFullCourierSkipped();
        testClockAdvances();
        testMultipleOrdersAssignedToSingleCourier();
        testOrderNotAssignedWhenWaitTooLong();

        printResults();
    }

    // order readyTime=5, run 10 ticks → must be picked up
    static void testOrderAssignedAfterReadyTime() {
        Model m = makeModel(
                List.of(makeOrder(1, 5)),
                List.of(makeCourier(1, 2, Courier.Status.AVAILABLE))
        );
        new Controller(m).run(10);
        assertTrue("order assigned after readyTime", m.getOrders().get(0).isPickedUp());
    }

    // order readyTime=20, run only 5 ticks → still WAITING
    static void testOrderNotAssignedBeforeReadyTime() {
        Model m = makeModel(
                List.of(makeOrder(1, 20)),
                List.of(makeCourier(1, 2, Courier.Status.AVAILABLE))
        );
        new Controller(m).run(5);
        assertTrue("order still WAITING before readyTime", m.getOrders().get(0).isWaiting());
    }

    // EN_ROUTE courier should be skipped entirely
    static void testEnRouteCourierSkipped() {
        Model m = makeModel(
                List.of(makeOrder(1, 0)),
                List.of(makeCourier(1, 2, Courier.Status.EN_ROUTE))
        );
        new Controller(m).run(10);
        assertTrue("EN_ROUTE courier skipped → order still WAITING",
                m.getOrders().get(0).isWaiting());
    }

    // courier at full capacity should be skipped
    static void testFullCourierSkipped() {
        Courier c = makeCourier(1, 1, Courier.Status.AVAILABLE);
        c.setCurrentCapacity(1); // already full
        Model m = makeModel(List.of(makeOrder(1, 0)), List.of(c));
        new Controller(m).run(10);
        assertTrue("full courier skipped → order still WAITING",
                m.getOrders().get(0).isWaiting());
    }

    // clock must advance each tick
    static void testClockAdvances() {
        Model m = makeModel(new ArrayList<>(), new ArrayList<>());
        new Controller(m).run(5);
        assertEqual("clock after 5 ticks", 5, m.getCurrentTime());
    }

    // two orders, one courier with capacity 2 → both should be assigned
    static void testMultipleOrdersAssignedToSingleCourier() {
        Model m = makeModel(
                List.of(makeOrder(1, 0), makeOrder(2, 0)),
                List.of(makeCourier(1, 2, Courier.Status.AVAILABLE))
        );
        new Controller(m).run(5);
        assertTrue("order 1 picked up", m.getOrders().get(0).isPickedUp());
        assertTrue("order 2 picked up", m.getOrders().get(1).isPickedUp());
    }

    // waitTime > MAX_TIME_OF_STOPPING → order not assigned
    static void testOrderNotAssignedWhenWaitTooLong() {
        // readyTime = currentTime(0) + 100 → waitTime = 100 > 60
        Model m = makeModel(
                List.of(makeOrder(1, 100)),
                List.of(makeCourier(1, 2, Courier.Status.AVAILABLE))
        );
        new Controller(m).run(1);
        assertTrue("order with waitTime>MAX skipped", m.getOrders().get(0).isWaiting());
    }

    // ── Helpers ──────────────────────────────────────────────────
    static Model makeModel(List<Order> orders, List<Courier> couriers) {
        return new Model(new ArrayList<>(orders), new ArrayList<>(couriers));
    }

    static Order makeOrder(int id, int readyTime) {
        return new Order(id, new Node(1,0,0), new Node(2,1,1),
                0, readyTime, readyTime, readyTime + 45, Order.Status.WAITING);
    }

    static Courier makeCourier(int id, int capacity, Courier.Status status) {
        return new Courier(id, "Zone", "Hub", 0, new ArrayList<>(), capacity, status);
    }

    static void printResults() {
        System.out.println("\nControllerTest: " + passed + " passed, " + failed + " failed");
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