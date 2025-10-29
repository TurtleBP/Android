    package com.pro.milkteaapp.adapter.admin;

    import android.view.LayoutInflater;
    import android.view.View;
    import android.view.ViewGroup;
    import android.widget.ImageButton;
    import android.widget.ImageView;
    import android.widget.RatingBar;
    import android.widget.TextView;

    import androidx.annotation.NonNull;
    import androidx.recyclerview.widget.DiffUtil;
    import androidx.recyclerview.widget.ListAdapter;
    import androidx.recyclerview.widget.RecyclerView;

    import com.bumptech.glide.Glide;
    import com.pro.milkteaapp.R;
    import com.pro.milkteaapp.utils.ImageLoader;

    import java.text.SimpleDateFormat;
    import java.util.Date;
    import java.util.Locale;

    public class AdminRatingsAdapter extends ListAdapter<AdminRatingsAdapter.Item, AdminRatingsAdapter.VH> {

        public interface OnDeleteClick { void onDelete(@NonNull Item item); }
        private final OnDeleteClick onDeleteClick;

        public AdminRatingsAdapter(OnDeleteClick onDeleteClick) {
            super(DIFF);
            this.onDeleteClick = onDeleteClick;
        }

        public static final DiffUtil.ItemCallback<Item> DIFF = new DiffUtil.ItemCallback<Item>() {
            @Override public boolean areItemsTheSame(@NonNull Item a, @NonNull Item b) {
                return a.orderId.equals(b.orderId);
            }
            @Override public boolean areContentsTheSame(@NonNull Item a, @NonNull Item b) {
                return a.equals(b);
            }
        };

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_admin_rating, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Item it = getItem(position);

            h.tvUser.setText(it.userName == null || it.userName.isEmpty() ? "Người dùng" : it.userName);
            h.ratingBar.setRating((float) it.stars);
            h.tvComment.setText(it.comment == null ? "" : it.comment);

            String when = it.ratedAtMs > 0
                    ? new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(it.ratedAtMs))
                    : "--";
            h.tvMeta.setText("#" + it.orderId + " · " + when);

            // Avatar: URL hoặc tên drawable
            if (it.avatar != null && !it.avatar.isEmpty()) {
                try {
                    ImageLoader.load(h.imgAvatar, it.avatar, R.drawable.ic_avatar_default);
                } catch (Throwable t) {
                    Glide.with(h.imgAvatar.getContext())
                            .load(R.drawable.ic_avatar_default).into(h.imgAvatar);
                }
            } else {
                Glide.with(h.imgAvatar.getContext())
                        .load(R.drawable.ic_avatar_default).into(h.imgAvatar);
            }

            h.btnDelete.setOnClickListener(v -> {
                if (onDeleteClick != null) onDeleteClick.onDelete(it);
            });
        }

        public static class VH extends RecyclerView.ViewHolder {
            ImageView imgAvatar;
            TextView tvUser, tvComment, tvMeta;
            RatingBar ratingBar;
            ImageButton btnDelete;

            public VH(@NonNull View itemView) {
                super(itemView);
                imgAvatar = itemView.findViewById(R.id.imgAvatar);
                tvUser    = itemView.findViewById(R.id.tvUser);
                tvComment = itemView.findViewById(R.id.tvComment);
                tvMeta    = itemView.findViewById(R.id.tvMeta);
                ratingBar = itemView.findViewById(R.id.ratingBar);
                btnDelete = itemView.findViewById(R.id.btnDelete);
            }
        }

        // Model hiển thị Rating trên admin
        public static class Item {
            public String orderId;
            public String userId;
            public String userName;
            public String avatar;   // URL hoặc tên drawable
            public double stars;
            public String comment;
            public long ratedAtMs;

            public Item() {}

            public Item(String orderId, String userId, String userName,
                        String avatar, double stars, String comment, long ratedAtMs) {
                this.orderId = orderId;
                this.userId = userId;
                this.userName = userName;
                this.avatar = avatar;
                this.stars = stars;
                this.comment = comment;
                this.ratedAtMs = ratedAtMs;
            }

            @Override public boolean equals(Object o) {
                if (!(o instanceof Item)) return false;
                Item b = (Item) o;
                return safeEq(orderId, b.orderId) &&
                        safeEq(userId, b.userId) &&
                        safeEq(userName, b.userName) &&
                        safeEq(avatar, b.avatar) &&
                        stars == b.stars &&
                        safeEq(comment, b.comment) &&
                        ratedAtMs == b.ratedAtMs;
            }

            private boolean safeEq(Object a, Object b) {
                if (a == b) return true;
                if (a == null || b == null) return false;
                return a.equals(b);
            }
        }
    }
