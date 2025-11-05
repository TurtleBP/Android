package com.pro.milkteaapp.utils;

import android.app.Activity;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.ColorRes;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.pro.milkteaapp.R;

public class StatusBarUtil {

    /**
     * Thiết lập status bar với màu tùy chỉnh
     * @param activity Activity cần setup
     * @param colorRes Màu resource
     * @param lightIcons true = icons màu đen, false = icons màu trắng
     */
    public static void setupStatusBar(Activity activity, @ColorRes int colorRes, boolean lightIcons) {
        if (activity == null || activity.isFinishing()) return;

        Window window = activity.getWindow();
        View decorView = window.getDecorView();

        // Đảm bảo status bar trong suốt
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        WindowCompat.setDecorFitsSystemWindows(window, false);

        // Tạo hoặc cập nhật status bar view
        setupStatusBarView(activity, decorView, colorRes);

        // Điều khiển màu icons status bar
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, decorView);
        controller.setAppearanceLightStatusBars(lightIcons);
    }

    /**
     * Thiết lập status bar với màu mặc định (primary_dark) và icons trắng
     */
    public static void setupDefaultStatusBar(Activity activity) {
        setupStatusBar(activity, R.color.primary_light, false);
    }

    private static void setupStatusBarView(Activity activity, View decorView, @ColorRes int colorRes) {
        // Tìm status bar view hiện có
        View existingView = decorView.findViewWithTag("custom_status_bar");

        if (existingView != null) {
            // Cập nhật màu cho view đã tồn tại
            existingView.setBackgroundColor(ContextCompat.getColor(activity, colorRes));
            return;
        }

        // Tạo mới status bar view
        View statusBarView = new View(activity);
        statusBarView.setTag("custom_status_bar"); // Thêm tag để tìm lại sau này

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                getStatusBarHeight(activity)
        );
        statusBarView.setLayoutParams(params);
        statusBarView.setBackgroundColor(ContextCompat.getColor(activity, colorRes));

        // Thêm vào decor view
        if (decorView instanceof FrameLayout) {
            ((FrameLayout) decorView).addView(statusBarView);
        }
    }

    /**
     * Lấy chiều cao status bar an toàn, không dùng reflection
     */
    @SuppressWarnings({"DiscouragedApi", "InternalInsetResource"})
    private static int getStatusBarHeight(Activity activity) {
        try {
            // Sử dụng resource ID trực tiếp thay vì getIdentifier()
            int resourceId = activity.getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (resourceId > 0) {
                return activity.getResources().getDimensionPixelSize(resourceId);
            }

            // Fallback: sử dụng giá trị mặc định 24dp
            return (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    24,
                    activity.getResources().getDisplayMetrics()
            );
        } catch (Exception e) {
            // Fallback cuối cùng
            return (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    24,
                    activity.getResources().getDisplayMetrics()
            );
        }
    }

    /**
     * Xóa status bar view custom (nếu cần)
     */
    public static void removeCustomStatusBar(Activity activity) {
        if (activity == null) return;

        View decorView = activity.getWindow().getDecorView();
        View statusBarView = decorView.findViewWithTag("custom_status_bar");

        if (statusBarView != null && statusBarView.getParent() != null) {
            ((ViewGroup) statusBarView.getParent()).removeView(statusBarView);
        }
    }

    /**
     * Cập nhật màu status bar (cho trường hợp dynamic theme)
     */
    public static void updateStatusBarColor(Activity activity, @ColorRes int colorRes) {
        if (activity == null) return;

        View decorView = activity.getWindow().getDecorView();
        View statusBarView = decorView.findViewWithTag("custom_status_bar");

        if (statusBarView != null) {
            statusBarView.setBackgroundColor(ContextCompat.getColor(activity, colorRes));
        }
    }
}