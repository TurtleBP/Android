package com.pro.milkteaapp.activity;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.pro.milkteaapp.databinding.ActivityCheckoutBinding;
import com.pro.milkteaapp.models.CartItem;
import com.pro.milkteaapp.models.CheckoutInfo;
import com.pro.milkteaapp.models.Products;
import com.pro.milkteaapp.models.SelectedTopping;
import com.pro.milkteaapp.models.User;
import com.pro.milkteaapp.utils.MoneyUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class CheckoutActivity extends AppCompatActivity {

    private ActivityCheckoutBinding binding;
    private final ArrayList<CartItem> cartItems = new ArrayList<>();

    @Nullable private CheckoutInfo checkoutInfo;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private User currentUser;
    private double subtotal; // Tổng tiền hàng
    private double discountPercent = 0.0;
    private double totalAmount;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCheckoutBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        setupToolbar();
        restoreFromIntent();
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

    @SuppressWarnings("unchecked")
    private void restoreFromIntent() {
        Serializable extra = getIntent().getSerializableExtra("cart_items");
        if (extra instanceof ArrayList<?> raw) {
            for (Object o : raw) if (o instanceof CartItem) cartItems.add((CartItem) o);
        }
        Serializable infoSer = getIntent().getSerializableExtra("checkout_info");
        if (infoSer instanceof CheckoutInfo) {
            checkoutInfo = (CheckoutInfo) infoSer;
            if (!TextUtils.isEmpty(checkoutInfo.getAddress()) && binding.edtAddress != null) {
                binding.edtAddress.setText(checkoutInfo.getAddress());
            }
        }
    }

    private long computeSubtotal() {
        long sum = 0;
        for (CartItem i : cartItems) sum += i.getTotalPrice();
        return sum;
    }

    private long getSubtotal() {
        return checkoutInfo != null ? (long) checkoutInfo.getSubtotal() : computeSubtotal();
    }

    private long getDiscount() {
        return checkoutInfo != null ? (long) checkoutInfo.getDiscount() : 0;
    }

    private long getShippingFee() {
        return checkoutInfo != null ? (long) checkoutInfo.getShippingFee() : 15_000L;
    }

    private long getGrandTotal() {
        return checkoutInfo != null ? (long) checkoutInfo.getGrandTotal()
                : Math.max(0, getSubtotal() - getDiscount() + getShippingFee());
    }

    private void renderTotal() {
        binding.tvTotal.setText(MoneyUtils.formatVnd(getGrandTotal()));
    }

    private boolean validateInputs() {
        String name    = binding.edtName.getText() != null ? binding.edtName.getText().toString().trim() : "";
        String phone   = binding.edtPhone.getText() != null ? binding.edtPhone.getText().toString().trim() : "";
        String address = binding.edtAddress.getText() != null ? binding.edtAddress.getText().toString().trim() : "";

        if (TextUtils.isEmpty(name))    { binding.edtName.setError("Vui lòng nhập họ tên"); binding.edtName.requestFocus(); return false; }
        if (TextUtils.isEmpty(phone))   { binding.edtPhone.setError("Vui lòng nhập số điện thoại"); binding.edtPhone.requestFocus(); return false; }
        if (TextUtils.isEmpty(address)) { binding.edtAddress.setError("Vui lòng nhập địa chỉ"); binding.edtAddress.requestFocus(); return false; }
        if (cartItems.isEmpty()) {
            Toast.makeText(this, "Giỏ hàng trống", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void loadCheckoutData() {

        String userId = FirebaseAuth.getInstance().getUid();
        if (userId != null) {
            FirebaseFirestore.getInstance().collection("users").document(userId).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            currentUser = documentSnapshot.toObject(User.class);
                        }
                        calculateFinalTotal();
                    })
                    .addOnFailureListener(e -> {
                        calculateFinalTotal();
                    });
        } else {
            calculateFinalTotal();
        }
    }

    private void calculateFinalTotal() {

        double discountPercent = 0.0;
        double loyaltyDiscount = 0.0;

        if (currentUser != null && currentUser.getLoyaltyTier() != null) {
            switch (currentUser.getLoyaltyTier()) {
                case "Gold":
                    discountPercent = 0.10;
                    break;
                case "Silver":
                    discountPercent = 0.05;
                    break;
                default:
                    discountPercent = 0.0;
                    break;
            }
        }

        loyaltyDiscount = subtotal * discountPercent;

        totalAmount = subtotal - loyaltyDiscount;
    }

    private void setLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnPlaceOrder.setEnabled(!loading);
    }

    private void placeOrder() {
        if (!validateInputs()) return;
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "Bạn chưa đăng nhập", Toast.LENGTH_SHORT).show();
            return;
        }
        setLoading(true);

        String uid = auth.getCurrentUser().getUid();
        long subtotal = getSubtotal();
        long discount = getDiscount();
        long shipping = getShippingFee();
        long total = getGrandTotal();

        String paymentMethod = checkoutInfo != null ? checkoutInfo.getPaymentMethod() : "COD";
        String shippingLabel = checkoutInfo != null ? checkoutInfo.getShippingLabel()
                : String.format(Locale.getDefault(), "Tiêu chuẩn (%s)", MoneyUtils.formatVnd(shipping));
        String voucherCode = checkoutInfo != null ? checkoutInfo.getVoucherCode() : "";

        String orderId = db.collection("orders").document().getId();

        Map<String, Object> order = new HashMap<>();
        order.put("orderId", orderId);
        order.put("userId", uid);
        order.put("name", binding.edtName.getText().toString().trim());
        order.put("phone", binding.edtPhone.getText().toString().trim());
        order.put("address", binding.edtAddress.getText().toString().trim());
        order.put("subtotal", subtotal);
        order.put("discount", discount);
        order.put("shippingFee", shipping);
        order.put("total", total);
        order.put("paymentMethod", paymentMethod);
        order.put("shippingLabel", shippingLabel);
        order.put("voucherCode", TextUtils.isEmpty(voucherCode) ? null : voucherCode);
        order.put("status", "pending");
        order.put("createdAt", Timestamp.now());

        WriteBatch batch = db.batch();
        batch.set(db.collection("orders").document(orderId), order);

        for (CartItem item : cartItems) {
            Products p = item.getMilkTea();
            Map<String, Object> row = new HashMap<>();
            row.put("productId", p != null ? p.getId() : null);
            row.put("name", p != null ? p.getName() : null);
            row.put("price", p != null && p.getPrice() != null ? p.getPrice().longValue() : 0L);
            row.put("quantity", item.getQuantity());
            row.put("size", item.getSize());
            row.put("unitPrice", item.getUnitPrice());
            row.put("lineTotal", item.getTotalPrice());

            java.util.List<Map<String, Object>> tops = new java.util.ArrayList<>();
            for (SelectedTopping st : item.getToppings()) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", st.id);
                m.put("name", st.name);
                m.put("price", st.price);
                tops.add(m);
            }
            row.put("toppings", tops);

            String itemId = db.collection("orders").document(orderId)
                    .collection("items").document().getId();
            batch.set(db.collection("orders").document(orderId)
                    .collection("items").document(itemId), row);
        }

        batch.commit()
                .addOnSuccessListener(v -> {
                    recordVoucherUsageIfNeeded(uid, voucherCode,
                            () -> {
                                setLoading(false);
                                Toast.makeText(this, "Đặt hàng thành công!", Toast.LENGTH_SHORT).show();
                                finish();
                            },
                            e -> {
                                setLoading(false);
                                Toast.makeText(this, "Đơn đã tạo nhưng cập nhật lượt voucher lỗi: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                finish();
                            });
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Lỗi đặt hàng: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void recordVoucherUsageIfNeeded(@NonNull String uid,
                                            @Nullable String voucherCode,
                                            @NonNull Runnable onDone,
                                            @NonNull java.util.function.Consumer<Exception> onError) {
        if (TextUtils.isEmpty(voucherCode)) { onDone.run(); return; }

        db.collection("vouchers")
                .whereEqualTo("code", voucherCode.trim().toUpperCase(Locale.getDefault()))
                .limit(1)
                .get()
                .addOnSuccessListener(qs -> {
                    if (qs.isEmpty()) { onDone.run(); return; }
                    String voucherId = qs.getDocuments().get(0).getId();

                    db.collection("users").document(uid)
                            .collection("voucher_usages").document(voucherId)
                            .update("count", FieldValue.increment(1))
                            .addOnSuccessListener(x -> onDone.run())
                            .addOnFailureListener(e -> {
                                Map<String,Object> seed = new HashMap<>();
                                seed.put("count", 1L);
                                db.collection("users").document(uid)
                                        .collection("voucher_usages").document(voucherId)
                                        .set(seed)
                                        .addOnSuccessListener(x -> onDone.run())
                                        .addOnFailureListener(onError::accept);
                            });
                })
                .addOnFailureListener(onError::accept);
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }
}
