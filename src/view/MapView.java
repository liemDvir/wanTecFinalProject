package view;

import controller.Controller;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import model.*;

import java.util.List;
import java.util.ArrayList;

/**
 * ═══════════════════════════════════════════════════
 *  MapView — the V in MVC.
 *
 *  Responsibilities (ONLY these):
 *    - Render whatever the Controller gives it.
 *    - Expose public draw methods:
 *        drawMap(graph, restaurant, orders, couriers)
 *        updateSidebar(time, waiting, pickedUp, delivered, couriers)
 *    - On each animation tick: notify Controller via tick(),
 *      then let Controller push updated data back via drawMap().
 *    - Forward button presses to Controller.
 *
 *  Rules:
 *    - NEVER holds a Model reference.
 *    - NEVER makes any decision.
 *    - NEVER creates or modifies data objects.
 *    - Receives everything it needs as method parameters.
 * ═══════════════════════════════════════════════════
 */
public class MapView extends Application {

    // ── Layout ───────────────────────────────────────────────────
    private static final double MAP_W     = 780;
    private static final double MAP_H     = 620;
    private static final double SIDEBAR_W = 310;  // wider to fit SCORE column
    private static final double BOTTOM_H  = 56;

    // ── Colour palette ────────────────────────────────────────────
    private static final Color BG_DARK      = Color.web("#12141A");
    private static final Color ROAD_COLOR   = Color.web("#2E3340");
    private static final Color ROAD_OUTLINE = Color.web("#3A4050");
    private static final Color NODE_COLOR   = Color.web("#3D4455");
    private static final Color RESTAURANT_C = Color.web("#FF8C42");
    private static final Color COURIER_C    = Color.web("#4ADE80");
    private static final Color CUSTOMER_C   = Color.web("#60A5FA");
    private static final Color TEXT_PRIMARY = Color.web("#E8EAF0");
    private static final Color TEXT_DIM     = Color.web("#6B7280");
    private static final Color ACCENT       = Color.web("#FF8C42");
    private static final Color SIDEBAR_BG   = Color.web("#0E1016");
    private static final Color PANEL_BG     = Color.web("#1A1D27");
    private static final Color LATE_COLOR   = Color.web("#F87171");

    // ── Static bridge — set by Controller before launch() ────────
    private static Controller pendingController;
    public  static void setController(Controller c) { pendingController = c; }

    // ── Instance fields ───────────────────────────────────────────
    private Controller controller;

    // Animation state — used ONLY for smooth visual interpolation.
    // The Controller owns simulation logic; this is purely cosmetic.
    // progress goes 0→1 over TICK_INTERVAL_NS, then resets each tick.
    private static final long   TICK_INTERVAL_NS = 600_000_000L;
    private              long   tickStartTime    = 0;  // ns timestamp of last tick
    private              double animProgress     = 1.0; // 0.0=just moved, 1.0=arrived

    // Snapshot of couriers kept for interpolation between ticks
    private List<Courier> lastCouriers  = new ArrayList<>();
    private Graph         lastGraph     = null;
    private Restaurant    lastRestaurant = null;
    private List<Order>   lastOrders    = new ArrayList<>();

    // UI nodes
    private Canvas canvas;
    private Label  lblTime;
    private Label  lblOrders;
    private Label  lblCouriers;
    private Button btnStart;
    private VBox   ordersTableBox;

    // ── JavaFX lifecycle ──────────────────────────────────────────

