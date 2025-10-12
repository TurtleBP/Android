package com.pro.milkteaapp.utils;

import java.text.NumberFormat;
import java.util.Locale;

public class MoneyUtils {

    /**
     * Formats a given price value into Vietnamese dong (VND) currency format.
     * The method assumes the input price is in VND, not in thousands.
     *
     * @param price The price to format, as a double.
     * @return A formatted string representing the price in VND (e.g., "45.000đ").
     */
    public static String formatVnd(double price) {
        try {
            NumberFormat format = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
            // Set the number of decimal places to zero for VND
            format.setMaximumFractionDigits(0);
            return format.format(price);
        } catch (Exception e) {
            // Fallback in case of formatting error
            return (long) price + "đ";
        }
    }
    public static String format(long amount) {
        NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("vi","VN"));
        return nf.format(amount); // ví dụ: 1.234.567 ₫
    }
}