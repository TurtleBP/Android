package com.pro.milkteaapp.fragment;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.models.Order;

import java.text.SimpleDateFormat;
import java.util.*;

public class OrdersListFragment extends Fragment {

    private static final String ARG_STATUS = "arg_status";

    public static OrdersListFragment newInstance(String status) {
        Bundle b = new Bundle();
        b.putString(ARG_STATUS, status);
        OrdersListFragment f = new OrdersListFragment();
        f.setArguments(b);
        return f;
    }

    private String status;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recyclerView;
    private View emptyState, loadingState;
    private ListenerRegistration registration;
    private OrdersAdapter adapter;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_pending_orders, container, false);
        // Có thể dùng chung layout fragment_finished_orders, 2 file giống nhau; ở đây tái sử dụng 1 file
        swipeRefresh = v.findViewById(R.id.swipeRefresh);
        recyclerView = v.findViewById(R.id.rvItems);
        emptyState = v.findViewById(R.id.emptyState);
        loadingState = v.findViewById(R.id.loadingState);

        status = getArguments() != null ? getArguments().getString(ARG_STATUS, "PENDING") : "PENDING";

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new OrdersAdapter();
        recyclerView.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(this::loadData);
        loadData();
        return v;
    }

    private void loadData() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { showEmpty(); return; }

        showLoading(true);
        Query q = FirebaseFirestore.getInstance().collection("orders")
                .whereEqualTo("userId", user.getUid())
                .whereEqualTo("status", status)
                .orderBy("createdAt", Query.Direction.DESCENDING);

        if (registration != null) registration.remove();
        registration = q.addSnapshotListener((snap, err) -> {
            swipeRefresh.setRefreshing(false);
            showLoading(false);
            if (err != null) {
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
            adapter.setIsPendingTab("PENDING".equals(status));
            adapter.submitList(data);
            if (data.isEmpty()) showEmpty(); else showList();
        });
    }

    private void showLoading(boolean s) { loadingState.setVisibility(s ? View.VISIBLE : View.GONE); }
    private void showEmpty() { emptyState.setVisibility(View.VISIBLE); recyclerView.setVisibility(View.GONE); }
    private void showList() { emptyState.setVisibility(View.GONE); recyclerView.setVisibility(View.VISIBLE); }

    @Override public void onDestroyView() {
        super.onDestroyView();
        if (registration != null) registration.remove();
    }

    /** Adapter hiển thị đơn + Hủy đơn (chỉ ở tab Pending) */
    static class OrdersAdapter extends ListAdapter<Order, OrdersAdapter.VH> {
        private boolean isPendingTab = false;
        @SuppressLint("NotifyDataSetChanged")
        void setIsPendingTab(boolean b) { isPendingTab = b; notifyDataSetChanged(); }

        static final DiffUtil.ItemCallback<Order> DIFF = new DiffUtil.ItemCallback<>() {
            @Override
            public boolean areItemsTheSame(@NonNull Order a, @NonNull Order b) {
                return Objects.equals(a.getId(), b.getId());
            }

            @Override
            public boolean areContentsTheSame(@NonNull Order a, @NonNull Order b) {
                return a.equals(b);
            }
        };

        OrdersAdapter() { super(DIFF); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvOrderId, tvOrderStatus, tvOrderTotal, tvOrderTime;
            MaterialButton btnCancel;

            VH(@NonNull View itemView) {
                super(itemView);
                tvOrderId     = itemView.findViewById(R.id.tvOrderId);
                tvOrderStatus = itemView.findViewById(R.id.tvOrderStatus);
                tvOrderTotal  = itemView.findViewById(R.id.tvOrderTotal);
                tvOrderTime   = itemView.findViewById(R.id.tvOrderTime);
                btnCancel     = itemView.findViewById(R.id.btnCancel);
            }

            @SuppressLint("SetTextI18n")
            void bind(Order o, boolean isPendingTab) {
                tvOrderId.setText("#" + o.getId());
                tvOrderStatus.setText(o.getStatus());
                tvOrderTotal.setText(String.format(Locale.getDefault(), "%,.0f₫", o.getTotal()));
                tvOrderTime.setText(format(o.getCreatedAt()));

                btnCancel.setVisibility(isPendingTab ? View.VISIBLE : View.GONE);
                btnCancel.setOnClickListener(v -> cancelOrder(o, itemView));
            }

            private String format(Timestamp ts) {
                if (ts == null) return "";
                return new SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault()).format(ts.toDate());
            }

            private void cancelOrder(Order o, View itemView) {
                FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
                if (u == null) return;
                Map<String, Object> updates = new HashMap<>();
                updates.put("status", "CANCELLED");
                updates.put("canceledAt", FieldValue.serverTimestamp());
                updates.put("canceledBy", u.getUid());
                FirebaseFirestore.getInstance().collection("orders")
                        .document(o.getId())
                        .update(updates)
                        .addOnSuccessListener(s ->
                                Toast.makeText(itemView.getContext(), "Đã hủy đơn #" + o.getId(), Toast.LENGTH_SHORT).show())
                        .addOnFailureListener(e ->
                                Toast.makeText(itemView.getContext(), "Lỗi hủy: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_orders, parent, false);
            return new VH(v);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int pos) { h.bind(getItem(pos), isPendingTab); }
    }
}
