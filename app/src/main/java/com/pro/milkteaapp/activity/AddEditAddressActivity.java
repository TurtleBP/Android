package com.pro.milkteaapp.activity;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.models.Address;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class AddEditAddressActivity extends AppCompatActivity {

    private FirebaseFirestore db;

    // KHỚP VỚI _dialog_address_editor.xml: EditText + CheckBox
    private EditText edtFullName, edtPhone, edtLine1, edtLine2, edtDistrict, edtCity, edtNote;
    private CheckBox cbDefault;
    private MaterialButton btnSave;

    private Address existingAddress; // chế độ sửa
    private boolean isEditMode = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout._dialog_address_editor); // layout có toolbar + nút Lưu + include form

        db = FirebaseFirestore.getInstance();
        initViews();
        setupToolbar();
        checkEditMode();
        setupSaveButton();
    }

    private void initViews() {
        // Ánh xạ đúng kiểu
        edtFullName = findViewById(R.id.edtFullName);
        edtPhone    = findViewById(R.id.edtPhone);
        edtLine1    = findViewById(R.id.edtLine1);
        edtLine2    = findViewById(R.id.edtLine2);
        edtDistrict = findViewById(R.id.edtDistrict);
        edtCity     = findViewById(R.id.edtCity);
        edtNote     = findViewById(R.id.edtNote);
        cbDefault   = findViewById(R.id.cbDefault);

        btnSave     = findViewById(R.id.btnSave);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar); // dùng NoActionBar theme để tránh đè đôi thanh
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
    }

    private void checkEditMode() {
        // Nhận Address từ Intent (yêu cầu Address implements Serializable hoặc Parcelable)
        Object extra = getIntent().getSerializableExtra("address");
        if (extra instanceof Address) {
            existingAddress = (Address) extra;
        }
        if (existingAddress != null) {
            isEditMode = true;
            MaterialToolbar toolbar = findViewById(R.id.toolbar);
            toolbar.setTitle(getString(R.string.edit_address));
            populateAddressData();
        }
    }

    private void populateAddressData() {
        edtFullName.setText(safe(existingAddress.getFullName()));
        edtPhone.setText(safe(existingAddress.getPhone()));
        edtLine1.setText(safe(existingAddress.getLine1()));
        edtLine2.setText(safe(existingAddress.getLine2()));
        edtDistrict.setText(safe(existingAddress.getDistrict()));
        edtCity.setText(safe(existingAddress.getCity()));
        edtNote.setText(safe(existingAddress.getNote()));
        cbDefault.setChecked(existingAddress.isDefault());
    }

    // Ở cuối class, sau hàm toast():


    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }


    private void setupSaveButton() {
        btnSave.setOnClickListener(v -> saveAddress());
    }

    private void saveAddress() {
        if (!validateForm()) return;

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            toast("Vui lòng đăng nhập lại");
            return;
        }

        Address address = createAddressFromForm();

        if (isEditMode && existingAddress != null && existingAddress.getId() != null) {
            updateAddress(uid, address);
        } else {
            addNewAddress(uid, address);
        }
    }

    private Address createAddressFromForm() {
        Address a = new Address();
        a.setFullName(Objects.requireNonNull(edtFullName.getText()).toString().trim());
        a.setPhone(Objects.requireNonNull(edtPhone.getText()).toString().trim());
        a.setLine1(Objects.requireNonNull(edtLine1.getText()).toString().trim());
        a.setLine2(Objects.requireNonNull(edtLine2.getText()).toString().trim());
        a.setDistrict(Objects.requireNonNull(edtDistrict.getText()).toString().trim());
        a.setCity(Objects.requireNonNull(edtCity.getText()).toString().trim());
        a.setNote(Objects.requireNonNull(edtNote.getText()).toString().trim());
        a.setDefault(cbDefault.isChecked());
        if (isEditMode && existingAddress != null) {
            a.setId(existingAddress.getId());
        }
        return a;
    }

    private boolean validateForm() {
        if (TextUtils.isEmpty(s(edtFullName))) { edtFullName.setError("Vui lòng nhập họ tên"); return false; }
        if (TextUtils.isEmpty(s(edtPhone)))    { edtPhone.setError("Vui lòng nhập số điện thoại"); return false; }
        if (TextUtils.isEmpty(s(edtLine1)))    { edtLine1.setError("Vui lòng nhập địa chỉ"); return false; }
        if (TextUtils.isEmpty(s(edtDistrict))) { edtDistrict.setError("Vui lòng nhập quận/huyện"); return false; }
        if (TextUtils.isEmpty(s(edtCity)))     { edtCity.setError("Vui lòng nhập thành phố"); return false; }
        return true;
    }

    private void addNewAddress(@NonNull String uid, @NonNull Address address) {
        // Nếu đánh dấu mặc định → bỏ mặc định các địa chỉ khác trước
        if (address.isDefault()) {
            removeDefaultFromOtherAddresses(uid, () -> actuallyAddAddress(uid, address));
        } else {
            actuallyAddAddress(uid, address);
        }
    }

    // THÊM mới: dùng Map để có createdAt = serverTimestamp (quan trọng cho “địa chỉ gốc”)
    private void actuallyAddAddress(@NonNull String uid, @NonNull Address address) {
        Map<String, Object> data = new HashMap<>();
        data.put("fullName", address.getFullName());
        data.put("phone", address.getPhone());
        data.put("line1", address.getLine1());
        data.put("line2", address.getLine2());
        data.put("district", address.getDistrict());
        data.put("city", address.getCity());
        data.put("note", address.getNote());
        data.put("isDefault", address.isDefault());
        data.put("createdAt", FieldValue.serverTimestamp());

        db.collection("users").document(uid)
                .collection("addresses")
                .add(data)
                .addOnSuccessListener(ref -> {
                    toast("Đã thêm địa chỉ mới");
                    finish();
                })
                .addOnFailureListener(e -> toast("Lỗi: " + e.getMessage()));
    }

    private void updateAddress(@NonNull String uid, @NonNull Address address) {
        // Nếu đang tick mặc định và trước đó chưa mặc định → bỏ mặc định các bản ghi khác
        if (address.isDefault() && (existingAddress == null || !existingAddress.isDefault())) {
            removeDefaultFromOtherAddresses(uid, () -> actuallyUpdateAddress(uid, address));
        } else {
            actuallyUpdateAddress(uid, address);
        }
    }

    // CẬP NHẬT: set() giữ nguyên createdAt cũ (nếu model/Firestore đã có)
    private void actuallyUpdateAddress(@NonNull String uid, @NonNull Address address) {
        if (existingAddress == null || existingAddress.getId() == null) return;

        db.collection("users").document(uid)
                .collection("addresses").document(existingAddress.getId())
                .set(address)
                .addOnSuccessListener(v -> {
                    toast("Đã cập nhật địa chỉ");
                    finish();
                })
                .addOnFailureListener(e -> toast("Lỗi: " + e.getMessage()));
    }

    // Bỏ mặc định các địa chỉ khác (chạy xong mới tiếp tục)
    private void removeDefaultFromOtherAddresses(@NonNull String uid, @NonNull Runnable onDone) {
        db.collection("users").document(uid)
                .collection("addresses")
                .whereEqualTo("isDefault", true)
                .get()
                .addOnSuccessListener(q -> {
                    if (q.isEmpty()) { onDone.run(); return; }
                    final int[] left = { q.size() };
                    for (DocumentSnapshot doc : q.getDocuments()) {
                        doc.getReference().update("isDefault", false)
                                .addOnCompleteListener(t -> {
                                    left[0]--;
                                    if (left[0] <= 0) onDone.run();
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    // Nếu đọc lỗi, vẫn tiếp tục thêm/cập nhật để không chặn người dùng
                    onDone.run();
                });
    }

    private static String s(EditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
