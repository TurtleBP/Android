package com.pro.milkteaapp.fragment.admin.management;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.handler.AddActionHandler;

public class ManagementFragment extends Fragment {

    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private FloatingActionButton fab;
    private ManagementPagerAdapter adapter;

    private final String[] TITLES = {"Sản phẩm", "Category", "Voucher", "Rating"};

    public ManagementFragment(){}

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_admin_management, container, false);
        tabLayout = v.findViewById(R.id.tabLayout);
        viewPager = v.findViewById(R.id.viewPager);
        fab       = v.findViewById(R.id.fabAdd);

        adapter = new ManagementPagerAdapter(this);
        viewPager.setAdapter(adapter);
        viewPager.setOffscreenPageLimit(1); // đủ mượt, tránh giữ quá nhiều fragment

        new TabLayoutMediator(tabLayout, viewPager,
                (tab, pos) -> tab.setText(TITLES[pos])
        ).attach();

        viewPager.registerOnPageChangeCallback(onPageChangeCallback);

        // init
        syncFabFor(0);
        setTitle(0);
        return v;
    }

    private final ViewPager2.OnPageChangeCallback onPageChangeCallback = new ViewPager2.OnPageChangeCallback() {
        @Override public void onPageSelected(int position) {
            syncFabFor(position);
            setTitle(position);
        }
    };

    private void setTitle(int position){
        if (getActivity() instanceof AppCompatActivity) {
            AppCompatActivity act = (AppCompatActivity) getActivity();
            if (act.getSupportActionBar() != null) {
                act.getSupportActionBar().setTitle("Quản lý • " + TITLES[position]);
            }
        }
    }

    private void syncFabFor(int pos){
        if (fab == null) return;

        // Tab Rating: ẩn FAB nếu không có hành động Thêm
        if (pos == 3) {
            fab.hide();
            fab.setOnClickListener(null);
            return;
        }

        // Lấy fragment hiện tại từ adapter cache
        Fragment cur = adapter.getFragmentAt(pos);
        if (cur instanceof AddActionHandler) {
            fab.show();
            fab.setOnClickListener(_v -> ((AddActionHandler) cur).onAddAction());
        } else {
            fab.setOnClickListener(null);
            fab.hide();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (viewPager != null) viewPager.unregisterOnPageChangeCallback(onPageChangeCallback);
        fab = null; tabLayout = null; viewPager = null;
    }
}
