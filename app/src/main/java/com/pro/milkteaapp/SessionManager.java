package com.pro.milkteaapp;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Quản lý session đơn giản bằng SharedPreferences.
 * Chuẩn mới:
 * - KEY_UID sẽ lưu luôn docId trong Firestore (ví dụ: USR00001)
 * - KEY_NAME lưu fullName để màn hình chào không phải query lại
 * - KEY_AVATAR lưu tên/avatar URL để ProductFragment, Home... dùng lại
 */
public class SessionManager {
    private static final String PREFS = "MyPrefs";

    private static final String KEY_ROLE       = "role";
    private static final String KEY_UID        = "uid";           // 🔴 docID trong Firestore (USR00001)
    private static final String KEY_EMAIL      = "email";
    private static final String KEY_NAME       = "displayName";   // fullName
    private static final String KEY_LOGGED_IN  = "is_logged_in";
    private static final String KEY_AVATAR     = "avatar";

    private final SharedPreferences prefs;

    public SessionManager(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /* ================== ROLE ================== */

    public void setRole(@Nullable String role) {
        prefs.edit()
                .putString(KEY_ROLE, role == null ? "user" : role.trim().toLowerCase())
                .apply();
    }

    @Nullable
    public String getRole() {
        return prefs.getString(KEY_ROLE, null);
    }

    public boolean isAdmin() {
        String r = getRole();
        return r != null && r.equalsIgnoreCase("admin");
    }

    public boolean hasCachedRole() {
        String r = getRole();
        return r != null && !r.trim().isEmpty();
    }

    /* ================== UID / EMAIL / NAME ================== */

    /**
     * Lưu docId (user id) hiện tại – chuẩn mới: USRxxxxx
     */
    public void setUid(@Nullable String uid) {
        prefs.edit().putString(KEY_UID, uid).apply();
    }

    /**
     * Lấy docId (user id) hiện tại – có thể là USRxxxxx hoặc UID firebase cũ
     */
    @Nullable
    public String getUid() {
        return prefs.getString(KEY_UID, null);
    }

    public void setEmail(@Nullable String email) {
        prefs.edit().putString(KEY_EMAIL, email).apply();
    }

    @Nullable
    public String getEmail() {
        return prefs.getString(KEY_EMAIL, null);
    }

    public void setDisplayName(@Nullable String name) {
        prefs.edit().putString(KEY_NAME, name).apply();
    }

    @Nullable
    public String getDisplayName() {
        return prefs.getString(KEY_NAME, null);
    }

    /* ================== AVATAR ================== */

    public void setAvatar(@Nullable String urlOrName) {
        prefs.edit().putString(KEY_AVATAR, urlOrName).apply();
    }

    @Nullable
    public String getAvatar() {
        return prefs.getString(KEY_AVATAR, null);
    }

    /* ================== LOGIN STATE ================== */

    public void setLoggedIn(boolean loggedIn) {
        prefs.edit().putBoolean(KEY_LOGGED_IN, loggedIn).apply();
    }

    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_LOGGED_IN, false);
    }

    /* ================== COMBO SAVE USER ================== */

    /**
     * Dùng khi login xong (hoặc register xong) mà bạn đã biết đủ thông tin.
     * uid: nên là docId trong Firestore (USR00001)
     */
    public void saveUserSession(@NonNull String uid,
                                @Nullable String email,
                                @Nullable String name,
                                @Nullable String role) {
        prefs.edit()
                .putString(KEY_UID, uid)
                .putString(KEY_EMAIL, email)
                .putString(KEY_NAME, name)
                .putString(KEY_ROLE, role == null ? "user" : role.trim().toLowerCase())
                .putBoolean(KEY_LOGGED_IN, true)
                .apply();
    }

    /**
     * Dùng khi vừa load được 1 document users/{id} từ Firestore.
     * Giúp ProductFragment/ProfileFragment gọi 1 dòng là xong.
     */
    public void saveUserFromFirestore(@NonNull String docId,
                                      @Nullable String email,
                                      @Nullable String fullName,
                                      @Nullable String role,
                                      @Nullable String avatar) {
        SharedPreferences.Editor ed = prefs.edit();
        ed.putString(KEY_UID, docId);
        if (email != null)    ed.putString(KEY_EMAIL, email);
        if (fullName != null) ed.putString(KEY_NAME, fullName);
        if (role != null)     ed.putString(KEY_ROLE, role.trim().toLowerCase());
        if (avatar != null)   ed.putString(KEY_AVATAR, avatar);
        ed.putBoolean(KEY_LOGGED_IN, true);
        ed.apply();
    }

    /* ================== CLEAR ================== */

    public void clear() {
        prefs.edit().clear().apply();
    }

    /* ================== DEBUG ================== */

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
