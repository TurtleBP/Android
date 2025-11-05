package com.pro.milkteaapp.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.pro.milkteaapp.R;

public class ForgotPasswordActivity extends AppCompatActivity {

    private EditText emailEditText;
    private FirebaseAuth auth; // Firebase Authentication instance

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        emailEditText = findViewById(R.id.emailEditText);
        Button resetButton = findViewById(R.id.resetButton);
        TextView loginTextView = findViewById(R.id.loginTextView);

        // ✅ Khởi tạo FirebaseAuth
        auth = FirebaseAuth.getInstance();

        // Gửi email reset
        resetButton.setOnClickListener(v -> {
            String email = emailEditText.getText().toString().trim();

            if (TextUtils.isEmpty(email)) {
                Toast.makeText(this,
                        getString(R.string.please_enter_your_email),
                        Toast.LENGTH_SHORT).show();
                return;
            }

            // ✅ Gửi email reset mật khẩu
            auth.sendPasswordResetEmail(email)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(this,
                                    getString(R.string.reset_link_sent),
                                    Toast.LENGTH_LONG).show();
                            navigateToLogin();
                        } else {
                            Toast.makeText(this,
                                    getString(R.string.email_not_found_or_invalid),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
        });

        // Quay lại trang login
        loginTextView.setOnClickListener(v -> navigateToLogin());
    }

    private void navigateToLogin() {
        Intent intent = new Intent(ForgotPasswordActivity.this, LoginActivity.class);
        startActivity(intent);
        finish();
    }
}
