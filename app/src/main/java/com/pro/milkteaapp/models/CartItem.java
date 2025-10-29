package com.pro.milkteaapp.models;

import androidx.annotation.NonNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** CartItem hỗ trợ nhiều topping:
 *  - Size: Medium +3000, Large +6000
 *  - Topping: cộng dồn theo selectedToppings
 *  - Hợp nhất item theo (productId + size + sorted(toppingIds))
 */
public class CartItem implements Serializable {
    private static final long serialVersionUID = 2L;

    private static final long DELTA_MEDIUM = 3000L;
    private static final long DELTA_LARGE  = 6000L;

    private final Products product;
    private int quantity;
    private String size; // Small / Medium / Large (hoặc Nhỏ / Vừa / Lớn)
    private ArrayList<SelectedTopping> toppings; // danh sách topping đã chọn
    private long totalPrice;

    public CartItem(@NonNull Products product, int quantity, String size,
                    java.util.List<SelectedTopping> toppings) {
        this.product  = Objects.requireNonNull(product, "product không được null");
        this.quantity = Math.max(1, quantity);
        this.size     = size;
        this.toppings = new ArrayList<>(toppings != null ? toppings : java.util.Collections.emptyList());
        normalizeToppingOrder();
        recalcTotal();
    }

    // ===== Getter/Setter =====
    public Products getMilkTea() { return product; }  // tương thích code cũ
    public Products getProduct() { return product; }
    public int getQuantity() { return quantity; }
    public String getSize()  { return size; }
    public long getTotalPrice() { return totalPrice; }
    public java.util.List<SelectedTopping> getToppings() {
        return java.util.Collections.unmodifiableList(toppings);
    }

    public void setQuantity(int quantity) {
        if (quantity > 0) { this.quantity = quantity; recalcTotal(); }
    }
    public void setSize(String size) { this.size = size; recalcTotal(); }
    public void setToppings(java.util.List<SelectedTopping> list) {
        this.toppings = new ArrayList<>(list != null ? list : java.util.Collections.emptyList());
        normalizeToppingOrder();
        recalcTotal();
    }
    public void increaseQuantity(int added) {
        if (added > 0) { this.quantity += added; recalcTotal(); }
    }
    public void increaseQuantity() { increaseQuantity(1); }
    public void decreaseQuantity() {
        if (this.quantity > 1) { this.quantity--; recalcTotal(); }
    }

    // ===== Giá =====
    public long getBasePrice() {
        Double p = product.getPrice();
        return p == null ? 0L : p.longValue();
    }
    public double getPrice() {
        Double p = product.getPrice();
        return p == null ? 0.0 : p;
    }
    /** Giá 1 ly sau size + tổng topping (chưa nhân quantity) */
    public long getUnitPrice() {
        long unit = getBasePrice();

        String s = normalize(size);
        if ("medium".equals(s) || "vừa".equals(s)) unit += DELTA_MEDIUM;
        else if ("large".equals(s) || "lớn".equals(s)) unit += DELTA_LARGE;

        long tops = 0L;
        for (SelectedTopping t : toppings) tops += Math.max(0L, t.price);
        unit += tops;

        return unit;
    }

    private void recalcTotal() { this.totalPrice = getUnitPrice() * Math.max(1, this.quantity); }

    private static String normalize(String v) {
        return v == null ? "" : v.trim().toLowerCase(Locale.getDefault());
    }

    private void normalizeToppingOrder() {
        Collections.sort(this.toppings, Comparator.comparing(t -> t.id == null ? "" : t.id));
    }

    // ===== Thông tin cầu nối =====
    public String getProductId() { return product.getId(); }
    public String getName()      { return product.getName(); }
    public String getImageUrl()  { return product.getImageUrl(); }

    /** Nhãn gọn để hiển thị trong giỏ */
    public String getToppingsLabel() {
        if (toppings == null || toppings.isEmpty()) return "Không";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < toppings.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(toppings.get(i).name);
        }
        return sb.toString();
    }

    /** Map lưu vào order.items */
    public Map<String, Object> toOrderItemMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("productId", getProductId());
        m.put("name", getName());
        m.put("price", getBasePrice());
        m.put("qty", getQuantity());
        m.put("imageUrl", getImageUrl());
        m.put("size", getSize());

        java.util.List<Map<String, Object>> tops = new java.util.ArrayList<>();
        for (SelectedTopping st : toppings) tops.add(st.toMap());
        m.put("toppings", tops);

        m.put("unitPrice", getUnitPrice());
        m.put("lineTotal", getTotalPrice());
        return m;
    }

    // Hợp nhất theo (productId + size + sorted(toppingIds))
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CartItem that)) return false;
        return Objects.equals(getProductId(), that.getProductId())
                && Objects.equals(normalize(size), normalize(that.size))
                && sameToppingIds(that.toppings);
    }
    private boolean sameToppingIds(java.util.List<SelectedTopping> other) {
        if (toppings == null || toppings.isEmpty()) return other == null || other.isEmpty();
        if (other == null || toppings.size() != other.size()) return false;
        for (int i = 0; i < toppings.size(); i++) {
            String a = toppings.get(i).id == null ? "" : toppings.get(i).id;
            String b = other.get(i).id == null ? "" : other.get(i).id;
            if (!a.equals(b)) return false;
        }
        return true;
    }
    @Override
    public int hashCode() {
        int h = Objects.hash(getProductId(), normalize(size));
        if (toppings != null) for (SelectedTopping t : toppings) h = 31 * h + (t.id == null ? 0 : t.id.hashCode());
        return h;
    }
}
