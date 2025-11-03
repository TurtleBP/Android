package com.pro.milkteaapp.adapter.admin;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.models.Order;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Objects;

public class AdminOrdersAdapter extends ListAdapter<Order, AdminOrdersAdapter.VH> {

    // ===== Interface callbacks =====
    public interface OnItemClick { void click(@NonNull Order o); }
    public interface OnConfirm { void confirm(Order o); }
    public interface OnCancel  { void cancel(Order o); }
    public interface OnDelete  { void delete(Order o); }

    private final OnItemClick onItemClick;
    private final OnConfirm onConfirm;
    private final OnCancel  onCancel;
    private final OnDelete  onDelete;

    // ===== Control buttons visibility =====
    private boolean showConfirm = true;   // Pending
    private boolean showCancel  = true;   // Pending
    private boolean showDelete  = false;  // Cancelled

    @SuppressLint("NotifyDataSetChanged")
    public void setShowConfirm(boolean show) { this.showConfirm = show; notifyDataSetChanged(); }
    @SuppressLint("NotifyDataSetChanged")
    public void setShowCancel(boolean show)  { this.showCancel  = show; notifyDataSetChanged(); }
    @SuppressLint("NotifyDataSetChanged")
    public void setShowDelete(boolean show)  { this.showDelete  = show; notifyDataSetChanged(); }

    // ===== DiffUtil =====
    private static final DiffUtil.ItemCallback<Order> DIFF = new DiffUtil.ItemCallback<>() {
        @Override public boolean areItemsTheSame(@NonNull Order a, @NonNull Order b) {
            return Objects.equals(a.getId(), b.getId());
        }
        @SuppressLint("DiffUtilEquals")
        @Override public boolean areContentsTheSame(@NonNull Order a, @NonNull Order b) {
            return a.equals(b);
        }
    };

    // ===== Constructor =====
    public AdminOrdersAdapter(@NonNull OnItemClick onItemClick,
                              @NonNull OnConfirm onConfirm,
                              @NonNull OnCancel onCancel,
                              @NonNull OnDelete onDelete) {
        super(DIFF);
        this.onItemClick = onItemClick;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
        this.onDelete = onDelete;
        setHasStableIds(true);
    }

    @Override public long getItemId(int position) {
        Order o = getItem(position);
        return o != null ? o.getId().hashCode() : RecyclerView.NO_ID;
    }

    // ===== ViewHolder =====
    public static class VH extends RecyclerView.ViewHolder {
        TextView tvId, tvTotal, tvUser, tvTime, tvStatus;
        MaterialButton btnConfirm, btnCancel, btnDelete;

        VH(@NonNull View itemView) {
            super(itemView);
            tvId = itemView.findViewById(R.id.tvOrderId);
            tvTotal = itemView.findViewById(R.id.tvOrderTotal);
            tvUser = itemView.findViewById(R.id.tvOrderUser);
            tvTime = itemView.findViewById(R.id.tvOrderTime);
            tvStatus = itemView.findViewById(R.id.tvOrderStatus);
            btnConfirm = itemView.findViewById(R.id.btnConfirm);
            btnCancel  = itemView.findViewById(R.id.btnCancel);
            btnDelete  = itemView.findViewById(R.id.btnDelete);
        }

        @SuppressLint("SetTextI18n")
        void bind(Order o,
                  boolean showConfirm, boolean showCancel, boolean showDelete,
                  OnConfirm onConfirm, OnCancel onCancel, OnDelete onDelete) {

            btnConfirm.setVisibility(showConfirm ? View.VISIBLE : View.GONE);
            btnCancel.setVisibility(showCancel ? View.VISIBLE : View.GONE);
            btnDelete.setVisibility(showDelete ? View.VISIBLE : View.GONE);

            tvId.setText("#" + o.getId());
            tvTotal.setText(String.format(Locale.getDefault(), "%,.0f₫", o.getTotal()));
            tvUser.setText(o.getUserId());
            tvStatus.setText(o.getStatus());
            tvTime.setText(formatTime(o.getCreatedAt()));

            // ==== Set màu status theo trạng thái ====
            String s = (o.getStatus() == null) ? "" : o.getStatus().toUpperCase(Locale.ROOT);
            int bg;
            if ("FINISHED".equals(s)) {
                bg = R.drawable.bg_status_pill_finished;
            } else if ("CANCELLED".equals(s) || "CANCELED".equals(s)) {
                bg = R.drawable.bg_status_pill_cancelled;
            } else {
                bg = R.drawable.bg_status_pill_pending;
            }
            tvStatus.setBackground(ContextCompat.getDrawable(itemView.getContext(), bg));
            tvStatus.setTextColor(ContextCompat.getColor(itemView.getContext(), android.R.color.white));
            // ========================================

            btnConfirm.setOnClickListener(v -> onConfirm.confirm(o));
            btnCancel.setOnClickListener(v -> onCancel.cancel(o));
            btnDelete.setOnClickListener(v -> onDelete.delete(o));
        }

        private String formatTime(Timestamp ts) {
            if (ts == null) return "";
            return new SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault()).format(ts.toDate());
        }
    }

    // ===== Adapter overrides =====
    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_order, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Order o = getItem(position);
        holder.bind(o, showConfirm, showCancel, showDelete, onConfirm, onCancel, onDelete);

        // ==== click toàn bộ item để xem chi tiết ====
        holder.itemView.setOnClickListener(v -> {
            if (onItemClick != null) onItemClick.click(o);
        });
    }

    @Override
    public void onViewRecycled(@NonNull VH holder) {
        super.onViewRecycled(holder);
        holder.btnConfirm.setVisibility(View.GONE);
        holder.btnCancel.setVisibility(View.GONE);
        holder.btnDelete.setVisibility(View.GONE);
    }
}
