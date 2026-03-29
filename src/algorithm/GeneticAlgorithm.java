package algorithm;

import model.*;
import java.util.*;

/**
 * ═══════════════════════════════════════════════════════════════
 *  GeneticAlgorithm — Hybrid Genetic Algorithm for delivery routing.
 *
 *  Based on the project proposal, this algorithm solves the problem of
 *  assigning and ordering deliveries across multiple couriers, where:
 *    - Each courier can carry multiple orders simultaneously.
 *    - Order matters: P1→P2→D1→D2 may be better than P1→D1→P2→D2
 *      depending on geography.
 *    - Every pickup must come before its dropoff (precedence constraint).
 *    - No order may be delivered later than its deadline (time window).
 *    - Couriers wait at the restaurant if food isn't ready yet.
 *
 *  ──────────────────────────────────────────────────────────────
 *  ALGORITHM OVERVIEW (as described in the project proposal):
 *
 *  Phase 1 — Preprocessing
 *    Compute travel times between all relevant nodes using the
 *    graph's Dijkstra. Build a distance matrix for O(1) lookup.
 *
 *  Phase 2 — Initial Population via Order Bundling
 *    Instead of random initialization, group orders that are
 *    geographically close and temporally compatible into "bundles".
 *    Each bundle becomes the starting chromosome — far better than
 *    a random start and leads to faster convergence.
 *
 *  Phase 3 — Genetic Loop (runs for NUM_GENERATIONS per call)
 *    a) Evaluate fitness of each chromosome (lower = better).
 *    b) Select best chromosomes (elitism + tournament).
 *    c) Crossover: swap route segments between two parents.
 *    d) Mutation: randomly reorder actions within a courier's route.
 *    e) Repair: fix any constraint violations after each operator.
 *    f) Local Search: attempt to relocate late orders to faster couriers.
 *
 *  Phase 4 — Return Best Solution
 *    The best chromosome is decoded into a concrete route plan
 *    (list of RouteActions) for each courier.
 *
 *  ──────────────────────────────────────────────────────────────
 *  KEY CONCEPTS:
 *
 *  Chromosome: a full assignment of all waiting orders to couriers,
 *    including the exact sequence of PICKUP and DROPOFF actions.
 *    Example for 2 couriers, 3 orders:
 *      Courier 1: [P1, P2, D2, D1]   ← picked up 1&2, dropped 2 first
 *      Courier 2: [P3, D3]
 *
 *  Fitness: total weighted delivery time across all orders.
 *    - Penalty added for every order that misses its deadline.
 *    - Urgent orders (close to deadline) weighted more heavily.
 *
 *  Precedence constraint: for every order i, PICKUP_i must appear
 *    before DROPOFF_i in the same courier's route.
 * ═══════════════════════════════════════════════════════════════
 */
public class GeneticAlgorithm {

    // ── Tunable parameters ────────────────────────────────────────
    private static final int    POPULATION_SIZE   = 30;
    private static final int    NUM_GENERATIONS   = 20;
    private static final double MUTATION_RATE     = 0.15;
    private static final double CROSSOVER_RATE    = 0.80;
    private static final int    ELITE_COUNT       = 3;   // top N survive unchanged
    private static final double LATE_PENALTY      = 1000.0; // penalty per late order
    private static final double URGENCY_WEIGHT    = 2.0;    // extra weight for near-deadline

    private final Random rng = new Random();

    // ══════════════════════════════════════════════════════════════
    //  INNER CLASSES
    // ══════════════════════════════════════════════════════════════

    /**
     * A single action in a courier's route.
     * Every order contributes exactly two actions: PICKUP then DROPOFF.
     * The order of these actions across orders is what the GA optimises.
     */
    public enum ActionType { PICKUP, DROPOFF }

    public static class RouteAction {
        public final ActionType type;
        public final Order      order;

        public RouteAction(ActionType type, Order order) {
            this.type  = type;
            this.order = order;
        }

        @Override
        public String toString() {
            return type + "_" + order.getId();
        }
    }

    /**
     * A chromosome represents a complete assignment of all waiting orders
     * to all available couriers, including the sequence of actions.
     *
     * routes[i] = ordered list of RouteActions for courier i.
     *
     * Invariant: for every order assigned to courier i,
     *   PICKUP appears before DROPOFF in routes[i].
     */
    public static class Chromosome {
        public final List<List<RouteAction>> routes; // one route list per courier
        public       double                 fitness;

