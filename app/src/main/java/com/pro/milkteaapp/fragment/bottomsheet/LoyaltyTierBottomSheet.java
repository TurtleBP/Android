package com.pro.milkteaapp.fragment.bottomsheet;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.utils.LoyaltyPolicy;

import java.util.List;

/**
 * BottomSheet hiển thị trạng thái & quyền lợi Loyalty (RULE RỜI).
 * - Có thể truyền ARG_TIER/ARG_POINTS (newInstance) hoặc tự load Firestore.
 * - Hoàn toàn đồng bộ với LoyaltyPolicy (không free topping).
 */
public class LoyaltyTierBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "LoyaltyTierBottomSheet";
    private static final String ARG_TIER = "arg_tier";
    private static final String ARG_POINTS = "arg_points";

    public static LoyaltyTierBottomSheet newInstance(@NonNull String tier, long points) {
        LoyaltyTierBottomSheet s = new LoyaltyTierBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_TIER, tier);
        args.putLong(ARG_POINTS, points);
        s.setArguments(args);
        return s;
    }

    /** Gọi nhanh không truyền tham số (sheet tự load Firestore) */
    public static void show(@NonNull androidx.fragment.app.FragmentManager fm) {
        LoyaltyTierBottomSheet s = new LoyaltyTierBottomSheet();
        s.show(fm, TAG);
    }

    // View refs
    private TextView tvCurrentTier, tvCurrentPoints, tvNextInfo;
    private TextView tvBenefitsCurrent, tvBenefitsNext;

    // RULE RỜI
    private TextView tvRuleUnrank, tvRuleBronze, tvRuleSilver, tvRuleGold;

    // Args (nếu có)
    private String initialTier = null;
    private long initialPoints = 0L;
    private boolean hasInitial = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            initialTier = args.getString(ARG_TIER);
            initialPoints = args.getLong(ARG_POINTS, 0L);
            hasInitial = !TextUtils.isEmpty(initialTier);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inf.inflate(R.layout.bottomsheet_loyalty_tiers, container, false);

        // Header + benefits
        tvCurrentTier     = v.findViewById(R.id.tvCurrentTier);
        tvCurrentPoints   = v.findViewById(R.id.tvCurrentPoints);
        tvNextInfo        = v.findViewById(R.id.tvNextInfo);
        tvBenefitsCurrent = v.findViewById(R.id.tvBenefitsCurrent);
        tvBenefitsNext    = v.findViewById(R.id.tvBenefitsNext);

        // Rules (rời)
        tvRuleUnrank = v.findViewById(R.id.tvRuleUnrank);
        tvRuleBronze = v.findViewById(R.id.tvRuleBronze);
        tvRuleSilver = v.findViewById(R.id.tvRuleSilver);
        tvRuleGold   = v.findViewById(R.id.tvRuleGold);

        renderStaticRules();

        if (hasInitial) {
            // Ưu tiên points để tính lại tier cho chắc chắn
            String t = LoyaltyPolicy.tierForPoints(initialPoints);
            applyUI(t, initialPoints);
        } else {
            loadAndRenderUser();
        }
        return v;
    }

    /** Render phần "quy tắc điểm -> hạng" theo LoyaltyPolicy constants. */
    @SuppressLint("SetTextI18n")
    private void renderStaticRules() {
        String unrank = "0–" + LoyaltyPolicy.UNRANK_MAX + ": Chưa xếp hạng";
        String bronze = LoyaltyPolicy.BRONZE_MIN + "–" + LoyaltyPolicy.BRONZE_MAX + ": Đồng (giảm 5%)";
        String silver = LoyaltyPolicy.SILVER_MIN + "–" + LoyaltyPolicy.SILVER_MAX + ": Bạc (giảm 10%)";
        String gold   = "≥" + LoyaltyPolicy.GOLD_MIN + ": Vàng (giảm 20%)";

        tvRuleUnrank.setText(unrank);
        tvRuleBronze.setText(bronze);
        tvRuleSilver.setText(silver);
        tvRuleGold.setText(gold);
    }

    /** Nếu không truyền tier/points, tự load theo current user. */
    private void loadAndRenderUser() {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) {
            applyUI("Chưa xếp hạng", 0);
            return;
        }
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(this::bindDoc)
                .addOnFailureListener(e -> applyUI("Chưa xếp hạng", 0));
    }

    private void bindDoc(@Nullable DocumentSnapshot doc) {
        long points = 0L;
        String tier = null;
        if (doc != null && doc.exists()) {
            Long p = doc.getLong("loyaltyPoints");
            if (p != null) points = p;
            String t = doc.getString("loyaltyTier");
            if (!TextUtils.isEmpty(t)) tier = t;
        }
        if (TextUtils.isEmpty(tier)) tier = LoyaltyPolicy.tierForPoints(points);
        applyUI(tier, points);
    }

    /** Áp dữ liệu ra UI theo LoyaltyPolicy. */
    @SuppressLint("SetTextI18n")
    private void applyUI(@NonNull String currentTier, long points) {
        // Header: hạng & điểm tích lũy
        tvCurrentTier.setText(currentTier);
        tvCurrentPoints.setText(points + " điểm tích lũy");

        // Next tier info
        LoyaltyPolicy.NextTierInfo next = LoyaltyPolicy.nextTier(points);
        if (next.targetTier == null) {
            tvNextInfo.setText("Bạn đang ở hạng cao nhất.");
        } else {
            tvNextInfo.setText("Còn " + next.remainingPoints + " điểm để lên " + next.targetTier);
        }

        // Quyền lợi hiện tại
        List<String> curBenefits = LoyaltyPolicy.benefitsForTier(currentTier);
        tvBenefitsCurrent.setText("• " + joinLines(curBenefits));

        // Quyền lợi hạng kế tiếp (nếu đã max -> hiển thị lợi ích Vàng)
        List<String> nextBenefits = (next.targetTier == null)
                ? LoyaltyPolicy.benefitsForTier("Vàng")
                : LoyaltyPolicy.benefitsForTier(next.targetTier);
        tvBenefitsNext.setText("• " + joinLines(nextBenefits));
    }

    /** Join list theo định dạng bullet. */
    private String joinLines(@Nullable List<String> arr) {
        if (arr == null || arr.isEmpty()) return "-";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.size(); i++) {
            sb.append(arr.get(i));
            if (i < arr.size() - 1) sb.append("\n• ");
        }
        return sb.toString();
    }
}
