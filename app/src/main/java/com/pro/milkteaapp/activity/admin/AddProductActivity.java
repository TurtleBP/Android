package com.pro.milkteaapp.activity.admin;

import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.SessionManager;
import com.pro.milkteaapp.activity.MainActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AddProductActivity extends AppCompatActivity {

    // ==== Keys nhận từ Intent (để hỗ trợ Edit) ====
    public static final String EXTRA_EDIT_MODE        = "EDIT_MODE";
    public static final String EXTRA_PRODUCT_ID       = "PRODUCT_ID";
    public static final String EXTRA_PRODUCT_NAME     = "PRODUCT_NAME";
    public static final String EXTRA_PRODUCT_PRICE    = "PRODUCT_PRICE";
    public static final String EXTRA_PRODUCT_CATEGORY = "PRODUCT_CATEGORY";
    public static final String EXTRA_PRODUCT_IMAGE    = "PRODUCT_IMAGE";
    public static final String EXTRA_PRODUCT_DESC     = "PRODUCT_DESCRIPTION";

    private TextInputEditText etName, etPrice, etImageUrl, etDescription;
    private MaterialAutoCompleteTextView actCategory;
    private ProgressBar progress;
    private Button btnAdd, btnReset;

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private boolean isSubmitting = false;
    private boolean isEditMode = false;
    private String editingProductId = null;

    // Dropdown category
    private ArrayAdapter<String> categoryAdapter;
    private final ArrayList<String> categories = new ArrayList<>();
    private static final boolean REQUIRE_CATEGORY_IN_LIST = true; // true = bắt buộc chọn trong danh sách

    // Bottom nav debounce
    private static final long NAV_DEBOUNCE_MS = 400L;
    private long lastNavClick = 0L;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Guard quyền admin sớm
        SessionManager sm = new SessionManager(this);
        if (!sm.isAdmin()) {
            Toast.makeText(this, "Bạn không có quyền truy cập (admin-only)", Toast.LENGTH_SHORT).show();
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
            return;
        }

        setContentView(R.layout.activity_add_product);

        // Toolbar
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Form
        etName        = findViewById(R.id.editTextProductName);
        etPrice       = findViewById(R.id.editTextProductPrice);
        actCategory   = findViewById(R.id.actCategory);               // << đổi sang dropdown
        etImageUrl    = findViewById(R.id.editTextProductImage);
        etDescription = findViewById(R.id.editTextProductDescription);
        btnAdd        = findViewById(R.id.buttonAddProduct);
        btnReset      = findViewById(R.id.buttonReset);
        progress      = findViewById(R.id.progressBar);

        db   = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        // Dropdown adapter
        categoryAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, categories);
        actCategory.setAdapter(categoryAdapter);

        // Load category từ Firestore
        loadCategoriesForDropdown();

        // Bottom Navigation
        setupBottomNavigation();

        // Xác định Add hay Edit
        parseEditModeFromIntent();

        btnAdd.setOnClickListener(v -> {
            if (isEditMode) updateProduct();
            else addProduct();
        });
        btnReset.setOnClickListener(v -> resetForm());
    }

    // Xử lý nút back trên Toolbar
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ---------------- Bottom Navigation ----------------
    private void setupBottomNavigation() {
        BottomNavigationView nav = findViewById(R.id.bottomNavigationView);
        if (nav == null) return;

        if (nav.getSelectedItemId() != R.id.adminManagement) {
            nav.setSelectedItemId(R.id.adminManagement);
        }

        nav.setOnItemSelectedListener(onNavSelectedListener);
        nav.setOnItemReselectedListener(item -> {});
    }

    private final NavigationBarView.OnItemSelectedListener onNavSelectedListener = item -> {
        long now = SystemClock.elapsedRealtime();
        if (now - lastNavClick < NAV_DEBOUNCE_MS) return false;
        lastNavClick = now;

        int id = item.getItemId();
        if (id == R.id.adminManagement) {
            return true;
        }
        String target = mapMenuIdToTarget(id);
        if (target == null) return false;

        Intent i = new Intent(this, AdminMainActivity.class);
        i.putExtra(AdminMainActivity.EXTRA_ADMIN_TARGET, target);
        startActivity(i);
        overridePendingTransition(0, 0);
        finish();
        return true;
    };

    private String mapMenuIdToTarget(@IdRes int menuId) {
        if (menuId == R.id.adminOrders)     return "orders";
        if (menuId == R.id.adminUsers)      return "users";
        if (menuId == R.id.adminStatistics) return "stats";
        if (menuId == R.id.adminManagement) return "products";
        return null;
    }

    // ---------------- Load Categories ----------------
    private void loadCategoriesForDropdown() {
        setLoading(true);
        db.collection("categories")
                .whereEqualTo("active", true)
                .orderBy("name", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(this::onCategoriesLoaded)
                .addOnFailureListener(e -> {
                    setLoading(false);
                    toast("Lỗi tải category: " + e.getMessage());
                });
    }

    private void onCategoriesLoaded(QuerySnapshot qs) {
        categories.clear();
        for (DocumentSnapshot d : qs.getDocuments()) {
            String name = d.getString("name");
            if (name != null && !name.trim().isEmpty()) {
                categories.add(name.trim());
            }
        }
        categoryAdapter.notifyDataSetChanged();
        setLoading(false);
    }

    // ---------------- Edit / Add ----------------
    private void parseEditModeFromIntent() {
        Intent it = getIntent();
        if (it == null) return;

        isEditMode = it.getBooleanExtra(EXTRA_EDIT_MODE, false);
        editingProductId = it.getStringExtra(EXTRA_PRODUCT_ID);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(isEditMode ? "Sửa sản phẩm" : "Thêm sản phẩm");
        }

        if (isEditMode) {
            String name = it.getStringExtra(EXTRA_PRODUCT_NAME);
            Double price = null;
            try { price = (Double) it.getSerializableExtra(EXTRA_PRODUCT_PRICE); } catch (Exception ignored) {}
            String category = it.getStringExtra(EXTRA_PRODUCT_CATEGORY);
            String image = it.getStringExtra(EXTRA_PRODUCT_IMAGE);
            String desc = it.getStringExtra(EXTRA_PRODUCT_DESC);

            if (name != null) etName.setText(name);
            if (price != null) etPrice.setText(formatPrice(price));
            if (category != null) actCategory.setText(category, false); // << set vào dropdown
            if (image != null) etImageUrl.setText(image);
            if (desc != null) etDescription.setText(desc);
        }
    }

    // ====== CREATE ======
    private void addProduct() {
        if (isSubmitting) return;

        String name        = txt(etName);
        String category    = txt(actCategory);
        String imageUrl    = txt(etImageUrl);
        String description = txt(etDescription);
        String priceRaw    = txt(etPrice);

        if (name.isEmpty() || category.isEmpty() || priceRaw.isEmpty()) {
            toast("Vui lòng nhập đầy đủ Tên, Danh mục và Giá.");
            return;
        }
        if (REQUIRE_CATEGORY_IN_LIST && !categories.contains(category)) {
            toast("Vui lòng chọn Danh mục từ danh sách.");
            actCategory.requestFocus();
            return;
        }

        Double price = parsePrice(priceRaw);
        if (price == null || price <= 0) {
            toast("Giá sản phẩm không hợp lệ.");
            return;
        }

        setLoading(true);

        String uid = (auth.getCurrentUser() != null) ? auth.getCurrentUser().getUid() : null;
        DocumentReference ref = db.collection("products").document();

        Map<String, Object> data = new HashMap<>();
        data.put("id", ref.getId());
        data.put("name", name);
        data.put("price", price);
        data.put("category", category);
        data.put("imageUrl", imageUrl.isEmpty() ? null : imageUrl);
        data.put("description", description.isEmpty() ? null : description);
        data.put("status", "active");
        data.put("searchableName", name.toLowerCase(Locale.ROOT));
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("createdByUid", uid);

        ref.set(data)
                .addOnSuccessListener(unused -> {
                    setLoading(false);
                    toast("Đã thêm sản phẩm.");
                    finish();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    toast("Thêm thất bại: " + e.getMessage());
                });
    }

    // ====== UPDATE ======
    private void updateProduct() {
        if (isSubmitting) return;

        if (editingProductId == null || editingProductId.trim().isEmpty()) {
            toast("Không xác định được ID sản phẩm để sửa.");
            return;
        }

        String name        = txt(etName);
        String category    = txt(actCategory);
        String imageUrl    = txt(etImageUrl);
        String description = txt(etDescription);
        String priceRaw    = txt(etPrice);

        if (name.isEmpty() || category.isEmpty() || priceRaw.isEmpty()) {
            toast("Vui lòng nhập đầy đủ Tên, Danh mục và Giá.");
            return;
        }
        if (REQUIRE_CATEGORY_IN_LIST && !categories.contains(category)) {
            toast("Vui lòng chọn Danh mục từ danh sách.");
            actCategory.requestFocus();
            return;
        }

        Double price = parsePrice(priceRaw);
        if (price == null || price <= 0) {
            toast("Giá sản phẩm không hợp lệ.");
            return;
        }

        setLoading(true);

        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        data.put("price", price);
        data.put("category", category);
        data.put("imageUrl", imageUrl.isEmpty() ? null : imageUrl);
        data.put("description", description.isEmpty() ? null : description);
        data.put("searchableName", name.toLowerCase(Locale.ROOT));
        data.put("updatedAt", FieldValue.serverTimestamp());

        db.collection("products").document(editingProductId)
                .update(data)
                .addOnSuccessListener(unused -> {
                    setLoading(false);
                    toast("Đã cập nhật sản phẩm.");
                    finish();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    toast("Cập nhật thất bại: " + e.getMessage());
                });
    }

    private void resetForm() {
        etName.setText("");
        etPrice.setText("");
        actCategory.setText("", false);
        etImageUrl.setText("");
        etDescription.setText("");
        etName.requestFocus();
    }

    // ===== Helpers =====
    private String txt(TextInputEditText e) {
        return e.getText() != null ? e.getText().toString().trim() : "";
    }
    private String txt(MaterialAutoCompleteTextView e) {
        return e.getText() != null ? e.getText().toString().trim() : "";
    }

    private void toast(String m) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }

    private void setLoading(boolean loading) {
        isSubmitting = loading;
        if (progress != null) progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (btnAdd != null) btnAdd.setEnabled(!loading);
        if (btnReset != null) btnReset.setEnabled(!loading);
    }

    private Double parsePrice(String priceRaw) {
        try {
            String norm = priceRaw.replaceAll("[^\\d.]", "").replace(",", "").trim();
            if (norm.isEmpty()) return null;
            return Double.parseDouble(norm);
        } catch (Exception e) {
            return null;
        }
    }

    private String formatPrice(Double price) {
        if (price == null) return "";
        long p = Math.round(price);
        return String.valueOf(p);
    }
}
