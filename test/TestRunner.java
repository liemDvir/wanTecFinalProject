/**
 * Runs all test suites and prints a combined summary.
 *
 * Compile (from project root):
 *   javac -d out src/model/*.java src/controller/*.java \
 *               test/model/*.java test/controller/*.java test/TestRunner.java
 *
 * Run:
 *   java -cp out TestRunner
 */
//public class TestRunner {
//    public static void main(String[] args) throws Exception {
//        Class<?>[] suites = {
//                model.OrderTest.class,
//                model.CourierTest.class,
//                model.ModelTest.class,
//                controller.ControllerTest.class,
//        };
//
//        int totalPassed = 0, totalFailed = 0;
//
//        for (Class<?> suite : suites) {
//            System.out.println("\n" + "─".repeat(50));
//            suite.getMethod("main", String[].class).invoke(null, (Object) new String[]{});
//            // read static fields
//            totalPassed += (int) suite.getField("passed").get(null);
//            totalFailed += (int) suite.getField("failed").get(null);
//        }
//
//        System.out.println("\n" + "═".repeat(50));
//        System.out.printf("TOTAL: %d passed, %d failed%n", totalPassed, totalFailed);
//        if (totalFailed > 0) System.exit(1);
//    }
//}

