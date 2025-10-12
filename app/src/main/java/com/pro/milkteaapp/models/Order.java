package com.pro.milkteaapp.models;

import com.google.firebase.Timestamp;
import java.util.List;
import java.util.Objects;

public class Order {
    private String id;
    private String userId;
    private String status; // PENDING | FINISHED
    private List<Item> items;
    private double total;
    private Timestamp createdAt;
    private Timestamp confirmedAt;
    private String confirmedBy;
    // ...
    private com.google.firebase.Timestamp canceledAt;
    private String canceledBy;      // uid hoặc email
    private String cancelReason;    // lý do (optional)

    public com.google.firebase.Timestamp getCanceledAt() { return canceledAt; }
    public String getCanceledBy() { return canceledBy; }
    public String getCancelReason() { return cancelReason; }

    public void setCanceledAt(com.google.firebase.Timestamp canceledAt) { this.canceledAt = canceledAt; }
    public void setCanceledBy(String canceledBy) { this.canceledBy = canceledBy; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
// ...

    public static class Item {
        public String productId;
        public String name;
        public double price;
        public int qty;
        public String imageUrl;

        public Item() {}
    }

    public Order() {}

    // Getter/Setter
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public String getStatus() { return status; }
    public List<Item> getItems() { return items; }
    public double getTotal() { return total; }
    public Timestamp getCreatedAt() { return createdAt; }
    public Timestamp getConfirmedAt() { return confirmedAt; }
    public String getConfirmedBy() { return confirmedBy; }

    public void setUserId(String userId) { this.userId = userId; }
    public void setStatus(String status) { this.status = status; }
    public void setItems(List<Item> items) { this.items = items; }
    public void setTotal(double total) { this.total = total; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public void setConfirmedAt(Timestamp confirmedAt) { this.confirmedAt = confirmedAt; }
    public void setConfirmedBy(String confirmedBy) { this.confirmedBy = confirmedBy; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Order order)) return false;
        return Double.compare(order.total, total) == 0 &&
                Objects.equals(id, order.id) &&
                Objects.equals(userId, order.userId) &&
                Objects.equals(status, order.status) &&
                Objects.equals(items, order.items) &&
                Objects.equals(createdAt, order.createdAt) &&
                Objects.equals(confirmedAt, order.confirmedAt) &&
                Objects.equals(confirmedBy, order.confirmedBy);
    }

    @Override public int hashCode() {
        return Objects.hash(id, userId, status, items, total, createdAt, confirmedAt, confirmedBy);
    }
}
