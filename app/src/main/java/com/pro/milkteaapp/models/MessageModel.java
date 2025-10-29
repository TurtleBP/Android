package com.pro.milkteaapp.models;

import com.google.firebase.Timestamp;

public class MessageModel {
    private String id;
    private String type;       // "order_done" | "order_review" | ...
    private String orderId;
    private String message;
    private Integer rating;    // optional
    private String review;     // optional
    private Timestamp createdAt;
    private Boolean read;

    public MessageModel() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }
    public String getReview() { return review; }
    public void setReview(String review) { this.review = review; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Boolean getRead() { return read; }
    public void setRead(Boolean read) { this.read = read; }
}
