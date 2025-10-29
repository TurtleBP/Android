package com.pro.milkteaapp.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.io.Serializable;
import java.util.Map;

public class Order implements Serializable {

    public static final String STATUS_PENDING    = "PENDING";
    public static final String STATUS_FINISHED  = "FINISHED";
    public static final String STATUS_CANCELLED  = "CANCELLED";

    private String id;
    private String userId;
    private String status;
    private double finalTotal;
    private double subtotal;
    private double discount;
    private double shippingFee;
    private String address; // chuỗi hiển thị ngắn
    private Timestamp createdAt;

    // Thông tin người nhận (trích từ addressObj nếu có)
    private String receiverName;
    private String receiverPhone;
    private String addressDisplay;

    public Order() {}

    @NonNull
    @SuppressWarnings("unchecked")
    public static Order from(@NonNull DocumentSnapshot d) {
        Order o = new Order();
        o.id = d.getId();
        o.userId = d.getString("userId");
        o.status = d.getString("status");

        Double ft = d.getDouble("finalTotal");
        o.finalTotal = ft == null ? 0 : ft;

        Double st = d.getDouble("subtotal");
        o.subtotal = st == null ? 0 : st;

        Double dc = d.getDouble("discount");
        o.discount = dc == null ? 0 : dc;

        Double sf = d.getDouble("shippingFee");
        o.shippingFee = sf == null ? 0 : sf;

        o.address = d.getString("address");

        o.createdAt = d.getTimestamp("createdAt");

        Object addrObj = d.get("addressObj");
        if (addrObj instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) addrObj;
            Object nm = map.get("fullName");
            Object ph = map.get("phone");
            Object disp = map.get("display");
            o.receiverName  = nm   == null ? "" : String.valueOf(nm);
            o.receiverPhone = ph   == null ? "" : String.valueOf(ph);
            o.addressDisplay= disp == null ? "" : String.valueOf(disp);
        }
        return o;
    }

    // ===== getters =====
    @NonNull public String getId() { return id == null ? "" : id; }
    @Nullable public String getUserId() { return userId; }
    @Nullable public String getStatus() { return status; }
    public double getFinalTotal() { return finalTotal; }
    public double getTotal() { return finalTotal; }               // ✅ thêm alias cho adapter cũ
    public double getSubtotal() { return subtotal; }
    public double getDiscount() { return discount; }
    public double getShippingFee() { return shippingFee; }
    @Nullable public String getAddress() { return address; }
    @Nullable public Timestamp getCreatedAt() { return createdAt; }
    @NonNull public String getReceiverName() { return receiverName == null ? "" : receiverName; }
    @NonNull public String getReceiverPhone() { return receiverPhone == null ? "" : receiverPhone; }
    @NonNull public String getAddressDisplay() {
        if (addressDisplay != null && !addressDisplay.isEmpty()) return addressDisplay;
        return address == null ? "" : address;
    }

    // ===== setters cho phía admin dùng khi cần tạo/ghi tạm =====
    public void setId(@NonNull String id) { this.id = id; }      // ✅ fix AdminOrdersFragment
    public void setStatus(@Nullable String status) { this.status = status; }
    public void setFinalTotal(double finalTotal) { this.finalTotal = finalTotal; }
    public void setSubtotal(double subtotal) { this.subtotal = subtotal; }
    public void setDiscount(double discount) { this.discount = discount; }
    public void setShippingFee(double shippingFee) { this.shippingFee = shippingFee; }
    public void setAddress(@Nullable String address) { this.address = address; }
    public void setCreatedAt(@Nullable Timestamp createdAt) { this.createdAt = createdAt; }
}
