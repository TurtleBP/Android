package com.pro.milkteaapp.models;

import androidx.annotation.NonNull;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * CartItem hoàn chỉnh:
 * - Tính giá theo size & topping
 * - Cầu nối tới Products (productId, name, base price, imageUrl)
 * - Hỗ trợ convert sang Map để lưu Firestore (toOrderItemMap)
 */
public class CartItem implements Serializable {
    private final Products milkTea;   // Không để null
    private int quantity;       // >= 1
    private String size;        // "Nhỏ" | "Vừa" | "Lớn" (tùy app)
    private String topping;     // ví dụ "Trân châu trắng" | null/""
    private double totalPrice;  // = unitPrice * quantity

    public CartItem(Products milkTea, int quantity, String size, String topping) {
        if (milkTea == null) {
            throw new IllegalArgumentException("milkTea không được null");
        }
        this.milkTea = milkTea;
        this.quantity = quantity > 0 ? quantity : 1;
        this.size = size;
        this.topping = topping;
        this.calculateTotalPrice();
    }

    /* =========================
       CÁC GETTER/SETTER CƠ BẢN
       ========================= */
    public Products getMilkTea() {
        return milkTea;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getSize() {
        return size;
    }

    public String getTopping() {
        return topping;
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    public void setQuantity(int quantity) {
        if (quantity > 0) {
            this.quantity = quantity;
            this.calculateTotalPrice();
        }
    }

    public void setSize(String size) {
        this.size = size;
        this.calculateTotalPrice();
    }

    public void setTopping(String topping) {
        this.topping = topping;
        this.calculateTotalPrice();
    }

    /* =======================================
       CẦU NỐI SANG SẢN PHẨM (Products object)
       ======================================= */
    /** ID sản phẩm gốc (để lưu đơn hàng) */
    public String getProductId() {
        return milkTea != null ? milkTea.getId() : null;
    }

    /** Tên sản phẩm gốc */
    public String getName() {
        return milkTea != null ? milkTea.getName() : null;
    }

    /** Giá gốc (chưa cộng size/topping) */
    public double getPrice() {
        return milkTea != null ? milkTea.getPrice() : 0.0;
    }

    /** Ảnh sản phẩm (nếu có) */
    public String getImageUrl() {
        return milkTea != null ? milkTea.getImageUrl() : null;
    }

    /* ===========================
       TÁNH GIÁ & CẬP NHẬT TỔNG
       =========================== */

    /** Giá 1 ly sau khi áp size/topping (không nhân số lượng) */
    public double getUnitPrice() {
        return calculatePriceForOneItem();
    }

    /** Tính giá 1 ly theo size/topping */
    public double calculatePriceForOneItem() {
        double basePrice = getPrice();
        double finalPrice = basePrice;

        // Tính giá theo size (tùy label của bạn)
        // "Vừa" +10%, "Lớn" +20%
        if ("Vừa".equalsIgnoreCase(size)) {
            finalPrice += basePrice * 0.10;
        } else if ("Lớn".equalsIgnoreCase(size)) {
            finalPrice += basePrice * 0.20;
        }
        // nếu "Nhỏ" hoặc null -> giữ nguyên

        // Tính giá theo topping (ví dụ)
        if ("Trân châu trắng".equalsIgnoreCase(topping)) {
            finalPrice += 5000;
        }
        // có thể bổ sung các topping khác sau này

        return finalPrice;
    }

    /** Tổng tiền = unitPrice * quantity */
    public void calculateTotalPrice() {
        this.totalPrice = calculatePriceForOneItem() * this.quantity;
    }

    /* ===========================
       TIỆN ÍCH SỐ LƯỢNG
       =========================== */
    public void increaseQuantity(int addedQuantity) {
        if (addedQuantity > 0) {
            this.quantity += addedQuantity;
            this.calculateTotalPrice();
        }
    }

    public void increaseQuantity() {
        increaseQuantity(1);
    }

    public void decreaseQuantity() {
        if (this.quantity > 1) {
            this.quantity--;
            this.calculateTotalPrice();
        }
    }

    /* ===========================
       FIRESTORE HELPER
       =========================== */
    /**
     * Map phù hợp để đẩy lên Firestore trong mảng items của order:
     * {
     *   productId, name, price (base), qty, imageUrl,
     *   size, topping, unitPrice, lineTotal
     * }
     */
    public Map<String, Object> toOrderItemMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("productId",  getProductId());
        m.put("name",       getName());
        m.put("price",      getPrice());         // base price
        m.put("qty",        getQuantity());
        m.put("imageUrl",   getImageUrl());
        m.put("size",       getSize());
        m.put("topping",    getTopping());
        m.put("unitPrice",  getUnitPrice());     // sau size/topping
        m.put("lineTotal",  getTotalPrice());    // unitPrice * qty
        return m;
    }

    @NonNull
    @Override
    public String toString() {
        return "CartItem{" +
                "milkTea=" + (milkTea != null ? milkTea.getName() : "null") +
                ", quantity=" + quantity +
                ", size='" + size + '\'' +
                ", topping='" + topping + '\'' +
                ", totalPrice=" + totalPrice +
                '}';
    }

    /* ===========================
       EQUALS/HASHCODE
       =========================== */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CartItem cartItem)) return false;
        return quantity == cartItem.quantity &&
                Objects.equals(getProductId(), cartItem.getProductId()) &&
                Objects.equals(size, cartItem.size) &&
                Objects.equals(topping, cartItem.topping);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getProductId(), size, topping, quantity);
    }
}
