package com.pro.milkteaapp.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.SessionManager;
import com.pro.milkteaapp.databinding.ActivityEditProfileBinding;
import com.pro.milkteaapp.utils.StatusBarUtil;

import java.util.HashMap;
import java.util.Map;

public class EditProfileActivity extends AppCompatActivity {

    private ActivityEditProfileBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private DocumentReference userDoc;
    private SessionManager session;

    private ActivityResultLauncher<String> pickImageLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEditProfileBinding.inflate(getLayoutInflater());
        StatusBarUtil.setupDefaultStatusBar(this);
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        session = new SessionManager(this);

        setupToolbar();
        if (!ensureLoginOrFinish()) return;

        // đăng ký launcher chọn ảnh
        registerGalleryPicker();

        binding.btnPickAvatar.setOnClickListener(v -> showPickAvatarOptions());
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
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, R.string.please_login_first, Toast.LENGTH_SHORT).show();
            finish();
            return false;
        }
        return true;
    }

    private void registerGalleryPicker() {
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        // hiển thị luôn
                        loadAvatar(binding.imgAvatarPreview, uri.toString(), R.drawable.ic_avatar_default);
                        // lưu Firestore
                        saveAvatarStringToFirestore(uri.toString());
                    }
                }
        );
    }

    private void loadCurrentProfile() {
        setLoading(true);

        String sid = session.getUid();
        if (!TextUtils.isEmpty(sid)) {
            userDoc = db.collection("users").document(sid);
            fetchAndBind(userDoc);
            return;
        }

        FirebaseUser u = auth.getCurrentUser();
        String email = u != null ? u.getEmail() : null;
        if (!TextUtils.isEmpty(email)) {
            db.collection("users")
                    .whereEqualTo("email", email)
                    .limit(1)
                    .get()
                    .addOnSuccessListener(qs -> {
                        if (!qs.isEmpty()) {
                            DocumentSnapshot doc = qs.getDocuments().get(0);
                            userDoc = doc.getReference();
                            session.setUid(doc.getId());
                            fetchAndBind(userDoc);
                        } else {
                            // fallback theo firebase uid
                            userDoc = db.collection("users").document(u.getUid());
                            fetchAndBind(userDoc);
                        }
                    })
                    .addOnFailureListener(e -> {
                        setLoading(false);
                        Toast.makeText(this, "Lỗi tải hồ sơ: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        } else {
            userDoc = db.collection("users").document(auth.getCurrentUser().getUid());
            fetchAndBind(userDoc);
        }
    }

    private void fetchAndBind(@NonNull DocumentReference ref) {
        ref.get()
                .addOnSuccessListener(snap -> {
                    FirebaseUser u = auth.getCurrentUser();

                    String fullName = firstNonEmpty(snap.getString("fullName"), u != null ? u.getDisplayName() : null);
                    String email = firstNonEmpty(snap.getString("email"), u != null ? u.getEmail() : null);
                    String phone = orEmpty(snap.getString("phone"));
                    String address = orEmpty(snap.getString("address"));
                    String avatar = orEmpty(snap.getString("avatar"));

                    binding.editTextFullName.setText(fullName);
                    binding.editTextEmail.setText(email);
                    binding.editTextPhone.setText(phone);
                    binding.editTextAddress.setText(address);

                    if (!TextUtils.isEmpty(avatar)) session.setAvatar(avatar);
                    loadAvatar(binding.imgAvatarPreview, avatar, R.drawable.ic_avatar_default);

                    setLoading(false);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Lỗi tải hồ sơ: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
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
            String email = text(binding.editTextEmail);
            String phone = text(binding.editTextPhone);
            String address = text(binding.editTextAddress);

            if (!validate(fullName, email, phone)) return;

            if (userDoc == null) {
                String finalId = !TextUtils.isEmpty(session.getUid()) ? session.getUid() : u.getUid();
                userDoc = db.collection("users").document(finalId);
            }

            saveProfileFields(fullName, email, phone, address);
        });
    }

    /**
     * Hiển thị lựa chọn:
     * 1. Ảnh có sẵn (drawable)
     * 2. Chọn từ thư viện
     */
    private void showPickAvatarOptions() {
        String[] options = new String[]{"Chọn avatar có sẵn", "Chọn từ thư viện"};
        new AlertDialog.Builder(this)
                .setTitle("Chọn ảnh đại diện")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        // mở danh sách drawable
                        showAvatarChooser();
                    } else {
                        // mở gallery
                        if (pickImageLauncher != null) {
                            pickImageLauncher.launch("image/*");
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // giữ nguyên logic cũ: chọn từ drawable
    /* Thay thế hàm showAvatarChooser() bằng bản mới */
    /** Chọn avatar từ res/drawable, chỉ lấy file có tên bắt đầu bằng "avt" */
    private void showAvatarChooser() {
        // Lấy toàn bộ resource trong R.drawable
        java.lang.reflect.Field[] drawables = R.drawable.class.getFields();
        java.util.List<String> avtNames = new java.util.ArrayList<>();

        // Lọc ra tên bắt đầu bằng "avt"
        for (java.lang.reflect.Field field : drawables) {
            String name = field.getName();
            if (name.toLowerCase().startsWith("avt")) {
                avtNames.add(name);
            }
        }

        // Nếu không có ảnh nào
        if (avtNames.isEmpty()) {
            Toast.makeText(this, "Không tìm thấy ảnh avatar nào trong res/drawable", Toast.LENGTH_SHORT).show();
            return;
        }

        // Chuyển sang mảng để hiển thị dialog
        String[] names = avtNames.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle("Chọn avatar từ res/drawable")
                .setItems(names, (dialog, which) -> {
                    String drawableName = names[which];
                    saveAvatarDrawableNameToFirestore(drawableName);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }



    private void saveAvatarDrawableNameToFirestore(@NonNull String drawableName) {
        ensureUserDoc();
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

    // dùng cho gallery
    private void saveAvatarStringToFirestore(@NonNull String avatarString) {
        ensureUserDoc();
        setLoading(true);

        Map<String, Object> upd = new HashMap<>();
        upd.put("avatar", avatarString);

        userDoc.set(upd, SetOptions.merge())
                .addOnSuccessListener(v -> {
                    session.setAvatar(avatarString);
                    setLoading(false);
                    Toast.makeText(this, "Đã cập nhật ảnh đại diện", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Lỗi cập nhật: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void ensureUserDoc() {
        if (userDoc == null) {
            FirebaseUser u = auth.getCurrentUser();
            String finalId = !TextUtils.isEmpty(session.getUid())
                    ? session.getUid()
                    : (u != null ? u.getUid() : "unknown");
            userDoc = db.collection("users").document(finalId);
        }
    }

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

    /**
     * Hàm load avatar nâng cấp:
     * - http(s):// -> load mạng
     * - content:// hoặc file:// -> load từ thiết bị
     * - tên drawable -> load từ drawable
     */
    private void loadAvatar(@NonNull ImageView img, String urlOrName, @DrawableRes int placeholder) {
        if (!TextUtils.isEmpty(urlOrName)) {
            String s = urlOrName.trim();

            // 1) URL trên mạng
            if (s.startsWith("http://") || s.startsWith("https://")) {
                Glide.with(this)
                        .load(s)
                        .placeholder(placeholder)
                        .error(placeholder)
                        .into(img);
                return;
            }

            // 2) URI từ gallery (content://...) hoặc file://
            if (s.startsWith("content://") || s.startsWith("file://")) {
                Uri uri = Uri.parse(s);
                Glide.with(this)
                        .load(uri)
                        .placeholder(placeholder)
                        .error(placeholder)
                        .into(img);
                return;
            }

            // 3) drawable name
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

        // fallback
        Glide.with(this).load(placeholder).into(img);
    }

    @SuppressLint("DiscouragedApi")
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

    private void setEnabled(EditText e, boolean enabled) {
        if (e != null) e.setEnabled(enabled);
    }

    private String text(@NonNull EditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    private String orEmpty(String v) {
        return v == null ? "" : v;
    }

    private String firstNonEmpty(String a, String b) {
        return !TextUtils.isEmpty(a) ? a : (!TextUtils.isEmpty(b) ? b : "");
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
