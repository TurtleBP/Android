package com.pro.milkteaapp.activity.admin;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.models.Products;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EditProductActivity extends AppCompatActivity {

    private TextInputEditText editTextName, editTextPrice, editTextImage, editTextDescription;
    private MaterialAutoCompleteTextView actvCategory;
    private AlertDialog progressDialog;
    private FirebaseFirestore db;

    private String productId;

    private final List<String> categoryList = new ArrayList<>();
    private ArrayAdapter<String> categoryAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_product);

        db = FirebaseFirestore.getInstance();

        progressDialog = new AlertDialog.Builder(this)
                .setView(R.layout._dialog_loading)
                .setCancelable(false)
                .create();

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        // Views
        editTextName        = findViewById(R.id.editTextProductName);
        editTextPrice       = findViewById(R.id.editTextProductPrice);
        actvCategory        = findViewById(R.id.actCategory); // ⬅️ dropdown
        editTextImage       = findViewById(R.id.editTextProductImage);
        editTextDescription = findViewById(R.id.editTextProductDescription);

        // Adapter dropdown
        categoryAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, categoryList);
        actvCategory.setAdapter(categoryAdapter);
        actvCategory.setOnClickListener(v -> actvCategory.showDropDown());

        // Nhận productId
        productId = getIntent().getStringExtra("productId");
        if (productId == null || productId.trim().isEmpty()) {
            Toast.makeText(this, "Không tìm thấy ID sản phẩm!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Tải dữ liệu song song: categories trước, rồi product (để map đúng cat)
        loadCategories(() -> loadProductData(productId));

        findViewById(R.id.buttonUpdateProduct).setOnClickListener(v -> updateProduct());
    }

    /* ======================== Load categories & product ======================== */

    private void loadCategories(Runnable onDone) {
        if (!isFinishing() && progressDialog != null) progressDialog.show();

        db.collection("categories")
                .whereEqualTo("active", true)
                .orderBy("name")
                .get()
                .addOnSuccessListener(qs -> {
                    categoryList.clear();
                    qs.getDocuments().forEach(d -> {
                        String n = d.getString("name");
                        if (n != null && !n.trim().isEmpty()) categoryList.add(n.trim());
                    });
                    categoryAdapter.notifyDataSetChanged();

                    if (!isFinishing() && progressDialog != null) progressDialog.dismiss();
                    if (onDone != null) onDone.run();
                })
                .addOnFailureListener(e -> {
                    if (!isFinishing() && progressDialog != null) progressDialog.dismiss();
                    Toast.makeText(this, "Lỗi tải danh mục: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    // Vẫn gọi tiếp để còn load product (nhưng dropdown sẽ trống)
                    if (onDone != null) onDone.run();
                });
    }

    private void loadProductData(String id) {
        if (!isFinishing() && progressDialog != null) progressDialog.show();

        DocumentReference docRef = db.collection("products").document(id);
        docRef.get()
                .addOnSuccessListener(snapshot -> {
                    if (!isFinishing() && progressDialog != null) progressDialog.dismiss();
                    if (!snapshot.exists()) {
                        Toast.makeText(this, "Không tìm thấy sản phẩm", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    Products product = snapshot.toObject(Products.class);
                    if (product == null) {
                        Toast.makeText(this, "Dữ liệu sản phẩm rỗng", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    // Prefill
                    editTextName.setText(nullSafe(product.getName()));
                    editTextPrice.setText(formatPrice(product.getPrice()));
                    editTextImage.setText(nullSafe(product.getImageUrl()));
                    editTextDescription.setText(nullSafe(product.getDescription()));

                    String cat = nullSafe(product.getCategory());
                    if (!cat.isEmpty()) {
                        // Nếu category sản phẩm chưa nằm trong list active -> thêm tạm để hiển thị
                        if (!containsIgnoreCase(categoryList, cat)) {
                            categoryList.add(cat + " (đã ẩn)");
                            categoryAdapter.notifyDataSetChanged();
                        }
                        actvCategory.setText(cat, false);
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isFinishing() && progressDialog != null) progressDialog.dismiss();
                    Toast.makeText(this, "Lỗi tải dữ liệu: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    /* ======================== Update product ======================== */

    private void updateProduct() {
        String name        = getTextOrEmpty(editTextName);
        String priceStr    = getTextOrEmpty(editTextPrice);
        String categoryUI  = actvCategory.getText() == null ? "" : actvCategory.getText().toString().trim();
        String image       = getTextOrEmpty(editTextImage);
        String description = getTextOrEmpty(editTextDescription);

        if (name.isEmpty() || priceStr.isEmpty() || categoryUI.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập đủ thông tin!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Nếu category có hậu tố "(đã ẩn)" → không cho cập nhật; bắt user chọn category hợp lệ
        String normalizedCat = categoryUI.replace(" (đã ẩn)", "").trim();
        if (!containsIgnoreCase(categoryList, normalizedCat)) {
            Toast.makeText(this, "Vui lòng chọn danh mục hợp lệ từ danh sách!", Toast.LENGTH_SHORT).show();
            actvCategory.showDropDown();
            return;
        }

        Double price = parsePrice(priceStr);
        if (price == null || price <= 0) {
            Toast.makeText(this, "Giá không hợp lệ!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isFinishing() && progressDialog != null) progressDialog.show();

        Map<String, Object> updated = new HashMap<>();
        updated.put("name", name);
        updated.put("price", price);
        updated.put("category", normalizedCat);
        updated.put("imageUrl", image.isEmpty() ? null : image);
        updated.put("description", description.isEmpty() ? null : description);
        updated.put("searchableName", name.toLowerCase(Locale.ROOT));
        updated.put("updatedAt", FieldValue.serverTimestamp());

        db.collection("products").document(productId)
                .update(updated)
                .addOnSuccessListener(aVoid -> {
                    if (!isFinishing() && progressDialog != null) progressDialog.dismiss();
                    Toast.makeText(this, "Cập nhật thành công!", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(e -> {
                    if (!isFinishing() && progressDialog != null) progressDialog.dismiss();
                    Toast.makeText(this, "Lỗi cập nhật: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    /* ======================== Helpers ======================== */

    @Override
    protected void onDestroy() {
        if (progressDialog != null && progressDialog.isShowing()) progressDialog.dismiss();
        super.onDestroy();
    }

    private static String getTextOrEmpty(TextInputEditText edt) {
        return edt.getText() == null ? "" : edt.getText().toString().trim();
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }

    private static String formatPrice(Double price) {
        if (price == null) return "";
        long p = Math.round(price);
        return String.valueOf(p);
    }

    /** Parse giá kiểu "45,000", "45.000", "45000"… */
    private static Double parsePrice(String raw) {
        try {
            String norm = raw.replaceAll("[^\\d.]", "").replace(",", "").trim();
            if (norm.isEmpty()) return null;
            return Double.parseDouble(norm);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean containsIgnoreCase(List<String> list, String value) {
        for (String s : list) {
            if (s.equalsIgnoreCase(value)) return true;
        }
        return false;
    }
}
