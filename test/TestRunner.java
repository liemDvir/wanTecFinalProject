/**
 * Runs all test suites and prints a combined summary.
 *
 * Compile (from project root):
 *   javac -d out src/model/*.java src/view/MapGenerator.java src/view/OrderTableRow.java \
 *               src/controller/Controller.java \
 *               test/model/*.java test/view/*.java test/controller/*.java test/TestRunner.java
 *
 * Run:
 *   java -cp out TestRunner
 *
 * Note: MapView tests require JavaFX and are run separately.
 */
public class TestRunner {

    public static void main(String[] args) throws Exception {
        Class<?>[] suites = {
                // model
                model.NodeTest.class,
                model.OrderTest.class,
                model.CourierTest.class,
                model.GraphTest.class,
                model.ModelTest.class,
                model.RestaurantTest.class,
                // view (no JavaFX)
                view.MapGeneratorTest.class,
                view.OrderTableRowTest.class,
                // controller (no JavaFX)
                controller.ControllerTest.class,
        };

        int totalPassed = 0, totalFailed = 0;

        for (Class<?> suite : suites) {
            System.out.println("\n" + "─".repeat(55));
            suite.getMethod("main", String[].class)
                    .invoke(null, (Object) new String[]{});
            totalPassed += (int) suite.getField("passed").get(null);
            totalFailed += (int) suite.getField("failed").get(null);
        }

        System.out.println("\n" + "═".repeat(55));
        System.out.printf("TOTAL: %d passed, %d failed%n", totalPassed, totalFailed);
        if (totalFailed > 0) System.exit(1);
    }
}