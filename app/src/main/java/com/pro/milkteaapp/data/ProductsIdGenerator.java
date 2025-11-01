package com.pro.milkteaapp.data;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/** Sinh ID sản phẩm dạng PRD00001, PRD00002... qua Firestore Transaction. */
public class ProductsIdGenerator {
    private static final String META_COLLECTION   = "meta";
    private static final String SEQ_DOC           = "counters";
    private static final String FIELD_PRODUCT_SEQ = "productSeq";

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    /** Mặc định: PRD + 5 chữ số → PRD00001 */
    public Task<String> nextProductId() {
        return nextProductId(5, "PRD");
    }

    public Task<String> nextProductId(int width, String prefix) {
        final DocumentReference ref = db.collection(META_COLLECTION).document(SEQ_DOC);
        return db.runTransaction(tr -> {
            DocumentSnapshot snap = tr.get(ref);

            long current = 0L;
            if (snap.exists() && snap.contains(FIELD_PRODUCT_SEQ)) {
                Object raw = snap.get(FIELD_PRODUCT_SEQ);
                if (raw instanceof Number) {
                    current = ((Number) raw).longValue();
                }
            }

            long next = current + 1;

            Map<String, Object> updates = new HashMap<>();
            updates.put(FIELD_PRODUCT_SEQ, next);

            if (snap.exists()) {
                tr.update(ref, updates);
            } else {
                tr.set(ref, updates);
            }

            String number = String.format("%0" + width + "d", next);
            return prefix + number; // ví dụ: PRD00001
        });
    }
}
