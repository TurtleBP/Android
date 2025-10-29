package com.pro.milkteaapp.data;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.*;

import java.util.HashMap;
import java.util.Map;

public class OrdersIdGenerator {

    private static final String META_COLLECTION = "meta";
    private static final String SEQ_DOC = "counters";
    private static final String FIELD_ORDER_SEQ = "orderSeq";

    private final FirebaseFirestore db;

    public OrdersIdGenerator() {
        this.db = FirebaseFirestore.getInstance();
    }

    /**
     * Tạo ID đơn dạng ORD00001, ORD00002... bằng transaction để đảm bảo không trùng.
     * width = 5 => 00001; đổi width nếu muốn (6 -> 000001).
     */
    public Task<String> nextOrderId(int width, String prefix) {
        final DocumentReference ref = db.collection(META_COLLECTION).document(SEQ_DOC);
        return db.runTransaction(transaction -> {
            DocumentSnapshot snap = transaction.get(ref);

            long current = 0L;
            if (snap.exists() && snap.contains(FIELD_ORDER_SEQ)) {
                Number n = snap.getDouble(FIELD_ORDER_SEQ);
                if (n != null) current = n.longValue();
            }

            long next = current + 1;

            Map<String, Object> updates = new HashMap<>();
            updates.put(FIELD_ORDER_SEQ, (double) next); // Firestore lưu số dạng double

            if (snap.exists()) {
                transaction.update(ref, updates);
            } else {
                transaction.set(ref, updates);
            }

            // Format: ORD + pad-left width
            String number = String.format("%0" + width + "d", next);
            return prefix + number;
        });
    }

    /** Mặc định: prefix ORD, width 5 -> ORD00001 */
    public Task<String> nextOrderId() {
        return nextOrderId(5, "ORD");
    }
}
