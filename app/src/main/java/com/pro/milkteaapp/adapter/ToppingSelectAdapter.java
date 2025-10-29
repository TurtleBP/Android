package com.pro.milkteaapp.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.pro.milkteaapp.databinding.ItemToppingSelectBinding;
import com.pro.milkteaapp.models.SelectedTopping;
import com.pro.milkteaapp.models.Topping;
import com.pro.milkteaapp.utils.MoneyUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ToppingSelectAdapter extends RecyclerView.Adapter<ToppingSelectAdapter.VH> {

    public interface OnToppingChecked {
        void onCheckedChanged(@NonNull Topping topping, boolean checked);
    }

    private final List<Topping> data = new ArrayList<>();
    private final Set<String> checkedIds = new HashSet<>();
    private final OnToppingChecked listener;

    public ToppingSelectAdapter(OnToppingChecked listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    @Override public long getItemId(int position) {
        Topping t = data.get(position);
        String key = t.getId() == null ? t.getName() : t.getId();
        return (key == null ? "" : key).hashCode();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemToppingSelectBinding b = ItemToppingSelectBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Topping t = data.get(position);

        // 1) Tháo listener cũ trước khi setChecked
        h.binding.checkbox.setOnCheckedChangeListener(null);

        // 2) Bind text/price
        h.binding.checkbox.setText(t.getName());
        h.binding.price.setText(MoneyUtils.formatVnd(t.getPrice()));

        // 3) Set trạng thái checked theo checkedIds
        boolean isChecked = checkedIds.contains(t.getId());
        h.binding.checkbox.setChecked(isChecked);

        // 4) Gắn listener mới
        h.binding.checkbox.setOnCheckedChangeListener((CompoundButton cb, boolean ch) -> {
            if (t.getId() != null) {
                if (ch) checkedIds.add(t.getId());
                else    checkedIds.remove(t.getId());
            }
            if (listener != null) listener.onCheckedChanged(t, ch);
        });
    }

    @Override public int getItemCount() { return data.size(); }

    /** Cập nhật danh sách + precheck những topping đã chọn */
    public void submitList(List<Topping> tops, List<SelectedTopping> prechecked) {
        data.clear();
        if (tops != null) data.addAll(tops);

        checkedIds.clear();
        if (prechecked != null) {
            for (SelectedTopping st : prechecked) {
                if (st.id != null) checkedIds.add(st.id);
            }
        }
        notifyDataSetChanged();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemToppingSelectBinding binding;
        VH(@NonNull ItemToppingSelectBinding b) { super(b.getRoot()); this.binding = b; }
    }
}
