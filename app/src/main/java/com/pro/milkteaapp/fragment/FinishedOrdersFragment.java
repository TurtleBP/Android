package com.pro.milkteaapp.fragment;

import android.annotation.SuppressLint;
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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.adapters.OrdersAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FinishedOrdersFragment extends Fragment {

    private static final String STATUS_FINISHED = "FINISHED";

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
                /* cancellable = */ false,
                /* cancelListener = */ null
        );
        recyclerView.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(() -> swipeRefresh.setRefreshing(false));

        startListening();
        return v;
    }

    @SuppressLint("NotifyDataSetChanged")
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

        stopListening();
        registration = FirebaseFirestore.getInstance()
                .collection("orders")
                .whereEqualTo("userId", uid)
                .whereEqualTo("status", STATUS_FINISHED)
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
