package com.pro.milkteaapp.adapter;

import android.annotation.SuppressLint;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.models.Category;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Adapter quản lý danh sách Category (cho cả Admin và User)
 */
public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.VH> {

    public interface OnCategoryActionListener {
        void onEdit(Category c);
        void onDelete(Category c);
    }

    private final OnCategoryActionListener listener;
    private final boolean adminMode;
    private List<Category> list = new ArrayList<>();

    public CategoryAdapter(OnCategoryActionListener l) { this(l, true); }

    public CategoryAdapter(OnCategoryActionListener l, boolean adminMode) {
        this.listener = l;
        this.adminMode = adminMode;
        setHasStableIds(true);
    }

    public void submit(List<Category> newList) {
        List<Category> old = this.list;
        List<Category> copy = new ArrayList<>(newList);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return old.size(); }
            @Override public int getNewListSize() { return copy.size(); }
            @Override public boolean areItemsTheSame(int i, int j) {
                String a = safe(old.get(i).getId());
                String b = safe(copy.get(j).getId());
                return !TextUtils.isEmpty(a) && a.equals(b);
            }
            @Override public boolean areContentsTheSame(int i, int j) {
                Category A = old.get(i), B = copy.get(j);
                return Objects.equals(safe(A.getName()), safe(B.getName()))
                        && A.isActive() == B.isActive()
                        && Objects.equals(safe(A.getImageUrl()), safe(B.getImageUrl()));
            }
        });
        this.list = copy;
        diff.dispatchUpdatesTo(this);
    }

    public List<Category> getCurrent() { return new ArrayList<>(list); }

    @Override
    public long getItemId(int position) {
        if (position < 0 || position >= list.size()) return RecyclerView.NO_ID;
        String id = list.get(position).getId();
        return !TextUtils.isEmpty(id) ? id.hashCode() : safe(list.get(position).getName()).hashCode();
    }

    @Override
    public int getItemViewType(int position) { return adminMode ? 1 : 0; }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        @LayoutRes int layout = (viewType == 1)
                ? R.layout.item_category_admin
                : R.layout.item_category;
        View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new VH(v, viewType == 1);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Category c = list.get(position);
        h.bind(c);
    }

    @Override
    public int getItemCount() { return list.size(); }

    /* =================================================== */

    class VH extends RecyclerView.ViewHolder {
        TextView tvName;
        ImageView img;
        // Admin-only
        TextView tvActive;
        ImageView btnEdit, btnDelete;
        final boolean admin;

        VH(@NonNull View itemView, boolean admin) {
            super(itemView);
            this.admin = admin;
            tvName = itemView.findViewById(R.id.categoryName);
            img    = itemView.findViewById(R.id.categoryImage);
            if (admin) {
                tvActive = itemView.findViewById(R.id.tvActive);
                btnEdit  = itemView.findViewById(R.id.btnEdit);
                btnDelete= itemView.findViewById(R.id.btnDelete);
            }
        }

        void bind(Category c) {
            tvName.setText(safe(c.getName()));

            String key = safe(c.getImageUrl());
            if (key.isEmpty()) {
                img.setImageResource(R.drawable.milktea);
            } else if (isUrl(key)) {
                // ✅ Load từ URL (Firebase Storage hoặc HTTP link)
                Glide.with(itemView)
                        .load(key)
                        .apply(new RequestOptions()
                                .placeholder(R.drawable.milktea)
                                .error(R.drawable.milktea)
                                .centerCrop())
                        .into(img);
            } else {
                // ✅ Load từ drawable (tên ảnh trong res/drawable, ví dụ: "tra_sua.png")
                String resName = normalizeResName(key);
                @SuppressLint("DiscouragedApi") int resId = itemView.getContext().getResources()
                        .getIdentifier(resName, "drawable",
                                itemView.getContext().getPackageName());
                if (resId != 0) img.setImageResource(resId);
                else img.setImageResource(R.drawable.milktea);
            }

            if (admin) {
                tvActive.setText(c.isActive() ? "Active" : "Inactive");

                if (btnEdit != null)
                    btnEdit.setOnClickListener(v -> {
                        if (listener != null) listener.onEdit(c);
                    });

                if (btnDelete != null)
                    btnDelete.setOnClickListener(v -> {
                        if (listener != null) listener.onDelete(c);
                    });

                itemView.setOnClickListener(v -> {
                    if (listener != null) listener.onEdit(c);
                });
            }
        }

        /** Kiểm tra xem chuỗi có phải URL hợp lệ không */
        private boolean isUrl(String s) {
            String x = s.toLowerCase();
            return x.startsWith("http://") || x.startsWith("https://")
                    || x.startsWith("gs://") || x.startsWith("android.resource://");
        }

        /** Chuẩn hóa tên resource (bỏ đuôi .png/.jpg và "drawable/") */
        private String normalizeResName(String s) {
            String n = s.trim();
            int slash = n.lastIndexOf('/');
            if (slash >= 0 && slash < n.length() - 1) n = n.substring(slash + 1);
            int dot = n.lastIndexOf('.');
            if (dot > 0) n = n.substring(0, dot);
            return n;
        }
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
