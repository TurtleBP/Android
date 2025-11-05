package com.pro.milkteaapp.fragment.bottomsheet;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.*;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Transaction;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.SessionManager;

import java.util.HashMap;
import java.util.Map;

/**
 * BottomSheet đánh giá đơn hàng – mỗi đơn chỉ được đánh giá 1 lần.
 * ✅ ID người dùng sử dụng USRxxxxx từ SessionManager, không dùng Firebase UID.
 */
public class RateOrderBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_ORDER_ID = "arg_order_id";

    public static RateOrderBottomSheet newInstance(@NonNull String orderId) {
        RateOrderBottomSheet f = new RateOrderBottomSheet();
        Bundle b = new Bundle();
        b.putString(ARG_ORDER_ID, orderId);
        f.setArguments(b);
        return f;
    }

    private RatingBar ratingBar;
    private EditText reviewInput;
    private MaterialButton btnSubmit, btnCancel;
    private TextView tvRatingHint;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.bs_rate_order, container, false);
        ratingBar   = v.findViewById(R.id.ratingBar);
        reviewInput = v.findViewById(R.id.edtReview);
        btnSubmit   = v.findViewById(R.id.btnSubmit);
        btnCancel   = v.findViewById(R.id.btnCancel);
        tvRatingHint = v.findViewById(R.id.tvRatingHint);

        // 🌟 Màu sao vàng
        ratingBar.setProgressTintList(ContextCompat.getColorStateList(requireContext(), R.color.gold));
        ratingBar.setSecondaryProgressTintList(ContextCompat.getColorStateList(requireContext(), R.color.gold));
        ratingBar.setProgressBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.gray_200));

        // 💬 Hiển thị hint khi thay đổi số sao
        ratingBar.setOnRatingBarChangeListener((bar, rating, fromUser) -> {
            if (!fromUser) return;
            String hint = switch ((int) rating) {
                case 1 -> "1/5 - Rất tệ 😞";
                case 2 -> "2/5 - Tệ 😕";
                case 3 -> "3/5 - Bình thường 😐";
                case 4 -> "4/5 - Hài lòng 🙂";
                case 5 -> "5/5 - Rất hài lòng 😍";
                default -> "";
            };
            tvRatingHint.setText(hint);
        });

        btnCancel.setOnClickListener(view -> dismiss());
        btnSubmit.setOnClickListener(view -> submitOnce());

        return v;
    }

    private void submitOnce() {
        String orderId = getOrderId();
        String uid = getUnifiedUidOrToast();
        if (orderId == null || uid == null) return;

        final float stars = ratingBar.getRating();
        final String review = reviewInput.getText() != null ? reviewInput.getText().toString().trim() : "";

        if (stars <= 0f) {
            toast("Vui lòng chọn số sao");
            return;
        }

        final SessionManager sm = new SessionManager(requireContext());
        final String displayName = firstNonEmpty(
                sm.getDisplayName(),
                emailPrefix(sm.getEmail()),
                "Người dùng"
        );
        final String avatarUrl = firstNonEmpty(sm.getAvatar(), null);

        setLoading(true);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference orderRef = db.collection("orders").document(orderId);

        db.runTransaction((Transaction.Function<Void>) transaction -> {
            Map<String, Object> order = transaction.get(orderRef).getData();
            if (order == null) {
                throw new IllegalStateException("Đơn hàng không tồn tại");
            }

            Object ratedBy = order.get("ratedBy");
            Object ratedAt = order.get("ratedAt");
            if (ratedBy != null || ratedAt != null) {
                throw new IllegalStateException("Bạn đã đánh giá đơn này rồi");
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put("rating", stars);
            if (!TextUtils.isEmpty(review)) updates.put("review", review);
            updates.put("ratedAt", FieldValue.serverTimestamp());
            updates.put("ratedBy", uid);        // ✅ USRxxxxx
            updates.put("userId", uid);         // ✅ USRxxxxx
            updates.put("userName", displayName);
            if (!TextUtils.isEmpty(avatarUrl)) updates.put("userAvatar", avatarUrl);

            transaction.update(orderRef, updates);
            return null;
        }).addOnSuccessListener(unused -> {
            writeInboxRated(uid, orderId, Math.round(stars), review);
            toast("Cảm ơn bạn đã đánh giá!");
            dismiss();
        }).addOnFailureListener(e -> {
            String msg = !TextUtils.isEmpty(e.getMessage()) ? e.getMessage() : "Lỗi không xác định";
            toast("Không thể gửi đánh giá: " + msg);
            setLoading(false);
        });
    }

    private void writeInboxRated(@NonNull String uid, @NonNull String orderId, int rating, @Nullable String review) {
        try {
            FirebaseFirestore.getInstance()
                    .collection("users").document(uid)
                    .collection("inbox")
                    .add(new HashMap<String, Object>() {{
                        put("type", "order_review");
                        put("orderId", orderId);
                        put("message", "Bạn đã đánh giá đơn #" + orderId + " (" + rating + "☆)");
                        put("rating", rating);
                        if (!TextUtils.isEmpty(review)) put("review", review);
                        put("createdAt", Timestamp.now());
                        put("read", false);
                    }});
        } catch (Throwable ignored) {}
    }

    @Nullable
    private String getOrderId() {
        Bundle args = getArguments();
        return args == null ? null : args.getString(ARG_ORDER_ID);
    }

    /** ✅ Lấy USRxxxxx từ SessionManager, fallback cảnh báo nếu chưa đăng nhập */
    @Nullable
    private String getUnifiedUidOrToast() {
        String customId = null;
        try {
            customId = new SessionManager(requireContext()).getUid();
        } catch (Throwable ignored) {}

        if (!TextUtils.isEmpty(customId)) {
            return customId.trim(); // USRxxxxx
        }

        toast("Không tìm thấy ID người dùng. Vui lòng đăng nhập lại.");
        return null;
    }

    private void setLoading(boolean loading) {
        if (btnSubmit != null) btnSubmit.setEnabled(!loading);
        if (btnCancel != null) btnCancel.setEnabled(!loading);
    }

    private void toast(String m) {
        if (getContext() != null) {
            Toast.makeText(getContext(), m, Toast.LENGTH_LONG).show();
        }
    }

    private static String firstNonEmpty(String... arr) {
        if (arr == null) return null;
        for (String s : arr) if (s != null && !s.trim().isEmpty()) return s.trim();
        return null;
    }

    private static String emailPrefix(String email) {
        if (email == null) return null;
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }
}
