package controller;

import model.Courier;
import model.Model;
import model.Order;

public class Controller {


    public static int   MAX_TIME_OF_STOPPING = 60;
    // TODO - VIEW OBJECT
    private Model model;

    public Controller(Model model) {
        setModel(model);
    }

    public void setModel(Model model) {
        this.model = model;
    }

    public Model getModel() {
        return model;
    }

    // running basic simulation
    public void run(int totalMinutes) {
        for (int i = 0; i < totalMinutes; i++) {
            step();
        }
    }

    // one step, things that happen in one time unit
    private void step() {

        int currentTime = model.getCurrentTime();

        for (Courier courier : model.getCouriers()) {

            if (courier.getStatus() != Courier.Status.WAITING) {
                continue;
            }

            if (courier.getCurrentCapacity() >= courier.getCapacity()) {
                continue;
            }

            Order candidate = findBestOrderForCourier(courier, currentTime);

            if (candidate != null) {
                assignOrderToCourier(courier, candidate, currentTime);
            }
        }
    }

    private Order findBestOrderForCourier(Courier courier, int currentTime) {

        Order bestOrder = null;
        int bestReadyTime = Integer.MAX_VALUE;

        for (Order order : model.getOrders()) {

            if (order.isPickedUp()) {
                continue;
            }

            int waitTime = Math.max(order.getReadyTime(), currentTime)
                    - currentTime;

            if (waitTime > MAX_TIME_OF_STOPPING) {
                continue;
            }

            if (order.getReadyTime() < bestReadyTime) {
                bestReadyTime = order.getReadyTime();
                bestOrder = order;
            }
        }

        return bestOrder;
    }

    private void assignOrderToCourier(Courier courier,Order order,int currentTime) {

        courier.assignOrder(order);
        //TODO - PUTS THE ORDER IN THE COURIER
        order.markPickedUp();

        int finishTime = order.getReadyTime()
                + courier.getEstimatedAvailableTimeMinutes();

        courier.setEstimatedAvailableTimeMinutes(finishTime);
    }




}
