package com.pro.milkteaapp.activity;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.SessionManager;
import com.pro.milkteaapp.databinding.ActivityEditProfileBinding;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class EditProfileActivity extends AppCompatActivity {

    private ActivityEditProfileBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private DocumentReference userDoc;
    private SessionManager session;

    // Danh sách tên drawable có sẵn trong res/drawable (đổi theo bộ icon của bạn)
    private static final String[] AVATAR_NAMES = new String[] {
            "avt01",
            "avt02",
            "avt03",
            "avt04",
            "avt05",
            "avt06",
            "ic_avatar_default"            // fallback mặc định
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEditProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth    = FirebaseAuth.getInstance();
        db      = FirebaseFirestore.getInstance();
        session = new SessionManager(this);

        setupToolbar();
        if (!ensureLoginOrFinish()) return;

        // Nút chọn avatar từ drawable
        binding.btnPickAvatar.setOnClickListener(v -> showAvatarChooser());

        // Lưu thông tin hồ sơ (không đụng tới ảnh — ảnh đã lưu ngay khi chọn)
        setupSaveButton();

        // Tải hồ sơ hiện tại
        loadCurrentProfile();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.edit_profile);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    private boolean ensureLoginOrFinish() {
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) {
            Toast.makeText(this, R.string.please_login_first, Toast.LENGTH_SHORT).show();
            finish();
            return false;
        }
        userDoc = db.collection("users").document(u.getUid());
        return true;
    }

    private void loadCurrentProfile() {
        if (userDoc == null) return;
        setLoading(true);

        userDoc.get()
                .addOnSuccessListener(snap -> {
                    FirebaseUser u = auth.getCurrentUser();

                    String fullName = firstNonEmpty(
                            snap.getString("fullName"),
                            (u != null ? u.getDisplayName() : null)
                    );
                    String email   = firstNonEmpty(
                            snap.getString("email"),
                            (u != null ? u.getEmail() : null)
                    );
                    String phone   = orDefault(snap.getString("phone"));
                    String address = orDefault(snap.getString("address"));
                    String avatar  = orDefault(snap.getString("avatar")); // có thể là URL hoặc tên drawable

                    binding.editTextFullName.setText(fullName);
                    binding.editTextEmail.setText(email);
                    binding.editTextPhone.setText(phone);
                    binding.editTextAddress.setText(address);

                    // Lưu vào session để phần khác dùng lại
                    if (!TextUtils.isEmpty(avatar)) session.setAvatar(avatar);

                    // Hiển thị avatar: URL thì load URL, nếu là tên drawable thì resolve sang resId
                    loadAvatar(binding.imgAvatarPreview, avatar, R.drawable.ic_avatar_default);

                    setLoading(false);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, getString(R.string.load_failed_with_reason, e.getMessage()), Toast.LENGTH_SHORT).show();
                });
    }

    private void setupSaveButton() {
        binding.buttonSaveProfile.setOnClickListener(v -> {
            if (auth.getCurrentUser() == null || userDoc == null) {
                Toast.makeText(this, R.string.please_login_first, Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            final String fullName = text(binding.editTextFullName);
            final String email    = text(binding.editTextEmail);
            final String phone    = text(binding.editTextPhone);
            final String address  = text(binding.editTextAddress);

            if (!validate(fullName, email, phone)) return;

            // Không xử lý ảnh ở đây (đã lưu ngay khi chọn trong dialog)
            saveProfileFields(fullName, email, phone, address);
        });
    }

    /** Mở dialog chọn avatar từ danh sách drawable */
    private void showAvatarChooser() {
        new AlertDialog.Builder(this)
                .setTitle("Chọn ảnh đại diện")
                .setItems(AVATAR_NAMES, (dialog, which) -> {
                    String drawableName = AVATAR_NAMES[which];
                    saveAvatarDrawableNameToFirestore(drawableName);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Lưu tên drawable vào Firestore và cập nhật UI ngay */
    private void saveAvatarDrawableNameToFirestore(@NonNull String drawableName) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || userDoc == null) {
            Toast.makeText(this, R.string.please_login_first, Toast.LENGTH_SHORT).show();
            return;
        }
        setLoading(true);

        Map<String, Object> upd = new HashMap<>();
        upd.put("avatar", drawableName);

        userDoc.set(upd, SetOptions.merge())
                .addOnSuccessListener(v -> {
                    session.setAvatar(drawableName);
                    loadAvatar(binding.imgAvatarPreview, drawableName, R.drawable.ic_avatar_default);
                    setLoading(false);
                    Toast.makeText(this, "Đã cập nhật ảnh đại diện", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Lỗi cập nhật: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    /** Lưu các trường văn bản vào Firestore (không liên quan ảnh) */
    private void saveProfileFields(@NonNull String fullName,
                                   @NonNull String email,
                                   @NonNull String phone,
                                   @NonNull String address) {
        setLoading(true);

        Map<String, Object> data = new HashMap<>();
        data.put("fullName", fullName);
        data.put("email", email);
        data.put("phone", phone);
        data.put("address", address);
        data.put("updatedAt", FieldValue.serverTimestamp());

        userDoc.set(data, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    FirebaseUser u = auth.getCurrentUser();
                    if (u != null && !TextUtils.isEmpty(fullName)) {
                        u.updateProfile(new UserProfileChangeRequest.Builder()
                                .setDisplayName(fullName)
                                .build());
                    }
                    setLoading(false);
                    Toast.makeText(this, R.string.saved_successfully, Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this,
                            getString(R.string.save_failed_with_reason, e.getMessage()),
                            Toast.LENGTH_LONG).show();
                });
    }

    // ===== Helpers =====

    /** Load avatar: nếu là URL thì load URL; nếu là tên drawable thì resolve resId và load; nếu rỗng → placeholder */
    private void loadAvatar(@NonNull ImageView img, String urlOrName, @DrawableRes int placeholder) {
        if (!TextUtils.isEmpty(urlOrName)) {
            String s = urlOrName.trim();
            if (s.startsWith("http://") || s.startsWith("https://")) {
                Glide.with(this)
                        .load(s)
                        .placeholder(placeholder)
                        .error(placeholder)
                        .into(img);
                return;
            }
            // tên dạng "drawable:ic_xxx" hoặc "ic_xxx"
            if (s.startsWith("drawable:")) s = s.substring("drawable:".length()).trim();
            int resId = resIdFromName(this, s);
            if (resId != 0) {
                Glide.with(this)
                        .load(resId)
                        .placeholder(placeholder)
                        .error(placeholder)
                        .into(img);
                return;
            }
        }
        Glide.with(this).load(placeholder).into(img);
    }

    private int resIdFromName(@NonNull Context ctx, @NonNull String name) {
        if (TextUtils.isEmpty(name)) return 0;
        return ctx.getResources().getIdentifier(name, "drawable", ctx.getPackageName());
    }

    private boolean validate(@NonNull String fullName, @NonNull String email, @NonNull String phone) {
        if (TextUtils.isEmpty(fullName)) {
            binding.editTextFullName.setError(getString(R.string.error_fullname_required));
            binding.editTextFullName.requestFocus();
            return false;
        }
        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.editTextEmail.setError(getString(R.string.error_invalid_email));
            binding.editTextEmail.requestFocus();
            return false;
        }
        if (!TextUtils.isEmpty(phone) && (phone.length() < 9 || phone.length() > 11)) {
            binding.editTextPhone.setError(getString(R.string.error_invalid_phone));
            binding.editTextPhone.requestFocus();
            return false;
        }
        return true;
    }

    private void setLoading(boolean loading) {
        View progress = findViewById(R.id.progressBar);
        if (progress != null) progress.setVisibility(loading ? View.VISIBLE : View.GONE);

        binding.buttonSaveProfile.setEnabled(!loading);
        setEnabled(binding.editTextFullName, !loading);
        setEnabled(binding.editTextEmail, !loading);
        setEnabled(binding.editTextPhone, !loading);
        setEnabled(binding.editTextAddress, !loading);
        binding.btnPickAvatar.setEnabled(!loading);
    }

    private void setEnabled(EditText e, boolean enabled) { if (e != null) e.setEnabled(enabled); }
    private String text(@NonNull EditText e) { return e.getText() == null ? "" : e.getText().toString().trim(); }
    private String orDefault(String v) { return v == null ? "" : v; }
    private String firstNonEmpty(String a, String b) { return !TextUtils.isEmpty(a) ? a : (!TextUtils.isEmpty(b) ? b : ""); }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
