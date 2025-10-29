package com.pro.milkteaapp.activity;

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
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.adapter.OrderLinesAdapter;
import com.pro.milkteaapp.databinding.ActivityOrderDetailBinding;
import com.pro.milkteaapp.models.Order;
import com.pro.milkteaapp.models.OrderLine;
import com.pro.milkteaapp.utils.MoneyUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OrderDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ORDER_ID = "extra_order_id";

    private ActivityOrderDetailBinding b;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final OrderLinesAdapter itemsAdapter = new OrderLinesAdapter();

    private String orderId;
    private String status; // lưu trạng thái hiện tại để quyết định có cho hủy hay không

    @SuppressLint("PrivateResource")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityOrderDetailBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        // Toolbar + Back
        setSupportActionBar(b.toolbar);
        b.toolbar.setNavigationIcon(com.google.android.material.R.drawable.ic_arrow_back_black_24);
        b.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        // Items list
        b.rvItems.setLayoutManager(new LinearLayoutManager(this));
        b.rvItems.setAdapter(itemsAdapter);

        orderId = getIntent().getStringExtra(EXTRA_ORDER_ID);
        if (TextUtils.isEmpty(orderId)) {
            Toast.makeText(this, "Thiếu mã đơn hàng", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        b.btnCancelOrder.setOnClickListener(v -> showCancelDialog());

        loadOrder(orderId);
    }

    private void loadOrder(String orderId) {
        b.progress.setVisibility(View.VISIBLE);
        db.collection("orders").document(orderId).get()
                .addOnSuccessListener(this::bindOrder)
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Lỗi tải đơn: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    finish();
                })
                .addOnCompleteListener(t -> b.progress.setVisibility(View.GONE));
    }

    @SuppressLint("SetTextI18n")
    @SuppressWarnings("unchecked")
    private void bindOrder(DocumentSnapshot d) {
        if (!d.exists()) { Toast.makeText(this, "Đơn không tồn tại", Toast.LENGTH_SHORT).show(); finish(); return; }

        b.tvOrderId.setText("#" + d.getId());
        status = s(d.getString("status"));
        b.tvStatus.setText(status);

        b.tvSubtotal.setText(MoneyUtils.formatVnd(nz(d.getDouble("subtotal"))));
        b.tvDiscount.setText(MoneyUtils.formatVnd(nz(d.getDouble("discount"))));
        b.tvShippingFee.setText(MoneyUtils.formatVnd(nz(d.getDouble("shippingFee"))));
        b.tvFinalTotal.setText(MoneyUtils.formatVnd(nz(d.getDouble("finalTotal"))));

        // Địa chỉ
        String addressStr = d.getString("address");
        Map<String, Object> addressObj = (Map<String, Object>) d.get("addressObj");
        if (addressObj != null && !addressObj.isEmpty()) {
            String name = s(addressObj.get("fullName"));
            String phone = s(addressObj.get("phone"));
            String line1 = s(addressObj.get("line1"));
            String line2 = s(addressObj.get("line2"));
            String city = s(addressObj.get("city"));
            String province = s(addressObj.get("province"));
            String postal = s(addressObj.get("postalCode"));
            String display = s(addressObj.get("display"));

            b.tvReceiver.setText(join(" • ", name, phone));
            b.tvAddressLines.setText(!TextUtils.isEmpty(display)
                    ? display
                    : join(", ", line1, line2, city, province, postal));
        } else {
            b.tvReceiver.setText("—");
            b.tvAddressLines.setText(!TextUtils.isEmpty(addressStr) ? addressStr : "Chưa có địa chỉ");
        }

        // Items
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

        // Thông tin hủy (nếu có)
        String cancelReason = d.getString("cancelReason");
        Timestamp canceledAt = d.getTimestamp("canceledAt");
        if (!TextUtils.isEmpty(cancelReason) || canceledAt != null || Order.STATUS_CANCELLED.equals(status)) {
            b.cardCancelInfo.setVisibility(View.VISIBLE);
            b.tvCancelReason.setText(!TextUtils.isEmpty(cancelReason) ? cancelReason : "—");
            if (canceledAt != null) {
                String timeStr = DateFormat.getMediumDateFormat(this).format(canceledAt.toDate())
                        + " " + DateFormat.getTimeFormat(this).format(canceledAt.toDate());
                b.tvCanceledAt.setText(String.format(Locale.getDefault(), "Thời gian hủy: %s", timeStr));
            } else {
                b.tvCanceledAt.setText("");
            }
        } else {
            b.cardCancelInfo.setVisibility(View.GONE);
        }

        // Quy tắc hiển thị nút HỦY:
        // Cho hủy nếu đơn PENDING hoặc CONFIRMED (tùy business của bạn)
        boolean cancelable =
                Order.STATUS_PENDING.equals(status);
        // Ẩn nếu đã hủy/hoàn tất/đang giao/đang xử lý
        b.btnCancelOrder.setVisibility(cancelable ? View.VISIBLE : View.GONE);
    }

    @SuppressLint({"CutPasteId", "MissingInflatedId"})
    private void showCancelDialog() {
        // Inflate view nhập lý do
        View view = LayoutInflater.from(this).inflate(R.layout._dialog_cancel_reason, null, false);
        // Tận dụng 1 input trong layout có sẵn; hoặc tự tạo layout riêng. Ở đây gán id edtLine1 để nhập lý do.
        TextInputLayout til; TextInputEditText edt;
        til = view.findViewById(R.id.edtLine1) != null ? (TextInputLayout) view.findViewById(R.id.edtLine1).getParent().getParent() : null;
        edt = view.findViewById(R.id.edtLine1);

        // Nếu layout của bạn khác, thay bằng layout nhập lý do riêng:
        // View view = LayoutInflater.from(this).inflate(R.layout.dialog_cancel_reason, null);
        // TextInputLayout til = view.findViewById(R.id.tilReason);
        // TextInputEditText edt = view.findViewById(R.id.edtReason);

        if (edt != null) {
            edt.setHint("Nhập lý do hủy đơn");
            edt.setText("");
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Hủy đơn hàng")
                .setMessage("Bạn chắc chắn muốn hủy đơn này?")
                .setView(view)
                .setPositiveButton("Hủy đơn", (dialog, which) -> {
                    String reason = edt != null ? String.valueOf(edt.getText()).trim() : "";
                    performCancelOrder(reason);
                })
                .setNegativeButton("Đóng", null)
                .show();
    }

    private void performCancelOrder(String reason) {
        if (TextUtils.isEmpty(orderId)) return;

        b.progress.setVisibility(View.VISIBLE);
        db.collection("orders").document(orderId)
                .update(
                        "status", Order.STATUS_CANCELLED,
                        "cancelReason", reason,
                        "canceledAt", FieldValue.serverTimestamp()
                )
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Đã hủy đơn hàng", Toast.LENGTH_SHORT).show();
                    // Cập nhật UI tại chỗ (không phải reload mạng)
                    status = Order.STATUS_CANCELLED;
                    b.tvStatus.setText(status);
                    b.btnCancelOrder.setVisibility(View.GONE);
                    b.cardCancelInfo.setVisibility(View.VISIBLE);
                    b.tvCancelReason.setText(reason);
                    // Thời gian hủy sẽ hiển thị sau lần reload; hoặc bạn có thể fetch lại doc:
                    db.collection("orders").document(orderId).get()
                            .addOnSuccessListener(this::bindCancelBlockOnly);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Hủy đơn thất bại: " + e.getMessage(), Toast.LENGTH_LONG).show();
                })
                .addOnCompleteListener(t -> b.progress.setVisibility(View.GONE));
    }

    private void bindCancelBlockOnly(DocumentSnapshot d) {
        if (d == null) return;
        String reason = d.getString("cancelReason");
        Timestamp canceledAt = d.getTimestamp("canceledAt");
        b.cardCancelInfo.setVisibility(View.VISIBLE);
        b.tvCancelReason.setText(!TextUtils.isEmpty(reason) ? reason : "—");
        if (canceledAt != null) {
            String timeStr = DateFormat.getMediumDateFormat(this).format(canceledAt.toDate())
                    + " " + DateFormat.getTimeFormat(this).format(canceledAt.toDate());
            b.tvCanceledAt.setText(String.format(Locale.getDefault(), "Thời gian hủy: %s", timeStr));
        } else {
            b.tvCanceledAt.setText("");
        }
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
