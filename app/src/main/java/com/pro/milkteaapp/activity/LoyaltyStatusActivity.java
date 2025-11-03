    package com.pro.milkteaapp.activity;

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

    import java.util.List;

    public class LoyaltyStatusActivity extends AppCompatActivity {

        private ActivityLoyaltyStatusBinding b;
        private FirebaseAuth auth;
        private FirebaseFirestore db;

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            b = ActivityLoyaltyStatusBinding.inflate(getLayoutInflater());
            setContentView(b.getRoot());

            setSupportActionBar(b.toolbar);
            b.toolbar.setNavigationOnClickListener(v -> onBackPressed());

            auth = FirebaseAuth.getInstance();
            db   = FirebaseFirestore.getInstance();

            // Nút mở BottomSheet “Bảng quyền lợi”
            b.btnViewRules.setOnClickListener(v -> openTierSheet());

            loadUser();
        }

        private void openTierSheet() {
            // Truyền current points & tier để sheet hiển thị “còn thiếu bao nhiêu điểm”
            String tier = b.tvTier.getText() != null ? b.tvTier.getText().toString() : "Chưa xếp hạng";
            long points = 0L;
            try {
                points = Long.parseLong(String.valueOf(b.tvLoyaltyPoints.getText()));
            } catch (Exception ignore) {}

            LoyaltyTierBottomSheet sheet = LoyaltyTierBottomSheet.newInstance(tier, points);
            sheet.show(getSupportFragmentManager(), "LoyaltyTierSheet");
        }

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
                        if (tier == null || tier.isEmpty()) tier = LoyaltyPolicy.tierForPoints(points);

                        long totalSpent = doc.getLong("totalSpent") == null ? 0L : doc.getLong("totalSpent");

                        b.tvTier.setText(tier);
                        b.tvLoyaltyPoints.setText(String.valueOf(points));
                        b.tvTotalSpent.setText(String.format("%,d đ", totalSpent));

                        // Quyền lợi hiện tại (hiển thị ngay trên trang)
                        List<String> benefits = LoyaltyPolicy.benefitsForTier(tier);
                        b.tvBenefits.setText("• " + android.text.TextUtils.join("\n• ", benefits));
                    })
                    .addOnFailureListener(e -> {
                        b.progress.setVisibility(View.GONE);
                        Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        }
    }
