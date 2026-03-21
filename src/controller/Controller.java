package controller;

import javafx.application.Application;
import model.*;
import view.MapGenerator;
import view.MapView;

import java.util.*;

/**
 * ═══════════════════════════════════════════════════
 *  Controller — the C in MVC.
 *
 *  THE ONLY place in the program where decisions are made:
 *    - How to build the city (where restaurant goes,
 *      where couriers start, where customers are).
 *    - Which courier gets which order.
 *    - When to advance the simulation clock.
 *    - When to tell the View to redraw.
 *
 *  Rules:
 *    - Never draws anything (calls View methods only).
 *    - Creates and owns the Model.
 *    - Passes data to the View — View never pulls from Model.
 * ═══════════════════════════════════════════════════
 */
public class Controller {

    // ── Configuration — all tunable constants live here ──────────
    private static final int    GRID_COLS         = 7;
    private static final int    GRID_ROWS         = 5;
    private static final double MAP_WIDTH         = 780;
    private static final double MAP_HEIGHT        = 620;
    private static final int    NUM_COURIERS      = 2;
    private static final int    NUM_CUSTOMERS     = 3;
    private static final int    COURIER_CAPACITY  = 3;
    private static final int    DEFAULT_PREP_TIME = 12;
    private static final int    MAX_WAIT_MINUTES  = 60;
    private static final int    ORDER_DEADLINE    = 45;

    private Model   model;
    private MapView view;
    private boolean running = false;

    // Stores the last computed score per order id — for debug display in the table
    private final Map<Integer, Double> lastScores = new HashMap<>();

    private static final long TICK_INTERVAL_NS = 600_000_000L; // 600ms per tick

    // ── Bootstrap ─────────────────────────────────────────────────

    /**
     * Called by Main.
     * Builds the model, then launches the JavaFX window.
     */
    public void start(String[] args) {
        model = buildCity();
        MapView.setController(this);
        Application.launch(MapView.class, args);
    }

