package com.pro.milkteaapp.activity.admin;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.SessionManager;
import com.pro.milkteaapp.activity.MainActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Màn hình gộp: Thêm + Sửa sản phẩm.
 * - Nếu có EXTRA_PRODUCT_ID -> Edit mode (inflate activity_edit_product)
 * - Nếu không -> Add mode (inflate activity_add_product)
 * Phiên bản này KHÔNG dùng ViewModel/Factory/Repository.
 * Toàn bộ CRUD gọi Firestore trực tiếp.
 */
public class ProductEditorActivity extends AppCompatActivity {

    public static final String EXTRA_PRODUCT_ID = "productId";

    // Inputs dùng chung
    private TextInputEditText etName, etPrice, etImageUrl, etDescription;
    private MaterialAutoCompleteTextView actCategory;

    // Nút theo template
    private Button btnSave, btnReset;
    private ProgressBar progress;

    private boolean isEditMode = false;
    private String productId;

    // Dropdown
    private final ArrayList<String> categories = new ArrayList<>();
    private ArrayAdapter<String> categoryAdapter;
    private static final boolean REQUIRE_CATEGORY_IN_LIST = true;

    // Firestore + Auth
    private FirebaseFirestore db;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Chặn non-admin
        if (!new SessionManager(this).isAdmin()) {
            Toast.makeText(this, "Bạn không có quyền truy cập (admin-only)", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
            return;
        }

        // Xác định mode & layout
        productId = getIntent().getStringExtra(EXTRA_PRODUCT_ID);
        isEditMode = productId != null && !productId.trim().isEmpty();
        setContentView(isEditMode ? R.layout.activity_edit_product : R.layout.activity_add_product);

        // Bind
        bindViewsByTemplate();

        // Firebase
        db   = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        // Dropdown
        categoryAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, categories);
        actCategory.setAdapter(categoryAdapter);
        actCategory.setOnClickListener(v -> actCategory.showDropDown());

        // Load danh mục, xong nếu edit thì load sản phẩm
        loadCategories(() -> {
            if (isEditMode) loadProductForEdit(productId);
        });

