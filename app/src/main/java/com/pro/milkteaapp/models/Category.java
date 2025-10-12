package com.pro.milkteaapp.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.PropertyName;

public class Category {
    private String id;
    private String name;
    private boolean active;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public Category() {}

    public Category(String id, String name, boolean active, Timestamp createdAt, Timestamp updatedAt) {
        this.id = id;
        this.name = name;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @PropertyName("id")
    public String getId() { return id; }
    @PropertyName("id")
    public void setId(String id) { this.id = id; }

    @PropertyName("name")
    public String getName() { return name; }
    @PropertyName("name")
    public void setName(String name) { this.name = name; }

    @PropertyName("active")
    public boolean isActive() { return active; }
    @PropertyName("active")
    public void setActive(boolean active) { this.active = active; }

    @PropertyName("createdAt")
    public Timestamp getCreatedAt() { return createdAt; }
    @PropertyName("createdAt")
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    @PropertyName("updatedAt")
    public Timestamp getUpdatedAt() { return updatedAt; }
    @PropertyName("updatedAt")
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
