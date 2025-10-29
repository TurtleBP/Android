package com.pro.milkteaapp.models;

import androidx.annotation.Keep;

import java.io.Serializable;
import java.util.List;

/** Firestore: collection "toppings"
 *  {
 *    name: "Trân châu trắng",
 *    price: 5000,
 *    categories: ["Trà sữa","Sữa tươi"],
 *    active: true
 *  }
 */
@Keep
public class Topping implements Serializable {
    private String id;
    private String name;
    private Long price;
    private List<String> categories;
    private Boolean active;

    public Topping() {}

    public String getId() { return id; }
    public void   setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void   setName(String name) { this.name = name; }

    public long getPrice() { return price == null ? 0L : price; }
    public void setPrice(Long price) { this.price = price; }

    public List<String> getCategories() { return categories; }
    public void   setCategories(List<String> categories) { this.categories = categories; }

    public boolean getActive() { return active != null ? active : true; }
    public void   setActive(Boolean active) { this.active = active; }
}
