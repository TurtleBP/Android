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

import java.util.ArrayList;
import java.util.List;

/**
 * BottomSheet hiển thị trạng thái & quyền lợi Loyalty.
 * - Nếu có ARG_TIER/ARG_POINTS: dùng dữ liệu truyền vào.
 * - Nếu không: tự load từ Firestore theo current user.
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

    // Option tiện: gọi nhanh không truyền gì (sheet sẽ tự load Firestore)
    public static void show(@NonNull androidx.fragment.app.FragmentManager fm) {
        LoyaltyTierBottomSheet s = new LoyaltyTierBottomSheet();
        s.show(fm, TAG);
    }

    private TextView tvCurrentTier, tvCurrentPoints, tvNextInfo;
    private TextView tvBenefitsCurrent, tvBenefitsNext;
    private TextView tvRuleUnrank, tvRuleBronze, tvRuleSilver, tvRuleGold;

    // dữ liệu nhận từ args (nếu có)
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

        tvCurrentTier = v.findViewById(R.id.tvCurrentTier);
        tvCurrentPoints = v.findViewById(R.id.tvCurrentPoints);
        tvNextInfo = v.findViewById(R.id.tvNextInfo);
        tvBenefitsCurrent = v.findViewById(R.id.tvBenefitsCurrent);
        tvBenefitsNext = v.findViewById(R.id.tvBenefitsNext);
        tvRuleUnrank = v.findViewById(R.id.tvRuleUnrank);
        tvRuleBronze = v.findViewById(R.id.tvRuleBronze);
        tvRuleSilver = v.findViewById(R.id.tvRuleSilver);
        tvRuleGold = v.findViewById(R.id.tvRuleGold);

        renderStaticRules();

        if (hasInitial) {
            // Dùng dữ liệu do Activity truyền vào
            applyUI(initialTier, initialPoints);
        } else {
            // Fallback: tự load theo current user
            loadAndRenderUser();
        }
        return v;
    }

    @SuppressLint("SetTextI18n")
    private void renderStaticRules() {
        tvRuleUnrank.setText("0–99: Chưa xếp hạng");
        tvRuleBronze.setText("100–399: Đồng (giảm 5%)");
        tvRuleSilver.setText("400–999: Bạc (giảm 10%)");
        tvRuleGold.setText("≥1000: Vàng (giảm 20%)");
    }

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
        long points = 0;
        String tier = null;
        if (doc != null && doc.exists()) {
            Long p = doc.getLong("loyaltyPoints");
            if (p != null) points = p;
            String t = doc.getString("loyaltyTier");
            if (!TextUtils.isEmpty(t)) tier = t;
        }
        if (TextUtils.isEmpty(tier)) tier = tierForPoints(points);
        applyUI(tier, points);
    }

    @SuppressLint("SetTextI18n")
    private void applyUI(@NonNull String currentTier, long points) {
        tvCurrentTier.setText(currentTier);
        tvCurrentPoints.setText(points + " điểm tích lũy");

        NextInfo next = nextTier(points);
        if (next.targetTier == null) {
            tvNextInfo.setText("Bạn đang ở hạng cao nhất.");
        } else {
            tvNextInfo.setText("Còn " + next.remainingPoints + " điểm để lên " + next.targetTier);
        }

        List<String> curBenefits = benefitsFor(currentTier);
        tvBenefitsCurrent.setText("• " + joinLines(curBenefits));

        List<String> nextBenefits = (next.targetTier == null) ? curBenefits : benefitsFor(next.targetTier);
        tvBenefitsNext.setText("• " + joinLines(nextBenefits));
    }

    /* ====== RULES ====== */

    // Map điểm -> tier: 0-99 Unrank, 100-399 Đồng, 400-999 Bạc, >=1000 Vàng
    private String tierForPoints(long pts) {
        if (pts >= 1000) return "Vàng";
        if (pts >= 400)  return "Bạc";
        if (pts >= 100)  return "Đồng";
        return "Chưa xếp hạng";
    }

    private static class NextInfo {
        @Nullable String targetTier;
        long remainingPoints;
        NextInfo(@Nullable String t, long r){ targetTier=t; remainingPoints=r; }
    }

    private NextInfo nextTier(long pts) {
        if (pts < 100)   return new NextInfo("Đồng", 100 - pts);
        if (pts < 400)   return new NextInfo("Bạc", 400 - pts);
        if (pts < 1000)  return new NextInfo("Vàng", 1000 - pts);
        return new NextInfo(null, 0);
    }

    // Quyền lợi theo tier
    private List<String> benefitsFor(@NonNull String tier) {
        ArrayList<String> list = new ArrayList<>();
        switch (tier) {
            case "Vàng":
                list.add("Giảm 20% trên tổng tiền hàng");
                list.add("Ưu tiên giao hàng và hỗ trợ");
                list.add("Nhận ưu đãi/voucher độc quyền");
                list.add("Điểm đổi quà tiêu chuẩn");
                break;
            case "Bạc":
                list.add("Giảm 10% trên tổng tiền hàng");
                list.add("Nhận voucher định kỳ");
                list.add("Ưu tiên hỗ trợ");
                list.add("Điểm đổi quà tiêu chuẩn");
                break;
            case "Đồng":
                list.add("Giảm 5% trên tổng tiền hàng");
                list.add("Tham gia chương trình sự kiện");
                list.add("Điểm đổi quà tiêu chuẩn");
                break;
            default: // Chưa xếp hạng
                list.add("Tích điểm để thăng hạng");
                list.add("Tiếp cận danh sách đổi quà cơ bản");
                break;
        }
        return list;
    }

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
