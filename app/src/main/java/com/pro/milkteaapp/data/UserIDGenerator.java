package com.pro.milkteaapp.data;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * Sinh mã user tự động: USR00001, USR00002...
 * Dùng transaction Firestore để tránh trùng.
 */
public class UserIDGenerator {

    private static final String META_COLLECTION = "meta";
    private static final String SEQ_DOC = "userCounters";
    private static final String FIELD_USER_SEQ = "userSeq";

    private final FirebaseFirestore db;

    public UserIDGenerator() {
        this.db = FirebaseFirestore.getInstance();
    }

    public Task<String> nextUserId(int width, String prefix) {
        final DocumentReference ref = db.collection(META_COLLECTION).document(SEQ_DOC);
        return db.runTransaction(transaction -> {
            DocumentSnapshot snap = transaction.get(ref);

            long current = 0L;
            if (snap.exists() && snap.contains(FIELD_USER_SEQ)) {
                Number n = snap.getDouble(FIELD_USER_SEQ);
                if (n != null) current = n.longValue();
            }

            long next = current + 1;

            Map<String, Object> updates = new HashMap<>();
            // Firestore số sẽ lưu dạng double
            updates.put(FIELD_USER_SEQ, (double) next);

            if (snap.exists()) {
                transaction.update(ref, updates);
            } else {
                transaction.set(ref, updates);
            }

            // ví dụ: width = 5 -> 00001
            String number = String.format("%0" + width + "d", next);
            return prefix + number;
        });
    }

    public Task<String> nextUserId() {
        return nextUserId(5, "USR");
    }
}
