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
import javafx.scene.control.ScrollPane;
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
 *  Only change from the original:
 *    drawCouriers() now uses the Courier's edge-traversal fields
 *    (edgeOriginNode, edgeDestNode, edgeTicksDone, edgeTicksTotal)
 *    to interpolate the visual position smoothly across the full
 *    duration of each edge, instead of the old previousNode/currentNode
 *    single-tick interpolation.
 *
 *  Animation formula:
 *    fraction = (edgeTicksDone - 1 + animProgress) / edgeTicksTotal
 *    x = edgeOrigin.x + (edgeDest.x - edgeOrigin.x) * fraction
 *
 *  This gives perfect continuity: the position at animProgress=0 of
 *  tick T equals the position at animProgress=1 of tick T-1.
 * ═══════════════════════════════════════════════════
 */
public class MapView extends Application {

    // ── Layout ───────────────────────────────────────────────────
    private static final double MAP_W     = 780;
    private static final double MAP_H     = 620;
    private static final double SIDEBAR_W = 310;
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

    // ── Static bridge ─────────────────────────────────────────────
    private static Controller pendingController;
    public  static void setController(Controller c) { pendingController = c; }

    // ── Instance fields ───────────────────────────────────────────
    private Controller controller;

    private static final long   TICK_INTERVAL_NS = 600_000_000L;
    private              long   tickStartTime    = 0;
    private              double animProgress     = 1.0;

    private List<Courier>  lastCouriers   = new ArrayList<>();
    private Graph          lastGraph      = null;
    private Restaurant     lastRestaurant = null;
    private List<Order>    lastOrders     = new ArrayList<>();

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

        new AnimationTimer() {
            @Override public void handle(long now) {
                if (tickStartTime > 0) {
                    animProgress = Math.min(1.0,
                            (double)(now - tickStartTime) / TICK_INTERVAL_NS);
                }
                redrawCouriersOnly();
            }
        }.start();

