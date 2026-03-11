package model;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import java.util.logging.Logger;

public class Model {

    private static final Logger LOGGER = Logger.getLogger(Model.class.getName());

    private List<Order> orders;
    private List<Courier> couriers;
    private int currentTime;

    public Model() {
        // generate random value
        // TODO- get real data somehow

        LOGGER.info("creating new model");

        Order newOrder = createRandomOrder();
        orders.add(newOrder);

        Courier newCourier = createRandomCourier();
        couriers.add(newCourier);

        currentTime = 0;

    }

    public Model(List<Order> orders, List<Courier> couriers) {
        this.orders = orders;
        this.couriers = couriers;
        currentTime = 0;
    }

    public Model(List<Order> orders, List<Courier> couriers, int currentTime) {
        this.orders = orders;
        this.couriers = couriers;
        this.currentTime = currentTime;
    }

    public List<Order> getOrders() {
        return orders;
    }

    public void setOrders(List<Order> orders) {
        this.orders = orders;
    }

    public List<Courier> getCouriers() {
        return couriers;
    }

    public void setCouriers(List<Courier> couriers) {
        this.couriers = couriers;
    }

    public int getCurrentTime() {
        return currentTime;
    }

    public void setCurrentTime(int currentTime) {
        this.currentTime = currentTime;
    }

    // creates random order
    public Order createRandomOrder() {
        Random random = new Random();
        Node pickupNode = null;
        Node dropoffNode = null;
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document document = builder.newDocument();
            Element pickup = document.createElement("pickup");
            pickup.setTextContent("Pickup-Alpha");
            Element dropoff = document.createElement("dropoff");
            dropoff.setTextContent("Dropoff-Bravo");
            pickupNode = pickup;
            dropoffNode = dropoff;
        } catch (ParserConfigurationException ignored) {
            LOGGER.warning("failed making order");
        }

        int orderTime = random.nextInt(101);
        int prepTime = 15;
        int readyTime = orderTime + prepTime;
        int deadline = readyTime + 30;
        return new Order(101, pickupNode, dropoffNode, orderTime, prepTime, readyTime, deadline, Order.Status.WAITING);
    }

    // creates random courier
    public Courier createRandomCourier() {
        Random random = new Random();
        List<String> routePlan = new ArrayList<>();
        String pickup = "Pickup-" + random.nextInt(100);
        String dropoff = "Dropoff-" + random.nextInt(100);
        routePlan.add(pickup);
        routePlan.add(dropoff);
        int id = random.nextInt(1000) + 1;
        String controlArea = "Central-" + random.nextInt(10);
        String currentLocation = "Hub-" + random.nextInt(20);
        int estimatedAvailableTimeMinutes = random.nextInt(61);
        int capacity = random.nextInt(5) + 1;
        Courier.Status status = Courier.Status.values()[random.nextInt(Courier.Status.values().length)];
        return new Courier(id, controlArea, currentLocation, estimatedAvailableTimeMinutes, routePlan, capacity, status);
    }

}
