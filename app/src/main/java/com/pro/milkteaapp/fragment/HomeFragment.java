package com.pro.milkteaapp.fragment;

import android.animation.Animator;
import android.os.Bundle;
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
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.adapters.BannerAdapter;
import com.pro.milkteaapp.adapters.ProductsSmallAdapter;
import com.pro.milkteaapp.adapters.ReviewAdapter;
import com.pro.milkteaapp.models.Products;
import com.pro.milkteaapp.models.Review;

import java.util.ArrayList;
import java.util.List;

/**
 * Trang chủ:
 * - Banner (tĩnh)
 * - Danh mục (tĩnh)
 * - Sản phẩm nổi bật (demo)
 * - Review khách hàng (demo)
 * - Nút fabProduct (Extended FAB) + Lottie -> mở tab Sản phẩm qua MainActivity.openProductsTab()
 * YÊU CẦU XML:
 * - Extended FAB id = @+id/fabProduct
 * - LottieAnimationView id = @+id/lavCart (app:lottie_rawRes="@raw/milktea_splash")
 * - NestedScrollView id = @+id/scrollHome (để anchor FAB & xử lý shrink/extend khi cuộn)
 */
public class HomeFragment extends Fragment {

    private ViewPager2 vpBanner;
    private RecyclerView rvTopPrice, rvReviews;
    private SwipeRefreshLayout swipeRefresh;
    private ExtendedFloatingActionButton fabProduct;
    private LottieAnimationView lavCart; // Lottie overlay chồng lên FAB
    private NestedScrollView scrollHome;

