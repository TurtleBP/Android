package com.pro.milkteaapp.fragment.admin.management;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.HashMap;
import java.util.Map;

/** Adapter có cache fragment theo position để lấy lại dễ dàng */
public class ManagementPagerAdapter extends FragmentStateAdapter {

    private final Map<Integer, Fragment> cache = new HashMap<>();

    public ManagementPagerAdapter(@NonNull Fragment host) {
        super(host);
    }

    @NonNull @Override
    public Fragment createFragment(int position) {
        Fragment f;
        switch (position) {
            case 0: // Sản phẩm
                f = new com.pro.milkteaapp.fragment.admin.AdminProductsFragment();
                break;
            case 1: // Category
                f = new com.pro.milkteaapp.fragment.CategoryManageFragment();
                break;
            case 2: // Voucher
                f = new com.pro.milkteaapp.fragment.admin.management.VoucherManageFragment();
                break;
            case 3: // Rating
            default:
                f = new com.pro.milkteaapp.fragment.admin.management.RatingManageFragment();
                break;
        }
        cache.put(position, f);
        return f;
    }

    @Override
    public int getItemCount() { return 4; }

    /** Lấy fragment đã tạo cho position; có thể null nếu chưa khởi tạo trang đó */
    public Fragment getFragmentAt(int position) {
        return cache.get(position);
    }

    // Nếu muốn ổn định hơn khi dữ liệu thay đổi:
    // @Override public long getItemId(int position) { return position; }
    // @Override public boolean containsItem(long itemId) { return itemId >= 0 && itemId < getItemCount(); }
}
