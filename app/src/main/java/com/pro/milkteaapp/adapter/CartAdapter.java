package com.pro.milkteaapp.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
        this.cartItems = cartItems != null ? cartItems : new ArrayList<>();
        this.listener = listener;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        // ID ổn định cho cùng một dòng hàng (KHÔNG phụ thuộc quantity)
        CartItem ci = cartItems.get(position);
        StringBuilder toppingsKey = new StringBuilder();
        if (ci.getToppings() != null) {
            for (int i = 0; i < ci.getToppings().size(); i++) {
                toppingsKey.append(ci.getToppings().get(i).name);
                if (i < ci.getToppings().size() - 1) toppingsKey.append(",");
            }
        }
        String key = (safe(ci.getProductId()) + "|" +
                safe(ci.getSize()) + "|" +
                safe(ci.getSugar()) + "|" +
                safe(ci.getIce()) + "|" +
                safe(ci.getNote()) + "|" +
                toppingsKey);
        return key.hashCode();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_cart, parent, false);
        return new ViewHolder(v);
    }

    // Partial bind để chỉ cập nhật quantity/total khi có payload
    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()) {
            if (payloads.contains("QTY_TOTAL")) {
                CartItem item = cartItems.get(position);
                h.cartItemQuantity.setText(String.valueOf(item.getQuantity()));
                h.cartItemTotal.setText(MoneyUtils.formatVnd(item.getTotalPrice()));
                return; // không đụng tới ảnh/tên… tránh nháy
            }
        }
        super.onBindViewHolder(h, position, payloads);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        CartItem item = cartItems.get(position);
        Products p = item.getMilkTea(); // giữ tương thích code cũ

        // ===== ẢNH =====
        String imageSource = item.getImageUrl();  // có thể là url, "drawable:name" hoặc "name"
        ImageLoader.load(h.cartItemImage, imageSource, R.drawable.ic_milk_tea);

        // ===== TÊN =====
        h.cartItemName.setText(p != null ? p.getName() : "Sản phẩm");

        // ===== HÀNG 1: Size • Đường • Đá =====
        String size = item.getSize();
        String sugar = item.getSugar();
        String ice = item.getIce();

        StringBuilder line1 = new StringBuilder();
        if (!TextUtils.isEmpty(size)) {
            line1.append(String.format(Locale.getDefault(), "Size %s", size));
        }
        if (!TextUtils.isEmpty(sugar)) {
            if (line1.length() > 0) line1.append(" • ");
            line1.append(sugar);
        }
        if (!TextUtils.isEmpty(ice)) {
            if (line1.length() > 0) line1.append(" • ");
            line1.append(ice);
        }

        if (line1.length() > 0) {
            h.cartItemSize.setText(line1.toString());
            h.cartItemSize.setVisibility(View.VISIBLE);
        } else {
            h.cartItemSize.setText("");
            h.cartItemSize.setVisibility(View.GONE);
        }

        // ===== HÀNG 2: TOPPING (tự xuống dòng) =====
        if (item.getToppings() != null && !item.getToppings().isEmpty()) {
            StringBuilder sb = new StringBuilder("Topping: ");
            for (int i = 0; i < item.getToppings().size(); i++) {
                sb.append(item.getToppings().get(i).name);
                if (i < item.getToppings().size() - 1) sb.append(", ");
            }
            h.cartItemTopping.setText(sb.toString());
            h.cartItemTopping.setVisibility(View.VISIBLE);
        } else {
            h.cartItemTopping.setText("");
            h.cartItemTopping.setVisibility(View.GONE);
        }

        // ===== GHI CHÚ (nếu có) =====
        String note = item.getNote();
        if (!TextUtils.isEmpty(note)) {
            h.cartItemNote.setText("Ghi chú: " + note);
            h.cartItemNote.setVisibility(View.VISIBLE);
        } else {
            h.cartItemNote.setText("");
            h.cartItemNote.setVisibility(View.GONE);
        }

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

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        ImageView cartItemImage, incrementButton, decrementButton, deleteButton;
        TextView cartItemName, cartItemSize, cartItemTopping, cartItemNote, cartItemQuantity, cartItemTotal;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            cartItemImage   = itemView.findViewById(R.id.cartItemImage);
            cartItemName    = itemView.findViewById(R.id.cartItemName);
            cartItemSize    = itemView.findViewById(R.id.cartItemSize);
            cartItemTopping = itemView.findViewById(R.id.cartItemTopping);
            cartItemNote    = itemView.findViewById(R.id.cartItemNote);
            cartItemQuantity= itemView.findViewById(R.id.cartItemQuantity);
            cartItemTotal   = itemView.findViewById(R.id.cartItemTotal);
            incrementButton = itemView.findViewById(R.id.incrementButton);
            decrementButton = itemView.findViewById(R.id.decrementButton);
            deleteButton    = itemView.findViewById(R.id.deleteButton);
        }
    }
}
