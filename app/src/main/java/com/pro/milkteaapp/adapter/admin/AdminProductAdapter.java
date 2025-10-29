package com.pro.milkteaapp.adapter.admin;

import android.content.Intent;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.activity.admin.ProductEditorActivity;
import com.pro.milkteaapp.models.Products;
import com.pro.milkteaapp.utils.ImageUtils;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class AdminProductAdapter extends RecyclerView.Adapter<AdminProductAdapter.ProductViewHolder> {

    public interface OnProductActionListener {
        void onEditProduct(Products product);
        void onDeleteProduct(Products product);
        void onEditStock(Products product);
    }

    private final OnProductActionListener listener;
    private final NumberFormat currencyFormat;
    private List<Products> productList;

    public AdminProductAdapter(@NonNull List<Products> initialList,
                               @NonNull OnProductActionListener listener) {
        this.productList = new ArrayList<>(initialList);
        this.listener = listener;
        this.currencyFormat = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
        setHasStableIds(true);
    }

    /** Danh sách hiện tại (copy để an toàn) */
    public List<Products> getCurrentList() {
        return new ArrayList<>(productList);
    }

    /** Cập nhật với DiffUtil cho mượt */
    public void updateList(@NonNull List<Products> newList) {
        final List<Products> oldList = this.productList;
        final List<Products> newCopy = new ArrayList<>(newList);

        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return oldList.size(); }
            @Override public int getNewListSize() { return newCopy.size(); }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                String oldId = safeStr(oldList.get(oldItemPosition).getId());
                String newId = safeStr(newCopy.get(newItemPosition).getId());
                if (!TextUtils.isEmpty(oldId) && !TextUtils.isEmpty(newId)) return oldId.equals(newId);
                // Fallback khi thiếu id: so theo name+price
                String ok = safeStr(oldList.get(oldItemPosition).getName()) + "_" + oldList.get(oldItemPosition).getPrice();
                String nk = safeStr(newCopy.get(newItemPosition).getName()) + "_" + newCopy.get(newItemPosition).getPrice();
                return ok.equals(nk);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                Products o = oldList.get(oldItemPosition);
                Products n = newCopy.get(newItemPosition);
                return Objects.equals(safeStr(o.getName()), safeStr(n.getName())) &&
                        Objects.equals(safeStr(o.getCategory()), safeStr(n.getCategory())) &&
                        Objects.equals(safeStr(o.getImageUrl()), safeStr(n.getImageUrl())) &&
                        o.getPrice() == n.getPrice() &&
                        o.getStock() == n.getStock() &&
                        o.getSoldCount() == n.getSoldCount();
            }
        });

        this.productList = newCopy;
        diff.dispatchUpdatesTo(this);
    }

    @Override
    public long getItemId(int position) {
        if (position < 0 || position >= productList.size()) return RecyclerView.NO_ID;
        String id = productList.get(position).getId();
        if (!TextUtils.isEmpty(id)) return id.hashCode();
        // Fallback khi thiếu id
        String key = safeStr(productList.get(position).getName()) + "_" + productList.get(position).getPrice();
        return key.hashCode();
    }

    @NonNull
    @Override
    public ProductViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_admin_product, parent, false);
        return new ProductViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ProductViewHolder holder, int position) {
        holder.bind(productList.get(position));
    }

    @Override
    public int getItemCount() {
        return productList == null ? 0 : productList.size();
    }

    /** ViewHolder */
    public class ProductViewHolder extends RecyclerView.ViewHolder {
        private final TextView productName, productCategory, productPrice, productStock, productSold;
        private final ImageView productImage;
        private final View btnEdit, btnDelete;

        public ProductViewHolder(@NonNull View itemView) {
            super(itemView);
            productName     = itemView.findViewById(R.id.productName);
            productCategory = itemView.findViewById(R.id.productCategory);
            productPrice    = itemView.findViewById(R.id.productPrice);
            productStock    = itemView.findViewById(R.id.productStock);
            productSold     = itemView.findViewById(R.id.productSold);
            productImage    = itemView.findViewById(R.id.productImage);
            btnEdit         = itemView.findViewById(R.id.btnEdit);
            btnDelete       = itemView.findViewById(R.id.btnDelete);
        }

        void bind(Products p) {
            // Text
            productName.setText(safeStr(p.getName()));
            productCategory.setText(safeStr(p.getCategory()));
            productPrice.setText(formatPrice(p.getPrice()));
            productStock.setText(String.valueOf(p.getStock()));
            productSold.setText(String.valueOf(p.getSoldCount()));

            // Ảnh: tự phát hiện URL hay tên drawable
            String img = safeStr(p.getImageUrl());
            if (isHttpUrl(img)) {
                RequestOptions opts = new RequestOptions()
                        .placeholder(R.drawable.ic_placeholder)
                        .error(R.drawable.ic_loading)
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC);

                Glide.with(itemView)
                        .load(img)
                        .apply(opts)
                        .into(productImage);
            } else {
                int resId = ImageUtils.getImageResId(itemView.getContext(), img);
                if (resId == 0) productImage.setImageResource(R.drawable.ic_placeholder);
                else productImage.setImageResource(resId);
            }

            // ===== Click handlers =====
            View.OnClickListener goEdit = v -> {
                int pos = getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                Products prod = productList.get(pos);

                if (listener != null) {
                    listener.onEditProduct(prod);
                } else {
                    // Fallback: mở ProductEditorActivity khi không có listener
                    Intent intent = new Intent(itemView.getContext(), ProductEditorActivity.class);
                    intent.putExtra(ProductEditorActivity.EXTRA_PRODUCT_ID, safeStr(prod.getId()));
                    itemView.getContext().startActivity(intent);
                }
            };

            itemView.setOnClickListener(goEdit);
            if (btnEdit != null) btnEdit.setOnClickListener(goEdit);

            if (btnDelete != null) {
                btnDelete.setOnClickListener(v -> {
                    int pos = getBindingAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) return;
                    if (listener != null) listener.onDeleteProduct(productList.get(pos));
                });
            }

            // Chỉnh tồn kho: click vào text tồn kho
            productStock.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                if (listener != null) listener.onEditStock(productList.get(pos));
            });
        }

        private String formatPrice(Double price) {
            double p = safeDouble(price);
            return currencyFormat.format(p) + "đ";
        }
    }

    // ===== Helpers =====
    private static String safeStr(String s) { return s == null ? "" : s; }
    private static double safeDouble(Double d){ return d == null ? 0d : d; }
    private static boolean isHttpUrl(String s) {
        if (TextUtils.isEmpty(s)) return false;
        boolean looksLikeUrl = Patterns.WEB_URL.matcher(s).matches();
        return looksLikeUrl && (s.startsWith("http://") || s.startsWith("https://"));
    }
}