    @Override
    public void start(Stage stage) {
        this.controller = pendingController;

        canvas  = new Canvas(MAP_W, MAP_H);
        VBox sidebar   = buildSidebar();
        HBox bottomBar = buildBottomBar();

        VBox root = new VBox(new HBox(canvas, sidebar), bottomBar);
        root.setBackground(new Background(
                new BackgroundFill(BG_DARK, null, null)));

        stage.setTitle("Delivery Simulation");
        stage.setScene(new Scene(root, MAP_W + SIDEBAR_W, MAP_H + BOTTOM_H));
        stage.setResizable(false);
        stage.show();

        // AnimationTimer — purely for smooth visual rendering.
        // Runs at ~60fps, only responsible for interpolating courier positions.
        // All simulation logic stays in the Controller thread.
        new AnimationTimer() {
            @Override public void handle(long now) {
                if (tickStartTime > 0) {
                    // progress: 0.0 = just ticked, 1.0 = next tick due
                    animProgress = Math.min(1.0,
                            (double)(now - tickStartTime) / TICK_INTERVAL_NS);
                }
                // Redraw couriers at interpolated position every frame
                redrawCouriersOnly();
            }
        }.start();

        controller.onViewReady(this);
    }

    // ══════════════════════════════════════════════════════════════
    //  PUBLIC DISPLAY API — called by Controller, never by View itself
    // ══════════════════════════════════════════════════════════════

    /**
     * Full redraw — called by Controller after each tick.
     * Saves a snapshot of the data for the animation loop to use.
     */
    public void drawMap(Graph graph, Restaurant restaurant,
                        List<Order> orders, List<Courier> couriers) {
        // Save snapshot for the animation loop
        lastGraph      = graph;
        lastRestaurant = restaurant;
        lastOrders     = orders;
        lastCouriers   = couriers;
        tickStartTime  = System.nanoTime();
        animProgress   = 0.0;

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(BG_DARK);
        gc.fillRect(0, 0, MAP_W, MAP_H);
        if (graph == null) return;

        drawRoads(gc, graph);
        drawIntersections(gc, graph);
        drawRestaurant(gc, restaurant);
        drawCustomers(gc, orders);
        drawCouriers(gc, couriers, animProgress);
        drawLegend(gc);
    }

    /**
     * Lightweight redraw called at ~60fps by the AnimationTimer.
     * Only redraws the moving parts (couriers) over the static background.
     * Avoids redrawing roads/restaurant on every frame.
     */
    private void redrawCouriersOnly() {
        if (lastGraph == null || lastCouriers == null) return;
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // Repaint background + static elements once per tick,
        // then let animation loop only update couriers
        gc.setFill(BG_DARK);
        gc.fillRect(0, 0, MAP_W, MAP_H);
        drawRoads(gc, lastGraph);
        drawIntersections(gc, lastGraph);
        drawRestaurant(gc, lastRestaurant);
        drawCustomers(gc, lastOrders);
        drawCouriers(gc, lastCouriers, animProgress);
        drawLegend(gc);
    }

    /**
     * Refresh the sidebar stats.
     * Controller passes the exact numbers — View just displays them.
     */
    public void updateSidebar(int time, int waiting, int assigned,
                              int pickedUp, int delivered, int courierCount) {
        lblTime.setText("t = " + time);
        lblOrders.setText(
                "WAITING "   + waiting   + "  |  " +
                        "ASSIGNED "  + assigned  + "  |  " +
                        "PICKED "    + pickedUp  + "  |  " +
                        "DONE "      + delivered);
        lblCouriers.setText(courierCount + " active");
    }

