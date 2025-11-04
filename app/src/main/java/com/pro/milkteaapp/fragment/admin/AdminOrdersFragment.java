package com.pro.milkteaapp.fragment.admin;

import android.content.Intent;
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
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.activity.admin.AdminOrderDetailActivity;
import com.pro.milkteaapp.adapter.admin.AdminOrdersAdapter;
import com.pro.milkteaapp.models.Order;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AdminOrdersFragment extends Fragment {

    private TabLayout tabAdmin;
    private RecyclerView recyclerView;
    private View emptyState, loadingState;
    private SwipeRefreshLayout swipeRefresh;
    private ListenerRegistration registration;
    private AdminOrdersAdapter adapter;

    private String currentStatus = "PENDING";

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
        loadData();

        return v;
    }

    private void setupTabs() {
        if (tabAdmin.getTabCount() == 0) {
            tabAdmin.addTab(tabAdmin.newTab().setText("Pending"));
            tabAdmin.addTab(tabAdmin.newTab().setText("Finished"));
            tabAdmin.addTab(tabAdmin.newTab().setText("Cancelled"));
        }

        tabAdmin.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int pos = tab.getPosition();
                currentStatus = (pos == 0) ? "PENDING" : (pos == 1) ? "FINISHED" : "CANCELLED";

                recyclerView.setVisibility(View.INVISIBLE);
                adapter.submitList(new ArrayList<>());

                showLoading(true);
                loadData();
            }

            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) { loadData(); }
        });
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new AdminOrdersAdapter(
                this::onItemClicked,
                null, // Không dùng xác nhận
                null, // Không dùng huỷ
                this::onDeleteClicked // Giữ lại chức năng xoá
        );

        // Tắt toàn bộ nút confirm & cancel
        adapter.setShowConfirm(false);
        adapter.setShowCancel(false);

        recyclerView.setItemAnimator(null);
        adapter.setStateRestorationPolicy(
                RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        );
        recyclerView.setAdapter(adapter);
    }

    /** Click item → mở chi tiết đơn hàng dành riêng cho admin */
    private void onItemClicked(@NonNull Order order) {
        if (!isAdded()) return;
        Intent i = new Intent(requireContext(), AdminOrderDetailActivity.class);
        i.putExtra(AdminOrderDetailActivity.EXTRA_ORDER_ID, order.getId());
        startActivity(i);
    }

    private void loadData() {
        if (!isAdded()) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Query q;

        // Query server theo field “ổn định” để có index đơn giản, rồi sort lại trên client.
        if ("PENDING".equalsIgnoreCase(currentStatus)) {
            q = db.collection("orders")
                    .whereEqualTo("status", "PENDING")
                    .orderBy("createdAt", Query.Direction.DESCENDING);
        } else if ("FINISHED".equalsIgnoreCase(currentStatus)) {
            // vẫn orderBy createdAt (ít index), client sẽ sort lại theo finishedAt
            q = db.collection("orders")
                    .whereEqualTo("status", "FINISHED")
                    .orderBy("createdAt", Query.Direction.DESCENDING);
        } else { // CANCELLED tab → lấy cả CANCELLED & CANCELED
            q = db.collection("orders")
                    .whereIn("status", java.util.Arrays.asList("CANCELLED", "CANCELED"))
                    .orderBy("createdAt", Query.Direction.DESCENDING);
        }

        if (registration != null) { registration.remove(); registration = null; }

        registration = q.addSnapshotListener((snap, err) -> {
            if (!isAdded()) return;

            swipeRefresh.setRefreshing(false);

            if (err != null) {
                showLoading(false);
                showEmpty();
                toastLong("Lỗi tải: " + err.getMessage());
                return;
            }

            List<Order> data = new ArrayList<>();
            if (snap != null) {
                for (DocumentSnapshot d : snap.getDocuments()) {
                    Order o = d.toObject(Order.class);
                    if (o != null) { o.setId(d.getId()); data.add(o); }
                }
            }

            // ===== Client-side sort theo timestamp “đúng nghĩa” =====
            if ("CANCELLED".equalsIgnoreCase(currentStatus)) {
                // Ưu tiên cancelledAt/canceledAt, fallback createdAt
                Collections.sort(data, (a, b) -> {
                    Timestamp ta = firstNonNullCancelTs(a);
                    Timestamp tb = firstNonNullCancelTs(b);
                    long la = tsToLong(ta != null ? ta : a.getCreatedAt());
                    long lb = tsToLong(tb != null ? tb : b.getCreatedAt());
                    return Long.compare(lb, la); // desc
                });
            } else if ("FINISHED".equalsIgnoreCase(currentStatus)) {
                // Ưu tiên finishedAt, fallback createdAt
                Collections.sort(data, (a, b) -> {
                    long la = tsToLong(a.getFinishedAt() != null ? a.getFinishedAt() : a.getCreatedAt());
                    long lb = tsToLong(b.getFinishedAt() != null ? b.getFinishedAt() : b.getCreatedAt());
                    return Long.compare(lb, la); // desc
                });
            } // PENDING: đã orderBy createdAt desc ở server

            // Chỉ hiện nút xóa ở tab Cancelled
            adapter.setShowDelete("CANCELLED".equalsIgnoreCase(currentStatus));

            adapter.submitList(data, () -> {
                showLoading(false);
                if (data.isEmpty()) showEmpty(); else showList();
                recyclerView.setVisibility(View.VISIBLE);
            });
        });
    }

    /** Xoá hẳn record (tab Cancelled) */
    private void onDeleteClicked(@NonNull Order order) {
        FirebaseFirestore.getInstance().collection("orders")
                .document(order.getId())
                .delete()
                .addOnSuccessListener(s -> toast("🗑️ Đã xóa đơn #" + order.getId()))
                .addOnFailureListener(e -> toastLong("❌ Lỗi xóa: " + e.getMessage()));
    }

    // ===== Helpers =====
    private static long tsToLong(@Nullable Timestamp ts) {
        return ts != null ? ts.toDate().getTime() : 0L;
    }

    @Nullable
    private static Timestamp firstNonNullCancelTs(@NonNull Order o) {
        if (o.getCancelledAt() != null) return o.getCancelledAt(); // British
        // Nếu model có thêm field khác, có thể bổ sung getter getCanceledAt()
        try {
            // phản xạ nhẹ nếu bạn có cả 2 field
            java.lang.reflect.Method m = o.getClass().getMethod("getCanceledAt");
            Object v = m.invoke(o);
            if (v instanceof Timestamp) return (Timestamp) v;
        } catch (Exception ignore) {}
        return null;
    }

    private void showLoading(boolean s) { loadingState.setVisibility(s ? View.VISIBLE : View.GONE); }
    private void showEmpty() { emptyState.setVisibility(View.VISIBLE); recyclerView.setVisibility(View.GONE); }
    private void showList() { emptyState.setVisibility(View.GONE); recyclerView.setVisibility(View.VISIBLE); }

    private void toast(@NonNull String m) { if (!isAdded()) return; Toast.makeText(getContext(), m, Toast.LENGTH_SHORT).show(); }
    private void toastLong(@NonNull String m) { if (!isAdded()) return; Toast.makeText(getContext(), m, Toast.LENGTH_LONG).show(); }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (registration != null) { registration.remove(); registration = null; }
    }
}
