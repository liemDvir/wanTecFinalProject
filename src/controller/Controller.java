package controller;

import algorithm.GeneticAlgorithm;
import javafx.application.Application;
import model.*;
import view.MapGenerator;
import view.MapView;

import java.util.*;

/**
 * ═══════════════════════════════════════════════════
 *  Controller — the C in MVC.
 *
 *  Dynamic simulation:
 *    - Restaurant is placed at a RANDOM node.
 *    - Initial orders are randomly generated (2–5).
 *    - Every ~10 ticks, 1–2 NEW orders are spawned at random locations.
 *    - The GA runs at tick 0 and re-plans whenever new orders appear
 *      and there are available couriers.
 * ═══════════════════════════════════════════════════
 */
public class Controller {

    // ── Grid / display ────────────────────────────────────────────
    private static final int    GRID_COLS         = 7;
    private static final int    GRID_ROWS         = 5;
    private static final double MAP_WIDTH         = 780;
    private static final double MAP_HEIGHT        = 620;

    // ── Simulation parameters ─────────────────────────────────────
    private static final int    NUM_COURIERS       = 2;
    private static final int    COURIER_CAPACITY   = 5;
    private static final int    DEFAULT_PREP_TIME  = 12;
    private static final int    ORDER_DEADLINE     = 45;

    // Initial order count: random between these bounds (inclusive).
    private static final int    INIT_ORDERS_MIN    = 2;
    private static final int    INIT_ORDERS_MAX    = 5;

    // New-order spawning window (ticks).
    // Every SPAWN_WINDOW ticks, 1–2 new orders are created at a random
    // tick inside that window (not always at the boundary).
    private static final int    SPAWN_WINDOW       = 10;
    private static final int    SPAWN_MIN          = 1;   // min orders per wave
    private static final int    SPAWN_MAX          = 2;   // max orders per wave

    // GA re-planning interval (ticks).
    private static final int    GA_INTERVAL        = 10;

    // ── State ─────────────────────────────────────────────────────
    private final Random rng = new Random();

    private Model   model;
    private MapView view;
    private boolean running = false;

    /** Fires GA at tick 0 (initialised to GA_INTERVAL). */
    private int ticksSinceLastGA = GA_INTERVAL;

    /** Running order-ID counter (never resets within a city). */
    private int nextOrderId = 1;

    /** The exact tick at which the next wave of orders will spawn.
     *  Randomly chosen inside each SPAWN_WINDOW interval. */
    private int nextSpawnTick = -1;

    /** True when new orders were just spawned this tick → force GA. */
    private boolean newOrdersThisTick = false;

    private final Map<Integer, Deque<GeneticAlgorithm.RouteAction>> courierPlans   = new HashMap<>();
    private final Map<Integer, List<Order>>                          courierCarried = new HashMap<>();
    private final Map<Integer, Double>                               lastScores     = new HashMap<>();

    private static final long TICK_INTERVAL_NS = 600_000_000L;

    // ── Bootstrap ─────────────────────────────────────────────────

    public void start(String[] args) {
        model = buildCity();
        MapView.setController(this);
        Application.launch(MapView.class, args);
    }

