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

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieDrawable;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.android.material.card.MaterialCardView;

import android.widget.ImageView;
import android.widget.TextView;

import com.pro.milkteaapp.R;
import com.pro.milkteaapp.SessionManager;
import com.pro.milkteaapp.activity.AddressActivity;
import com.pro.milkteaapp.activity.ChangePasswordActivity;
import com.pro.milkteaapp.activity.EditProfileActivity;
import com.pro.milkteaapp.activity.LoginActivity;
import com.pro.milkteaapp.activity.OrderHistoryActivity;
import com.pro.milkteaapp.activity.admin.AdminMainActivity;
import com.pro.milkteaapp.activity.LoyaltyStatusActivity;
import com.pro.milkteaapp.databinding.ActivityProfileBinding;
import com.pro.milkteaapp.utils.ImageLoader;
import com.pro.milkteaapp.utils.TierStyleUtil;
import com.pro.milkteaapp.utils.LoyaltyPolicy;

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
            } else if (isAdded()) {
                requireActivity().getOnBackPressedDispatcher().onBackPressed();
            }
        });

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        session = new SessionManager(requireContext());

        if (auth.getCurrentUser() == null) {
            gotoLogin();
            return binding.getRoot();
        }

        // Nút chức năng chính
        binding.orderHistoryLayout.setOnClickListener(
                v -> startActivity(new Intent(requireContext(), OrderHistoryActivity.class)));
        binding.btnEditProfile.setOnClickListener(
                v -> startActivity(new Intent(requireContext(), EditProfileActivity.class)));
        binding.btnChangePassword.setOnClickListener(
                v -> startActivity(new Intent(requireContext(), ChangePasswordActivity.class)));
        binding.btnManageAddress.setOnClickListener(
                v -> startActivity(new Intent(requireContext(), AddressActivity.class)));

        View.OnClickListener openEdit = vv -> startActivity(new Intent(requireContext(), EditProfileActivity.class));
        binding.btnChangeAvatar.setOnClickListener(openEdit);
        binding.imgAvatar.setOnClickListener(openEdit);

        binding.btnAdminPanel.setOnClickListener(
                v -> startActivity(new Intent(requireContext(), AdminMainActivity.class)));

        binding.btnLogout.setOnClickListener(v -> {
            try { auth.signOut(); } catch (Throwable ignored) {}
            session.clear();
            Toast.makeText(requireContext(), getString(R.string.logged_out_successfully), Toast.LENGTH_SHORT).show();
            gotoLogin();
        });

        // ✅ Chỉ giữ click cho nút XEM
        binding.btnViewMember.setOnClickListener(
                v -> startActivity(new Intent(requireContext(), LoyaltyStatusActivity.class)));

        // ❌ Bỏ click cho badge, crown và lottie
        View vBadge = binding.getRoot().findViewById(R.id.tvTierBadge);
        if (vBadge != null) vBadge.setClickable(false);
        View vCrown = binding.getRoot().findViewById(R.id.imgTierCrown);
        if (vCrown != null) vCrown.setClickable(false);
        View vLottie = binding.getRoot().findViewById(R.id.lottieCrown);
        if (vLottie != null) vLottie.setClickable(false);

        startProfileRealtime();
        return binding.getRoot();
    }

    private void startProfileRealtime() {
        FirebaseUser fUser = auth.getCurrentUser();
        if (fUser == null) { gotoLogin(); return; }

        showLoading(true);
        stopProfileRealtime();

        String docId = session.getUid();
        if (!TextUtils.isEmpty(docId)) {
            profileListener = listenUserDoc(docId);
            return;
        }

        String email = fUser.getEmail();
        if (!TextUtils.isEmpty(email)) {
            db.collection("users")
                    .whereEqualTo("email", email)
                    .limit(1)
                    .get()
                    .addOnSuccessListener(qs -> {
                        showLoading(false);
                        if (!qs.isEmpty()) {
                            DocumentSnapshot doc = qs.getDocuments().get(0);
                            session.saveUserFromFirestore(
                                    doc.getId(),
                                    doc.getString("email"),
                                    doc.getString("fullName"),
                                    doc.getString("role"),
                                    doc.getString("avatar")
                            );
                            bindProfile(doc);
                            profileListener = listenUserDoc(doc.getId());
                        } else {
                            Toast.makeText(requireContext(), "Chưa có hồ sơ người dùng.", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(e -> {
                        showLoading(false);
                        Toast.makeText(requireContext(), "Lỗi tải hồ sơ: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        } else {
            profileListener = listenUserDoc(fUser.getUid());
        }
    }

    private ListenerRegistration listenUserDoc(@NonNull String docId) {
        return db.collection("users").document(docId)
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

    private void bindProfile(@Nullable DocumentSnapshot snap) {
        String fullName = "";
        String email = "";
        String role = session.getRole();
        String phone = "";
        String address = "";
        String avatar = null;

        long loyaltyPoints = 0L;
        String loyaltyTier = "Chưa xếp hạng";

        String userDocId = snap != null ? snap.getId() : session.getUid();

        if (snap != null && snap.exists()) {
            if (snap.getString("fullName") != null) fullName = snap.getString("fullName");
            if (snap.getString("email") != null) email = snap.getString("email");
            if (snap.getString("role") != null) role = snap.getString("role");
            if (snap.getString("phone") != null) phone = snap.getString("phone");
            if (snap.getString("address") != null) address = snap.getString("address");
            if (snap.getString("avatar") != null) avatar = snap.getString("avatar");

            if (snap.contains("loyaltyPoints")) {
                Long lp = snap.getLong("loyaltyPoints");
                if (lp != null) loyaltyPoints = lp;
            }

            String tierInDoc = snap.getString("loyaltyTier");
            if (!TextUtils.isEmpty(tierInDoc)) {
                loyaltyTier = tierInDoc;
            } else {
                loyaltyTier = LoyaltyPolicy.tierForPoints(loyaltyPoints);
            }

            session.saveUserFromFirestore(
                    snap.getId(), email, fullName, role, avatar
            );
        }

        reconcileTierIfNeeded(userDocId, loyaltyPoints, loyaltyTier);

        binding.tvName.setText(TextUtils.isEmpty(fullName) ? getString(R.string.unknown) : fullName);
        binding.tvEmail.setText(TextUtils.isEmpty(email) ? getString(R.string.unknown) : email);
        binding.tvRole.setText(TextUtils.isEmpty(role) ? "user" : role);
        binding.tvPhone.setText(TextUtils.isEmpty(phone) ? getString(R.string.unknown) : phone);
        binding.tvAddress.setText(TextUtils.isEmpty(address) ? getString(R.string.unknown) : address);

        TextView tvBadge = binding.getRoot().findViewById(R.id.tvTierBadge);
        if (tvBadge != null) tvBadge.setText(loyaltyTier);
        binding.tvLoyaltyPoints.setText(String.valueOf(loyaltyPoints));

        ImageLoader.load(binding.imgAvatar, avatar, R.drawable.ic_avatar_default);

        boolean isAdmin = "admin".equalsIgnoreCase(role);
        binding.btnAdminPanel.setVisibility(isAdmin ? View.VISIBLE : View.GONE);

        applyTierVisual(loyaltyTier);
    }

    private void reconcileTierIfNeeded(@Nullable String userDocId, long points, @Nullable String currentTier) {
        if (TextUtils.isEmpty(userDocId)) return;
        String expected = LoyaltyPolicy.tierForPoints(points);
        String cur = (currentTier == null) ? "" : currentTier.trim();
        if (!expected.equalsIgnoreCase(cur)) {
            FirebaseFirestore.getInstance()
                    .collection("users").document(userDocId)
                    .update("loyaltyTier", expected);
        }
    }

    private void applyTierVisual(@NonNull String tier) {
        MaterialCardView card = binding.cardLoyalty;
        ImageView imgCrown = binding.getRoot().findViewById(R.id.imgTierCrown);
        TextView tvBadge = binding.getRoot().findViewById(R.id.tvTierBadge);

        TierStyleUtil.apply(requireContext(), tier, card, imgCrown, tvBadge, tvBadge);

        // Lottie chỉ để trang trí
        View vLottie = binding.getRoot().findViewById(R.id.lottieCrown);
        if (vLottie instanceof LottieAnimationView lottie) {
            int resId = getCrownResForTier(tier);
            lottie.cancelAnimation();
            if (resId != 0) {
                lottie.setAnimation(resId);
                lottie.setRepeatCount(LottieDrawable.INFINITE);
                lottie.playAnimation();
                lottie.setVisibility(View.VISIBLE);
            } else {
                lottie.setVisibility(View.GONE);
            }
        }
    }

    private int getCrownResForTier(@NonNull String tier) {
        String t = tier.trim().toLowerCase();
        return switch (t) {
            case "vàng", "gold" -> R.raw.crown_gold;
            case "bạc", "silver" -> R.raw.crown_silver;
            case "đồng", "dong", "bronze" -> R.raw.crown_bronze;
            default -> 0;
        };
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
    public void onResume() {
        super.onResume();
        startProfileRealtime(); // Tự reload mỗi lần quay lại
    }

    @Override
    public void onPause() {
        stopProfileRealtime();
        super.onPause();
    }
}
