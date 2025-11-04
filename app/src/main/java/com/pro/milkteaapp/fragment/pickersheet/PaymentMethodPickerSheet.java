package com.pro.milkteaapp.fragment.pickersheet;

import android.os.Bundle;
import android.view.*;
import androidx.annotation.*;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.pro.milkteaapp.R; // Thêm import R
import com.pro.milkteaapp.databinding.PickerPaymentMethodBinding;

public class PaymentMethodPickerSheet extends BottomSheetDialogFragment {

    // Định nghĩa hằng số cho các phương thức thanh toán
    public static final String METHOD_COD = "COD";
    public static final String METHOD_VNPAY = "VNPAY";
    public static final String METHOD_ZALOPAY = "ZALOPAY";
    public static final String METHOD_BANK_TRANSFER = "BANK_TRANSFER";


    public interface Listener { void onPicked(String method); }
    private PickerPaymentMethodBinding b;
    private Listener listener;

    public void setListener(Listener l) { this.listener = l; }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle s) {
        b = PickerPaymentMethodBinding.inflate(inf, c, false);
        return b.getRoot();
    }

    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        // Thêm sự kiện click
        b.btnCOD.setOnClickListener(v1 -> pick(METHOD_COD));
        b.btnBankTransfer.setOnClickListener(v1 -> pick(METHOD_BANK_TRANSFER));
        b.btnVNPAY.setOnClickListener(v1 -> pick(METHOD_VNPAY));
        b.btnZaloPay.setOnClickListener(v1 -> pick(METHOD_ZALOPAY));
    }

    private void pick(String m) { if (listener!=null) listener.onPicked(m); dismiss(); }
    @Override public void onDestroyView(){ super.onDestroyView(); b=null; }
}