package com.pro.milkteaapp.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.navigation.NavigationBarView;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.databinding.ActivityMainBinding;
import com.pro.milkteaapp.fragment.CartFragment;
import com.pro.milkteaapp.fragment.HomeFragment;
import com.pro.milkteaapp.fragment.MessagesFragment;
import com.pro.milkteaapp.fragment.ProductFragment;
import com.pro.milkteaapp.fragment.ProfileFragment;
import com.pro.milkteaapp.utils.StatusBarUtil;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    private static final long NAV_DEBOUNCE_MS = 400L;
    private long lastNavClick = 0L;

    public static final String EXTRA_TARGET_FRAGMENT = "target_fragment";
    private static final String TARGET_HOME = "home";
    private static final String TARGET_PRODUCTS = "products";
    private static final String TARGET_CART = "cart";
    private static final String TARGET_MESSAGES = "messages";
    private static final String TARGET_PROFILE = "profile";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        StatusBarUtil.setupDefaultStatusBar(this);
        setContentView(binding.getRoot());

        // Lắng nghe chọn bottom nav
        binding.bottomNavigationView.setOnItemSelectedListener(onNavSelectedListener);

        // Reselect: ví dụ, về top nếu bấm lại tab hiện tại
        binding.bottomNavigationView.setOnItemReselectedListener(item -> {
            Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            // ((HomeFragment) f).scrollToTop();
        });

        // Nếu có intent điều hướng → xử lý; ngược lại lần đầu vào chọn Home
        boolean handled = handleIntent(getIntent());
        if (!handled && savedInstanceState == null) {
            binding.bottomNavigationView.setSelectedItemId(R.id.navigation_home);
            navigateTo(R.id.navigation_home);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    /** Xử lý điều hướng qua Intent; trả true nếu đã xử lý */
    private boolean handleIntent(Intent intent) {
        if (intent == null || !intent.hasExtra(EXTRA_TARGET_FRAGMENT)) return false;

        String target = intent.getStringExtra(EXTRA_TARGET_FRAGMENT);
        @IdRes int menuId = mapTargetToMenuId(target);
        if (menuId == 0) return false;

        int current = binding.bottomNavigationView.getSelectedItemId();
        if (current == menuId) {
            navigateTo(menuId);
        } else {
            binding.bottomNavigationView.setSelectedItemId(menuId);
        }
        return true;
    }

    public void openHomeTab() {
        NavigationBarView bottomNav = findViewById(R.id.bottomNavigationView);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.navigation_home);
        } else {
            safeReplaceFragment(new HomeFragment());
        }
    }

    // === Public helpers để Fragment gọi chuyển tab ===

    public void openProductsTab() {
        NavigationBarView bottomNav = findViewById(R.id.bottomNavigationView);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.navigation_products);
        } else {
            switchToProductsFragment();
        }
    }

    public void openCartTab() {
        NavigationBarView bottomNav = findViewById(R.id.bottomNavigationView);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.navigation_cart);
        } else {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new CartFragment())
                    .addToBackStack(null)
                    .commit();
        }
    }

    /** (Tuỳ chọn) Fallback manual fragment switch */
    private void switchToProductsFragment() {
        Fragment products = new ProductFragment();
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, products)
                .addToBackStack(null)
                .commit();
    }

    // === Bottom nav listener ===

    private final NavigationBarView.OnItemSelectedListener onNavSelectedListener =
            item -> {
                long now = SystemClock.elapsedRealtime();
                if (now - lastNavClick < NAV_DEBOUNCE_MS) return false; // chặn click dồn
                lastNavClick = now;
                return navigateTo(item.getItemId());
            };

    /** Điều hướng tới fragment theo menuId */
    private boolean navigateTo(@IdRes int menuId) {
        Fragment target = null;
        if (menuId == R.id.navigation_home) {
            target = new HomeFragment();
        } else if (menuId == R.id.navigation_products) {
            target = new ProductFragment();
        } else if (menuId == R.id.navigation_cart) {
            target = new CartFragment();
        } else if (menuId == R.id.navigation_messages) {
            target = new MessagesFragment();
        } else if (menuId == R.id.navigation_profile) {
            target = new ProfileFragment();
        }
        if (target == null) return false;

        safeReplaceFragment(target);
        return true;
    }

    public void selectBottomNav(@IdRes int itemId) {
        if (binding != null) {
            binding.bottomNavigationView.setSelectedItemId(itemId);
        }
    }

    /** Thay fragment an toàn, tránh IllegalStateException/state loss */
    private void safeReplaceFragment(@NonNull Fragment fragment) {
        if (isFinishing() || isDestroyed()) return;

        FragmentManager fm = getSupportFragmentManager();
        if (fm.isStateSaved()) {
            getWindow().getDecorView().post(() -> safeReplaceFragment(fragment));
            return;
        }
        fm.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    @IdRes
    private int mapTargetToMenuId(String target) {
        if (target == null) return 0;
        return switch (target) {
            case TARGET_HOME -> R.id.navigation_home;
            case TARGET_PRODUCTS -> R.id.navigation_products;
            case TARGET_CART -> R.id.navigation_cart;
            case TARGET_MESSAGES -> R.id.navigation_messages;
            case TARGET_PROFILE -> R.id.navigation_profile;
            default -> 0;
        };
    }

}