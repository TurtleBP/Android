package com.pro.milkteaapp.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class LoyaltyPolicy {

    private LoyaltyPolicy(){}

    /* ===== Ngưỡng điểm thăng hạng (KHÔNG bị trừ) ===== */
    public static final long UNRANK_MAX = 99;
    public static final long BRONZE_MIN = 100;
    public static final long BRONZE_MAX = 399;
    public static final long SILVER_MIN = 400;
    public static final long SILVER_MAX = 999;
    public static final long GOLD_MIN   = 1000;

    /* ===== Chuẩn hoá tên hạng để xử lý không phân biệt hoa/thường ===== */
    private static String normalizeTier(String tier) {
        if (tier == null) return "Chưa xếp hạng";
        tier = tier.trim();
        if (tier.equalsIgnoreCase("Vàng")) return "Vàng";
        if (tier.equalsIgnoreCase("Bạc"))  return "Bạc";
        if (tier.equalsIgnoreCase("Đồng")) return "Đồng";
        return "Chưa xếp hạng";
    }

    /* ===== Suy ra hạng từ tổng điểm thăng hạng ===== */
    public static String tierForPoints(long points) {
        if (points >= GOLD_MIN)   return "Vàng";
        if (points >= SILVER_MIN) return "Bạc";
        if (points >= BRONZE_MIN) return "Đồng";
        return "Chưa xếp hạng";
    }

    /* ===== % giảm theo hạng (áp dụng trên subtotal) ===== */
    public static double discountPercent(String tier) {
        return switch (normalizeTier(tier)) {
            case "Vàng" -> 0.15; // 15%
            case "Bạc" -> 0.10; // 10%
            case "Đồng" -> 0.05; // 5%
            default -> 0.0;  // Chưa xếp hạng
        };
    }

    /* ===== Quyền lợi theo hạng (dùng cho BottomSheet / màn trạng thái) ===== */
    public static List<String> benefitsForTier(String tier) {
        String t = normalizeTier(tier);
        return switch (t) {
            case "Vàng" -> Arrays.asList(
                    "Giảm 15% trên tổng tiền hàng",
                    "Ưu tiên hỗ trợ & khuyến mãi",
                    "Mốc đổi quà ưu đãi hơn"
            );
            case "Bạc" -> Arrays.asList(
                    "Giảm 10% trên tổng tiền hàng",
                    "Nhận voucher định kỳ",
                    "Đổi quà theo bảng tiêu chuẩn"
            );
            case "Đồng" -> Arrays.asList(
                    "Giảm 5% trên tổng tiền hàng",
                    "Tích điểm để lên hạng",
                    "Đổi quà theo bảng tiêu chuẩn"
            );
            default -> // Chưa xếp hạng
                    Arrays.asList(
                            "Tích điểm 10.000đ = 1 điểm",
                            "Đủ 100 điểm sẽ lên hạng Đồng"
                    );
        };
    }

    /* ===== Thông tin hạng kế tiếp cho UI ===== */
    public static class NextTierInfo {
        public final String targetTier;       // null nếu đã cao nhất
        public final long targetPoints;       // mốc điểm cần đạt của hạng kế tiếp
        public final long remainingPoints;    // còn thiếu bao nhiêu điểm
        public final List<String> nextTierBenefits; // quyền lợi của hạng kế tiếp

        public NextTierInfo(String targetTier, long targetPoints, long remainingPoints, List<String> nextTierBenefits) {
            this.targetTier = targetTier;
            this.targetPoints = targetPoints;
            this.remainingPoints = remainingPoints;
            this.nextTierBenefits = nextTierBenefits == null ? new ArrayList<>() : nextTierBenefits;
        }
    }

    /* ===== Tính hạng kế tiếp từ currentPoints ===== */
    public static NextTierInfo nextTier(long currentPoints) {
        if (currentPoints < BRONZE_MIN) {
            long need = Math.max(0, BRONZE_MIN - currentPoints);
            return new NextTierInfo("Đồng", BRONZE_MIN, need, benefitsForTier("Đồng"));
        }
        if (currentPoints <= BRONZE_MAX) {
            long need = Math.max(0, SILVER_MIN - currentPoints);
            return new NextTierInfo("Bạc", SILVER_MIN, need, benefitsForTier("Bạc"));
        }
        if (currentPoints <= SILVER_MAX) {
            long need = Math.max(0, GOLD_MIN - currentPoints);
            return new NextTierInfo("Vàng", GOLD_MIN, need, benefitsForTier("Vàng"));
        }
        // Đã là hạng cao nhất
        return new NextTierInfo(null, GOLD_MIN, 0, benefitsForTier("Vàng"));
    }
}
