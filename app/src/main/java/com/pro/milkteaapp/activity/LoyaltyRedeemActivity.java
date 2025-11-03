package com.pro.milkteaapp.activity;

import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.pro.milkteaapp.adapter.LoyaltyRewardAdapter;
import com.pro.milkteaapp.databinding.ActivityLoyaltyRedeemBinding;
import com.pro.milkteaapp.models.LoyaltyReward;

import java.util.ArrayList;
import java.util.List;

public class LoyaltyRedeemActivity extends AppCompatActivity implements LoyaltyRewardAdapter.RedeemListener {

    private ActivityLoyaltyRedeemBinding b;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private long rewardPoints = 0;
    private LoyaltyRewardAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityLoyaltyRedeemBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        setSupportActionBar(b.toolbar);
        b.toolbar.setNavigationOnClickListener(v -> onBackPressed());

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        setupList();
        loadRewardPoints();
    }

    private void setupList() {
        b.recyclerRewards.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LoyaltyRewardAdapter(dummyRewards(), this);
        b.recyclerRewards.setAdapter(adapter);
    }

    private List<LoyaltyReward> dummyRewards() {
        List<LoyaltyReward> list = new ArrayList<>();
        list.add(new LoyaltyReward("R001", "Voucher -10.000đ", "Giảm 10k cho đơn bất kỳ", 80));
        list.add(new LoyaltyReward("R002", "Voucher -20.000đ", "Giảm 20k cho đơn từ 80k", 150));
        list.add(new LoyaltyReward("R003", "Topping Free", "Đổi 1 topping miễn phí", 60));
        list.add(new LoyaltyReward("R004", "Thức uống miễn phí", "Đổi 1 ly size S", 300));
        return list;
    }

    private void loadRewardPoints() {
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "Bạn chưa đăng nhập", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        String uid = auth.getCurrentUser().getUid();
        b.progress.setVisibility(android.view.View.VISIBLE);

        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    b.progress.setVisibility(android.view.View.GONE);
                    if (!doc.exists()) {
                        Toast.makeText(this, "Không tìm thấy hồ sơ", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (doc.contains("rewardPoints") && doc.getLong("rewardPoints") != null) {
                        rewardPoints = doc.getLong("rewardPoints");
                    }

                    b.tvRewardPoints.setText(String.valueOf(rewardPoints));
                })
                .addOnFailureListener(e -> {
                    b.progress.setVisibility(android.view.View.GONE);
                    Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void onRedeem(LoyaltyReward reward) {
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "Bạn chưa đăng nhập", Toast.LENGTH_SHORT).show();
            return;
        }
        if (reward == null) return;

        if (rewardPoints < reward.getCostPoints()) {
            Toast.makeText(this, "Điểm đổi quà không đủ", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = auth.getCurrentUser().getUid();
        long remain = rewardPoints - reward.getCostPoints();

        b.progress.setVisibility(android.view.View.VISIBLE);
        db.collection("users").document(uid)
                .update("rewardPoints", remain)
                .addOnSuccessListener(v -> {
                    db.collection("users").document(uid)
                            .collection("redeemed_rewards")
                            .add(reward.toMap())
                            .addOnSuccessListener(x -> {
                                b.progress.setVisibility(android.view.View.GONE);
                                rewardPoints = remain;
                                b.tvRewardPoints.setText(String.valueOf(rewardPoints));
                                Toast.makeText(this, "Đổi quà thành công!", Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e -> {
                                b.progress.setVisibility(android.view.View.GONE);
                                Toast.makeText(this, "Đã trừ điểm nhưng không lưu được lịch sử", Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    b.progress.setVisibility(android.view.View.GONE);
                    Toast.makeText(this, "Đổi quà thất bại: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
