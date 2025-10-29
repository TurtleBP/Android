package com.pro.milkteaapp.activity;

import android.annotation.SuppressLint;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.ListenerRegistration;
import com.pro.milkteaapp.adapter.AddressAdapter;
import com.pro.milkteaapp.fragment.AddEditAddressDialog;
import com.pro.milkteaapp.repository.AddressRepository;
import com.pro.milkteaapp.databinding.ActivityAddressBinding;
import com.pro.milkteaapp.models.Address;

import java.util.ArrayList;
import java.util.List;

public class AddressActivity extends AppCompatActivity
        implements AddressAdapter.Listener, AddEditAddressDialog.Listener {

    private ActivityAddressBinding binding;
    private final List<Address> data = new ArrayList<>();
    private AddressAdapter adapter;
    private final AddressRepository repo = new AddressRepository();

    // Realtime listener
    private ListenerRegistration addressesReg;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddressBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        MaterialToolbar tb = binding.toolbar;
        tb.setNavigationOnClickListener(v -> finish());

        binding.rvAddress.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AddressAdapter(data, this, false);
        binding.rvAddress.setAdapter(adapter);

        FloatingActionButton fab = binding.fabAdd;
        fab.setOnClickListener(v -> AddEditAddressDialog.newInstance(null)
                .show(getSupportFragmentManager(), "add_address"));
    }

    /** Đăng ký lắng nghe realtime khi Activity hiển thị */
    @SuppressLint("NotifyDataSetChanged")
    @Override
    protected void onStart() {
        super.onStart();
        addressesReg = repo.listenAll(addresses -> {
            data.clear();
            data.addAll(addresses);
            adapter.notifyDataSetChanged();
        }, e -> {
            // TODO: hiển thị lỗi nếu cần (Toast/Snackbar/log)
        });
    }

    /** Hủy lắng nghe để tránh rò rỉ khi Activity không còn ở foreground */
    @Override
    protected void onStop() {
        super.onStop();
        if (addressesReg != null) {
            addressesReg.remove();
            addressesReg = null;
        }
    }

    // =============== AddressAdapter.Listener ===============

    @Override
    public void onItemClick(int position) {
        if (position < 0 || position >= data.size()) return;
        AddEditAddressDialog.newInstance(data.get(position))
                .show(getSupportFragmentManager(), "edit_address");
    }

    @Override
    public void onSetDefault(int position) {
        if (position < 0 || position >= data.size()) return;
        Address target = data.get(position);
        repo.setDefault(target.getId(), () -> {
            // UI sẽ tự cập nhật qua realtime listener
        }, e -> {
            // TODO: show error
        });
    }

    @Override
    public void onEdit(int position) { onItemClick(position); }

    @Override
    public void onDelete(int position) {
        if (position < 0 || position >= data.size()) return;
        Address removed = data.get(position);
        repo.delete(removed.getId(), () -> {
            // UI sẽ tự cập nhật qua realtime listener
        }, e -> {
            // TODO: show error
        });
    }

    // =============== AddEditAddressDialog.Listener ===============

    @Override
    public void onAddressSaved(Address address, boolean isEdit) {
        // Dùng realtime nên KHÔNG cần cập nhật thủ công danh sách ở đây.
        // Để trống (no-op). Nếu muốn feedback, có thể show Toast tại đây.
    }
}