    public void onViewReady(MapView view) {
        this.view = view;
        refreshView();
        Thread t = new Thread(() -> {
            while (true) {
                try { Thread.sleep(TICK_INTERVAL_NS / 1_000_000); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                if (!running) continue;
                tick();
                javafx.application.Platform.runLater(this::refreshView);
            }
        });
        t.setDaemon(true);
        t.start();
    }

    public void setRunning(boolean r) { this.running = r; }
    public boolean isRunning()        { return running; }

    // ── City construction ─────────────────────────────────────────

    public void buildCityForTest() { model = buildCity(); }

    private Model buildCity() {
        Model        m         = new Model();
        MapGenerator generator = new MapGenerator(GRID_COLS, GRID_ROWS, MAP_WIDTH, MAP_HEIGHT);
        Graph        graph     = generator.buildGraph();
        m.setGraph(graph);

        List<Node> all = new ArrayList<>();
        for (int id : graph.nodeIds()) all.add(graph.getNode(id));

        // ── Random restaurant location ────────────────────────────
        Node restaurantNode = all.get(rng.nextInt(all.size()));
        m.setRestaurant(new Restaurant(1, "Trattoria Roma", restaurantNode, DEFAULT_PREP_TIME));

        // ── Couriers start near the restaurant ────────────────────
        List<Node> near = nodesNear(restaurantNode, all, NUM_COURIERS);
        for (int i = 0; i < NUM_COURIERS; i++) {
            Node s = near.get(i);
            Courier c = new Courier(i + 1, "Zone-" + (i + 1), "Node-" + s.getId(),
                    0, new ArrayList<>(), COURIER_CAPACITY, Courier.Status.AVAILABLE);
            c.setCurrentNode(s);
            m.addCourier(c);
        }

        // ── Random initial orders ─────────────────────────────────
        nextOrderId = 1;
        int initCount = INIT_ORDERS_MIN + rng.nextInt(INIT_ORDERS_MAX - INIT_ORDERS_MIN + 1);
        List<Node> candidates = new ArrayList<>(all);
        candidates.remove(restaurantNode);
        Collections.shuffle(candidates, rng);

        for (int i = 0; i < initCount && i < candidates.size(); i++) {
            Node dropoff = candidates.get(i);
            int  orderT  = rng.nextInt(4);                                 // 0–3
            int  prepT   = DEFAULT_PREP_TIME + rng.nextInt(6);             // 12–17
            int  readyT  = orderT + prepT;
            int  deadline = readyT + ORDER_DEADLINE;
            m.addOrder(new Order(nextOrderId++, restaurantNode, dropoff,
                    orderT, prepT, readyT, deadline, Order.Status.WAITING));
        }

        // Schedule the first spawn wave at a random tick inside [1, SPAWN_WINDOW].
        scheduleNextSpawn(0);

        System.out.printf("[INIT] Restaurant at node %d  |  %d initial orders  |  first spawn at t=%d%n",
                restaurantNode.getId(), initCount, nextSpawnTick);
        System.out.flush();

        return m;
    }

    // ── Order spawning ────────────────────────────────────────────

    /**
     * Schedule the next spawn tick randomly inside the next SPAWN_WINDOW.
     * @param windowStart the first tick of the current window
     */
    private void scheduleNextSpawn(int windowStart) {
        // Spawn at a random tick inside (windowStart, windowStart + SPAWN_WINDOW]
        nextSpawnTick = windowStart + 1 + rng.nextInt(SPAWN_WINDOW);
    }

    /**
     * Called every tick. If the current tick matches nextSpawnTick,
     * spawn 1–2 new orders at random customer locations.
     */
    private void maybeSpawnOrders(int currentTime) {
        newOrdersThisTick = false;

        if (currentTime < nextSpawnTick) return;

        int count = SPAWN_MIN + rng.nextInt(SPAWN_MAX - SPAWN_MIN + 1);

        Graph graph = model.getGraph();
        Node  restaurant = model.getRestaurant().getLocation();
        List<Node> all = new ArrayList<>();
        for (int id : graph.nodeIds()) all.add(graph.getNode(id));
        all.remove(restaurant);
        Collections.shuffle(all, rng);

        // Avoid placing orders at the same node as an existing undelivered order.
        Set<Integer> usedNodes = new HashSet<>();
        for (Order o : model.getOrders())
            if (!o.isDelivered() && o.getDropoff() != null)
                usedNodes.add(o.getDropoff().getId());

        int spawned = 0;
        for (Node candidate : all) {
            if (spawned >= count) break;
            if (usedNodes.contains(candidate.getId())) continue;

            int prepT    = DEFAULT_PREP_TIME + rng.nextInt(8);          // 12–19
            int readyT   = currentTime + prepT;
            int deadline = readyT + ORDER_DEADLINE + rng.nextInt(10);   // some variation

            Order newOrder = new Order(nextOrderId++, restaurant, candidate,
                    currentTime, prepT, readyT, deadline, Order.Status.WAITING);
            model.addOrder(newOrder);
            spawned++;

            System.out.printf("[SPAWN] t=%d  Order #%d → node %d  (ready t=%d, deadline t=%d)%n",
                    currentTime, newOrder.getId(), candidate.getId(), readyT, deadline);
        }

        if (spawned > 0) {
            newOrdersThisTick = true;
            System.out.flush();
        }

        // Schedule the next wave.
        scheduleNextSpawn(currentTime);
    }

    // ── Main tick ─────────────────────────────────────────────────

    public void tick() {
        int time = model.getCurrentTime();

        // ── Step 0: possibly spawn new orders ─────────────────────
        maybeSpawnOrders(time);

        // ── Step 1: GA planning ───────────────────────────────────
        // Runs at tick 0 (ticksSinceLastGA starts at GA_INTERVAL),
        // every GA_INTERVAL ticks, AND immediately when new orders spawn.
        ticksSinceLastGA++;
        boolean gaTriggered = ticksSinceLastGA >= GA_INTERVAL || newOrdersThisTick;

        if (gaTriggered) {
            ticksSinceLastGA = 0;
            List<Courier> available = model.getAvailableCouriers();
            List<Order>   waiting   = model.getWaitingOrders();
            if (!available.isEmpty() && !waiting.isEmpty()) {
                GeneticAlgorithm.Chromosome best =
                        runGeneticOptimization(available, waiting, time);
                if (best != null) applyGeneticPlan(best, available);
            }
        }

        // ── Step 2: advance couriers ──────────────────────────────
        for (Courier c : model.getCouriers()) advanceCourier(c, time);

        // ── Step 3: clock ─────────────────────────────────────────
        model.advanceTime();
    }

    // ── GA integration ────────────────────────────────────────────

    private GeneticAlgorithm.Chromosome runGeneticOptimization(
            List<Courier> available, List<Order> waiting, int time) {
        printGaHeader(time, available.size(), waiting.size());
        GeneticAlgorithm ga = new GeneticAlgorithm();
        GeneticAlgorithm.Chromosome best = ga.run(available, waiting, model.getGraph(), time,
                stats -> { System.out.println(stats.toLogLine()); System.out.flush(); });
        if (best != null) printGaSummary(best, available);
        return best;
    }

    private void applyGeneticPlan(GeneticAlgorithm.Chromosome best,
                                  List<Courier> available) {
        for (int ci = 0; ci < best.routes.size() && ci < available.size(); ci++) {
            Courier courier = available.get(ci);
            if (courier.getStatus() != Courier.Status.AVAILABLE) continue;

            List<GeneticAlgorithm.RouteAction> route = best.routes.get(ci);
            if (route.isEmpty()) continue;

            for (GeneticAlgorithm.RouteAction a : route)
                if (a.type == GeneticAlgorithm.ActionType.PICKUP && a.order.isWaiting())
                    a.order.markAssigned();

            courierPlans.put(courier.getId(),  new ArrayDeque<>(route));
            courierCarried.put(courier.getId(), new ArrayList<>());
            courier.setCurrentCapacity(0);
            startNextPlanAction(courier);
        }
    }

    // ── Courier state machine ─────────────────────────────────────

    private void advanceCourier(Courier courier, int currentTime) {
        switch (courier.getStatus()) {
            case HEADING_TO_RESTAURANT -> {
                if (courier.stepForward()) {
                    courier.setPreviousNode(courier.getCurrentNode());
                    processRestaurantArrival(courier, currentTime);
                }
            }
            case WAITING_AT_RESTAURANT -> {
                courier.setPreviousNode(courier.getCurrentNode());
                processRestaurantArrival(courier, currentTime);
            }
            case DELIVERING -> {
                if (courier.stepForward()) {
                    courier.setPreviousNode(courier.getCurrentNode());
                    processCustomerArrival(courier, currentTime);
                }
            }
            case AVAILABLE -> { /* idle */ }
        }
    }

    private void processRestaurantArrival(Courier courier, int currentTime) {
        Deque<GeneticAlgorithm.RouteAction> plan = courierPlans.get(courier.getId());
        if (plan == null || plan.isEmpty()) { finishCourierPlan(courier); return; }

        while (!plan.isEmpty()
                && plan.peek().type == GeneticAlgorithm.ActionType.PICKUP) {
            GeneticAlgorithm.RouteAction a = plan.peek();
            if (currentTime >= a.order.getReadyTime()) {
                plan.poll();
                if (a.order.isAssigned()) {
                    a.order.markPickedUp();
                    courier.setCurrentCapacity(courier.getCurrentCapacity() + 1);
                    courierCarried.computeIfAbsent(courier.getId(), k -> new ArrayList<>())
                            .add(a.order);
                }
            } else {
                courier.setActiveOrder(a.order);
                courier.setStatus(Courier.Status.WAITING_AT_RESTAURANT);
                return;
            }
        }
        startNextPlanAction(courier);
    }

    private void processCustomerArrival(Courier courier, int currentTime) {
        Order order = courier.getActiveOrder();
        if (order != null && !order.isDelivered()) order.markDelivered();

        List<Order> carried = courierCarried.get(courier.getId());
        if (carried != null && order != null) carried.remove(order);
        if (courier.getCurrentCapacity() > 0)
            courier.setCurrentCapacity(courier.getCurrentCapacity() - 1);

        startNextPlanAction(courier);
    }

    private void startNextPlanAction(Courier courier) {
        Deque<GeneticAlgorithm.RouteAction> plan = courierPlans.get(courier.getId());
        if (plan == null || plan.isEmpty()) { finishCourierPlan(courier); return; }

        GeneticAlgorithm.RouteAction next = plan.peek();
        Graph graph = model.getGraph();

        if (next.type == GeneticAlgorithm.ActionType.PICKUP) {
            Order order = next.order;
            courier.setActiveOrder(order);
            if (courier.getCurrentNode() == null) courier.setCurrentNode(order.getPickup());

            List<Node>    path  = graph.getShortestPath(courier.getCurrentNode().getId(),
                    order.getPickup().getId());
            List<Integer> ticks = computeEdgeTicks(path, courier.getCurrentNode(), graph);
            courier.setCurrentPath(path, ticks);
            courier.setStatus(Courier.Status.HEADING_TO_RESTAURANT);

        } else {
            plan.poll();
            Order order = next.order;
            courier.setActiveOrder(order);

            List<Node>    path  = graph.getShortestPath(courier.getCurrentNode().getId(),
                    order.getDropoff().getId());
            List<Integer> ticks = computeEdgeTicks(path, courier.getCurrentNode(), graph);
            courier.setCurrentPath(path, ticks);
            courier.setStatus(Courier.Status.DELIVERING);
        }
    }

    private void finishCourierPlan(Courier courier) {
        courier.setActiveOrder(null);
        courier.setCurrentCapacity(0);
        courier.setCurrentPath(new ArrayList<>(), new ArrayList<>());
        courierPlans.remove(courier.getId());
        courierCarried.remove(courier.getId());
        courier.setStatus(Courier.Status.AVAILABLE);
    }

    // ── Edge tick computation ─────────────────────────────────────

    private List<Integer> computeEdgeTicks(List<Node> path, Node start, Graph graph) {
        List<Integer> ticks = new ArrayList<>();
        if (path == null || path.isEmpty()) return ticks;
        Node prev = start;
        for (Node next : path) {
            double w = 1.0;
            for (Graph.Edge e : graph.getEdges(prev.getId()))
                if (e.getTo() == next.getId()) { w = e.getWeight(); break; }
            ticks.add(Math.max(1, (int) Math.round(w)));
            prev = next;
        }
        return ticks;
    }

    // ── Console log helpers ───────────────────────────────────────

    private void printGaHeader(int time, int nc, int no) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════╗");
        System.out.printf ("║  🧬 GA RUN  t=%-4d  |  %d courier(s)  |  %d waiting order(s)%n", time, nc, no);
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.flush();
    }

