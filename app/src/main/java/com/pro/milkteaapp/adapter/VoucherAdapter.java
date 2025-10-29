package com.pro.milkteaapp.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.*;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.Timestamp;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.models.Voucher;
import com.pro.milkteaapp.utils.MoneyUtils;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Adapter quản lý Voucher: hiển thị chi tiết đẹp + switch Active + nút Sửa/Xóa.
 * Layout item: res/layout/item_voucher.xml
 */
public class VoucherAdapter extends RecyclerView.Adapter<VoucherAdapter.VH> {

    public interface Listener {
        void onEdit(@NonNull Voucher v);
        void onDelete(@NonNull Voucher v);
        void onToggle(@NonNull Voucher v, boolean active);
    }

    private final Listener listener;
    private final List<Voucher> data = new ArrayList<>();
    private final SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    public VoucherAdapter(@NonNull Listener l) {
        this.listener = l;
        setHasStableIds(true);
    }

    public void submit(@Nullable List<Voucher> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    @Override public int getItemCount() { return data.size(); }

    @Override public long getItemId(int position) {
        String id = data.get(position) != null ? data.get(position).getId() : null;
        return id != null ? id.hashCode() : position;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_voucher, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Voucher v = data.get(pos);
        if (v == null) return;

        // --- Mã voucher ---
        h.tvCode.setText(nz(v.getCode()));

        // --- Ưu đãi ---
        h.tvBenefit.setText(buildBenefit(v));

        // --- Hạn sử dụng ---
        h.tvExpiry.setText(buildExpiry(v.getEndAt()));

        // --- Áp dụng với hóa đơn ---
        h.tvMin.setText(buildMinOrder(v.getMinOrder()));

        // --- Hình thức thanh toán (chips) ---
        bindChannels(h.chipsChannels, v.getAllowedChannels());

        // --- Số lượng / giới hạn ---
        h.tvLimit.setText(buildLimit((long) v.getPerUserLimit()));

        // --- Active switch ---
        h.swActive.setOnCheckedChangeListener(null);
        h.swActive.setChecked(Boolean.TRUE.equals(v.isActive()));
        h.swActive.setOnCheckedChangeListener((btn, checked) -> listener.onToggle(v, checked));

        // --- Hành động ---
        h.btnEdit.setOnClickListener(x -> listener.onEdit(v));
        h.btnDelete.setOnClickListener(x -> listener.onDelete(v));
    }

    // ================= Helpers =================

    /** Ưu đãi: tự diễn giải theo type */
    @NonNull
    private String buildBenefit(@NonNull Voucher v) {
        final String type = nz(v.getType());
        if ("ORDER_FIXED".equals(type)) {
            long amount = nzLong(v.getAmount());
            return "Giảm " + MoneyUtils.formatVnd(amount);
        }
        if ("ORDER_PERCENT".equals(type)) {
            long percent = nzLong(v.getPercent());
            long max = nzLong(v.getMaxDiscount());
            String s = "Giảm " + percent + "% tổng đơn";
            if (max > 0) s += " (tối đa " + MoneyUtils.formatVnd(max) + ")";
            return s;
        }
        if ("SHIPPING_FIXED".equals(type)) {
            long amount = nzLong(v.getAmount());
            return "Giảm phí ship " + MoneyUtils.formatVnd(amount);
        }
        return "Ưu đãi";
    }

    /** Hạn sử dụng: đến dd/MM/yyyy hoặc “Không giới hạn” */
    @NonNull
    private String buildExpiry(@Nullable Timestamp endAt) {
        if (endAt == null) return "Không giới hạn";
        Date d = endAt.toDate();
        return "Đến " + df.format(d);
    }

    /** Đơn tối thiểu: “Từ xxxđ” hoặc “Không yêu cầu” */
    @NonNull
    private String buildMinOrder(@Nullable Long minOrder) {
        long m = nzLong(minOrder);
        if (m <= 0) return "Không yêu cầu";
        return "Từ " + MoneyUtils.formatVnd(m);
    }

    /** Số lượng/giới hạn mỗi khách: “Mỗi khách hàng X lần” hoặc “Không giới hạn” */
    @NonNull
    private String buildLimit(@Nullable Long perUserLimit) {
        long p = nzLong(perUserLimit);
        if (p > 0) return "Mỗi khách hàng " + p + " lần";
        return "Không giới hạn";
    }

    /** Tạo Chip cho allowedChannels */
    private void bindChannels(@NonNull ChipGroup group, @Nullable List<String> channels) {
        group.removeAllViews();
        if (channels == null || channels.isEmpty()) {
            Chip chip = new Chip(group.getContext(), null, com.google.android.material.R.attr.chipStyle);
            chip.setText("Tất cả");
            chip.setCheckable(false);
            group.addView(chip);
            return;
        }
        for (String ch : channels) {
            Chip chip = new Chip(group.getContext(), null, com.google.android.material.R.attr.chipStyle);
            chip.setText(ch);
            chip.setCheckable(false);
            group.addView(chip);
        }
    }

    @NonNull private static String nz(@Nullable String s) { return s == null ? "" : s; }
    private static long nzLong(@Nullable Number n) { return n == null ? 0L : n.longValue(); }

    // ================= ViewHolder =================
    static class VH extends RecyclerView.ViewHolder {
        TextView tvCode, tvBenefit, tvExpiry, tvMin, tvLimit;
        ChipGroup chipsChannels;
        SwitchMaterial swActive;
        View btnEdit, btnDelete;

        VH(@NonNull View v) {
            super(v);
            tvCode = v.findViewById(R.id.tvCode);
            tvBenefit = v.findViewById(R.id.tvBenefit);
            tvExpiry = v.findViewById(R.id.tvExpiry);
            tvMin = v.findViewById(R.id.tvMin);
            tvLimit = v.findViewById(R.id.tvLimit);
            chipsChannels = v.findViewById(R.id.chipsChannels);
            swActive = v.findViewById(R.id.swActive);
            btnEdit = v.findViewById(R.id.btnEdit);
            btnDelete = v.findViewById(R.id.btnDelete);
        }
    }
}
