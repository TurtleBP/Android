package com.pro.milkteaapp.adapters;

import android.content.Context;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.databinding.ItemProductBinding;
import com.pro.milkteaapp.models.Products;
import com.pro.milkteaapp.utils.MoneyUtils;

import java.util.Locale;
import java.util.Objects;

public class ProductsAdapter extends ListAdapter<Products, ProductsAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(Products product);
    }

    private static final String TAG = "ProductsAdapter";

    private final OnItemClickListener listener;
    private static final long CLICK_DEBOUNCE_MS = 300L;
    private long lastClickTime = 0L;

    private final boolean isAdmin;

    public ProductsAdapter(@NonNull Context context, OnItemClickListener listener) {
        this(context, listener, inferIsAdmin(context));
    }

    public ProductsAdapter(@NonNull Context context, OnItemClickListener listener, boolean isAdmin) {
        super(DIFF_CALLBACK);
        this.listener = listener;
        this.isAdmin = isAdmin;
        setHasStableIds(true);
    }

    @Deprecated
    public ProductsAdapter(OnItemClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
        this.isAdmin = false;
        setHasStableIds(true);
    }

    private static boolean inferIsAdmin(Context context) {
        String role = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .getString("role", "user");
        return "admin".equalsIgnoreCase(role);
    }

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
                public boolean areContentsTheSame(@NonNull Products oldItem, @NonNull Products newItem) {
                    return Objects.equals(oldItem.getName(), newItem.getName())
                            && oldItem.getPrice() == newItem.getPrice()
                            && Objects.equals(oldItem.getImageUrl(), newItem.getImageUrl())
                            && Objects.equals(oldItem.getDescription(), newItem.getDescription())
                            && Objects.equals(oldItem.getCategory(), newItem.getCategory());
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
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemProductBinding binding = ItemProductBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Products product = getItem(position);
        if (product == null) return;

        // Tên
        String displayName = product.getName();
        if (TextUtils.isEmpty(displayName)) {
            displayName = holder.itemView.getContext().getString(R.string.unknown_item);
        }
        holder.binding.milkTeaName.setText(displayName);

        // Giá (Products.price có thể là double hoặc Double; MoneyUtils đã xử lý)
        holder.binding.milkTeaPrice.setText(MoneyUtils.formatVnd(product.getPrice()));

        // A11y
        holder.binding.milkTeaImage.setContentDescription(displayName);

        // Ảnh: URL / resource name / file or content URI
        bindImage(holder, product.getImageUrl(), R.drawable.milktea);

        // Click debounce
        holder.itemView.setOnClickListener(v -> {
            long now = SystemClock.elapsedRealtime();
            if (now - lastClickTime < CLICK_DEBOUNCE_MS) return;
            lastClickTime = now;
            if (listener != null) listener.onItemClick(product);
        });
    }

    /* =========================== IMAGE LOADER =========================== */

    private void bindImage(@NonNull ViewHolder holder, String raw, int fallbackRes) {
        final Context ctx = holder.itemView.getContext();

        // 0) Null/empty → fallback
        if (TextUtils.isEmpty(raw)) {
            holder.binding.milkTeaImage.setImageResource(fallbackRes);
            return;
        }

        // 1) Chuẩn hoá chuỗi nguồn
        String source = sanitize(raw);

        // 2) Phân loại
        if (looksLikeUrl(source)) {
            // URL mạng
            loadFromUrlWithFallback(holder, source, fallbackRes);
            return;
        }

        if (looksLikeLocalUri(source)) {
            // content:// hoặc file://
            Glide.with(ctx)
                    .load(source)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .placeholder(R.drawable.ic_placeholder)
                    .error(fallbackRes)
                    .into(holder.binding.milkTeaImage);
            return;
        }

        // 3) Tên resource nội bộ (tea_abc.png / tea_abc)
        String imageName = stripExt(source).trim().toLowerCase(Locale.US);
        int resId = ctx.getResources().getIdentifier(imageName, "drawable", ctx.getPackageName());
        if (resId != 0) {
            holder.binding.milkTeaImage.setImageResource(resId);
        } else {
            // Không tìm thấy resource → fallback
            holder.binding.milkTeaImage.setImageResource(fallbackRes);
            Log.w(TAG, "Drawable not found for name: " + imageName);
        }
    }

    /**
     * Thử load:
     *  - Nếu là http:// → ưu tiên chuyển sang https:// trước
     *  - Nếu fail → fallback về URL gốc (http) (cần bật cleartext nếu API 28+)
     */
    private void loadFromUrlWithFallback(@NonNull ViewHolder holder, @NonNull String url, int fallbackRes) {
        final Context ctx = holder.itemView.getContext();

        final String primary = preferHttps(url);       // https://... hoặc giữ nguyên nếu đã https
        final String secondary = restoreOriginalIfChanged(url, primary); // http://... nếu ban đầu là http

        RequestListener<android.graphics.drawable.Drawable> listener = new RequestListener<android.graphics.drawable.Drawable>() {
            @Override
            public boolean onLoadFailed(GlideException e, Object model, Target<android.graphics.drawable.Drawable> target, boolean isFirstResource) {
                Log.e(TAG, "Glide load failed: " + model, e);

                // Nếu đã thử https và có bản http gốc → thử lại lần 2 bằng http
                if (secondary != null && secondary.startsWith("http://")) {
                    Log.w(TAG, "Retrying image load with cleartext URL: " + secondary);
                    Glide.with(ctx)
                            .load(secondary)
                            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                            .placeholder(R.drawable.ic_placeholder)
                            .error(fallbackRes)
                            .into(holder.binding.milkTeaImage);
                    return true; // đã tự xử lý tiếp
                }
                return false; // để Glide hiển thị error()
            }

            @Override
            public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model, Target<android.graphics.drawable.Drawable> target, DataSource dataSource, boolean isFirstResource) {
                return false;
            }
        };

        Glide.with(ctx)
                .load(primary)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .placeholder(R.drawable.ic_placeholder)
                .error(fallbackRes)
                .listener(listener)
                .into(holder.binding.milkTeaImage);
    }

    /* =========================== HELPERS =========================== */

    private static String sanitize(String raw) {
        String s = raw.trim().replace('\\', '/');
        if (s.startsWith("//")) s = "https:" + s;     // protocol-relative
        // encode khoảng trắng
        s = s.replace(" ", "%20");
        return s;
    }

    private static boolean looksLikeUrl(String s) {
        String x = s.toLowerCase(Locale.US);
        return x.startsWith("http://") || x.startsWith("https://") || x.startsWith("//");
    }

    private static boolean looksLikeLocalUri(String s) {
        String x = s.toLowerCase(Locale.US);
        return x.startsWith("content://") || x.startsWith("file://");
    }

    private static String stripExt(String s) {
        if (s.endsWith(".png") || s.endsWith(".jpg") || s.endsWith(".jpeg") || s.endsWith(".webp")) {
            int dot = s.lastIndexOf('.');
            return dot > 0 ? s.substring(0, dot) : s;
        }
        return s;
    }

    /** Nếu là http:// → trả về https:// ưu tiên thử trước, ngược lại trả về nguyên văn */
    private static String preferHttps(String url) {
        if (url.startsWith("http://")) {
            return "https://" + url.substring("http://".length());
        }
        return url;
    }

    /** Nếu đã chuyển sang https và URL ban đầu là http → trả lại bản http để fallback; nếu không thì null */
    private static String restoreOriginalIfChanged(String original, String primary) {
        if (original.startsWith("http://") && !original.equals(primary)) {
            return original; // cho phép thử lại bằng http
        }
        return null;
    }

    private static String safe(String s) { return s == null ? "" : s; }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemProductBinding binding;
        ViewHolder(@NonNull ItemProductBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
