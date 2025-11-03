package com.pro.milkteaapp.utils;

import java.util.ArrayList;
import java.util.List;

public final class LoyaltyPolicy {

    private LoyaltyPolicy(){}

    // Ngưỡng điểm thăng hạng (KHÔNG bị trừ)
    public static final long UNRANK_MAX = 99;
    public static final long BRONZE_MIN = 100;
    public static final long BRONZE_MAX = 399;
    public static final long SILVER_MIN = 400;
    public static final long SILVER_MAX = 999;
    public static final long GOLD_MIN   = 1000;

    // % giảm theo hạng (áp dụng trên subtotal)
    public static double discountPercent(String tier) {
        if (tier == null) return 0.0;
        switch (tier) {
            case "Vàng":  return 0.20; // 20%
            case "Bạc":   return 0.10; // 10%
            case "Đồng":  return 0.05; // 5%
            default:      return 0.0;  // Chưa xếp hạng
        }
    }

    // Suy ra hạng từ tổng điểm thăng hạng
    public static String tierForPoints(long points) {
        if (points >= GOLD_MIN) return "Vàng";
        if (points >= SILVER_MIN) return "Bạc";
        if (points >= BRONZE_MIN) return "Đồng";
        return "Chưa xếp hạng";
    }

    // Quyền lợi theo hạng (hiển thị trong BottomSheet + trang trạng thái)
    public static List<String> benefitsForTier(String tier) {
        List<String> list = new ArrayList<>();
        if (tier == null) tier = "Chưa xếp hạng";
        switch (tier) {
            case "Vàng":
                list.add("Giảm 20% mỗi đơn");
                list.add("Ưu tiên cao nhất khi hỗ trợ/khuyến mãi");
                list.add("Nhận voucher định kỳ");
                list.add("Xử lý đơn nhanh, ưu tiên tích lũy");
                break;
            case "Bạc":
                list.add("Giảm 10% mỗi đơn");
                list.add("Ưu tiên chăm sóc & khuyến mãi");
                list.add("Có mặt trong danh mục đổi quà điểm");
                break;
            case "Đồng":
                list.add("Giảm 5% mỗi đơn");
                list.add("Nhận thông tin khuyến mãi sớm");
                list.add("Được đổi quà bằng điểm đổi quà");
                break;
            default: // Chưa xếp hạng
                list.add("Tích điểm để lên hạng và nhận ưu đãi");
                list.add("Có thể đổi quà bằng điểm đổi quà khi đủ điều kiện");
                break;
        }
        return list;
    }

    // Thông tin hạng tiếp theo (để hiển thị “còn thiếu bao nhiêu điểm”)
    public static class NextTierInfo {
        public final String targetTier;     // null nếu đã cao nhất
        public final long targetPoints;     // mốc điểm cần đạt của hạng sau
        public final long remainingPoints;  // còn thiếu

        public NextTierInfo(String targetTier, long targetPoints, long remainingPoints) {
            this.targetTier = targetTier;
            this.targetPoints = targetPoints;
            this.remainingPoints = remainingPoints;
        }
    }

    public static NextTierInfo nextTier(long currentPoints) {
        if (currentPoints < BRONZE_MIN) {
            long need = Math.max(0, BRONZE_MIN - currentPoints);
            return new NextTierInfo("Đồng", BRONZE_MIN, need);
        }
        if (currentPoints <= BRONZE_MAX) {
            long need = Math.max(0, SILVER_MIN - currentPoints);
            return new NextTierInfo("Bạc", SILVER_MIN, need);
        }
        if (currentPoints <= SILVER_MAX) {
            long need = Math.max(0, GOLD_MIN - currentPoints);
            return new NextTierInfo("Vàng", GOLD_MIN, need);
        }
        // Đã là hạng cao nhất
        return new NextTierInfo(null, GOLD_MIN, 0);
    }
}
