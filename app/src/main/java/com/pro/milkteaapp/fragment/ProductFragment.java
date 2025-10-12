package com.pro.milkteaapp.fragment;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.adapters.ProductsSectionAdapter;
import com.pro.milkteaapp.databinding.FragmentProductBinding;
import com.pro.milkteaapp.models.Products;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Hiển thị sản phẩm theo từng category (SECTION HEADER + GRID 2 cột).
 *  AppBar mở: hiện Greeting + Search, ẩn dropdown.
 *  AppBar thu gọn: hiện dropdown chọn category và tự cuộn đến section tương ứng.
 */
public class ProductFragment extends Fragment implements ProductsSectionAdapter.OnProductClick {

    private static final String ALL = "Tất cả";

    private FragmentProductBinding binding;
    private ProductsSectionAdapter adapter;

    private final List<Products> allProducts = new ArrayList<>();
    private final List<String> categories = new ArrayList<>();
    private final Map<String, Integer> headerPositions = new HashMap<>();

    private FirebaseFirestore db;
    private ListenerRegistration subProducts;

    // AppBar & dropdown (khi thu gọn)
    private AppBarLayout appBar;
    private MaterialAutoCompleteTextView actvCategoryCollapsed;
    private ArrayAdapter<String> ddAdapter; // giữ tham chiếu để cập nhật

    // ====== Lifecycle ======
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProductBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // ===== Firestore & Adapter =====
        db = FirebaseFirestore.getInstance();
        adapter = new ProductsSectionAdapter(this);

