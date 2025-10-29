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
import android.widget.Toast;

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
import com.pro.milkteaapp.adapter.ProductsSectionAdapter;
import com.pro.milkteaapp.databinding.FragmentProductBinding;
import com.pro.milkteaapp.fragment.bottomsheet.ProductDetailBottomSheet;
import com.pro.milkteaapp.models.CartItem;
import com.pro.milkteaapp.models.Products;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ProductFragment extends Fragment implements ProductsSectionAdapter.OnProductClick {

    private static final String ALL = "Tất cả";

    private FragmentProductBinding binding;
    private ProductsSectionAdapter adapter;

    private final List<Products> allProducts = new ArrayList<>();
    private final List<String> categories = new ArrayList<>();
    private final Map<String, Integer> headerPositions = new HashMap<>();

    private FirebaseFirestore db;
    private ListenerRegistration subProducts;

    private AppBarLayout appBar;
    private MaterialAutoCompleteTextView actvCategoryCollapsed;
    private ArrayAdapter<String> ddAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentProductBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        adapter = new ProductsSectionAdapter(this);

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

        appBar = binding.appbar;
        actvCategoryCollapsed = binding.actvCategoryCollapsed;

        binding.collapsedBarContainer.setAlpha(0f);
        binding.collapsedBarContainer.setVisibility(View.GONE);
        setupAppBarOffsetBehavior();

        ddAdapter = new ArrayAdapter<>(requireContext(), R.layout._item_dropdown, new ArrayList<>());
        actvCategoryCollapsed.setAdapter(ddAdapter);
        actvCategoryCollapsed.setText(ALL, false);
        actvCategoryCollapsed.setThreshold(0);
        actvCategoryCollapsed.setOnClickListener(v -> actvCategoryCollapsed.showDropDown());
        actvCategoryCollapsed.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) actvCategoryCollapsed.showDropDown(); });
        actvCategoryCollapsed.setOnTouchListener((v, e) -> { actvCategoryCollapsed.showDropDown(); return false; });

        actvCategoryCollapsed.setOnItemClickListener((parent1, v1, position1, id1) -> {
            String selected = (String) parent1.getItemAtPosition(position1);
            if (binding != null) binding.searchEditText.setText("");
            if (appBar != null) appBar.setExpanded(false, true);
            if (ALL.equals(selected)) {
                assert binding != null;
                binding.recyclerView.post(() -> binding.recyclerView.smoothScrollToPosition(0));
            } else {
                scrollToCategorySmooth(selected, dpToPx());
            }
        });

        setupFabCart();
        greetUser();
        setupSearchListener();
        setupProfileIconClickListener();

        loadCategoriesThenListenProducts();
    }

    private void scrollToCategorySmooth(@NonNull String category, int topOffsetPx) {
        if (binding == null) return;
        Integer pos = headerPositions.get(category);
        if (pos == null) { Log.w("UI", "header not found for " + category); return; }
        binding.recyclerView.post(() -> {
            if (binding.recyclerView.getLayoutManager() == null) return;
            androidx.recyclerview.widget.LinearSmoothScroller scroller =
                    new androidx.recyclerview.widget.LinearSmoothScroller(requireContext()) {
                        @Override protected int getVerticalSnapPreference() { return SNAP_TO_START; }
                        @Override public int calculateDyToMakeVisible(View view, int snapPreference) {
                            int dy = super.calculateDyToMakeVisible(view, snapPreference);
                            return dy - topOffsetPx;
                        }
                    };
            scroller.setTargetPosition(pos);
            binding.recyclerView.getLayoutManager().startSmoothScroll(scroller);
        });
    }
    private int dpToPx() { return Math.round(8 * getResources().getDisplayMetrics().density); }

    private void loadCategoriesThenListenProducts() {
        setLoading(true);
        categories.clear();
        db.collection("categories").whereEqualTo("active", true).orderBy("name", Query.Direction.ASCENDING)
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
                    bindCollapsedCategoryDropdown();
                    listenProductsRealtime();
                });
    }

    private void listenProductsRealtime() {
        if (subProducts != null) { subProducts.remove(); subProducts = null; }
        setLoading(true); setEmpty(false);
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
                adapter.submitRows(new ArrayList<>()); setEmpty(true); return;
            }
            fillProductsFromSnapshot(snapshot);
            ensureCategoriesIfEmpty();
            rebuildSectionsAndShow(null);
        });
    }

    private void fallbackListenProductsByNameOnly() {
        if (subProducts != null) { subProducts.remove(); subProducts = null; }
        setLoading(true); setEmpty(false);
        subProducts = db.collection("products")
                .orderBy("name", Query.Direction.ASCENDING)
                .addSnapshotListener((QuerySnapshot snapshot, FirebaseFirestoreException e2) -> {
                    if (!isAdded() || binding == null) return;
                    setLoading(false);
                    allProducts.clear();
                    if (e2 != null) { Log.e("Firestore", "Fallback listen failed", e2); adapter.submitRows(new ArrayList<>()); setEmpty(true); return; }
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
                    if (q.isEmpty() || matchesQuery(p, q)) underCat.add(p);
                }
            }
            if (underCat.isEmpty()) continue;
            headerPositions.put(cat, rows.size());
            rows.add(ProductsSectionAdapter.Row.header(cat));
            for (Products p : underCat) rows.add(ProductsSectionAdapter.Row.product(p));
        }
        adapter.submitRows(rows);
        setEmpty(rows.isEmpty());
    }

    private boolean matchesQuery(Products p, String q) {
        String name = safe(p.getName()).toLowerCase(Locale.getDefault());
        String cat  = safe(p.getCategory()).toLowerCase(Locale.getDefault());
        String sname= safe(p.getSearchableName()).toLowerCase(Locale.getDefault());
        return name.contains(q) || cat.contains(q) || sname.contains(q);
    }

    private List<String> orderCategories(List<String> src) {
        List<String> preferred = java.util.List.of("Trà sữa", "Trà", "Trái cây tươi", "Cà Phê");
        List<String> result = new ArrayList<>();
        result.add(ALL);
        if (src != null) {
            for (String p : preferred) for (String s : src) { if (norm(s).equals(norm(p))) { result.add(s); break; } }
            for (String s : src) {
                boolean already = false; for (String r : result) if (norm(r).equals(norm(s))) { already = true; break; }
                if (!already) result.add(s);
            }
        }
        return result;
    }

    private void setupAppBarOffsetBehavior() {
        if (appBar == null || binding == null) return;
        appBar.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
            int total = appBarLayout.getTotalScrollRange();
            int offset = Math.abs(verticalOffset);
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

    private void bindCollapsedCategoryDropdown() {
        if (actvCategoryCollapsed == null || ddAdapter == null) return;
        List<String> ordered = orderCategories(categories);
        ddAdapter.clear(); ddAdapter.addAll(ordered); ddAdapter.notifyDataSetChanged();
        if (!ALL.contentEquals(actvCategoryCollapsed.getText())) actvCategoryCollapsed.setText(ALL, false);
    }

    private void setupSearchListener() {
        if (binding == null) return;
        binding.searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                rebuildSectionsAndShow(s == null ? "" : s.toString());
            }
        });
    }

    private void setLoading(boolean show) { if (binding != null) binding.loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE); }
    private void setEmpty(boolean show)   { if (binding != null) binding.emptyState.setVisibility(show ? View.VISIBLE : View.GONE); }

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
                if (TextUtils.isEmpty(authName)) authName = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getEmail();
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

    // ======================= Product click → mở BottomSheet (mới) =======================
    @Override
    public void onClick(Products milkTea) {
        if (!isAdded() || milkTea == null || TextUtils.isEmpty(milkTea.getId())) return;

        ProductDetailBottomSheet sheet = ProductDetailBottomSheet.newInstance(milkTea);
        sheet.setListener((p, qty, size, toppings) -> {
            CartItem newItem = new CartItem(p, qty, size, toppings);

            int existed = -1;
            for (int i = 0; i < CartFragment.cartItems.size(); i++) {
                if (newItem.equals(CartFragment.cartItems.get(i))) { existed = i; break; }
            }
            if (existed >= 0) {
                CartFragment.cartItems.get(existed).increaseQuantity(qty);
            } else {
                CartFragment.cartItems.add(newItem);
            }

            String label = newItem.getToppingsLabel();
            Toast.makeText(requireContext(),
                    "Đã thêm " + qty + " " + p.getName() + " (" + size + ("Không".equals(label) ? "" : ", " + label) + ")",
                    Toast.LENGTH_SHORT).show();
        });
        sheet.show(getParentFragmentManager(), "product_detail_sheet");
    }

    private static String safe(String s){ return s == null ? "" : s; }
    private static String norm(String s) { return s == null ? "" : s.trim().toLowerCase(Locale.getDefault()); }
}
