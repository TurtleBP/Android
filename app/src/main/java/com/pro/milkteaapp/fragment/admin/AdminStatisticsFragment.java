package com.pro.milkteaapp.fragment.admin;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.activity.admin.AdminMainActivity;
import com.pro.milkteaapp.models.Order;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AdminStatisticsFragment extends Fragment
        implements AdminMainActivity.ScrollToTop, AdminMainActivity.SupportsRefresh {

    private SwipeRefreshLayout swipe;
    private RecyclerView recyclerView;
    private View loading, empty;
    private SimpleStringAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<String> data = new ArrayList<>();

    // KPI views (4 cái)
    private TextView kpi1Title, kpi1Value;
    private TextView kpi2Title, kpi2Value;
    private TextView kpi3Title, kpi3Value;
    private TextView kpi4Title, kpi4Value;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.activity_admin_statistics, container, false);
        swipe = v.findViewById(R.id.swipeRefresh);
        recyclerView = v.findViewById(R.id.recycler);
        loading = v.findViewById(R.id.loadingState);
        empty = v.findViewById(R.id.emptyState);

        // Bind KPI (lấy 4 include trong GridLayout theo thứ tự)
        GridLayout grid = v.findViewById(R.id.kpiGrid);
        View k1 = grid.getChildAt(0);
        View k2 = grid.getChildAt(1);
        View k3 = grid.getChildAt(2);
        View k4 = grid.getChildAt(3);

        kpi1Title = k1.findViewById(R.id.tvKpiTitle);
        kpi1Value = k1.findViewById(R.id.tvKpiValue);
        kpi2Title = k2.findViewById(R.id.tvKpiTitle);
        kpi2Value = k2.findViewById(R.id.tvKpiValue);
        kpi3Title = k3.findViewById(R.id.tvKpiTitle);
        kpi3Value = k3.findViewById(R.id.tvKpiValue);
        kpi4Title = k4.findViewById(R.id.tvKpiTitle);
        kpi4Value = k4.findViewById(R.id.tvKpiValue);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.addItemDecoration(new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));
        adapter = new SimpleStringAdapter();
        recyclerView.setAdapter(adapter);

        swipe.setOnRefreshListener(this::refresh);

        // Nạp dữ liệu lần đầu
        showLoading(true);
        refresh();

        return v;
    }

    @Override
    public void scrollToTop() {
        if (recyclerView != null) recyclerView.smoothScrollToPosition(0);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void refresh() {
        swipe.setRefreshing(true);
        showLoading(true);

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Lấy các đơn trong ngày hôm nay (0h → 23h59)
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date startOfDay = cal.getTime();

        Date now = new Date();

        db.collection("orders")
                .whereGreaterThanOrEqualTo("createdAt", startOfDay)
                .whereLessThanOrEqualTo("createdAt", now)
                .get()
                .addOnSuccessListener(snap -> {
                    double totalRevenue = 0;
                    int totalOrders = 0;
                    int cancelledOrders = 0;

                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        Order order = doc.toObject(Order.class);
                        if (order == null) continue;

                        totalOrders++;
                        if ("CANCELLED".equals(order.getStatus())) cancelledOrders++;
                        if ("FINISHED".equals(order.getStatus())) totalRevenue += order.getTotal();
                    }

                    double cancelRate = (totalOrders == 0) ? 0 : (cancelledOrders * 100.0 / totalOrders);
                    double avgPerOrder = (totalOrders == 0) ? 0 : (totalRevenue / totalOrders);

                    // Hiển thị KPI
                    NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
                    kpi1Title.setText("Doanh thu hôm nay");
                    kpi1Value.setText(nf.format(totalRevenue));

                    kpi2Title.setText("Số đơn hôm nay");
                    kpi2Value.setText(String.valueOf(totalOrders));

                    kpi3Title.setText("Tỉ lệ huỷ đơn");
                    kpi3Value.setText(String.format(Locale.getDefault(), "%.1f%%", cancelRate));

                    kpi4Title.setText("Trung bình/đơn");
                    kpi4Value.setText(nf.format(avgPerOrder));

                    // Hiển thị danh sách mô tả tóm tắt
                    data.clear();
                    data.add("Đơn hoàn thành: " + (totalOrders - cancelledOrders));
                    data.add("Đơn bị huỷ: " + cancelledOrders);
                    data.add("Tổng doanh thu: " + nf.format(totalRevenue));
                    data.add("Trung bình/đơn: " + nf.format(avgPerOrder));

                    adapter.setItems(data);

                    swipe.setRefreshing(false);
                    showLoading(false);
                    toggleEmpty(data.isEmpty());
                })
                .addOnFailureListener(e -> {
                    swipe.setRefreshing(false);
                    showLoading(false);
                    toggleEmpty(true);
                    Toast.makeText(getContext(), "Lỗi tải dữ liệu: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }


    private void showLoading(boolean show) {
        loading.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void toggleEmpty(boolean isEmpty) {
        empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
    }

    // ===== Adapter đơn giản =====
    private static class SimpleStringAdapter extends RecyclerView.Adapter<SimpleViewHolder> {
        private final List<String> items = new ArrayList<>();
        @SuppressLint("NotifyDataSetChanged")
        void setItems(List<String> newItems) {
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
        }
        @NonNull @Override
        public SimpleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View itemView = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_row_simple, parent, false);
            return new SimpleViewHolder(itemView);
        }
        @Override public void onBindViewHolder(@NonNull SimpleViewHolder holder, int position) {
            holder.bind(items.get(position));
        }
        @Override public int getItemCount() { return items.size(); }
    }
}
