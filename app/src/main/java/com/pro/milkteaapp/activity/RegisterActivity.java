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
import com.pro.milkteaapp.models.User;

import java.util.Objects;

public class RegisterActivity extends AppCompatActivity {

    private EditText fullNameEditText, emailEditText, phoneEditText, addressEditText,
            passwordEditText, confirmPasswordEditText;
    private Button registerButton;
    private TextView loginTextView;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    // Thay ProgressDialog bằng AlertDialog custom
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

        // Cho phép nhấn Done trên bàn phím để submit
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
        // dialog_loading.xml cần tồn tại trong res/layout
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

    // ========== ĐĂNG KÝ ========== //
    private void doRegister() {
        final String fullName        = s(fullNameEditText);
        final String email           = s(emailEditText);
        final String phone           = s(phoneEditText);
        final String address         = s(addressEditText);
        final String password        = s(passwordEditText);
        final String confirmPassword = s(confirmPasswordEditText);

        if (!validateInputs(fullName, email, phone, password, confirmPassword)) return;

        setLoading(true);
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    if (result.getUser() != null) {
                        result.getUser().sendEmailVerification()
                                .addOnFailureListener(e ->
                                        Log.w("Auth", "Send verification email failed: " + e.getMessage()));
                    }

                    String uid = Objects.requireNonNull(result.getUser()).getUid();
                    saveUserToFirestore(uid, fullName, email, phone, address);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    handleRegistrationError(e);
                });
    }

    // ========== VALIDATE ========== //
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

        // Phone có thể để trống; nếu nhập thì kiểm tra hợp lệ nhẹ
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

    // ========== LƯU FIRESTORE ========== //
    private void saveUserToFirestore(String uid, String fullName, String email, String phone, String address) {
        User user = new User();
        user.setUid(uid);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPhone(phone == null ? "" : phone);
        user.setAddress(address == null ? "" : address);
        user.setRole("user"); // mặc định user

        db.collection("users").document(uid)
                .set(user)
                .addOnSuccessListener(unused -> handleRegistrationSuccess())
                .addOnFailureListener(this::handleFirestoreError);
    }

    // ========== THÀNH CÔNG ========== //
    private void handleRegistrationSuccess() {
        safeSignOutAndClear();
        setLoading(false);
        Toast.makeText(this, "Đăng ký thành công! Vui lòng đăng nhập.", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // ========== XỬ LÝ LỖI ========== //
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

    private void handleFirestoreError(Exception e) {
        Log.e("Firestore", "Failed to save user data", e);

        if (auth.getCurrentUser() != null) {
            auth.getCurrentUser().delete()
                    .addOnSuccessListener(aVoid -> {
                        safeSignOutAndClear();
                        setLoading(false);
                        Toast.makeText(this, "Lỗi lưu dữ liệu người dùng. Vui lòng thử lại.", Toast.LENGTH_LONG).show();
                    })
                    .addOnFailureListener(deleteError -> {
                        safeSignOutAndClear();
                        setLoading(false);
                        Toast.makeText(this, "Lỗi hệ thống. Vui lòng liên hệ hỗ trợ.", Toast.LENGTH_LONG).show();
                    });
        } else {
            safeSignOutAndClear();
            setLoading(false);
            Toast.makeText(this, "Lỗi lưu dữ liệu người dùng. Vui lòng thử lại.", Toast.LENGTH_LONG).show();
        }
    }

    // ========== TIỆN ÍCH ========== //
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