    private void printGaSummary(GeneticAlgorithm.Chromosome best, List<Courier> available) {
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.printf ("║  ✅ BEST  fitness=%-8.1f%n", best.fitness);
        System.out.println("║  Full route plans:");
        for (int i = 0; i < best.routes.size(); i++) {
            String label = (i < available.size()) ? "Courier "+available.get(i).getId() : "Courier "+(i+1);
            List<GeneticAlgorithm.RouteAction> r = best.routes.get(i);
            System.out.printf("║    %-12s → %s%n", label, r.isEmpty() ? "(idle)" : r.toString());
        }
        System.out.println("╚══════════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.flush();
    }

    // ── City regeneration ─────────────────────────────────────────

    public void regenerateCity() {
        running = false;
        ticksSinceLastGA = GA_INTERVAL;
        courierPlans.clear();
        courierCarried.clear();
        model = buildCity();
        refreshView();
    }

    // ── View updates ──────────────────────────────────────────────

    private void refreshView() {
        if (view == null) return;
        view.drawMap(model.getGraph(), model.getRestaurant(), model.getOrders(), model.getCouriers());
        view.updateSidebar(model.getCurrentTime(),
                model.getWaitingOrders().size(),
                (int) model.getOrders().stream().filter(Order::isAssigned).count(),
                (int) model.getOrders().stream().filter(Order::isPickedUp).count(),
                (int) model.getOrders().stream().filter(Order::isDelivered).count(),
                model.getCouriers().size());
        view.drawTable(buildTableRows());
    }

    private List<view.OrderTableRow> buildTableRows() {
        List<view.OrderTableRow> rows = new ArrayList<>();
        int time = model.getCurrentTime();

        for (Order order : model.getOrders()) {
            Courier assigned = null;
            String  label    = "—";
            for (Courier c : model.getCouriers()) {
                if (c.getActiveOrder() != null && c.getActiveOrder().getId() == order.getId()) {
                    assigned = c; label = "Courier " + c.getId(); break;
                }
                List<Order> carried = courierCarried.get(c.getId());
                if (carried != null && carried.stream().anyMatch(o -> o.getId() == order.getId())) {
                    assigned = c; label = "Courier " + c.getId(); break;
                }
            }

            String status = switch (order.getStatus()) {
                case WAITING   -> "WAITING";
                case ASSIGNED  -> "ASSIGNED";
                case PICKED_UP -> "PICKED UP";
                case DELIVERED -> "DELIVERED";
            };

            String eta;
            if (order.isDelivered()) {
                eta = "✓";
            } else if (assigned != null) {
                int steps = assigned.getCurrentPath() != null ? assigned.getCurrentPath().size() : 0;
                Graph g   = model.getGraph();
                if (assigned.getStatus() == Courier.Status.DELIVERING
                        && assigned.getActiveOrder() != null
                        && assigned.getActiveOrder().getId() == order.getId()) {
                    eta = "t=" + (time + steps);
                } else {
                    int del  = (g != null) ? (int) g.shortestTime(order.getPickup().getId(), order.getDropoff().getId()) : 10;
                    int wait = Math.max(0, order.getReadyTime() - (time + steps));
                    eta = "t=" + (time + steps + wait + del);
                }
            } else {
                Graph g = model.getGraph();
                int del = (g != null) ? (int) g.shortestTime(order.getPickup().getId(), order.getDropoff().getId()) : 10;
                eta = "t=" + (Math.max(order.getReadyTime(), time) + del);
            }

            boolean isLate = time > order.getDeadline() && !order.isDelivered();
            Double  raw    = lastScores.get(order.getId());
            String  score  = (raw != null && order.isWaiting()) ? String.format("%.1f", raw) : "—";

            rows.add(new view.OrderTableRow(order.getId(), label, status, eta, order.getDeadline(), isLate, score));
        }
        return rows;
    }

    // ── Node selection ────────────────────────────────────────────

    private List<Node> nodesNear(Node anchor, List<Node> all, int n) {
        List<Node> s = new ArrayList<>(all); s.remove(anchor);
        s.sort(Comparator.comparingDouble(nd -> Math.hypot(nd.getX()-anchor.getX(), nd.getY()-anchor.getY())));
        return s.subList(0, Math.min(n, s.size()));
    }

    public Model getModel()            { return model; }
    public void setModelForTest(Model m) { this.model = m; }
}