package com.pro.milkteaapp.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.*;
import com.pro.milkteaapp.activity.OrderDetailActivity;
import com.pro.milkteaapp.adapter.OrdersAdapter;
import com.pro.milkteaapp.databinding.FragmentOrdersBinding;
import com.pro.milkteaapp.models.Order;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OrdersFragment extends Fragment implements OrdersAdapter.Listener {

    private static final String ARG_FILTER = "arg_filter";

    /** ✅ Gọi từ Activity: OrdersFragment.newInstance("ALL" | Order.STATUS_*) */
    public static OrdersFragment newInstance(@Nullable String filter) {
        OrdersFragment f = new OrdersFragment();
        Bundle b = new Bundle();
        b.putString(ARG_FILTER, filter);
        f.setArguments(b);
        return f;
    }

    private FragmentOrdersBinding b;
    private FirebaseFirestore db;
    private ListenerRegistration registration;
    private final List<Order> allOrders = new ArrayList<>();
    private OrdersAdapter adapter;

    // Tab filter hiện tại
    private String currentFilter = "ALL";

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle s) {
        b = FragmentOrdersBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        db = FirebaseFirestore.getInstance();

        // Đọc filter từ args (nếu có)
        if (getArguments() != null) {
            String arg = getArguments().getString(ARG_FILTER);
            if (arg != null && !arg.trim().isEmpty()) currentFilter = arg.trim();
        }

        // RecyclerView
        b.rvOrders.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new OrdersAdapter(this);
        b.rvOrders.setAdapter(adapter);

        setupTabsAndSelectCurrent();
        subscribeOrdersRealtime();
    }

    private void setupTabsAndSelectCurrent() {
        b.tabs.addTab(b.tabs.newTab().setText("Tất cả").setTag("ALL"));
        b.tabs.addTab(b.tabs.newTab().setText("Chờ xác nhận").setTag(Order.STATUS_PENDING));
        b.tabs.addTab(b.tabs.newTab().setText("Hoàn tất").setTag(Order.STATUS_FINISHED));
        b.tabs.addTab(b.tabs.newTab().setText("Đã hủy").setTag(Order.STATUS_CANCELLED));

        // Chọn tab theo currentFilter
        for (int i = 0; i < b.tabs.getTabCount(); i++) {
            TabLayout.Tab t = b.tabs.getTabAt(i);
            if (t != null && String.valueOf(t.getTag()).equals(currentFilter)) {
                t.select();
                break;
            }
        }

        b.tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                Object tag = tab.getTag();
                currentFilter = tag == null ? "ALL" : String.valueOf(tag);
                applyFilter();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void subscribeOrdersRealtime() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(requireContext(), "Vui lòng đăng nhập để xem đơn hàng", Toast.LENGTH_SHORT).show();
            return;
        }

        Query q = db.collection("orders")
                .whereEqualTo("userId", user.getUid())
                .orderBy("createdAt", Query.Direction.DESCENDING);

        registration = q.addSnapshotListener((snap, e) -> {
            if (e != null) {
                if (isAdded()) Toast.makeText(requireContext(), "Lỗi tải đơn: " + e.getMessage(), Toast.LENGTH_LONG).show();
                return;
            }
            if (snap == null) return;

            allOrders.clear();
            for (DocumentSnapshot d : snap.getDocuments()) {
                allOrders.add(Order.from(d));
            }
            applyFilter();
            b.empty.setVisibility(allOrders.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private void applyFilter() {
        List<Order> filtered = new ArrayList<>();
        if ("ALL".equals(currentFilter)) {
            filtered.addAll(allOrders);
        } else {
            for (Order o : allOrders) {
                if (currentFilter.equals(o.getStatus())) filtered.add(o);
            }
        }
        if (filtered.size() > 1) {
            Collections.sort(filtered, (o1, o2) -> {
                if (o1.getCreatedAt() == null && o2.getCreatedAt() == null) return o2.getId().compareTo(o1.getId());
                if (o1.getCreatedAt() == null) return 1;
                if (o2.getCreatedAt() == null) return -1;
                return o2.getCreatedAt().compareTo(o1.getCreatedAt());
            });
        }
        adapter.submitList(filtered);
    }

    @Override
    public void onOrderClick(@NonNull Order order) {
        Intent i = new Intent(requireContext(), com.pro.milkteaapp.activity.OrderDetailActivity.class);
        i.putExtra(com.pro.milkteaapp.activity.OrderDetailActivity.EXTRA_ORDER_ID, order.getId());
        startActivity(i);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (registration != null) registration.remove();
        registration = null;
        b = null;
    }
}
