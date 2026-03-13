import controller.Controller;
import model.Courier;
import model.Model;
import model.Node;
import model.Order;

import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {

        // ── Option 1: use random model (tests the fixed default constructor)
        Model randomModel = new Model();
        Controller ctrl1  = new Controller(randomModel);
        ctrl1.run(30);
        System.out.println("Random model done.\n");




        // ── Option 2: hand-crafted data for deterministic testing
        Node pickupA  = new Node(1, 2.0, 3.0, "Restaurant A");
        Node dropoffA = new Node(2, 5.0, 6.0, "Customer 1");
        Node pickupB  = new Node(3, 1.0, 1.0, "Restaurant B");
        Node dropoffB = new Node(4, 7.0, 2.0, "Customer 2");

        List<Order> orders = new ArrayList<>();
        orders.add(new Order(1, pickupA, dropoffA,  0, 10, 10, 55, Order.Status.WAITING));
        orders.add(new Order(2, pickupB, dropoffB,  5,  8, 13, 58, Order.Status.WAITING));

        List<Courier> couriers = new ArrayList<>();
        couriers.add(new Courier(1, "North", "Hub-A", 5, new ArrayList<>(), 2, Courier.Status.AVAILABLE));
        couriers.add(new Courier(2, "South", "Hub-B", 3, new ArrayList<>(), 2, Courier.Status.AVAILABLE));

        Model fixedModel = new Model(orders, couriers);
        Controller ctrl2 = new Controller(fixedModel);
        ctrl2.run(60);

        System.out.println("\nFinal order statuses:");
        for (Order o : fixedModel.getOrders())
            System.out.println("  " + o);

        System.out.println("\nFinal courier statuses:");
        for (Courier c : fixedModel.getCouriers())
            System.out.println("  " + c);
    }

}