package com.pro.milkteaapp.adapters;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.android.material.button.MaterialButton;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.utils.MoneyUtils;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OrdersAdapter extends RecyclerView.Adapter<OrdersAdapter.VH> {

    public interface OnCancelClickListener {
        void onCancel(@NonNull String orderId);
    }

    private final List<Map<String, Object>> data;
    private final boolean cancellable;
    private final OnCancelClickListener cancelListener;
    private final SimpleDateFormat df = new SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault());

    public OrdersAdapter(@NonNull List<Map<String, Object>> data,
                         boolean cancellable,
                         OnCancelClickListener cancelListener) {
        this.data = data;
        this.cancellable = cancellable;
        this.cancelListener = cancelListener;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        String id = s(data.get(position).get("_id"));
        return id.hashCode();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_orders, parent, false);
        return new VH(v);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Map<String, Object> m = data.get(position);

        String id = s(m.get("_id"));
        String shortId = id.length() > 6 ? id.substring(0, 6) : id;
        String status = s(m.get("status"));
        double total = toDouble(m.get("total"));

        String timeText = "";
        Object ts = m.get("createdAt");
        if (ts instanceof Timestamp) {
            timeText = df.format(((Timestamp) ts).toDate());
        }

        h.tvOrderId.setText("Đơn #" + shortId);
        h.tvOrderStatus.setText(status.isEmpty() ? "PENDING" : status);
        h.tvOrderTotal.setText(MoneyUtils.formatVnd(total));
        h.tvOrderTime.setText(timeText);

        // Chỉ hiển thị nút Hủy nếu đơn PENDING và adapter được cho phép
        boolean canShowCancel = cancellable && "PENDING".equalsIgnoreCase(status);
        h.btnCancel.setVisibility(canShowCancel ? View.VISIBLE : View.GONE);
        h.btnCancel.setOnClickListener(v -> {
            if (cancelListener != null) cancelListener.onCancel(id);
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    private static String s(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static double toDouble(Object o) {
        if (o == null) return 0d;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (Exception e) {
            return 0d;
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvOrderId, tvOrderStatus, tvOrderTotal, tvOrderTime;
        MaterialButton btnCancel;

        VH(@NonNull View itemView) {
            super(itemView);
            tvOrderId = itemView.findViewById(R.id.tvOrderId);
            tvOrderStatus = itemView.findViewById(R.id.tvOrderStatus);
            tvOrderTotal = itemView.findViewById(R.id.tvOrderTotal);
            tvOrderTime = itemView.findViewById(R.id.tvOrderTime);
            btnCancel = itemView.findViewById(R.id.btnCancel);
        }
    }
}
