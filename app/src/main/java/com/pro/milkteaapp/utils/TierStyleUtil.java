package com.pro.milkteaapp.utils;

import android.content.Context;
import android.graphics.PorterDuff;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import com.google.android.material.card.MaterialCardView;
import com.pro.milkteaapp.R;

/** Gắn màu & nhãn theo cấp: Chưa xếp hạng / Đồng / Bạc / Vàng */
public final class TierStyleUtil {

    private TierStyleUtil(){}

    public record TierColors(@ColorInt int bg, @ColorInt int dark, String label) {
    }

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

    /** Áp style cho card + crown + badge + text tier */
    public static void apply(Context c,
                             @NonNull String tier,
                             MaterialCardView cardLoyalty,
                             ImageView imgCrown,
                             TextView tvTierBadge,
                             TextView tvLoyaltyTier) {
        TierColors t = resolve(c, tier);

        // Viền card
        if (cardLoyalty != null) {
            cardLoyalty.setStrokeColor(t.dark);
        }

        // Vương miện (tint)
        if (imgCrown != null) {
            imgCrown.setColorFilter(t.bg, PorterDuff.Mode.SRC_IN);
        }

        // Badge
        if (tvTierBadge != null) {
            tvTierBadge.getBackground().setColorFilter(t.bg, PorterDuff.Mode.SRC_IN);
            tvTierBadge.setText(t.label);
        }

        // Text "Hạng" ở cột số liệu
        if (tvLoyaltyTier != null) {
            tvLoyaltyTier.setText(t.label);
            tvLoyaltyTier.setTextColor(t.dark);
        }
    }
}
