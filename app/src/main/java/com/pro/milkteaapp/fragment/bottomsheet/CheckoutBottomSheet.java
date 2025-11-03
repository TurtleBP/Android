package com.pro.milkteaapp.fragment.bottomsheet;

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
import com.google.firebase.firestore.FirebaseFirestore;
import com.pro.milkteaapp.adapter.CheckoutItemAdapter;
import com.pro.milkteaapp.databinding.CheckoutBottomSheetBinding;
import com.pro.milkteaapp.fragment.pickersheet.AddressPickerSheet;
import com.pro.milkteaapp.fragment.pickersheet.PaymentMethodPickerSheet;
import com.pro.milkteaapp.fragment.pickersheet.ShippingPickerSheet;
import com.pro.milkteaapp.fragment.pickersheet.VoucherListPickerSheet;
import com.pro.milkteaapp.models.Address;
import com.pro.milkteaapp.models.CartItem;
import com.pro.milkteaapp.models.CheckoutInfo;
import com.pro.milkteaapp.models.User;
import com.pro.milkteaapp.models.Voucher;
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

    private CheckoutBottomSheetBinding b;
    private OnCheckoutConfirmListener listener;
    private final List<CartItem> items = new ArrayList<>();

    // State
    private Address selectedAddress;
    private String paymentMethod = "COD";
    private Voucher selectedVoucher = null;
    private String voucherCode = "";
    private String shippingLabel = "Tiêu chuẩn (15.000đ)";
    private long shippingFee = 15_000L;

    private User currentUser;

    public void setOnCheckoutConfirmListener(OnCheckoutConfirmListener l) {
        this.listener = l;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup container, @Nullable Bundle s) {
        b = CheckoutBottomSheetBinding.inflate(inf, container, false);
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

        // Tải thông tin User để tính giảm giá
        loadUserAndRender();

        // Render ban đầu
        updateSummaryUI();

        // Chọn địa chỉ
        b.rowAddress.setOnClickListener(v1 -> {
            AddressPickerSheet sheet = new AddressPickerSheet();
            sheet.setListener(addr -> {
                selectedAddress = addr;
                setText(b.tvAddressValue, addr != null ? addr.displayLine() : "");
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
            String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                    ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
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

    private void setText(TextView tv, String txt) {
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
        String uid = (FirebaseAuth.getInstance().getCurrentUser() != null)
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

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

        long orderDisc = 0L, shipDisc = 0L;
        if (selectedVoucher != null) {
            VoucherUtils.DiscountResult r = VoucherUtils.calc(selectedVoucher, subtotal, shippingFee);
            orderDisc = r.orderDiscount;
            shipDisc = r.shippingDiscount;
        }
        long voucherDiscount = orderDisc + shipDisc;

        // === Giảm giá thành viên theo yêu cầu mới ===
        double loyaltyPercent = 0.0;
        if (currentUser != null && currentUser.getLoyaltyTier() != null) {
            loyaltyPercent = switch (currentUser.getLoyaltyTier()) {
                case "Vàng" -> 0.20; // 20%
                case "Bạc" -> 0.10; // 10%
                case "Đồng" -> 0.05; // 5%
                default -> 0.0; // Chưa xếp hạng
            };
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
        long totalDiscount = getTotalDiscount(orderDisc, shipDisc, subtotal);
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

    private long getTotalDiscount(long orderDisc, long shipDisc, long subtotal) {
        long voucherDiscount = orderDisc + shipDisc;

        // === Giảm giá thành viên theo yêu cầu mới ===
        double loyaltyPercent = 0.0;
        if (currentUser != null && currentUser.getLoyaltyTier() != null) {
            loyaltyPercent = switch (currentUser.getLoyaltyTier()) {
                case "Vàng" -> 0.20; // 20%
                case "Bạc" -> 0.10; // 10%
                case "Đồng" -> 0.05; // 5%
                default -> 0.0; // Chưa xếp hạng
            };
        }
        long loyaltyDiscount = (long) (subtotal * loyaltyPercent);

        return voucherDiscount + loyaltyDiscount;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
