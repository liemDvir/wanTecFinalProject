package model;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for Courier.java — movement, assignment, path traversal
 * Location: test/model/CourierTest.java
 */
public class CourierTest {
    static int passed = 0, failed = 0;

    public static void main(String[] args) {
        System.out.println("=== CourierTest ===\n");

        // Construction
        testInitialState();
        testConvenienceConstructor_currentCapacityZero();

        // Capacity
        testHasCapacity_empty();
        testHasCapacity_atLimit();
        testHasCapacity_overLimit();

        // assignOrder
        testAssignOrder_setsActiveOrder();
        testAssignOrder_incrementsCapacity();
        testAssignOrder_addsToRoutePlan();

        // Movement — stepForward
        testStepForward_emptyPath_returnsTrue();
        testStepForward_onePath_movesAndReturnsTrue();
        testStepForward_multiStep_advancesOneAtATime();
        testStepForward_savesPreviousNode();
        testStepForward_onNullPath_returnsTrue();

        // hasReachedDestination
        testHasReachedDestination_emptyPath();
        testHasReachedDestination_nonEmptyPath();

        // completeDelivery
        testCompleteDelivery_resetsState();
        testCompleteDelivery_setsAvailable();

        // Edge cases
        testStepForward_previousNodeUpdatedEachStep();
        testAssignOrder_nullRoutePlanInitialised();
        testCompleteDelivery_withNoActiveOrder();

        printResults();
    }

    static void testInitialState() {
        Courier c = makeCourier(2, Courier.Status.AVAILABLE);
        assertEqual("id",              1,                        c.getId());
        assertEqual("capacity",        2,                        c.getCapacity());
        assertEqual("currentCapacity", 0,                        c.getCurrentCapacity());
        assertEqual("status",          Courier.Status.AVAILABLE, c.getStatus());
        assertTrue("routePlan empty",  c.getRoutePlan().isEmpty());
        assertTrue("currentPath empty",c.getCurrentPath().isEmpty());
    }

    static void testConvenienceConstructor_currentCapacityZero() {
        Courier c = new Courier(1,"Z","Hub",0,new ArrayList<>(),3,Courier.Status.AVAILABLE);
        assertEqual("capacity defaults 0", 0, c.getCurrentCapacity());
    }

    static void testHasCapacity_empty() {
        assertTrue("has capacity when empty", makeCourier(2, Courier.Status.AVAILABLE).hasCapacity());
    }

    static void testHasCapacity_atLimit() {
        Courier c = makeCourier(1, Courier.Status.AVAILABLE);
        c.assignOrder(makeOrder(1));
        assertFalse("no capacity at limit", c.hasCapacity());
    }

    static void testHasCapacity_overLimit() {
        Courier c = makeCourier(1, Courier.Status.AVAILABLE);
        c.setCurrentCapacity(5); // force over limit
        assertFalse("no capacity over limit", c.hasCapacity());
    }

    static void testAssignOrder_setsActiveOrder() {
        Courier c = makeCourier(2, Courier.Status.AVAILABLE);
        Order   o = makeOrder(7);
        c.assignOrder(o);
        assertTrue("activeOrder set", c.getActiveOrder() == o);
    }

    static void testAssignOrder_incrementsCapacity() {
        Courier c = makeCourier(3, Courier.Status.AVAILABLE);
        c.assignOrder(makeOrder(1));
        assertEqual("capacity after 1 assign", 1, c.getCurrentCapacity());
        c.assignOrder(makeOrder(2));
        assertEqual("capacity after 2 assigns", 2, c.getCurrentCapacity());
    }

    static void testAssignOrder_addsToRoutePlan() {
        Courier c = makeCourier(2, Courier.Status.AVAILABLE);
        c.assignOrder(makeOrder(5));
        assertEqual("routePlan size", 2, c.getRoutePlan().size());
        assertTrue("PICKUP entry",  c.getRoutePlan().get(0).startsWith("PICKUP:5"));
        assertTrue("DROPOFF entry", c.getRoutePlan().get(1).startsWith("DROPOFF:5"));
    }

    static void testStepForward_emptyPath_returnsTrue() {
        Courier c = makeCourier(1, Courier.Status.AVAILABLE);
        assertTrue("empty path returns true", c.stepForward());
    }

    static void testStepForward_onePath_movesAndReturnsTrue() {
        Courier c = makeCourier(1, Courier.Status.AVAILABLE);
        Node start = new Node(0, 0, 0);
        Node dest  = new Node(1, 1, 0);
        c.setCurrentNode(start);
        c.setCurrentPath(listOf(dest));

        boolean arrived = c.stepForward();
        assertTrue("arrived after 1 step", arrived);
        assertTrue("currentNode is dest", c.getCurrentNode().equals(dest));
    }

