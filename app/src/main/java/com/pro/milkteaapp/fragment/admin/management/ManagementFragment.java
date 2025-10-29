package com.pro.milkteaapp.fragment.admin.management;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.tabs.TabLayoutMediator;
import com.pro.milkteaapp.databinding.FragmentAdminManagementBinding;
import com.pro.milkteaapp.handler.AddActionHandler;

public class ManagementFragment extends Fragment {

    private FragmentAdminManagementBinding binding;
    private ManagementPagerAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAdminManagementBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);

        adapter = new ManagementPagerAdapter(this);
        binding.viewPager.setAdapter(adapter);

        new TabLayoutMediator(binding.tabLayout, binding.viewPager, (tab, position) -> {
            switch (position) {
                case ManagementPagerAdapter.PAGE_PRODUCTS: tab.setText("Sản phẩm"); break;
                case ManagementPagerAdapter.PAGE_CATEGORY: tab.setText("Danh mục"); break;
                case ManagementPagerAdapter.PAGE_VOUCHER:  tab.setText("Giảm Giá");  break;
                case ManagementPagerAdapter.PAGE_RATING:   tab.setText("Đánh giá"); break;
                case ManagementPagerAdapter.PAGE_TOPPING:  tab.setText("Top pings"); break;
            }
        }).attach();

        // FAB chung: uỷ quyền cho fragment hiện tại nếu fragment đó implements AddActionHandler
        binding.fabAdd.setOnClickListener(v1 -> {
            int pos = binding.viewPager.getCurrentItem();
            Fragment current = adapter.getFragment(pos);
            if (current instanceof AddActionHandler) {
                ((AddActionHandler) current).onAddAction();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
