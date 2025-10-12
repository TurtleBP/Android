package com.pro.milkteaapp.activity;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.pro.milkteaapp.databinding.ActivityCheckoutBinding;
import com.pro.milkteaapp.models.CartItem;
import com.pro.milkteaapp.models.Products;
import com.pro.milkteaapp.utils.MoneyUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class CheckoutActivity extends AppCompatActivity {

    private ActivityCheckoutBinding binding;
    private final ArrayList<CartItem> cartItems = new ArrayList<>();

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCheckoutBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        setupToolbar();
        restoreCartFromIntent();
        prefillUserInfo();     // tùy chọn: nếu đã lưu user -> có thể tự bỏ qua
        renderTotal();

        binding.btnPlaceOrder.setOnClickListener(v -> placeOrder());
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Thanh toán");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

    }

    private void restoreCartFromIntent() {
        Serializable extra = getIntent().getSerializableExtra("cart_items");
        if (extra instanceof ArrayList<?> raw) {
            try {
                for (Object o : raw) {
                    if (o instanceof CartItem) {
                        cartItems.add((CartItem) o);
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private void prefillUserInfo() {
        // Nếu bạn đã có sẵn màn Profile cập nhật vào Firestore (users/{uid} {displayName, phone, address})
        // có thể đọc lại ở đây để auto điền. Để đơn giản, mình bỏ qua fetch (tránh rườm rà).
        // Bạn có thể tự thêm phần get() Firestore users/{uid} nếu muốn tự động điền.
    }

    private double computeTotal() {
        double total = 0.0;
        for (CartItem item : cartItems) {
            total += item.getTotalPrice(); // đã tính theo quantity bên CartItem
        }
        return total;
    }

    private void renderTotal() {
        double total = computeTotal();
        binding.tvTotal.setText(MoneyUtils.formatVnd(total));
    }

    private void setLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnPlaceOrder.setEnabled(!loading);
    }

    private boolean validateInputs() {
        String name    = binding.edtName.getText() != null ? binding.edtName.getText().toString().trim() : "";
        String phone   = binding.edtPhone.getText() != null ? binding.edtPhone.getText().toString().trim() : "";
        String address = binding.edtAddress.getText() != null ? binding.edtAddress.getText().toString().trim() : "";

        if (TextUtils.isEmpty(name)) {
            binding.edtName.setError("Vui lòng nhập họ tên");
            binding.edtName.requestFocus();
            return false;
        }
        if (TextUtils.isEmpty(phone)) {
            binding.edtPhone.setError("Vui lòng nhập số điện thoại");
            binding.edtPhone.requestFocus();
            return false;
        }
        if (TextUtils.isEmpty(address)) {
            binding.edtAddress.setError("Vui lòng nhập địa chỉ");
            binding.edtAddress.requestFocus();
            return false;
        }
        if (cartItems.isEmpty()) {
            Toast.makeText(this, "Giỏ hàng trống", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void placeOrder() {
        if (!validateInputs()) return;

        String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        if (uid == null) {
            Toast.makeText(this, "Bạn chưa đăng nhập", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);

        double total = computeTotal();

        // Tạo order doc
        String orderId = db.collection("orders").document().getId();
        Map<String, Object> order = new HashMap<>();
        order.put("orderId", orderId);
        order.put("userId", uid);
        order.put("name",    Objects.requireNonNull(binding.edtName.getText()).toString().trim());
        order.put("phone",   Objects.requireNonNull(binding.edtPhone.getText()).toString().trim());
        order.put("address", Objects.requireNonNull(binding.edtAddress.getText()).toString().trim());
        order.put("total",   total);
        order.put("status",  "pending");
        order.put("createdAt", Timestamp.now());

        WriteBatch batch = db.batch();
        // orders/{orderId}
        batch.set(db.collection("orders").document(orderId), order);

        // orders/{orderId}/items/{autoId}
        for (CartItem item : cartItems) {
            Products p = item.getMilkTea();
            Map<String, Object> row = new HashMap<>();
            row.put("productId",  p != null ? p.getId()        : null);
            row.put("name",       p != null ? p.getName()      : null);
            row.put("price",      p != null ? p.getPrice()     : 0.0);
            row.put("imageUrl",   p != null ? p.getImageUrl()  : null);
            row.put("quantity",   item.getQuantity());
            row.put("size",       item.getSize());
            row.put("topping",    item.getTopping());
            row.put("lineTotal",  item.getTotalPrice());

            String autoId = db.collection("orders").document(orderId)
                    .collection("items").document().getId();
            batch.set(db.collection("orders").document(orderId)
                    .collection("items").document(autoId), row);
        }

        batch.commit()
                .addOnSuccessListener(v -> {
                    setLoading(false);
                    Toast.makeText(this, "Đặt hàng thành công!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Lỗi đặt hàng: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }
}
