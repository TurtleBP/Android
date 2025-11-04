package com.pro.milkteaapp.fragment.bottomsheet;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.databinding.BottomSheetBankTransferBinding;
import com.pro.milkteaapp.utils.MoneyUtils;

import java.util.HashMap;
import java.util.Map;

public class BankTransferSheet extends BottomSheetDialogFragment {

    public interface OnPaymentConfirmedListener {
        void onPaymentConfirmed();
    }

    private static final String ARG_TOTAL_AMOUNT = "arg_total_amount";
    private static final String ARG_CONTENT_MEMO = "arg_content_memo";

    private BottomSheetBankTransferBinding b;
    private OnPaymentConfirmedListener listener;
    private double totalAmount = 0;
    private String contentMemo = "";

    private final Map<String, String> fakeBankData = new HashMap<>();

    public static BankTransferSheet newInstance(double totalAmount, String contentMemo) {
        BankTransferSheet f = new BankTransferSheet();
        Bundle args = new Bundle();
        args.putDouble(ARG_TOTAL_AMOUNT, totalAmount);
        args.putString(ARG_CONTENT_MEMO, contentMemo);
        f.setArguments(args);
        return f;
    }

    public void setListener(OnPaymentConfirmedListener l) {
        this.listener = l;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            totalAmount = getArguments().getDouble(ARG_TOTAL_AMOUNT, 0);
            contentMemo = getArguments().getString(ARG_CONTENT_MEMO, "[MaDonHang]");
        }

        // Dữ liệu ngân hàng giả
        fakeBankData.put("0123456789", "NGUYEN VAN A");
        fakeBankData.put("9876543210", "TRAN THI B");
        fakeBankData.put("11112222", "CONG TY TNHH MILKA");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup container, @Nullable Bundle s) {
        b = BottomSheetBankTransferBinding.inflate(inf, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);

        Context context = getContext();
        if (context == null) return;

        // Hiển thị tiền và nội dung
        b.tvAmount.setText(MoneyUtils.formatVnd((long) totalAmount));
        b.tvContent.setText(contentMemo);

        b.ivQrCode.setImageResource(R.drawable.logo_app);
        b.tvBankName.setText(getString(R.string.bank_name_value));

        // === LOGIC MỚI: Vô hiệu hóa nút và đặt gợi ý ===
        b.btnConfirmPayment.setEnabled(false); // Vô hiệu hóa nút
        b.tvAccountHolderName.setText("Vui lòng nhập STK hợp lệ"); // Hướng dẫn
        b.tvAccountHolderName.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray));
        // =============================================

        // Logic giả lập STK
        b.etAccountNumber.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence c, int i, int i1, int i2) {}

            @Override
            public void onTextChanged(CharSequence c, int i, int i1, int i2) {}

            @Override
            public void afterTextChanged(Editable editable) {
                Context safeContext = getContext();
                if (safeContext == null) return;

                String input = editable.toString().trim();

                if (fakeBankData.containsKey(input)) {
                    // 1. Hợp lệ
                    b.tvAccountHolderName.setText(fakeBankData.get(input));
                    b.tvAccountHolderName.setTextColor(ContextCompat.getColor(safeContext, android.R.color.black));
                    b.btnConfirmPayment.setEnabled(true); // Kích hoạt nút
                    b.etAccountNumber.setError(null); // Xóa lỗi (nếu có)
                } else if (input.isEmpty()) {
                    // 2. Trống
                    b.tvAccountHolderName.setText("Vui lòng nhập STK hợp lệ");
                    b.tvAccountHolderName.setTextColor(ContextCompat.getColor(safeContext, android.R.color.darker_gray));
                    b.btnConfirmPayment.setEnabled(false); // Vô hiệu hóa nút
                    b.etAccountNumber.setError(null);
                } else {
                    // 3. Sai
                    b.tvAccountHolderName.setText("Không tìm thấy chủ tài khoản");
                    b.tvAccountHolderName.setTextColor(ContextCompat.getColor(safeContext, android.R.color.holo_red_dark));
                    b.btnConfirmPayment.setEnabled(false); // Vô hiệu hóa nút
                    b.etAccountNumber.setError("Số tài khoản không hợp lệ"); // Hiển thị lỗi
                }
            }
        });

        // Xử lý nút xác nhận (sẽ không chạy nếu nút bị vô hiệu hóa)
        b.btnConfirmPayment.setOnClickListener(v1 -> {
            if (listener != null) {
                listener.onPaymentConfirmed();
            }
            dismiss();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}

