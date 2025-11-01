package com.pro.milkteaapp.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

// === IMPORT CỦA GOOGLE PAY ===
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.wallet.AutoResolveHelper;
// import com.google.android.gms.wallet.IsReadyToPayRequest; // Không cần ở đây
import com.google.android.gms.wallet.PaymentData;
import com.google.android.gms.wallet.PaymentDataRequest;
import com.google.android.gms.wallet.PaymentsClient;
import com.google.android.gms.wallet.Wallet;
import com.google.android.gms.wallet.WalletConstants;
// =============================

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
import com.pro.milkteaapp.utils.MoneyUtils;

// (Không cần import PickerSheet ở đây)

// Thêm các import JSON
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

// === KHÔNG CẦN IMPLEMENT LISTENER NỮA ===
public class CheckoutActivity extends AppCompatActivity {

    private ActivityCheckoutBinding binding;
    private final ArrayList<CartItem> cartItems = new ArrayList<>();

    @Nullable
    private CheckoutInfo checkoutInfo; // Biến này RẤT QUAN TRỌNG
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    // === BIẾN MỚI CHO GOOGLE PAY ===
    private PaymentsClient paymentsClient;
    private static final int LOAD_PAYMENT_DATA_REQUEST_CODE = 991;
    // =============================

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCheckoutBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Khởi tạo Google Pay Client (Để xử lý thanh toán)
        paymentsClient = createPaymentsClient();

        setupToolbar();
        restoreFromIntent(); // Hàm này sẽ lấy checkoutInfo từ Intent
        renderTotal();       // Hàm này sẽ lấy tổng tiền từ checkoutInfo

