package com.pro.milkteaapp.activity;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.databinding.ActivityEditProfileBinding;

import java.util.HashMap;
import java.util.Map;

public class EditProfileActivity extends AppCompatActivity {

    private ActivityEditProfileBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private DocumentReference userDoc;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEditProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        setupToolbar();
        if (!ensureLoginOrFinish()) return;

        setupSaveButton();
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

                    String email = firstNonEmpty(
                            snap.getString("email"),
                            (u != null ? u.getEmail() : null)
                    );

                    String phone   = orDefault(snap.getString("phone"));
                    String address = orDefault(snap.getString("address"));

                    binding.editTextFullName.setText(fullName);
                    binding.editTextEmail.setText(email);
                    binding.editTextPhone.setText(phone);
                    binding.editTextAddress.setText(address);

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

            saveToFirestore(fullName, email, phone, address);
        });
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

    private void saveToFirestore(@NonNull String fullName,
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
                    Toast.makeText(this, getString(R.string.save_failed_with_reason, e.getMessage()), Toast.LENGTH_LONG).show();
                });
    }

    // ===== Helpers =====
    private void setLoading(boolean loading) {
        View progress = findViewById(R.id.progressBar);
        if (progress != null) progress.setVisibility(loading ? View.VISIBLE : View.GONE);

        binding.buttonSaveProfile.setEnabled(!loading);
        setEnabled(binding.editTextFullName, !loading);
        setEnabled(binding.editTextEmail, !loading);
        setEnabled(binding.editTextPhone, !loading);
        setEnabled(binding.editTextAddress, !loading);
    }

    private void setEnabled(EditText e, boolean enabled) {
        if (e != null) e.setEnabled(enabled);
    }

    private String text(@NonNull EditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    private String orDefault(String v) {
        return v == null ? "" : v;
    }

    private String firstNonEmpty(String a, String b) {
        if (!TextUtils.isEmpty(a)) return a;
        if (!TextUtils.isEmpty(b)) return b;
        return "";
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
