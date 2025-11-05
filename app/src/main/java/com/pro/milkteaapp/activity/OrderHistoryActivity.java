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

        setSupportActionBar(b.toolbar);
        b.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(b.container.getId(), OrdersFragment.newInstance("ALL", false))
                    .commit();
        }
    }
}
