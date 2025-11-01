package com.pro.milkteaapp.fragment.admin;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.activity.admin.AdminOrderDetailActivity;
import com.pro.milkteaapp.adapter.admin.AdminOrdersAdapter;
import com.pro.milkteaapp.models.Order;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

                boolean isPending   = "PENDING".equalsIgnoreCase(currentStatus);
                boolean isCancelled = "CANCELLED".equalsIgnoreCase(currentStatus);

                adapter.setShowConfirm(isPending);
                adapter.setShowCancel(isPending);
                adapter.setShowDelete(isCancelled);

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
                this::onConfirmClicked,
                this::onCancelClicked,
                this::onDeleteClicked
        );
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
        Query q = db.collection("orders")
                .whereEqualTo("status", currentStatus)
                .orderBy("createdAt", Query.Direction.DESCENDING);

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

            boolean isPending   = "PENDING".equalsIgnoreCase(currentStatus);
            boolean isCancelled = "CANCELLED".equalsIgnoreCase(currentStatus);

            adapter.setShowConfirm(isPending);
            adapter.setShowCancel(isPending);
            adapter.setShowDelete(isCancelled);

            adapter.submitList(data, () -> {
                showLoading(false);
                if (data.isEmpty()) showEmpty(); else showList();
                recyclerView.setVisibility(View.VISIBLE);
            });
        });
    }

    /** Xác nhận → FINISHED */
    private void onConfirmClicked(@NonNull Order order) {
        if (!"PENDING".equalsIgnoreCase(currentStatus)) return;

        FirebaseUser admin = FirebaseAuth.getInstance().getCurrentUser();
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "FINISHED");
        updates.put("confirmedAt", FieldValue.serverTimestamp());
        updates.put("finishedAt",  FieldValue.serverTimestamp());
        updates.put("confirmedBy", admin != null ? admin.getEmail() : null);

        FirebaseFirestore.getInstance().collection("orders")
                .document(order.getId())
                .update(updates)
                .addOnSuccessListener(s -> {
                    toast("✅ Đã xác nhận đơn #" + order.getId());
                    writeInbox(order.getUserId(), "order_done",
                            "Đơn hàng với mã " + order.getId() + " đã hoàn thành",
                            order.getId(), null);
                })
                .addOnFailureListener(e -> toastLong("❌ Lỗi xác nhận: " + e.getMessage()));
    }

    /** Huỷ → CANCELLED (ghi cả 2 dạng tên field) */
    private void onCancelClicked(@NonNull Order order) {
        if (!"PENDING".equalsIgnoreCase(currentStatus)) return;

        final TextInputEditText input = new TextInputEditText(requireContext());
        input.setHint("Lý do huỷ (tuỳ chọn)");

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Huỷ đơn #" + order.getId())
                .setMessage("Bạn có chắc muốn huỷ đơn này?")
                .setView(input)
                .setNegativeButton("Không", (d, w) -> d.dismiss())
                .setPositiveButton("Huỷ đơn", (d, w) -> {
                    String reason = input.getText() != null ? input.getText().toString().trim() : null;
                    FirebaseUser admin = FirebaseAuth.getInstance().getCurrentUser();

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("status", "CANCELLED");

                    // dạng bạn đang dùng trong fragment
                    updates.put("cancelledAt", FieldValue.serverTimestamp());
                    updates.put("cancelledBy", admin != null ? admin.getEmail() : null);
                    if (!TextUtils.isEmpty(reason)) updates.put("cancelledReason", reason);

                    // dạng màn hình user có thể đang đọc
                    updates.put("canceledAt", FieldValue.serverTimestamp());
                    if (!TextUtils.isEmpty(reason)) updates.put("cancelReason", reason);

                    FirebaseFirestore.getInstance().collection("orders")
                            .document(order.getId())
                            .update(updates)
                            .addOnSuccessListener(s -> {
                                toast("🚫 Đã huỷ đơn #" + order.getId());
                                writeInbox(
                                        order.getUserId(),
                                        "order_cancelled",
                                        "Đơn hàng với mã " + order.getId() + " đã bị huỷ"
                                                + (TextUtils.isEmpty(reason) ? "" : (": " + reason)),
                                        order.getId(),
                                        reason
                                );
                            })
                            .addOnFailureListener(e -> toastLong("❌ Lỗi huỷ: " + e.getMessage()));
                })
                .show();
    }

    /** Xoá hẳn record (tab Cancelled) */
    private void onDeleteClicked(@NonNull Order order) {
        FirebaseFirestore.getInstance().collection("orders")
                .document(order.getId())
                .delete()
                .addOnSuccessListener(s -> toast("🗑️ Đã xóa đơn #" + order.getId()))
                .addOnFailureListener(e -> toastLong("❌ Lỗi xóa: " + e.getMessage()));
    }

    private void writeInbox(String userId, String type, String message, String orderId, @Nullable String reason) {
        if (userId == null || userId.isEmpty()) return;
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", type);
        msg.put("orderId", orderId);
        msg.put("message", message);
        if (!TextUtils.isEmpty(reason)) msg.put("reason", reason);
        msg.put("createdAt", FieldValue.serverTimestamp());
        msg.put("read", false);

        FirebaseFirestore.getInstance()
                .collection("users").document(userId)
                .collection("inbox")
                .add(msg)
                .addOnFailureListener(e -> toastLong("❗Không thể ghi tin nhắn: " + e.getMessage()));
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
