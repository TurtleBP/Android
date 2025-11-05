package com.pro.milkteaapp.activity;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.pro.milkteaapp.databinding.ActivityChangePasswordBinding;
import com.pro.milkteaapp.utils.StatusBarUtil;

import java.util.Objects;

public class ChangePasswordActivity extends AppCompatActivity {

    private ActivityChangePasswordBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChangePasswordBinding.inflate(getLayoutInflater());
        StatusBarUtil.setupDefaultStatusBar(this);
        setContentView(binding.getRoot());

        setupToolbar();
        setupChangePasswordButton();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Đổi mật khẩu");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void setupChangePasswordButton() {
        binding.buttonChangePassword.setOnClickListener(v -> {
            String currentPassword = Objects.requireNonNull(binding.editTextCurrentPassword.getText()).toString();
            String newPassword = Objects.requireNonNull(binding.editTextNewPassword.getText()).toString();
            String confirmPassword = Objects.requireNonNull(binding.editTextConfirmPassword.getText()).toString();

            if (currentPassword.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Vui lòng điền đầy đủ thông tin", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!newPassword.equals(confirmPassword)) {
                Toast.makeText(this, "Mật khẩu mới không khớp", Toast.LENGTH_SHORT).show();
                return;
            }

            // Change password logic here
            Toast.makeText(this, "Đã đổi mật khẩu thành công", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }

}