    private ProductsSmallAdapter topPriceAdapter;
    private ReviewAdapter reviewAdapter;

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
        setupTopProducts();
        setupReviewList();
        setupSwipeRefresh();
        setupFabWithLottie();     // Hiệu ứng + điều hướng qua MainActivity
        setupFabScrollBehavior(); // Co/giãn FAB khi cuộn
    }

    private void initViews(@NonNull View v) {
        vpBanner     = v.findViewById(R.id.vpBanner);
        rvTopPrice   = v.findViewById(R.id.rvTopPrice);
        rvReviews    = v.findViewById(R.id.rvReviews);
        swipeRefresh = v.findViewById(R.id.swipeRefresh);
        fabProduct   = v.findViewById(R.id.fabProduct); // ✅ id theo yêu cầu
        lavCart      = v.findViewById(R.id.lavCart);    // ✅ Lottie overlay
        scrollHome   = v.findViewById(R.id.scrollHome); // ✅ dùng cho anchor & scroll
    }

    // --- BANNER ---
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

    // --- DANH SÁCH SẢN PHẨM NỔI BẬT (DEMO) ---
    private void setupTopProducts() {
        rvTopPrice.setLayoutManager(
                new LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false));
        rvTopPrice.setNestedScrollingEnabled(false);
        rvTopPrice.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rvTopPrice.addItemDecoration(new SpacingDecoration(dp(8)));

        topPriceAdapter = new ProductsSmallAdapter(requireContext(), p -> {});
        rvTopPrice.setAdapter(topPriceAdapter);

        List<Products> demo = new ArrayList<>();

        demo.add(new Products() {{
            setId("demo-5"); setName("Trà Sữa Olong");
            setPrice(65000); setCategory("Trà Sữa");
            setImageUrl("milktea_olong");
            setDescription("Olong béo, hậu ngọt.");
            setSearchableName("tra sua oolong");
        }});

        demo.add(new Products() {{
            setId("demo-1"); setName("Trà Sữa Trân Châu");
            setPrice(50000); setCategory("Trà Sữa");
            setImageUrl("milktea");
            setDescription("Truyền thống, đậm vị.");
            setSearchableName("tra sua tran chau");
        }});

        demo.add(new Products() {{
            setId("demo-2"); setName("Trà Sữa Matcha");
            setPrice(46000); setCategory("Trà Sữa");
            setImageUrl("milktea_matcha");
            setDescription("Matcha thơm nhẹ, không ngấy.");
            setSearchableName("tra sua matcha");
        }});

        demo.add(new Products() {{
            setId("demo-3"); setName("Trà Sữa Caramel");
            setPrice(48000); setCategory("Trà Sữa");
            setImageUrl("milktea_caramel");
            setDescription("Thơm caramel béo ngậy.");
            setSearchableName("tra sua caramel");
        }});

        demo.add(new Products() {{
            setId("demo-4"); setName("Trà Sữa Socola");
            setPrice(42000); setCategory("Trà Sữa");
            setImageUrl("milktea_chocolate");
            setDescription("Đậm vị cacao.");
            setSearchableName("tra sua socola");
        }});

        topPriceAdapter.submitList(demo);
    }

    // --- DANH SÁCH REVIEW ---
    private void setupReviewList() {
        rvReviews.setLayoutManager(
                new LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false));
        rvReviews.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rvReviews.addItemDecoration(new SpacingDecoration(dp(8)));

        List<Review> demoReviews = new ArrayList<>();
        demoReviews.add(new Review("Minh Anh", "Trà sữa rất ngon, topping nhiều!", "https://i.pravatar.cc/150?img=5", 5));
        demoReviews.add(new Review("Hữu Duy", "Đóng gói đẹp, giao hàng nhanh.", "https://i.pravatar.cc/150?img=12", 4));
        demoReviews.add(new Review("Thảo Nhi", "Uống ngon nhưng hơi ngọt.", "https://i.pravatar.cc/150?img=45", 3.5));
        demoReviews.add(new Review("Bảo Trân", "Nhân viên dễ thương, tư vấn nhiệt tình.", "https://i.pravatar.cc/150?img=23", 5));

        reviewAdapter = new ReviewAdapter(demoReviews);
        rvReviews.setAdapter(reviewAdapter);
    }

    // --- REFRESH ---
    private void setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener(() -> {
            topPriceAdapter.notifyDataSetChanged();
            reviewAdapter.notifyDataSetChanged();
            rvTopPrice.smoothScrollToPosition(0);
            rvReviews.smoothScrollToPosition(0);
            swipeRefresh.setRefreshing(false);
        });
    }

    // --- FAB + LOTTIE: chạy animation rồi mở tab Sản phẩm ---
    private void setupFabWithLottie() {
        if (fabProduct == null) return;

        fabProduct.setOnClickListener(view -> {
            // Chống double-click
            fabProduct.setEnabled(false);

            // 1) Thu nhỏ để ẩn text/icon trong lúc hiệu ứng
            if (fabProduct.isExtended()) fabProduct.shrink();

            // 2) Hiện Lottie chồng lên FAB và play
            if (lavCart != null) {
                lavCart.setVisibility(View.VISIBLE);
                lavCart.setProgress(0f);
                lavCart.removeAllAnimatorListeners();
                lavCart.playAnimation();

                lavCart.addAnimatorListener(new Animator.AnimatorListener() {
                    @Override public void onAnimationStart(@NonNull Animator animation) {}

                    @Override public void onAnimationEnd(@NonNull Animator animation) {
                        // Ẩn Lottie & khôi phục FAB
                        lavCart.setVisibility(View.GONE);
                        fabProduct.extend();
                        fabProduct.setEnabled(true);

                        // Điều hướng: gọi MainActivity.openProductsTab()
                        if (requireActivity() instanceof com.pro.milkteaapp.activity.MainActivity) {
                            ((com.pro.milkteaapp.activity.MainActivity) requireActivity()).openProductsTab();
                        }
                    }

                    @Override public void onAnimationCancel(@NonNull Animator animation) {
                        onAnimationEnd(animation);
                    }

                    @Override public void onAnimationRepeat(@NonNull Animator animation) {}
                });
            } else {
                // Nếu chưa khai báo Lottie trong XML, fallback điều hướng ngay
                fabProduct.extend();
                fabProduct.setEnabled(true);
                if (requireActivity() instanceof com.pro.milkteaapp.activity.MainActivity) {
                    ((com.pro.milkteaapp.activity.MainActivity) requireActivity()).openProductsTab();
                }
            }
        });
    }

    // --- Co/giãn FAB khi cuộn để gọn UI ---
    private void setupFabScrollBehavior() {
        if (scrollHome == null || fabProduct == null) return;

        scrollHome.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener)
                (sv, x, y, ox, oy) -> {
                    if (y > oy + 10 && fabProduct.isExtended()) {
                        fabProduct.shrink();
                    } else if (y < oy - 10 && !fabProduct.isExtended()) {
                        fabProduct.extend();
                    }
                }
        );
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }

    /** ItemDecoration: khoảng cách giữa các item RecyclerView */
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

    // --- Lifecycle cleanup: đảm bảo animation dừng khi rời màn hình ---
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
}
