package com.pro.milkteaapp.models;

import java.util.HashMap;
import java.util.Map;

public class LoyaltyReward {
    private String id;
    private String name;
    private String description;
    private long costPoints;

    public LoyaltyReward() {}

    public LoyaltyReward(String id, String name, String description, long costPoints) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.costPoints = costPoints;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public long getCostPoints() {
        return costPoints;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("description", description);
        m.put("costPoints", costPoints);
        m.put("redeemedAt", com.google.firebase.Timestamp.now());
        return m;
    }
}
