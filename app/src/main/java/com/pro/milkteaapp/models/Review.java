package com.pro.milkteaapp.models;

public class Review {
    private String name;
    private String comment;
    private String avatarUrl;
    private double rating;

    public Review() {}

    public Review(String name, String comment, String avatarUrl, double rating) {
        this.name = name;
        this.comment = comment;
        this.avatarUrl = avatarUrl;
        this.rating = rating;
    }

    public String getName() { return name; }
    public String getComment() { return comment; }
    public String getAvatarUrl() { return avatarUrl; }
    public double getRating() { return rating; }
}
