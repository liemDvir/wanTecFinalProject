package model;

import java.util.List;

    public class Courier {

        private int id;

        private String controlArea;
        private String currentLocation;
        private int estimatedAvailableTimeMinutes;
        private List<String> routePlan;
        private int capacity; // max delivers per courier
        private int currentCapacity; // how many delivers has the courier
        private Courier.Status status;



        public enum Status {
            AVAILABLE, // has no delivers, not in a Restaurant
            EN_ROUTE, // in a route
            WAITING // waiting in the Restaurant
        }


        public Courier(int id, String controlArea, String currentLocation, int estimatedAvailableTimeMinutes,
                       List<String> routePlan, int capacity,int currentCapacity,Courier.Status status) {

            setId(id);
            setControlArea(controlArea);
            setCurrentLocation(currentLocation);
            setEstimatedAvailableTimeMinutes(estimatedAvailableTimeMinutes);
            setRoutePlan(routePlan);
            setCapacity(capacity);
            setCurrentCapacity(currentCapacity);
            setStatus(status);
        }

        public Courier(int id, String controlArea, String currentLocation, int estimatedAvailableTimeMinutes,
                       List<String> routePlan, int capacity,Courier.Status status) {

            setId(id);
            setControlArea(controlArea);
            setCurrentLocation(currentLocation);
            setEstimatedAvailableTimeMinutes(estimatedAvailableTimeMinutes);
            setRoutePlan(routePlan);
            setCapacity(capacity);
            setCurrentCapacity(0);
            setStatus(status);
        }

        //getters and setters

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getControlArea() {
            return controlArea;
        }

        public void setControlArea(String controlArea) {
            this.controlArea = controlArea;
        }

        public String getCurrentLocation() {
            return currentLocation;
        }

        public void setCurrentLocation(String currentLocation) {
            this.currentLocation = currentLocation;
        }

        public int getEstimatedAvailableTimeMinutes() {
            return estimatedAvailableTimeMinutes;
        }

        public void setEstimatedAvailableTimeMinutes(int estimatedAvailableTimeMinutes) {
            this.estimatedAvailableTimeMinutes = estimatedAvailableTimeMinutes;
        }

        public List<String> getRoutePlan() {
            return routePlan;
        }

        public void setRoutePlan(List<String> routePlan) {
            this.routePlan = routePlan;
        }

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        public Courier.Status getStatus() {
            return status;
        }

        public void setStatus(Courier.Status status) {
            this.status = status;
        }

        public int getCurrentCapacity() {
            return currentCapacity;
        }

        public void setCurrentCapacity(int currentCapacity) {
            this.currentCapacity = currentCapacity;
        }



        public void assignOrder(Order order) {
            // TODO - PUTS THE ORDER IN THE COURIER LIST
        }


    }


