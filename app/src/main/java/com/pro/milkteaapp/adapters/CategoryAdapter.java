package com.pro.milkteaapp.adapters;

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

import com.pro.milkteaapp.R;
import com.pro.milkteaapp.models.Category;
// Nếu bạn đã dùng Glide ở adapter sản phẩm, có thể bật 2 dòng dưới và dùng cho ảnh URL
// import com.bumptech.glide.Glide;
// import com.bumptech.glide.request.RequestOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.VH> {

    public interface OnCategoryActionListener {
        void onEdit(Category c);
        void onDelete(Category c);
    }

    private final OnCategoryActionListener listener;
    private final boolean adminMode; // true = dùng layout admin, false = layout user
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
                if (!TextUtils.isEmpty(a) && !TextUtils.isEmpty(b)) return a.equals(b);
                return safe(old.get(i).getName()).equalsIgnoreCase(safe(copy.get(j).getName()));
            }
            @Override public boolean areContentsTheSame(int i, int j) {
                Category A = old.get(i), B = copy.get(j);
                return Objects.equals(safe(A.getName()), safe(B.getName())) && A.isActive() == B.isActive();
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
        if (!TextUtils.isEmpty(id)) return id.hashCode();
        return safe(list.get(position).getName()).hashCode();
    }

    @Override
    public int getItemViewType(int position) { return adminMode ? 1 : 0; }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        @LayoutRes int layout = (viewType == 1) ? R.layout.item_category_admin : R.layout.item_category;
        View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new VH(v, viewType == 1);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Category c = list.get(position);
        h.bind(c);
    }

    @Override public int getItemCount() { return list.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName;
        ImageView img;
        // Chỉ có ở layout admin
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

            // Ảnh category (nếu bạn có field imageUrl hoặc imageName trong Category)
            // Ưu tiên URL (Glide), fallback resource nếu bạn đặt ảnh trong drawable với tên trùng
            // String image = safe(c.getImageUrl()); // nếu có
            // if (image.startsWith("http")) {
            //     Glide.with(itemView).load(image)
            //         .apply(new RequestOptions().placeholder(R.drawable.ic_placeholder)
            //                                   .error(R.drawable.ic_placeholder))
            //         .into(img);
            // } else {
            //     int resId = itemView.getContext().getResources()
            //            .getIdentifier(image, "drawable", itemView.getContext().getPackageName());
            //     if (resId != 0) img.setImageResource(resId);
            //     else img.setImageResource(R.drawable.ic_placeholder);
            // }

            if (admin) {
                tvActive.setText(c.isActive() ? "Active" : "Inactive");

                if (btnEdit != null) {
                    btnEdit.setOnClickListener(v -> {
                        CategoryAdapter.OnCategoryActionListener l = getListener();
                        if (l != null) l.onEdit(c);
                    });
                }
                if (btnDelete != null) {
                    btnDelete.setOnClickListener(v -> {
                        CategoryAdapter.OnCategoryActionListener l = getListener();
                        if (l != null) l.onDelete(c);
                    });
                }
                itemView.setOnClickListener(v -> {
                    CategoryAdapter.OnCategoryActionListener l = getListener();
                    if (l != null) l.onEdit(c);
                });
            } else {
                // user mode: click item → có thể filter theo category, tuỳ bạn sử dụng
            }
        }

        private CategoryAdapter.OnCategoryActionListener getListener() {
            RecyclerView rv = (RecyclerView) itemView.getParent();
            if (rv == null) return null;
            RecyclerView.Adapter<?> ad = rv.getAdapter();
            if (ad instanceof CategoryAdapter) return ((CategoryAdapter) ad).listener;
            return null;
        }
    }

    private static String safe(String s){ return s == null ? "" : s; }
}
