package com.pro.milkteaapp.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.pro.milkteaapp.R;
import com.pro.milkteaapp.databinding.ItemCartBinding;
import com.pro.milkteaapp.models.CartItem;
import com.pro.milkteaapp.models.Products;
import com.pro.milkteaapp.utils.ImageUtils;
import com.pro.milkteaapp.utils.MoneyUtils;

import java.util.List;

public class CartAdapter extends RecyclerView.Adapter<CartAdapter.ViewHolder> {
    private final Context context;
    private final List<CartItem> cartItems;
    private final OnItemActionClickListener listener;

    public interface OnItemActionClickListener {
        void onItemRemoved(int position);
        void onQuantityIncreased(int position);
        void onQuantityDecreased(int position);
    }

    public CartAdapter(Context context, List<CartItem> cartItems, OnItemActionClickListener listener) {
        this.context = context;
        this.cartItems = cartItems;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCartBinding binding = ItemCartBinding.inflate(LayoutInflater.from(context), parent, false);
        return new ViewHolder(binding);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CartItem cartItem = cartItems.get(position);
        Products mt = cartItem.getMilkTea();

        holder.binding.cartItemName.setText(mt.getName());
        holder.binding.cartItemQuantity.setText(String.valueOf(cartItem.getQuantity()));
        holder.binding.cartItemSize.setText("Size: " + cartItem.getSize());
        holder.binding.cartItemTopping.setText("Topping: " + cartItem.getTopping());
        holder.binding.cartItemTotal.setText(MoneyUtils.formatVnd(cartItem.getTotalPrice()));

        // Load image từ drawable name (không dùng URL)
        String imageName = mt.getImageUrl();

        if (!TextUtils.isEmpty(imageName)) {
            int resId = ImageUtils.getImageResId(context, imageName);
            if (resId != 0) {
                holder.binding.cartItemImage.setImageResource(resId);
            } else {
                holder.binding.cartItemImage.setImageResource(R.mipmap.ic_launcher); // fallback
            }
        } else {
            holder.binding.cartItemImage.setImageResource(R.mipmap.ic_launcher); // fallback
        }

        // Xử lý các nút bấm
        holder.binding.deleteButton.setOnClickListener(v -> {
            if (listener != null) {
                int pos = holder.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) listener.onItemRemoved(pos);
            }
        });

        holder.binding.incrementButton.setOnClickListener(v -> {
            if (listener != null) {
                int pos = holder.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) listener.onQuantityIncreased(pos);
            }
        });

        holder.binding.decrementButton.setOnClickListener(v -> {
            if (listener != null) {
                int pos = holder.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) listener.onQuantityDecreased(pos);
            }
        });
    }

    @Override
    public int getItemCount() {
        return cartItems.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemCartBinding binding;
        public ViewHolder(@NonNull ItemCartBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
