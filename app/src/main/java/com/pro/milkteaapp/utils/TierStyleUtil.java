package com.pro.milkteaapp.utils;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.widget.ImageViewCompat;

import com.google.android.material.card.MaterialCardView;
import com.pro.milkteaapp.R;

/** Gắn màu & nhãn theo cấp: Chưa xếp hạng / Đồng / Bạc / Vàng */
public final class TierStyleUtil {

    private TierStyleUtil() {}

    public record TierColors(@ColorInt int bg, @ColorInt int dark, String label) {}

    public static @NonNull TierColors resolve(Context c, @NonNull String tier) {
        return switch (tier) {
            case "Vàng" -> new TierColors(
                    c.getColor(R.color.tier_gold_bg),
                    c.getColor(R.color.tier_gold_dark),
                    "Vàng"
            );
            case "Bạc" -> new TierColors(
                    c.getColor(R.color.tier_silver_bg),
                    c.getColor(R.color.tier_silver_dark),
                    "Bạc"
            );
            case "Đồng" -> new TierColors(
                    c.getColor(R.color.tier_bronze_bg),
                    c.getColor(R.color.tier_bronze_dark),
                    "Đồng"
            );
            default -> new TierColors(
                    c.getColor(R.color.tier_unrank_bg),
                    c.getColor(R.color.tier_unrank_dark),
                    "Chưa xếp hạng"
            );
        };
    }

    /** Áp style cho card + crown + badge + text tier (không đụng vào visibility của crown) */
    public static void apply(Context c,
                             @NonNull String tier,
                             MaterialCardView cardLoyalty,
                             ImageView imgCrown,
                             TextView tvTierBadge,
                             TextView tvLoyaltyTier) {
        TierColors t = resolve(c, tier);

        // Viền card
        if (cardLoyalty != null) {
            cardLoyalty.setStrokeWidth(dp(c));
            cardLoyalty.setStrokeColor(t.dark);
        }

        // Vương miện: chỉ tint, KHÔNG ẩn/hiện ở đây
        if (imgCrown != null) {
            ImageViewCompat.setImageTintList(imgCrown, ColorStateList.valueOf(t.bg));
        }

        // Badge (wrap & mutate trước khi set tint)
        if (tvTierBadge != null) {
            Drawable bg = tvTierBadge.getBackground();
            if (bg != null) {
                bg = DrawableCompat.wrap(bg.mutate());
                DrawableCompat.setTint(bg, t.bg);
                tvTierBadge.setBackground(bg);
            }
            tvTierBadge.setText(t.label);
            tvTierBadge.setTextColor(t.dark);
        }

        // Text “Hạng” (nếu layout còn dùng)
        if (tvLoyaltyTier != null) {
            tvLoyaltyTier.setText(t.label);
            tvLoyaltyTier.setTextColor(t.dark);
        }
    }

    /* ===== Helpers ===== */
    private static int dp(Context c) {
        float d = c.getResources().getDisplayMetrics().density;
        return Math.round(2 * d);
    }
}
