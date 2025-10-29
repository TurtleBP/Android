package com.pro.milkteaapp.fragment;

import android.animation.Animator;
import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.widget.CompositePageTransformer;
import androidx.viewpager2.widget.MarginPageTransformer;
import androidx.viewpager2.widget.ViewPager2;

import com.airbnb.lottie.LottieAnimationView;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.adapter.BannerAdapter;
import com.pro.milkteaapp.adapter.ProductsSmallAdapter;
import com.pro.milkteaapp.adapter.ReviewAdapter;
import com.pro.milkteaapp.models.Products;
import com.pro.milkteaapp.models.Review;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {

    private ViewPager2 vpBanner;
    private RecyclerView rvTopPrice, rvReviews;
    private SwipeRefreshLayout swipeRefresh;
    private ExtendedFloatingActionButton fabProduct;
    private LottieAnimationView lavCart;
    private NestedScrollView scrollHome;

    private ProductsSmallAdapter topPriceAdapter;
    private ReviewAdapter reviewAdapter;

    // Giữ sản phẩm nổi bật (demo)
    private final List<Products> topProducts = new ArrayList<>();
    // Đánh giá khách hàng
    private final List<Review> reviews = new ArrayList<>();

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public HomeFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        initViews(v);
        setupBanner();
        setupTopProducts();     // ✅ giữ sản phẩm nổi bật
        setupReviewList();      // ✅ hiển thị đánh giá khách hàng
        setupSwipeRefresh();
        setupFabWithLottie();
        setupFabScrollBehavior();
    }

    private void initViews(@NonNull View v) {
        vpBanner     = v.findViewById(R.id.vpBanner);
        rvTopPrice   = v.findViewById(R.id.rvTopPrice);
        rvReviews    = v.findViewById(R.id.rvReviews);
        swipeRefresh = v.findViewById(R.id.swipeRefresh);
        lavCart      = v.findViewById(R.id.lavCart);
        scrollHome   = v.findViewById(R.id.scrollHome);
    }

    // --- Banner ---
    private void setupBanner() {
        BannerAdapter bannerAdapter = new BannerAdapter(
                new int[]{R.drawable.banner_1, R.drawable.banner_2, R.drawable.banner_3}
        );
        vpBanner.setAdapter(bannerAdapter);
        vpBanner.setOffscreenPageLimit(3);
        CompositePageTransformer transformer = new CompositePageTransformer();
        transformer.addTransformer(new MarginPageTransformer(dp(12)));
        vpBanner.setPageTransformer(transformer);
    }

    // --- Sản phẩm nổi bật (demo tĩnh, giữ nguyên) ---
    private void setupTopProducts() {
        rvTopPrice.setLayoutManager(
                new LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false));
        rvTopPrice.setNestedScrollingEnabled(false);
        rvTopPrice.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rvTopPrice.addItemDecoration(new SpacingDecoration(dp(8)));

        topPriceAdapter = new ProductsSmallAdapter(requireContext(), p -> {});
        rvTopPrice.setAdapter(topPriceAdapter);

        topProducts.clear();
        topProducts.add(new Products() {{ setId("demo-5"); setName("Trà Sữa Olong");   setPrice(65000.0); setCategory("Trà Sữa"); setImageUrl("milktea_olong");   setDescription("Olong béo, hậu ngọt.");      setSearchableName("tra sua oolong"); }});
        topProducts.add(new Products() {{ setId("demo-1"); setName("Trà Sữa Trân Châu"); setPrice(50000.0); setCategory("Trà Sữa"); setImageUrl("milktea");         setDescription("Truyền thống, đậm vị.");     setSearchableName("tra sua tran chau"); }});
        topProducts.add(new Products() {{ setId("demo-2"); setName("Trà Sữa Matcha");  setPrice(46000.0); setCategory("Trà Sữa"); setImageUrl("milktea_matcha");  setDescription("Matcha thơm nhẹ, không ngấy."); setSearchableName("tra sua matcha"); }});
        topProducts.add(new Products() {{ setId("demo-3"); setName("Trà Sữa Caramel"); setPrice(48000.0); setCategory("Trà Sữa"); setImageUrl("milktea_caramel"); setDescription("Thơm caramel béo ngậy.");   setSearchableName("tra sua caramel"); }});
        topProducts.add(new Products() {{ setId("demo-4"); setName("Trà Sữa Socola");  setPrice(42000.0); setCategory("Trà Sữa"); setImageUrl("milktea_chocolate"); setDescription("Đậm vị cacao.");            setSearchableName("tra sua socola"); }});

        topPriceAdapter.submitList(new ArrayList<>(topProducts));
    }

    // --- Reviews theo đơn hàng ---
    private void setupReviewList() {
        rvReviews.setLayoutManager(
                new LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false));
        rvReviews.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rvReviews.addItemDecoration(new SpacingDecoration(dp(8)));

        reviewAdapter = new ReviewAdapter(reviews);
        rvReviews.setAdapter(reviewAdapter);

        loadRecentOrderReviews();
    }

    /** Lấy đánh giá từ orders (rating > 0), sắp xếp sao GIẢM DẦN; trong cùng số sao thì ratedAt mới nhất trước */
    private void loadRecentOrderReviews() {
        final int LIMIT = 20;

        // Lưu ý: cần composite index (rating DESC, ratedAt DESC) nếu Firestore yêu cầu
        Query q = db.collection("orders")
                .whereGreaterThan("rating", 0)
                .orderBy("rating", Query.Direction.DESCENDING)                // ⭐ sắp xếp sao cao -> thấp
                .orderBy("ratedAt", Query.Direction.DESCENDING)               // trong cùng số sao: mới nhất trước
                .limit(LIMIT);

        q.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) return;

            List<DocumentSnapshot> docs = task.getResult().getDocuments();
            reviews.clear();

            // Tạo list ban đầu (chưa enrich)
            for (DocumentSnapshot d : docs) {
                Number ratingNum = (Number) d.get("rating");
                if (ratingNum == null) continue;

                double stars   = ratingNum.doubleValue();
                String comment = s(d.get("review"));

                // tạm: để “Người dùng” nếu chưa lấy được tên
                reviews.add(new Review("Người dùng", comment, null, stars));
            }
            reviewAdapter.notifyDataSetChanged();

            // Enrich theo userId (load từng hồ sơ user rồi set lại item tương ứng)
            int idx = 0;
            for (DocumentSnapshot d : docs) {
                Number ratingNum = (Number) d.get("rating");
                if (ratingNum == null) continue;

                final int position  = idx;
                final String comment = s(d.get("review"));
                final double stars   = ((Number) d.get("rating")).doubleValue();
                final String userId  = s(d.get("userId"));

                if (!TextUtils.isEmpty(userId)) {
                    db.collection("users").document(userId).get()
                            .addOnSuccessListener(u -> {
                                String displayName = s(u.get("displayName"));
                                String fullName    = s(u.get("fullName"));
                                String nameField   = s(u.get("name"));
                                String email       = s(u.get("email"));
                                String avatar      = firstNonEmpty(s(u.get("avatar")), s(u.get("photoUrl"))); // bạn sẽ thêm avatar sau

                                String finalName = firstNonEmpty(displayName, fullName, nameField, emailPrefix(email), "Người dùng");

                                if (position >= 0 && position < reviews.size()) {
                                    Review r = new Review(finalName, comment, avatar, stars);
                                    reviews.set(position, r);
                                    reviewAdapter.notifyItemChanged(position);
                                }
                            });
                }
                idx++;
            }
        });
    }

    // --- Refresh ---
    @SuppressLint("NotifyDataSetChanged")
    private void setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener(() -> {
            loadRecentOrderReviews();           // reload reviews
            topPriceAdapter.notifyDataSetChanged();
            rvTopPrice.smoothScrollToPosition(0);
            rvReviews.smoothScrollToPosition(0);
            swipeRefresh.setRefreshing(false);
        });
    }

    // --- FAB + Lottie mở tab Sản phẩm ---
    private void setupFabWithLottie() {
        if (fabProduct == null) return;

        fabProduct.setOnClickListener(view -> {
            fabProduct.setEnabled(false);
            if (fabProduct.isExtended()) fabProduct.shrink();

            if (lavCart != null) {
                lavCart.setVisibility(View.VISIBLE);
                lavCart.setProgress(0f);
                lavCart.removeAllAnimatorListeners();
                lavCart.playAnimation();
                lavCart.addAnimatorListener(new Animator.AnimatorListener() {
                    @Override public void onAnimationStart(@NonNull Animator animation) {}
                    @Override public void onAnimationEnd(@NonNull Animator animation) {
                        lavCart.setVisibility(View.GONE);
                        fabProduct.extend();
                        fabProduct.setEnabled(true);
                        if (requireActivity() instanceof com.pro.milkteaapp.activity.MainActivity) {
                            ((com.pro.milkteaapp.activity.MainActivity) requireActivity()).openProductsTab();
                        }
                    }
                    @Override public void onAnimationCancel(@NonNull Animator animation) { onAnimationEnd(animation); }
                    @Override public void onAnimationRepeat(@NonNull Animator animation) {}
                });
            } else {
                fabProduct.extend();
                fabProduct.setEnabled(true);
                if (requireActivity() instanceof com.pro.milkteaapp.activity.MainActivity) {
                    ((com.pro.milkteaapp.activity.MainActivity) requireActivity()).openProductsTab();
                }
            }
        });
    }

    private void setupFabScrollBehavior() {
        if (scrollHome == null || fabProduct == null) return;
        scrollHome.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener)
                (sv, x, y, ox, oy) -> {
                    if (y > oy + 10 && fabProduct.isExtended()) {
                        fabProduct.shrink();
                    } else if (y < oy - 10 && !fabProduct.isExtended()) {
                        fabProduct.extend();
                    }
                });
    }

    private int dp(int v) { return Math.round(getResources().getDisplayMetrics().density * v); }

    public static class SpacingDecoration extends RecyclerView.ItemDecoration {
        private final int space;
        public SpacingDecoration(int space) { this.space = space; }
        @Override
        public void getItemOffsets(@NonNull android.graphics.Rect outRect,
                                   @NonNull View view,
                                   @NonNull RecyclerView parent,
                                   @NonNull RecyclerView.State state) {
            outRect.set(space, space, space, space);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (lavCart != null && lavCart.isAnimating()) {
            lavCart.cancelAnimation();
            lavCart.setVisibility(View.GONE);
        }
        if (fabProduct != null && !fabProduct.isExtended()) {
            fabProduct.extend();
        }
        if (fabProduct != null) fabProduct.setEnabled(true);
    }

    // -------- helpers --------
    private static String s(Object o) { return o == null ? null : String.valueOf(o); }

    private static String emailPrefix(String email) {
        if (email == null) return null;
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    private static String firstNonEmpty(String... arr) {
        if (arr == null) return null;
        for (String s : arr) if (s != null && !s.trim().isEmpty()) return s.trim();
        return null;
    }
}
