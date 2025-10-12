package com.pro.milkteaapp.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;
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
            // KHỚP với item_product.xml bạn gửi (milkTeaImage / milkTeaName / milkTeaPrice)
            milkTeaImage = itemView.findViewById(R.id.milkTeaImage);
            milkTeaName  = itemView.findViewById(R.id.milkTeaName);
            milkTeaPrice = itemView.findViewById(R.id.milkTeaPrice);
        }

        @SuppressLint("SetTextI18n")
        void bind(Products p, OnProductClick l) {
            Context ctx = itemView.getContext();

            // Tên
            milkTeaName.setText(p.getName() == null ? "" : p.getName());

            // Giá (an toàn cho cả double và Double)
            double price = safePrice(p);
            NumberFormat nf = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
            milkTeaPrice.setText(nf.format(Math.round(price)) + " đ");

            // Ảnh: URL hoặc tên drawable
            String image = p.getImageUrl();
            if (!TextUtils.isEmpty(image) && isHttpUrl(image)) {
                Glide.with(ctx)
                        .load(image)
                        .placeholder(R.drawable.ic_milk_tea)
                        .error(R.drawable.ic_milk_tea)
                        .into(milkTeaImage);
            } else if (!TextUtils.isEmpty(image)) {
                int resId = ctx.getResources().getIdentifier(image, "drawable", ctx.getPackageName());
                milkTeaImage.setImageResource(resId != 0 ? resId : R.drawable.ic_milk_tea);
            } else {
                milkTeaImage.setImageResource(R.drawable.ic_milk_tea);
            }

            itemView.setOnClickListener(v -> { if (l != null) l.onClick(p); });
        }

        /** Trả về giá an toàn: hỗ trợ cả getPrice() là double hoặc Double (nullable) */
        private static double safePrice(Products p) {
            try {
                // Trường hợp getPrice() là Double (nullable)
                // -> auto-unboxing có thể NPE, nên bắt luôn.
                Double pv = null;
                try {
                    // Nếu phương thức trả về Double (wrapper)
                    pv = (Double) Products.class.getMethod("getPrice").invoke(p);
                } catch (NoSuchMethodException ex) {
                    // Nếu không phải Double, có thể là double primitive:
                    // gọi thẳng p.getPrice() (qua cast Number) trong block dưới
                }

                if (pv != null) return pv;

                // Trường hợp phương thức là double primitive (không null)
                // Gọi trực tiếp để lấy giá trị
                // (tránh reflection thêm lần nữa cho hiệu năng)
                return p.getPrice(); // nếu là primitive double, dòng này OK
            } catch (Throwable ignored) {
                // Bất kỳ lỗi nào -> trả 0
                return 0d;
            }
        }

        private static boolean isHttpUrl(String s) {
            return s.startsWith("http://") || s.startsWith("https://");
        }
    }
}
