package com.pro.milkteaapp.models;

import java.io.Serializable;

public class User implements Serializable {
    private String uid;
    private String email;
    private String fullName;
    private String role;
    private String phone;
    private String address;
    private String avatar;

    // điểm để THĂNG HẠNG
    private String loyaltyTier;
    private long loyaltyPoints;

    // điểm để ĐỔI QUÀ
    private long rewardPoints;

    // tổng tiền đã thanh toán
    private long totalSpent;

    public User() {}

    public User(String uid, String email, String fullName, String role, String phone, String address) {
        this.uid = uid;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.phone = phone;
        this.address = address;
    }

    public User(String uid, String email, String fullName, String role,
                String phone, String address, String avatar) {
        this.uid = uid;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.phone = phone;
        this.address = address;
        this.avatar = avatar;
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getRole() { return role != null ? role : "user"; }
    public void setRole(String role) { this.role = role; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }

    public String getLoyaltyTier() { return loyaltyTier; }
    public void setLoyaltyTier(String loyaltyTier) { this.loyaltyTier = loyaltyTier; }

    public long getLoyaltyPoints() { return loyaltyPoints; }
    public void setLoyaltyPoints(long loyaltyPoints) { this.loyaltyPoints = loyaltyPoints; }

    public long getRewardPoints() { return rewardPoints; }
    public void setRewardPoints(long rewardPoints) { this.rewardPoints = rewardPoints; }

    public long getTotalSpent() { return totalSpent; }
    public void setTotalSpent(long totalSpent) { this.totalSpent = totalSpent; }
}
