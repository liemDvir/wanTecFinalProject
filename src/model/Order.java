package model;

import org.w3c.dom.Node;

public class Order {

    int id;
    Node pickup;
    Node dropoff;
    int orderTime;
    int prepTime;
    int readyTime;
    int deadline;
    private Status status;

    public enum Status {
        WAITING,
        PICKED_UP,
        DELIVERED
    }


    public Order() {
    }


    public Order(int id, Node pickup, Node dropoff, int orderTime, int prepTime, int readyTime, int deadline,Status status) {
        setId(id);
        setPickup(pickup);
        setDropoff(dropoff);
        setOrderTime(orderTime);
        setPrepTime(prepTime);
        setReadyTime(readyTime);
        setDeadline(deadline);
        setStatus(status);

    }

    // getters and setters

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Node getPickup() {
        return pickup;
    }

    public void setPickup(Node pickup) {
        this.pickup = pickup;
    }

    public Node getDropoff() {
        return dropoff;
    }

    public void setDropoff(Node dropoff) {
        this.dropoff = dropoff;
    }

    public int getOrderTime() {
        return orderTime;
    }

    public void setOrderTime(int orderTime) {
        this.orderTime = orderTime;
    }

    public int getPrepTime() {
        return prepTime;
    }

    public void setPrepTime(int prepTime) {
        this.prepTime = prepTime;
    }

    public int getReadyTime() {
        return readyTime;
    }

    public void setReadyTime(int readyTime) {
        this.readyTime = readyTime;
    }

    public int getDeadline() {
        return deadline;
    }

    public void setDeadline(int deadline) {
        this.deadline = deadline;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    // status helper

    public boolean isWaiting() {
        return status == Status.WAITING;
    }

    public boolean isPickedUp() {
        return status == Status.PICKED_UP;
    }

    public boolean isDelivered() {
        return status == Status.DELIVERED;
    }

    public void markPickedUp() {
        this.status = Status.PICKED_UP;
    }

    public void markDelivered() {
        this.status = Status.DELIVERED;
    }


    @Override
    public String toString() {
        return "Order{" +
                "id=" + id +
                ", pickup=" + pickup +
                ", dropoff=" + dropoff +
                ", orderTime=" + orderTime +
                ", prepTime=" + prepTime +
                ", readyTime=" + readyTime +
                ", deadline=" + deadline +
                ", status=" + status +
                '}';
    }
}
