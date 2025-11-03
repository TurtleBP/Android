package com.pro.milkteaapp.activity;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.WriteBatch;
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

        String uid = auth.getCurrentUser().getUid();

        long subtotal = getSubtotal();
        long discount = getDiscount();
        long shipping = getShippingFee();
        long total = getGrandTotal();

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

        String orderId = db.collection("orders").document().getId();

        Map<String, Object> order = new HashMap<>();
        order.put("orderId", orderId);
        order.put("userId", uid);
        order.put("name", null);
        order.put("phone", null);
        order.put("address", address);
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

            java.util.List<Map<String, Object>> tops = new ArrayList<>();
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
            batch.set(
                    db.collection("orders").document(orderId)
                            .collection("items").document(itemId),
                    row
            );
        }

        batch.commit()
                .addOnSuccessListener(v -> {
                    // 1) cộng điểm thăng hạng (không trừ)
                    addLoyaltyPointsForTier(uid, total);
                    // 2) cộng điểm đổi quà (có thể trừ khi redeem)
                    addRewardPoints(uid, total);
                    // 3) cộng tổng chi tiêu
                    updateTotalSpent(uid, total);
                    // 4) ghi voucher
                    recordVoucherUsageIfNeeded(uid, voucherCode,
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

    // Điểm để lên hạng (loyaltyPoints) – KHÔNG trừ
    private void addLoyaltyPointsForTier(String userId, long totalAmount) {
        if (userId == null || userId.isEmpty() || totalAmount == 0) {
            Log.e("LoyaltyUpdate", "Không đủ thông tin để cộng điểm.");
            return;
        }

        final long pointsToAdd = totalAmount / 10_000L; // 10k = 1 điểm
        if (pointsToAdd == 0) {
            Log.d("LoyaltyUpdate", "Đơn hàng không đủ giá trị để cộng điểm thăng hạng.");
            return;
        }

        final FirebaseFirestore db = FirebaseFirestore.getInstance();
        final com.google.firebase.firestore.DocumentReference userRef = db.collection("users").document(userId);

        db.runTransaction(transaction -> {
                    DocumentSnapshot userDoc = transaction.get(userRef);
                    if (!userDoc.exists()) {
                        throw new FirebaseFirestoreException("User không tồn tại.",
                                FirebaseFirestoreException.Code.NOT_FOUND);
                    }

                    long currentPoints = userDoc.contains("loyaltyPoints")
                            ? userDoc.getLong("loyaltyPoints") : 0L;

                    long newTotalPoints = currentPoints + pointsToAdd;

                    // Áp dụng sơ đồ hạng mới
                    String newTier;
                    if (newTotalPoints >= 1000) {
                        newTier = "Vàng";
                    } else if (newTotalPoints >= 400) {
                        newTier = "Bạc";
                    } else if (newTotalPoints >= 100) {
                        newTier = "Đồng";
                    } else {
                        newTier = "Chưa xếp hạng";
                    }

                    transaction.update(userRef,
                            "loyaltyPoints", newTotalPoints,
                            "loyaltyTier", newTier
                    );
                    return null;
                })
                .addOnSuccessListener(aVoid -> Log.d("LoyaltyUpdate", "Đã cộng " + pointsToAdd + " điểm hạng cho " + userId))
                .addOnFailureListener(e -> Log.e("LoyaltyUpdate", "Cộng điểm hạng thất bại: ", e));
    }

    // Điểm để ĐỔI QUÀ (rewardPoints) – có thể trừ khi đổi quà
    private void addRewardPoints(String userId, long totalAmount) {
        if (userId == null || userId.isEmpty() || totalAmount <= 0) return;

        long rewardToAdd = totalAmount / 20_000L; // 20k = 1 điểm đổi quà
        if (rewardToAdd == 0) return;

        db.collection("users").document(userId)
                .update("rewardPoints", FieldValue.increment(rewardToAdd))
                .addOnSuccessListener(aVoid -> Log.d("Loyalty", "Cộng điểm đổi quà OK"))
                .addOnFailureListener(e -> Log.e("Loyalty", "Cộng điểm đổi quà lỗi", e));
    }

    // Tổng chi tiêu
    private void updateTotalSpent(String userId, long paidAmount) {
        if (userId == null || paidAmount <= 0) return;
        db.collection("users").document(userId)
                .update("totalSpent", FieldValue.increment(paidAmount))
                .addOnSuccessListener(aVoid -> Log.d("Loyalty", "Cập nhật tổng chi tiêu OK"))
                .addOnFailureListener(e -> Log.e("Loyalty", "Cập nhật tổng chi tiêu lỗi", e));
    }

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
