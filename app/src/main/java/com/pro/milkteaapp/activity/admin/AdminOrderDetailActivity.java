package com.pro.milkteaapp.activity.admin;

import android.annotation.SuppressLint;
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
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Transaction;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.adapter.OrderLinesAdapter;
import com.pro.milkteaapp.databinding.ActivityAdminOrderDetailBinding;
import com.pro.milkteaapp.models.OrderLine;
import com.pro.milkteaapp.utils.MoneyUtils;
import com.pro.milkteaapp.utils.StatusBarUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminOrderDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ORDER_ID = "order_id";

    private ActivityAdminOrderDetailBinding b;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final OrderLinesAdapter itemsAdapter = new OrderLinesAdapter();

    private String orderId;
    private String userId;
    private String statusNow;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityAdminOrderDetailBinding.inflate(getLayoutInflater());
        StatusBarUtil.setupDefaultStatusBar(this);
        setContentView(b.getRoot());

        setSupportActionBar(b.toolbar);
        b.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        b.rvItems.setLayoutManager(new LinearLayoutManager(this));
        b.rvItems.setAdapter(itemsAdapter);

        orderId = getIntent().getStringExtra(EXTRA_ORDER_ID);
        if (TextUtils.isEmpty(orderId)) {
            toastLong("Thiếu mã đơn hàng");
            finish();
            return;
        }

        b.btnAdminConfirm.setOnClickListener(v -> doConfirm());
        b.btnAdminCancel.setOnClickListener(v -> showCancelDialog());

        loadOrder(orderId);
    }

    private void loadOrder(String orderId) {
        setLoading(true);
        final DocumentReference orderRef = db.collection("orders").document(orderId);
        orderRef.get()
                .addOnSuccessListener(this::bindOrder)
                .addOnFailureListener(e -> {
                    toastLong("Lỗi tải đơn: " + e.getMessage());
                    finish();
                })
                .addOnCompleteListener(t -> setLoading(false));
    }

    @SuppressLint("SetTextI18n")
    @SuppressWarnings("unchecked")
    private void bindOrder(DocumentSnapshot d) {
        if (!d.exists()) {
            toast("Đơn không tồn tại");
            finish();
            return;
        }

        String id = d.getId();
        statusNow = s(d.getString("status"));
        userId = s(d.getString("userId"));

        // Người đặt
        b.tvUser.setText("Đang tải người đặt.");
        if (!TextUtils.isEmpty(userId)) {
            db.collection("users").document(userId).get()
                    .addOnSuccessListener(u -> {
                        String name = s(u.getString("name"));
                        String email = s(u.getString("email"));
                        b.tvUser.setText(!TextUtils.isEmpty(name) || !TextUtils.isEmpty(email)
                                ? join(name, email)
                                : "(Không có thông tin người dùng)");
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

        String confirmedBy = s(d.getString("confirmedBy"));

        // huỷ (nhận cả 2 kiểu khoá)
        String cancelReason = firstNonEmpty(
                s(d.getString("cancelReason")),
                s(d.getString("cancelledReason"))
        );
        Timestamp canceledAt = d.getTimestamp("canceledAt");
        if (canceledAt == null) canceledAt = d.getTimestamp("cancelledAt");
        String cancelledBy = s(d.getString("cancelledBy"));

        // UI
        b.toolbar.setTitle("Đơn #" + id);
        b.tvOrderId.setText("#" + id);
        b.tvStatus.setText(statusNow);

        b.tvSubtotal.setText(MoneyUtils.formatVnd(subtotal));
        b.tvDiscount.setText(MoneyUtils.formatVnd(discount));
        b.tvShippingFee.setText(MoneyUtils.formatVnd(shippingFee));
        b.tvFinalTotal.setText(MoneyUtils.formatVnd(finalTotal));

        b.tvCreatedAt.setText(createdAt != null ? formatTs(createdAt) : "—");

        setVisible(b.tvConfirmedAt, confirmedAt != null);
        if (confirmedAt != null) b.tvConfirmedAt.setText("Xác nhận: " + formatTs(confirmedAt));

        setVisible(b.tvFinishedAt, finishedAt != null);
        if (finishedAt != null) b.tvFinishedAt.setText("Hoàn tất: " + formatTs(finishedAt));

        setVisible(b.tvConfirmedBy, !TextUtils.isEmpty(confirmedBy));
        if (!TextUtils.isEmpty(confirmedBy)) b.tvConfirmedBy.setText("Người xác nhận: " + confirmedBy);

        // địa chỉ
        String addressStr = s(d.getString("address"));
        Map<String, Object> addressObj = null;
        Object addressAny = d.get("addressObj");
        if (addressAny instanceof Map) {
            addressObj = (Map<String, Object>) addressAny;
        }
        if (addressObj != null && !addressObj.isEmpty()) {
            String name = s(addressObj.get("fullName"));
            String phone = s(addressObj.get("phone"));
            String display = s(addressObj.get("display"));
            b.tvReceiver.setText(join(name, phone));
            b.tvAddressLines.setText(!TextUtils.isEmpty(display) ? display : "—");
        } else {
            b.tvReceiver.setText("—");
            b.tvAddressLines.setText(!TextUtils.isEmpty(addressStr) ? addressStr : "Chưa có địa chỉ");
        }

        // items — GIỮ API CŨ
        itemsAdapter.submit(mapOrderLines(d.get("items")));

        // block huỷ
        renderCancelBlock(statusNow, cancelReason, canceledAt, cancelledBy);

        // nút theo trạng thái
        updateButtonsByStatus();
    }

    private void updateButtonsByStatus() {
        boolean isPending = "PENDING".equalsIgnoreCase(statusNow);
        setVisible(b.btnAdminConfirm, isPending);
        setVisible(b.btnAdminCancel,  isPending);
    }

    /** Pending → FINISHED, sau đó mới cộng loyaltyPoints + totalSpent */
    @SuppressLint("SetTextI18n")
    private void doConfirm() {
        if (TextUtils.isEmpty(orderId)) return;

        lockActionButtons(true);
        setLoading(true);

        final DocumentReference orderRef = db.collection("orders").document(orderId);
        db.runTransaction((Transaction.Function<Void>) tr -> {
            DocumentSnapshot snap = tr.get(orderRef);
            String cur = s(snap.getString("status"));
            if (!"PENDING".equalsIgnoreCase(cur)) {
                throw new IllegalStateException("Chỉ xác nhận đơn từ trạng thái PENDING.");
            }
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", "FINISHED");
            updates.put("confirmedAt", FieldValue.serverTimestamp());
            updates.put("finishedAt", FieldValue.serverTimestamp());
            updates.put("confirmedBy", getAdminEmail());
            tr.update(orderRef, updates);
            return null;
        }).addOnSuccessListener(v -> {
            toast("✅ Đã xác nhận đơn");
            statusNow = "FINISHED";
            b.tvStatus.setText("FINISHED");
            updateButtonsByStatus();
            writeInboxForUser(
                    userId,
                    "order_done",
                    "Đơn hàng " + orderId + " đã hoàn thành",
                    orderId,
                    null
            );

            // === CHỈ TẠI ĐÂY mới cộng điểm / tổng chi tiêu ===
            awardPointsForFinished(orderId, userId);

        }).addOnFailureListener(e -> {
            toastLong("Lỗi xác nhận: " + e.getMessage());
        }).addOnCompleteListener(t -> {
            lockActionButtons(false);
            setLoading(false);
        });
    }

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

    @SuppressLint("SetTextI18n")
    private void doCancel(String reason) {
        if (TextUtils.isEmpty(orderId)) return;

        lockActionButtons(true);
        setLoading(true);

        final DocumentReference orderRef = db.collection("orders").document(orderId);
        db.runTransaction((Transaction.Function<Void>) tr -> {
            DocumentSnapshot snap = tr.get(orderRef);
            String cur = s(snap.getString("status"));
            if (!"PENDING".equalsIgnoreCase(cur)) {
                throw new IllegalStateException("Chỉ huỷ đơn từ trạng thái PENDING.");
            }
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", "CANCELLED");
            // Ghi cả 2 kiểu khoá để tương thích
            updates.put("cancelReason", reason);
            updates.put("canceledAt", FieldValue.serverTimestamp());
            updates.put("cancelledReason", reason);
            updates.put("cancelledAt", FieldValue.serverTimestamp());
            updates.put("cancelledBy", getAdminEmail());
            tr.update(orderRef, updates);
            return null;
        }).addOnSuccessListener(v -> {
            toast("🚫 Đã huỷ đơn");
            statusNow = "CANCELLED";
            b.tvStatus.setText("CANCELLED");
            renderCancelBlock(
                    statusNow,
                    reason,
                    null,
                    getAdminEmail()
            );
            updateButtonsByStatus();
            writeInboxForUser(
                    userId,
                    "order_cancelled",
                    "Đơn hàng " + orderId + " đã bị huỷ" + (TextUtils.isEmpty(reason) ? "" : (": " + reason)),
                    orderId,
                    reason
            );
        }).addOnFailureListener(e -> {
            toastLong("Lỗi huỷ: " + e.getMessage());
        }).addOnCompleteListener(t -> {
            lockActionButtons(false);
            setLoading(false);
        });
    }

    /** Đọc total/finalTotal rồi cộng loyaltyPoints + totalSpent. KHÔNG cộng rewardPoints. */
    private void awardPointsForFinished(@Nullable String orderId, @Nullable String userId) {
        if (TextUtils.isEmpty(orderId) || TextUtils.isEmpty(userId)) return;

        db.collection("orders").document(orderId).get()
                .addOnSuccessListener(doc -> {
                    if (doc == null || !doc.exists()) return;

                    long finalTotal = 0L;
                    Double ft = doc.getDouble("finalTotal");
                    if (ft != null && ft > 0) finalTotal = ft.longValue();
                    if (finalTotal == 0L) {
                        Double t = doc.getDouble("total");
                        if (t != null) finalTotal = t.longValue();
                    }
                    if (finalTotal <= 0) return;

                    addLoyaltyPointsForTier(userId, finalTotal);
                    updateTotalSpent(userId, finalTotal);
                })
                .addOnFailureListener(e -> {
                    // log nhẹ, không chặn UI
                });
    }

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

    // ===== Helpers =====
    @SuppressLint("SetTextI18n")
    private void renderCancelBlock(String status, String cancelReason, @Nullable Timestamp canceledAt, String cancelledBy) {
        boolean show = "CANCELLED".equalsIgnoreCase(status)
                || !TextUtils.isEmpty(cancelReason)
                || canceledAt != null;

        setVisible(b.cardCancelInfo, show);

        if (!show) return;

        b.tvCancelReason.setText(!TextUtils.isEmpty(cancelReason) ? cancelReason : "—");

        if (canceledAt != null) {
            b.tvCanceledAt.setText("Thời gian huỷ: " + formatTs(canceledAt));
            setVisible(b.tvCanceledAt, true);
        } else {
            setVisible(b.tvCanceledAt, false);
        }

        if (!TextUtils.isEmpty(cancelledBy)) {
            b.tvCanceledBy.setText("Huỷ bởi: " + cancelledBy);
            setVisible(b.tvCanceledBy, true);
        } else {
            setVisible(b.tvCanceledBy, false);
        }
    }

    private static String firstNonEmpty(String... vals) {
        if (vals == null) return "";
        for (String v : vals) {
            if (!TextUtils.isEmpty(v)) return v;
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private List<OrderLine> mapOrderLines(Object itemsObj) {
        List<OrderLine> lines = new ArrayList<>();
        if (itemsObj instanceof List<?>) {
            for (Object o : (List<?>) itemsObj) {
                if (o instanceof Map<?, ?>) {
                    Map<String, Object> m = (Map<String, Object>) o;
                    lines.add(OrderLine.fromMap(m));
                }
            }
        }
        return lines;
    }

    private void lockActionButtons(boolean lock) {
        b.btnAdminConfirm.setEnabled(!lock);
        b.btnAdminCancel.setEnabled(!lock);
    }

    private void setVisible(View v, boolean visible) {
        v.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void setLoading(boolean loading) {
        b.progress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private String getAdminEmail() {
        return FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getEmail()
                : null;
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
    private void toastLong(String m) { Toast.makeText(this, m, Toast.LENGTH_LONG).show(); }

    private static String s(Object o) { return o == null ? "" : String.valueOf(o); }
    private static String join(String a, String b) {
        boolean ea = !TextUtils.isEmpty(a), eb = !TextUtils.isEmpty(b);
        if (ea && eb) return a + " • " + b;
        return ea ? a : (eb ? b : "");
    }
    private static double nz(Double d) { return d == null ? 0d : d; }
    private static String formatTs(Timestamp ts) {
        if (ts == null) return "";
        return DateFormat.format("HH:mm dd/MM/yyyy", ts.toDate()).toString();
    }

    /* ==== Chỉ còn cộng loyaltyPoints + totalSpent ==== */
    private void addLoyaltyPointsForTier(String userId, long totalAmount) {
        if (TextUtils.isEmpty(userId) || totalAmount <= 0) return;

        final long pointsToAdd = totalAmount / 10_000L; // 10k = 1 điểm
        if (pointsToAdd == 0) return;

        final DocumentReference userRef = db.collection("users").document(userId);
        db.runTransaction(tr -> {
            DocumentSnapshot userDoc = tr.get(userRef);
            if (!userDoc.exists()) return null;

            long currentPoints = userDoc.contains("loyaltyPoints") ? userDoc.getLong("loyaltyPoints") : 0L;
            long newTotalPoints = currentPoints + pointsToAdd;

            String newTier;
            if (newTotalPoints >= 1000) newTier = "Vàng";
            else if (newTotalPoints >= 400) newTier = "Bạc";
            else if (newTotalPoints >= 100) newTier = "Đồng";
            else newTier = "Chưa xếp hạng";

            tr.update(userRef, "loyaltyPoints", newTotalPoints, "loyaltyTier", newTier);
            return null;
        });
    }

    private void updateTotalSpent(String userId, long paidAmount) {
        if (TextUtils.isEmpty(userId) || paidAmount <= 0) return;
        db.collection("users").document(userId)
                .update("totalSpent", FieldValue.increment(paidAmount));
    }
}
