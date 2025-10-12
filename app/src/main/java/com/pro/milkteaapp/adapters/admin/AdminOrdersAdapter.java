package com.pro.milkteaapp.adapters.admin;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
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

/**
 * Adapter dành riêng cho Admin:
 * - Tab Pending: hiện nút Xác nhận
 * - Tab Finished: không hiện nút nào
 * - Tab Cancelled: hiện nút Xóa
 */
public class AdminOrdersAdapter extends ListAdapter<Order, AdminOrdersAdapter.VH> {

    public interface OnConfirm { void confirm(Order o); }
    public interface OnDelete { void delete(Order o); }

    private final OnConfirm onConfirm;
    private final OnDelete onDelete;

    private boolean showConfirm = true;   // Tab Pending
    private boolean showDelete  = false;  // Tab Cancelled

    @SuppressLint("NotifyDataSetChanged")
    public void setShowConfirm(boolean show) { this.showConfirm = show; notifyDataSetChanged(); }
    @SuppressLint("NotifyDataSetChanged")
    public void setShowDelete(boolean show)  { this.showDelete  = show; notifyDataSetChanged(); }

    private static final DiffUtil.ItemCallback<Order> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull Order a, @NonNull Order b) {
            return Objects.equals(a.getId(), b.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Order a, @NonNull Order b) {
            return a.equals(b);
        }
    };

    public AdminOrdersAdapter(@NonNull OnConfirm onConfirm, @NonNull OnDelete onDelete) {
        super(DIFF);
        this.onConfirm = onConfirm;
        this.onDelete  = onDelete;
        setHasStableIds(true); // tránh flicker khi đổi tab
    }

    @Override
    public long getItemId(int position) {
        Order o = getItem(position);
        return (o != null && o.getId() != null) ? o.getId().hashCode() : RecyclerView.NO_ID;
    }

    public static class VH extends RecyclerView.ViewHolder {
        TextView tvId, tvTotal, tvUser, tvTime, tvStatus;
        MaterialButton btnConfirm, btnDelete;

        VH(@NonNull View itemView) {
            super(itemView);
            tvId     = itemView.findViewById(R.id.tvOrderId);
            tvTotal  = itemView.findViewById(R.id.tvOrderTotal);
            tvUser   = itemView.findViewById(R.id.tvOrderUser);
            tvTime   = itemView.findViewById(R.id.tvOrderTime);
            tvStatus = itemView.findViewById(R.id.tvOrderStatus);
            btnConfirm = itemView.findViewById(R.id.btnConfirm);
            btnDelete  = itemView.findViewById(R.id.btnDelete);
        }

        @SuppressLint("SetTextI18n")
        void bind(Order o,
                  boolean showConfirm,
                  boolean showDelete,
                  OnConfirm onConfirm,
                  OnDelete onDelete) {

            // Ẩn/hiện trước khi gán dữ liệu tránh flicker
            btnConfirm.setVisibility(showConfirm ? View.VISIBLE : View.GONE);
            btnDelete.setVisibility(showDelete ? View.VISIBLE : View.GONE);

            tvId.setText("#" + o.getId());
            tvTotal.setText(String.format(Locale.getDefault(), "%,.0f₫", o.getTotal()));
            tvUser.setText(o.getUserId());
            tvStatus.setText(o.getStatus());
            tvTime.setText(formatTime(o.getCreatedAt()));

            btnConfirm.setOnClickListener(v -> onConfirm.confirm(o));
            btnDelete.setOnClickListener(v -> onDelete.delete(o));
        }

        private String formatTime(Timestamp ts) {
            if (ts == null) return "";
            return new SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault()).format(ts.toDate());
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int vt) {
        View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_admin_order, p, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        h.bind(getItem(pos), showConfirm, showDelete, onConfirm, onDelete);
    }

    @Override
    public void onViewRecycled(@NonNull VH holder) {
        super.onViewRecycled(holder);
        holder.btnConfirm.setVisibility(View.GONE);
        holder.btnDelete.setVisibility(View.GONE);
    }
}
