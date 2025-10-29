package com.pro.milkteaapp.models;

import com.google.firebase.firestore.PropertyName;
// Nếu muốn Firestore tự set timestamp khi ghi, bật 2 dòng dưới:
// import com.google.firebase.firestore.ServerTimestamp;

import java.io.Serializable;
import java.util.Date;

public class Products implements Serializable {
    private String id;
    private String name;
    private Double price;          // nullable
    private String category;
    private String imageUrl;
    private String description;
    private String status;
    private String searchableName;
    private String createdByUid;

    private Integer stock = 0;     // nullable an toàn
    private Integer soldCount = 0;

    // @ServerTimestamp
    private Date createdAt;
    // @ServerTimestamp
    private Date updatedAt;

    public Products() {}

    public Products(String id, String name, Double price, String category,
                    String imageUrl, String description, String status,
                    String searchableName, String createdByUid,
                    Integer stock, Integer soldCount) {
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
    public Double getPrice() { return price; }
    public String getCategory() { return category; }
    public String getImageUrl() { return imageUrl; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public String getSearchableName() { return searchableName; }
    public String getCreatedByUid() { return createdByUid; }
    public Integer getStock() { return stock; }
    public Integer getSoldCount() { return soldCount; }
    public Date getCreatedAt() { return createdAt; }
    public Date getUpdatedAt() { return updatedAt; }

    // Setters
    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setPrice(Double price) { this.price = price; }
    public void setCategory(String category) { this.category = category; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setDescription(String description) { this.description = description; }
    public void setStatus(String status) { this.status = status; }
    public void setSearchableName(String searchableName) { this.searchableName = searchableName; }
    public void setCreatedByUid(String createdByUid) { this.createdByUid = createdByUid; }
    public void setStock(Integer stock) { this.stock = stock; }
    public void setSoldCount(Integer soldCount) { this.soldCount = soldCount; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }

    // ===== Alias cho dữ liệu cũ (nếu tồn tại key khác) =====
    @PropertyName("title")
    public void setTitle(String v) { this.name = v; }

    @PropertyName("imgUrl")
    public void setImgUrl(String v) { this.imageUrl = v; }

    @PropertyName("image")
    public void setImage(String v) { this.imageUrl = v; }

    @PropertyName("categoryName")
    public void setCategoryName(String v) { this.category = v; }
}
