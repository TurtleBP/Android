package com.pro.milkteaapp.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OrderLine implements Serializable {

    private String name;
    private String size;
    private List<String> toppings;
    private int quantity;
    private double unitPrice;
    private double lineTotal;

    public OrderLine() {}

    @NonNull
    @SuppressWarnings("unchecked")
    public static OrderLine fromMap(@Nullable Map<String, Object> m) {
        OrderLine ol = new OrderLine();
        if (m == null) return ol;

        // tên sản phẩm
        Object n = pick(m, "productName", "name", "title");
        ol.name = n == null ? "" : String.valueOf(n);

        // size/biến thể
        Object s = pick(m, "sizeName", "size", "variant");
        ol.size = s == null ? "" : String.valueOf(s);

        // toppings có thể là List<String> hoặc String gộp
        Object tops = m.get("toppings");
        List<String> topList = new ArrayList<>();
        if (tops instanceof List) {
            for (Object t : (List<?>) tops) {
                if (t != null) topList.add(String.valueOf(t));
            }
        } else if (tops instanceof String) {
            String[] parts = String.valueOf(tops).split(",");
            for (String p : parts) {
                String pp = p.trim();
                if (!pp.isEmpty()) topList.add(pp);
            }
        }
        ol.toppings = topList;

        // số lượng
        ol.quantity = toInt(pick(m, "quantity", "qty"));

        // đơn giá
        ol.unitPrice = toDouble(pick(m, "unitPrice", "price"));

        // thành tiền dòng
        Object lt = pick(m, "lineTotal", "totalPrice", "amount");
        ol.lineTotal = lt != null ? toDouble(lt) : (ol.unitPrice * ol.quantity);

        return ol;
    }

    private static Object pick(Map<String, Object> m, String... keys) {
        for (String k : keys) if (m.containsKey(k)) return m.get(k);
        return null;
    }

    private static int toInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception ignored) { return 0; }
    }

    private static double toDouble(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try { return Double.parseDouble(String.valueOf(o)); } catch (Exception ignored) { return 0; }
    }

    // ===== GETTERS =====
    @NonNull public String getName() { return name == null ? "" : name; }
    @NonNull public String getSize() { return size == null ? "" : size; }
    @NonNull public List<String> getToppings() { return toppings == null ? new ArrayList<>() : toppings; }
    public int getQuantity() { return quantity; }
    public double getUnitPrice() { return unitPrice; }
    public double getLineTotal() { return lineTotal; }

    // ===== HIỂN THỊ PHỤ =====
    @NonNull
    public String buildMeta() {
        StringBuilder sb = new StringBuilder();
        if (!getSize().isEmpty()) sb.append(getSize());
        List<String> tops = getToppings();
        if (!tops.isEmpty()) {
            if (sb.length() > 0) sb.append(" • ");
            sb.append("Topping: ");
            for (int i = 0; i < tops.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(tops.get(i));
            }
        }
        return sb.toString();
    }
}