        // ===== RecyclerView: Grid 2 cột (HEADER span=2, ITEM span=1) =====
        GridLayoutManager glm = new GridLayoutManager(getContext(), 2);
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) {
                int viewType = adapter.getItemViewType(position);
                return (viewType == ProductsSectionAdapter.TYPE_HEADER) ? 2 : 1;
            }
        });
        binding.recyclerView.setLayoutManager(glm);
        binding.recyclerView.setHasFixedSize(true);
        binding.recyclerView.setAdapter(adapter);

        // ===== AppBar + Collapsed dropdown =====
        appBar = binding.appbar;
        actvCategoryCollapsed = binding.actvCategoryCollapsed;

        // Ẩn thanh dropdown lúc đầu (tránh che greeting)
        binding.collapsedBarContainer.setAlpha(0f);
        binding.collapsedBarContainer.setVisibility(View.GONE);
        setupAppBarOffsetBehavior(); // sẽ fade-in khi AppBar collapsed

        // ===== Dropdown adapter (rỗng trước, sẽ nạp sau khi load categories) =====
        ddAdapter = new ArrayAdapter<>(
                requireContext(),
                R.layout._item_dropdown, // TextView 48dp (bạn đã tạo)
                new ArrayList<>()
        );
        actvCategoryCollapsed.setAdapter(ddAdapter);

        // Mặc định hiển thị "Tất cả" (không trigger listener)
        actvCategoryCollapsed.setText(ALL, false);

        // UX mở dropdown đúng kiểu ExposedDropdown
        actvCategoryCollapsed.setThreshold(0);
        actvCategoryCollapsed.setOnClickListener(v -> actvCategoryCollapsed.showDropDown());
        actvCategoryCollapsed.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) actvCategoryCollapsed.showDropDown();
        });
        actvCategoryCollapsed.setOnTouchListener((v, e) -> {
            actvCategoryCollapsed.showDropDown();
            return false;
        });

        // Khi chọn category → CUỘN MƯỢT tới section (không rebuild)
        actvCategoryCollapsed.setOnItemClickListener((parent1, v1, position1, id1) -> {
            String selected = (String) parent1.getItemAtPosition(position1);

            // Xoá từ khoá tìm kiếm (tránh filter chồng)
            if (binding != null) {
                binding.searchEditText.setText("");
            }

            // Thu AppBar để list lộ ra
            if (appBar != null) appBar.setExpanded(false, true);

            if (ALL.equals(selected)) {
                // Cuộn mượt về đầu trang
                assert binding != null;
                binding.recyclerView.post(() -> binding.recyclerView.smoothScrollToPosition(0));
            } else {
                // Cuộn mượt tới section tương ứng, chừa offset 8dp cho thoáng
                scrollToCategorySmooth(selected, dpToPx());
            }
        });

        // ===== Khởi tạo các phần còn lại =====
        setupFabCart();
        greetUser();
        setupSearchListener();
        setupProfileIconClickListener();

        // Load categories trước (để có thứ tự), rồi listen products realtime
        loadCategoriesThenListenProducts();
    }

    // ====== Smooth scroll-to-category với offset đỉnh ======
    private void scrollToCategorySmooth(@NonNull String category, int topOffsetPx) {
        if (binding == null) return;

        Integer pos = headerPositions.get(category);
        if (pos == null) {
            Log.w("UI", "scrollToCategorySmooth: header not found for " + category);
            return;
        }

        binding.recyclerView.post(() -> {
            if (binding.recyclerView.getLayoutManager() == null) return;

            androidx.recyclerview.widget.LinearSmoothScroller scroller =
                    new androidx.recyclerview.widget.LinearSmoothScroller(requireContext()) {
                        @Override
                        protected int getVerticalSnapPreference() {
                            return SNAP_TO_START;
                        }

                        @Override
                        public int calculateDyToMakeVisible(View view, int snapPreference) {
                            int dy = super.calculateDyToMakeVisible(view, snapPreference);
                            return dy - topOffsetPx; // trừ offset để header không dính sát toolbar
                        }
                    };
            scroller.setTargetPosition(pos);
            binding.recyclerView.getLayoutManager().startSmoothScroll(scroller);
        });
    }

    private int dpToPx() {
        return Math.round(8 * getResources().getDisplayMetrics().density);
    }

    // ======================= Load data =======================

    private void loadCategoriesThenListenProducts() {
        setLoading(true);
        categories.clear();

        db.collection("categories")
                .whereEqualTo("active", true)
                .orderBy("name", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(qs -> {
                    for (DocumentSnapshot d : qs.getDocuments()) {
                        String name = d.getString("name");
                        if (!TextUtils.isEmpty(name)) categories.add(name.trim());
                    }
                    bindCollapsedCategoryDropdown();
                    listenProductsRealtime();
                })
                .addOnFailureListener(e -> {
                    Log.w("Firestore", "Load categories failed: " + e.getMessage());
                    bindCollapsedCategoryDropdown(); // tạm thời trống; sẽ bổ sung sau khi có products
                    listenProductsRealtime();
                });
    }

    /** Realtime listener (có fallback khi thiếu composite index) */
    private void listenProductsRealtime() {
        if (subProducts != null) { subProducts.remove(); subProducts = null; }
        setLoading(true);
        setEmpty(false);

        Query q = db.collection("products")
                .orderBy("category", Query.Direction.ASCENDING)
                .orderBy("name", Query.Direction.ASCENDING);

        subProducts = q.addSnapshotListener((QuerySnapshot snapshot, FirebaseFirestoreException e) -> {
            if (!isAdded() || binding == null) return;
            setLoading(false);
            allProducts.clear();

            if (e != null) {
                Log.e("Firestore", "Listen products failed", e);
                String msg = String.valueOf(e.getMessage());
                if (msg.contains("FAILED_PRECONDITION") && msg.contains("requires an index")) {
                    fallbackListenProductsByNameOnly();
                    return;
                }
                adapter.submitRows(new ArrayList<>());
                setEmpty(true);
                return;
            }

            fillProductsFromSnapshot(snapshot);
            ensureCategoriesIfEmpty();
            rebuildSectionsAndShow(null); // build rows + headerPositions ban đầu
        });
    }

    private void fallbackListenProductsByNameOnly() {
        if (subProducts != null) { subProducts.remove(); subProducts = null; }
        setLoading(true);
        setEmpty(false);

        subProducts = db.collection("products")
                .orderBy("name", Query.Direction.ASCENDING)
                .addSnapshotListener((QuerySnapshot snapshot, FirebaseFirestoreException e2) -> {
                    if (!isAdded() || binding == null) return;
                    setLoading(false);
                    allProducts.clear();

                    if (e2 != null) {
                        Log.e("Firestore", "Fallback listen failed", e2);
                        adapter.submitRows(new ArrayList<>());
                        setEmpty(true);
                        return;
                    }

                    fillProductsFromSnapshot(snapshot);
                    ensureCategoriesIfEmpty();
                    rebuildSectionsAndShow(null);
                });
    }

    private void fillProductsFromSnapshot(@Nullable QuerySnapshot snapshot) {
        if (snapshot != null && !snapshot.isEmpty()) {
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                Products m = doc.toObject(Products.class);
                if (m == null) continue;

                if (TextUtils.isEmpty(m.getId())) m.setId(doc.getId());

                // Fallback legacy "image"
                if (m.getImageUrl() == null) {
                    String legacy = doc.getString("image");
                    if (legacy != null) m.setImageUrl(legacy);
                }
                allProducts.add(m);
            }
            Log.d("UI", "Products loaded: " + allProducts.size());
        } else {
            Log.w("Firestore", "No data in products");
        }
    }

    private void ensureCategoriesIfEmpty() {
        if (!categories.isEmpty()) return;
        LinkedHashMap<String, Boolean> seen = new LinkedHashMap<>();
        for (Products p : allProducts) {
            String cat = norm(safe(p.getCategory()));
            if (!cat.isEmpty() && !seen.containsKey(cat)) seen.put(cat, true);
        }
        categories.addAll(seen.keySet());
        bindCollapsedCategoryDropdown();
    }

    // ======================= Build sections =======================

    /** Ghép danh sách [HEADER, PRODUCT...] theo từng category; filter theo text nếu có */
    private void rebuildSectionsAndShow(@Nullable String filterQuery) {
        String q = filterQuery == null ? "" : filterQuery.trim().toLowerCase(Locale.getDefault());
        List<ProductsSectionAdapter.Row> rows = new ArrayList<>();
        headerPositions.clear();

        List<String> orderedCats = orderCategories(categories);

        for (String cat : orderedCats) {
            String catKey = norm(cat);
            List<Products> underCat = new ArrayList<>();
            for (Products p : allProducts) {
                String pCatKey = norm(p.getCategory());
                if (pCatKey.equals(catKey)) {
                    if (q.isEmpty() || matchesQuery(p, q)) {
                        underCat.add(p);
                    }
                }
            }
            if (underCat.isEmpty()) continue;

            headerPositions.put(cat, rows.size());
            rows.add(ProductsSectionAdapter.Row.header(cat));
            for (Products p : underCat) rows.add(ProductsSectionAdapter.Row.product(p));
        }

        adapter.submitRows(rows);
        setEmpty(rows.isEmpty());

        Log.d("UI", "Categories: " + categories);
        Log.d("UI", "Rows submitted: " + rows.size());
    }

    private boolean matchesQuery(Products p, String q) {
        String name = safe(p.getName()).toLowerCase(Locale.getDefault());
        String cat  = safe(p.getCategory()).toLowerCase(Locale.getDefault());
        String sname= safe(p.getSearchableName()).toLowerCase(Locale.getDefault());
        return name.contains(q) || cat.contains(q) || sname.contains(q);
    }

    /** Thứ tự ưu tiên: Trà sữa → Trà → Trái cây tươi → Cà Phê; còn lại giữ nguyên.
     *  Luôn thêm "Tất cả" đứng đầu.
     */
    private List<String> orderCategories(List<String> src) {
        List<String> preferred = List.of("Trà sữa", "Trà", "Trái cây tươi", "Cà Phê");

        List<String> result = new ArrayList<>();
        // Luôn add "Tất cả" đứng đầu
        result.add(ALL);

        if (src != null) {
            // Ưu tiên các nhóm ưa thích
            for (String p : preferred) {
                for (String s : src) {
                    if (norm(s).equals(norm(p))) { result.add(s); break; }
                }
            }
            // Thêm các category còn lại, tránh trùng
            for (String s : src) {
                boolean already = false;
                for (String r : result) if (norm(r).equals(norm(s))) { already = true; break; }
                if (!already) result.add(s);
            }
        }
        return result;
    }

    // ======================= AppBar & Dropdown (collapsed) =======================
    private void setupAppBarOffsetBehavior() {
        if (appBar == null || binding == null) return;

        appBar.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
            int total = appBarLayout.getTotalScrollRange();
            int offset = Math.abs(verticalOffset);

            // Xuất hiện sớm hơn một chút (65% cuộn)
            float triggerPercent = 0.65f;
            boolean shouldShow = total > 0 && offset >= total * triggerPercent;

            if (shouldShow) {
                if (binding.collapsedBarContainer.getVisibility() != View.VISIBLE) {
                    binding.collapsedBarContainer.setVisibility(View.VISIBLE);
                    binding.collapsedBarContainer.animate().alpha(1f).setDuration(200).start();
                }
            } else {
                if (binding.collapsedBarContainer.getVisibility() == View.VISIBLE) {
                    binding.collapsedBarContainer.animate()
                            .alpha(0f)
                            .setDuration(200)
                            .withEndAction(() -> binding.collapsedBarContainer.setVisibility(View.GONE))
                            .start();
                }
            }
        });
    }

    /** Nạp dữ liệu cho dropdown (luôn có "Tất cả" ở đầu + setText) */
    private void bindCollapsedCategoryDropdown() {
        if (actvCategoryCollapsed == null || ddAdapter == null) return;

        List<String> ordered = orderCategories(categories);
        ddAdapter.clear();
        ddAdapter.addAll(ordered);
        ddAdapter.notifyDataSetChanged();

        // Đảm bảo text hiển thị là "Tất cả" sau khi nạp
        if (!ALL.contentEquals(actvCategoryCollapsed.getText())) {
            actvCategoryCollapsed.setText(ALL, false);
        }
    }

    // ======================= Search =======================

    private void setupSearchListener() {
        if (binding == null) return;
        binding.searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                rebuildSectionsAndShow(s == null ? "" : s.toString());
            }
        });
    }

    // ======================= Misc =======================

    private void setLoading(boolean show) {
        if (binding != null) binding.loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void setEmpty(boolean show) {
        if (binding != null) binding.emptyState.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void setupFabCart() {
        if (binding == null) return;
        binding.fabCart.setOnClickListener(v -> {
            if (!isAdded()) return;
            if (requireActivity() instanceof com.pro.milkteaapp.activity.MainActivity) {
                ((com.pro.milkteaapp.activity.MainActivity) requireActivity()).openCartTab();
            }
        });
    }

    @SuppressLint("SetTextI18n")
    private void greetUser() {
        if (binding == null) return;

        SharedPreferences prefs = requireActivity().getSharedPreferences("MyPrefs", android.content.Context.MODE_PRIVATE);
        String name = prefs.getString("username", "");

        if (TextUtils.isEmpty(name)) {
            String authName = null;
            if (com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null) {
                authName = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getDisplayName();
                if (TextUtils.isEmpty(authName)) {
                    authName = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getEmail();
                }
            }
            name = !TextUtils.isEmpty(authName) ? authName : "Người dùng";
        }
        binding.textGreeting.setText("Xin chào, " + name + "!");
    }

    private void setupProfileIconClickListener() {
        if (binding == null) return;
        binding.profileIcon.setOnClickListener(v -> {
            if (!isAdded()) return;
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new ProfileFragment())
                    .addToBackStack(null)
                    .commit();
        });
    }

    // Click item sản phẩm → chi tiết
    @Override
    public void onClick(Products milkTea) {
        if (!isAdded() || milkTea == null || TextUtils.isEmpty(milkTea.getId())) return;
        ProductDetailFragment f = ProductDetailFragment.newInstance(milkTea.getId());
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, f)
                .addToBackStack("product_detail")
                .commit();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (subProducts != null) { subProducts.remove(); subProducts = null; }
        binding = null;
    }

    // ======================= Helpers =======================
    private static String safe(String s){ return s == null ? "" : s; }
    private static String norm(String s) { return s == null ? "" : s.trim().toLowerCase(Locale.getDefault()); }
}
