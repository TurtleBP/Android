package com.pro.milkteaapp.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.SessionManager;
import com.pro.milkteaapp.activity.AddressActivity;
import com.pro.milkteaapp.activity.ChangePasswordActivity;
import com.pro.milkteaapp.activity.EditProfileActivity;
import com.pro.milkteaapp.activity.LoginActivity;
import com.pro.milkteaapp.activity.OrderHistoryActivity;
import com.pro.milkteaapp.activity.admin.AdminMainActivity;
import com.pro.milkteaapp.databinding.ActivityProfileBinding;
import com.pro.milkteaapp.utils.ImageLoader;

public class ProfileFragment extends Fragment {

    private ActivityProfileBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private SessionManager session;
    private ListenerRegistration profileListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = ActivityProfileBinding.inflate(inflater, container, false);

        binding.toolbar.setNavigationOnClickListener(v -> {
            if (isAdded() && requireActivity() instanceof com.pro.milkteaapp.activity.MainActivity) {
                ((com.pro.milkteaapp.activity.MainActivity) requireActivity()).openHomeTab();
            }
        });

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();
        session = new SessionManager(requireContext());

        // Chưa login → đá ra login
        if (auth.getCurrentUser() == null) {
            gotoLogin();
            return binding.getRoot();
        }

        // ====== Các nút chức năng ======
        binding.orderHistoryLayout.setOnClickListener(
                v -> startActivity(new Intent(requireContext(), OrderHistoryActivity.class)));

        binding.btnEditProfile.setOnClickListener(
                v -> startActivity(new Intent(requireContext(), EditProfileActivity.class)));

        binding.btnChangePassword.setOnClickListener(
                v -> startActivity(new Intent(requireContext(), ChangePasswordActivity.class)));

        binding.btnLogout.setOnClickListener(v -> {
            try { auth.signOut(); } catch (Throwable ignored) {}
            session.clear();
            Toast.makeText(requireContext(), getString(R.string.logged_out_successfully), Toast.LENGTH_SHORT).show();
            gotoLogin();
        });

        binding.btnAdminPanel.setOnClickListener(
                v -> startActivity(new Intent(requireContext(), AdminMainActivity.class)));

        binding.btnManageAddress.setOnClickListener(
                v -> startActivity(new Intent(requireContext(), AddressActivity.class)));

        binding.btnChangeAvatar.setOnClickListener(
                v -> startActivity(new Intent(requireContext(), EditProfileActivity.class)));
        binding.imgAvatar.setOnClickListener(
                v -> startActivity(new Intent(requireContext(), EditProfileActivity.class)));

        // Bắt đầu nghe profile
        startProfileRealtime();

        return binding.getRoot();
    }

    private void startProfileRealtime() {
        FirebaseUser fUser = auth.getCurrentUser();
        if (fUser == null) {
            gotoLogin();
            return;
        }

        showLoading(true);
        stopProfileRealtime();

        // 1. Thử lấy uid đã lưu trong session
        String sessionUid = session.getUid();

        // Nếu session đã có mã kiểu USR00001 (tức là doc ID mới) → nghe thẳng doc đó
        if (!TextUtils.isEmpty(sessionUid) && sessionUid.startsWith("USR")) {
            profileListener = db.collection("users").document(sessionUid)
                    .addSnapshotListener((snap, e) -> {
                        if (!isAdded()) return;
                        showLoading(false);
                        if (e != null) {
                            Toast.makeText(requireContext(), "Lỗi tải hồ sơ: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        bindProfile(snap);
                    });
            return;
        }

        // 2. Nếu chưa có USR trong session → query theo email
        String email = fUser.getEmail();
        if (TextUtils.isEmpty(email)) {
            showLoading(false);
            Toast.makeText(requireContext(), "Không tìm thấy email người dùng.", Toast.LENGTH_SHORT).show();
            return;
        }

        profileListener = db.collection("users")
                .whereEqualTo("email", email)
                .limit(1)
                .addSnapshotListener((snapshots, e) -> {
                    if (!isAdded()) return;
                    showLoading(false);

                    if (e != null) {
                        Toast.makeText(requireContext(), "Lỗi tải hồ sơ: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (snapshots == null || snapshots.isEmpty()) {
                        Toast.makeText(requireContext(), "Chưa có thông tin hồ sơ.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    DocumentSnapshot doc = snapshots.getDocuments().get(0);

                    // 🔁 LƯU LẠI doc ID (USRxxxx) vào session để lần sau nghe thẳng
                    String docId = doc.getId();
                    if (!TextUtils.isEmpty(docId)) {
                        session.setUid(docId);
                    }

                    bindProfile(doc);
                });
    }

    private void stopProfileRealtime() {
        if (profileListener != null) {
            profileListener.remove();
            profileListener = null;
        }
    }

    /** Bind từ users/{id} */
    private void bindProfile(@Nullable DocumentSnapshot snap) {
        String fullName = "";
        String email    = "";
        String role     = session.getRole();
        String phone    = "";
        String address  = "";
        String avatar   = null;

        if (snap != null && snap.exists()) {
            if (snap.getString("fullName") != null) fullName = snap.getString("fullName");
            if (snap.getString("email")    != null) email    = snap.getString("email");
            if (snap.getString("role")     != null) {
                role = snap.getString("role");
                session.setRole(role);
            }
            if (snap.getString("phone")    != null) phone    = snap.getString("phone");
            if (snap.getString("address")  != null) address  = snap.getString("address");
            if (snap.getString("avatar")   != null) avatar   = snap.getString("avatar");

            // 🔁 nếu lần này lấy được docId (USR...) thì lưu lại để lần sau không phải query theo email
            String docId = snap.getId();
            if (!TextUtils.isEmpty(docId) && !docId.equals(session.getUid())) {
                session.setUid(docId);
            }
        }

        binding.tvName.setText(TextUtils.isEmpty(fullName) ? getString(R.string.unknown) : fullName);
        binding.tvEmail.setText(TextUtils.isEmpty(email) ? getString(R.string.unknown) : email);
        binding.tvRole.setText(role == null ? "user" : role);
        binding.tvPhone.setText(TextUtils.isEmpty(phone) ? getString(R.string.unknown) : phone);
        binding.tvAddress.setText(TextUtils.isEmpty(address) ? getString(R.string.unknown) : address);

        ImageLoader.load(binding.imgAvatar, avatar, R.drawable.ic_avatar_default);

        boolean isAdmin = "admin".equalsIgnoreCase(role);
        binding.btnAdminPanel.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
    }

    private void showLoading(boolean show) {
        if (binding == null) return;
        binding.progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.contentGroup.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void gotoLogin() {
        if (getActivity() == null) return;
        Intent i = new Intent(requireContext(), LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        getActivity().finish();
    }

    @Override
    public void onDestroyView() {
        stopProfileRealtime();
        binding = null;
        super.onDestroyView();
    }
}
