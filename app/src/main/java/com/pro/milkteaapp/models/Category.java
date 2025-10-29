package com.pro.milkteaapp.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.PropertyName;
import java.io.Serializable;
import java.util.Date;

public class Category implements Serializable {

    private String id;           // Lấy từ document ID
    private String name;         // Tên danh mục
    private String imageUrl;     // URL ảnh (nhập thủ công)
    private boolean active;      // Trạng thái hiển thị
    private String description;  // Mô tả (tùy chọn)

    private Timestamp createdAt; // Thời điểm tạo (Firestore Timestamp)
    private Timestamp updatedAt; // Thời điểm cập nhật (Firestore Timestamp)

    public Category() {} // Bắt buộc cho Firestore

    // ✅ Constructor đầy đủ
    public Category(String id, String name, boolean active, String imageUrl,
                    Timestamp createdAt, Timestamp updatedAt) {
        this.id = id;
        this.name = name;
        this.active = active;
        this.imageUrl = imageUrl;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ===== Getters =====
    public String getId() { return id; }
    public String getName() { return name; }
    public String getImageUrl() { return imageUrl; }
    public boolean isActive() { return active; }
    public String getDescription() { return description; }

    public Timestamp getCreatedAt() { return createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }

    // ===== Convenience: chuyển Timestamp → Date =====
    public Date getCreatedAtDate() {
        return createdAt != null ? createdAt.toDate() : null;
    }

    public Date getUpdatedAtDate() {
        return updatedAt != null ? updatedAt.toDate() : null;
    }

    // ===== Setters =====
    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setActive(boolean active) { this.active = active; }
    public void setDescription(String description) { this.description = description; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    // ===== Alias cho các key cũ trong Firestore =====
    @PropertyName("imgUrl")
    public void setImgUrl(String v) { this.imageUrl = v; }

    @PropertyName("image")
    public void setImage(String v) { this.imageUrl = v; }

    @PropertyName("categoryName")
    public void setCategoryName(String v) { this.name = v; }
}
