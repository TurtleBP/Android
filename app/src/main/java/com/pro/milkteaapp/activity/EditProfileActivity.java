package com.pro.milkteaapp.activity;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.*;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.SessionManager;
import com.pro.milkteaapp.repository.UserProfileRepository;
import com.pro.milkteaapp.databinding.ActivityEditProfileBinding;
import com.pro.milkteaapp.utils.AvatarHelper;

import java.util.HashMap;
import java.util.Map;

public class EditProfileActivity extends AppCompatActivity {

    private ActivityEditProfileBinding binding;
    private FirebaseAuth auth;
    private SessionManager session;
    private UserProfileRepository repo;
    private DocumentReference currentUserDoc;

    private static final String[] AVATAR_NAMES = new String[] {
            "avt01","avt02","avt03","avt04","avt05","avt06","ic_avatar_default"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEditProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth    = FirebaseAuth.getInstance();
        session = new SessionManager(this);
        repo    = new UserProfileRepository(session);

        setupToolbar();
        ensureLoginOrFinish();
        setupSaveButton();
        setupAvatarButton();

        loadProfile();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.edit_profile);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void ensureLoginOrFinish() {
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, R.string.please_login_first, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void loadProfile() {
        setLoading(true);
        repo.loadProfile()
                .addOnSuccessListener(snap -> {
                    currentUserDoc = snap.getReference();
                    bindProfile(snap);
                    setLoading(false);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Lỗi tải hồ sơ: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void bindProfile(@NonNull DocumentSnapshot snap) {
        FirebaseUser u = auth.getCurrentUser();

        String fullName = firstNonEmpty(snap.getString("fullName"), u != null ? u.getDisplayName() : null);
        String email    = firstNonEmpty(snap.getString("email"), u != null ? u.getEmail() : null);
        String phone    = orEmpty(snap.getString("phone"));
        String address  = orEmpty(snap.getString("address"));
        String avatar   = orEmpty(snap.getString("avatar"));

        binding.editTextFullName.setText(fullName);
        binding.editTextEmail.setText(email);
        binding.editTextPhone.setText(phone);
        binding.editTextAddress.setText(address);

        if (!TextUtils.isEmpty(avatar)) session.setAvatar(avatar);

        AvatarHelper.load(this, binding.imgAvatarPreview, avatar, R.drawable.ic_avatar_default);
    }

    private void setupSaveButton() {
        binding.buttonSaveProfile.setOnClickListener(v -> {
            FirebaseUser u = auth.getCurrentUser();
            if (u == null) {
                Toast.makeText(this, R.string.please_login_first, Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            String fullName = text(binding.editTextFullName);
            String email    = text(binding.editTextEmail);
            String phone    = text(binding.editTextPhone);
            String address  = text(binding.editTextAddress);

            if (!validate(fullName, email, phone)) return;

            setLoading(true);

            Map<String, Object> data = new HashMap<>();
            data.put("fullName", fullName);
            data.put("email", email);
            data.put("phone", phone);
            data.put("address", address);
            data.put("updatedAt", FieldValue.serverTimestamp());

            repo.saveProfileFields(data)
                    .addOnSuccessListener(unused -> {
                        // update Firebase Auth displayName
                        u.updateProfile(new UserProfileChangeRequest.Builder()
                                .setDisplayName(fullName)
                                .build());
                        setLoading(false);
                        Toast.makeText(this, R.string.saved_successfully, Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        setLoading(false);
                        Toast.makeText(this, "Lỗi lưu: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        });
    }

    private void setupAvatarButton() {
        binding.btnPickAvatar.setOnClickListener(v -> showAvatarChooser());
    }

    private void showAvatarChooser() {
        new AlertDialog.Builder(this)
                .setTitle("Chọn ảnh đại diện")
                .setItems(AVATAR_NAMES, (dialog, which) -> {
                    String name = AVATAR_NAMES[which];
                    setLoading(true);
                    repo.saveAvatar(name)
                            .addOnSuccessListener(unused -> {
                                AvatarHelper.load(this, binding.imgAvatarPreview, name, R.drawable.ic_avatar_default);
                                setLoading(false);
                                Toast.makeText(this, "Đã cập nhật ảnh đại diện", Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e -> {
                                setLoading(false);
                                Toast.makeText(this, "Lỗi cập nhật: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ===== helpers =====
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

    private void setEnabled(EditText e, boolean enabled) {
        if (e != null) e.setEnabled(enabled);
    }

    private String text(@NonNull EditText e) { return e.getText() == null ? "" : e.getText().toString().trim(); }
    private String orEmpty(String v) { return v == null ? "" : v; }

    private String firstNonEmpty(String a, String b) {
        return !TextUtils.isEmpty(a) ? a : (!TextUtils.isEmpty(b) ? b : "");
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

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
