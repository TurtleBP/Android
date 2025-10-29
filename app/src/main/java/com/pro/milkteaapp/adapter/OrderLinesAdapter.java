package com.pro.milkteaapp.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.pro.milkteaapp.R;
import com.pro.milkteaapp.models.OrderLine;
import com.pro.milkteaapp.utils.MoneyUtils;

import java.util.ArrayList;
import java.util.List;

public class OrderLinesAdapter extends RecyclerView.Adapter<OrderLinesAdapter.VH> {

    private final List<OrderLine> data = new ArrayList<>();

    public void submit(List<OrderLine> items) {
        data.clear();
        if (items != null) data.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_order_line, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        OrderLine it = data.get(position);
        h.tvName.setText(it.getName());

        String meta = it.buildMeta();
        if (meta.isEmpty()) {
            h.tvMeta.setVisibility(View.GONE);
        } else {
            h.tvMeta.setVisibility(View.VISIBLE);
            h.tvMeta.setText(meta);
        }

        String qtyPrice = MoneyUtils.formatVnd(it.getUnitPrice()) + " × " + it.getQuantity();
        h.tvQtyPrice.setText(qtyPrice);

        h.tvLineTotal.setText(MoneyUtils.formatVnd(it.getLineTotal()));
    }

    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvMeta, tvQtyPrice, tvLineTotal;
        VH(@NonNull View v) {
            super(v);
            tvName = v.findViewById(R.id.tvName);
            tvMeta = v.findViewById(R.id.tvMeta);
            tvQtyPrice = v.findViewById(R.id.tvQtyPrice);
            tvLineTotal = v.findViewById(R.id.tvLineTotal);
        }
    }
}