    /**
     * Called by MapView once the window is open and ready.
     *
     * The Controller starts its own simulation thread here.
     * The thread runs the loop — on every tick it calls tick(),
     * then uses Platform.runLater() to push the result to the GUI.
     *
     * This way the Controller drives the GUI, not the other way around.
     * The View never wakes up on its own — it only reacts to Controller calls.
     */
    public void onViewReady(MapView view) {
        this.view = view;

        // Draw initial state immediately
        refreshView();

        // Controller owns the simulation thread.
        // Platform.runLater() ensures all GUI calls happen on the JavaFX thread.
        Thread simulationThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(TICK_INTERVAL_NS / 1_000_000); // convert ns → ms
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                if (!running) continue; // paused — skip tick but keep thread alive

                // Run logic on the simulation thread
                tick();

                // Push GUI update to the JavaFX thread
                javafx.application.Platform.runLater(this::refreshView);
            }
        });

        // Daemon thread — shuts down automatically when the app closes
        simulationThread.setDaemon(true);
        simulationThread.start();
    }

    // ── Simulation control (called by View buttons) ───────────────

    /** Start or pause the simulation. */
    public void setRunning(boolean running) { this.running = running; }
    public boolean isRunning()              { return running; }

    // ── City construction — ALL decisions made here ───────────────

    /**
     * Test-only entry point — builds the city without launching JavaFX.
     * Allows unit tests to call tick() and inspect the model directly.
     */
    public void buildCityForTest() {
        model = buildCity();
        // view remains null — refreshView() guards against this
    }
    /**
     * Build a complete city:
     *   1. Ask MapGenerator for a Graph (pure street layout).
     *   2. Decide where the restaurant goes (most central node).
     *   3. Decide where couriers start (near the restaurant).
     *   4. Decide where customer dropoffs are (far from restaurant).
     *   5. Create all model objects and populate the Model.
     */
    private Model buildCity() {
        Model        m         = new Model();
        MapGenerator generator = new MapGenerator(GRID_COLS, GRID_ROWS, MAP_WIDTH, MAP_HEIGHT);
        Graph        graph     = generator.buildGraph();
        m.setGraph(graph);

        List<Node> allNodes = new ArrayList<>();
        for (int id : graph.nodeIds()) allNodes.add(graph.getNode(id));

        // ── Decision: restaurant at the most central node ─────────
        // TODO - RANDOM Restaurant OR THE USER DECIDE WHERE THE Restaurant WILL BE
        Node restaurantNode = pickCentralNode(allNodes);
        Restaurant restaurant = new Restaurant(1, "Trattoria Roma",
                restaurantNode, DEFAULT_PREP_TIME);
        m.setRestaurant(restaurant);

        // ── Decision: couriers start near the restaurant ──────────
        // TODO - RANDOM PLACE TO START OR THE USER DECIDE WHERE THE COURIERS WILL BE
        List<Node> nearNodes = nodesNear(restaurantNode, allNodes, NUM_COURIERS);
        for (int i = 0; i < NUM_COURIERS; i++) {
            Node    start   = nearNodes.get(i);
            Courier courier = new Courier(
                    i + 1,
                    "Zone-" + (i + 1),
                    "Node-" + start.getId(),
                    0,
                    new ArrayList<>(),
                    COURIER_CAPACITY,
                    Courier.Status.AVAILABLE
            );
            courier.setCurrentNode(start); // store actual Node for Dijkstra
            m.addCourier(courier);
        }

        // ── Decision: customers placed far from the restaurant ────
        // TODO - RANDOM PLACE OR THE USER DECIDE WHERE THE CUSTOMER WILL BE
        List<Node> farNodes = nodesFar(restaurantNode, allNodes, NUM_CUSTOMERS);
        for (int i = 0; i < NUM_CUSTOMERS; i++) {
            Node dropoff  = farNodes.get(i);
            int  orderT   = i * 2;                          // stagger order times
            int  prepT    = DEFAULT_PREP_TIME + (i * 2);    // vary prep times
            int  readyT   = orderT + prepT;
            int  deadline = readyT + ORDER_DEADLINE;
            m.addOrder(new Order(
                    i + 1,
                    restaurantNode,
                    dropoff,
                    orderT, prepT, readyT, deadline,
                    Order.Status.WAITING
            ));
        }

        return m;
    }

    // ── Simulation tick — all logic here ─────────────────────────

    /**
     * Advance the simulation by one minute.
     * Each tick does two things:
     *   1. Assign new orders to available couriers.
     *   2. Move every active courier one step along its path.
     */
    public void tick() {
        int time = model.getCurrentTime();

        // Step 1: assign orders to idle couriers
        for (Courier courier : model.getAvailableCouriers()) {
            if (courier.getCurrentCapacity() >= courier.getCapacity()) continue;
            Order best = pickBestOrder(courier, time);
            if (best != null) assignOrder(courier, best);
        }

        // Step 2: advance every courier one step
        for (Courier courier : model.getCouriers()) {
            advanceCourier(courier, time);
        }

        model.advanceTime();
        // refreshView() is called via Platform.runLater in the simulation loop —
        // do NOT call it here directly (would run on the wrong thread)
    }

    /**
     * Move one courier one step forward according to its current status.
     *
     * HEADING_TO_RESTAURANT → walk toward restaurant
     *   → on arrival: if food ready → DELIVERING, else WAITING_AT_RESTAURANT
     *
     * WAITING_AT_RESTAURANT → wait until readyTime
     *   → once ready: set path to customer, status = DELIVERING
     *
     * DELIVERING → walk toward customer
     *   → on arrival: mark order delivered, courier becomes AVAILABLE
     */
    private void advanceCourier(Courier courier, int currentTime) {
        switch (courier.getStatus()) {

            case HEADING_TO_RESTAURANT -> {
                boolean arrived = courier.stepForward();
                if (arrived) {
                    // Fix: sync previousNode to currentNode so animation
                    // doesn't replay the last step while waiting
                    courier.setPreviousNode(courier.getCurrentNode());
                    Order order = courier.getActiveOrder();
                    if (currentTime >= order.getReadyTime()) {
                        startDelivery(courier);
                    } else {
                        courier.setStatus(Courier.Status.WAITING_AT_RESTAURANT);
                    }
                }
            }

            case WAITING_AT_RESTAURANT -> {
                // Fix: keep previousNode == currentNode so no animation plays
                courier.setPreviousNode(courier.getCurrentNode());
                Order order = courier.getActiveOrder();
                if (order != null && currentTime >= order.getReadyTime()) {
                    startDelivery(courier);
                }
            }

            case DELIVERING -> {
                boolean arrived = courier.stepForward();
                if (arrived) {
                    Order order = courier.getActiveOrder();
                    if (order != null) order.markDelivered();
                    // Fix: sync previousNode before completing so no jump occurs
                    courier.setPreviousNode(courier.getCurrentNode());
                    courier.completeDelivery();
                }
            }

            case AVAILABLE -> { /* nothing to do */ }
        }
    }

    /**
     * Assign an order to a courier:
     *   - Store the order on the courier.
     *   - Compute the Dijkstra path to the restaurant.
     *   - Set status to HEADING_TO_RESTAURANT.
     *
     * Note: order is kept as WAITING until the courier actually picks it up
     * at the restaurant — markPickedUp() is called in startDelivery().
     */
    private void assignOrder(Courier courier, Order order) {
        courier.assignOrder(order);

        // Mark as ASSIGNED immediately — no other courier can claim it now
        order.markAssigned();

        // Guard: if courier has no position yet, place it at the restaurant
        if (courier.getCurrentNode() == null) {
            courier.setCurrentNode(order.getPickup());
        }

        Graph      graph = model.getGraph();
        List<Node> path  = graph.getShortestPath(
                courier.getCurrentNode().getId(),
                order.getPickup().getId());

        courier.setCurrentPath(path);
        courier.setStatus(Courier.Status.HEADING_TO_RESTAURANT);
    }

    /**
     * Called when the courier arrives at the restaurant and food is ready.
     * Computes the delivery path and sends the courier toward the customer.
     */
    private void startDelivery(Courier courier) {
        Order      order = courier.getActiveOrder();
        Graph      graph = model.getGraph();
        List<Node> path  = graph.getShortestPath(
                order.getPickup().getId(),
                order.getDropoff().getId());

        order.markPickedUp();
        courier.setCurrentPath(path);
        courier.setStatus(Courier.Status.DELIVERING);
    }

    /**
     * Decision: find the best unassigned order for this courier.
     *
     * Scoring (lower = better) — weighted sum of 4 factors in priority order:
     *
     *   1. Time until deadline           (weight 4) — most urgent first
     *   2. Courier distance to restaurant(weight 3) — how far the courier must travel to pick up
     *   3. Restaurant readyTime          (weight 2) — food almost ready preferred
     *   4. Restaurant to customer dist.  (weight 1) — shorter delivery preferred
     *
     * All distance factors use Dijkstra on the city graph.
     * An order is skipped if:
     *   - The courier cannot reach the restaurant before the deadline.
     *   - The courier would have to wait > MAX_WAIT_MINUTES for the food.
     */
    private Order pickBestOrder(Courier courier, int currentTime) {
        Graph  graph      = model.getGraph();
        Node   courierPos = courier.getCurrentNode();

        Order  best      = null;
        double bestScore = Double.MAX_VALUE;

        for (Order order : model.getWaitingOrders()) {

            Node pickup  = order.getPickup();   // restaurant node
            Node dropoff = order.getDropoff();  // customer node

            // ── Factor 2: courier → restaurant (travel time) ──────
            double courierToRestaurant = (courierPos != null && graph != null)
                    ? graph.shortestTime(courierPos.getId(), pickup.getId())
                    : 0;

            // Earliest minute the courier can be at the restaurant
            double arrivalAtRestaurant = currentTime + courierToRestaurant;

            // Earliest minute the courier can actually pick up
            // (must wait if food isn't ready yet)
            double pickupTime = Math.max(arrivalAtRestaurant, order.getReadyTime());

            // ── Guard: skip if wait at restaurant is too long ─────
            double waitAtRestaurant = pickupTime - arrivalAtRestaurant;
            if (waitAtRestaurant > MAX_WAIT_MINUTES) continue;

            // ── Factor 4: restaurant → customer (delivery distance) ─
            double restaurantToCustomer = (graph != null)
                    ? graph.shortestTime(pickup.getId(), dropoff.getId())
                    : 0;

            // Estimated delivery time
            double estimatedDelivery = pickupTime + restaurantToCustomer;

            // ── Guard: skip if delivery will miss the deadline ────
            if (estimatedDelivery > order.getDeadline()) continue;

            // ── Factor 1: time remaining until deadline ───────────
            // Less time remaining → more urgent → lower score (we invert)
            double timeUntilDeadline = order.getDeadline() - currentTime;

            // ── Factor 3: ready time ──────────────────────────────
            double readyTime = order.getReadyTime();

            // ── Weighted score (lower = better assignment) ────────
            //   Priority: deadline urgency > courier distance > readyTime > delivery dist
            double score = (4.0 / Math.max(timeUntilDeadline, 1))
                    + (3.0 * courierToRestaurant)
                    + (2.0 * readyTime)
                    + (1.0 * restaurantToCustomer);

            // Save score for debug display in the orders table
            lastScores.put(order.getId(), score);

            if (score < bestScore) {
                bestScore = score;
                best      = order;
            }
        }
        return best;
    }

    // ── City regeneration ─────────────────────────────────────────

    /**
     * Called when user clicks "New City".
     * Controller rebuilds everything, then tells View to redraw.
     * View does not touch the model — it just redraws what it receives.
     */
    public void regenerateCity() {
        running = false;
        model   = buildCity();
        refreshView();
    }

    // ── View updates — Controller decides when View redraws ───────

    /**
     * Push all display data to the View.
     * View receives exactly what it needs — nothing more.
     */
    private void refreshView() {
        if (view == null) return;
        view.drawMap(
                model.getGraph(),
                model.getRestaurant(),
                model.getOrders(),
                model.getCouriers()
        );
        view.updateSidebar(
                model.getCurrentTime(),
                model.getWaitingOrders().size(),
                (int) model.getOrders().stream().filter(Order::isAssigned).count(),
                (int) model.getOrders().stream().filter(Order::isPickedUp).count(),
                (int) model.getOrders().stream().filter(Order::isDelivered).count(),
                model.getCouriers().size()
        );
        view.drawTable(buildTableRows());
    }

    /**
     * Build one display row per order.
     * All decisions about what to show live here — not in the View.
     */
    private List<view.OrderTableRow> buildTableRows() {
        List<view.OrderTableRow> rows = new ArrayList<>();
        int currentTime = model.getCurrentTime();

        for (Order order : model.getOrders()) {

            // Decision: which courier is handling this order?
            Courier assignedCourier = null;
            String  courierLabel   = "—";
            for (Courier c : model.getCouriers()) {
                if (c.getActiveOrder() != null &&
                        c.getActiveOrder().getId() == order.getId()) {
                    assignedCourier = c;
                    courierLabel    = "Courier " + c.getId();
                    break;
                }
            }

            // Decision: human-readable status
            String status = switch (order.getStatus()) {
                case WAITING   -> "WAITING";
                case ASSIGNED  -> "ASSIGNED";
                case PICKED_UP -> "PICKED UP";
                case DELIVERED -> "DELIVERED";
            };

            // Decision: ETA using real Dijkstra distances
            String eta;
            if (order.isDelivered()) {
                eta = "✓";
            } else if (assignedCourier != null) {
                Graph graph = model.getGraph();
                // Steps remaining in path = minutes left to walk
                int stepsLeft = assignedCourier.getCurrentPath() != null
                        ? assignedCourier.getCurrentPath().size() : 0;

                if (assignedCourier.getStatus() == Courier.Status.DELIVERING) {
                    // Already picked up — just delivery steps remaining
                    eta = "t=" + (currentTime + stepsLeft);
                } else {
                    // Still heading to restaurant or waiting
                    int walkToRest = stepsLeft;
                    int waitForFood = Math.max(0, order.getReadyTime() - (currentTime + walkToRest));
                    int deliverySteps = (graph != null)
                            ? (int) graph.shortestTime(
                            order.getPickup().getId(),
                            order.getDropoff().getId())
                            : 10;
                    eta = "t=" + (currentTime + walkToRest + waitForFood + deliverySteps);
                }
            } else {
                // Unassigned — estimate from restaurant to customer
                Graph graph = model.getGraph();
                int deliverySteps = (graph != null)
                        ? (int) graph.shortestTime(
                        order.getPickup().getId(),
                        order.getDropoff().getId())
                        : 10;
                int earliest = Math.max(order.getReadyTime(), currentTime) + deliverySteps;
                eta = "t=" + earliest;
            }

            // Decision: is this order running late?
            boolean isLate = currentTime > order.getDeadline()
                    && !order.isDelivered();

            // Score from last pickBestOrder evaluation (null if not yet scored)
            Double  rawScore   = lastScores.get(order.getId());
            String  scoreLabel = (rawScore != null && order.isWaiting())
                    ? String.format("%.1f", rawScore)
                    : "—";

            rows.add(new view.OrderTableRow(
                    order.getId(),
                    courierLabel,
                    status,
                    eta,
                    order.getDeadline(),
                    isLate,
                    scoreLabel
            ));
        }
        return rows;
    }

    // ── Node selection decisions ──────────────────────────────────

    /** Pick the node closest to the centre of the map. */
    private Node pickCentralNode(List<Node> nodes) {
        double cx = MAP_WIDTH / 2, cy = MAP_HEIGHT / 2;
        return nodes.stream()
                .min(Comparator.comparingDouble(
                        n -> Math.hypot(n.getX() - cx, n.getY() - cy)))
                .orElse(nodes.get(0));
    }

    /** Pick the n nodes closest to the anchor. */
    private List<Node> nodesNear(Node anchor, List<Node> all, int n) {
        List<Node> sorted = new ArrayList<>(all);
        sorted.remove(anchor);
        sorted.sort(Comparator.comparingDouble(
                node -> Math.hypot(node.getX() - anchor.getX(),
                        node.getY() - anchor.getY())));
        return sorted.subList(0, Math.min(n, sorted.size()));
    }

    /** Pick the n nodes farthest from the anchor. */
    private List<Node> nodesFar(Node anchor, List<Node> all, int n) {
        List<Node> sorted = new ArrayList<>(all);
        sorted.remove(anchor);
        sorted.sort(Comparator.comparingDouble(
                node -> -Math.hypot(node.getX() - anchor.getX(),
                        node.getY() - anchor.getY())));
        return sorted.subList(0, Math.min(n, sorted.size()));
    }

    // ── Getters ───────────────────────────────────────────────────
    public Model getModel() { return model; }
    /**
     * Test-only — inject a pre-built model directly.
     * Allows tests to use a deterministic graph instead of the random one.
     */
    public void setModelForTest(Model m) {
        this.model = m;
    }

}