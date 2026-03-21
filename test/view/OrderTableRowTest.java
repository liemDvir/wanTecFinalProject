package view;

/**
 * Tests for OrderTableRow.java
 * Location: test/view/OrderTableRowTest.java
 */
public class OrderTableRowTest {
    static int passed = 0, failed = 0;

    public static void main(String[] args) {
        System.out.println("=== OrderTableRowTest ===\n");

        testAllFieldsStored();
        testIsLate_true();
        testIsLate_false();
        testScore_dash();
        testScore_value();
        testCourierLabel_unassigned();
        testStatus_waiting();
        testStatus_assigned();
        testStatus_pickedUp();
        testStatus_delivered();

        // Edge cases
        testDeadline_zero();
        testOrderId_zero();
        testEta_checkmark();
        testScore_floatFormat();
        testCourierLabel_withNumber();

        printResults();
    }

    static void testAllFieldsStored() {
        OrderTableRow r = new OrderTableRow(3, "Courier 1", "WAITING", "t=27", 57, false, "47.2");
        assertEqual("orderId",      3,          r.orderId);
        assertEqual("courierLabel", "Courier 1", r.courierLabel);
        assertEqual("status",       "WAITING",   r.status);
        assertEqual("eta",          "t=27",      r.eta);
        assertEqual("deadline",     57,          r.deadline);
        assertFalse("isLate false", r.isLate);
        assertEqual("score",        "47.2",      r.score);
    }

    static void testIsLate_true() {
        OrderTableRow r = new OrderTableRow(1, "—", "WAITING", "t=99", 30, true, "—");
        assertTrue("isLate=true stored", r.isLate);
    }

    static void testIsLate_false() {
        OrderTableRow r = new OrderTableRow(1, "—", "WAITING", "t=25", 50, false, "—");
        assertFalse("isLate=false stored", r.isLate);
    }

    static void testScore_dash() {
        OrderTableRow r = new OrderTableRow(1, "—", "ASSIGNED", "t=25", 50, false, "—");
        assertEqual("score dash", "—", r.score);
    }

    static void testScore_value() {
        OrderTableRow r = new OrderTableRow(1, "—", "WAITING", "t=25", 50, false, "99.5");
        assertEqual("score value", "99.5", r.score);
    }

    static void testCourierLabel_unassigned() {
        OrderTableRow r = new OrderTableRow(1, "—", "WAITING", "t=20", 45, false, "12.3");
        assertEqual("unassigned label", "—", r.courierLabel);
    }

    static void testStatus_waiting() {
        assertEqual("WAITING status", "WAITING",
                new OrderTableRow(1,"—","WAITING","t=10",45,false,"1.0").status);
    }

    static void testStatus_assigned() {
        assertEqual("ASSIGNED status", "ASSIGNED",
                new OrderTableRow(1,"Courier 1","ASSIGNED","t=15",45,false,"—").status);
    }

    static void testStatus_pickedUp() {
        assertEqual("PICKED UP status", "PICKED UP",
                new OrderTableRow(1,"Courier 1","PICKED UP","t=20",45,false,"—").status);
    }

    static void testStatus_delivered() {
        assertEqual("DELIVERED status", "DELIVERED",
                new OrderTableRow(1,"Courier 1","DELIVERED","✓",45,false,"—").status);
    }

    // ── Edge cases ────────────────────────────────────────────────

    static void testDeadline_zero() {
        OrderTableRow r = new OrderTableRow(1,"—","WAITING","t=0",0,true,"—");
        assertEqual("deadline zero", 0, r.deadline);
        assertTrue("isLate when deadline=0", r.isLate);
    }

    static void testOrderId_zero() {
        assertEqual("orderId zero", 0,
                new OrderTableRow(0,"—","WAITING","t=5",30,false,"—").orderId);
    }

    static void testEta_checkmark() {
        assertEqual("eta checkmark for delivered", "✓",
                new OrderTableRow(1,"Courier 1","DELIVERED","✓",45,false,"—").eta);
    }

    static void testScore_floatFormat() {
        // Scores come formatted as "%.1f" from Controller
        OrderTableRow r = new OrderTableRow(1,"—","WAITING","t=10",45,false,"123.4");
        assertTrue("score has decimal", r.score.contains("."));
    }

    static void testCourierLabel_withNumber() {
        assertEqual("courier label with id", "Courier 2",
                new OrderTableRow(1,"Courier 2","ASSIGNED","t=15",45,false,"—").courierLabel);
    }

    static void printResults() {
        System.out.println("\nOrderTableRowTest: " + passed + " passed, " + failed + " failed");
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

