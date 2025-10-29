package com.pro.milkteaapp.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.pro.milkteaapp.R;
import com.pro.milkteaapp.databinding.ItemReviewBinding;
import com.pro.milkteaapp.models.Review;
import com.pro.milkteaapp.utils.ImageLoader;

import java.util.List;

public class ReviewAdapter extends RecyclerView.Adapter<ReviewAdapter.VH> {

    private final List<Review> list;

    public ReviewAdapter(List<Review> list) { this.list = list; }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemReviewBinding b = ItemReviewBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Review r = list.get(position);
        h.b.tvName.setText(r.getName());
        h.b.tvComment.setText(r.getComment());
        h.b.ratingBar.setRating((float) r.getRating());

        // ✅ URL thì load URL; nếu là tên drawable (vd "ic_profile_tanthinh" hoặc "drawable:ic_profile_tanthinh") thì load drawable
        ImageLoader.load(h.b.imgAvatar, r.getAvatarUrl(), R.drawable.ic_profile);
    }

    @Override public int getItemCount() { return list.size(); }

    public static class VH extends RecyclerView.ViewHolder {
        final ItemReviewBinding b;
        public VH(@NonNull ItemReviewBinding b) { super(b.getRoot()); this.b = b; }
    }
}
