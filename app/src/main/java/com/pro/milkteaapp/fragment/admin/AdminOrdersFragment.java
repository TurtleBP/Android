package com.pro.milkteaapp.fragment.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.models.Order;
import com.pro.milkteaapp.adapters.admin.AdminOrdersAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Quản lý đơn hàng cho Admin — 3 tab: Pending / Finished / Cancelled
 * - Tab Pending: hiện nút Xác nhận
 * - Tab Finished: không có nút
 * - Tab Cancelled: hiện nút Xóa
 */
public class AdminOrdersFragment extends Fragment {

    private TabLayout tabAdmin;
    private RecyclerView recyclerView;
    private View emptyState, loadingState;
    private SwipeRefreshLayout swipeRefresh;
    private ListenerRegistration registration;
    private AdminOrdersAdapter adapter;

    private String currentStatus = "PENDING"; // tab mặc định

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.activity_admin_orders, container, false);

        tabAdmin     = v.findViewById(R.id.tabAdmin);
        swipeRefresh = v.findViewById(R.id.swipeRefresh);
        recyclerView = v.findViewById(R.id.recycler);
        emptyState   = v.findViewById(R.id.emptyState);
        loadingState = v.findViewById(R.id.loadingState);

        setupTabs();
        setupRecyclerView();

        swipeRefresh.setOnRefreshListener(this::loadData);
        loadData(); // lần đầu
        return v;
    }

    private void setupTabs() {
        if (tabAdmin.getTabCount() == 0) {
            tabAdmin.addTab(tabAdmin.newTab().setText("Pending"));
            tabAdmin.addTab(tabAdmin.newTab().setText("Finished"));
            tabAdmin.addTab(tabAdmin.newTab().setText("Cancelled"));
        }

        tabAdmin.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                int pos = tab.getPosition();
                currentStatus = (pos == 0) ? "PENDING"
                        : (pos == 1) ? "FINISHED" : "CANCELLED";

                // Set cờ theo tab MỚI trước khi bind
                adapter.setShowConfirm("PENDING".equals(currentStatus));
                adapter.setShowDelete("CANCELLED".equals(currentStatus));

                // Ẩn list & xóa dữ liệu cũ để tránh reuse ViewHolder tab trước
                recyclerView.setVisibility(View.INVISIBLE);
                adapter.submitList(java.util.Collections.emptyList());

                showLoading(true);
                loadData();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) { loadData(); }
        });
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new AdminOrdersAdapter(this::onConfirmClicked, this::onDeleteClicked);

        // Tắt animator để loại bỏ mọi animation đổi trạng thái (tránh lóe nút)
        recyclerView.setItemAnimator(null);

        // Ngăn khôi phục scroll khi list trống
        adapter.setStateRestorationPolicy(
                RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        );

        recyclerView.setAdapter(adapter);
    }

    private void loadData() {
        if (!isAdded()) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Query q = db.collection("orders")
                .whereEqualTo("status", currentStatus)
                .orderBy("createdAt", Query.Direction.DESCENDING);

        if (registration != null) {
            registration.remove();
            registration = null;
        }

        registration = q.addSnapshotListener((snap, err) -> {
            if (!isAdded()) return;

            swipeRefresh.setRefreshing(false);

            if (err != null) {
                showLoading(false);
                showEmpty();
                Toast.makeText(getContext(), "Lỗi tải: " + err.getMessage(), Toast.LENGTH_LONG).show();
                return;
            }

            List<Order> data = new ArrayList<>();
            if (snap != null) {
                for (DocumentSnapshot d : snap.getDocuments()) {
                    Order o = d.toObject(Order.class);
                    if (o != null) { o.setId(d.getId()); data.add(o); }
                }
            }

            // Đặt cờ theo tab trước khi bind
            adapter.setShowConfirm("PENDING".equals(currentStatus));
            adapter.setShowDelete("CANCELLED".equals(currentStatus));

            // Chỉ hiện list sau khi submitList đã render xong
            adapter.submitList(data, () -> {
                showLoading(false);
                if (data.isEmpty()) {
                    showEmpty();
                } else {
                    showList();
                }
                recyclerView.setVisibility(View.VISIBLE);
            });
        });
    }

    /** Khi admin xác nhận đơn ở tab Pending */
    private void onConfirmClicked(Order order) {
        if (!"PENDING".equals(currentStatus)) return;

        FirebaseUser admin = FirebaseAuth.getInstance().getCurrentUser();
        java.util.Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("status", "FINISHED");
        updates.put("confirmedAt", FieldValue.serverTimestamp());
        updates.put("confirmedBy", admin != null ? admin.getEmail() : null);

        FirebaseFirestore.getInstance().collection("orders")
                .document(order.getId())
                .update(updates)
                .addOnSuccessListener(s -> Toast.makeText(getContext(),
                        "✅ Đã xác nhận đơn #" + order.getId(), Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(getContext(),
                        "❌ Lỗi xác nhận: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    /** Khi admin xóa đơn đã hủy ở tab Cancelled */
    private void onDeleteClicked(Order order) {
        FirebaseFirestore.getInstance().collection("orders")
                .document(order.getId())
                .delete()
                .addOnSuccessListener(s -> Toast.makeText(getContext(),
                        "🗑️ Đã xóa đơn #" + order.getId(), Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(getContext(),
                        "❌ Lỗi xóa: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void showLoading(boolean s) { loadingState.setVisibility(s ? View.VISIBLE : View.GONE); }
    private void showEmpty() { emptyState.setVisibility(View.VISIBLE); recyclerView.setVisibility(View.GONE); }
    private void showList() { emptyState.setVisibility(View.GONE); recyclerView.setVisibility(View.VISIBLE); }

    @Override public void onDestroyView() {
        super.onDestroyView();
        if (registration != null) { registration.remove(); registration = null; }
    }
}
