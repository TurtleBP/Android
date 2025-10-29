package com.pro.milkteaapp.fragment.admin.management;

import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.pro.milkteaapp.fragment.admin.AdminProductsFragment;

/**
 * Tabs trong màn Quản lý:
 * 0: Sản phẩm
 * 1: Danh mục
 * 2: Voucher
 * 3: Đánh giá
 * 4: Toppings
 */
public class ManagementPagerAdapter extends FragmentStateAdapter {

    public static final int PAGE_PRODUCTS = 0;
    public static final int PAGE_CATEGORY = 1;
    public static final int PAGE_VOUCHER  = 2;
    public static final int PAGE_RATING   = 3;
    public static final int PAGE_TOPPING  = 4;

    public static final int PAGE_COUNT    = 5;

    // Dùng để lấy fragment hiện tại trong ViewPager
    private final SparseArray<Fragment> registeredFragments = new SparseArray<>();

    public ManagementPagerAdapter(@NonNull Fragment parent) {
        super(parent);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        Fragment f;
        switch (position) {
            case PAGE_PRODUCTS: f = new AdminProductsFragment();  break;
            case PAGE_CATEGORY: f = new CategoryManageFragment(); break;
            case PAGE_VOUCHER:  f = new VoucherManageFragment();  break;
            case PAGE_RATING:   f = new RatingManageFragment();   break;
            case PAGE_TOPPING:  f = new ToppingManageFragment();  break;
            default:            f = new CategoryManageFragment();
        }
        registeredFragments.put(position, f);
        return f;
    }

    @Override
    public int getItemCount() {
        return PAGE_COUNT;
    }

    /** Cho phép ManagementFragment lấy fragment hiện tại để gọi onAddAction() */
    @Nullable
    public Fragment getFragment(int position) {
        return registeredFragments.get(position);
    }
}
