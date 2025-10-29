package com.pro.milkteaapp.utils;

import java.text.NumberFormat;
import java.util.Locale;

public class MoneyUtils {
    public static String formatVnd(double price) {
        try {
            NumberFormat f = NumberFormat.getCurrencyInstance(new Locale("vi","VN"));
            f.setMaximumFractionDigits(0);
            return f.format(price);          // ví dụ: 45.000 ₫
        } catch (Exception e) {
            return ((long) price) + "đ";
        }
    }
    public static String format(double amount) { return formatVnd(amount); }
}
