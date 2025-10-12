package com.pro.milkteaapp.fragment;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.adapters.OrdersAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PendingOrdersFragment extends Fragment {

    private static final String STATUS_PENDING = "PENDING";

    private SwipeRefreshLayout swipeRefresh;
    private androidx.recyclerview.widget.RecyclerView recyclerView;
    private View progress;
    private View emptyView;

    private final List<Map<String, Object>> data = new ArrayList<>();
    private OrdersAdapter adapter;

    private ListenerRegistration registration;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_orders_list, container, false);

        swipeRefresh = v.findViewById(R.id.swipeRefresh);
        recyclerView = v.findViewById(R.id.recyclerView);
        progress     = v.findViewById(R.id.progressBar);
        emptyView    = v.findViewById(R.id.emptyView);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new OrdersAdapter(
                data,
                /* cancellable = */ true,
                this::confirmCancel
        );
        recyclerView.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(() -> {
            // đã realtime nên chỉ tắt hiệu ứng
            swipeRefresh.setRefreshing(false);
        });

        startListening();
        return v;
    }

    private void startListening() {
        setLoading(true);

        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

        if (TextUtils.isEmpty(uid)) {
            setLoading(false);
            toggleEmpty(true);
            Toast.makeText(requireContext(), "Vui lòng đăng nhập.", Toast.LENGTH_SHORT).show();
            return;
        }

        stopListening(); // đảm bảo không nhân đôi listener
        registration = FirebaseFirestore.getInstance()
                .collection("orders")
                .whereEqualTo("userId", uid)
                .whereEqualTo("status", STATUS_PENDING)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, e) -> {
                    setLoading(false);
                    if (!isAdded()) return;

                    if (e != null) {
                        toggleEmpty(true);
                        Toast.makeText(requireContext(), "Lỗi tải dữ liệu: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    data.clear();
                    if (snap != null) {
                        snap.getDocuments().forEach(d -> {
                            Map<String, Object> m = d.getData();
                            if (m != null) {
                                m.put("_id", d.getId());
                                data.add(m);
                            }
                        });
                    }
                    adapter.notifyDataSetChanged();
                    toggleEmpty(data.isEmpty());
                });
    }

    private void stopListening() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }

    // ====== Hủy đơn ======
    private void confirmCancel(@NonNull String orderId) {
        final EditText input = new EditText(requireContext());
        input.setHint("Lý do hủy (tuỳ chọn)");

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Hủy đơn hàng")
                .setMessage("Bạn có chắc muốn hủy đơn này?")
                .setView(input)
                .setPositiveButton("Hủy đơn", (d, w) -> {
                    String reason = input.getText() == null ? "" : input.getText().toString().trim();
                    performCancel(orderId, reason);
                })
                .setNegativeButton("Đóng", null)
                .show();
    }

    private void performCancel(@NonNull String docId, @NonNull String reason) {
        setLoading(true);
        FirebaseFirestore.getInstance()
                .collection("orders")
                .document(docId)
                .update(
                        "status", "CANCELLED",
                        "cancelledAt", FieldValue.serverTimestamp(),
                        "cancelReason", reason
                )
                .addOnSuccessListener(v -> Toast.makeText(requireContext(), "Đã hủy đơn", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(), "Lỗi hủy đơn: " + e.getMessage(), Toast.LENGTH_LONG).show())
                .addOnCompleteListener(t -> setLoading(false));
    }

    private void setLoading(boolean show) {
        if (progress != null) progress.setVisibility(show ? View.VISIBLE : View.GONE);
        if (recyclerView != null) recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
        if (emptyView != null && show) emptyView.setVisibility(View.GONE);
    }

    private void toggleEmpty(boolean empty) {
        if (emptyView != null) emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (recyclerView != null) recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        stopListening();
        super.onDestroyView();
    }
}
