package com.pro.milkteaapp.activity;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.databinding.ActivityOrderHistoryBinding;
import com.pro.milkteaapp.fragment.PendingOrdersFragment;
import com.pro.milkteaapp.fragment.FinishedOrdersFragment;
import com.pro.milkteaapp.fragment.CancelledOrdersFragment; // <-- fragment đã có sẵn theo bạn

public class OrderHistoryActivity extends AppCompatActivity {

    public static final String EXTRA_INITIAL_TAB = "extra_initial_tab";
    public static final String TAB_PENDING   = "pending";
    public static final String TAB_FINISHED  = "finished";
    public static final String TAB_CANCELLED = "cancelled";

    private ActivityOrderHistoryBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        binding = ActivityOrderHistoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());

        setupPager(binding.viewPager, binding.tabLayout);
        selectInitialTabFromIntent(binding.viewPager);
    }

    private void setupPager(@NonNull ViewPager2 viewPager, @NonNull TabLayout tabLayout) {
        viewPager.setAdapter(new OrdersPagerAdapter(this));
        viewPager.setOffscreenPageLimit(3);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0: tab.setText(R.string.tab_pending);   break; // “Đang chờ”
                case 1: tab.setText(R.string.tab_finished);  break; // “Hoàn tất”
                case 2: tab.setText(R.string.tab_cancelled); break; // “Đã hủy”
            }
        }).attach();
    }

    private void selectInitialTabFromIntent(@NonNull ViewPager2 viewPager) {
        String tab = getIntent().getStringExtra(EXTRA_INITIAL_TAB);
        int index = 0;
        if (TAB_FINISHED.equalsIgnoreCase(tab))      index = 1;
        else if (TAB_CANCELLED.equalsIgnoreCase(tab)) index = 2;
        viewPager.setCurrentItem(index, false);
    }

    // ===== Adapter cho ViewPager2 =====
    private static class OrdersPagerAdapter extends androidx.viewpager2.adapter.FragmentStateAdapter {

        OrdersPagerAdapter(@NonNull AppCompatActivity activity) {
            super(activity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return switch (position) {
                case 1 -> new FinishedOrdersFragment();
                case 2 -> new CancelledOrdersFragment();
                default -> new PendingOrdersFragment();
            };
        }

        @Override
        public int getItemCount() { return 3; }
    }
}
