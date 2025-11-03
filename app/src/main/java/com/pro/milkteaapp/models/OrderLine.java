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
    private List<Topping> toppings;  // sửa kiểu toppings sang List<Topping>

    private String sugar;
    private String ice;
    private String note;

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

        ol.sugar = asString(pick(m, "sugar"));
        ol.ice = asString(pick(m, "ice"));
        ol.note = asString(pick(m, "note"));

        // toppings: chuyển từ List<Map> thành List<Topping>
        Object tops = m.get("toppings");
        List<Topping> topList = new ArrayList<>();
        if (tops instanceof List) {
            for (Object t : (List<?>) tops) {
                if (t instanceof Map) {
                    // Ép kiểu an toàn từ Map sang Topping
                    Topping topping = mapToTopping((Map<String, Object>) t);
                    topList.add(topping);
                }
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

    private static String asString(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    // Chuyển map thành đối tượng Topping
    private static Topping mapToTopping(Map<String, Object> map) {
        Topping t = new Topping();
        Object id = map.get("id");
        Object name = map.get("name");
        Object price = map.get("price");
        t.setId(id == null ? "" : String.valueOf(id));
        t.setName(name == null ? "" : String.valueOf(name));
        if (price instanceof Number) {
            t.setPrice(((Number) price).longValue());
        } else {
            try {
                t.setPrice(Long.parseLong(String.valueOf(price)));
            } catch (Exception e) {
                t.setPrice(0L);
            }
        }
        return t;
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
    @NonNull public List<Topping> getToppings() { return toppings == null ? new ArrayList<>() : toppings; }

    @NonNull public String getSugar() { return sugar == null ? "" : sugar; }
    @NonNull public String getIce() { return ice == null ? "" : ice; }
    @NonNull public String getNote() { return note == null ? "" : note; }

    public int getQuantity() { return quantity; }
    public double getUnitPrice() { return unitPrice; }
    public double getLineTotal() { return lineTotal; }

    // ===== HIỂN THỊ PHỤ =====
    @NonNull
    public String buildMeta() {
        StringBuilder sb = new StringBuilder();
        boolean added = false;

//        // Nếu có size thì xuống dòng ghi "Size: <size>"
//        if (!getSize().isEmpty()) {
//            sb.append("Size: ").append(getSize()).append("\n");
//        }
//
//        // Nếu có topping thì xuống dòng ghi "Topping:" rồi list từng topping xuống dòng, thụt đầu dòng 5-6 spaces
//        List<Topping> tops = getToppings();
//        if (!tops.isEmpty()) {
//            sb.append("Topping: ");
//            for (Topping t : tops) {
//                sb.append(" - ").append(t.getName()).append("");
//            }
//        }

        // Size
        if (!getSize().isEmpty()) {
            sb.append("Size: ").append(getSize());
            added = true;
        }

        // Sugar
        if (!getSugar().isEmpty()) {
            if (added) sb.append(" • ");
            sb.append(getSugar());
            added = true;
        }

        // Ice
        if (!getIce().isEmpty()) {
            if (added) sb.append(" • ");
            sb.append(getIce());
            added = true;
        }

        // Topping
        List<Topping> tops = getToppings();
        if (!tops.isEmpty()) {
            if (added) sb.append(" • ");
            sb.append("Topping: ");
            for (int i = 0; i < tops.size(); i++) {
                sb.append(tops.get(i).getName());
                if (i < tops.size() - 1) sb.append(", ");
            }
        }

        return sb.toString().trim();  // trim để tránh xuống dòng thừa cuối cùng
    }
}
