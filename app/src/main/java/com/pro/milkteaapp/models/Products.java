package com.pro.milkteaapp.models;

import java.io.Serializable;

public class Products implements Serializable {
    private String id;
    private String name;
    private double price;
    private String category;
    private String imageUrl;
    private String description;
    private String status;
    private String searchableName;
    private String createdByUid;

    // Thêm các trường mới cho quản lý admin
    private int stock = 0;
    private int soldCount = 0;
    private Object createdAt;
    private Object updatedAt;

    // Constructor không tham số cần cho Firestore
    public Products() {}

    public Products(String id, String name, double price, String category,
                    String imageUrl, String description, String status,
                    String searchableName, String createdByUid) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.category = category;
        this.imageUrl = imageUrl;
        this.description = description;
        this.status = status;
        this.searchableName = searchableName;
        this.createdByUid = createdByUid;
    }

    // Constructor đầy đủ với các trường mới
    public Products(String id, String name, double price, String category,
                    String imageUrl, String description, String status,
                    String searchableName, String createdByUid,
                    int stock, int soldCount) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.category = category;
        this.imageUrl = imageUrl;
        this.description = description;
        this.status = status;
        this.searchableName = searchableName;
        this.createdByUid = createdByUid;
        this.stock = stock;
        this.soldCount = soldCount;
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public double getPrice() { return price; }
    public String getCategory() { return category; }
    public String getImageUrl() { return imageUrl; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public String getSearchableName() { return searchableName; }
    public String getCreatedByUid() { return createdByUid; }
    public int getStock() { return stock; }
    public int getSoldCount() { return soldCount; }
    public Object getCreatedAt() { return createdAt; }
    public Object getUpdatedAt() { return updatedAt; }

    // Setters
    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setPrice(double price) { this.price = price; }
    public void setCategory(String category) { this.category = category; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setDescription(String description) { this.description = description; }
    public void setStatus(String status) { this.status = status; }
    public void setSearchableName(String searchableName) { this.searchableName = searchableName; }
    public void setCreatedByUid(String createdByUid) { this.createdByUid = createdByUid; }
    public void setStock(int stock) { this.stock = stock; }
    public void setSoldCount(int soldCount) { this.soldCount = soldCount; }
    public void setCreatedAt(Object createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Object updatedAt) { this.updatedAt = updatedAt; }
}