        // === THAY ĐỔI LISTENER CỦA NÚT "ĐẶT HÀNG" ===
        binding.btnPlaceOrder.setOnClickListener(v -> {
            // 1. Kiểm tra lại thông tin (tên, sđt, địa chỉ) trên màn hình này
            if (!validateInputs()) return;

            // 2. Lấy phương thức thanh toán từ CheckoutInfo đã nhận
            String paymentMethod = (checkoutInfo != null && checkoutInfo.getPaymentMethod() != null)
                    ? checkoutInfo.getPaymentMethod() : "COD";
            // === THÊM DÒNG NÀY ĐỂ KIỂM TRA ===
            Toast.makeText(this, "Phương thức đã chọn: '" + paymentMethod + "'", Toast.LENGTH_LONG).show();
            // ===================================
            // 3. Quyết định luồng thanh toán
            if (paymentMethod.equals("Google Pay")) {
                // Nếu là Google Pay, bắt đầu luồng Google Pay
                setLoading(true);
                requestGooglePay();
            } else {
                // Mặc định là COD
                placeOrder("COD", null);
            }
        });
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
            for (Object o : raw)
                if (o instanceof CartItem) cartItems.add((CartItem) o);
        }
        Serializable infoSer = getIntent().getSerializableExtra("checkout_info");
        if (infoSer instanceof CheckoutInfo) {
            checkoutInfo = (CheckoutInfo) infoSer;
            // Pre-fill địa chỉ từ CheckoutInfo
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
        // Lấy phí ship *cuối cùng* (đã trừ voucher) từ checkoutInfo
        return checkoutInfo != null ? (long) checkoutInfo.getShippingFee() : 15_000L;
    }

    private long getGrandTotal() {
        return checkoutInfo != null ? (long) checkoutInfo.getGrandTotal() :
                Math.max(0, getSubtotal() - getDiscount() + getShippingFee());
    }

    private void renderTotal() {
        // Hiển thị tổng tiền cuối cùng từ checkoutInfo
        binding.tvTotal.setText(MoneyUtils.formatVnd(getGrandTotal()));
    }

    private boolean validateInputs() {
        String name = binding.edtName.getText() != null ? binding.edtName.getText().toString().trim() : "";
        String phone = binding.edtPhone.getText() != null ? binding.edtPhone.getText().toString().trim() : "";
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

    private void setLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnPlaceOrder.setEnabled(!loading);
    }

    /**
     * Hàm lưu đơn hàng vào Firestore
     * @param paymentMethod Phương thức thanh toán (ví dụ: "COD", "Google Pay")
     * @param googlePayToken Token nhận được từ Google Pay (nếu có)
     */
    private void placeOrder(String paymentMethod, @Nullable String googlePayToken) {
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "Bạn chưa đăng nhập", Toast.LENGTH_SHORT).show();
            setLoading(false); // Nhớ tắt loading nếu có lỗi
            return;
        }
        setLoading(true);

        String uid = auth.getCurrentUser().getUid();

        // Lấy thông tin giá trị từ checkoutInfo
        long subtotal = getSubtotal();
        long discount = getDiscount();
        long shipping = getShippingFee(); // Đây là phí ship *cuối cùng*
        long total = getGrandTotal();

        String shippingLabel = checkoutInfo != null ? checkoutInfo.getShippingLabel() : "Tiêu chuẩn";
        String voucherCode = checkoutInfo != null ? checkoutInfo.getVoucherCode() : "";

        String orderId = db.collection("orders").document().getId();

        Map<String, Object> order = new HashMap<>();
        order.put("orderId", orderId);
        order.put("userId", uid);
        // Lấy thông tin người nhận từ các trường EditText đã được điền
        order.put("name", binding.edtName.getText().toString().trim());
        order.put("phone", binding.edtPhone.getText().toString().trim());
        order.put("address", binding.edtAddress.getText().toString().trim());

        order.put("subtotal", subtotal);
        order.put("discount", discount);
        order.put("shippingFee", shipping); // Lưu phí ship cuối cùng
        order.put("total", total);

        order.put("paymentMethod", paymentMethod); // <-- Dùng paymentMethod được truyền vào
        if (googlePayToken != null) {
            order.put("paymentToken", googlePayToken); // Lưu token (tùy chọn)
        }

        order.put("shippingLabel", shippingLabel);
        order.put("voucherCode", TextUtils.isEmpty(voucherCode) ? null : voucherCode);
        order.put("status", "pending");
        order.put("createdAt", Timestamp.now());

        WriteBatch batch = db.batch();
        batch.set(db.collection("orders").document(orderId), order);

        // Thêm các sản phẩm trong giỏ hàng vào subcollection 'items'
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

        // Commit batch và ghi lại việc sử dụng voucher
        batch.commit()
                .addOnSuccessListener(v -> {
                    recordVoucherUsageIfNeeded(uid, voucherCode,
                            () -> {
                                setLoading(false);
                                Toast.makeText(this, "Đặt hàng thành công!", Toast.LENGTH_SHORT).show();
                                finish(); // Đóng Activity sau khi thành công
                            },
                            e -> {
                                setLoading(false);
                                Toast.makeText(this, "Đơn đã tạo nhưng cập nhật lượt voucher lỗi: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                finish(); // Đóng Activity
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
                    } // Voucher không tồn tại (có thể đã bị xóa)
                    String voucherId = qs.getDocuments().get(0).getId();

                    // Ghi lại lượt sử dụng
                    db.collection("users").document(uid)
                            .collection("voucher_usages").document(voucherId)
                            .update("count", FieldValue.increment(1))
                            .addOnSuccessListener(x -> onDone.run())
                            .addOnFailureListener(e -> {
                                // Nếu thất bại (ví dụ: document chưa tồn tại), hãy tạo mới
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

    // ======================================================
    // === CÁC HÀM CỦA GOOGLE PAY (ĐỂ XỬ LÝ THANH TOÁN) ===
    // ======================================================

    /**
     * Khởi tạo PaymentsClient
     */
    private PaymentsClient createPaymentsClient() {
        Wallet.WalletOptions walletOptions = new Wallet.WalletOptions.Builder()
                .setEnvironment(WalletConstants.ENVIRONMENT_TEST) // Môi trường TEST
                .build();
        return Wallet.getPaymentsClient(this, walletOptions);
    }

    /**
     * Tạo và gửi yêu cầu thanh toán Google Pay
     */
    private void requestGooglePay() {
        // Lấy tổng số tiền TỪ CHECKOUT INFO
        long totalAmount = getGrandTotal();
        // Google Pay yêu cầu định dạng "123.00"
        String priceString = String.format(Locale.ROOT, "%d.00", totalAmount);

        // Tạo yêu cầu dữ liệu thanh toán
        Optional<JSONObject> paymentDataRequestJson = getGooglePayPaymentDataRequest(priceString);
        if (!paymentDataRequestJson.isPresent()) {
            Toast.makeText(this, "Lỗi tạo yêu cầu Google Pay", Toast.LENGTH_SHORT).show();
            setLoading(false); // Tắt loading nếu lỗi
            return;
        }

        PaymentDataRequest request = PaymentDataRequest.fromJson(paymentDataRequestJson.get().toString());

        // Hiển thị payment sheet của Google Pay
        if (request != null) {
            AutoResolveHelper.resolveTask(
                    paymentsClient.loadPaymentData(request),
                    this,
                    LOAD_PAYMENT_DATA_REQUEST_CODE);
            // Không tắt loading ở đây, vì sheet đang mở
        } else {
            setLoading(false); // Tắt loading nếu lỗi
        }
    }

    /**
     * Xử lý kết quả trả về từ Google Pay (ĐÃ SỬA LỖI STATUS)
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == LOAD_PAYMENT_DATA_REQUEST_CODE) {
            switch (resultCode) {
                case RESULT_OK:
                    // Thanh toán thành công
                    PaymentData paymentData = PaymentData.getFromIntent(data);
                    handleGooglePaySuccess(paymentData);
                    break;
                case RESULT_CANCELED:
                    // Người dùng hủy
                    Toast.makeText(this, "Đã hủy thanh toán", Toast.LENGTH_SHORT).show();
                    setLoading(false); // Tắt loading
                    break;
                case AutoResolveHelper.RESULT_ERROR:
                    // Lỗi từ Google Pay
                    Status status = AutoResolveHelper.getStatusFromIntent(data);
                    String errorMessage = (status != null && status.getStatusMessage() != null) ?
                            status.getStatusMessage() : "Lỗi Google Pay không xác định";

                    Toast.makeText(this, "Lỗi Google Pay: " + errorMessage, Toast.LENGTH_LONG).show();
                    setLoading(false); // Tắt loading
                    break;
            }
        }
    }

    /**
     * Xử lý khi Google Pay thanh toán thành công
     */
    private void handleGooglePaySuccess(PaymentData paymentData) {
        try {
            JSONObject paymentDataJson = new JSONObject(paymentData.toJson());
            // Lấy token thanh toán (để gửi lên server backend của bạn nếu cần)
            String paymentToken = paymentDataJson
                    .getJSONObject("paymentMethodData")
                    .getJSONObject("tokenizationData")
                    .getString("token");

            // Gọi hàm placeOrder với phương thức là "Google Pay"
            // Hàm placeOrder đã bao gồm setLoading(true)
            placeOrder("Google Pay", paymentToken);

        } catch (JSONException e) {
            Toast.makeText(this, "Lỗi xử lý dữ liệu thanh toán", Toast.LENGTH_LONG).show();
            setLoading(false);
        }
    }

    // --- CÁC HÀM TIỆN ÍCH JSON (CHO YÊU CẦU THANH TOÁN) ---

    private static JSONObject getBaseRequest() throws JSONException {
        return new JSONObject()
                .put("apiVersion", 2)
                .put("apiVersionMinor", 0);
    }

    private static JSONObject getGatewayTokenizationSpecification() throws JSONException {
        // Thay đổi "example" và "exampleGatewayMerchantId" bằng cổng thanh toán thật của bạn
        return new JSONObject()
                .put("type", "PAYMENT_GATEWAY")
                .put("parameters", new JSONObject()
                        .put("gateway", "example") // Ví dụ: "stripe"
                        .put("gatewayMerchantId", "exampleGatewayMerchantId")); // ID merchant của bạn
    }

    private static JSONArray getAllowedPaymentMethods() throws JSONException {
        JSONObject cardPaymentMethod = new JSONObject()
                .put("type", "CARD")
                .put("parameters", new JSONObject()
                        .put("allowedAuthMethods", new JSONArray()
                                .put("PAN_ONLY")
                                .put("CRYPTOGRAM_3DS"))
                        .put("allowedCardNetworks", new JSONArray()
                                .put("AMEX")
                                .put("DISCOVER")
                                .put("JCB")
                                .put("MASTERCARD")
                                .put("VISA")))
                .put("tokenizationSpecification", getGatewayTokenizationSpecification());

        return new JSONArray().put(cardPaymentMethod);
    }

    private static JSONObject getTransactionInfo(String price) throws JSONException {
        return new JSONObject()
                .put("totalPrice", price)
                .put("totalPriceStatus", "FINAL")
                .put("currencyCode", "VND"); // Đảm bảo đây là mã tiền tệ bạn dùng
    }

    private static JSONObject getMerchantInfo() throws JSONException {
        // Thay bằng tên cửa hàng của bạn
        return new JSONObject().put("merchantName", "Milk Tea App");
    }

    public static Optional<JSONObject> getGooglePayPaymentDataRequest(String price) {
        try {
            JSONObject paymentDataRequest = getBaseRequest();
            paymentDataRequest.put("allowedPaymentMethods", getAllowedPaymentMethods());
            paymentDataRequest.put("transactionInfo", getTransactionInfo(price));
            paymentDataRequest.put("merchantInfo", getMerchantInfo());

            // Tùy chọn:
            paymentDataRequest.put("shippingAddressRequired", false);
            paymentDataRequest.put("emailRequired", false);

            return Optional.of(paymentDataRequest);
        } catch (JSONException e) {
            return Optional.empty();
        }
    }
}