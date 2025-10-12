package com.pro.milkteaapp.models;

import java.io.Serializable;

public class User implements Serializable {
    private String uid;
    private String email;
    private String fullName;
    private String role;
    private String phone;
    private String address;

    // Constructor không tham số
    public User() {}

    // Constructor có tham số
    public User(String uid, String email, String fullName, String role, String phone, String address) {
        this.uid = uid;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.phone = phone;
        this.address = address;
    }

    // Getters và Setters
    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getRole() {
        return role != null ? role : "user";
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}