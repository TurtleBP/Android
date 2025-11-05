package com.pro.milkteaapp.activity;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.pro.milkteaapp.databinding.ActivityLoyaltyStatusBinding;
import com.pro.milkteaapp.fragment.bottomsheet.LoyaltyTierBottomSheet;
import com.pro.milkteaapp.utils.LoyaltyPolicy;
import com.pro.milkteaapp.utils.StatusBarUtil;

import java.util.List;

public class LoyaltyStatusActivity extends AppCompatActivity {

    private ActivityLoyaltyStatusBinding b;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityLoyaltyStatusBinding.inflate(getLayoutInflater());
        StatusBarUtil.setupDefaultStatusBar(this);
        setContentView(b.getRoot());

        setSupportActionBar(b.toolbar);
        b.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        // Nút xem “Quy tắc & quyền lợi các hạng”
        b.btnViewAllTiers.setOnClickListener(v -> {
            LoyaltyTierBottomSheet sheet = new LoyaltyTierBottomSheet();
            sheet.show(getSupportFragmentManager(), "LoyaltyTierBottomSheet");
        });

        loadUser();
    }

    @SuppressLint("SetTextI18n")
    private void loadUser() {
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "Bạn chưa đăng nhập", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        b.progress.setVisibility(View.VISIBLE);

        db.collection("users").document(auth.getCurrentUser().getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    b.progress.setVisibility(View.GONE);
                    if (!doc.exists()) return;

                    long points = doc.getLong("loyaltyPoints") == null ? 0L : doc.getLong("loyaltyPoints");
                    String tier = doc.getString("loyaltyTier");
                    if (tier == null || tier.trim().isEmpty()) {
                        tier = LoyaltyPolicy.tierForPoints(points);
                    }

                    // Hiển thị hạng & điểm hiện tại
                    b.tvCurrentTier.setText(tier);
                    b.tvCurrentPoints.setText(String.valueOf(points));

                    // Hiển thị quyền lợi hạng hiện tại
                    List<String> benefits = LoyaltyPolicy.benefitsForTier(tier);
                    b.tvBenefits.setText("• " + android.text.TextUtils.join("\n• ", benefits));

                    // Tính & hiển thị mục tiêu tiếp theo
                    LoyaltyPolicy.NextTierInfo nxt = LoyaltyPolicy.nextTier(points);
                    if (nxt.targetTier == null) {
                        b.tvNextTier.setText("Bạn đang ở hạng cao nhất.");
                        b.tvNextBenefits.setText(
                                "• " + android.text.TextUtils.join("\n• ",
                                        LoyaltyPolicy.benefitsForTier("Vàng"))
                        );
                    } else {
                        b.tvNextTier.setText("Còn " + nxt.remainingPoints + " điểm để lên " + nxt.targetTier);
                        b.tvNextBenefits.setText("• " + android.text.TextUtils.join("\n• ", nxt.nextTierBenefits));
                    }
                })
                .addOnFailureListener(e -> {
                    b.progress.setVisibility(View.GONE);
                    Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
