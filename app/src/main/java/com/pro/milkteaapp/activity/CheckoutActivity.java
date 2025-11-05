package com.pro.milkteaapp.activity;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.pro.milkteaapp.SessionManager;
import com.pro.milkteaapp.fragment.bottomsheet.CheckoutBottomSheet;
import com.pro.milkteaapp.models.CartItem;
import com.pro.milkteaapp.models.CheckoutInfo;
import com.pro.milkteaapp.models.Products;
import com.pro.milkteaapp.models.SelectedTopping;
import com.pro.milkteaapp.utils.MoneyUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class CheckoutActivity extends AppCompatActivity
        implements CheckoutBottomSheet.OnCheckoutConfirmListener {

    private final ArrayList<CartItem> cartItems = new ArrayList<>();
    @Nullable
    private CheckoutInfo checkoutInfo;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        restoreFromIntent();
        openCheckoutBottomSheet();
    }

    private void restoreFromIntent() {
        Serializable extra = getIntent().getSerializableExtra("cart_items");
        if (extra instanceof ArrayList<?> raw) {
            for (Object o : raw) {
                if (o instanceof CartItem) {
                    cartItems.add((CartItem) o);
                }
            }
        }

        Serializable infoSer = getIntent().getSerializableExtra("checkout_info");
        if (infoSer instanceof CheckoutInfo) {
            checkoutInfo = (CheckoutInfo) infoSer;
        }
    }

    private void openCheckoutBottomSheet() {
        CheckoutBottomSheet sheet = CheckoutBottomSheet.newInstance(cartItems);
        sheet.setOnCheckoutConfirmListener(this);
        sheet.show(getSupportFragmentManager(), "CheckoutBottomSheet");
    }

    @Override
    public void onCheckoutConfirmed(@NonNull CheckoutInfo info) {
        this.checkoutInfo = info;
        placeOrder();
    }

    private long computeSubtotalFromCart() {
        long sum = 0;
        for (CartItem i : cartItems) {
            sum += i.getTotalPrice();
        }
        return sum;
    }

    private long getSubtotal() {
        return checkoutInfo != null
                ? (long) checkoutInfo.getSubtotal()
                : computeSubtotalFromCart();
    }

    private long getDiscount() {
        return checkoutInfo != null
                ? (long) checkoutInfo.getDiscount()
                : 0L;
    }

    private long getShippingFee() {
        return checkoutInfo != null
                ? (long) checkoutInfo.getShippingFee()
                : 15_000L;
    }

    private long getGrandTotal() {
        return checkoutInfo != null
                ? (long) checkoutInfo.getGrandTotal()
                : Math.max(0L, getSubtotal() - getDiscount() + getShippingFee());
    }

    private void placeOrder() {
        if (cartItems.isEmpty()) {
            Toast.makeText(this, "Giỏ hàng trống", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "Bạn chưa đăng nhập", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // ✅ Lấy userId hiệu lực: ưu tiên USRxxxxx từ SessionManager, fallback Firebase UID
        String effectiveUid = null;
        try {
            effectiveUid = new SessionManager(getApplicationContext()).getUid();
        } catch (Throwable ignored) {}
        if (TextUtils.isEmpty(effectiveUid)) {
            effectiveUid = auth.getCurrentUser().getUid();
        }

        long subtotal = getSubtotal();
        long discount = getDiscount();
        long shipping = getShippingFee();
        long finalTotal = getGrandTotal();

        String address = checkoutInfo != null ? checkoutInfo.getAddress() : "";
        String paymentMethod = checkoutInfo != null ? checkoutInfo.getPaymentMethod() : "COD";
        String shippingLabel = checkoutInfo != null
                ? checkoutInfo.getShippingLabel()
                : String.format(Locale.getDefault(), "Tiêu chuẩn (%s)", MoneyUtils.formatVnd(shipping));
        String voucherCode = checkoutInfo != null ? checkoutInfo.getVoucherCode() : "";

        if (TextUtils.isEmpty(address)) {
            Toast.makeText(this, "Vui lòng chọn địa chỉ giao hàng", Toast.LENGTH_SHORT).show();
            return;
        }

        // Có thể dùng OrdersIdGenerator nếu dự án của bạn đang áp dụng; ở đây giữ nguyên doc id tự sinh
        String orderId = db.collection("orders").document().getId();

        Map<String, Object> order = new HashMap<>();
        order.put("orderId", orderId);
        order.put("userId", effectiveUid);        // ✅ đồng bộ users/{USRxxxxx}
        order.put("name", null);
        order.put("phone", null);
        order.put("address", address);
        order.put("subtotal", subtotal);
        order.put("discount", discount);
        order.put("shippingFee", shipping);
        order.put("finalTotal", finalTotal);      // ✅ đổi từ "total" → "finalTotal"
        order.put("paymentMethod", paymentMethod);
        order.put("shippingLabel", shippingLabel);
        order.put("voucherCode", TextUtils.isEmpty(voucherCode) ? null : voucherCode);
        order.put("status", "PENDING");           // ✅ chuẩn hóa status
        order.put("createdAt", FieldValue.serverTimestamp()); // ✅ đồng bộ timestamp server

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

            // Chi tiết tùy chọn
            row.put("sugar", item.getSugar());
            row.put("ice", item.getIce());
            row.put("note", item.getNote());

            // toppings
            java.util.List<Map<String, Object>> tops = new ArrayList<>();
            if (item.getToppings() != null) {
                for (SelectedTopping st : item.getToppings()) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", st.getId());
                    m.put("name", st.getName());
                    m.put("price", st.getPrice());
                    tops.add(m);
                }
            }
            row.put("toppings", tops);

            String itemId = db.collection("orders").document(orderId)
                    .collection("items").document().getId();
            batch.set(
                    db.collection("orders").document(orderId)
                            .collection("items").document(itemId),
                    row
            );
        }

        final String finalEffectiveUid = effectiveUid;
        final String finalVoucherCode = voucherCode;

        batch.commit()
                .addOnSuccessListener(v -> {
                    // Không cộng điểm ở đây — điểm chỉ cộng khi Admin xác nhận FINISHED
                    recordVoucherUsageIfNeeded(finalEffectiveUid, finalVoucherCode,
                            () -> {
                                Toast.makeText(this, "Đặt hàng thành công!", Toast.LENGTH_SHORT).show();
                                finish();
                            },
                            e -> {
                                Toast.makeText(this, "Đơn đã tạo nhưng cập nhật lượt voucher lỗi: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                finish();
                            });
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Lỗi đặt hàng: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    // ====== Ghi nhận users/{uid}/voucher_usages/{voucherId}.count += 1 nếu có code ======
    private void recordVoucherUsageIfNeeded(@NonNull String uid,
                                            @Nullable String voucherCode,
                                            @NonNull Runnable onDone,
                                            @NonNull java.util.function.Consumer<Exception> onError) {
        if (TextUtils.isEmpty(voucherCode)) {
            onDone.run();
            return;
        }

        db.collection("vouchers")
                .whereEqualTo("code", voucherCode.trim().toUpperCase(Locale.getDefault()))
                .limit(1)
                .get()
                .addOnSuccessListener(qs -> {
                    if (qs.isEmpty()) {
                        onDone.run();
                        return;
                    }
                    String voucherId = qs.getDocuments().get(0).getId();

                    db.collection("users").document(uid)
                            .collection("voucher_usages").document(voucherId)
                            .update("count", FieldValue.increment(1))
                            .addOnSuccessListener(x -> onDone.run())
                            .addOnFailureListener(e -> {
                                Map<String, Object> seed = new HashMap<>();
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
