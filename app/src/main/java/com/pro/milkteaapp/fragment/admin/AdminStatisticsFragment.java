package com.pro.milkteaapp.fragment.admin;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.firebase.firestore.*;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.activity.admin.AdminMainActivity;
import com.pro.milkteaapp.adapter.admin.AdminStatisticAdapter;
import com.pro.milkteaapp.models.Order;

import java.text.NumberFormat;
import java.util.*;

/**
 * Fragment thống kê dành cho Admin
 * - Cho phép lọc theo ngày / tuần / tháng / tất cả
 * - Cho phép lọc theo người dùng
 * - Tính doanh thu chỉ từ đơn FINISHED
 */
public class AdminStatisticsFragment extends Fragment
        implements AdminMainActivity.ScrollToTop, AdminMainActivity.SupportsRefresh {

    private SwipeRefreshLayout swipe;
    private RecyclerView recyclerView;
    private View loading, empty;
    private Spinner spinnerTime, spinnerUser;

    private AdminStatisticAdapter adapter;
    private final List<String> data = new ArrayList<>();

    private TextView kpi1Title, kpi1Value;
    private TextView kpi2Title, kpi2Value;
    private TextView kpi3Title, kpi3Value;
    private TextView kpi4Title, kpi4Value;

    private FirebaseFirestore db;
    private final List<String> userIds = new ArrayList<>();
    private String selectedUserId = "ALL";
    private int selectedTimeType = 0; // 0=day, 1=week, 2=month, 3=all

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_admin_statistics, container, false);

        db = FirebaseFirestore.getInstance();
        swipe = v.findViewById(R.id.swipeRefresh);
        recyclerView = v.findViewById(R.id.recycler);
        loading = v.findViewById(R.id.loadingState);
        empty = v.findViewById(R.id.emptyState);
        spinnerTime = v.findViewById(R.id.spinnerTime);
        spinnerUser = v.findViewById(R.id.spinnerUser);

        // KPI binding
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

        // Recycler setup
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.addItemDecoration(new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));
        adapter = new AdminStatisticAdapter();
        recyclerView.setAdapter(adapter);

        // Spinner thời gian
        ArrayAdapter<String> timeAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                Arrays.asList("Hôm nay", "Tuần này", "Tháng này", "Tất cả"));
        spinnerTime.setAdapter(timeAdapter);
        spinnerTime.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedTimeType = position;
                refresh();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Spinner người dùng
        loadUsers();

        swipe.setOnRefreshListener(this::refresh);
        showLoading(true);
        refresh();

        return v;
    }

    /** Load danh sách user */
    private void loadUsers() {
        db.collection("users").get().addOnSuccessListener(snap -> {
            List<String> names = new ArrayList<>();
            userIds.clear();
            names.add("Tất cả người dùng");
            userIds.add("ALL");
            for (DocumentSnapshot doc : snap.getDocuments()) {
                String name = doc.getString("name");
                names.add(name != null ? name : doc.getId());
                userIds.add(doc.getId());
            }

            ArrayAdapter<String> userAdapter = new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_spinner_dropdown_item, names);
            spinnerUser.setAdapter(userAdapter);
            spinnerUser.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                    selectedUserId = userIds.get(pos);
                    refresh();
                }
                @Override public void onNothingSelected(AdapterView<?> parent) {}
            });
        });
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

        Date startDate = getStartDate(selectedTimeType);
        Date now = new Date();

        Query query = db.collection("orders")
                .whereGreaterThanOrEqualTo("createdAt", startDate)
                .whereLessThanOrEqualTo("createdAt", now);

        if (!"ALL".equals(selectedUserId)) {
            query = query.whereEqualTo("userId", selectedUserId);
        }

        query.get().addOnSuccessListener(snap -> {
            double totalRevenue = 0;
            int totalOrders = 0;
            int finishedOrders = 0;
            int cancelledOrders = 0;

            for (DocumentSnapshot doc : snap.getDocuments()) {
                Order order = doc.toObject(Order.class);
                if (order == null) continue;
                totalOrders++;

                String status = order.getStatus();
                if ("CANCELLED".equals(status)) cancelledOrders++;
                else if ("FINISHED".equals(status)) {
                    finishedOrders++;
                    totalRevenue += order.getTotal();
                }
            }

            double cancelRate = (totalOrders == 0) ? 0 : (cancelledOrders * 100.0 / totalOrders);
            double avgPerOrder = (finishedOrders == 0) ? 0 : (totalRevenue / finishedOrders);

            NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
            kpi1Title.setText("Doanh thu");
            kpi1Value.setText(nf.format(totalRevenue));
            kpi2Title.setText("Đơn hoàn thành");
            kpi2Value.setText(String.valueOf(finishedOrders));
            kpi3Title.setText("Tỉ lệ huỷ đơn");
            kpi3Value.setText(String.format(Locale.getDefault(), "%.1f%%", cancelRate));
            kpi4Title.setText("TB/đơn hoàn thành");
            kpi4Value.setText(nf.format(avgPerOrder));

            data.clear();
            data.add("Khoảng thời gian: " + getTimeLabel(selectedTimeType));
            data.add("Tổng đơn: " + totalOrders);
            data.add("Hoàn thành: " + finishedOrders);
            data.add("Huỷ: " + cancelledOrders);
            data.add("Doanh thu: " + nf.format(totalRevenue));
            data.add("TB/đơn hoàn thành: " + nf.format(avgPerOrder));

            adapter.setItems(data);

            swipe.setRefreshing(false);
            showLoading(false);
            toggleEmpty(data.isEmpty());
        }).addOnFailureListener(e -> {
            swipe.setRefreshing(false);
            showLoading(false);
            toggleEmpty(true);
            Toast.makeText(getContext(), "Lỗi tải dữ liệu: " + e.getMessage(), Toast.LENGTH_LONG).show();
        });
    }

    /** Xác định thời gian bắt đầu */
    private Date getStartDate(int type) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        switch (type) {
            case 1: cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek()); break; // Tuần này
            case 2: cal.set(Calendar.DAY_OF_MONTH, 1); break; // Tháng này
            case 3: cal.setTimeInMillis(0); break; // Tất cả
        }
        return cal.getTime();
    }

    private String getTimeLabel(int type) {
        switch (type) {
            case 0: return "Hôm nay";
            case 1: return "Tuần này";
            case 2: return "Tháng này";
            default: return "Tất cả thời gian";
        }
    }

    private void showLoading(boolean show) {
        loading.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void toggleEmpty(boolean isEmpty) {
        empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
    }
}
