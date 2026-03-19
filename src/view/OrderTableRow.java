package view;

/**
 * Data container for one row in the orders table.
 * Controller builds these and passes them to the View.
 */
public class OrderTableRow {

    public final int     orderId;
    public final String  courierLabel;
    public final String  status;
    public final String  eta;
    public final int     deadline;
    public final boolean isLate;
    public final String  score; // weighted score from pickBestOrder — for debugging

    public OrderTableRow(int orderId, String courierLabel,
                         String status, String eta,
                         int deadline, boolean isLate, String score) {
        this.orderId      = orderId;
        this.courierLabel = courierLabel;
        this.status       = status;
        this.eta          = eta;
        this.deadline     = deadline;
        this.isLate       = isLate;
        this.score        = score;
    }
}