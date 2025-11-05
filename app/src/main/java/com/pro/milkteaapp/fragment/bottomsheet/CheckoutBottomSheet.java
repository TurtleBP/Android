package com.pro.milkteaapp.fragment.bottomsheet;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.pro.milkteaapp.SessionManager;
import com.pro.milkteaapp.adapter.CheckoutItemAdapter;
import com.pro.milkteaapp.databinding.BottomsheetCheckoutBinding;
import com.pro.milkteaapp.fragment.pickersheet.AddressPickerSheet;
import com.pro.milkteaapp.fragment.pickersheet.PaymentMethodPickerSheet;
import com.pro.milkteaapp.fragment.pickersheet.ShippingPickerSheet;
import com.pro.milkteaapp.fragment.pickersheet.VoucherListPickerSheet;
import com.pro.milkteaapp.models.Address;
import com.pro.milkteaapp.models.CartItem;
import com.pro.milkteaapp.models.CheckoutInfo;
import com.pro.milkteaapp.models.User;
import com.pro.milkteaapp.models.Voucher;
import com.pro.milkteaapp.utils.LoyaltyPolicy;
import com.pro.milkteaapp.utils.MoneyUtils;
import com.pro.milkteaapp.utils.VoucherUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CheckoutBottomSheet extends BottomSheetDialogFragment {

    public interface OnCheckoutConfirmListener {
        void onCheckoutConfirmed(@NonNull CheckoutInfo info);
    }

    private static final String ARG_ITEMS = "arg_items";

    public static CheckoutBottomSheet newInstance(@NonNull ArrayList<CartItem> items) {
        CheckoutBottomSheet f = new CheckoutBottomSheet();
        Bundle b = new Bundle();
        b.putSerializable(ARG_ITEMS, items);
        f.setArguments(b);
        return f;
    }

    private BottomsheetCheckoutBinding b;
    private OnCheckoutConfirmListener listener;
    private final List<CartItem> items = new ArrayList<>();

    // State
    @Nullable private Address selectedAddress;
    private String paymentMethod = "COD";
    @Nullable private Voucher selectedVoucher = null;
    private String voucherCode = "";
    private String shippingLabel = "Tiêu chuẩn (15.000đ)";
    private long shippingFee = 15_000L;

    @Nullable private User currentUser;

    // ====== Prefs lưu địa chỉ đã chọn gần nhất ======
    private static final String PREFS = "checkout_prefs";
    private static final String KEY_LAST_ADDR_ID = "last_checkout_address_id";

    public void setOnCheckoutConfirmListener(OnCheckoutConfirmListener l) {
        this.listener = l;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup container, @Nullable Bundle s) {
        b = BottomsheetCheckoutBinding.inflate(inf, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);

        // Nhận danh sách item
        if (getArguments() != null) {
            Serializable ser = getArguments().getSerializable(ARG_ITEMS);
            if (ser instanceof ArrayList<?>) {
                for (Object o : (ArrayList<?>) ser) {
                    if (o instanceof CartItem) items.add((CartItem) o);
                }
            }
        }

        // List sản phẩm
        b.rvItems.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.rvItems.setAdapter(new CheckoutItemAdapter(requireContext(), items));

        // 1) Tải địa chỉ đã chọn gần nhất (nếu có), nếu không -> 2) lấy địa chỉ mặc định
        loadInitialAddressThenRender();

        // Tải thông tin User để tính giảm giá
        loadUserAndRender();

        // Render ban đầu
        updateSummaryUI();

        // Chọn địa chỉ
        b.rowAddress.setOnClickListener(v1 -> {
            AddressPickerSheet sheet = new AddressPickerSheet();
            sheet.setListener(addr -> {
                selectedAddress = addr;
                // Lưu lại ID địa chỉ đã chọn để lần sau mở Checkout sẽ ưu tiên hiển thị
                if (addr != null && addr.getId() != null) {
                    saveLastAddressId(addr.getId());
                }
                setText(b.tvAddressValue, addr != null ? addr.displayLine() : "Chọn địa chỉ");
                updateSummaryUI();
            });
            sheet.show(getParentFragmentManager(), "AddressPicker");
        });

        // Chọn phương thức thanh toán
        b.rowPayment.setOnClickListener(v12 -> {
            PaymentMethodPickerSheet sheet = new PaymentMethodPickerSheet();
            sheet.setListener(pm -> {
                paymentMethod = pm;
                setText(b.tvPaymentValue, pm);
                updateSummaryUI();
            });
            sheet.show(getParentFragmentManager(), "PaymentPicker");
        });

        // Chọn voucher từ danh sách hợp lệ
        b.rowVoucher.setOnClickListener(v13 -> {
            Bundle args = new Bundle();
            args.putLong("subtotal", calcSubtotal());
            args.putString("payment", paymentMethod);
            // ✅ Truyền custom userId (USRxxxxx) cho sheet lọc voucher theo user
            String uid = getEffectiveUserId(requireContext());
            args.putString("userId", uid);

            VoucherListPickerSheet sheet = new VoucherListPickerSheet();
            sheet.setArguments(args);
            sheet.setListener(vSelected -> {
                selectedVoucher = vSelected;
                voucherCode = (vSelected != null ? safe(vSelected.getCode()) : "");
                setText(b.tvVoucherValue, voucherCode.isEmpty() ? "Không áp dụng" : voucherCode);
                updateSummaryUI();
            });
            sheet.show(getParentFragmentManager(), "VoucherPickerList");
        });

        // Chọn phương thức giao hàng
        b.rowShipping.setOnClickListener(v14 -> {
            ShippingPickerSheet sheet = new ShippingPickerSheet();
            sheet.setListener((label, fee) -> {
                shippingLabel = label;
                shippingFee = (long) fee;
                setText(b.tvShippingValue, label);
                updateSummaryUI();
            });
            sheet.show(getParentFragmentManager(), "ShippingPicker");
        });

        // Xác nhận
        b.btnConfirm.setOnClickListener(v15 -> {
            if (selectedAddress == null) {
                setText(b.tvAddressValue, "Vui lòng chọn địa chỉ");
                return;
            }
            if (listener != null) listener.onCheckoutConfirmed(buildInfo());
            dismiss();
        });
    }

    // ====== Default Address logic ======

    private void loadInitialAddressThenRender() {
        String uid = getEffectiveUserId(requireContext());
        if (uid == null) {
            // Chưa login -> để trống
            setText(b.tvAddressValue, "Chọn địa chỉ");
            return;
        }

        String lastId = loadLastAddressId();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        if (lastId != null && !lastId.trim().isEmpty()) {
            // Ưu tiên địa chỉ đã chọn lần trước
            db.collection("users").document(uid)
                    .collection("addresses").document(lastId)
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            Address a = doc.toObject(Address.class);
                            if (a != null) {
                                a.setId(doc.getId());
                                selectedAddress = a;
                                setText(b.tvAddressValue, a.displayLine());
                                updateSummaryUI();
                                return;
                            }
                        }
                        // Không còn tồn tại -> fallback sang địa chỉ mặc định
                        fetchDefaultAddress(uid);
                    })
                    .addOnFailureListener(e -> fetchDefaultAddress(uid));
        } else {
            // Chưa có lựa chọn trước đó -> lấy mặc định
            fetchDefaultAddress(uid);
        }
    }

    private void fetchDefaultAddress(@NonNull String uid) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("users").document(uid)
                .collection("addresses")
                .whereEqualTo("isDefault", true)
                .limit(1)
                .get()
                .addOnSuccessListener(qs -> {
                    Address a = null;
                    for (DocumentSnapshot d : qs.getDocuments()) {
                        a = d.toObject(Address.class);
                        if (a != null) { a.setId(d.getId()); }
                        break;
                    }
                    selectedAddress = a;
                    setText(b.tvAddressValue, a != null ? a.displayLine() : "Chọn địa chỉ");
                    updateSummaryUI();
                })
                .addOnFailureListener(e -> setText(b.tvAddressValue, "Chọn địa chỉ"));
    }

    private void saveLastAddressId(@NonNull String id) {
        SharedPreferences sp = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_LAST_ADDR_ID, id).apply();
    }

    @Nullable
    private String loadLastAddressId() {
        SharedPreferences sp = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return sp.getString(KEY_LAST_ADDR_ID, null);
    }

    // ====== Tính tiền và render ======

    private void setText(@Nullable TextView tv, @NonNull String txt) {
        if (tv != null) tv.setText(txt);
    }

    private long calcSubtotal() {
        long subtotal = 0L;
        for (CartItem it : items) {
            subtotal += it.getUnitPrice() * it.getQuantity();
        }
        return subtotal;
    }

    private void loadUserAndRender() {
        String uid = getEffectiveUserId(requireContext());
        if (uid == null) {
            updateSummaryUI();
            return;
        }

        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        currentUser = doc.toObject(User.class);
                    }
                    updateSummaryUI();
                })
                .addOnFailureListener(e -> updateSummaryUI());
    }

    private void updateSummaryUI() {
        long subtotal = calcSubtotal();

        // Voucher
        long orderDisc = 0L, shipDisc = 0L;
        if (selectedVoucher != null) {
            VoucherUtils.DiscountResult r = VoucherUtils.calc(selectedVoucher, subtotal, shippingFee);
            orderDisc = r.orderDiscount;
            shipDisc = r.shippingDiscount;
        }
        long voucherDiscount = orderDisc + shipDisc;

        // Loyalty theo LoyaltyPolicy
        double loyaltyPercent = 0.0;
        if (currentUser != null) {
            String t = currentUser.getLoyaltyTier();
            if (t == null || t.isEmpty()) {
                long pts = currentUser.getLoyaltyPoints();
                t = LoyaltyPolicy.tierForPoints(pts);
            }
            loyaltyPercent = LoyaltyPolicy.discountPercent(t);
        }
        long loyaltyDiscount = (long) (subtotal * loyaltyPercent);

        long totalDiscount = voucherDiscount + loyaltyDiscount;
        long total = Math.max(0L, subtotal - totalDiscount + shippingFee);

        b.tvSubtotal.setText(MoneyUtils.formatVnd(subtotal));
        b.tvDiscount.setText(String.format(Locale.getDefault(), "- %s", MoneyUtils.formatVnd(voucherDiscount)));
        b.tvLoyaltyDiscount.setText(String.format(Locale.getDefault(), "- %s", MoneyUtils.formatVnd(loyaltyDiscount)));
        b.tvShipping.setText(MoneyUtils.formatVnd(shippingFee));
        b.tvFinal.setText(MoneyUtils.formatVnd(total));

        if (selectedAddress != null) setText(b.tvAddressValue, selectedAddress.displayLine());
        setText(b.tvVoucherValue, voucherCode.isEmpty() ? "Không áp dụng" : voucherCode);
        setText(b.tvShippingValue, shippingLabel);
        setText(b.tvPaymentValue, paymentMethod);
    }

    private CheckoutInfo buildInfo() {
        long subtotal = calcSubtotal();

        long orderDisc = 0L, shipDisc = 0L;
        if (selectedVoucher != null) {
            VoucherUtils.DiscountResult r = VoucherUtils.calc(selectedVoucher, subtotal, shippingFee);
            orderDisc = r.orderDiscount;
            shipDisc = r.shippingDiscount;
        }
        long voucherDiscount = orderDisc + shipDisc;

        // Loyalty theo LoyaltyPolicy
        double loyaltyPercent = 0.0;
        if (currentUser != null) {
            String t = currentUser.getLoyaltyTier();
            if (t == null || t.isEmpty()) {
                long pts = currentUser.getLoyaltyPoints();
                t = LoyaltyPolicy.tierForPoints(pts);
            }
            loyaltyPercent = LoyaltyPolicy.discountPercent(t);
        }
        long loyaltyDiscount = (long) (subtotal * loyaltyPercent);

        long totalDiscount = voucherDiscount + loyaltyDiscount;
        long total = Math.max(0L, subtotal - totalDiscount + shippingFee);

        String addressString = selectedAddress != null ? selectedAddress.displayLine() : "";

        return new CheckoutInfo(
                addressString,
                selectedAddress,
                paymentMethod,
                voucherCode,
                shippingLabel,
                subtotal,
                totalDiscount,
                shippingFee,
                total
        );
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    /** Lấy userId thống nhất: ưu tiên USRxxxxx từ SessionManager, fallback Firebase UID */
    @Nullable
    private String getEffectiveUserId(@NonNull Context ctx) {
        try {
            String custom = new SessionManager(ctx.getApplicationContext()).getUid();
            if (custom != null && !custom.isEmpty()) return custom;
        } catch (Throwable ignored) {}
        return (FirebaseAuth.getInstance().getCurrentUser() != null)
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;
    }
}
