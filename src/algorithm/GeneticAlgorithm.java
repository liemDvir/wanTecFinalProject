package algorithm;

import model.*;
import java.util.*;
import java.util.function.Consumer;

/**
 * ═══════════════════════════════════════════════════════════════
 *  GeneticAlgorithm — Hybrid Genetic Algorithm for delivery routing.
 *
 *  Implements the full algorithm as described in the project proposal:
 *
 *  Phase 1 — Preprocessing: Dijkstra distance matrix for O(1) lookups.
 *  Phase 2 — Order Bundling: greedy bundling by geographic proximity
 *            and time-window compatibility for quality initial population.
 *  Phase 3 — Genetic Loop:
 *    a) Fitness: minimise average delivery latency with late penalties,
 *       urgency weighting, and unassigned-order penalties.
 *    b) Selection: elitism + tournament.
 *    c) Crossover: route-level with full repair (duplicates AND missing).
 *    d) Mutation: intra-route swap OR inter-courier order move.
 *    e) Repair: fix duplicates, missing orders, precedence violations.
 *    f) Local Search: relocate late orders + bundle-late-with-fast.
 *  Phase 4 — Return best chromosome.
 *
 *  Waiting Strategy is handled by the Controller (courier waits at
 *  restaurant if food isn't ready yet), not inside the GA itself.
 * ═══════════════════════════════════════════════════════════════
 */
public class GeneticAlgorithm {

    // ── Tunable parameters ────────────────────────────────────────
    private static final int    POPULATION_SIZE   = 30;
    private static final int    NUM_GENERATIONS   = 20;
    private static final double MUTATION_RATE     = 0.15;
    private static final double CROSSOVER_RATE    = 0.80;
    private static final int    ELITE_COUNT       = 3;
    private static final double LATE_PENALTY      = 1000.0;
    private static final double URGENCY_WEIGHT    = 2.0;
    private static final double UNASSIGNED_PENALTY = 5000.0; // per missing order

    private final Random rng = new Random();

    // ══════════════════════════════════════════════════════════════
    //  INNER CLASSES
    // ══════════════════════════════════════════════════════════════

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

    public static class Chromosome {
        public final List<List<RouteAction>> routes;
        public       double                 fitness;

        public Chromosome(int numCouriers) {
            routes  = new ArrayList<>();
            for (int i = 0; i < numCouriers; i++) routes.add(new ArrayList<>());
            fitness = Double.MAX_VALUE;
        }

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

    public static class GenerationStats {
        public final int    generation;
        public final double bestFitness;
        public final double worstFitness;
        public final double avgFitness;
        public final double improvement;
        public final double improvePct;
        public final double overallBest;

        public GenerationStats(int generation, double bestFitness,
                               double worstFitness, double avgFitness,
                               double improvement, double improvePct,
                               double overallBest) {
            this.generation   = generation;
            this.bestFitness  = bestFitness;
            this.worstFitness = worstFitness;
            this.avgFitness   = avgFitness;
            this.improvement  = improvement;
            this.improvePct   = improvePct;
            this.overallBest  = overallBest;
        }

