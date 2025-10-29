package com.pro.milkteaapp.adapter;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.models.Order;
import com.pro.milkteaapp.utils.MoneyUtils;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class OrdersAdapter extends RecyclerView.Adapter<OrdersAdapter.VH> {

    public interface Listener {
        void onOrderClick(@NonNull Order order);
    }

    private final List<Order> data = new ArrayList<>();
    private final Listener listener;

    public OrdersAdapter(Listener listener) { this.listener = listener; }

    @SuppressLint("NotifyDataSetChanged")
    public void submitList(@NonNull List<Order> items) {
        data.clear();
        data.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_orders, parent, false); // ✅ đảm bảo file tồn tại
        return new VH(v);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Order o = data.get(position);

        h.tvOrderId.setText("#" + o.getId());

        // Thời gian tạo
        DateFormat df = android.text.format.DateFormat.getMediumDateFormat(h.itemView.getContext());
        DateFormat tf = android.text.format.DateFormat.getTimeFormat(h.itemView.getContext());
        String createdStr = "";
        if (o.getCreatedAt() != null) {
            Date d = o.getCreatedAt().toDate();
            createdStr = df.format(d) + " " + tf.format(d);
        }
        h.tvCreatedAt.setText(createdStr.isEmpty() ? "—" : createdStr);

        h.tvTotal.setText(MoneyUtils.formatVnd(o.getFinalTotal()));

        // Người nhận + SĐT
        String receiver = o.getReceiverName();
        String phone = o.getReceiverPhone();
        if (!receiver.isEmpty() || !phone.isEmpty()) {
            h.tvReceiver.setText((receiver.isEmpty() ? "" : receiver) +
                    (!phone.isEmpty() ? " • " + phone : ""));
            h.tvReceiver.setVisibility(View.VISIBLE);
        } else {
            h.tvReceiver.setVisibility(View.GONE);
        }

        // Địa chỉ ngắn
        h.tvAddress.setText(o.getAddressDisplay());

        // Chip trạng thái
        String st = o.getStatus() == null ? "" : o.getStatus();
        h.chipStatus.setText(mapStatusText(st));
        // Màu nền chip: dùng android holo* cho chắc có
        h.chipStatus.setChipBackgroundColorResource(mapStatusColor(st));
        h.chipStatus.setTextColor(h.itemView.getResources().getColor(android.R.color.white));

        h.card.setOnClickListener(v -> { if (listener != null) listener.onOrderClick(o); });
    }

    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        MaterialCardView card;
        TextView tvOrderId, tvCreatedAt, tvTotal, tvReceiver, tvAddress;
        Chip chipStatus;

        VH(@NonNull View v) {
            super(v);
            card = (MaterialCardView) v;
            tvOrderId = v.findViewById(R.id.tvOrderId);
            tvCreatedAt = v.findViewById(R.id.tvCreatedAt);
            tvTotal = v.findViewById(R.id.tvTotal);
            tvReceiver = v.findViewById(R.id.tvReceiver);
            tvAddress = v.findViewById(R.id.tvAddress);
            chipStatus = v.findViewById(R.id.chipStatus);
        }
    }

    private static String mapStatusText(String s) {
        return switch (s) {
            case Order.STATUS_PENDING -> "Chờ xác nhận";
            case Order.STATUS_FINISHED -> "Hoàn tất";
            case Order.STATUS_CANCELLED -> "Đã hủy";
            default -> s.isEmpty() ? "Không rõ" : s;
        };
    }

    private static int mapStatusColor(String s) {
        // Tránh phụ thuộc Material Dynamic (không phải máy nào cũng có)
        return switch (s) {
            case Order.STATUS_FINISHED ->
                    android.R.color.holo_green_dark;  // ✅ thay material_dynamic_green60
            case Order.STATUS_CANCELLED ->
                    android.R.color.holo_red_dark;    // ✅ thay material_dynamic_red60
            default -> android.R.color.darker_gray;
        };
    }
}
