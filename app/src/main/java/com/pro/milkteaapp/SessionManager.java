package com.pro.milkteaapp;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class SessionManager {
    private static final String PREFS = "MyPrefs";

    private static final String KEY_ROLE = "role";
    private static final String KEY_UID  = "uid";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_NAME  = "displayName";
    private static final String KEY_LOGGED_IN = "is_logged_in";
    private static final String KEY_AVATAR = "avatar"; // NEW

    private final SharedPreferences prefs;

    public SessionManager(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ===== ROLE =====
    public void setRole(@Nullable String role) {
        prefs.edit().putString(KEY_ROLE, role == null ? "" : role.trim().toLowerCase()).apply();
    }

    @Nullable
    public String getRole() { return prefs.getString(KEY_ROLE, null); }

    public boolean isAdmin() {
        String r = getRole();
        return r != null && r.equalsIgnoreCase("admin");
    }

    public boolean hasCachedRole() {
        String r = getRole();
        return r != null && !r.trim().isEmpty();
    }

    // ===== UID / EMAIL / NAME =====
    public void setUid(String uid) { prefs.edit().putString(KEY_UID, uid).apply(); }
    @Nullable public String getUid() { return prefs.getString(KEY_UID, null); }

    public void setEmail(String email) { prefs.edit().putString(KEY_EMAIL, email).apply(); }
    @Nullable public String getEmail() { return prefs.getString(KEY_EMAIL, null); }

    public void setDisplayName(String name) { prefs.edit().putString(KEY_NAME, name).apply(); }
    @Nullable public String getDisplayName() { return prefs.getString(KEY_NAME, null); }

    // ===== AVATAR =====
    public void setAvatar(@Nullable String url) { prefs.edit().putString(KEY_AVATAR, url).apply(); }
    @Nullable public String getAvatar() { return prefs.getString(KEY_AVATAR, null); }

    // ===== LOGIN STATE =====
    public void setLoggedIn(boolean loggedIn) { prefs.edit().putBoolean(KEY_LOGGED_IN, loggedIn).apply(); }
    public boolean isLoggedIn() { return prefs.getBoolean(KEY_LOGGED_IN, false); }

    // ===== COMBO SAVE USER =====
    public void saveUserSession(String uid, String email, String name, String role) {
        prefs.edit()
                .putString(KEY_UID, uid)
                .putString(KEY_EMAIL, email)
                .putString(KEY_NAME, name)
                .putString(KEY_ROLE, role == null ? "user" : role.trim().toLowerCase())
                .putBoolean(KEY_LOGGED_IN, true)
                .apply();
    }

    // ===== CLEAR SESSION =====
    public void clear() { prefs.edit().clear().apply(); }

    // ===== SUMMARY =====
    @NonNull
    @Override
    public String toString() {
        return "SessionManager{" +
                "uid='" + getUid() + '\'' +
                ", email='" + getEmail() + '\'' +
                ", name='" + getDisplayName() + '\'' +
                ", role='" + getRole() + '\'' +
                ", avatar='" + getAvatar() + '\'' +
                ", loggedIn=" + isLoggedIn() +
                '}';
    }
}