    static void testStepForward_multiStep_advancesOneAtATime() {
        Courier c = makeCourier(1, Courier.Status.AVAILABLE);
        Node n0 = new Node(0,0,0), n1 = new Node(1,1,0), n2 = new Node(2,2,0);
        c.setCurrentNode(n0);
        c.setCurrentPath(listOf(n1, n2));

        assertFalse("not arrived after step 1", c.stepForward());
        assertTrue("at n1 after step 1", c.getCurrentNode().equals(n1));

        assertTrue("arrived after step 2", c.stepForward());
        assertTrue("at n2 after step 2", c.getCurrentNode().equals(n2));
    }

    static void testStepForward_savesPreviousNode() {
        Courier c = makeCourier(1, Courier.Status.AVAILABLE);
        Node n0 = new Node(0,0,0), n1 = new Node(1,1,0);
        c.setCurrentNode(n0);
        c.setCurrentPath(listOf(n1));
        c.stepForward();
        assertTrue("previousNode saved", c.getPreviousNode().equals(n0));
    }

    static void testStepForward_onNullPath_returnsTrue() {
        Courier c = makeCourier(1, Courier.Status.AVAILABLE);
        c.setCurrentPath(null);
        assertTrue("null path returns true", c.stepForward());
    }

    static void testHasReachedDestination_emptyPath() {
        Courier c = makeCourier(1, Courier.Status.AVAILABLE);
        assertTrue("empty path = reached", c.hasReachedDestination());
    }

    static void testHasReachedDestination_nonEmptyPath() {
        Courier c = makeCourier(1, Courier.Status.AVAILABLE);
        c.setCurrentPath(listOf(new Node(1,0,0)));
        assertFalse("non-empty path = not reached", c.hasReachedDestination());
    }

    static void testCompleteDelivery_resetsState() {
        Courier c = makeCourier(2, Courier.Status.DELIVERING);
        c.assignOrder(makeOrder(1));
        c.completeDelivery();
        assertEqual("capacity reset to 0", 0, c.getCurrentCapacity());
        assertTrue("activeOrder null", c.getActiveOrder() == null);
        assertTrue("currentPath empty", c.getCurrentPath().isEmpty());
    }

    static void testCompleteDelivery_setsAvailable() {
        Courier c = makeCourier(2, Courier.Status.DELIVERING);
        c.assignOrder(makeOrder(1));
        c.completeDelivery();
        assertEqual("status AVAILABLE", Courier.Status.AVAILABLE, c.getStatus());
    }

    static void testStepForward_previousNodeUpdatedEachStep() {
        // After each step, previousNode should be the node we just left
        Courier c = makeCourier(1, Courier.Status.AVAILABLE);
        Node n0=new Node(0,0,0), n1=new Node(1,1,0), n2=new Node(2,2,0);
        c.setCurrentNode(n0);
        c.setCurrentPath(listOf(n1, n2));

        c.stepForward();
        assertTrue("prev=n0 after step1", c.getPreviousNode().equals(n0));
        c.stepForward();
        assertTrue("prev=n1 after step2", c.getPreviousNode().equals(n1));
    }

    static void testAssignOrder_nullRoutePlanInitialised() {
        // If routePlan is null it should be initialised, not crash
        Courier c = makeCourier(2, Courier.Status.AVAILABLE);
        c.setRoutePlan(null);
        try {
            c.assignOrder(makeOrder(1));
            assertTrue("null routePlan init: no crash", true);
        } catch (NullPointerException e) {
            assertTrue("null routePlan init: no crash", false);
        }
    }

    static void testCompleteDelivery_withNoActiveOrder() {
        // completeDelivery with null activeOrder should not crash
        Courier c = makeCourier(1, Courier.Status.DELIVERING);
        try {
            c.completeDelivery();
            assertTrue("completeDelivery null activeOrder: no crash", true);
        } catch (Exception e) {
            assertTrue("completeDelivery null activeOrder: no crash", false);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────
    static Courier makeCourier(int capacity, Courier.Status status) {
        return new Courier(1,"Zone","Hub",0,new ArrayList<>(),capacity,status);
    }
    static Order makeOrder(int id) {
        return new Order(id, new Node(10,0,0), new Node(20,1,1), 0,10,10,45, Order.Status.WAITING);
    }
    @SafeVarargs
    static <T> List<T> listOf(T... items) {
        List<T> list = new ArrayList<>();
        for (T item : items) list.add(item);
        return list;
    }

    static void printResults() {
        System.out.println("\nCourierTest: " + passed + " passed, " + failed + " failed");
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
    static void assertEqual(String n, Object e, Object g) {
        boolean ok = e==null ? g==null : e.equals(g);
        if (ok) { System.out.println("  [PASS] "+n); passed++; }
        else { System.out.println("  [FAIL] "+n+" — expected="+e+" got="+g); failed++; }
    }
}