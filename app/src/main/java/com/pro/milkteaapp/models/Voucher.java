package com.pro.milkteaapp.models;

import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;

import java.util.List;

public class Voucher {
    private String id;                 // Firestore docId (set thủ công sau khi toObjects)
    private String code;               // TRASUA15K
    private String type;               // ORDER_FIXED | ORDER_PERCENT | SHIPPING_FIXED
    private long amount;               // cho ORDER_FIXED/SHIPPING_FIXED
    private int percent;               // cho ORDER_PERCENT
    @Nullable private Long maxDiscount;// trần cho ORDER_PERCENT (nullable)
    private long minOrder;             // điều kiện đơn tối thiểu
    @Nullable private Timestamp startAt; // auto now khi tạo
    @Nullable private Timestamp endAt;   // đến cuối ngày
    private int perUserLimit;          // mỗi KH được dùng tối đa
    private boolean active;
    @Nullable private List<String> allowedChannels; // ["COD","MOMO","CARD"]
    @Nullable private String description;

    public Voucher() {}

    // ===== getters/setters =====
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCode() { return code; }
    public String getType() { return type; }
    public long getAmount() { return amount; }
    public int getPercent() { return percent; }
    @Nullable public Long getMaxDiscount() { return maxDiscount; }
    public long getMinOrder() { return minOrder; }
    @Nullable public Timestamp getStartAt() { return startAt; }
    @Nullable public Timestamp getEndAt() { return endAt; }
    public int getPerUserLimit() { return perUserLimit; }
    public boolean isActive() { return active; }
    @Nullable public List<String> getAllowedChannels() { return allowedChannels; }
    @Nullable public String getDescription() { return description; }

    public void setCode(String code) { this.code = code; }
    public void setType(String type) { this.type = type; }
    public void setAmount(long amount) { this.amount = amount; }
    public void setPercent(int percent) { this.percent = percent; }
    public void setMaxDiscount(@Nullable Long maxDiscount) { this.maxDiscount = maxDiscount; }
    public void setMinOrder(long minOrder) { this.minOrder = minOrder; }
    public void setStartAt(@Nullable Timestamp startAt) { this.startAt = startAt; }
    public void setEndAt(@Nullable Timestamp endAt) { this.endAt = endAt; }
    public void setPerUserLimit(int perUserLimit) { this.perUserLimit = perUserLimit; }
    public void setActive(boolean active) { this.active = active; }
    public void setAllowedChannels(@Nullable List<String> allowedChannels) { this.allowedChannels = allowedChannels; }
    public void setDescription(@Nullable String description) { this.description = description; }
}
