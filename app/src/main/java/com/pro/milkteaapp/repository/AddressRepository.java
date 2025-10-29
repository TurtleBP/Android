package com.pro.milkteaapp.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.*;
import com.pro.milkteaapp.models.Address;

import java.util.*;

public class AddressRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    private @NonNull String requireUid() {
        String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        if (uid == null) throw new IllegalStateException("User not logged in");
        return uid;
    }

    private CollectionReference colRef() {
        return db.collection("users")
                .document(requireUid())
                .collection("addresses");
    }

    public void fetchAll(@NonNull OnAddresses cb, @Nullable OnError err) {
        colRef()
                .orderBy("isDefault", Query.Direction.DESCENDING)
                .orderBy(FieldPath.documentId(), Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snap -> {
                    List<Address> out = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Address a = toAddress(d);
                        if (a != null) out.add(a);
                    }
                    cb.onResult(out);
                })
                .addOnFailureListener(e -> { if (err != null) err.onError(e); });
    }

    /** Realtime: tự cập nhật UI khi add/edit/delete */
    public ListenerRegistration listenAll(@NonNull OnAddresses cb, @Nullable OnError err) {
        return colRef()
                .orderBy("isDefault", Query.Direction.DESCENDING)
                .orderBy(FieldPath.documentId(), Query.Direction.DESCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) { if (err != null) err.onError(e); return; }
                    if (snap == null) return;
                    List<Address> out = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Address a = toAddress(d);
                        if (a != null) out.add(a);
                    }
                    cb.onResult(out);
                });
    }

    public void addOrUpdate(@NonNull Address a, @NonNull OnAddressSaved onDone, @Nullable OnError err) {
        Map<String, Object> map = toMap(a);
        if (a.getId() == null || a.getId().isEmpty()) {
            map.put("createdAt", FieldValue.serverTimestamp());
            colRef().add(map)
                    .addOnSuccessListener(ref -> { a.setId(ref.getId()); onDone.onSaved(a); })
                    .addOnFailureListener(e -> { if (err != null) err.onError(e); });
        } else {
            colRef().document(a.getId()).set(map, SetOptions.merge())
                    .addOnSuccessListener(unused -> onDone.onSaved(a))
                    .addOnFailureListener(e -> { if (err != null) err.onError(e); });
        }
    }

    public void delete(@NonNull String id, @NonNull Runnable onDone, @Nullable OnError err) {
        colRef().document(id).delete()
                .addOnSuccessListener(unused -> onDone.run())
                .addOnFailureListener(e -> { if (err != null) err.onError(e); });
    }

    /** Đảm bảo chỉ 1 địa chỉ mặc định */
    public void setDefault(@NonNull String id, @NonNull Runnable onDone, @Nullable OnError err) {
        colRef().get().addOnSuccessListener(snap -> {
            WriteBatch batch = db.batch();
            for (DocumentSnapshot d : snap.getDocuments()) {
                batch.update(d.getReference(), "isDefault", d.getId().equals(id));
            }
            batch.commit()
                    .addOnSuccessListener(unused -> onDone.run())
                    .addOnFailureListener(e -> { if (err != null) err.onError(e); });
        }).addOnFailureListener(e -> { if (err != null) err.onError(e); });
    }

    private Address toAddress(DocumentSnapshot d) {
        try {
            Address a = new Address();
            a.setId(d.getId());
            a.setFullName(d.getString("fullName"));
            a.setPhone(d.getString("phone"));
            a.setLine1(d.getString("line1"));
            a.setLine2(d.getString("line2"));
            a.setCity(d.getString("city"));
            a.setProvince(d.getString("province"));
            a.setPostalCode(d.getString("postalCode"));
            Boolean def = d.getBoolean("isDefault");
            a.setDefault(def != null && def);
            return a;
        } catch (Throwable ignored) { return null; }
    }

    private Map<String, Object> toMap(Address a) {
        Map<String, Object> m = new HashMap<>();
        m.put("fullName", nz(a.getFullName()));
        m.put("phone", nz(a.getPhone()));
        m.put("line1", nz(a.getLine1()));
        m.put("line2", nz(a.getLine2()));
        m.put("city", nz(a.getCity()));
        m.put("province", nz(a.getProvince()));
        m.put("postalCode", nz(a.getPostalCode()));
        m.put("isDefault", a.isDefault());
        return m;
    }

    private static String nz(String s) { return s == null ? "" : s; }

    public interface OnAddresses { void onResult(@NonNull List<Address> addresses); }
    public interface OnAddressSaved { void onSaved(@NonNull Address saved); }
    public interface OnError { void onError(@NonNull Exception e); }
}
