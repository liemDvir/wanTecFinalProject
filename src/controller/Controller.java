package controller;

import model.Courier;
import model.Model;
import model.Order;

public class Controller {

    public static final int MAX_TIME_OF_STOPPING = 60;

    // TODO - VIEW OBJECT
    private Model model;

    public Controller(Model model) {
        setModel(model);
    }

    public void setModel(Model model) { this.model = model; }

    public Model getModel()           { return model; }

    // ── Simulation loop ──────────────────────────────────────────

    public void run(int totalMinutes) {
        for (int i = 0; i < totalMinutes; i++) {
            step();
            model.setCurrentTime(model.getCurrentTime() + 1);  // FIX: advance clock
        }
    }

    // ── One simulation tick ──────────────────────────────────────

    private void step() {
        int currentTime = model.getCurrentTime();

        for (Courier courier : model.getCouriers()) {

            // FIX: original checked for WAITING but "free" couriers are AVAILABLE.
            //      WAITING means the courier is at a restaurant waiting for food —
            //      that courier CAN still take another order if under capacity,
            //      so we allow both AVAILABLE and WAITING here.
            if (courier.getStatus() == Courier.Status.EN_ROUTE) {
                continue;   // courier is travelling — can't take new orders
            }

            if (courier.getCurrentCapacity() >= courier.getCapacity()) {
                continue;   // courier is full
            }

            Order candidate = findBestOrderForCourier(courier, currentTime);
            if (candidate != null) {
                assignOrderToCourier(courier, candidate, currentTime);
            }
        }
    }

    // ── Order selection ──────────────────────────────────────────

    private Order findBestOrderForCourier(Courier courier, int currentTime) {
        Order bestOrder    = null;
        int   bestReadyTime = Integer.MAX_VALUE;

        for (Order order : model.getOrders()) {
            if (order.isPickedUp() || order.isDelivered()) {
                continue;
            }

            int waitTime = Math.max(order.getReadyTime(), currentTime) - currentTime;
            if (waitTime > MAX_TIME_OF_STOPPING) {
                continue;
            }

            if (order.getReadyTime() < bestReadyTime) {
                bestReadyTime = order.getReadyTime();
                bestOrder     = order;
            }
        }
        return bestOrder;
    }

    // ── Order assignment ─────────────────────────────────────────

    private void assignOrderToCourier(Courier courier, Order order, int currentTime) {
        courier.assignOrder(order);   // adds to routePlan, increments currentCapacity
        order.markPickedUp();

        // FIX: original added readyTime + estimatedAvailable which could give wrong
        //      result when the courier must first wait for food to be ready.
        //      Correct: max(currentTime, readyTime) + travel time estimate.
        //      For now travel time is a placeholder until Graph is implemented.
        int earliestPickup = Math.max(currentTime, order.getReadyTime());
        int finishTime     = earliestPickup + courier.getEstimatedAvailableTimeMinutes();
        courier.setEstimatedAvailableTimeMinutes(finishTime);
    }
}