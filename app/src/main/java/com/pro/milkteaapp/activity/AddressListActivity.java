package com.pro.milkteaapp.activity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.firestore.FirebaseFirestore;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.adapters.AddressAdapter;
import com.pro.milkteaapp.models.Address;

import java.util.ArrayList;
import java.util.List;

public class AddressListActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private ListenerRegistration listener;

    private RecyclerView recyclerView;
    private MaterialButton btnAddAddress;

    private AddressAdapter adapter;
    private final List<Address> addresses = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_address_list);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        // Nếu layout chưa có navigationIcon, bật dòng dưới:
        // toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        recyclerView = findViewById(R.id.rcv_address);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // KHỞI TẠO ADAPTER: dùng anonymous class vì Listener có 4 phương thức
        adapter = new AddressAdapter(new AddressAdapter.Listener() {
            @Override public void onEdit(Address a)      { editAddress(a); }

            @Override public void onDelete(Address a)    { deleteAddress(a); }

            @Override public void onSetDefault(Address a){ setDefaultAddress(a); }

            @Override public void onSelect(Address a)    {
                // Nếu cần chọn địa chỉ cho màn Checkout, xử lý tại đây
                Toast.makeText(AddressListActivity.this, "Đã chọn địa chỉ", Toast.LENGTH_SHORT).show();
            }
        });
        recyclerView.setAdapter(adapter);

        btnAddAddress = findViewById(R.id.btn_add_address);

        db = FirebaseFirestore.getInstance();

        btnAddAddress.setOnClickListener(v -> {
            startActivity(new Intent(this, AddEditAddressActivity.class)
                    .putExtra("mode", "add"));
        });
        loadAddresses();
    }

    private void loadAddresses() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            Toast.makeText(this, "Bạn chưa đăng nhập", Toast.LENGTH_SHORT).show();
            return;
        }

        if (listener != null) listener.remove();
        listener = db.collection("users").document(uid)
                .collection("addresses")
                .orderBy("isDefault", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        Toast.makeText(this, "Lỗi tải địa chỉ: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (snap == null) {
                        adapter.submit(new ArrayList<>());
                        return;
                    }

                    List<Address> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        Address a = doc.toObject(Address.class);
                        if (a != null) {
                            a.setId(doc.getId()); // quan trọng để stableIds & DiffUtil
                            list.add(a);
                        }
                    }
                    adapter.submit(list);
                });
    }

    private void addDummyAddress() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            Toast.makeText(this, "Bạn chưa đăng nhập", Toast.LENGTH_SHORT).show();
            return;
        }

        // Có thể mở màn hình Add Address chuẩn; tạm thời thêm mẫu để test UI
        Address address = new Address();
        address.setFullName("Hoàng Anh");
        address.setPhone("0909xxxxxx");
        address.setLine1("123 Nguyễn Văn Linh");
        address.setDistrict("Quận 7");
        address.setCity("TP.HCM");
        address.setDefault(false); // giá trị mặc định; Firestore sẽ sắp xếp theo isDefault

        db.collection("users").document(uid)
                .collection("addresses")
                .add(address)
                .addOnSuccessListener(ref -> Toast.makeText(this, "Đã thêm địa chỉ mới", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    // ======== Hành động ========

    private void editAddress(@NonNull Address a) {
        // TODO: Mở màn hình Edit hoặc dialog chỉnh sửa địa chỉ
        Toast.makeText(this, "Sửa địa chỉ: " + safe(a.getFullName()), Toast.LENGTH_SHORT).show();
    }

    private void deleteAddress(@NonNull Address a) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null || a.getId() == null) return;

        db.collection("users").document(uid)
                .collection("addresses").document(a.getId())
                .delete()
                .addOnSuccessListener(v -> Toast.makeText(this, "Đã xoá địa chỉ", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(err -> Toast.makeText(this, "Xoá thất bại: " + err.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void setDefaultAddress(@NonNull Address target) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null || target.getId() == null) return;

        // Lấy danh sách hiện có từ adapter để cập nhật batch
        // (Có thể bạn muốn giữ một biến addresses nếu cần)
        // Ở đây, mình đọc từ UI hiện tại bằng cách gọi load lại snapshot thì hơi thừa,
        // nên thực hiện batch trên tất cả doc đang hiển thị:
        WriteBatch batch = db.batch();

        // 1) Bỏ cờ mặc định tất cả
        RecyclerView.Adapter rvAdapter = recyclerView.getAdapter();
        if (rvAdapter instanceof AddressAdapter) {
            // Không có getter public cho data -> đơn giản: query lại & set theo điều kiện
            // Cách tối ưu: bạn có thể giữ biến 'addresses' đồng bộ với adapter.submit(list)
        }

        // Giải pháp an toàn: query toàn bộ và set isDefault theo id
        db.collection("users").document(uid)
                .collection("addresses")
                .get()
                .addOnSuccessListener(qs -> {
                    WriteBatch b = db.batch();
                    for (DocumentSnapshot d : qs.getDocuments()) {
                        DocumentReference dr = d.getReference();
                        boolean toDefault = d.getId().equals(target.getId());
                        b.update(dr, "isDefault", toDefault);
                    }
                    b.commit()
                            .addOnSuccessListener(v -> Toast.makeText(this, "Đã đặt làm địa chỉ mặc định", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(err -> Toast.makeText(this, "Không thể đặt mặc định: " + err.getMessage(), Toast.LENGTH_SHORT).show());
                })
                .addOnFailureListener(err -> Toast.makeText(this, "Lỗi khi đọc danh sách: " + err.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private static String safe(String s) { return s == null ? "" : s.trim(); }

    @Override
    protected void onDestroy() {
        if (listener != null) listener.remove();
        super.onDestroy();
    }
}
