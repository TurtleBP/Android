package com.pro.milkteaapp.fragment.bottomsheet;

import android.content.Context;
import android.os.Bundle;
import android.view.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.adapter.AddressAdapter;
import com.pro.milkteaapp.repository.AddressRepository;
import com.pro.milkteaapp.models.Address;

import java.util.ArrayList;
import java.util.List;

public class AddressPickerBottomSheet extends BottomSheetDialogFragment implements AddressAdapter.Listener {

    public interface Listener { void onAddressPicked(Address address); }

    private Listener listener;
    private final List<Address> data = new ArrayList<>();
    private AddressAdapter adapter;
    private int selected = RecyclerView.NO_POSITION;
    private final AddressRepository repo = new AddressRepository();

    @Override public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof Listener) listener = (Listener) context;
        else if (getParentFragment() instanceof Listener) listener = (Listener) getParentFragment();
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle s) {
        View v = inflater.inflate(R.layout.bottomsheet_address_picker, container, false);
        RecyclerView rv = v.findViewById(R.id.rvAddress);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new AddressAdapter(data, this, true);
        rv.setAdapter(adapter);

        MaterialButton btn = v.findViewById(R.id.btnConfirm);
        btn.setOnClickListener(view -> {
            if (selected != RecyclerView.NO_POSITION && listener != null) {
                listener.onAddressPicked(data.get(selected));
                dismiss();
            }
        });

        loadAddresses();
        return v;
    }

    private void loadAddresses() {
        repo.fetchAll(addresses -> {
            data.clear();
            data.addAll(addresses);
            adapter.notifyDataSetChanged();
            int def = findDefaultIndex();
            if (def >= 0) {
                selected = def;
                adapter.setSelectedIndex(def);
                adapter.notifyItemChanged(def);
            }
        }, e -> { /* TODO show error */ });
    }

    private int findDefaultIndex() {
        for (int i = 0; i < data.size(); i++) if (data.get(i).isDefault()) return i;
        return -1;
    }

    // Adapter callbacks
    @Override public void onItemClick(int position) { selected = position; }
    @Override public void onSetDefault(int position) { /* disabled in picker */ }
    @Override public void onEdit(int position) { /* disabled in picker */ }
    @Override public void onDelete(int position) { /* disabled in picker */ }
}
