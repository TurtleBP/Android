package com.pro.milkteaapp.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.*;
import com.pro.milkteaapp.SessionManager;
import com.pro.milkteaapp.activity.admin.AdminMainActivity;
import com.pro.milkteaapp.databinding.ActivityLoginBinding;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    // Bật nếu bạn yêu cầu email đã xác thực mới cho vào app
    private static final boolean EMAIL_VERIFY_REQUIRED = false;

    private ActivityLoginBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    // Tránh double navigate
    private boolean isNavigating = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        // Chỉ auto-route nếu đã từng ĐĂNG NHẬP (is_logged_in == true)
        checkExistingLoginAndRoute();

        // Đăng nhập
        binding.loginButton.setOnClickListener(v -> {
            String email = safeTrim(binding.emailEditText.getText() != null
                    ? binding.emailEditText.getText().toString() : "");
            String password = safeTrim(binding.passwordEditText.getText() != null
                    ? binding.passwordEditText.getText().toString() : "");

            if (!validate(email, password)) return;
            loginUser(email, password);
        });

        // Đăng ký
        binding.registerTextView.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class)));

        // Quên mật khẩu
        binding.forgotPasswordTextView.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, ForgotPasswordActivity.class)));
    }

    // ====== CHỈ AUTO-ROUTE KHI IS_LOGGED_IN = TRUE ======
    private void checkExistingLoginAndRoute() {
        SharedPreferences prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);
        boolean isLoggedIn = prefs.getBoolean("is_logged_in", false);
        if (!isLoggedIn) {
            // Chưa từng đăng nhập bằng LoginActivity → Ở lại màn hình đăng nhập
            return;
        }

        FirebaseUser fbUser = auth.getCurrentUser();
        if (fbUser == null) {
            // Cờ local nói đã login nhưng Firebase không có session → về Login để đăng nhập lại
            clearLoggedInFlagIfInconsistent();
            return;
        }

        if (EMAIL_VERIFY_REQUIRED && !fbUser.isEmailVerified()) {
            Toast.makeText(this, "Email của bạn chưa được xác thực.", Toast.LENGTH_SHORT).show();
            clearLoggedInFlagIfInconsistent();
            return;
        }

        // Ưu tiên dùng custom userId (USRxxxxx) từ SessionManager nếu đã có
        String cachedCustomId = null;
        try { cachedCustomId = new SessionManager(getApplicationContext()).getUid(); } catch (Throwable ignored) {}
        if (!TextUtils.isEmpty(cachedCustomId)) {
            // Đã có USRxxxxx → lấy role nhanh rồi route
            fetchRoleAndRouteByCustomId(cachedCustomId);
            return;
        }

        // Chưa có customId → map từ Firebase user → users/{USRxxxxx}
        resolveCustomUserId(fbUser.getUid(), fbUser.getEmail(),
                this::fetchRoleAndRouteByCustomId,
                e -> {
                    // Fallback cực đoan về users/{FirebaseUID} (giữ tương thích nếu DB cũ)
                    Log.w(TAG, "Fallback to legacy users/{FirebaseUID}: " + e.getMessage());
                    fetchRoleAndRouteByLegacyUid(fbUser.getUid());
                });
    }

    // ====== LOGIN CHÍNH THỨC TỪ UI ======
    private void loginUser(String email, String password) {
        setLoading(true);
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser fbUser = authResult.getUser();
                    if (fbUser == null) {
                        setLoading(false);
                        showError("Không tìm thấy UID người dùng.");
                        return;
                    }

                    if (EMAIL_VERIFY_REQUIRED && !fbUser.isEmailVerified()) {
                        setLoading(false);
                        // Nếu bắt buộc xác thực email, không set is_logged_in
                        showError("Vui lòng xác thực email trước khi đăng nhập.");
                        return;
                    }

                    // Map Firebase user -> users/{USRxxxxx}
                    resolveCustomUserId(fbUser.getUid(), fbUser.getEmail(),
                            // Thành công: lưu cache + route
                            this::fetchRoleAndRouteByCustomId,
                            // Thất bại: fallback legacy
                            e -> fetchRoleAndRouteByLegacyUid(fbUser.getUid()));
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showError("Đăng nhập thất bại: " + e.getMessage());
                    Log.e(TAG, "signIn failed", e);
                });
    }

    // ====== MAP FirebaseUser -> users/{USRxxxxx} ======
    /**
     * Tìm document người dùng theo thứ tự:
     *  1) users where authUid == firebaseUid
     *  2) users where email == email
     *  → Lấy documentId (USRxxxxx) và trả về.
     */
    private void resolveCustomUserId(@Nullable String firebaseUid,
                                     @Nullable String email,
                                     @NonNull java.util.function.Consumer<String> onFound,
                                     @NonNull java.util.function.Consumer<Exception> onNotFound) {
        // Query 1: by authUid
        if (!TextUtils.isEmpty(firebaseUid)) {
            db.collection("users")
                    .whereEqualTo("authUid", firebaseUid)
                    .limit(1)
                    .get()
                    .addOnSuccessListener(q1 -> {
                        if (!q1.isEmpty()) {
                            DocumentSnapshot d = q1.getDocuments().get(0);
                            String customId = d.getId();
                            cacheIdentityOnLogin(customId, d.getString("fullName"), d.getString("role"));
                            onFound.accept(customId);
                        } else {
                            // Query 2: by email
                            queryByEmailFallback(email, onFound, onNotFound);
                        }
                    })
                    .addOnFailureListener(e -> queryByEmailFallback(email, onFound, onNotFound));
        } else {
            queryByEmailFallback(email, onFound, onNotFound);
        }
    }

    private void queryByEmailFallback(@Nullable String email,
                                      @NonNull java.util.function.Consumer<String> onFound,
                                      @NonNull java.util.function.Consumer<Exception> onNotFound) {
        if (TextUtils.isEmpty(email)) {
            onNotFound.accept(new IllegalStateException("Missing email for mapping"));
            return;
        }
        db.collection("users")
                .whereEqualTo("email", email)
                .limit(1)
                .get()
                .addOnSuccessListener(q2 -> {
                    if (!q2.isEmpty()) {
                        DocumentSnapshot d = q2.getDocuments().get(0);
                        String customId = d.getId();
                        cacheIdentityOnLogin(customId, d.getString("fullName"), d.getString("role"));
                        onFound.accept(customId);
                    } else {
                        onNotFound.accept(new IllegalStateException("No users by email"));
                    }
                })
                .addOnFailureListener(onNotFound::accept);
    }

    // ====== SAU KHI BIẾT USRxxxxx → LẤY ROLE & ROUTE ======
    private void fetchRoleAndRouteByCustomId(@NonNull String customId) {
        db.collection("users").document(customId).get()
                .addOnSuccessListener(snap -> {
                    String role = (snap != null && snap.exists() && snap.getString("role") != null)
                            ? snap.getString("role") : "user";
                    String displayName = snap != null ? snap.getString("fullName") : null;

                    // Lưu cache đăng nhập + customId
                    cacheIdentityOnLogin(customId, displayName, role);

                    // Điều hướng
                    routeByRole(role);
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "fetchRoleAndRouteByCustomId failed: " + e.getMessage());
                    // Không lấy được role → vẫn route user
                    cacheIdentityOnLogin(customId, null, "user");
                    routeByRole("user");
                });
    }

    // ====== FALLBACK LEGACY: users/{FirebaseUID} ======
    private void fetchRoleAndRouteByLegacyUid(@NonNull String legacyUid) {
        db.collection("users").document(legacyUid).get()
                .addOnSuccessListener(snap -> {
                    String role = (snap != null && snap.exists() && snap.getString("role") != null)
                            ? snap.getString("role") : "user";
                    String displayName = snap != null ? snap.getString("fullName") : null;

                    // Dù legacy, vẫn cache để app tiếp tục hoạt động (user_id = legacyUid)
                    cacheIdentityOnLogin(legacyUid, displayName, role);

                    routeByRole(role);
                })
                .addOnFailureListener(e -> {
                    // Nếu DB hoàn toàn không có gì, vẫn cho vào user
                    Log.w(TAG, "fetchRoleAndRouteByLegacyUid failed: " + e.getMessage());
                    cacheIdentityOnLogin(legacyUid, null, "user");
                    routeByRole("user");
                });
    }

    // ====== ROUTING THEO ROLE ======
    private void routeByRole(String roleRaw) {
        setLoading(false);
        if (isNavigating) return; // tránh double navigate
        isNavigating = true;

        String role = normalizeRole(roleRaw);
        if ("admin".equals(role)) {
            redirectToAdminMain();
        } else {
            redirectToUserMain();
        }
    }

    private void redirectToAdminMain() {
        Toast.makeText(this, "Chào mừng Admin!", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(LoginActivity.this, AdminMainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void redirectToUserMain() {
        Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // ====== LƯU DỮ LIỆU LOCAL ======
    /**
     * Lưu thông tin đăng nhập tối thiểu vào SharedPreferences + SessionManager:
     * - user_id = documentId (ưu tiên USRxxxxx; fallback: Firebase UID)
     * - role, fullName (nếu có)
     * - is_logged_in = true
     */
    private void cacheIdentityOnLogin(@NonNull String userIdDoc,
                                      @Nullable String displayName,
                                      @Nullable String roleRaw) {
        String role = normalizeRole(roleRaw);

        SharedPreferences prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);
        prefs.edit()
                .putString("user_id", safe(userIdDoc))     // ⚠️ Đây sẽ là USRxxxxx nếu map thành công
                .putString("fullName", safe(displayName))  // ProductFragment đang dùng key này
                .putString("role", role)
                .putBoolean("is_logged_in", true)
                .apply();

        // Ghi vào SessionManager nếu có
        try {
            SessionManager sm = new SessionManager(getApplicationContext());
            sm.setUid(userIdDoc); // USRxxxxx / hoặc legacy FirebaseUID
            sm.setRole(role);
            if (!TextUtils.isEmpty(displayName)) sm.setDisplayName(displayName);
        } catch (Throwable ignored) {}
    }

    // ====== TIỆN ÍCH ======
    private void clearLoggedInFlagIfInconsistent() {
        SharedPreferences prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);
        prefs.edit().putBoolean("is_logged_in", false).apply();
    }

    private boolean validate(String email, String password) {
        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            showError("Vui lòng nhập email và mật khẩu");
            return false;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Email không hợp lệ");
            return false;
        }
        if (password.length() < 6) {
            showError("Mật khẩu tối thiểu 6 ký tự");
            return false;
        }
        return true;
    }

    private void setLoading(boolean loading) {
        if (binding == null) return;
        binding.loginButton.setEnabled(!loading);
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private String normalizeRole(@Nullable String role) {
        if (role == null) return "user";
        String r = role.trim().toLowerCase();
        return ("admin".equals(r)) ? "admin" : "user";
    }

    private String safe(String s) { return s == null ? "" : s; }
    private String safeTrim(String s) { return s == null ? "" : s.trim(); }

    private void showError(String message) {
        Toast.makeText(LoginActivity.this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
