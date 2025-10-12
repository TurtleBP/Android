package com.pro.milkteaapp.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.pro.milkteaapp.activity.admin.AdminMainActivity;
import com.pro.milkteaapp.databinding.ActivityLoginBinding;
import com.pro.milkteaapp.models.User;

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

        // Có thể còn session Firebase hoặc chưa, nhưng is_logged_in mới là "nguồn sự thật" cho auto-route
        if (auth.getCurrentUser() == null) {
            // Cờ local nói đã login nhưng Firebase không có session → về Login để đăng nhập lại
            clearLoggedInFlagIfInconsistent();
            return;
        }

        if (EMAIL_VERIFY_REQUIRED && !auth.getCurrentUser().isEmailVerified()) {
            // Yêu cầu xác thực email mà chưa verify → không auto-route
            Toast.makeText(this, "Email của bạn chưa được xác thực.", Toast.LENGTH_SHORT).show();
            clearLoggedInFlagIfInconsistent();
            return;
        }

        // Đọc role cache để điều hướng nhanh
        String cachedRole = prefs.getString("role", null);
        if (!TextUtils.isEmpty(cachedRole)) {
            routeByRole(cachedRole);
        } else {
            // Không có role cache (hiếm gặp vì đã từng login), có thể fetch Firestore rồi route
            String uid = auth.getCurrentUser().getUid();
            fetchUserRoleAndRoute(uid);
        }
    }

    // ====== LOGIN CHÍNH THỨC TỪ UI ======
    private void loginUser(String email, String password) {
        setLoading(true);
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    if (authResult.getUser() == null) {
                        setLoading(false);
                        showError("Không tìm thấy UID người dùng.");
                        return;
                    }

                    if (EMAIL_VERIFY_REQUIRED && !authResult.getUser().isEmailVerified()) {
                        setLoading(false);
                        // Nếu bắt buộc xác thực email, không set is_logged_in
                        showError("Vui lòng xác thực email trước khi đăng nhập.");
                        return;
                    }

                    String uid = authResult.getUser().getUid();
                    // Lấy role rồi set is_logged_in = true + route
                    fetchUserRoleAndRoute(uid);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showError("Đăng nhập thất bại: " + e.getMessage());
                    Log.e(TAG, "signIn failed", e);
                });
    }

    // Lấy document users/{uid} → lưu SharedPreferences (is_logged_in=true) → điều hướng
    private void fetchUserRoleAndRoute(String uid) {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(snap -> {
                    String role = (snap != null && snap.exists() && snap.getString("role") != null)
                            ? snap.getString("role") : "user";

                    String displayName = snap != null ? snap.getString("fullName") : null;
                    if (TextUtils.isEmpty(displayName)) {
                        displayName = auth.getCurrentUser() != null ? auth.getCurrentUser().getEmail() : "Người dùng";
                    }

                    SharedPreferences prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);
                    prefs.edit()
                            .putString("user_id", uid)
                            .putString("fullName", displayName)  // ProductFragment đang dùng key này
                            .putString("role", role)
                            .putBoolean("is_logged_in", true)    // <-- CHỈ set khi ĐĂNG NHẬP thành công
                            .apply();

                    // Điều hướng
                    routeByRole(role);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    // Không lấy được role → vẫn có thể cho vào user main (tùy chính sách)
                    String fallbackName = auth.getCurrentUser() != null ? auth.getCurrentUser().getEmail() : "Người dùng";
                    SharedPreferences prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);
                    prefs.edit()
                            .putString("username", fallbackName)
                            .putString("role", "user")
                            .putBoolean("is_logged_in", true)
                            .apply();

                    routeByRole("user");
                });
    }

    // ====== ROUTING THEO ROLE ======
    private void routeByRole(String roleRaw) {
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

    // ====== LƯU DỮ LIỆU LOCAL (nếu bạn còn dùng) ======
    private void saveUserInfoToSharedPreferences(User user) {
        SharedPreferences prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);
        prefs.edit()
                .putString("user_id", safe(user.getUid()))
                .putString("username", safe(user.getEmail()))
                .putString("full_name", safe(user.getFullName()))
                .putString("email", safe(user.getEmail()))
                .putString("role", normalizeRole(user.getRole()))
                .putBoolean("is_logged_in", true)
                .apply();

        Log.d(TAG, "Saved user to prefs: " + user.getEmail() + " | role=" + user.getRole());
    }

    private void saveMinimalUser(String uid, String email, String fullName, String role) {
        SharedPreferences prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);
        prefs.edit()
                .putString("user_id", safe(uid))
                .putString("username", safe(email))
                .putString("full_name", safe(fullName))
                .putString("email", safe(email))
                .putString("role", normalizeRole(role))
                .putBoolean("is_logged_in", true)
                .apply();
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

    private String normalizeRole(String role) {
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