    /**
     * Rebuild the orders table inside the Sidebar.
     * Controller passes rows — View only renders them.
     */
    public void drawTable(List<OrderTableRow> rows) {
        ordersTableBox.getChildren().clear();

        // ── Header ────────────────────────────────────────────────
        String[] headers = { "#",  "COURIER", "STATUS", "ETA", "DL", "SCORE" };
        double[] widths  = { 30,   62,         74,       40,   32,    52    };
        // total = 30+62+74+40+32+52 = 290 + padding(6+6) = 302 → fits in 310

        HBox headerRow = new HBox();
        headerRow.setBackground(new Background(
                new BackgroundFill(Color.web("#1A1D27"), new CornerRadii(4,4,0,0,false), null)));
        headerRow.setPadding(new Insets(5, 6, 5, 6));

        for (int i = 0; i < headers.length; i++) {
            Label h = new Label(headers[i]);
            h.setMinWidth(widths[i]);
            h.setMaxWidth(widths[i]);
            h.setTextFill(TEXT_DIM);
            h.setFont(Font.font("Helvetica Neue", FontWeight.BOLD, 9));
            headerRow.getChildren().add(h);
        }
        ordersTableBox.getChildren().add(headerRow);

        // ── Data rows ─────────────────────────────────────────────
        for (int r = 0; r < rows.size(); r++) {
            OrderTableRow row = rows.get(r);

            HBox dataRow = new HBox();
            dataRow.setPadding(new Insets(5, 6, 5, 6));
            Color rowBg = r % 2 == 0
                    ? Color.web("#12141A")
                    : Color.web("#15181F");
            dataRow.setBackground(new Background(
                    new BackgroundFill(rowBg, CornerRadii.EMPTY, null)));

            // Status dot colour
            Color statusColor = switch (row.status) {
                case "ASSIGNED"  -> Color.web("#818CF8"); // indigo
                case "PICKED UP" -> Color.web("#FCD34D");
                case "DELIVERED" -> Color.web("#6B7280");
                default          -> CUSTOMER_C;
            };

            // Col 0: ID with coloured dot
            Label idLbl = new Label("●  #" + row.orderId);
            idLbl.setMinWidth(widths[0] + 10);
            idLbl.setMaxWidth(widths[0] + 10);
            idLbl.setTextFill(statusColor);
            idLbl.setFont(Font.font("Helvetica Neue", FontWeight.BOLD, 11));

            // Col 1: Courier
            Label courierLbl = new Label(row.courierLabel);
            courierLbl.setMinWidth(widths[1]);
            courierLbl.setMaxWidth(widths[1]);
            courierLbl.setTextFill(
                    row.courierLabel.equals("—") ? TEXT_DIM : COURIER_C);
            courierLbl.setFont(Font.font("Helvetica Neue", FontWeight.BOLD, 11));

            // Col 2: Status badge
            Label statusLbl = new Label(row.status);
            statusLbl.setMinWidth(widths[2]);
            statusLbl.setMaxWidth(widths[2]);
            statusLbl.setTextFill(statusColor);
            statusLbl.setFont(Font.font("Helvetica Neue", FontWeight.BOLD, 10));

            // Col 3: ETA
            Label etaLbl = new Label(row.eta);
            etaLbl.setMinWidth(widths[3]);
            etaLbl.setMaxWidth(widths[3]);
            etaLbl.setTextFill(row.isLate ? LATE_COLOR : TEXT_PRIMARY);
            etaLbl.setFont(Font.font("Helvetica Neue", 11));

            // Col 4: Deadline
            Label dlLbl = new Label("" + row.deadline);
            dlLbl.setMinWidth(widths[4]);
            dlLbl.setMaxWidth(widths[4]);
            dlLbl.setTextFill(row.isLate ? LATE_COLOR : TEXT_DIM);
            dlLbl.setFont(Font.font("Helvetica Neue", 11));

            // Col 5: Score — shown only while WAITING, dim otherwise
            Label scoreLbl = new Label(row.score);
            scoreLbl.setMinWidth(widths[5]);
            scoreLbl.setMaxWidth(widths[5]);
            scoreLbl.setTextFill(row.score.equals("—")
                    ? TEXT_DIM
                    : Color.web("#A78BFA")); // purple — clearly a debug value
            scoreLbl.setFont(Font.font("Helvetica Neue", 10));

            dataRow.getChildren().addAll(idLbl, courierLbl, statusLbl, etaLbl, dlLbl, scoreLbl);
            ordersTableBox.getChildren().add(dataRow);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  PRIVATE DRAWING HELPERS — pure rendering, zero logic
    // ══════════════════════════════════════════════════════════════

    private void drawRoads(GraphicsContext gc, Graph graph) {
        for (int fromId : graph.nodeIds()) {
            Node from = graph.getNode(fromId);
            for (Graph.Edge edge : graph.getEdges(fromId)) {
                if (edge.getTo() <= fromId) continue; // each road once
                Node to = graph.getNode(edge.getTo());
                if (to == null) continue;

                gc.setStroke(ROAD_OUTLINE);
                gc.setLineWidth(9);
                gc.strokeLine(from.getX(), from.getY(), to.getX(), to.getY());

                gc.setStroke(ROAD_COLOR);
                gc.setLineWidth(7);
                gc.strokeLine(from.getX(), from.getY(), to.getX(), to.getY());

                gc.setStroke(Color.web("#4A5060", 0.5));
                gc.setLineWidth(0.8);
                gc.setLineDashes(6, 6);
                gc.strokeLine(from.getX(), from.getY(), to.getX(), to.getY());
                gc.setLineDashes(0);
            }
        }
    }

    private void drawIntersections(GraphicsContext gc, Graph graph) {
        for (int id : graph.nodeIds()) {
            Node n = graph.getNode(id);
            gc.setFill(NODE_COLOR);
            gc.fillOval(n.getX() - 4.5, n.getY() - 4.5, 9, 9);
        }
    }

    private void drawRestaurant(GraphicsContext gc, Restaurant rest) {
        if (rest == null) return;
        Node n = rest.getLocation();

        // Glow rings
        for (int i = 4; i >= 1; i--) {
            gc.setFill(Color.color(RESTAURANT_C.getRed(),
                    RESTAURANT_C.getGreen(), RESTAURANT_C.getBlue(), 0.05 * i));
            double g = 18 + i * 4;
            gc.fillOval(n.getX() - g, n.getY() - g, g * 2, g * 2);
        }

        // Background square
        gc.setFill(RESTAURANT_C);
        gc.fillRoundRect(n.getX() - 14, n.getY() - 14, 28, 28, 6, 6);

        // Clear "R" letter in the centre
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Helvetica Neue", FontWeight.BOLD, 16));
        gc.fillText("R", n.getX() - 5, n.getY() + 6);

        // Name label to the right
        gc.setFill(TEXT_PRIMARY);
        gc.setFont(Font.font("Helvetica Neue", FontWeight.BOLD, 11));
        gc.fillText(rest.getName(), n.getX() + 17, n.getY() + 4);
    }

    private void drawCustomers(GraphicsContext gc, List<Order> orders) {
        for (Order o : orders) {
            Node n = o.getDropoff();
            if (n == null) continue;

            Color c = switch (o.getStatus()) {
                case WAITING   -> CUSTOMER_C;
                case ASSIGNED  -> Color.web("#818CF8"); // indigo — assigned but not picked up yet
                case PICKED_UP -> Color.web("#FCD34D");
                case DELIVERED -> Color.web("#6B7280");
            };
            if (o.isWaiting() || o.isAssigned()) {
                gc.setStroke(Color.color(c.getRed(), c.getGreen(), c.getBlue(), 0.25));
                gc.setLineWidth(1.5);
                gc.strokeOval(n.getX() - 14, n.getY() - 14, 28, 28);
            }
            gc.setFill(c);
            gc.fillOval(n.getX() - 8, n.getY() - 8, 16, 16);
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Helvetica Neue", FontWeight.BOLD, 9));
            gc.fillText(String.valueOf(o.getId()), n.getX() - 3, n.getY() + 4);
        }
    }

    private void drawCouriers(GraphicsContext gc, List<Courier> couriers,
                              double progress) {
        for (int idx = 0; idx < couriers.size(); idx++) {
            Courier c   = couriers.get(idx);
            Node    cur = c.getCurrentNode();
            Node    prev = c.getPreviousNode();
            if (cur == null) continue;

            // Interpolate position between ticks for smooth animation
            double x, y;
            if (prev != null && !prev.equals(cur) && progress < 1.0) {
                x = prev.getX() + (cur.getX() - prev.getX()) * progress;
                y = prev.getY() + (cur.getY() - prev.getY()) * progress;
            } else {
                x = cur.getX();
                y = cur.getY();
            }

            // Small offset so couriers on same node don't overlap
            x += (idx % 2 == 0 ? -1 : 1) * (idx * 8.0);

            // Colour by status
            Color fill = switch (c.getStatus()) {
                case AVAILABLE              -> Color.web("#6B7280");
                case HEADING_TO_RESTAURANT  -> COURIER_C;
                case WAITING_AT_RESTAURANT  -> Color.web("#FCD34D");
                case DELIVERING             -> Color.web("#F97316");
            };

            // Motion trail — only when actually moving
            if (prev != null && !prev.equals(cur) &&
                    c.getStatus() != Courier.Status.AVAILABLE &&
                    c.getStatus() != Courier.Status.WAITING_AT_RESTAURANT) {
                gc.setStroke(Color.color(fill.getRed(), fill.getGreen(), fill.getBlue(), 0.15));
                gc.setLineWidth(2);
                gc.strokeLine(prev.getX(), prev.getY(), cur.getX(), cur.getY());
            }

            // Glow
            gc.setFill(Color.color(fill.getRed(), fill.getGreen(), fill.getBlue(), 0.2));
            gc.fillOval(x - 16, y - 16, 32, 32);

            // Triangle (slightly larger)
            gc.setFill(fill);
            gc.fillPolygon(
                    new double[]{ x,      x - 11,  x + 11 },
                    new double[]{ y - 12, y + 8,   y + 8  },
                    3);

            // ID
            gc.setFill(Color.BLACK);
            gc.setFont(Font.font("Helvetica Neue", FontWeight.BOLD, 9));
            gc.fillText(String.valueOf(c.getId()), x - 3, y + 5);

            // Status label
            String label = switch (c.getStatus()) {
                case AVAILABLE             -> "idle";
                case HEADING_TO_RESTAURANT -> "→ rest.";
                case WAITING_AT_RESTAURANT -> "waiting";
                case DELIVERING            -> "→ cust.";
            };
            gc.setFill(fill);
            gc.setFont(Font.font("Helvetica Neue", 9));
            gc.fillText(label, x - 14, y + 22);
        }
    }

    private void drawLegend(GraphicsContext gc) {
        double x = 14, y = MAP_H - 100;

        // Compact background
        gc.setFill(Color.web("#12141A", 0.88));
        gc.fillRoundRect(x - 4, y - 12, 220, 95, 8, 8);

        gc.setFill(TEXT_DIM);
        gc.setFont(Font.font("Helvetica Neue", FontWeight.BOLD, 8));
        gc.fillText("LEGEND", x, y - 1);
        y += 10;

        // Two columns: left = locations, right = order statuses
        Object[][] left = {
                { RESTAURANT_C,          "Restaurant"   },
                { Color.web("#6B7280"),  "Courier idle" },
                { COURIER_C,             "→ restaurant" },
                { Color.web("#FCD34D"),  "Courier wait" },
                { Color.web("#F97316"),  "→ customer"   },
        };
        Object[][] right = {
                { CUSTOMER_C,            "WAITING"  },
                { Color.web("#818CF8"),  "ASSIGNED" },
                { Color.web("#FCD34D"),  "PICKED"   },
                { Color.web("#6B7280"),  "DONE"     },
        };

        double y0 = y;
        for (Object[] item : left) {
            gc.setFill((Color) item[0]);
            gc.fillOval(x, y - 4, 7, 7);
            gc.setFill(TEXT_PRIMARY);
            gc.setFont(Font.font("Helvetica Neue", 8));
            gc.fillText((String) item[1], x + 11, y + 2);
            y += 14;
        }

        y = y0;
        double x2 = x + 110;
        for (Object[] item : right) {
            gc.setFill((Color) item[0]);
            gc.fillOval(x2, y - 4, 7, 7);
            gc.setFill(TEXT_PRIMARY);
            gc.setFont(Font.font("Helvetica Neue", 8));
            gc.fillText((String) item[1], x2 + 11, y + 2);
            y += 14;
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  SIDEBAR & BOTTOM BAR — UI structure only, no logic
    // ══════════════════════════════════════════════════════════════

    private VBox buildSidebar() {
        lblTime     = styledLabel("t = 0");
        lblOrders   = new Label("—");
        lblOrders.setTextFill(TEXT_PRIMARY);
        lblOrders.setFont(Font.font("Helvetica Neue", FontWeight.BOLD, 10));
        lblOrders.setWrapText(true);
        lblCouriers = styledLabel("—");

        Label title = new Label("SIMULATION");
        title.setTextFill(ACCENT);
        title.setFont(Font.font("Helvetica Neue", FontWeight.BOLD, 13));

        // Orders table container — rows are added by drawTable()
        Label tableTitle = new Label("ORDERS");
        tableTitle.setTextFill(TEXT_DIM);
        tableTitle.setFont(Font.font("Helvetica Neue", FontWeight.BOLD, 10));

        ordersTableBox = new VBox(0);
        ordersTableBox.setBackground(new Background(
                new BackgroundFill(Color.web("#0E1016"), new CornerRadii(4), null)));

        VBox tableSection = new VBox(6, tableTitle, ordersTableBox);
        tableSection.setPadding(new Insets(10));
        tableSection.setBackground(new Background(
                new BackgroundFill(PANEL_BG, new CornerRadii(6), null)));

        VBox box = new VBox(12, title,
                sectionPanel("⏱  Time",     lblTime),
                sectionPanel("📦 Orders",   lblOrders),
                sectionPanel("🛵 Couriers", lblCouriers),
                tableSection);
        box.setPrefWidth(SIDEBAR_W);
        box.setPrefHeight(MAP_H);
        box.setPadding(new Insets(20, 14, 20, 14));
        box.setBackground(new Background(
                new BackgroundFill(SIDEBAR_BG, null, null)));
        return box;
    }

    private HBox buildBottomBar() {
        btnStart = styledButton("▶  Start", ACCENT);
        Button btnRegen = styledButton("⟳  New City", Color.web("#4B5563"));

        // Toggle simulation — View tells Controller to start/pause,
        // Controller owns the running state and the loop
        btnStart.setOnAction(e -> {
            boolean nowRunning = !controller.isRunning();
            controller.setRunning(nowRunning);
            btnStart.setText(nowRunning ? "⏸  Pause" : "▶  Start");
        });

        // View forwards the click — Controller makes all the decisions
        btnRegen.setOnAction(e -> {
            btnStart.setText("▶  Start");
            controller.regenerateCity();
        });

        HBox bar = new HBox(12, btnStart, btnRegen);
        bar.setPadding(new Insets(10, 16, 10, 16));
        bar.setPrefHeight(BOTTOM_H);
        bar.setBackground(new Background(
                new BackgroundFill(SIDEBAR_BG, null, null)));
        return bar;
    }

    private VBox sectionPanel(String heading, Label content) {
        Label h = new Label(heading);
        h.setTextFill(TEXT_DIM);
        h.setFont(Font.font("Helvetica Neue", 11));
        VBox p = new VBox(4, h, content);
        p.setPadding(new Insets(10));
        p.setBackground(new Background(
                new BackgroundFill(PANEL_BG, new CornerRadii(6), null)));
        return p;
    }

    private Label styledLabel(String text) {
        Label l = new Label(text);
        l.setTextFill(TEXT_PRIMARY);
        l.setFont(Font.font("Helvetica Neue", FontWeight.BOLD, 14));
        return l;
    }

    private Button styledButton(String text, Color bg) {
        Button b = new Button(text);
        b.setTextFill(Color.WHITE);
        b.setFont(Font.font("Helvetica Neue", FontWeight.BOLD, 12));
        String hex = String.format("#%02X%02X%02X",
                (int)(bg.getRed()*255), (int)(bg.getGreen()*255), (int)(bg.getBlue()*255));
        b.setStyle("-fx-background-color:" + hex +
                ";-fx-background-radius:6;-fx-padding:8 18 8 18;-fx-cursor:hand;");
        return b;
    }
}