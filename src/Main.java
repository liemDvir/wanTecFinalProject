import controller.Controller;

/**
 * Entry point — initialises the Controller and nothing else.
 * All application logic starts from the Controller.
 */
public class Main {
    public static void main(String[] args) {
        new Controller().start(args);
    }
}