        public String toLogLine() {
            String delta = improvement > 0
                    ? String.format("-%.1f (-%.1f%%)", improvement, improvePct)
                    : (improvement == 0 && generation == 0)
                      ? "  (initial)"
                      : "  (no change)";
            return String.format(
                    "[GA] Gen %2d | Best: %8.1f  Avg: %8.1f  Worst: %8.1f | Δ %-22s | Overall best: %.1f",
                    generation, bestFitness, avgFitness, worstFitness, delta, overallBest);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  PUBLIC ENTRY POINTS
    // ══════════════════════════════════════════════════════════════

    public Chromosome run(List<Courier> couriers, List<Order> orders,
                          Graph graph, int currentTime,
                          Consumer<GenerationStats> genLogger) {

        if (couriers.isEmpty() || orders.isEmpty()) return null;

        // ── Phase 1: Distance matrix ──────────────────────────────
        Map<Integer, double[]> distMatrix = buildDistanceMatrix(graph, couriers, orders);

        // ── Phase 2: Initial population ───────────────────────────
        List<Chromosome> population =
                createInitialPopulation(couriers, orders, graph, distMatrix, currentTime);

        for (Chromosome c : population)
            c.fitness = evaluateFitness(c, couriers, orders, distMatrix, currentTime);

        // ── Phase 3: Genetic loop ─────────────────────────────────
        Chromosome best        = getBest(population);
        double     prevBestFit = best.fitness;
        double     overallBest = best.fitness;

        if (genLogger != null)
            genLogger.accept(computeStats(0, population, prevBestFit, overallBest, true));

        for (int gen = 1; gen <= NUM_GENERATIONS; gen++) {

            population.sort(Comparator.comparingDouble(c -> c.fitness));
            List<Chromosome> nextGen = new ArrayList<>();

            // Elitism
            for (int i = 0; i < Math.min(ELITE_COUNT, population.size()); i++)
                nextGen.add(population.get(i).copy());

            // Fill rest of population
            while (nextGen.size() < POPULATION_SIZE) {
                Chromosome parent1 = tournamentSelect(population);
                Chromosome parent2 = tournamentSelect(population);

                Chromosome child;
                if (rng.nextDouble() < CROSSOVER_RATE)
                    child = crossover(parent1, parent2, couriers.size(), orders);
                else
                    child = parent1.copy();

                if (rng.nextDouble() < MUTATION_RATE)
                    mutate(child, rng);

                // Repair handles: duplicates, missing orders, precedence
                repair(child, couriers, orders);

                child.fitness = evaluateFitness(child, couriers, orders, distMatrix, currentTime);
                nextGen.add(child);
            }

            // Local search on every chromosome
            for (Chromosome c : nextGen)
                localSearch(c, couriers, orders, distMatrix, currentTime);

            population = nextGen;

            Chromosome genBest = getBest(population);
            if (genBest.fitness < best.fitness) best = genBest.copy();
            if (genBest.fitness < overallBest) overallBest = genBest.fitness;

            if (genLogger != null)
                genLogger.accept(computeStats(gen, population, prevBestFit, overallBest, false));

            prevBestFit = genBest.fitness;
        }

        return best;
    }

    public Chromosome run(List<Courier> couriers, List<Order> orders,
                          Graph graph, int currentTime) {
        return run(couriers, orders, graph, currentTime, null);
    }

    // ══════════════════════════════════════════════════════════════
    //  STATS
    // ══════════════════════════════════════════════════════════════

    private GenerationStats computeStats(int gen, List<Chromosome> population,
                                         double prevBest, double overallBest,
                                         boolean isInitial) {
        double best = Double.MAX_VALUE, worst = Double.MIN_VALUE, sum = 0;
        for (Chromosome c : population) {
            if (c.fitness < best)  best  = c.fitness;
            if (c.fitness > worst) worst = c.fitness;
            sum += c.fitness;
        }
        double avg         = sum / population.size();
        double improvement = isInitial ? 0 : Math.max(0, prevBest - best);
        double improvePct  = isInitial ? 0 : (prevBest > 0 ? (improvement / prevBest) * 100.0 : 0);
        return new GenerationStats(gen, best, worst, avg, improvement, improvePct, overallBest);
    }

    // ══════════════════════════════════════════════════════════════
    //  PHASE 1 — PREPROCESSING (Distance Matrix)
    // ══════════════════════════════════════════════════════════════

    private Map<Integer, double[]> buildDistanceMatrix(Graph graph,
                                                       List<Courier> couriers,
                                                       List<Order> orders) {
        Map<Integer, double[]> matrix = new HashMap<>();
        Set<Integer> sources = new HashSet<>();

        for (Courier c : couriers)
            if (c.getCurrentNode() != null) sources.add(c.getCurrentNode().getId());
        for (Order o : orders) {
            if (o.getPickup()  != null) sources.add(o.getPickup().getId());
            if (o.getDropoff() != null) sources.add(o.getDropoff().getId());
        }
        for (int src : sources) matrix.put(src, graph.dijkstra(src));
        return matrix;
    }

    private double travelTime(int fromId, int toId, Map<Integer, double[]> matrix) {
        double[] dist = matrix.get(fromId);
        if (dist == null || toId >= dist.length) return Double.MAX_VALUE / 2;
        return dist[toId];
    }

    // ══════════════════════════════════════════════════════════════
    //  PHASE 2 — INITIAL POPULATION (Order Bundling)
    //
    //  Per proposal: "קיבוץ הזמנות" — group orders by geographic
    //  proximity and time-window compatibility. Half the population
    //  uses bundling; the other half is random for diversity.
    // ══════════════════════════════════════════════════════════════

    private List<Chromosome> createInitialPopulation(List<Courier> couriers,
                                                     List<Order> orders, Graph graph,
                                                     Map<Integer, double[]> matrix,
                                                     int currentTime) {
        List<Chromosome> population = new ArrayList<>();
        int bundled = POPULATION_SIZE / 2;
        for (int i = 0; i < bundled; i++)
            population.add(createBundledChromosome(couriers, orders, matrix, currentTime));
        while (population.size() < POPULATION_SIZE)
            population.add(createRandomChromosome(couriers, orders));
        return population;
    }

    /**
     * Greedy bundling: sort orders by readyTime, then for each order
     * try every courier × every insertion position and pick the cheapest.
     * This produces bundled routes like [P1, P2, D2, D1] where orders
     * that are geographically close and temporally compatible are grouped.
     */
    private Chromosome createBundledChromosome(List<Courier> couriers,
                                               List<Order> orders,
                                               Map<Integer, double[]> matrix,
                                               int currentTime) {
        Chromosome c = new Chromosome(couriers.size());
        List<Order> sorted = new ArrayList<>(orders);
        sorted.sort(Comparator.comparingInt(Order::getReadyTime));

        for (Order order : sorted) {
            int    bestCourier = -1, bestPi = 0, bestDi = 1;
            double bestCost = Double.MAX_VALUE;

            for (int ci = 0; ci < couriers.size(); ci++) {
                List<RouteAction> route = c.routes.get(ci);
                long pickups = route.stream().filter(a -> a.type == ActionType.PICKUP).count();
                if (pickups >= couriers.get(ci).getCapacity()) continue;

                for (int pi = 0; pi <= route.size(); pi++) {
                    for (int di = pi + 1; di <= route.size() + 1; di++) {
                        double cost = insertionCost(route, order, pi, di, couriers.get(ci), matrix, currentTime);
                        if (cost < bestCost) {
                            bestCost = cost; bestCourier = ci; bestPi = pi; bestDi = di;
                        }
                    }
                }
            }

            if (bestCourier >= 0) {
                c.routes.get(bestCourier).add(bestPi, new RouteAction(ActionType.PICKUP, order));
                c.routes.get(bestCourier).add(bestDi, new RouteAction(ActionType.DROPOFF, order));
            } else {
                int fb = leastLoadedCourier(c, couriers);
                c.routes.get(fb).add(new RouteAction(ActionType.PICKUP, order));
                c.routes.get(fb).add(new RouteAction(ActionType.DROPOFF, order));
            }
        }
        return c;
    }

    private double insertionCost(List<RouteAction> route, Order newOrder,
                                 int pi, int di, Courier courier,
                                 Map<Integer, double[]> matrix, int currentTime) {
        List<RouteAction> trial = new ArrayList<>(route);
        trial.add(pi, new RouteAction(ActionType.PICKUP, newOrder));
        trial.add(di, new RouteAction(ActionType.DROPOFF, newOrder));
        return simulateRouteCost(trial, courier, matrix, currentTime);
    }

    /**
     * Simulate a route and return its cost.
     *
     * Per proposal ("פונקציית הכשירות"):
     *   - Cost = sum of (deliveryTime - orderTime) for each DROPOFF
     *     → minimises average delivery latency, not absolute time.
     *   - LATE_PENALTY + overshoot added when delivery exceeds deadline.
     *   - Courier waits at restaurant if food isn't ready (max(time, readyTime)).
     */
    private double simulateRouteCost(List<RouteAction> route, Courier courier,
                                     Map<Integer, double[]> matrix, int currentTime) {
        if (route.isEmpty()) return 0;

        double time      = currentTime;
        double totalCost = 0;
        Node   pos       = courier.getCurrentNode();

        for (RouteAction action : route) {
            Node dest = (action.type == ActionType.PICKUP)
                    ? action.order.getPickup()
                    : action.order.getDropoff();

            if (dest == null || pos == null) continue;

            time += travelTime(pos.getId(), dest.getId(), matrix);

            if (action.type == ActionType.PICKUP) {
                // Wait at restaurant if food isn't ready
                time = Math.max(time, action.order.getReadyTime());
            } else {
                // DROPOFF: delivery latency = deliveryTime - orderTime
                double latency = time - action.order.getOrderTime();
                totalCost += latency;

                // Late penalty if past deadline
                if (time > action.order.getDeadline()) {
                    totalCost += LATE_PENALTY + (time - action.order.getDeadline());
                }
            }

            pos = dest;
        }
        return totalCost;
    }

    /** Random chromosome: assign each order to a random courier. */
    private Chromosome createRandomChromosome(List<Courier> couriers, List<Order> orders) {
        Chromosome c = new Chromosome(couriers.size());
        List<Order> shuffled = new ArrayList<>(orders);
        Collections.shuffle(shuffled, rng);

        for (Order order : shuffled) {
            List<Integer> available = new ArrayList<>();
            for (int i = 0; i < couriers.size(); i++) {
                long pickups = c.routes.get(i).stream()
                        .filter(a -> a.type == ActionType.PICKUP).count();
                if (pickups < couriers.get(i).getCapacity()) available.add(i);
            }
            int ci = available.isEmpty()
                    ? rng.nextInt(couriers.size())
                    : available.get(rng.nextInt(available.size()));

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
    //
    //  Per proposal ("פונקציית הכשירות"):
    //    1. Sum of delivery latencies (deliveryTime - orderTime).
    //    2. Heavy penalty for late deliveries (past deadline).
    //    3. Urgency weight for near-deadline orders.
    //    4. UNASSIGNED_PENALTY for orders not in any route.
    // ══════════════════════════════════════════════════════════════

    private double evaluateFitness(Chromosome chromosome,
                                   List<Courier> couriers,
                                   List<Order> orders,
                                   Map<Integer, double[]> matrix,
                                   int currentTime) {
        double totalFitness = 0;

        // Route costs (delivery latencies + late penalties)
        for (int ci = 0; ci < couriers.size(); ci++)
            totalFitness += simulateRouteCost(chromosome.routes.get(ci),
                    couriers.get(ci), matrix, currentTime);

        // Penalty for unassigned orders — prevents "do nothing" from winning
        Set<Integer> assignedIds = new HashSet<>();
        for (List<RouteAction> route : chromosome.routes)
            for (RouteAction a : route)
                if (a.type == ActionType.PICKUP)
                    assignedIds.add(a.order.getId());

        for (Order o : orders)
            if (!assignedIds.contains(o.getId()))
                totalFitness += UNASSIGNED_PENALTY;

        // Urgency weight: orders near deadline get extra cost pressure
        for (Order o : orders) {
            double timeLeft = o.getDeadline() - currentTime;
            if (timeLeft > 0 && timeLeft < 20)
                totalFitness += URGENCY_WEIGHT * (20 - timeLeft);
        }

        return totalFitness;
    }

    // ══════════════════════════════════════════════════════════════
    //  PHASE 3b — SELECTION (Tournament, size 3)
    // ══════════════════════════════════════════════════════════════

    private Chromosome tournamentSelect(List<Chromosome> population) {
        Chromosome best = population.get(rng.nextInt(population.size()));
        for (int i = 1; i < 3; i++) {
            Chromosome c = population.get(rng.nextInt(population.size()));
            if (c.fitness < best.fitness) best = c;
        }
        return best;
    }

    // ══════════════════════════════════════════════════════════════
    //  PHASE 3c — CROSSOVER
    //
    //  Per proposal ("הצלבה"): swap entire courier routes between
    //  parents. After crossover, repair() fixes duplicates AND adds
    //  back any orders that got lost.
    // ══════════════════════════════════════════════════════════════

    private Chromosome crossover(Chromosome p1, Chromosome p2,
                                 int numCouriers, List<Order> orders) {
        Chromosome child = new Chromosome(numCouriers);
        for (int ci = 0; ci < numCouriers; ci++) {
            List<RouteAction> source = rng.nextBoolean()
                    ? p1.routes.get(ci) : p2.routes.get(ci);
            for (RouteAction a : source)
                child.routes.get(ci).add(new RouteAction(a.type, a.order));
        }
        return child;
    }

    // ══════════════════════════════════════════════════════════════
    //  PHASE 3d — MUTATION
    //
    //  Per proposal ("מוטציה"):
    //    Strategy 1: swap two actions within a courier route.
    //    Strategy 2: move an order (P+D pair) to a different courier.
    // ══════════════════════════════════════════════════════════════

    private void mutate(Chromosome c, Random rng) {
        List<Integer> nonEmpty = new ArrayList<>();
        for (int i = 0; i < c.routes.size(); i++)
            if (!c.routes.get(i).isEmpty()) nonEmpty.add(i);
        if (nonEmpty.isEmpty()) return;

        int ci = nonEmpty.get(rng.nextInt(nonEmpty.size()));
        List<RouteAction> route = c.routes.get(ci);

        if (rng.nextBoolean() && route.size() >= 2) {
            // Strategy 1: swap two positions
            int i = rng.nextInt(route.size());
            int j = rng.nextInt(route.size());
            Collections.swap(route, i, j);

        } else if (c.routes.size() > 1) {
            // Strategy 2: move an order to a different courier
            Set<Integer> orderIds = new HashSet<>();
            for (RouteAction a : route) orderIds.add(a.order.getId());
            if (orderIds.isEmpty()) return;

            int targetId = new ArrayList<>(orderIds).get(rng.nextInt(orderIds.size()));
            List<RouteAction> moved = new ArrayList<>();
            route.removeIf(a -> {
                if (a.order.getId() == targetId) { moved.add(a); return true; }
                return false;
            });

            int other = rng.nextInt(c.routes.size() - 1);
            if (other >= ci) other++;
            List<RouteAction> dest = c.routes.get(other);

            RouteAction pickup  = moved.stream().filter(a -> a.type == ActionType.PICKUP).findFirst().orElse(moved.get(0));
            RouteAction dropoff = moved.stream().filter(a -> a.type == ActionType.DROPOFF).findFirst().orElse(moved.get(moved.size()-1));

            int pIdx = rng.nextInt(dest.size() + 1);
            dest.add(pIdx, pickup);
            int dIdx = pIdx + 1 + rng.nextInt(dest.size() - pIdx);
            dest.add(dIdx, dropoff);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  PHASE 3e — REPAIR
    //
    //  Per proposal ("תיקונים מקומיים repair"):
    //    1. Remove duplicate pickups (order in >1 courier).
    //    2. Remove orphaned dropoffs.
    //    3. ADD BACK missing orders (order in 0 couriers).
    //    4. Fix precedence: PICKUP must come before DROPOFF.
    // ══════════════════════════════════════════════════════════════

    private void repair(Chromosome c, List<Courier> couriers, List<Order> orders) {
        // ── Step 1: Remove duplicate pickups ──────────────────────
        Set<Integer> seen = new HashSet<>();
        for (List<RouteAction> route : c.routes) {
            route.removeIf(a -> {
                if (a.type == ActionType.PICKUP) {
                    if (seen.contains(a.order.getId())) return true;
                    seen.add(a.order.getId());
                }
                return false;
            });
        }

        // ── Step 2: Remove orphaned dropoffs ──────────────────────
        Set<Integer> allPickups = new HashSet<>();
        for (List<RouteAction> route : c.routes)
            for (RouteAction a : route)
                if (a.type == ActionType.PICKUP) allPickups.add(a.order.getId());

        for (List<RouteAction> route : c.routes)
            route.removeIf(a -> a.type == ActionType.DROPOFF
                    && !allPickups.contains(a.order.getId()));

        // ── Step 3: Add back missing orders ───────────────────────
        // Any order not present in any route is appended to the
        // least-loaded courier. This prevents crossover from
        // "losing" orders and is required by the proposal.
        Set<Integer> presentIds = new HashSet<>(allPickups);
        for (Order o : orders) {
            if (!presentIds.contains(o.getId())) {
                int ci = leastLoadedCourier(c, couriers);
                List<RouteAction> route = c.routes.get(ci);
                route.add(new RouteAction(ActionType.PICKUP, o));
                route.add(new RouteAction(ActionType.DROPOFF, o));
            }
        }

        // ── Step 4: Fix precedence (PICKUP before DROPOFF) ────────
        for (List<RouteAction> route : c.routes) {
            boolean changed = true;
            while (changed) {
                changed = false;
                Map<Integer, Integer> pickupIdx = new HashMap<>();
                for (int i = 0; i < route.size(); i++)
                    if (route.get(i).type == ActionType.PICKUP)
                        pickupIdx.put(route.get(i).order.getId(), i);

                for (int i = 0; i < route.size(); i++) {
                    RouteAction a = route.get(i);
                    if (a.type == ActionType.DROPOFF) {
                        Integer pi = pickupIdx.get(a.order.getId());
                        if (pi != null && pi > i) {
                            route.remove(i);
                            route.add(Math.min(pi, route.size()),
                                    new RouteAction(ActionType.DROPOFF, a.order));
                            changed = true;
                            break;
                        }
                    }
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  PHASE 3f — LOCAL SEARCH
    //
    //  Per proposal ("שלב הליטוש"):
    //    1. Relocation of late orders to a closer/less-loaded courier.
    //    2. Bundle-late-with-fast: if a courier has a fast route,
    //       try adding a late order to it (if total still within deadline).
    // ══════════════════════════════════════════════════════════════

    private void localSearch(Chromosome c, List<Courier> couriers,
                             List<Order> orders, Map<Integer, double[]> matrix,
                             int currentTime) {
        double currentFitness = evaluateFitness(c, couriers, orders, matrix, currentTime);

        // ── 1. Relocation of late orders ──────────────────────────
        for (Order lateOrder : identifyLateOrders(c, couriers, matrix, currentTime)) {
            int sourceCi = findCourierForOrder(c, lateOrder);
            if (sourceCi < 0) continue;

            for (int targetCi = 0; targetCi < c.routes.size(); targetCi++) {
                if (targetCi == sourceCi) continue;

                long pickups = c.routes.get(targetCi).stream()
                        .filter(a -> a.type == ActionType.PICKUP).count();
                if (pickups >= couriers.get(targetCi).getCapacity()) continue;

                Chromosome trial = c.copy();
                trial.routes.get(sourceCi).removeIf(a -> a.order.getId() == lateOrder.getId());

                // Try best insertion position on the target route
                List<RouteAction> tRoute = trial.routes.get(targetCi);
                double bestCost = Double.MAX_VALUE;
                int bestPi = tRoute.size(), bestDi = tRoute.size() + 1;

                for (int pi = 0; pi <= tRoute.size(); pi++) {
                    for (int di = pi + 1; di <= tRoute.size() + 1; di++) {
                        List<RouteAction> test = new ArrayList<>(tRoute);
                        test.add(pi, new RouteAction(ActionType.PICKUP, lateOrder));
                        test.add(di, new RouteAction(ActionType.DROPOFF, lateOrder));
                        double cost = simulateRouteCost(test, couriers.get(targetCi), matrix, currentTime);
                        if (cost < bestCost) { bestCost = cost; bestPi = pi; bestDi = di; }
                    }
                }

                tRoute.add(bestPi, new RouteAction(ActionType.PICKUP, lateOrder));
                tRoute.add(bestDi, new RouteAction(ActionType.DROPOFF, lateOrder));

                double trialFitness = evaluateFitness(trial, couriers, orders, matrix, currentTime);
                if (trialFitness < currentFitness) {
                    // Apply the move
                    c.routes.get(sourceCi).removeIf(a -> a.order.getId() == lateOrder.getId());
                    c.routes.set(targetCi, trial.routes.get(targetCi));
                    currentFitness = trialFitness;
                    break;
                }
            }
        }

        // ── 2. Bundle-late-with-fast ──────────────────────────────
        // Per proposal: "קיבוץ מהיר עם מאוחר"
        // Find couriers with fast routes and try adding a late order to them.
        List<Order> lateOrders = identifyLateOrders(c, couriers, matrix, currentTime);
        if (lateOrders.isEmpty()) return;

        for (int ci = 0; ci < couriers.size(); ci++) {
            List<RouteAction> route = c.routes.get(ci);
            double routeCost = simulateRouteCost(route, couriers.get(ci), matrix, currentTime);

            // "Fast" route = one whose total cost is below the average
            if (routeCost > currentFitness / Math.max(1, couriers.size())) continue;

            long pickups = route.stream().filter(a -> a.type == ActionType.PICKUP).count();
            if (pickups >= couriers.get(ci).getCapacity()) continue;

            for (Order late : lateOrders) {
                int lateCi = findCourierForOrder(c, late);
                if (lateCi < 0 || lateCi == ci) continue;

                // Try inserting the late order at every position in the fast route
                double bestCost = Double.MAX_VALUE;
                int bestPi = route.size(), bestDi = route.size() + 1;

                for (int pi = 0; pi <= route.size(); pi++) {
                    for (int di = pi + 1; di <= route.size() + 1; di++) {
                        List<RouteAction> test = new ArrayList<>(route);
                        test.add(pi, new RouteAction(ActionType.PICKUP, late));
                        test.add(di, new RouteAction(ActionType.DROPOFF, late));
                        double cost = simulateRouteCost(test, couriers.get(ci), matrix, currentTime);
                        if (cost < bestCost) { bestCost = cost; bestPi = pi; bestDi = di; }
                    }
                }

                // Only apply if total fitness improves
                Chromosome trial = c.copy();
                trial.routes.get(lateCi).removeIf(a -> a.order.getId() == late.getId());
                trial.routes.get(ci).add(bestPi, new RouteAction(ActionType.PICKUP, late));
                trial.routes.get(ci).add(bestDi, new RouteAction(ActionType.DROPOFF, late));

                double trialFitness = evaluateFitness(trial, couriers, orders, matrix, currentTime);
                if (trialFitness < currentFitness) {
                    c.routes.get(lateCi).removeIf(a -> a.order.getId() == late.getId());
                    c.routes.set(ci, trial.routes.get(ci));
                    currentFitness = trialFitness;
                    break; // one improvement per fast courier
                }
            }
        }
    }

    private List<Order> identifyLateOrders(Chromosome c, List<Courier> couriers,
                                           Map<Integer, double[]> matrix, int currentTime) {
        List<Order> late = new ArrayList<>();
        for (int ci = 0; ci < couriers.size(); ci++) {
            double time = currentTime;
            Node pos = couriers.get(ci).getCurrentNode();
            for (RouteAction action : c.routes.get(ci)) {
                Node dest = action.type == ActionType.PICKUP
                        ? action.order.getPickup() : action.order.getDropoff();
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

    /** Find which courier route contains a PICKUP for the given order. */
    private int findCourierForOrder(Chromosome c, Order order) {
        for (int ci = 0; ci < c.routes.size(); ci++)
            for (RouteAction a : c.routes.get(ci))
                if (a.order.getId() == order.getId() && a.type == ActionType.PICKUP)
                    return ci;
        return -1;
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