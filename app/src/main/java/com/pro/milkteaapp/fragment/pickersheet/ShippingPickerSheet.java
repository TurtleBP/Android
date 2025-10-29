package com.pro.milkteaapp.fragment.pickersheet;

import android.os.Bundle;
import android.view.*;
import androidx.annotation.*;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.pro.milkteaapp.databinding.PickerShippingBinding;

public class ShippingPickerSheet extends BottomSheetDialogFragment {

    public interface Listener { void onPicked(String label, double fee); }
    private PickerShippingBinding b;
    private Listener listener;

    public void setListener(Listener l) { this.listener = l; }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle s) {
        b = PickerShippingBinding.inflate(inf, c, false);
        return b.getRoot();
    }

    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        b.btnFast.setOnClickListener(v1 -> pick("Nhanh (25.000đ)", 25_000));
        b.btnStandard.setOnClickListener(v1 -> pick("Tiêu chuẩn (15.000đ)", 15_000));
        b.btnSaver.setOnClickListener(v1 -> pick("Tiết kiệm (8.000đ)", 8_000));
    }

    private void pick(String label, double fee) { if (listener!=null) listener.onPicked(label, fee); dismiss(); }
    @Override public void onDestroyView(){ super.onDestroyView(); b=null; }
}
