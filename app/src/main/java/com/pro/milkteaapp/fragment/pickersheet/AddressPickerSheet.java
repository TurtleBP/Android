package com.pro.milkteaapp.fragment.pickersheet;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.ListenerRegistration;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.adapter.AddressAdapter;
import com.pro.milkteaapp.fragment.AddEditAddressDialog;
import com.pro.milkteaapp.models.Address;
import com.pro.milkteaapp.repository.AddressRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * BottomSheet hiển thị LIST địa chỉ từ Firestore để PICK.
 * - Layout: res/layout/bottomsheet_address_picker.xml
 * - Dùng AddressAdapter ở pickerMode=true (highlight item chọn)
 * - Realtime: listenAll() → tự cập nhật list khi add/edit/delete
 */
public class AddressPickerSheet extends BottomSheetDialogFragment implements AddressAdapter.Listener {

    public interface Listener {
        void onAddressPicked(Address address);
    }

    private Listener listener;
    public void setListener(Listener l) { this.listener = l; }

    private RecyclerView rv;
    private AddressAdapter adapter;
    private final List<Address> data = new ArrayList<>();
    private int selected = RecyclerView.NO_POSITION;

    private final AddressRepository repo = new AddressRepository();
    private ListenerRegistration reg;

    @Override
    public void onAttach(@NonNull Context ctx) {
        super.onAttach(ctx);
        if (getParentFragment() instanceof Listener) listener = (Listener) getParentFragment();
        else if (ctx instanceof Listener) listener = (Listener) ctx;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup container, @Nullable Bundle s) {
        View v = inf.inflate(R.layout.bottomsheet_address_picker, container, false);
        rv = v.findViewById(R.id.rvAddress);
        MaterialButton btnConfirm = v.findViewById(R.id.btnConfirm);
        MaterialButton btnAddNew = v.findViewById(R.id.btnAddNew);

        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new AddressAdapter(data, this, /*pickerMode=*/true);
        rv.setAdapter(adapter);

        btnConfirm.setOnClickListener(view -> {
            if (selected != RecyclerView.NO_POSITION && listener != null) {
                listener.onAddressPicked(data.get(selected));
                dismiss();
            }
        });

        btnAddNew.setOnClickListener(view -> {
            // Mở dialog thêm địa chỉ → lưu Firestore → realtime sẽ tự load lại
            AddEditAddressDialog.newInstance(null)
                    .show(getParentFragmentManager(), "add_address_from_picker");
        });

        return v;
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onStart() {
        super.onStart();
        // Lắng nghe realtime & ưu tiên địa chỉ mặc định
        reg = repo.listenAll(addresses -> {
            data.clear();
            data.addAll(addresses);
            adapter.setSelectedIndex(selected);
            adapter.notifyDataSetChanged();

            // Auto chọn địa chỉ mặc định nếu chưa chọn
            if (selected == RecyclerView.NO_POSITION) {
                int def = findDefaultIndex();
                if (def >= 0) {
                    selected = def;
                    adapter.setSelectedIndex(def);
                    adapter.notifyItemChanged(def);
                    rv.scrollToPosition(def);
                }
            } else if (selected >= data.size()) {
                selected = RecyclerView.NO_POSITION;
                adapter.setSelectedIndex(RecyclerView.NO_POSITION);
            }
        }, e -> {
            // TODO: show error nếu cần (Toast/Log)
        });
    }

    @Override
    public void onStop() {
        super.onStop();
        if (reg != null) { reg.remove(); reg = null; }
    }

    private int findDefaultIndex() {
        for (int i = 0; i < data.size(); i++) if (data.get(i).isDefault()) return i;
        return -1;
    }

    // ===== AddressAdapter.Listener (ở picker mode, chỉ dùng onItemClick) =====
    @Override public void onItemClick(int position) {
        selected = position;
        adapter.setSelectedIndex(position);
    }
    @Override public void onSetDefault(int position) { /* hidden in picker */ }
    @Override public void onEdit(int position) { /* hidden in picker */ }
    @Override public void onDelete(int position) { /* hidden in picker */ }
}
