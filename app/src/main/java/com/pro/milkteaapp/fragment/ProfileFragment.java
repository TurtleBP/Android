package com.pro.milkteaapp.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
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
import com.pro.milkteaapp.models.User;
import com.pro.milkteaapp.utils.ImageLoader;

public class ProfileFragment extends Fragment {

    private ActivityProfileBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private SessionManager session;
    private ListenerRegistration profileListener;

    private TextView textViewLoyaltyTier;
    private TextView textViewLoyaltyPoints;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = ActivityProfileBinding.inflate(inflater, container, false);

        // Toolbar back → quay lại Home tab
        binding.toolbar.setNavigationOnClickListener(v -> {
            if (isAdded() && requireActivity() instanceof com.pro.milkteaapp.activity.MainActivity) {
                ((com.pro.milkteaapp.activity.MainActivity) requireActivity()).openHomeTab();
            }
        });

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();
        session = new SessionManager(requireContext());

        // Nếu chưa đăng nhập → về Login
        if (auth.getCurrentUser() == null) {
            gotoLogin();
            return binding.getRoot();
        }

        // --- Nút chức năng ---
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

        // Bấm ảnh hoặc nút đổi avatar → mở EditProfile
        binding.btnChangeAvatar.setOnClickListener(
                v -> startActivity(new Intent(requireContext(), EditProfileActivity.class)));
        binding.imgAvatar.setOnClickListener(
                v -> startActivity(new Intent(requireContext(), EditProfileActivity.class)));

        startProfileRealtime();

        loadUserProfile();

        return binding.getRoot();
    }

    // ========= PROFILE REALTIME (users/{uid}) =========
    private void startProfileRealtime() {
        String uid = resolveUidOrGoLogin();
        if (uid == null) return;

        showLoading(true);
        stopProfileRealtime();

        profileListener = db.collection("users").document(uid)
                .addSnapshotListener((snap, e) -> {
                    if (!isAdded()) return;
                    showLoading(false);

                    if (e != null) {
                        Toast.makeText(requireContext(), "Lỗi tải hồ sơ: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    bindProfile(snap);
                });
    }



    private void stopProfileRealtime() {
        if (profileListener != null) {
            profileListener.remove();
            profileListener = null;
        }
    }

    /** Bind từ users/{uid}: fullName, email, role, phone, address, avatar */
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
            if (snap.getString("avatar")   != null) avatar   = snap.getString("avatar"); // URL hoặc tên drawable
        }

        binding.tvName.setText(TextUtils.isEmpty(fullName) ? getString(R.string.unknown) : fullName);
        binding.tvEmail.setText(TextUtils.isEmpty(email) ? getString(R.string.unknown) : email);
        binding.tvRole.setText(role == null ? "user" : role);
        binding.tvPhone.setText(TextUtils.isEmpty(phone) ? getString(R.string.unknown) : phone);
        binding.tvAddress.setText(TextUtils.isEmpty(address) ? getString(R.string.unknown) : address);

        // ✅ Load avatar hỗ trợ URL và drawable name
        ImageLoader.load(binding.imgAvatar, avatar, R.drawable.ic_avatar_default);

        boolean isAdmin = "admin".equalsIgnoreCase(role);
        binding.btnAdminPanel.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
    }

    // ========= COMMON =========
    private String resolveUidOrGoLogin() {
        String uid = session.getUid();
        if (uid == null && auth.getCurrentUser() != null) {
            uid = auth.getCurrentUser().getUid();
            session.setUid(uid);
        }
        if (uid == null) {
            gotoLogin();
        }
        return uid;
    }

    private void loadUserProfile() {
        db.collection("users").document(auth.getUid()).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        User currentUser = documentSnapshot.toObject(User.class);

                        if (currentUser != null) {
                            String tier = currentUser.getLoyaltyTier();
                            long points = currentUser.getLoyaltyPoints();

                            if (tier == null || tier.isEmpty()) {
                                tier = "Bronze"; // Mặc định nếu dữ liệu cũ chưa có
                            }

                            if (textViewLoyaltyTier != null) {
                                textViewLoyaltyTier.setText("Hạng: " + tier);
                            }
                            if (textViewLoyaltyPoints != null) {
                                textViewLoyaltyPoints.setText("Điểm tích lũy: " + points);
                            }
                        }
                    }
                });
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
