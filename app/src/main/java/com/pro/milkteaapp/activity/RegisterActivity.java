package com.pro.milkteaapp.activity;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.firestore.FirebaseFirestore;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.SessionManager;
import com.pro.milkteaapp.data.UserIDGenerator;
import com.pro.milkteaapp.models.User;

public class RegisterActivity extends AppCompatActivity {

    private EditText fullNameEditText, emailEditText, phoneEditText, addressEditText,
            passwordEditText, confirmPasswordEditText;
    private Button registerButton;
    private TextView loginTextView;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private AlertDialog loadingDialog;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        initViews();
        setupFirebase();
        setupLoadingDialog();
        setupListeners();
    }

    private void initViews() {
        fullNameEditText        = findViewById(R.id.fullNameEditText);
        emailEditText           = findViewById(R.id.emailEditText);
        phoneEditText           = findViewById(R.id.phoneEditText);
        addressEditText         = findViewById(R.id.addressEditText);
        passwordEditText        = findViewById(R.id.passwordEditText);
        confirmPasswordEditText = findViewById(R.id.confirmPasswordEditText);
        registerButton          = findViewById(R.id.registerButton);
        loginTextView           = findViewById(R.id.loginTextView);

        confirmPasswordEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                registerButton.performClick();
                return true;
            }
            return false;
        });
    }

    private void setupFirebase() {
        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();
    }

    @SuppressLint("InflateParams")
    private void setupLoadingDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = LayoutInflater.from(this);
        builder.setView(inflater.inflate(R.layout._dialog_loading, null));
        builder.setCancelable(false);
        loadingDialog = builder.create();
    }

    private void setupListeners() {
        registerButton.setOnClickListener(v -> doRegister());

        loginTextView.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });
    }

    private void doRegister() {
        final String fullName        = s(fullNameEditText);
        final String email           = s(emailEditText);
        final String phone           = s(phoneEditText);
        final String address         = s(addressEditText);
        final String password        = s(passwordEditText);
        final String confirmPassword = s(confirmPasswordEditText);

        if (!validateInputs(fullName, email, phone, password, confirmPassword)) return;

        setLoading(true);

        // 1. tạo tài khoản Firebase Auth (để login)
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    if (result == null || result.getUser() == null) {
                        setLoading(false);
                        Toast.makeText(this, "Không tạo được tài khoản. Vui lòng thử lại.", Toast.LENGTH_LONG).show();
                        return;
                    }

                    // gửi mail verify (không bắt buộc)
                    result.getUser().sendEmailVerification()
                            .addOnFailureListener(e ->
                                    Log.w("Auth", "Send verification email failed: " + e.getMessage()));

                    // 2. sinh mã user dạng USR00001
                    UserIDGenerator gen = new UserIDGenerator();
                    gen.nextUserId()
                            .addOnSuccessListener(newId -> {
                                // 3. lưu Firestore với documentId = newId
                                saveUserToFirestore(newId, fullName, email, phone, address);
                            })
                            .addOnFailureListener(e -> {
                                setLoading(false);
                                Toast.makeText(this, "Không tạo được mã người dùng: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    handleRegistrationError(e);
                });
    }

    private boolean validateInputs(String fullName, String email, String phone,
                                   String password, String confirmPassword) {
        if (TextUtils.isEmpty(fullName)) {
            fullNameEditText.setError("Vui lòng nhập họ và tên");
            fullNameEditText.requestFocus();
            return false;
        }

        if (TextUtils.isEmpty(email)) {
            emailEditText.setError("Vui lòng nhập email");
            emailEditText.requestFocus();
            return false;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.setError("Email không hợp lệ");
            emailEditText.requestFocus();
            return false;
        }

        if (!TextUtils.isEmpty(phone)) {
            String digitsOnly = phone.replaceAll("\\D+", "");
            if (digitsOnly.length() < 9 || digitsOnly.length() > 11) {
                phoneEditText.setError("Số điện thoại không hợp lệ (9-11 chữ số)");
                phoneEditText.requestFocus();
                return false;
            }
        }

        if (TextUtils.isEmpty(password)) {
            passwordEditText.setError("Vui lòng nhập mật khẩu");
            passwordEditText.requestFocus();
            return false;
        }

        if (password.length() < 6) {
            passwordEditText.setError("Mật khẩu phải có ít nhất 6 ký tự");
            passwordEditText.requestFocus();
            return false;
        }

        if (TextUtils.isEmpty(confirmPassword)) {
            confirmPasswordEditText.setError("Vui lòng xác nhận mật khẩu");
            confirmPasswordEditText.requestFocus();
            return false;
        }

        if (!password.equals(confirmPassword)) {
            confirmPasswordEditText.setError("Mật khẩu xác nhận không khớp");
            confirmPasswordEditText.requestFocus();
            return false;
        }

        return true;
    }

    private void saveUserToFirestore(String userId,
                                     String fullName,
                                     String email,
                                     String phone,
                                     String address) {
        User user = new User();
        user.setUid(userId); // ✅ uid = document id
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPhone(phone == null ? "" : phone);
        user.setAddress(address == null ? "" : address);
        user.setRole("user");
        user.setAvatar("");

        db.collection("users").document(userId)
                .set(user)
                .addOnSuccessListener(unused -> handleRegistrationSuccess(userId))
                .addOnFailureListener(e -> {
                    // nếu lưu Firestore lỗi thì vẫn nên signOut để user đăng ký lại
                    setLoading(false);
                    Toast.makeText(this, "Lỗi lưu hồ sơ: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void handleRegistrationSuccess(String userId) {
        // lưu luôn vào session ID này để Fragment khác dùng
        try {
            new SessionManager(this).setUid(userId);
        } catch (Throwable ignored) {}

        safeSignOutAndClear();
        setLoading(false);
        Toast.makeText(this, "Đăng ký thành công! Vui lòng đăng nhập.", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void handleRegistrationError(Exception e) {
        String msg = "Đăng ký thất bại: ";
        if (e instanceof FirebaseAuthUserCollisionException) {
            msg += "Email đã được sử dụng";
        } else if (e instanceof FirebaseAuthWeakPasswordException) {
            msg += "Mật khẩu quá yếu";
        } else if (e instanceof FirebaseAuthInvalidCredentialsException) {
            msg += "Thông tin xác thực không hợp lệ";
        } else {
            msg += (e != null && e.getMessage() != null) ? e.getMessage() : "Lỗi không xác định";
        }
        Log.e("Auth", "Registration failed", e);
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    private void safeSignOutAndClear() {
        try { FirebaseAuth.getInstance().signOut(); } catch (Exception ignore) {}
        try { new SessionManager(this).clear(); } catch (Throwable t) {
            Log.w("Session", "clear failed: " + t.getMessage());
        }
    }

    private void setLoading(boolean loading) {
        registerButton.setEnabled(!loading);
        if (loading && loadingDialog != null && !loadingDialog.isShowing()) {
            loadingDialog.show();
        } else if (!loading && loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }
    }

    private String s(EditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (loadingDialog != null && loadingDialog.isShowing()) loadingDialog.dismiss();
    }
}