        if (btnSave  != null) btnSave.setOnClickListener(v -> onSave());
        if (btnReset != null) btnReset.setOnClickListener(v -> resetForm());
    }

    /* ------------------------- Bind theo template ------------------------- */
    private void bindViewsByTemplate() {
        etName        = findViewById(R.id.editTextProductName);
        etPrice       = findViewById(R.id.editTextProductPrice);
        actCategory   = findViewById(R.id.actCategory);
        etImageUrl    = findViewById(R.id.editTextProductImage);
        etDescription = findViewById(R.id.editTextProductDescription);
        progress      = findViewById(R.id.progressBar);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
            toolbar.setTitle(isEditMode ? R.string.title_edit_product : R.string.title_add_product);
        }

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        btnSave  = isEditMode
                ? firstNonNull(findViewById(R.id.buttonUpdateProduct), findViewById(R.id.buttonAddProduct))
                : firstNonNull(findViewById(R.id.buttonAddProduct),   findViewById(R.id.buttonUpdateProduct));

        btnReset = findViewById(R.id.buttonReset);
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... views) {
        for (T v : views) if (v != null) return v;
        return null;
    }

    /* ------------------------- Load categories ------------------------- */
    private void loadCategories(Runnable onDone) {
        setLoading(true);
        db.collection("categories")
                .whereEqualTo("active", true)
                .orderBy("name", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(qs -> {
                    onCategoriesLoaded(qs);
                    if (onDone != null) onDone.run();   // giữ luồng edit
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    toast("Lỗi tải danh mục: " + e.getMessage());
                    if (onDone != null) onDone.run();
                });
    }

    private void onCategoriesLoaded(QuerySnapshot qs) {
        categories.clear();
        for (DocumentSnapshot d : qs.getDocuments()) {
            String name = d.getString("name");
            if (name != null && !name.trim().isEmpty()) categories.add(name.trim());
        }
        if (categoryAdapter != null) categoryAdapter.notifyDataSetChanged();

        if (isEditMode && actCategory.getText() != null) {
            String cat = actCategory.getText().toString();
            if (!cat.isEmpty()) actCategory.setText(cat, false);
        }
        setLoading(false);
    }

    /* ------------------------- Load product (Edit) ------------------------- */
    private void loadProductForEdit(String id) {
        setLoading(true);
        db.collection("products").document(id).get()
                .addOnSuccessListener(d -> {
                    setLoading(false);
                    if (!d.exists()) { toast("Không tìm thấy sản phẩm"); finish(); return; }

                    String name        = ns(d.getString("name"));
                    Double price       = d.getDouble("price");
                    String imageUrl    = ns(d.getString("imageUrl"));
                    String description = ns(d.getString("description"));
                    String category    = ns(d.getString("category"));

                    etName.setText(name);
                    etPrice.setText(formatPrice(price));
                    etImageUrl.setText(imageUrl);
                    etDescription.setText(description);

                    if (!category.isEmpty() && !containsIgnoreCase(categories, category)) {
                        categories.add(category + " (đã ẩn)");
                        if (categoryAdapter != null) categoryAdapter.notifyDataSetChanged();
                    }
                    actCategory.setText(category, false);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    toast("Lỗi tải sản phẩm: " + e.getMessage());
                    finish();
                });
    }

    /* ------------------------- Save (Add / Update) ------------------------- */
    private void onSave() {
        if (btnSave != null) btnSave.setEnabled(false);

        String name        = t(etName);
        String priceStr    = t(etPrice);
        String categoryUI  = actCategory.getText() == null ? "" : actCategory.getText().toString().trim();
        String image       = t(etImageUrl);
        String description = t(etDescription);

        if (name.isEmpty() || priceStr.isEmpty() || categoryUI.isEmpty()) {
            toast("Vui lòng nhập đủ Tên, Giá và Danh mục!");
            if (btnSave != null) btnSave.setEnabled(true);
            return;
        }

        String normalizedCat = categoryUI.replace(" (đã ẩn)", "").trim();
        if (REQUIRE_CATEGORY_IN_LIST && !containsIgnoreCase(categories, normalizedCat)) {
            toast("Vui lòng chọn danh mục hợp lệ từ danh sách!");
            actCategory.showDropDown();
            if (btnSave != null) btnSave.setEnabled(true);
            return;
        }

        Double price = parsePrice(priceStr);
        if (price == null || price <= 0) {
            toast("Giá không hợp lệ!");
            if (btnSave != null) btnSave.setEnabled(true);
            return;
        }

        setLoading(true);

        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        data.put("price", price);
        data.put("category", normalizedCat);
        data.put("imageUrl", image.isEmpty() ? null : image);
        data.put("description", description.isEmpty() ? null : description);
        data.put("searchableName", name.toLowerCase(Locale.ROOT));
        data.put("updatedAt", FieldValue.serverTimestamp());

        if (isEditMode) {
            // UPDATE trực tiếp Firestore
            db.collection("products").document(productId)
                    .update(data)
                    .addOnSuccessListener(v -> {
                        setLoading(false);
                        toast("Đã cập nhật sản phẩm.");
                        setResult(RESULT_OK);
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        setLoading(false);
                        toast("Cập nhật thất bại: " + e.getMessage());
                        if (btnSave != null) btnSave.setEnabled(true);
                    });
        } else {
            // CREATE bằng auto-id
            String uid = (auth.getCurrentUser() != null) ? auth.getCurrentUser().getUid() : null;
            data.put("status", "active");
            data.put("createdByUid", uid);
            data.put("createdAt", FieldValue.serverTimestamp());

            db.collection("products")
                    .add(data)
                    .addOnSuccessListener(ref -> {
                        // tuỳ bạn nếu muốn lưu field "id" bên trong document:
                        ref.update("id", ref.getId());
                        setLoading(false);
                        toast("Đã thêm sản phẩm: " + ref.getId());
                        setResult(RESULT_OK);
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        setLoading(false);
                        toast("Thêm thất bại: " + e.getMessage());
                        if (btnSave != null) btnSave.setEnabled(true);
                    });
        }
    }

    private void resetForm() {
        etName.setText("");
        etPrice.setText("");
        actCategory.setText("", false);
        etImageUrl.setText("");
        etDescription.setText("");
        etName.requestFocus();
    }

    /* ------------------------- Utils ------------------------- */
    private void setLoading(boolean loading) {
        if (progress != null) progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (btnSave  != null) btnSave.setEnabled(!loading);
        if (btnReset != null) btnReset.setEnabled(!loading);
    }

    private static String t(TextInputEditText e) {
        return e.getText() != null ? e.getText().toString().trim() : "";
    }
    private static String ns(String s) { return s == null ? "" : s; }

    private static boolean containsIgnoreCase(ArrayList<String> list, String v) {
        for (String s : list) if (s.equalsIgnoreCase(v)) return true;
        return false;
    }

    private static String formatPrice(Double price) {
        if (price == null) return "";
        return String.valueOf(Math.round(price));
    }

    private static Double parsePrice(String raw) {
        try {
            String norm = raw.replaceAll("[^\\d.]", "").replace(",", "").trim();
            if (norm.isEmpty()) return null;
            return Double.parseDouble(norm);
        } catch (Exception e) { return null; }
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
}
