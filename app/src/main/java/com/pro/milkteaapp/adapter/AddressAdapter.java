package com.pro.milkteaapp.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.models.Address;

import java.util.List;

public class AddressAdapter extends RecyclerView.Adapter<AddressAdapter.VH> {

    public interface Listener {
        void onItemClick(int position);
        void onSetDefault(int position);
        void onEdit(int position);
        void onDelete(int position);
    }

    private final List<Address> items;
    private final Listener listener;
    private final boolean pickerMode;
    private int selectedIndex = RecyclerView.NO_POSITION;

    public AddressAdapter(List<Address> items, Listener listener) {
        this(items, listener, false);
    }

    public AddressAdapter(List<Address> items, Listener listener, boolean pickerMode) {
        this.items = items;
        this.listener = listener;
        this.pickerMode = pickerMode;
    }

    public void setSelectedIndex(int i) { selectedIndex = i; }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_address, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Address a = items.get(position);

        String name = a.getFullName() != null ? a.getFullName() : "";
        String phone = (a.getPhone() != null && !a.getPhone().isEmpty()) ? (" • " + a.getPhone()) : "";
        h.tvNamePhone.setText(name + phone);
        h.tvAddressLines.setText(a.displayLine());

        // Ẩn/hiện nhóm nút theo mode
        if (pickerMode) {
            h.btnSetDefault.setVisibility(View.GONE);
            h.btnEdit.setVisibility(View.GONE);
            h.btnDelete.setVisibility(View.GONE);
        } else {
            h.btnSetDefault.setVisibility(View.VISIBLE);
            h.btnEdit.setVisibility(View.VISIBLE);
            h.btnDelete.setVisibility(View.VISIBLE);
        }

        // Highlight selection ở picker
        MaterialCardView card = h.card;
        if (pickerMode) {
            int strokeWidth = position == selectedIndex ? dp(h.itemView, 2) : dp(h.itemView, 0);
            @ColorInt int primary = MaterialColors.getColor(h.itemView, androidx.appcompat.R.attr.colorPrimary);
            card.setStrokeColor(primary);
            card.setStrokeWidth(strokeWidth);
        } else {
            card.setStrokeWidth(0);
        }

        h.itemView.setOnClickListener(v -> {
            if (listener == null) return;
            if (pickerMode) {
                int old = selectedIndex;
                selectedIndex = h.getBindingAdapterPosition();
                if (old != RecyclerView.NO_POSITION) notifyItemChanged(old);
                notifyItemChanged(selectedIndex);
                listener.onItemClick(selectedIndex);
            } else {
                listener.onItemClick(h.getBindingAdapterPosition());
            }
        });

        h.btnSetDefault.setOnClickListener(v -> { if (listener != null) listener.onSetDefault(h.getBindingAdapterPosition()); });
        h.btnEdit.setOnClickListener(v -> { if (listener != null) listener.onEdit(h.getBindingAdapterPosition()); });
        h.btnDelete.setOnClickListener(v -> { if (listener != null) listener.onDelete(h.getBindingAdapterPosition()); });
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        MaterialCardView card;
        TextView tvNamePhone, tvAddressLines;
        MaterialButton btnSetDefault, btnEdit, btnDelete;

        VH(@NonNull View v) {
            super(v);
            card = (MaterialCardView) v;
            tvNamePhone = v.findViewById(R.id.tvNamePhone);
            tvAddressLines = v.findViewById(R.id.tvAddressLines);
            btnSetDefault = v.findViewById(R.id.btnSetDefault);
            btnEdit = v.findViewById(R.id.btnEdit);
            btnDelete = v.findViewById(R.id.btnDelete);
        }
    }

    private static int dp(View v, int dp) {
        float d = v.getResources().getDisplayMetrics().density;
        return (int) (dp * d + 0.5f);
    }
}
