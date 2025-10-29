package com.pro.milkteaapp.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.models.Products;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ProductsSectionAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final String TAG = "ProductsSectionAdapter";

    @IntDef({TYPE_HEADER, TYPE_PRODUCT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface RowType {}
    public static final int TYPE_HEADER  = 0;
    public static final int TYPE_PRODUCT = 1;

    /** Row dữ liệu cho adapter (header / product) */
    public static class Row {
        @RowType public int type;
        public String header;     // khi type = HEADER
        public Products product;  // khi type = PRODUCT

        public static Row header(String title){
            Row r = new Row(); r.type = TYPE_HEADER; r.header = title; return r;
        }
        public static Row product(Products p){
            Row r = new Row(); r.type = TYPE_PRODUCT; r.product = p; return r;
        }
    }

    /** Callback click sản phẩm */
    public interface OnProductClick { void onClick(Products p); }

    private final List<Row> rows = new ArrayList<>();
    private final OnProductClick listener;

    public ProductsSectionAdapter(OnProductClick l){ this.listener = l; }

    @SuppressLint("NotifyDataSetChanged")
    public void submitRows(List<Row> newRows){
        rows.clear();
        if (newRows != null) rows.addAll(newRows);
        notifyDataSetChanged();
    }

    @Override public int getItemViewType(int position) { return rows.get(position).type; }
    @Override public int getItemCount() { return rows.size(); }

    @NonNull @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_section_header, parent, false);
            return new HeaderVH(v);
        } else {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_product, parent, false);
            return new ProductVH(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
        Row r = rows.get(position);
        if (getItemViewType(position) == TYPE_HEADER) {
            ((HeaderVH) h).bind(r.header);
        } else {
            ((ProductVH) h).bind(r.product, listener);
        }
    }

    /* ======================= ViewHolders ======================= */

    static class HeaderVH extends RecyclerView.ViewHolder {
        TextView tvHeader;
        HeaderVH(@NonNull View itemView) {
            super(itemView);
            tvHeader = itemView.findViewById(R.id.tvHeader);
        }
        void bind(String title) {
            tvHeader.setText(title == null ? "" : title);
        }
    }

    static class ProductVH extends RecyclerView.ViewHolder {
        ImageView milkTeaImage;
        TextView milkTeaName, milkTeaPrice;

        ProductVH(@NonNull View itemView) {
            super(itemView);
            // KHỚP với item_product.xml (milkTeaImage / milkTeaName / milkTeaPrice)
            milkTeaImage = itemView.findViewById(R.id.milkTeaImage);
            milkTeaName  = itemView.findViewById(R.id.milkTeaName);
            milkTeaPrice = itemView.findViewById(R.id.milkTeaPrice);
        }

        @SuppressLint("SetTextI18n")
        void bind(Products p, OnProductClick l) {
            Context ctx = itemView.getContext();

            // ===== Tên & Giá =====
            milkTeaName.setText(p.getName() == null ? "" : p.getName());

            double price;
            try {
                // ưu tiên getter chuẩn (Double có thể null)
                Double pv = p.getPrice();
                price = pv == null ? 0d : pv;
            } catch (Throwable t) {
                // nếu model trả về primitive double thì dòng trên vẫn OK;
                // bắt mọi trường hợp lạ để tránh crash
                try { price = (double) Products.class.getMethod("getPrice").invoke(p); }
                catch (Throwable ignore) { price = 0d; }
            }
            NumberFormat nf = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
            milkTeaPrice.setText(nf.format(Math.round(price)) + " đ");

            // ===== ẢNH (chuẩn hoá theo code cũ) =====
            String url = pickBestImageUrl(p);
            if (!TextUtils.isEmpty(url)) {
                if (url.startsWith("gs://")) {
                    // Glide KHÔNG load trực tiếp được gs://
                    // -> hiển thị placeholder & log để bạn xử lý đổi sang https downloadUrl
                    milkTeaImage.setImageResource(R.drawable.ic_milk_tea);
                    Log.w(TAG, "Cannot load gs:// with Glide: " + url);
                } else if (isHttpUrl(url)) {
                    // Tie lifecycle vào ViewHolder (itemView) để tránh leak
                    Glide.with(itemView)
                            .load(url)
                            .placeholder(R.drawable.ic_milk_tea)
                            .error(R.drawable.ic_products)
                            .into(milkTeaImage);
                } else {
                    // Trường hợp code cũ lưu "tên resource" (vd: milk_tea_1)
                    @SuppressLint("DiscouragedApi") int resId = ctx.getResources().getIdentifier(url, "drawable", ctx.getPackageName());
                    milkTeaImage.setImageResource(resId != 0 ? resId : R.drawable.ic_milk_tea);
                }
            } else {
                milkTeaImage.setImageResource(R.drawable.ic_milk_tea);
            }

            itemView.setOnClickListener(v -> { if (l != null) l.onClick(p); });
        }

        /** Ưu tiên imageUrl; fallback sang legacy "image" nếu có; nếu cả hai rỗng → null */
        private static String pickBestImageUrl(Products p) {
            try {
                // 1) imageUrl (mới)
                if (!TextUtils.isEmpty(p.getImageUrl())) return p.getImageUrl();
            } catch (Throwable ignore) {}

            try {
                // 2) legacy field "image" (code cũ)
                //    dùng reflection để không phụ thuộc compile-time
                Object legacy = Products.class.getMethod("getImage").invoke(p);
                if (legacy instanceof String && !TextUtils.isEmpty((String) legacy)) {
                    return (String) legacy;
                }
            } catch (Throwable ignore) {}

            return null;
        }

        private static boolean isHttpUrl(String s) {
            return s.startsWith("http://") || s.startsWith("https://");
        }
    }
}
