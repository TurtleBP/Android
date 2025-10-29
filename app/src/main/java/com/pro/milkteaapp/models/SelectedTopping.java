package com.pro.milkteaapp.models;

import androidx.annotation.NonNull;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/** Topping đã chọn (đưa vào giỏ/lưu order) */
public class SelectedTopping implements Serializable {
    public final String id;
    public final String name;
    public final long   price;

    public SelectedTopping(String id, String name, long price) {
        this.id = id;
        this.name = name;
        this.price = price;
    }

    @NonNull
    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("price", price);
        return m;
    }
}
