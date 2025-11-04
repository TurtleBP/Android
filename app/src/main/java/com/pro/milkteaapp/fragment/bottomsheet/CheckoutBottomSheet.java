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
import com.pro.milkteaapp.R;
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
    // Thêm hằng số cho phương thức thanh toán
    private static final String PM_BANK_TRANSFER = PaymentMethodPickerSheet.METHOD_BANK_TRANSFER;
    private static final String PM_COD = PaymentMethodPickerSheet.METHOD_COD;

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
    private String paymentMethod = PM_COD; // Sửa default thành hằng số
    @Nullable private Voucher selectedVoucher = null;
    private String voucherCode = "";
    private String shippingLabel = "Tiêu chuẩn (15.000đ)";
    private long shippingFee = 15_000L;

    @Nullable private User currentUser;

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

          if (getArguments() != null) {
            Serializable ser = getArguments().getSerializable(ARG_ITEMS);
            if (ser instanceof ArrayList<?>) {
                for (Object o : (ArrayList<?>) ser) {
                    if (o instanceof CartItem) items.add((CartItem) o);
                }
            }
        }

        b.rvItems.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.rvItems.setAdapter(new CheckoutItemAdapter(requireContext(), items));
        loadUserAndRender();
        updateSummaryUI();

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
                updateSummaryUI(); // Chỉ cần gọi update UI
            });
            sheet.show(getParentFragmentManager(), "PaymentPicker");
        });

        // CẬP NHẬT: Chọn voucher từ danh sách hợp lệ
        b.rowVoucher.setOnClickListener(v13 -> {
            Bundle args = new Bundle();
            args.putLong("subtotal", calcSubtotal());
            args.putString("payment", paymentMethod); // <-- Đã truyền PTTT
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

        // =================================================================
        // CẬP NHẬT LOGIC NÚT XÁC NHẬN (ĐƠN GIẢN HÓA)
        // =================================================================
        b.btnConfirm.setOnClickListener(v15 -> {
            if (selectedAddress == null) {
                setText(b.tvAddressValue, "Vui lòng chọn địa chỉ");
                return;
            }

            // Luôn xây dựng thông tin checkout
            final CheckoutInfo info = buildInfo();

            // Gửi thông tin về CartFragment ngay lập tức
            // CartFragment sẽ quyết định làm gì tiếp theo (hiện trang Bank hay lưu COD)
            if (listener != null) {
                listener.onCheckoutConfirmed(info);
            }
            dismiss();
        });
    }
    // (Giữ nguyên các hàm private: setText, calcSubtotal, loadUserAndRender)
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
        String uid = (FirebaseAuth.getInstance().getCurrentUser() != null)
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) {
            updateSummaryUI(); return;
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


    // CẬP NHẬT: Sửa lại UI cho tvPaymentValue
    private void updateSummaryUI() {
        long subtotal = calcSubtotal();

        // (Giữ nguyên phần tính toán discount)
        long orderDisc = 0L, shipDisc = 0L;
        if (selectedVoucher != null) {
            VoucherUtils.DiscountResult r = VoucherUtils.calc(selectedVoucher, subtotal, shippingFee);
            orderDisc = r.orderDiscount;
            shipDisc = r.shippingDiscount;
        }
        long voucherDiscount = orderDisc + shipDisc;
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

        // (Giữ nguyên phần set text số tiền)
        b.tvSubtotal.setText(MoneyUtils.formatVnd(subtotal));
        b.tvDiscount.setText(String.format(Locale.getDefault(), "- %s", MoneyUtils.formatVnd(voucherDiscount)));
        b.tvLoyaltyDiscount.setText(String.format(Locale.getDefault(), "- %s", MoneyUtils.formatVnd(loyaltyDiscount)));
        b.tvShipping.setText(MoneyUtils.formatVnd(shippingFee));
        b.tvFinal.setText(MoneyUtils.formatVnd(total));

        // (Giữ nguyên phần set text các lựa chọn)
        if (selectedAddress != null) setText(b.tvAddressValue, selectedAddress.displayLine());
        setText(b.tvVoucherValue, voucherCode.isEmpty() ? "Không áp dụng" : voucherCode);
        setText(b.tvShippingValue, shippingLabel);

        // Cập nhật text thanh toán cho đúng
        if (PM_BANK_TRANSFER.equals(paymentMethod)) {
            setText(b.tvPaymentValue, "Chuyển khoản NH");
        } else if (PM_COD.equals(paymentMethod)) {
            setText(b.tvPaymentValue, "COD");
        } else {
            setText(b.tvPaymentValue, paymentMethod);
        }
    }

    // (Giữ nguyên hàm buildInfo)
    private CheckoutInfo buildInfo() {
        long subtotal = calcSubtotal();

        long orderDisc = 0L, shipDisc = 0L;
        if (selectedVoucher != null) {
            VoucherUtils.DiscountResult r = VoucherUtils.calc(selectedVoucher, subtotal, shippingFee);
            orderDisc = r.orderDiscount;
            shipDisc = r.shippingDiscount;
        }
        long voucherDiscount = orderDisc + shipDisc;

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

    // (Giữ nguyên onDestroyView và safe)
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}