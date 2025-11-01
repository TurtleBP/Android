package com.pro.milkteaapp.activity.admin;

import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.adapter.OrderLinesAdapter;
import com.pro.milkteaapp.databinding.ActivityAdminOrderDetailBinding;
import com.pro.milkteaapp.models.OrderLine;
import com.pro.milkteaapp.utils.MoneyUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Chi tiết đơn dành cho ADMIN
 * - Hiển thị tên người đặt (users/{userId})
 * - Cho admin XÁC NHẬN / HUỶ luôn
 */
public class AdminOrderDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ORDER_ID = "order_id";

    private ActivityAdminOrderDetailBinding b;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final OrderLinesAdapter itemsAdapter = new OrderLinesAdapter();

    private String orderId;
    private String userId;     // để gửi inbox
    private String statusNow;  // để ẩn/hiện nút

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityAdminOrderDetailBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        setSupportActionBar(b.toolbar);
        b.toolbar.setNavigationOnClickListener(v -> onBackPressed());

        b.rvItems.setLayoutManager(new LinearLayoutManager(this));
        b.rvItems.setAdapter(itemsAdapter);

        orderId = getIntent().getStringExtra(EXTRA_ORDER_ID);
        if (TextUtils.isEmpty(orderId)) {
            Toast.makeText(this, "Thiếu mã đơn hàng", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // click nút
        b.btnAdminConfirm.setOnClickListener(v -> doConfirm());
        b.btnAdminCancel.setOnClickListener(v -> showCancelDialog());

        loadOrder(orderId);
    }

    private void loadOrder(String orderId) {
        b.progress.setVisibility(View.VISIBLE);
        db.collection("orders").document(orderId)
                .get()
                .addOnSuccessListener(this::bindOrder)
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Lỗi tải đơn: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    finish();
                })
                .addOnCompleteListener(t -> b.progress.setVisibility(View.GONE));
    }

    @SuppressWarnings("unchecked")
    private void bindOrder(DocumentSnapshot d) {
        if (!d.exists()) {
            Toast.makeText(this, "Đơn không tồn tại", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String id = d.getId();
        statusNow = s(d.getString("status"));
        userId = s(d.getString("userId"));

        // Hiển thị tên người đặt
        b.tvUser.setText("Đang tải người đặt...");
        if (!TextUtils.isEmpty(userId)) {
            db.collection("users").document(userId).get()
                    .addOnSuccessListener(u -> {
                        String name = s(u.getString("name"));
                        String email = s(u.getString("email"));
                        if (!TextUtils.isEmpty(name) || !TextUtils.isEmpty(email)) {
                            b.tvUser.setText(join(" • ", name, email));
                        } else {
                            b.tvUser.setText("(Không có thông tin người dùng)");
                        }
                    })
                    .addOnFailureListener(e -> b.tvUser.setText("Không thể tải người dùng"));
        } else {
            b.tvUser.setText("(Không có userId)");
        }

        // tiền
        double subtotal    = nz(d.getDouble("subtotal"));
        double discount    = nz(d.getDouble("discount"));
        double shippingFee = nz(d.getDouble("shippingFee"));
        double finalTotal  = nz(d.getDouble("finalTotal"));
        if (finalTotal == 0) finalTotal = nz(d.getDouble("total"));

        Timestamp createdAt   = d.getTimestamp("createdAt");
        Timestamp confirmedAt = d.getTimestamp("confirmedAt");
        Timestamp finishedAt  = d.getTimestamp("finishedAt");

        // huỷ
        String cancelReason = s(d.getString("cancelReason"));
        if (TextUtils.isEmpty(cancelReason)) cancelReason = s(d.getString("cancelledReason"));
        String cancelledBy = s(d.getString("cancelledBy"));
        Timestamp canceledAt = d.getTimestamp("canceledAt");
        if (canceledAt == null) canceledAt = d.getTimestamp("cancelledAt");

        String confirmedBy = s(d.getString("confirmedBy"));

        // gán
        b.toolbar.setTitle("Đơn #" + id);
        b.tvOrderId.setText("#" + id);
        b.tvStatus.setText(statusNow);

        b.tvSubtotal.setText(MoneyUtils.formatVnd(subtotal));
        b.tvDiscount.setText(MoneyUtils.formatVnd(discount));
        b.tvShippingFee.setText(MoneyUtils.formatVnd(shippingFee));
        b.tvFinalTotal.setText(MoneyUtils.formatVnd(finalTotal));

        if (createdAt != null) b.tvCreatedAt.setText(formatTs(createdAt));
        else b.tvCreatedAt.setText("—");

        if (confirmedAt != null) {
            b.tvConfirmedAt.setVisibility(View.VISIBLE);
            b.tvConfirmedAt.setText("Xác nhận: " + formatTs(confirmedAt));
        } else b.tvConfirmedAt.setVisibility(View.GONE);

        if (finishedAt != null) {
            b.tvFinishedAt.setVisibility(View.VISIBLE);
            b.tvFinishedAt.setText("Hoàn tất: " + formatTs(finishedAt));
        } else b.tvFinishedAt.setVisibility(View.GONE);

        if (!TextUtils.isEmpty(confirmedBy)) {
            b.tvConfirmedBy.setVisibility(View.VISIBLE);
            b.tvConfirmedBy.setText("Người xác nhận: " + confirmedBy);
        } else b.tvConfirmedBy.setVisibility(View.GONE);

        // địa chỉ
        String addressStr = s(d.getString("address"));
        Map<String, Object> addressObj = (Map<String, Object>) d.get("addressObj");
        if (addressObj != null && !addressObj.isEmpty()) {
            String name = s(addressObj.get("fullName"));
            String phone = s(addressObj.get("phone"));
            String display = s(addressObj.get("display"));
            b.tvReceiver.setText(join(" • ", name, phone));
            b.tvAddressLines.setText(!TextUtils.isEmpty(display) ? display : "—");
        } else {
            b.tvReceiver.setText("—");
            b.tvAddressLines.setText(!TextUtils.isEmpty(addressStr) ? addressStr : "Chưa có địa chỉ");
        }

        // items
        List<OrderLine> lines = new ArrayList<>();
        Object itemsObj = d.get("items");
        if (itemsObj instanceof List) {
            for (Object o : (List<?>) itemsObj) {
                if (o instanceof Map) {
                    lines.add(OrderLine.fromMap((Map<String, Object>) o));
                }
            }
        }
        itemsAdapter.submit(lines);

        // block huỷ
        if ("CANCELLED".equalsIgnoreCase(statusNow) || canceledAt != null || !TextUtils.isEmpty(cancelReason)) {
            b.cardCancelInfo.setVisibility(View.VISIBLE);
            b.tvCancelReason.setText(!TextUtils.isEmpty(cancelReason) ? cancelReason : "—");
            if (canceledAt != null) b.tvCanceledAt.setText("Thời gian huỷ: " + formatTs(canceledAt));
            if (!TextUtils.isEmpty(cancelledBy)) {
                b.tvCanceledBy.setText("Huỷ bởi: " + cancelledBy);
                b.tvCanceledBy.setVisibility(View.VISIBLE);
            } else b.tvCanceledBy.setVisibility(View.GONE);
        } else {
            b.cardCancelInfo.setVisibility(View.GONE);
        }

        // hiển thị nút
        updateButtonsByStatus();
    }

    /** Pending → show; Finished/Cancelled → hide */
    private void updateButtonsByStatus() {
        boolean isPending = "PENDING".equalsIgnoreCase(statusNow);
        b.btnAdminConfirm.setVisibility(isPending ? View.VISIBLE : View.GONE);
        b.btnAdminCancel.setVisibility(isPending ? View.VISIBLE : View.GONE);
    }

    /* ===== XÁC NHẬN ĐƠN ===== */
    private void doConfirm() {
        if (TextUtils.isEmpty(orderId)) return;
        String adminEmail = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getEmail()
                : null;

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "FINISHED");
        updates.put("confirmedAt", FieldValue.serverTimestamp());
        updates.put("finishedAt", FieldValue.serverTimestamp());
        updates.put("confirmedBy", adminEmail);

        b.progress.setVisibility(View.VISIBLE);
        db.collection("orders").document(orderId)
                .update(updates)
                .addOnSuccessListener(v -> {
                    Toast.makeText(this, "✅ Đã xác nhận đơn", Toast.LENGTH_SHORT).show();
                    statusNow = "FINISHED";
                    b.tvStatus.setText("FINISHED");
                    updateButtonsByStatus();

                    // gửi inbox cho user
                    writeInboxForUser(
                            userId,
                            "order_done",
                            "Đơn hàng " + orderId + " đã hoàn thành",
                            orderId,
                            null
                    );
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Lỗi xác nhận: " + e.getMessage(), Toast.LENGTH_LONG).show())
                .addOnCompleteListener(t -> b.progress.setVisibility(View.GONE));
    }

    /* ===== HUỶ ĐƠN ===== */
    private void showCancelDialog() {
        if (TextUtils.isEmpty(orderId)) return;

        View view = LayoutInflater.from(this).inflate(R.layout._dialog_cancel_reason, null, false);
        TextInputEditText edt = view.findViewById(R.id.edtReason);
        if (edt != null) {
            edt.setHint("Nhập lý do huỷ đơn");
            edt.setText("");
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Huỷ đơn hàng")
                .setMessage("Bạn chắc chắn muốn huỷ đơn này?")
                .setView(view)
                .setNegativeButton("Đóng", null)
                .setPositiveButton("Huỷ đơn", (d, w) -> {
                    String reason = edt != null ? String.valueOf(edt.getText()).trim() : "";
                    doCancel(reason);
                })
                .show();
    }

    private void doCancel(String reason) {
        String adminEmail = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getEmail()
                : null;

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "CANCELLED");
        // ghi cả 2 dạng
        updates.put("cancelReason", reason);
        updates.put("canceledAt", FieldValue.serverTimestamp());
        updates.put("cancelledReason", reason);
        updates.put("cancelledAt", FieldValue.serverTimestamp());
        updates.put("cancelledBy", adminEmail);

        b.progress.setVisibility(View.VISIBLE);
        db.collection("orders").document(orderId)
                .update(updates)
                .addOnSuccessListener(v -> {
                    Toast.makeText(this, "🚫 Đã huỷ đơn", Toast.LENGTH_SHORT).show();
                    statusNow = "CANCELLED";
                    b.tvStatus.setText("CANCELLED");
                    // show block huỷ
                    b.cardCancelInfo.setVisibility(View.VISIBLE);
                    b.tvCancelReason.setText(!TextUtils.isEmpty(reason) ? reason : "—");
                    b.tvCanceledBy.setText("Huỷ bởi: " + (adminEmail != null ? adminEmail : "admin"));
                    b.tvCanceledBy.setVisibility(View.VISIBLE);
                    updateButtonsByStatus();

                    writeInboxForUser(
                            userId,
                            "order_cancelled",
                            "Đơn hàng " + orderId + " đã bị huỷ" + (TextUtils.isEmpty(reason) ? "" : (": " + reason)),
                            orderId,
                            reason
                    );
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Lỗi huỷ: " + e.getMessage(), Toast.LENGTH_LONG).show())
                .addOnCompleteListener(t -> b.progress.setVisibility(View.GONE));
    }

    /* ===== Gửi inbox ===== */
    private void writeInboxForUser(String userId, String type, String message, String orderId, @Nullable String reason) {
        if (TextUtils.isEmpty(userId)) return;
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", type);
        msg.put("orderId", orderId);
        msg.put("message", message);
        if (!TextUtils.isEmpty(reason)) msg.put("reason", reason);
        msg.put("createdAt", FieldValue.serverTimestamp());
        msg.put("read", false);

        db.collection("users").document(userId)
                .collection("inbox")
                .add(msg);
    }

    private String formatTs(Timestamp ts) {
        return DateFormat.getMediumDateFormat(this).format(ts.toDate())
                + " " + DateFormat.getTimeFormat(this).format(ts.toDate());
    }

    private static String s(Object o) { return o == null ? "" : String.valueOf(o).trim(); }
    private static double nz(Double d) { return d == null ? 0 : d; }

    private static String join(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (TextUtils.isEmpty(p)) continue;
            if (sb.length() > 0) sb.append(sep);
            sb.append(p);
        }
        return sb.toString();
    }
}
