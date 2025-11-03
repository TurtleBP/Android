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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.SessionManager;
import com.pro.milkteaapp.adapter.ProductsSectionAdapter;
import com.pro.milkteaapp.databinding.FragmentProductBinding;
import com.pro.milkteaapp.fragment.bottomsheet.ProductDetailBottomSheet;
import com.pro.milkteaapp.models.CartItem;
import com.pro.milkteaapp.models.Products;
import com.pro.milkteaapp.utils.ImageLoader;

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
    private ListenerRegistration userListener;

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
        greetUser();          // ✅ bản mới
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
        subProducts = q.addSnapshotListener((snapshot, e) -> {
            if (!isAdded() || binding == null) return;
            setLoading(false);
            allProducts.clear();
            if (e != null) {
                Log.e("Firestore", "Listen products failed", e);
                adapter.submitRows(new ArrayList<>()); setEmpty(true); return;
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
                if (m.getImageUrl() == null) {
                    String legacy = doc.getString("image");
                    if (legacy != null) m.setImageUrl(legacy);
                }
                allProducts.add(m);
            }
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
            for (String p : preferred)
                for (String s : src)
                    if (norm(s).equals(norm(p))) { result.add(s); break; }
            for (String s : src) {
                boolean already = false;
                for (String r : result) if (norm(r).equals(norm(s))) { already = true; break; }
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

    /**
     * Chào user theo thứ tự:
     * 1. Nếu đã có trong Session (tên + avatar) → dùng luôn
     * 2. Nếu có docId (USRxxxxx) → nghe realtime Firestore → set lại session
     * 3. Nếu chưa có docId → query theo email lần đầu → set session
     * 4. Cuối cùng mới rơi xuống email
     */
    @SuppressLint("SetTextI18n")
    private void greetUser() {
        if (binding == null) return;

        SessionManager session = new SessionManager(requireContext());
        String cachedName = session.getDisplayName();
        String cachedAvatar = session.getAvatar();

        // nếu session đã có tên → ưu tiên
        if (!TextUtils.isEmpty(cachedName)) {
            binding.textGreeting.setText("Xin chào, " + cachedName + "!");
        } else {
            // fallback cũ từ SharedPreferences (phòng trường hợp màn cũ có save)
            SharedPreferences prefs = requireActivity().getSharedPreferences("MyPrefs", android.content.Context.MODE_PRIVATE);
            String name = prefs.getString("username", "");
            if (TextUtils.isEmpty(name)) {
                String authName = null;
                if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                    authName = FirebaseAuth.getInstance().getCurrentUser().getDisplayName();
                    if (TextUtils.isEmpty(authName)) authName = FirebaseAuth.getInstance().getCurrentUser().getEmail();
                }
                name = !TextUtils.isEmpty(authName) ? authName : "Người dùng";
            }
            binding.textGreeting.setText("Xin chào, " + name + "!");
        }

        // avatar từ session (nếu có) → hiển thị ngay
        ImageLoader.load(binding.profileIcon, cachedAvatar, R.drawable.ic_avatar_default);

        // 2. nếu session đã có docId (USRxxxxx) → nghe realtime
        String docId = session.getUid();
        if (!TextUtils.isEmpty(docId)) {
            listenUserAvatarAndNameRealtime(docId, session);
            return;
        }

        // 3. nếu chưa có docId → query theo email lần đầu
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            String email = FirebaseAuth.getInstance().getCurrentUser().getEmail();
            if (!TextUtils.isEmpty(email)) {
                db.collection("users")
                        .whereEqualTo("email", email)
                        .limit(1)
                        .get()
                        .addOnSuccessListener(qs -> {
                            if (!isAdded() || binding == null) return;
                            if (!qs.isEmpty()) {
                                DocumentSnapshot doc = qs.getDocuments().get(0);
                                String id = doc.getId();
                                String fullName = doc.getString("fullName");
                                String role = doc.getString("role");
                                String avatar = doc.getString("avatar");

                                // lưu lại toàn bộ vào session để lần sau không bị "Xin chào, gmail"
                                session.saveUserFromFirestore(
                                        id,
                                        email,
                                        fullName,
                                        role,
                                        avatar
                                );

                                // bind UI
                                String display = !TextUtils.isEmpty(fullName) ? fullName : email;
                                binding.textGreeting.setText("Xin chào, " + display + "!");
                                ImageLoader.load(binding.profileIcon, avatar, R.drawable.ic_avatar_default);

                                // và nghe realtime luôn
                                listenUserAvatarAndNameRealtime(id, session);
                            }
                        });
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private void listenUserAvatarAndNameRealtime(@NonNull String docId, @NonNull SessionManager session) {
        if (userListener != null) userListener.remove();
        userListener = db.collection("users").document(docId)
                .addSnapshotListener((snap, e) -> {
                    if (!isAdded() || binding == null) return;
                    if (snap != null && snap.exists()) {
                        String fullName = snap.getString("fullName");
                        String avatar   = snap.getString("avatar");
                        String email    = snap.getString("email");
                        String role     = snap.getString("role");

                        // cập nhật UI
                        String display = !TextUtils.isEmpty(fullName)
                                ? fullName
                                : (!TextUtils.isEmpty(email) ? email : "Người dùng");
                        binding.textGreeting.setText("Xin chào, " + display + "!");
                        ImageLoader.load(binding.profileIcon, avatar, R.drawable.ic_avatar_default);

                        // cập nhật session để màn khác dùng lại
                        session.saveUserFromFirestore(
                                docId,
                                email,
                                fullName,
                                role,
                                avatar
                        );
                    }
                });
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

    @Override
    public void onDestroyView() {
        if (subProducts != null) { subProducts.remove(); subProducts = null; }
        if (userListener != null) { userListener.remove(); userListener = null; }
        binding = null;
        super.onDestroyView();
    }

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
