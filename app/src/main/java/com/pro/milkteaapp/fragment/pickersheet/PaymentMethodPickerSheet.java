package com.pro.milkteaapp.fragment.pickersheet;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
// Đảm bảo bạn dùng đúng tên binding cho file 'picker_payment_method.xml'
import com.pro.milkteaapp.databinding.PickerPaymentMethodBinding;

public class PaymentMethodPickerSheet extends BottomSheetDialogFragment {

    public interface Listener {
        void onPicked(String method);
    }

    private PickerPaymentMethodBinding b;
    private Listener listener;

    private static final String ARG_IS_GOOGLE_PAY_READY = "is_google_pay_ready";

    /**
     * Hàm khởi tạo mới để truyền vào trạng thái Google Pay
     */
    public static PaymentMethodPickerSheet newInstance(boolean isGooglePayReady) {
        PaymentMethodPickerSheet fragment = new PaymentMethodPickerSheet();
        Bundle args = new Bundle();
        args.putBoolean(ARG_IS_GOOGLE_PAY_READY, isGooglePayReady);
        fragment.setArguments(args);
        return fragment;
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle s) {
        b = PickerPaymentMethodBinding.inflate(inf, c, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);

        boolean isGooglePayReady = false;
        if (getArguments() != null) {
            isGooglePayReady = getArguments().getBoolean(ARG_IS_GOOGLE_PAY_READY, false);
        }

        // 1. Set listener cho nút COD
        b.btnCOD.setOnClickListener(v1 -> pick("COD"));

        // 2. Chỉ hiển thị và set listener cho Google Pay NẾU nó sẵn sàng
        if (isGooglePayReady) {
            b.btnGooglePay.setVisibility(View.VISIBLE);
            b.btnGooglePay.setOnClickListener(v1 -> pick("Google Pay"));
        } else {
            b.btnGooglePay.setVisibility(View.GONE);
        }
    }

    private void pick(String m) {
        if (listener != null) {
            listener.onPicked(m);
        }
        dismiss();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}