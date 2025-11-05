package com.pro.milkteaapp.activity;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.pro.milkteaapp.databinding.ActivityOrderHistoryBinding;
import com.pro.milkteaapp.fragment.OrdersFragment;
import com.pro.milkteaapp.utils.StatusBarUtil;

public class OrderHistoryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityOrderHistoryBinding b = ActivityOrderHistoryBinding.inflate(getLayoutInflater());
        StatusBarUtil.setupDefaultStatusBar(this);
        setContentView(b.getRoot());

        // Toolbar + Back
        setSupportActionBar(b.toolbar);
        b.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        // Nhúng 1 fragment danh sách đơn (không còn tabs)
        if (savedInstanceState == null) {
            // Hiển thị tất cả đơn; nếu muốn lọc 1 trạng thái thì đổi "ALL" thành Order.STATUS_*
            getSupportFragmentManager().beginTransaction()
                    .replace(b.container.getId(), OrdersFragment.newInstance("ALL"))
                    .commit();
        }
    }
}
