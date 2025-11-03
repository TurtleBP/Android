package com.pro.milkteaapp.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.pro.milkteaapp.R;
import com.pro.milkteaapp.models.CartItem;
import com.pro.milkteaapp.models.Products;
import com.pro.milkteaapp.utils.ImageLoader;
import com.pro.milkteaapp.utils.MoneyUtils;

import java.util.List;

public class CartAdapter extends RecyclerView.Adapter<CartAdapter.ViewHolder> {

    public interface OnItemActionClickListener {
        void onItemRemoved(int position);
        void onQuantityIncreased(int position);
        void onQuantityDecreased(int position);
    }

    private final Context context;
    private final List<CartItem> cartItems;
    private final OnItemActionClickListener listener;

    public CartAdapter(Context context, List<CartItem> cartItems, OnItemActionClickListener listener) {
        this.context = context;
        this.cartItems = cartItems;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_cart, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        CartItem item = cartItems.get(position);
        Products p = item.getMilkTea(); // để tương thích code cũ

        // ===== ẢNH =====
        // quan trọng: dùng helper của anh để xử lý cả 3 trường hợp
        // 1) "https://..." 2) "drawable:ten_anh" 3) "ten_anh"
        String imageSource = item.getImageUrl();          // lấy từ CartItem (đã wrap từ product)
        ImageLoader.load(h.cartItemImage, imageSource, R.drawable.ic_milk_tea);

        // ===== TÊN =====
        h.cartItemName.setText(p != null ? p.getName() : "Sản phẩm");

        // ===== SIZE =====
        h.cartItemSize.setText(item.getSize());

        // ===== TOPPING =====
        if (item.getToppings() != null && !item.getToppings().isEmpty()) {
            StringBuilder sb = new StringBuilder("Topping: ");
            for (int i = 0; i < item.getToppings().size(); i++) {
                sb.append(item.getToppings().get(i).name);
                if (i < item.getToppings().size() - 1) sb.append(", ");
            }
            h.cartItemTopping.setText(sb.toString());
            h.cartItemTopping.setVisibility(View.VISIBLE);
        } else {
            h.cartItemTopping.setVisibility(View.GONE);
        }

        // ===== NOTE: CartItem KHÔNG CÓ NOTE → ẨN =====
        h.cartItemNote.setVisibility(View.GONE);

        // ===== SỐ LƯỢNG + THÀNH TIỀN =====
        h.cartItemQuantity.setText(String.valueOf(item.getQuantity()));
        h.cartItemTotal.setText(MoneyUtils.formatVnd(item.getTotalPrice()));

        // ===== ACTIONS =====
        h.incrementButton.setOnClickListener(v -> {
            if (listener != null) listener.onQuantityIncreased(h.getBindingAdapterPosition());
        });
        h.decrementButton.setOnClickListener(v -> {
            if (listener != null) listener.onQuantityDecreased(h.getBindingAdapterPosition());
        });
        h.deleteButton.setOnClickListener(v -> {
            if (listener != null) listener.onItemRemoved(h.getBindingAdapterPosition());
        });
    }

    @Override
    public int getItemCount() {
        return cartItems != null ? cartItems.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        ImageView cartItemImage, incrementButton, decrementButton, deleteButton;
        TextView cartItemName, cartItemSize, cartItemTopping, cartItemNote, cartItemQuantity, cartItemTotal;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            cartItemImage = itemView.findViewById(R.id.cartItemImage);
            cartItemName = itemView.findViewById(R.id.cartItemName);
            cartItemSize = itemView.findViewById(R.id.cartItemSize);
            cartItemTopping = itemView.findViewById(R.id.cartItemTopping);
            cartItemNote = itemView.findViewById(R.id.cartItemNote);
            cartItemQuantity = itemView.findViewById(R.id.cartItemQuantity);
            cartItemTotal = itemView.findViewById(R.id.cartItemTotal);
            incrementButton = itemView.findViewById(R.id.incrementButton);
            decrementButton = itemView.findViewById(R.id.decrementButton);
            deleteButton = itemView.findViewById(R.id.deleteButton);
        }
    }
}
