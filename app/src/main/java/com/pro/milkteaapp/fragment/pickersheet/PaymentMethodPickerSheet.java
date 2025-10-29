package com.pro.milkteaapp.fragment.pickersheet;

import android.os.Bundle;
import android.view.*;
import androidx.annotation.*;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.pro.milkteaapp.databinding.PickerPaymentMethodBinding;

public class PaymentMethodPickerSheet extends BottomSheetDialogFragment {

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
        b.btnCOD.setOnClickListener(v1 -> pick("COD"));
        b.btnVNPAY.setOnClickListener(v1 -> pick("VNPAY"));
        b.btnMomo.setOnClickListener(v1 -> pick("Momo"));
    }

    private void pick(String m) { if (listener!=null) listener.onPicked(m); dismiss(); }
    @Override public void onDestroyView(){ super.onDestroyView(); b=null; }
}
