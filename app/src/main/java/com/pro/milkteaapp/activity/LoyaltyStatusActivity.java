package com.pro.milkteaapp.activity;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;
import com.pro.milkteaapp.SessionManager;
import com.pro.milkteaapp.databinding.ActivityLoyaltyStatusBinding;
import com.pro.milkteaapp.fragment.bottomsheet.LoyaltyTierBottomSheet;
import com.pro.milkteaapp.utils.LoyaltyPolicy;
import com.pro.milkteaapp.utils.StatusBarUtil;

import java.util.List;

/**
 * LoyaltyStatusActivity – đồng bộ với LoyaltyTierBottomSheet
 * Chuẩn hoá USRxxxxx và giảm 15% cho hạng Vàng.
 */
public class LoyaltyStatusActivity extends AppCompatActivity {

    private ActivityLoyaltyStatusBinding b;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityLoyaltyStatusBinding.inflate(getLayoutInflater());
        StatusBarUtil.setupDefaultStatusBar(this);
        setContentView(b.getRoot());

        setSupportActionBar(b.toolbar);
        b.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        db = FirebaseFirestore.getInstance();

        b.btnViewAllTiers.setOnClickListener(v -> {
            LoyaltyTierBottomSheet sheet = new LoyaltyTierBottomSheet();
            sheet.show(getSupportFragmentManager(), "LoyaltyTierBottomSheet");
        });

        loadUser();
    }

    @SuppressLint("SetTextI18n")
    private void loadUser() {
        String docId = null;
        try {
            docId = new SessionManager(getApplicationContext()).getUid();
        } catch (Throwable ignored) {}

        if (TextUtils.isEmpty(docId)) {
            Toast.makeText(this, "Bạn chưa đăng nhập", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        b.progress.setVisibility(View.VISIBLE);

        db.collection("users").document(docId)
                .get()
                .addOnSuccessListener(doc -> {
                    b.progress.setVisibility(View.GONE);
                    if (doc == null || !doc.exists()) return;

                    Long pt = doc.getLong("loyaltyPoints");
                    long points = pt == null ? 0L : pt;
                    String tier = doc.getString("loyaltyTier");
                    if (tier == null || tier.trim().isEmpty()) {
                        tier = LoyaltyPolicy.tierForPoints(points);
                    }

                    b.tvCurrentTier.setText(tier);
                    b.tvCurrentPoints.setText(String.valueOf(points));

                    List<String> benefits = LoyaltyPolicy.benefitsForTier(tier);
                    b.tvBenefits.setText("• " + android.text.TextUtils.join("\n• ", benefits));

                    LoyaltyPolicy.NextTierInfo nxt = LoyaltyPolicy.nextTier(points);
                    if (nxt.targetTier == null) {
                        b.tvNextTier.setText("Bạn đang ở hạng cao nhất.");
                        b.tvNextBenefits.setText("• " + android.text.TextUtils.join("\n• ",
                                LoyaltyPolicy.benefitsForTier(tier)));
                    } else {
                        b.tvNextTier.setText("Còn " + nxt.remainingPoints + " điểm để lên " + nxt.targetTier);
                        List<String> nextBenefits = (nxt.nextTierBenefits != null && !nxt.nextTierBenefits.isEmpty())
                                ? nxt.nextTierBenefits
                                : LoyaltyPolicy.benefitsForTier(nxt.targetTier);
                        b.tvNextBenefits.setText("• " + android.text.TextUtils.join("\n• ", nextBenefits));
                    }
                })
                .addOnFailureListener(e -> {
                    b.progress.setVisibility(View.GONE);
                    Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