        controller.onViewReady(this);
    }

    // ── Public display API ────────────────────────────────────────

    public void drawMap(Graph graph, Restaurant restaurant,
                        List<Order> orders, List<Courier> couriers) {
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

    private void redrawCouriersOnly() {
        if (lastGraph == null || lastCouriers == null) return;
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(BG_DARK);
        gc.fillRect(0, 0, MAP_W, MAP_H);
        drawRoads(gc, lastGraph);
        drawIntersections(gc, lastGraph);
        drawRestaurant(gc, lastRestaurant);
        drawCustomers(gc, lastOrders);
        drawCouriers(gc, lastCouriers, animProgress);
        drawLegend(gc);
    }

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

    public void drawTable(List<OrderTableRow> rows) {
        ordersTableBox.getChildren().clear();

        String[] headers = { "#",  "COURIER", "STATUS", "ETA", "DL", "SCORE" };
        double[] widths  = { 30,   62,         74,       40,   32,    52    };

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

        for (int r = 0; r < rows.size(); r++) {
            OrderTableRow row = rows.get(r);

            HBox dataRow = new HBox();
            dataRow.setPadding(new Insets(5, 6, 5, 6));
            Color rowBg = r % 2 == 0 ? Color.web("#12141A") : Color.web("#15181F");
            dataRow.setBackground(new Background(
                    new BackgroundFill(rowBg, CornerRadii.EMPTY, null)));

            Color statusColor = switch (row.status) {
                case "ASSIGNED"  -> Color.web("#818CF8");
                case "PICKED UP" -> Color.web("#FCD34D");
                case "DELIVERED" -> Color.web("#6B7280");
                default          -> CUSTOMER_C;
            };

            Label idLbl = new Label("●  #" + row.orderId);
            idLbl.setMinWidth(widths[0] + 10);
            idLbl.setMaxWidth(widths[0] + 10);
            idLbl.setTextFill(statusColor);
            idLbl.setFont(Font.font("Helvetica Neue", FontWeight.BOLD, 11));

            Label courierLbl = new Label(row.courierLabel);
            courierLbl.setMinWidth(widths[1]);
            courierLbl.setMaxWidth(widths[1]);
            courierLbl.setTextFill(row.courierLabel.equals("—") ? TEXT_DIM : COURIER_C);
            courierLbl.setFont(Font.font("Helvetica Neue", FontWeight.BOLD, 11));

            Label statusLbl = new Label(row.status);
            statusLbl.setMinWidth(widths[2]);
            statusLbl.setMaxWidth(widths[2]);
            statusLbl.setTextFill(statusColor);
            statusLbl.setFont(Font.font("Helvetica Neue", FontWeight.BOLD, 10));

            Label etaLbl = new Label(row.eta);
            etaLbl.setMinWidth(widths[3]);
            etaLbl.setMaxWidth(widths[3]);
            etaLbl.setTextFill(row.isLate ? LATE_COLOR : TEXT_PRIMARY);
            etaLbl.setFont(Font.font("Helvetica Neue", 11));

            Label dlLbl = new Label("" + row.deadline);
            dlLbl.setMinWidth(widths[4]);
            dlLbl.setMaxWidth(widths[4]);
            dlLbl.setTextFill(row.isLate ? LATE_COLOR : TEXT_DIM);
            dlLbl.setFont(Font.font("Helvetica Neue", 11));

            Label scoreLbl = new Label(row.score);
            scoreLbl.setMinWidth(widths[5]);
            scoreLbl.setMaxWidth(widths[5]);
            scoreLbl.setTextFill(row.score.equals("—") ? TEXT_DIM : Color.web("#A78BFA"));
            scoreLbl.setFont(Font.font("Helvetica Neue", 10));

            dataRow.getChildren().addAll(idLbl, courierLbl, statusLbl, etaLbl, dlLbl, scoreLbl);
            ordersTableBox.getChildren().add(dataRow);
        }
    }

    // ── Drawing helpers ───────────────────────────────────────────

    private void drawRoads(GraphicsContext gc, Graph graph) {
        for (int fromId : graph.nodeIds()) {
            Node from = graph.getNode(fromId);
            for (Graph.Edge edge : graph.getEdges(fromId)) {
                if (edge.getTo() <= fromId) continue;
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

        for (int i = 4; i >= 1; i--) {
            gc.setFill(Color.color(RESTAURANT_C.getRed(),
                    RESTAURANT_C.getGreen(), RESTAURANT_C.getBlue(), 0.05 * i));
            double g = 18 + i * 4;
            gc.fillOval(n.getX() - g, n.getY() - g, g * 2, g * 2);
        }

        gc.setFill(RESTAURANT_C);
        gc.fillRoundRect(n.getX() - 14, n.getY() - 14, 28, 28, 6, 6);

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Helvetica Neue", FontWeight.BOLD, 16));
        gc.fillText("R", n.getX() - 5, n.getY() + 6);

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
                case ASSIGNED  -> Color.web("#818CF8");
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

    /**
     * Draw all couriers with smooth edge-traversal animation.
     *
     * Position interpolation (while on an edge):
     *   fraction = (edgeTicksDone - 1 + animProgress) / edgeTicksTotal
     *
     * This is continuous across ticks:
     *   end of tick T   (animProgress=1): fraction = edgeTicksDone / edgeTicksTotal
     *   start of tick T+1 (animProgress=0): fraction = edgeTicksDone / edgeTicksTotal ✓
     *
     * When the courier is idle or waiting, it is drawn at currentNode.
     */
    private void drawCouriers(GraphicsContext gc, List<Courier> couriers,
                              double progress) {
        for (int idx = 0; idx < couriers.size(); idx++) {
            Courier c   = couriers.get(idx);
            Node    cur = c.getCurrentNode();
            if (cur == null) continue;

            // ── Smooth position along the current edge ────────────
            double x, y;

            Node edgeOrigin = c.getEdgeOriginNode();
            Node edgeDest   = c.getEdgeDestNode();
            int  ticksDone  = c.getEdgeTicksDone();
            int  ticksTotal = c.getEdgeTicksTotal();

            boolean onEdge = edgeOrigin != null && edgeDest != null
                    && ticksTotal > 0
                    && c.getStatus() != Courier.Status.AVAILABLE
                    && c.getStatus() != Courier.Status.WAITING_AT_RESTAURANT;

            if (onEdge) {
                // fraction ∈ [0, 1] across the full edge duration
                // (edgeTicksDone - 1 + animProgress) / edgeTicksTotal
                //  → 0 at the very start of the edge (tick 1, animProgress=0)
                //  → 1 at the very end  of the edge (last tick, animProgress=1)
                double fraction = (ticksDone - 1 + progress) / ticksTotal;
                fraction = Math.max(0.0, Math.min(1.0, fraction));
                x = edgeOrigin.getX() + (edgeDest.getX() - edgeOrigin.getX()) * fraction;
                y = edgeOrigin.getY() + (edgeDest.getY() - edgeOrigin.getY()) * fraction;
            } else {
                x = cur.getX();
                y = cur.getY();
            }

            // Small lateral offset so couriers on the same node don't overlap
            x += (idx % 2 == 0 ? -1 : 1) * (idx * 8.0);

            // ── Status colour ─────────────────────────────────────
            Color fill = switch (c.getStatus()) {
                case AVAILABLE             -> Color.web("#6B7280");
                case HEADING_TO_RESTAURANT -> COURIER_C;
                case WAITING_AT_RESTAURANT -> Color.web("#FCD34D");
                case DELIVERING            -> Color.web("#F97316");
            };

            // ── Motion trail (drawn along the current edge) ───────
            if (onEdge && c.getStatus() != Courier.Status.AVAILABLE
                    && c.getStatus() != Courier.Status.WAITING_AT_RESTAURANT) {
                gc.setStroke(Color.color(fill.getRed(), fill.getGreen(), fill.getBlue(), 0.15));
                gc.setLineWidth(2);
                gc.strokeLine(edgeOrigin.getX(), edgeOrigin.getY(),
                        edgeDest.getX(),   edgeDest.getY());
            }

            // ── Glow ──────────────────────────────────────────────
            gc.setFill(Color.color(fill.getRed(), fill.getGreen(), fill.getBlue(), 0.2));
            gc.fillOval(x - 16, y - 16, 32, 32);

            // ── Triangle ──────────────────────────────────────────
            gc.setFill(fill);
            gc.fillPolygon(
                    new double[]{ x,      x - 11,  x + 11 },
                    new double[]{ y - 12, y + 8,   y + 8  },
                    3);

            // ── ID ────────────────────────────────────────────────
            gc.setFill(Color.BLACK);
            gc.setFont(Font.font("Helvetica Neue", FontWeight.BOLD, 9));
            gc.fillText(String.valueOf(c.getId()), x - 3, y + 5);

            // ── Status label ──────────────────────────────────────
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

        gc.setFill(Color.web("#12141A", 0.88));
        gc.fillRoundRect(x - 4, y - 12, 220, 95, 8, 8);

        gc.setFill(TEXT_DIM);
        gc.setFont(Font.font("Helvetica Neue", FontWeight.BOLD, 8));
        gc.fillText("LEGEND", x, y - 1);
        y += 10;

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

    // ── Sidebar & bottom bar ──────────────────────────────────────

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

        Label tableTitle = new Label("ORDERS");
        tableTitle.setTextFill(TEXT_DIM);
        tableTitle.setFont(Font.font("Helvetica Neue", FontWeight.BOLD, 10));

        ordersTableBox = new VBox(0);
        ordersTableBox.setBackground(new Background(
                new BackgroundFill(Color.web("#0E1016"), new CornerRadii(4), null)));

        // Wrap the orders table in a ScrollPane so it doesn't push
        // the bottom bar off-screen when there are many orders.
        ScrollPane tableScroll = new ScrollPane(ordersTableBox);
        tableScroll.setFitToWidth(true);
        tableScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        tableScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        tableScroll.setStyle(
                "-fx-background: #0E1016;" +
                        "-fx-background-color: #0E1016;" +
                        "-fx-border-color: transparent;");

        VBox tableSection = new VBox(6, tableTitle, tableScroll);
        tableSection.setPadding(new Insets(10));
        tableSection.setBackground(new Background(
                new BackgroundFill(PANEL_BG, new CornerRadii(6), null)));

        // Let the table section grow to fill remaining vertical space.
        VBox.setVgrow(tableSection, Priority.ALWAYS);

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

        btnStart.setOnAction(e -> {
            boolean nowRunning = !controller.isRunning();
            controller.setRunning(nowRunning);
            btnStart.setText(nowRunning ? "⏸  Pause" : "▶  Start");
        });

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