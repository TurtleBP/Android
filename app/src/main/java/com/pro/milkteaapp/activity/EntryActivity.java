package com.pro.milkteaapp.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.activity.ComponentActivity;
import androidx.core.splashscreen.SplashScreen;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.pro.milkteaapp.SessionManager;
import com.pro.milkteaapp.activity.admin.AdminMainActivity;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * EntryActivity không có layout.
 * Điều hướng theo trạng thái đăng nhập & role:
 *  - Chưa đăng nhập (hoặc chưa set is_logged_in) -> LoginActivity
 *  - Đã đăng nhập:
 *      + Có role cache -> route theo role
 *      + Không có cache -> fetch Firestore lấy role (fallback nhanh sang user)
 * Yêu cầu:
 *  - Thêm dependency "androidx.core:core-splashscreen:<latest>"
 *  - AndroidManifest: đặt theme LAUNCHER = Theme.MilkteaApp.Starting cho EntryActivity
 */
public class EntryActivity extends ComponentActivity {

    // Thời gian chờ tối đa khi chưa có cache role (đảm bảo splash mượt)
    private static final long MAX_WAIT_MS = 250L;

    private boolean navigated = false;
    private boolean ready = false;

    private void goOnce(Intent i) {
        if (navigated) return;
        navigated = true;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Hiển thị system splash đến khi "ready"
        SplashScreen.installSplashScreen(this).setKeepOnScreenCondition(() -> !ready);
        super.onCreate(savedInstanceState);

        FirebaseAuth auth = FirebaseAuth.getInstance();
        SessionManager sm  = new SessionManager(this);

        // ===== 1) CHƯA ĐĂNG NHẬP HỢP LỆ -> VỀ LOGIN =====
        // Chúng ta siết chặt điều kiện: phải có cả Firebase user và flag is_logged_in do app set sau khi LOGIN thành công.
        boolean hasFirebaseUser = (auth.getCurrentUser() != null);
        boolean isLoggedInFlag  = sm.isLoggedIn(); // do LoginActivity set khi login OK

        if (!hasFirebaseUser || !isLoggedInFlag) {
            // Trường hợp này bao gồm: vừa REGISTER xong nhưng đã signOut; hoặc chưa từng đăng nhập
            ready = true;
            goOnce(new Intent(this, LoginActivity.class));
            return;
        }

        // ===== 2) ĐÃ ĐĂNG NHẬP -> ĐI THEO ROLE =====

        // 2.1 Ưu tiên role đã cache trong SessionManager
        String cachedRole = sm.getRole();
        if (cachedRole != null && !cachedRole.trim().isEmpty()) {
            ready = true;
            Intent i = new Intent(
                    this,
                    sm.isAdmin() ? AdminMainActivity.class : MainActivity.class
            );
            goOnce(i);
            return;
        }

        // 2.2 Chưa có cache -> fetch Firestore, kèm fallback cực nhanh vào Main (user)
        Handler h = new Handler(Looper.getMainLooper());
        AtomicBoolean decided = new AtomicBoolean(false);

        Runnable fallback = () -> {
            if (decided.compareAndSet(false, true)) {
                ready = true;
                // Không có role kịp thời -> mặc định user
                goOnce(new Intent(this, MainActivity.class));
            }
        };
        h.postDelayed(fallback, MAX_WAIT_MS);

        String uid = auth.getCurrentUser().getUid();
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(snap -> {
                    String role = "user";
                    if (snap != null && snap.exists()) {
                        String r = snap.getString("role");
                        if (r != null && !r.trim().isEmpty()) role = r.trim();
                    }
                    sm.setUid(uid);
                    sm.setRole(role);

                    if (decided.compareAndSet(false, true)) {
                        ready = true;
                        h.removeCallbacks(fallback);
                        goOnce(new Intent(
                                this,
                                "admin".equalsIgnoreCase(role) ? AdminMainActivity.class : MainActivity.class
                        ));
                    }
                })
                .addOnFailureListener(e -> {
                    // Lỗi mạng/Firestore -> mặc định role=user
                    sm.setUid(uid);
                    sm.setRole("user");
                    if (decided.compareAndSet(false, true)) {
                        ready = true;
                        h.removeCallbacks(fallback);
                        goOnce(new Intent(this, MainActivity.class));
                    }
                });
    }
}
