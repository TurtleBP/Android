package com.pro.milkteaapp.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/** Các tiện ích ghi tin nhắn vào inbox người dùng. */
public final class InboxUtils {
    private InboxUtils() {}

    /** Admin (hoặc server) gọi: ghi tin nhắn “đơn hoàn thành” cho userId. */
    public static void writeOrderDoneToUser(@NonNull String userId, @NonNull String orderId) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "order_done");
        msg.put("orderId", orderId);
        msg.put("message", "Đơn hàng với mã " + orderId + " đã hoàn thành");
        msg.put("createdAt", Timestamp.now());
        msg.put("read", false);
        FirebaseFirestore.getInstance()
                .collection("users").document(userId)
                .collection("inbox").add(msg);
    }

    /** App user gọi sau khi đánh giá: ghi “đã đánh giá” cho chính user hiện tại. */
    public static void writeOrderReviewedToCurrentUserInbox(@NonNull String orderId,
                                                            @Nullable Integer rating,
                                                            @Nullable String review) {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) return;

        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "order_review");
        msg.put("orderId", orderId);
        msg.put("message", "Bạn đã gửi đánh giá cho đơn " + orderId);
        if (rating != null) msg.put("rating", rating);
        if (review != null) msg.put("review", review);
        msg.put("createdAt", Timestamp.now());
        msg.put("read", false);

        FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("inbox").add(msg);
    }
}
