package com.pro.milkteaapp.adapters;

import android.content.Context;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.databinding.ItemProductSmallBinding;
import com.pro.milkteaapp.models.Products;
import com.pro.milkteaapp.utils.MoneyUtils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Thẻ sản phẩm cỡ nhỏ — phù hợp danh sách ngang (featured / top price).
 * Dùng ViewBinding: ItemMilkTeaSmallBinding (layout item_milk_tea_small.xml).
 */
public class ProductsSmallAdapter extends ListAdapter<Products, ProductsSmallAdapter.VH> {

    public interface OnItemClickListener { void onItemClick(Products p); }

    private final OnItemClickListener listener;
    private static final long CLICK_DEBOUNCE_MS = 300L;
    private long lastClick = 0L;

    public ProductsSmallAdapter(@NonNull Context context, OnItemClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
        setHasStableIds(true);
    }

    // ===== DiffUtil =====
    private static final DiffUtil.ItemCallback<Products> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<>() {
                @Override
                public boolean areItemsTheSame(@NonNull Products oldItem, @NonNull Products newItem) {
                    String oldId = oldItem.getId();
                    String newId = newItem.getId();
                    if (!TextUtils.isEmpty(oldId) && !TextUtils.isEmpty(newId)) {
                        return oldId.equals(newId);
                    }
                    String oldKey = safe(oldItem.getName()) + "_" + oldItem.getPrice();
                    String newKey = safe(newItem.getName()) + "_" + newItem.getPrice();
                    return oldKey.equals(newKey);
                }

                @Override
                public boolean areContentsTheSame(@NonNull Products o, @NonNull Products n) {
                    return Objects.equals(o.getName(), n.getName())
                            && o.getPrice() == n.getPrice()
                            && Objects.equals(o.getImageUrl(), n.getImageUrl())
                            && Objects.equals(o.getDescription(), n.getDescription())
                            && Objects.equals(o.getCategory(), n.getCategory());
                }
            };

    @Override
    public long getItemId(int position) {
        Products p = getItem(position);
        if (p == null) return RecyclerView.NO_ID;
        String key = !TextUtils.isEmpty(p.getId())
                ? p.getId()
                : (safe(p.getName()) + "_" + p.getPrice());
        return key.hashCode();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemProductSmallBinding b = ItemProductSmallBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }
    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Products p = getItem(position);
        if (p == null) return;

        String name = TextUtils.isEmpty(p.getName())
                ? h.itemView.getContext().getString(R.string.unknown_item)
                : p.getName();
        h.b.milkTeaName.setText(name);
        h.b.milkTeaPrice.setText(MoneyUtils.formatVnd(p.getPrice()));
        h.b.milkTeaImage.setContentDescription(name);

        // ✅ Sửa chỗ này: truyền ImageView
        bindImage(h.b.milkTeaImage, p.getImageUrl(), R.drawable.milktea);

        h.itemView.setOnClickListener(v -> {
            long now = SystemClock.elapsedRealtime();
            if (now - lastClick < CLICK_DEBOUNCE_MS) return;
            lastClick = now;
            if (listener != null) listener.onItemClick(p);
        });
    }

    private static final Map<String, Integer> DRAWABLE_MAP = createDrawableMap();

    /** Khởi tạo Map chứa tên ảnh và resource tương ứng */
    private static Map<String, Integer> createDrawableMap() {
        Map<String, Integer> map = new HashMap<>();
        map.put("milktea_chocolate", R.drawable.milktea_chocolate);
        map.put("milktea_matcha", R.drawable.milktea_matcha);
        map.put("milktea_taro", R.drawable.milktea_taro);
        map.put("milktea_strawberry", R.drawable.milktea_strawberry);
        map.put("milktea_mango", R.drawable.milktea_mango);
        map.put("milktea_brownsugar", R.drawable.milktea_brownsugar);
        map.put("milktea_caramen", R.drawable.milktea_caramel);
        map.put("milktea_cheese", R.drawable.milktea_cheese);
        map.put("milktea_olong", R.drawable.milktea_olong);
        map.put("milktea_greenthai", R.drawable.milktea_greenthai);
        map.put("milktea_blueberry", R.drawable.milktea_blueberry);
        // thêm các ảnh khác nếu có
        return map;
    }

    private void bindImage(ImageView iv, String raw, @DrawableRes int fallbackRes) {
        if (TextUtils.isEmpty(raw)) {
            iv.setImageResource(fallbackRes);
            return;
        }

        String s = raw.trim().toLowerCase(Locale.US);

        // 1️⃣ Load URL online
        if (s.startsWith("http://") || s.startsWith("https://")) {
            Glide.with(iv.getContext())
                    .load(s)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .placeholder(R.drawable.ic_placeholder)
                    .error(fallbackRes)
                    .into(iv);
            return;
        }

        // 2️⃣ Không hỗ trợ gs:// trực tiếp
        if (s.startsWith("gs://")) {
            iv.setImageResource(fallbackRes);
            return;
        }

        // 3️⃣ Drawable nội bộ (sử dụng Map)
        s = s.replace(".png", "").replace(".jpg", "").replace(".jpeg", "").trim();
        Integer resId = DRAWABLE_MAP.get(s);
        iv.setImageResource(resId != null ? resId : fallbackRes);
    }
    private static boolean looksLikeUrl(String s) {
        String x = s.trim().toLowerCase(Locale.US);
        return x.startsWith("http://") || x.startsWith("https://");
    }

    private static String safe(String s) { return s == null ? "" : s; }

    public static class VH extends RecyclerView.ViewHolder {
        final ItemProductSmallBinding b;
        public VH(@NonNull ItemProductSmallBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
