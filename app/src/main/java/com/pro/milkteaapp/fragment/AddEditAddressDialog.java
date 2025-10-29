package com.pro.milkteaapp.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.*;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.button.MaterialButton;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.repository.AddressRepository;
import com.pro.milkteaapp.models.Address;

public class AddEditAddressDialog extends DialogFragment {

    public interface Listener { void onAddressSaved(Address address, boolean isEdit); }

    private static final String ARG_ADDRESS = "arg_address";

    public static AddEditAddressDialog newInstance(@Nullable Address a) {
        AddEditAddressDialog d = new AddEditAddressDialog();
        Bundle b = new Bundle();
        b.putParcelable(ARG_ADDRESS, a);
        d.setArguments(b);
        return d;
    }

    @NonNull @Override
    public Dialog onCreateDialog(@Nullable Bundle s) {
        View v = LayoutInflater.from(requireContext()).inflate(R.layout._dialog_address_editor, null, false);

        EditText edtName = v.findViewById(R.id.edtName);
        EditText edtPhone = v.findViewById(R.id.edtPhone);
        EditText edtLine1 = v.findViewById(R.id.edtLine1);
        EditText edtLine2 = v.findViewById(R.id.edtLine2);
        EditText edtCity = v.findViewById(R.id.edtCity);
        EditText edtProvince = v.findViewById(R.id.edtProvince);
        EditText edtPostal = v.findViewById(R.id.edtPostal);
        MaterialButton btnSave = v.findViewById(R.id.btnSave);

        Address editing = getArguments() != null ? getArguments().getParcelable(ARG_ADDRESS) : null;
        if (editing != null) {
            edtName.setText(editing.getFullName());
            edtPhone.setText(editing.getPhone());
            edtLine1.setText(editing.getLine1());
            edtLine2.setText(editing.getLine2());
            edtCity.setText(editing.getCity());
            edtProvince.setText(editing.getProvince());
            edtPostal.setText(editing.getPostalCode());
        }

        AddressRepository repo = new AddressRepository();

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(editing == null ? R.string.add_address : R.string.edit_address)
                .setView(v)
                .setNegativeButton(R.string.cancel, null)
                .create();

        btnSave.setOnClickListener(view -> {
            String name = val(edtName), phone = val(edtPhone), line1 = val(edtLine1),
                    city = val(edtCity), province = val(edtProvince);

            if (TextUtils.isEmpty(name) || TextUtils.isEmpty(phone) ||
                    TextUtils.isEmpty(line1) || TextUtils.isEmpty(city) || TextUtils.isEmpty(province)) {
                // TODO: setError cho từng ô nếu cần
                return;
            }

            Address out = editing != null ? editing : new Address();
            // ❌ KHÔNG set UUID thủ công ở đây — để repo quyết định add/update
            out.setFullName(name);
            out.setPhone(phone);
            out.setLine1(line1);
            out.setLine2(val(edtLine2));
            out.setCity(city);
            out.setProvince(province);
            out.setPostalCode(val(edtPostal));

            btnSave.setEnabled(false);
            repo.addOrUpdate(out, saved -> {
                if (getActivity() instanceof Listener) ((Listener) getActivity()).onAddressSaved(saved, editing != null);
                else if (getParentFragment() instanceof Listener) ((Listener) getParentFragment()).onAddressSaved(saved, editing != null);
                dismiss();
            }, e -> { btnSave.setEnabled(true); /* TODO: show error */ });
        });

        return dialog;
    }

    private static String val(EditText e) { return e.getText() != null ? e.getText().toString().trim() : ""; }
}
