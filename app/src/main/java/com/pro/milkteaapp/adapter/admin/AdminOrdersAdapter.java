package com.pro.milkteaapp.adapter.admin;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

    /* ===== Callbacks ===== */
    public interface OnItemClick { void click(@NonNull Order o); }
    public interface OnConfirm   { void confirm(@NonNull Order o); }
    public interface OnCancel    { void cancel(@NonNull Order o); }
    public interface OnDelete    { void delete(@NonNull Order o); }

    private final OnItemClick onItemClick;
    @Nullable private final OnConfirm onConfirm;
    @Nullable private final OnCancel  onCancel;
    @Nullable private final OnDelete  onDelete;

    /* ===== Button visibility flags ===== */
    private boolean showConfirm = true;   // Pending
    private boolean showCancel  = true;   // Pending
    private boolean showDelete  = false;  // Cancelled

    @SuppressLint("NotifyDataSetChanged")
    public void setShowConfirm(boolean show) { this.showConfirm = show; notifyDataSetChanged(); }
    @SuppressLint("NotifyDataSetChanged")
    public void setShowCancel(boolean show)  { this.showCancel  = show; notifyDataSetChanged(); }
    @SuppressLint("NotifyDataSetChanged")
    public void setShowDelete(boolean show)  { this.showDelete  = show; notifyDataSetChanged(); }

    /* ===== DiffUtil ===== */
    private static final DiffUtil.ItemCallback<Order> DIFF = new DiffUtil.ItemCallback<>() {
        @Override public boolean areItemsTheSame(@NonNull Order a, @NonNull Order b) {
            return Objects.equals(a.getId(), b.getId());
        }
        @Override public boolean areContentsTheSame(@NonNull Order a, @NonNull Order b) {
            // So sánh nhẹ vài field quan trọng để tránh phụ thuộc equals()
            return Objects.equals(a.getStatus(), b.getStatus())
                    && Double.compare(a.getFinalTotal(), b.getFinalTotal()) == 0
                    && tsEq(a.getCreatedAt(),  b.getCreatedAt())
                    && tsEq(a.getFinishedAt(), b.getFinishedAt())
                    && tsEq(a.getCancelledAt(), b.getCancelledAt());
        }
        private boolean tsEq(@Nullable Timestamp x, @Nullable Timestamp y) {
            if (x == y) return true;
            if (x == null || y == null) return false;
            return x.toDate().getTime() == y.toDate().getTime();
        }
    };

    /* ===== Constructor ===== */
    public AdminOrdersAdapter(@NonNull OnItemClick onItemClick,
                              @Nullable OnConfirm onConfirm,
                              @Nullable OnCancel onCancel,
                              @Nullable OnDelete onDelete) {
        super(DIFF);
        this.onItemClick = onItemClick;
        this.onConfirm   = onConfirm;
        this.onCancel    = onCancel;
        this.onDelete    = onDelete;
        setHasStableIds(true);
    }

    @Override public long getItemId(int position) {
        Order o = getItem(position);
        return o != null ? o.getId().hashCode() : RecyclerView.NO_ID;
    }

    /* ===== ViewHolder ===== */
    public static class VH extends RecyclerView.ViewHolder {
        TextView tvId, tvTotal, tvUser, tvTime, tvStatus;
        MaterialButton btnConfirm, btnCancel, btnDelete;

        VH(@NonNull View itemView) {
            super(itemView);
            tvId     = itemView.findViewById(R.id.tvOrderId);
            tvTotal  = itemView.findViewById(R.id.tvOrderTotal);
            tvUser   = itemView.findViewById(R.id.tvOrderUser);
            tvTime   = itemView.findViewById(R.id.tvOrderTime);
            tvStatus = itemView.findViewById(R.id.tvOrderStatus);
            btnConfirm = itemView.findViewById(R.id.btnConfirm);
            btnCancel  = itemView.findViewById(R.id.btnCancel);
            btnDelete  = itemView.findViewById(R.id.btnDelete);
        }

        @SuppressLint("SetTextI18n")
        void bind(@NonNull Order o,
                  boolean showConfirm, boolean showCancel, boolean showDelete,
                  @Nullable OnConfirm onConfirm,
                  @Nullable OnCancel onCancel,
                  @Nullable OnDelete onDelete,
                  @NonNull OnItemClick onItemClick) {

            // Nút
            btnConfirm.setVisibility(showConfirm && onConfirm != null ? View.VISIBLE : View.GONE);
            btnCancel.setVisibility(showCancel && onCancel != null ? View.VISIBLE : View.GONE);
            btnDelete.setVisibility(showDelete && onDelete != null ? View.VISIBLE : View.GONE);

            // Nội dung cơ bản
            tvId.setText("#" + o.getId());
            tvTotal.setText(String.format(Locale.getDefault(), "%,.0f₫", o.getFinalTotal()));
            tvUser.setText(o.getUserId());
            tvStatus.setText(o.getStatus());

            // Chọn timestamp để hiển thị:
            // - Cancelled → cancelledAt (nếu có) else createdAt
            // - Finished  → finishedAt  (nếu có) else createdAt
            // - Pending   → createdAt
            Timestamp timeToShow = pickDisplayTime(o);
            tvTime.setText(formatTime(timeToShow));

            // Màu trạng thái
            String s = o.getStatus() == null ? "" : o.getStatus().toUpperCase(Locale.ROOT);
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

            // Clicks
            itemView.setOnClickListener(v -> onItemClick.click(o));
            btnConfirm.setOnClickListener(v -> { if (onConfirm != null) onConfirm.confirm(o); });
            btnCancel.setOnClickListener(v -> { if (onCancel != null) onCancel.cancel(o); });
            btnDelete.setOnClickListener(v -> { if (onDelete != null) onDelete.delete(o); });
        }

        private Timestamp pickDisplayTime(@NonNull Order o) {
            String s = o.getStatus() == null ? "" : o.getStatus().toUpperCase(Locale.ROOT);
            if ("CANCELLED".equals(s) || "CANCELED".equals(s)) {
                return o.getCancelledAt() != null ? o.getCancelledAt() : o.getCreatedAt();
            } else if ("FINISHED".equals(s)) {
                return o.getFinishedAt() != null ? o.getFinishedAt() : o.getCreatedAt();
            }
            return o.getCreatedAt();
        }

        private String formatTime(@Nullable Timestamp ts) {
            if (ts == null) return "";
            return new SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault()).format(ts.toDate());
        }
    }

    /* ===== Adapter overrides ===== */
    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_order, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Order o = getItem(position);
        holder.bind(o, showConfirm, showCancel, showDelete, onConfirm, onCancel, onDelete, onItemClick);
    }

    @Override
    public void onViewRecycled(@NonNull VH holder) {
        super.onViewRecycled(holder);
        holder.btnConfirm.setVisibility(View.GONE);
        holder.btnCancel.setVisibility(View.GONE);
        holder.btnDelete.setVisibility(View.GONE);
    }
}
