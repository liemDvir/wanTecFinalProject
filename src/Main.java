import controller.Controller;
import model.Model;

public class Main {
    public static void main(String[] args) {

        System.out.println("Simulation started");

        Model model = new Model();
        Controller controller = new Controller(model);

        controller.run(300);

    }
}