        public Chromosome(int numCouriers) {
            routes  = new ArrayList<>();
            for (int i = 0; i < numCouriers; i++) routes.add(new ArrayList<>());
            fitness = Double.MAX_VALUE;
        }

        /** Deep copy — used when creating offspring. */
        public Chromosome copy() {
            Chromosome c = new Chromosome(routes.size());
            for (int i = 0; i < routes.size(); i++)
                for (RouteAction a : routes.get(i))
                    c.routes.get(i).add(new RouteAction(a.type, a.order));
            c.fitness = fitness;
            return c;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("Chromosome[fitness=")
                    .append(String.format("%.1f", fitness)).append("]\n");
            for (int i = 0; i < routes.size(); i++) {
                sb.append("  Courier ").append(i + 1).append(": ");
                sb.append(routes.get(i)).append("\n");
            }
            return sb.toString();
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  PUBLIC ENTRY POINT
    // ══════════════════════════════════════════════════════════════

    /**
     * Run the genetic algorithm and return the best chromosome found.
     *
     * Called by the Controller instead of (or alongside) pickBestOrder().
     * The Controller can then decode the chromosome into courier route plans.
     *
     * @param couriers  available couriers (AVAILABLE status only)
     * @param orders    unassigned waiting orders
     * @param graph     city graph (for travel time calculations)
     * @param currentTime current simulation time
     * @return the best Chromosome found, or null if no orders/couriers
     */
    public Chromosome run(List<Courier> couriers, List<Order> orders,
                          Graph graph, int currentTime) {

        if (couriers.isEmpty() || orders.isEmpty()) return null;

        // ── Phase 1: Build distance matrix (Dijkstra preprocessing) ──
        // Maps nodeId → shortest time array from that node.
        // Allows O(1) travel time lookup during fitness evaluation.
        Map<Integer, double[]> distMatrix = buildDistanceMatrix(graph, couriers, orders);

        // ── Phase 2: Create initial population via bundling ───────────
        List<Chromosome> population = createInitialPopulation(
                couriers, orders, graph, distMatrix, currentTime);

        // Evaluate fitness of every chromosome in the initial population
        for (Chromosome c : population)
            c.fitness = evaluateFitness(c, couriers, orders, distMatrix, currentTime);

        // ── Phase 3: Genetic loop ─────────────────────────────────────
        Chromosome best = getBest(population);

        for (int gen = 0; gen < NUM_GENERATIONS; gen++) {

            // Sort population best→worst
            population.sort(Comparator.comparingDouble(c -> c.fitness));

            List<Chromosome> nextGen = new ArrayList<>();

            // Elitism: carry top ELITE_COUNT unchanged into next generation
            for (int i = 0; i < Math.min(ELITE_COUNT, population.size()); i++)
                nextGen.add(population.get(i).copy());

            // Fill the rest of the next generation
            while (nextGen.size() < POPULATION_SIZE) {

                // a) Selection: pick two parents by tournament
                Chromosome parent1 = tournamentSelect(population);
                Chromosome parent2 = tournamentSelect(population);

                // b) Crossover
                Chromosome child;
                if (rng.nextDouble() < CROSSOVER_RATE)
                    child = crossover(parent1, parent2, couriers.size(), orders);
                else
                    child = parent1.copy();

                // c) Mutation
                if (rng.nextDouble() < MUTATION_RATE)
                    mutate(child, rng);

                // d) Repair: fix any precedence or capacity violations
                repair(child, couriers);

                // e) Evaluate
                child.fitness = evaluateFitness(child, couriers, orders,
                        distMatrix, currentTime);

                nextGen.add(child);
            }

            // f) Local search: try to relocate late orders
            for (Chromosome c : nextGen)
                localSearch(c, couriers, orders, distMatrix, currentTime);

            population = nextGen;

            // Track overall best
            Chromosome genBest = getBest(population);
            if (genBest.fitness < best.fitness) best = genBest.copy();
        }

        return best;
    }

    // ══════════════════════════════════════════════════════════════
    //  PHASE 1 — PREPROCESSING
    // ══════════════════════════════════════════════════════════════

    /**
     * Run Dijkstra from every relevant node (courier positions,
     * restaurant/pickup nodes, customer/dropoff nodes) and cache the results.
     *
     * This avoids running Dijkstra repeatedly during fitness evaluation,
     * making each fitness call O(1) per edge instead of O(E log V).
     */
    private Map<Integer, double[]> buildDistanceMatrix(Graph graph,
                                                       List<Courier> couriers,
                                                       List<Order> orders) {
        Map<Integer, double[]> matrix = new HashMap<>();

        Set<Integer> sources = new HashSet<>();

        // Courier starting positions
        for (Courier c : couriers)
            if (c.getCurrentNode() != null)
                sources.add(c.getCurrentNode().getId());

        // Pickup and dropoff nodes of all orders
        for (Order o : orders) {
            if (o.getPickup()  != null) sources.add(o.getPickup().getId());
            if (o.getDropoff() != null) sources.add(o.getDropoff().getId());
        }

        // Run Dijkstra from each source node
        for (int src : sources)
            matrix.put(src, graph.dijkstra(src));

        return matrix;
    }

    /**
     * Look up travel time between two nodes using the precomputed matrix.
     * Returns a large penalty value if the route is unreachable.
     */
    private double travelTime(int fromId, int toId, Map<Integer, double[]> matrix) {
        double[] dist = matrix.get(fromId);
        if (dist == null || toId >= dist.length) return Double.MAX_VALUE / 2;
        return dist[toId];
    }

    // ══════════════════════════════════════════════════════════════
    //  PHASE 2 — INITIAL POPULATION (ORDER BUNDLING)
    // ══════════════════════════════════════════════════════════════

    /**
     * Create an initial population using an order bundling heuristic.
     *
     * Rather than purely random assignment (which often produces terrible
     * starting points), we first try to group orders that:
     *   - Have nearby pickup/dropoff nodes.
     *   - Have compatible time windows.
     *   - Won't exceed courier capacity.
     *
     * This produces a much better starting point for the GA,
     * dramatically reducing the number of generations needed.
     *
     * One half of the population uses bundling; the other half is random
     * to maintain diversity and avoid premature convergence.
     */
    private List<Chromosome> createInitialPopulation(List<Courier> couriers,
                                                     List<Order> orders,
                                                     Graph graph,
                                                     Map<Integer, double[]> matrix,
                                                     int currentTime) {
        List<Chromosome> population = new ArrayList<>();

        // Half the population: greedy bundling-based initialisation
        int bundledCount = POPULATION_SIZE / 2;
        for (int i = 0; i < bundledCount; i++)
            population.add(createBundledChromosome(couriers, orders, matrix, currentTime));

        // Other half: random assignment (diversity)
        while (population.size() < POPULATION_SIZE)
            population.add(createRandomChromosome(couriers, orders));

        return population;
    }

    /**
     * Greedy bundling chromosome:
     *   1. Sort orders by readyTime (earliest food first).
     *   2. For each order, assign it to the courier that can pick it up
     *      soonest without violating any existing order's deadline.
     *   3. Insert pickup and dropoff at positions that minimise detour.
     *
     * The resulting route may have P1→P2→D1→D2 or P1→D1→P2→D2 —
     * whichever sequence was computed as cheaper for the greedy heuristic.
     */
    private Chromosome createBundledChromosome(List<Courier> couriers,
                                               List<Order> orders,
                                               Map<Integer, double[]> matrix,
                                               int currentTime) {
        Chromosome c = new Chromosome(couriers.size());

        // Sort orders: earliest readyTime first (food ready soonest)
        List<Order> sorted = new ArrayList<>(orders);
        sorted.sort(Comparator.comparingInt(Order::getReadyTime));

        for (Order order : sorted) {
            int bestCourier = -1;
            int bestPickupPos  = 0;
            int bestDropoffPos = 1;
            double bestCost = Double.MAX_VALUE;

            for (int ci = 0; ci < couriers.size(); ci++) {
                Courier courier = couriers.get(ci);
                List<RouteAction> route = c.routes.get(ci);

                // Respect courier capacity
                long pickupsInRoute = route.stream()
                        .filter(a -> a.type == ActionType.PICKUP).count();
                if (pickupsInRoute >= courier.getCapacity()) continue;

                // Try inserting pickup at position pi and dropoff at position di > pi
                for (int pi = 0; pi <= route.size(); pi++) {
                    for (int di = pi + 1; di <= route.size() + 1; di++) {
                        double cost = insertionCost(route, order, pi, di,
                                courier, matrix, currentTime);
                        if (cost < bestCost) {
                            bestCost     = cost;
                            bestCourier  = ci;
                            bestPickupPos  = pi;
                            bestDropoffPos = di;
                        }
                    }
                }
            }

            if (bestCourier >= 0) {
                // Insert dropoff first (higher index), then pickup (lower index)
                // so indices don't shift unexpectedly
                c.routes.get(bestCourier).add(bestDropoffPos,
                        new RouteAction(ActionType.DROPOFF, order));
                c.routes.get(bestCourier).add(bestPickupPos,
                        new RouteAction(ActionType.PICKUP, order));
            } else {
                // No feasible courier found — assign to least-loaded one
                int fallback = leastLoadedCourier(c, couriers);
                c.routes.get(fallback).add(new RouteAction(ActionType.PICKUP, order));
                c.routes.get(fallback).add(new RouteAction(ActionType.DROPOFF, order));
            }
        }

        return c;
    }

    /**
     * Estimate the cost of inserting an order's pickup at position pi
     * and dropoff at position di in a given route.
     *
     * Cost = total simulated delivery time for all orders in the route
     * after the insertion. Returns a large penalty if the insertion
     * causes any order to miss its deadline.
     */
    private double insertionCost(List<RouteAction> route, Order newOrder,
                                 int pi, int di, Courier courier,
                                 Map<Integer, double[]> matrix, int currentTime) {
        // Build a trial route with the new order inserted
        List<RouteAction> trial = new ArrayList<>(route);
        trial.add(di, new RouteAction(ActionType.DROPOFF, newOrder));
        trial.add(pi, new RouteAction(ActionType.PICKUP, newOrder));

        return simulateRouteCost(trial, courier, matrix, currentTime);
    }

    /**
     * Simulate executing a route and return its total cost.
     * Cost = sum of delivery times for all orders, with penalties
     * for any order that misses its deadline.
     */
    private double simulateRouteCost(List<RouteAction> route, Courier courier,
                                     Map<Integer, double[]> matrix, int currentTime) {
        if (route.isEmpty()) return 0;

        double time = currentTime;
        double totalCost = 0;
        Node currentPos = courier.getCurrentNode();

        // Track pickup times per order (to enforce precedence in simulation)
        Map<Integer, Double> pickupTimes = new HashMap<>();

        for (RouteAction action : route) {
            Node dest = (action.type == ActionType.PICKUP)
                    ? action.order.getPickup()
                    : action.order.getDropoff();

            if (dest == null || currentPos == null) continue;

            // Travel to the action's node
            double travel = travelTime(currentPos.getId(), dest.getId(), matrix);
            time += travel;

            if (action.type == ActionType.PICKUP) {
                // Wait at restaurant if food isn't ready yet
                time = Math.max(time, action.order.getReadyTime());
                pickupTimes.put(action.order.getId(), time);
            } else {
                // Delivery: check deadline
                double deadline = action.order.getDeadline();
                if (time > deadline) {
                    // Penalty proportional to how late we are
                    totalCost += LATE_PENALTY + (time - deadline);
                }
                totalCost += time; // minimise delivery time
            }

            currentPos = dest;
        }

        return totalCost;
    }

    /** Random chromosome: assign each order to a random courier. */
    private Chromosome createRandomChromosome(List<Courier> couriers, List<Order> orders) {
        Chromosome c = new Chromosome(couriers.size());
        List<Order> shuffled = new ArrayList<>(orders);
        Collections.shuffle(shuffled, rng);

        for (Order order : shuffled) {
            // Pick a random courier that still has capacity
            List<Integer> available = new ArrayList<>();
            for (int i = 0; i < couriers.size(); i++) {
                long pickups = c.routes.get(i).stream()
                        .filter(a -> a.type == ActionType.PICKUP).count();
                if (pickups < couriers.get(i).getCapacity()) available.add(i);
            }
            int ci = available.isEmpty()
                    ? rng.nextInt(couriers.size())
                    : available.get(rng.nextInt(available.size()));

            // Insert pickup before dropoff at random positions
            List<RouteAction> route = c.routes.get(ci);
            int pi = route.isEmpty() ? 0 : rng.nextInt(route.size() + 1);
            route.add(pi, new RouteAction(ActionType.PICKUP, order));
            int di = pi + 1 + (route.size() > pi + 1 ? rng.nextInt(route.size() - pi) : 0);
            route.add(di, new RouteAction(ActionType.DROPOFF, order));
        }
        return c;
    }

    // ══════════════════════════════════════════════════════════════
    //  PHASE 3a — FITNESS FUNCTION
    // ══════════════════════════════════════════════════════════════

    /**
     * Evaluate the fitness of a chromosome.
     *
     * Fitness components (lower = better):
     *   1. Total delivery time across all orders.
     *   2. Heavy penalty for every order that misses its deadline.
     *   3. Extra weight for orders that are close to their deadline
     *      (urgency), so the GA prioritises them.
     *
     * This directly reflects the project's fitness function described
     * in section "פונקציית הכשירות" of the proposal.
     */
    private double evaluateFitness(Chromosome chromosome,
                                   List<Courier> couriers,
                                   List<Order> orders,
                                   Map<Integer, double[]> matrix,
                                   int currentTime) {
        double totalFitness = 0;

        for (int ci = 0; ci < couriers.size(); ci++) {
            Courier courier = couriers.get(ci);
            List<RouteAction> route = chromosome.routes.get(ci);
            totalFitness += simulateRouteCost(route, courier, matrix, currentTime);
        }

        // Extra urgency weight: boost penalty for near-deadline orders
        // so the GA is pressured to serve them first
        for (Order o : orders) {
            double timeLeft = o.getDeadline() - currentTime;
            if (timeLeft < 20 && timeLeft > 0) {
                // The closer to deadline, the higher the urgency weight
                totalFitness += URGENCY_WEIGHT * (20 - timeLeft);
            }
        }

        return totalFitness;
    }

    // ══════════════════════════════════════════════════════════════
    //  PHASE 3b — SELECTION
    // ══════════════════════════════════════════════════════════════

    /**
     * Tournament selection: pick 3 random chromosomes, return the best.
     * Balances selection pressure with diversity — avoids always picking
     * the global best, which would cause premature convergence.
     */
    private Chromosome tournamentSelect(List<Chromosome> population) {
        int size = population.size();
        Chromosome best = population.get(rng.nextInt(size));
        for (int i = 1; i < 3; i++) {
            Chromosome candidate = population.get(rng.nextInt(size));
            if (candidate.fitness < best.fitness) best = candidate;
        }
        return best;
    }

    // ══════════════════════════════════════════════════════════════
    //  PHASE 3c — CROSSOVER
    // ══════════════════════════════════════════════════════════════

    /**
     * Route-based crossover: for each courier, randomly choose whether
     * to inherit the route segment from parent1 or parent2.
     *
     * After combining, orders that appear in both routes (duplicates)
     * or in neither route (missing) are repaired by the repair() method.
     *
     * This corresponds to the "Crossover" section of the proposal:
     * "שילוב חלקים משני כרומוזומים מוצלחים"
     */
    private Chromosome crossover(Chromosome p1, Chromosome p2,
                                 int numCouriers, List<Order> orders) {
        Chromosome child = new Chromosome(numCouriers);

        for (int ci = 0; ci < numCouriers; ci++) {
            // For each courier, inherit from p1 or p2 with equal probability
            List<RouteAction> source = rng.nextBoolean()
                    ? p1.routes.get(ci)
                    : p2.routes.get(ci);
            for (RouteAction a : source)
                child.routes.get(ci).add(new RouteAction(a.type, a.order));
        }

        return child;
    }

    // ══════════════════════════════════════════════════════════════
    //  PHASE 3d — MUTATION
    // ══════════════════════════════════════════════════════════════

    /**
     * Mutation: randomly reorder some actions within one courier's route,
     * or move an order from one courier to another.
     *
     * Two mutation strategies (chosen randomly):
     *   1. Swap two actions within the same courier's route (local swap).
     *   2. Move a complete order (pickup+dropoff pair) to a different courier.
     *
     * After mutation, repair() is always called to fix constraint violations.
     *
     * Corresponds to "מוטציה" in the proposal.
     */
    private void mutate(Chromosome c, Random rng) {
        // Pick a non-empty courier route
        List<Integer> nonEmpty = new ArrayList<>();
        for (int i = 0; i < c.routes.size(); i++)
            if (!c.routes.get(i).isEmpty()) nonEmpty.add(i);

        if (nonEmpty.isEmpty()) return;

        int ci = nonEmpty.get(rng.nextInt(nonEmpty.size()));
        List<RouteAction> route = c.routes.get(ci);

        if (rng.nextBoolean() && route.size() >= 2) {
            // Strategy 1: swap two random positions in this courier's route
            int i = rng.nextInt(route.size());
            int j = rng.nextInt(route.size());
            Collections.swap(route, i, j);

        } else if (c.routes.size() > 1) {
            // Strategy 2: move a random order to a different courier
            // Find orders assigned to this courier
            Set<Integer> orderIds = new HashSet<>();
            for (RouteAction a : route) orderIds.add(a.order.getId());

            if (orderIds.isEmpty()) return;

            int targetOrderId = new ArrayList<>(orderIds)
                    .get(rng.nextInt(orderIds.size()));

            // Remove both actions for this order from the current courier
            List<RouteAction> moved = new ArrayList<>();
            route.removeIf(a -> {
                if (a.order.getId() == targetOrderId) { moved.add(a); return true; }
                return false;
            });

            // Add them to a random different courier
            int other = rng.nextInt(c.routes.size() - 1);
            if (other >= ci) other++;
            List<RouteAction> dest = c.routes.get(other);

            // Insert pickup and dropoff at valid positions
            dest.add(rng.nextInt(dest.size() + 1), moved.stream()
                    .filter(a -> a.type == ActionType.PICKUP).findFirst()
                    .orElse(moved.get(0)));
            int dropIdx = rng.nextInt(dest.size() + 1);
            dest.add(dropIdx, moved.stream()
                    .filter(a -> a.type == ActionType.DROPOFF).findFirst()
                    .orElse(moved.get(moved.size() - 1)));
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  PHASE 3e — REPAIR
    // ══════════════════════════════════════════════════════════════

    /**
     * Repair a chromosome after crossover or mutation.
     *
     * Problems that can arise and are fixed here:
     *   1. An order appears in two different couriers' routes (duplicate).
     *      → Remove the extra occurrence.
     *   2. An order appears in no courier's route (missing).
     *      → Assign it to the least-loaded courier.
     *   3. A DROPOFF appears before its PICKUP (precedence violation).
     *      → Move the DROPOFF to after the PICKUP.
     *
     * "בכל הניצול הגנטי מבוצעים תיקונים מקומיים (repair)" — proposal
     */
    private void repair(Chromosome c, List<Courier> couriers) {
        // Count how many times each order appears (as pickup)
        Map<Integer, Integer> pickupCount = new HashMap<>();
        for (List<RouteAction> route : c.routes)
            for (RouteAction a : route)
                if (a.type == ActionType.PICKUP)
                    pickupCount.merge(a.order.getId(), 1, Integer::sum);

        // Fix duplicates: remove extra pickups+dropoffs
        Set<Integer> seen = new HashSet<>();
        for (List<RouteAction> route : c.routes) {
            route.removeIf(a -> {
                if (a.type == ActionType.PICKUP) {
                    if (seen.contains(a.order.getId())) return true; // duplicate
                    seen.add(a.order.getId());
                }
                return false;
            });
            // Also remove orphaned dropoffs (whose pickup was removed)
            Set<Integer> inRoute = new HashSet<>();
            route.stream().filter(a -> a.type == ActionType.PICKUP)
                    .forEach(a -> inRoute.add(a.order.getId()));
            route.removeIf(a -> a.type == ActionType.DROPOFF
                    && !inRoute.contains(a.order.getId()));
        }

        // Fix precedence: DROPOFF must come after PICKUP for same order
        for (List<RouteAction> route : c.routes) {
            Map<Integer, Integer> pickupIdx = new HashMap<>();
            for (int i = 0; i < route.size(); i++)
                if (route.get(i).type == ActionType.PICKUP)
                    pickupIdx.put(route.get(i).order.getId(), i);

            for (int i = 0; i < route.size(); i++) {
                RouteAction a = route.get(i);
                if (a.type == ActionType.DROPOFF) {
                    Integer pi = pickupIdx.get(a.order.getId());
                    if (pi != null && pi > i) {
                        // Dropoff is before pickup — move dropoff to after pickup
                        route.remove(i);
                        route.add(pi + 1 < route.size() ? pi + 1 : route.size(),
                                new RouteAction(ActionType.DROPOFF, a.order));
                    }
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  PHASE 3f — LOCAL SEARCH
    // ══════════════════════════════════════════════════════════════

    /**
     * Local search phase: identify "late" orders and try to relocate them.
     *
     * For each order that is projected to be late:
     *   1. Identify which courier is currently assigned to it.
     *   2. Try moving it to every other courier's route.
     *   3. If any move reduces the total fitness, apply it.
     *
     * This is the "Relocation of Late Orders" strategy described in
     * "שלב הליטוש: חיפוש מקומי וניהול הזמנות בעיכוב" of the proposal.
     *
     * Also implements "Bundle Late with Fast": if a courier has a fast
     * route, consider adding a late order to it if the total time
     * still respects all deadlines.
     */
    private void localSearch(Chromosome c, List<Courier> couriers,
                             List<Order> orders, Map<Integer, double[]> matrix,
                             int currentTime) {
        double originalFitness = evaluateFitness(c, couriers, orders, matrix, currentTime);

        for (Order lateOrder : identifyLateOrders(c, couriers, matrix, currentTime)) {

            // Find which courier currently carries this order
            int sourceCi = -1;
            for (int ci = 0; ci < c.routes.size(); ci++)
                for (RouteAction a : c.routes.get(ci))
                    if (a.order.getId() == lateOrder.getId()
                            && a.type == ActionType.PICKUP) {
                        sourceCi = ci;
                        break;
                    }

            if (sourceCi < 0) continue;

            // Try moving to every other courier
            for (int targetCi = 0; targetCi < c.routes.size(); targetCi++) {
                if (targetCi == sourceCi) continue;

                // Check capacity
                long pickups = c.routes.get(targetCi).stream()
                        .filter(a -> a.type == ActionType.PICKUP).count();
                if (pickups >= couriers.get(targetCi).getCapacity()) continue;

                Chromosome trial = c.copy();

                // Remove from source
                trial.routes.get(sourceCi).removeIf(
                        a -> a.order.getId() == lateOrder.getId());

                // Append to target
                List<RouteAction> targetRoute = trial.routes.get(targetCi);
                targetRoute.add(new RouteAction(ActionType.PICKUP,  lateOrder));
                targetRoute.add(new RouteAction(ActionType.DROPOFF, lateOrder));

                double trialFitness = evaluateFitness(
                        trial, couriers, orders, matrix, currentTime);

                if (trialFitness < originalFitness) {
                    // Apply the move
                    c.routes.get(sourceCi).removeIf(
                            a -> a.order.getId() == lateOrder.getId());
                    c.routes.get(targetCi).add(
                            new RouteAction(ActionType.PICKUP, lateOrder));
                    c.routes.get(targetCi).add(
                            new RouteAction(ActionType.DROPOFF, lateOrder));
                    originalFitness = trialFitness;
                    break;
                }
            }
        }
    }

    /**
     * Identify orders in the chromosome that are projected to be late.
     * "Late" = the simulated delivery time exceeds the order's deadline.
     */
    private List<Order> identifyLateOrders(Chromosome c, List<Courier> couriers,
                                           Map<Integer, double[]> matrix,
                                           int currentTime) {
        List<Order> late = new ArrayList<>();

        for (int ci = 0; ci < couriers.size(); ci++) {
            Courier courier = couriers.get(ci);
            double time = currentTime;
            Node pos = courier.getCurrentNode();

            for (RouteAction action : c.routes.get(ci)) {
                Node dest = action.type == ActionType.PICKUP
                        ? action.order.getPickup()
                        : action.order.getDropoff();

                if (dest == null || pos == null) continue;

                time += travelTime(pos.getId(), dest.getId(), matrix);

                if (action.type == ActionType.PICKUP)
                    time = Math.max(time, action.order.getReadyTime());
                else if (time > action.order.getDeadline())
                    if (!late.contains(action.order)) late.add(action.order);

                pos = dest;
            }
        }

        return late;
    }

    // ══════════════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════════════

    private Chromosome getBest(List<Chromosome> population) {
        return population.stream()
                .min(Comparator.comparingDouble(c -> c.fitness))
                .orElse(population.get(0));
    }

    private int leastLoadedCourier(Chromosome c, List<Courier> couriers) {
        int best = 0;
        long minLoad = Long.MAX_VALUE;
        for (int i = 0; i < couriers.size(); i++) {
            long load = c.routes.get(i).stream()
                    .filter(a -> a.type == ActionType.PICKUP).count();
            if (load < minLoad) { minLoad = load; best = i; }
        }
        return best;
    }
}