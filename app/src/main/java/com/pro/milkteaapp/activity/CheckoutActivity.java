package com.pro.milkteaapp.activity;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
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
import com.pro.milkteaapp.models.User;
import com.pro.milkteaapp.utils.MoneyUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Activity trung gian:
 * - nhận cart_items từ Intent
 * - mở CheckoutBottomSheet để user chọn địa chỉ / payment / voucher / ship
 * - nhận CheckoutInfo trả về qua onCheckoutConfirmed(...)
 * - tạo đơn trên Firestore
 * - đóng Activity
 * Không dùng layout activity_checkout.xml nữa.
 */
public class CheckoutActivity extends AppCompatActivity
        implements CheckoutBottomSheet.OnCheckoutConfirmListener {

    // giỏ hàng
    private final ArrayList<CartItem> cartItems = new ArrayList<>();

    // info từ bottomsheet trả về
    @Nullable
    private CheckoutInfo checkoutInfo;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private User currentUser;
    private double subtotal; // Tổng tiền hàng
    private double discountPercent = 0.0;
    private double totalAmount;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // KHÔNG setContentView(...) vì file XML cũ đã xoá

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        restoreFromIntent();
        openCheckoutBottomSheet();
    }

    private void restoreFromIntent() {
        // nhận list cart từ Intent
        Serializable extra = getIntent().getSerializableExtra("cart_items");
        if (extra instanceof ArrayList<?> raw) {
            for (Object o : raw) {
                if (o instanceof CartItem) {
                    cartItems.add((CartItem) o);
                }
            }
        }

        // nếu trước đó có send sẵn CheckoutInfo thì vẫn giữ
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
                    updateLoyaltyPoints(uid, total);
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
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Lỗi đặt hàng: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void updateLoyaltyPoints(String userId, double totalAmount) {
        if (userId == null || userId.isEmpty() || totalAmount == 0) {
            Log.e("LoyaltyUpdate", "Không đủ thông tin để cộng điểm.");
            return;
        }

        final long pointsToAdd = (long) Math.floor(totalAmount / 10000);
        if (pointsToAdd == 0) {
            Log.d("LoyaltyUpdate", "Đơn hàng không đủ giá trị để cộng điểm.");
            return;
        }

        final FirebaseFirestore db = FirebaseFirestore.getInstance();
        final DocumentReference userRef = db.collection("users").document(userId);

        db.runTransaction(transaction -> {
                    DocumentSnapshot userDoc = transaction.get(userRef);
                    if (!userDoc.exists()) {
                        throw new FirebaseFirestoreException("User không tồn tại.",
                                FirebaseFirestoreException.Code.NOT_FOUND);
                    }

                    long currentPoints = 0;
                    if (userDoc.contains("loyaltyPoints")) {
                        currentPoints = userDoc.getLong("loyaltyPoints");
                    }

                    long newTotalPoints = currentPoints + pointsToAdd;
                    String newTier = "Đồng";

                    if (newTotalPoints >= 2000) {
                        newTier = "Vàng";
                    } else if (newTotalPoints >= 500) {
                        newTier = "Bạc";
                    } else {
                        String currentTier = userDoc.getString("loyaltyTier");
                        newTier = (currentTier != null) ? currentTier : "Đồng";
                    }

                    transaction.update(userRef,
                            "loyaltyPoints", newTotalPoints,
                            "loyaltyTier", newTier
                    );

                    return null;
                })
                .addOnSuccessListener(aVoid -> Log.d("LoyaltyUpdate", "Đã cộng thành công " + pointsToAdd + " điểm cho user " + userId))
                .addOnFailureListener(e -> Log.e("LoyaltyUpdate", "Cộng điểm thất bại: ", e));
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
