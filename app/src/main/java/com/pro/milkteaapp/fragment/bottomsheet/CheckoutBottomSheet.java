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

// === IMPORT CỦA GOOGLE PAY ===
import com.google.android.gms.tasks.Task;
import com.google.android.gms.wallet.IsReadyToPayRequest;
import com.google.android.gms.wallet.PaymentsClient;
import com.google.android.gms.wallet.Wallet;
import com.google.android.gms.wallet.WalletConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Optional;
// =============================

import com.google.firebase.auth.FirebaseAuth;
import com.pro.milkteaapp.adapter.CheckoutItemAdapter;
import com.pro.milkteaapp.databinding.CheckoutBottomSheetBinding; // Đảm bảo tên file binding chính xác
import com.pro.milkteaapp.fragment.pickersheet.AddressPickerSheet;
import com.pro.milkteaapp.fragment.pickersheet.PaymentMethodPickerSheet;
import com.pro.milkteaapp.fragment.pickersheet.ShippingPickerSheet;
import com.pro.milkteaapp.fragment.pickersheet.VoucherListPickerSheet;
import com.pro.milkteaapp.models.Address;
import com.pro.milkteaapp.models.CartItem;
import com.pro.milkteaapp.models.CheckoutInfo;
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

    // === BIẾN MỚI CHO GOOGLE PAY ===
    private PaymentsClient paymentsClient;
    private boolean isGooglePayReady = false; // Lưu trạng thái Google Pay
    // =============================

    public void setOnCheckoutConfirmListener(OnCheckoutConfirmListener l) {
        this.listener = l;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup container, @Nullable Bundle s) {
        b = CheckoutBottomSheetBinding.inflate(inf, container, false);
        return b.getRoot();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);

        // === FIX LỖI: VÔ HIỆU HÓA HÀNG THANH TOÁN TẠM THỜI ===
        b.rowPayment.setEnabled(false);
        setText(b.tvPaymentValue, "Đang tải..."); // Hiển thị trạng thái loading
        // =================================================

        // === KHỞI TẠO GOOGLE PAY ===
        // Kiểm tra readiness ở đây, vì đây là màn hình chọn
        paymentsClient = createPaymentsClient();
        checkGooglePayReadiness();
        // ========================

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

        // === CLICK LISTENER CỦA ROW PAYMENT (giữ nguyên) ===
        b.rowPayment.setOnClickListener(v12 -> {
            // Mở picker và truyền trạng thái Google Pay vào
            // Lúc này, isGooglePayReady đã có giá trị đúng vì row đã được enable
            PaymentMethodPickerSheet sheet = PaymentMethodPickerSheet.newInstance(isGooglePayReady);
            sheet.setListener(pm -> {
                paymentMethod = pm;
                setText(b.tvPaymentValue, pm);
                updateSummaryUI(); // Cập nhật UI ngay khi chọn
            });
            sheet.show(getParentFragmentManager(), "PaymentPicker");
        });
        // ==========================================

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
            subtotal += it.getTotalPrice(); // Sử dụng getTotalPrice() cho nhất quán
        }
        return subtotal;
    }

    private void updateSummaryUI() {
        long subtotal = calcSubtotal();

        long orderDisc = 0L, shipDisc = 0L;
        if (selectedVoucher != null) {
            VoucherUtils.DiscountResult r = VoucherUtils.calc(selectedVoucher, subtotal, shippingFee);
            orderDisc = r.orderDiscount;
            shipDisc = r.shippingDiscount;
        }
        long discount = orderDisc + shipDisc;
        long finalShippingFee = Math.max(0L, shippingFee - shipDisc);
        long total = Math.max(0L, subtotal - orderDisc + finalShippingFee);

        b.tvSubtotal.setText(MoneyUtils.formatVnd(subtotal));
        b.tvDiscount.setText(String.format(Locale.getDefault(), "- %s", MoneyUtils.formatVnd(discount)));
        b.tvShipping.setText(MoneyUtils.formatVnd(finalShippingFee));
        b.tvFinal.setText(MoneyUtils.formatVnd(total));

        if (selectedAddress != null) setText(b.tvAddressValue, selectedAddress.displayLine());
        setText(b.tvVoucherValue, voucherCode.isEmpty() ? "Không áp dụng" : voucherCode);
        setText(b.tvShippingValue, shippingLabel);

        // CHỈ cập nhật text "Phương thức thanh toán" nếu nó KHÔNG đang tải
        if (b.rowPayment.isEnabled()) {
            setText(b.tvPaymentValue, paymentMethod);
        }
    }

    private CheckoutInfo buildInfo() {
        long subtotal = calcSubtotal();

        long orderDisc = 0L, shipDisc = 0L;
        if (selectedVoucher != null) {
            VoucherUtils.DiscountResult r = VoucherUtils.calc(selectedVoucher, subtotal, shippingFee);
            orderDisc = r.orderDiscount;
            shipDisc = r.shippingDiscount;
        }
        long discount = orderDisc + shipDisc;
        long finalShippingFee = Math.max(0L, shippingFee - shipDisc);
        long total = Math.max(0L, subtotal - orderDisc + finalShippingFee);

        String addressString = selectedAddress != null ? selectedAddress.displayLine() : "";

        return new CheckoutInfo(
                addressString,
                selectedAddress,
                paymentMethod,
                voucherCode,
                shippingLabel,
                subtotal,
                discount,
                finalShippingFee,
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

    // ======================================================
    // === CÁC HÀM CỦA GOOGLE PAY (ĐỂ KIỂM TRA READINESS) ===
    // ======================================================

    private PaymentsClient createPaymentsClient() {
        Wallet.WalletOptions walletOptions = new Wallet.WalletOptions.Builder()
                .setEnvironment(WalletConstants.ENVIRONMENT_TEST)
                .build();
        return Wallet.getPaymentsClient(requireActivity(), walletOptions);
    }

    /**
     * Sửa lại hàm checkGooglePayReadiness để kích hoạt lại row sau khi hoàn tất
     */
    private void checkGooglePayReadiness() {
        final Optional<JSONObject> isReadyToPayJson = getGooglePayIsReadyToPayRequest();
        if (!isReadyToPayJson.isPresent()) {
            // Nếu lỗi ngay lập tức, kích hoạt lại row
            if (b != null) {
                b.rowPayment.setEnabled(true);
                setText(b.tvPaymentValue, paymentMethod); // Hiển thị text "COD"
            }
            return;
        }
        IsReadyToPayRequest request = IsReadyToPayRequest.fromJson(isReadyToPayJson.get().toString());
        Task<Boolean> task = paymentsClient.isReadyToPay(request);

        // Chạy trên Main Thread của Activity
        task.addOnCompleteListener(requireActivity(), taskResult -> {
            try {
                if (taskResult.isSuccessful()) {
                    setGooglePayAvailable(true);
                } else {
                    setGooglePayAvailable(false);
                }
            } catch (Exception e) {
                setGooglePayAvailable(false);
            }

            // === FIX LỖI: KÍCH HOẠT LẠI ROW THANH TOÁN ===
            // Dù thành công hay thất bại, kích hoạt lại row
            if (b != null) { // Đảm bảo view chưa bị destroy
                b.rowPayment.setEnabled(true);
                // Cập nhật text, isGooglePayReady đã được set
                setText(b.tvPaymentValue, paymentMethod);
            }
            // ==========================================
        });
    }

    private void setGooglePayAvailable(boolean available) {
        this.isGooglePayReady = available;
    }

    // --- CÁC HÀM TIỆN ÍCH JSON (CHO READINESS) ---

    private static JSONObject getBaseRequest() throws JSONException {
        return new JSONObject()
                .put("apiVersion", 2)
                .put("apiVersionMinor", 0);
    }

    private static JSONObject getGatewayTokenizationSpecification() throws JSONException {
        return new JSONObject()
                .put("type", "PAYMENT_GATEWAY")
                .put("parameters", new JSONObject()
                        .put("gateway", "example")
                        .put("gatewayMerchantId", "exampleGatewayMerchantId"));
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

    public static Optional<JSONObject> getGooglePayIsReadyToPayRequest() {
        try {
            JSONObject isReadyToPayRequest = getBaseRequest();
            isReadyToPayRequest.put("allowedPaymentMethods", getAllowedPaymentMethods());
            return Optional.of(isReadyToPayRequest);
        } catch (JSONException e) {
            return Optional.empty();
        }
    }
}