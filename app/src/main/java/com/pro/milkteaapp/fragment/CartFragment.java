package com.pro.milkteaapp.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.*;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.*;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.activity.MainActivity;
import com.pro.milkteaapp.adapter.CartAdapter;
import com.pro.milkteaapp.databinding.FragmentCartBinding;
// THÊM CÁC IMPORT NÀY
import com.pro.milkteaapp.data.OrdersIdGenerator;
import com.pro.milkteaapp.fragment.bottomsheet.BankTransferSheet;
import com.pro.milkteaapp.fragment.bottomsheet.CheckoutBottomSheet;
import com.pro.milkteaapp.fragment.pickersheet.PaymentMethodPickerSheet;
// ====================
import com.pro.milkteaapp.models.*;
import com.pro.milkteaapp.utils.MoneyUtils;


import java.util.*;

public class CartFragment extends Fragment
        implements CartAdapter.OnItemActionClickListener,
        CheckoutBottomSheet.OnCheckoutConfirmListener {

    private static final String TAG = "CartFragment";

    private FragmentCartBinding binding;
    private CartAdapter adapter;
    public static List<CartItem> cartItems = new ArrayList<>();

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    // ---- Helpers cho lifecycle/UI ----
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private boolean isUiSafe() {
        return isAdded()
                && getView() != null
                && getViewLifecycleOwner().getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED);
    }

    private void runOnUiSafe(Runnable r) {
        if (!isUiSafe()) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else {
            mainHandler.post(() -> {
                if (isUiSafe()) r.run();
            });
        }
    }
    // -----------------------------------

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle s) {
        binding = FragmentCartBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        setupRecyclerView();
        setupClickListeners();
        setupBackPressedHandler();
        updateTotal();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void setupRecyclerView() {
        if (getContext() == null) return;
        binding.cartRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new CartAdapter(getContext(), cartItems, this);
        binding.cartRecyclerView.setAdapter(adapter);
    }

    @SuppressLint("NotifyDataSetChanged")
    private void setupClickListeners() {
        binding.checkoutButton.setOnClickListener(v -> {
            if (!binding.checkoutButton.isEnabled()) return;
            if (getContext() == null) return;

            if (cartItems.isEmpty()) {
                Toast.makeText(getContext(), R.string.empty_cart, Toast.LENGTH_SHORT).show();
                return;
            }

            FirebaseUser user = auth.getCurrentUser();
            if (user == null) {
                Toast.makeText(getContext(), "Vui lòng đăng nhập trước khi đặt hàng", Toast.LENGTH_SHORT).show();
                return;
            }

            CheckoutBottomSheet sheet = CheckoutBottomSheet.newInstance(new ArrayList<>(cartItems));
            sheet.setOnCheckoutConfirmListener(this);
            sheet.show(getParentFragmentManager(), "CheckoutSheet");
        });

        binding.toolbar.setNavigationOnClickListener(v -> handleBack());
    }

    private void setupBackPressedHandler() {
        OnBackPressedCallback backCallback = new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { handleBack(); }
        };
        requireActivity().getOnBackPressedDispatcher()
                .addCallback(getViewLifecycleOwner(), backCallback);
    }

    private void handleBack() {
        if (getActivity() == null) return;
        if (getParentFragmentManager().popBackStackImmediate()) return;
        if (getActivity() instanceof MainActivity) {
            try { ((MainActivity) getActivity()).selectBottomNav(R.id.navigation_home); return; }
            catch (Throwable ignored) {}
        }
        getActivity().finish();
    }

    private void updateTotal() {
        if (binding == null) return;
        double total = 0;
        for (CartItem item : cartItems) total += item.getTotalPrice();
        binding.totalTextView.setText(MoneyUtils.formatVnd(total));
        binding.checkoutButton.setEnabled(!cartItems.isEmpty());
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override public void onResume() {
        super.onResume();
        if (adapter != null) adapter.notifyDataSetChanged();
        updateTotal();
    }

    @Override
    public void onItemRemoved(int position) {
        if (getActivity() == null || isDetached()) return;
        if (position < 0 || position >= cartItems.size()) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("Xác nhận xóa")
                .setMessage("Bạn có chắc chắn muốn xóa sản phẩm này khỏi giỏ hàng không?")
                .setPositiveButton("Xóa", (dialog, which) -> {
                    cartItems.remove(position);
                    if (adapter != null) adapter.notifyItemRemoved(position);
                    updateTotal();
                    Toast.makeText(getContext(), "Đã xóa sản phẩm", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    @Override public void onQuantityIncreased(int position) {
        if (position >= 0 && position < cartItems.size()) {
            cartItems.get(position).increaseQuantity();
            if (adapter != null) adapter.notifyItemChanged(position);
            updateTotal();
        }
    }

    @Override public void onQuantityDecreased(int position) {
        if (position >= 0 && position < cartItems.size()) {
            CartItem item = cartItems.get(position);
            if (item.getQuantity() > 1) {
                item.decreaseQuantity();
                if (adapter != null) adapter.notifyItemChanged(position);
                updateTotal();
            } else onItemRemoved(position);
        }
    }

    // ====== callback từ CheckoutBottomSheet ======
    @Override
    public void onCheckoutConfirmed(@NonNull CheckoutInfo info) {
        Log.d(TAG, "Checkout confirmed. Payment method: " + info.paymentMethod);
        placeOrder(info);
    }

    @SuppressLint("NotifyDataSetChanged")
    private void placeOrder(@NonNull CheckoutInfo info) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            if (getContext() != null) {
                Toast.makeText(getContext(), "Vui lòng đăng nhập trước khi đặt hàng", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        final Context app = requireContext().getApplicationContext();
        if (binding != null) binding.checkoutButton.setEnabled(false);

// === LOGIC MỚI: PHÂN TÁCH PTTT ===

        // 1. Lấy ID đơn hàng TRƯỚC TIÊN
        new OrdersIdGenerator().nextOrderId()
                .addOnSuccessListener(orderId -> {
                    // Đã có orderId (ví dụ: "ORD12345")

                    // 2. Kiểm tra PTTT
                    if (PaymentMethodPickerSheet.METHOD_BANK_TRANSFER.equals(info.paymentMethod)) {
                        // === XỬ LÝ CHUYỂN KHOẢN ===

                        // 3. Tạo nội dung memo thật
                        String customerName = user.getDisplayName();
                        if (customerName == null || customerName.isEmpty()) {
                            customerName = user.getPhoneNumber(); // Lấy SĐT nếu không có tên
                        }
                        if (customerName == null) customerName = "KHACHHANG";

                        String safeName = customerName.toUpperCase(Locale.ROOT)
                                .replaceAll("\\s+", "")
                                .replaceAll("[^A-Z0-9]", "");

                        String finalMemo = safeName + orderId; // Ví dụ: NGUYENVANAORD12345

                        // 4. Hiển thị trang ngân hàng ảo
                        BankTransferSheet bankSheet = BankTransferSheet.newInstance(info.getGrandTotal(), finalMemo);

                        bankSheet.setListener(() -> {
                            // 5. Khi người dùng nhấn "Tôi đã hoàn tất thanh toán"
                            // -> Lưu đơn hàng vào DB
                            Log.d(TAG, "Bank payment confirmed by user for order: " + orderId);
                            saveOrderToFirestore(user.getUid(), orderId, info, app);
                        });

                        // Phải chạy trên UI thread
                        runOnUiSafe(() -> {
                            bankSheet.show(getParentFragmentManager(), "BankTransferSheet");
                            if (binding != null) binding.checkoutButton.setEnabled(true); // Kích hoạt lại nút
                        });

                    } else {
                        // === XỬ LÝ COD (HOẶC PTTT KHÁC) ===
                        // 3. Lưu đơn hàng ngay lập tức
                        Log.d(TAG, "Saving COD order: " + orderId);
                        saveOrderToFirestore(user.getUid(), orderId, info, app);
                    }
                })
                .addOnFailureListener(e -> {
                    // Lỗi khi tạo OrderID
                    Toast.makeText(app, "Lỗi sinh mã đơn: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    runOnUiSafe(() -> {
                        if (binding != null) binding.checkoutButton.setEnabled(true);
                    });
                });
    }

    /**
     * Hàm trợ giúp mới: Lưu đơn hàng vào Firestore
     */
    @SuppressLint("NotifyDataSetChanged")
    private void saveOrderToFirestore(String userId, String orderId, CheckoutInfo info, Context app) {

        runOnUiSafe(() -> {
            if (binding != null) binding.checkoutButton.setEnabled(false);
        });

        // 1. Build payload
        Map<String, Object> payload = buildOrderPayload(userId, cartItems, info);

        // 2. Set (Ghi đè) lên document
        db.collection("orders").document(orderId).set(payload)
                .addOnSuccessListener(aVoid -> {
                    // 3. Ghi nhận lượt dùng voucher
                    recordVoucherUsageIfNeeded(userId, info.voucherCode,
                            () -> {
                                // 4. Thành công -> Xóa giỏ hàng VÀ HIỂN THỊ TOAST
                                cartItems.clear();
                                Toast.makeText(app, app.getString(R.string.order_placed_successfully), Toast.LENGTH_SHORT).show();

                                runOnUiSafe(() -> {
                                    if (adapter != null) adapter.notifyDataSetChanged();
                                    updateTotal();
                                });
                            },
                            e -> {
                                // Lỗi ghi voucher nhưng vẫn thành công
                                Toast.makeText(app, "Đơn đã tạo, nhưng cập nhật lượt voucher lỗi: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                cartItems.clear();
                                runOnUiSafe(() -> {
                                    if (adapter != null) adapter.notifyDataSetChanged();
                                    updateTotal();
                                });
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(app, "Lỗi tạo đơn: " + e.getMessage(), Toast.LENGTH_LONG).show();
                })
                .addOnCompleteListener(task -> runOnUiSafe(() -> {
                    if (binding != null) binding.checkoutButton.setEnabled(true);
                }));
    }

    // Giữ nguyên method để tham khảo (KHÔNG gọi ở bước đặt hàng).
    private void updateLoyaltyPoints(String userId, double totalAmount) {
        if (userId == null || userId.isEmpty() || totalAmount == 0) {
            Log.e("LoyaltyUpdate", "Không đủ thông tin để cộng điểm.");
            return;
        }

        // quy tắc: 10k = 1 điểm
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
                    String newTier = "Đồng"; // Hạng mặc định

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

    /** Build payload đơn hàng theo CheckoutInfo (đã có giảm giá/ship/tổng) */
    private Map<String, Object> buildOrderPayload(String userId, List<CartItem> items, CheckoutInfo info) {
        List<Map<String, Object>> itemMaps = new ArrayList<>();
        for (CartItem ci : items) itemMaps.add(ci.toOrderItemMap());

        Map<String, Object> order = new HashMap<>();
        order.put("userId", userId);
        // === LOGIC CẬP NHẬT ===
        String pm = info.paymentMethod;
        // Kiểm tra xem phương thức thanh toán có phải COD không
        boolean isPaid = !PaymentMethodPickerSheet.METHOD_COD.equals(pm);

        order.put("status", "PENDING"); // Tất cả đơn hàng đều bắt đầu là PENDING
        order.put("isPaid", isPaid);    // Ghi lại trạng thái đã thanh toán
        order.put("paymentMethod", pm); // Ghi lại phương thức thanh toán
        // ======================
        order.put("items", itemMaps);

        order.put("subtotal", info.subtotal);
        order.put("discount", info.discount);
        order.put("shippingFee", info.shippingFee);
        order.put("finalTotal", info.grandTotal);

        final String shippingAddr = info.address.trim();
        order.put("address", shippingAddr);
        order.put("shippingAddress", shippingAddr);

        Map<String, Object> addressObj = new HashMap<>();
        if (info.addressObj != null) {
            Address a = info.addressObj;
            addressObj.put("fullName", a.getFullName());
            addressObj.put("phone", a.getPhone());
            addressObj.put("line1", a.getLine1());
            addressObj.put("line2", a.getLine2());
            addressObj.put("city", a.getCity());
            addressObj.put("province", a.getProvince());
            addressObj.put("postalCode", a.getPostalCode());
            addressObj.put("display", a.displayLine());
        } else {
            addressObj.put("display", shippingAddr);
        }
        order.put("addressObj", addressObj);

//        order.put("paymentMethod", info.paymentMethod);
        order.put("voucherCode", info.voucherCode);
        order.put("shippingLabel", info.shippingLabel);

        order.put("createdAt", FieldValue.serverTimestamp());
        return order;
    }

    /** Ghi nhận users/{uid}/voucher_usages/{voucherId}.count += 1 nếu có code */
    private void recordVoucherUsageIfNeeded(@NonNull String uid,
                                            @Nullable String voucherCode,
                                            @NonNull Runnable onDone,
                                            @NonNull java.util.function.Consumer<Exception> onError) {
        if (voucherCode == null || voucherCode.trim().isEmpty()) { onDone.run(); return; }

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
}
