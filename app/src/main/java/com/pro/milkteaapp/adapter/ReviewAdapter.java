package com.pro.milkteaapp.adapter;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.pro.milkteaapp.R;
import com.pro.milkteaapp.databinding.ItemReviewBinding;
import com.pro.milkteaapp.models.Review;
import com.pro.milkteaapp.utils.ImageLoader;

import java.util.List;

public class ReviewAdapter extends RecyclerView.Adapter<ReviewAdapter.VH> {

    private final List<Review> list;

    public ReviewAdapter(List<Review> list) {
        this.list = list;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemReviewBinding b = ItemReviewBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Review r = list.get(position);
        Context context = h.b.getRoot().getContext();

        h.b.tvName.setText(r.getName());
        h.b.tvComment.setText(r.getComment());
        h.b.ratingBar.setRating((float) r.getRating());

        // ⭐ Đổi màu sao vàng
        h.b.ratingBar.setProgressTintList(ContextCompat.getColorStateList(context, R.color.gold));
        h.b.ratingBar.setSecondaryProgressTintList(ContextCompat.getColorStateList(context, R.color.gold));
        h.b.ratingBar.setProgressBackgroundTintList(ContextCompat.getColorStateList(context, R.color.gray_200));

        // 💬 Mô tả cảm xúc theo sao
        h.b.tvRatingDesc.setText(getRatingDescription(r.getRating()));

        // Ảnh đại diện
        ImageLoader.load(h.b.imgAvatar, r.getAvatarUrl(), R.drawable.ic_profile);
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public static class VH extends RecyclerView.ViewHolder {
        final ItemReviewBinding b;
        public VH(@NonNull ItemReviewBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    // ⚙️ Helper: sao → mô tả
    private static String getRatingDescription(double rating) {
        int r = (int) Math.round(rating);
        return switch (r) {
            case 5 -> "Rất hài lòng 😍";
            case 4 -> "Hài lòng 🙂";
            case 3 -> "Bình thường 😐";
            case 2 -> "Tệ 😕";
            case 1 -> "Rất tệ 😞";
            default -> "";
        };
    }
}
