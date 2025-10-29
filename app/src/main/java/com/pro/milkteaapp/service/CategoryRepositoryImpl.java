package com.pro.milkteaapp.service;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.pro.milkteaapp.models.Category;
import com.pro.milkteaapp.repository.CategoryRepository;
import com.pro.milkteaapp.utils.Result;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Triển khai Repository cho collection "categories" trên Firestore.
 * Hỗ trợ: listenAll, listenAllActive, getById, add, update, delete.
 */
public class CategoryRepositoryImpl implements CategoryRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    /* ===================== Lấy danh mục đang active ===================== */
    @Override
    public LiveData<Result<List<Category>>> listenAllActive() {
        MutableLiveData<Result<List<Category>>> live = new MutableLiveData<>(Result.loading());

        db.collection("categories")
                .whereEqualTo("active", true)
                .orderBy("name", Query.Direction.ASCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        live.setValue(Result.error(e));
                        return;
                    }
                    List<Category> out = new ArrayList<>();
                    if (snap != null) {
                        for (DocumentSnapshot d : snap.getDocuments()) {
                            Category c = toCategory(d);
                            if (c != null) out.add(c);
                        }
                    }
                    live.setValue(Result.success(out));
                });

        return live;
    }

    /* ===================== Lấy tất cả danh mục (admin) ===================== */
    @Override
    public LiveData<Result<List<Category>>> listenAll() {
        MutableLiveData<Result<List<Category>>> live = new MutableLiveData<>(Result.loading());

        db.collection("categories")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        live.setValue(Result.error(e));
                        return;
                    }
                    List<Category> out = new ArrayList<>();
                    if (snap != null) {
                        for (DocumentSnapshot d : snap.getDocuments()) {
                            Category c = toCategory(d);
                            if (c != null) out.add(c);
                        }
                    }
                    live.setValue(Result.success(out));
                });

        return live;
    }

    /* ===================== Lấy 1 danh mục ===================== */
    @Override
    public LiveData<Result<Category>> getById(String id) {
        MutableLiveData<Result<Category>> live = new MutableLiveData<>(Result.loading());

        db.collection("categories").document(id)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        live.setValue(Result.error(new IllegalStateException("Không tìm thấy danh mục")));
                        return;
                    }
                    live.setValue(Result.success(toCategory(doc)));
                })
                .addOnFailureListener(e -> live.setValue(Result.error(e)));

        return live;
    }

    /* ===================== Thêm danh mục ===================== */
    @Override
    public LiveData<Result<Void>> add(Map<String, Object> data) {
        MutableLiveData<Result<Void>> live = new MutableLiveData<>(Result.loading());
        Map<String, Object> payload = new HashMap<>(data);

        payload.remove("id");
        if (!payload.containsKey("active")) payload.put("active", true);
        payload.put("createdAt", FieldValue.serverTimestamp());
        payload.put("updatedAt", FieldValue.serverTimestamp());

        db.collection("categories")
                .add(payload)
                .addOnSuccessListener(ref -> live.setValue(Result.success(null)))
                .addOnFailureListener(e -> live.setValue(Result.error(e)));

        return live;
    }

    /* ===================== Cập nhật danh mục ===================== */
    @Override
    public LiveData<Result<Void>> update(String id, Map<String, Object> data) {
        MutableLiveData<Result<Void>> live = new MutableLiveData<>(Result.loading());
        Map<String, Object> payload = new HashMap<>(data);

        payload.remove("id");
        payload.put("updatedAt", FieldValue.serverTimestamp());

        db.collection("categories").document(id)
                .update(payload)
                .addOnSuccessListener(v -> live.setValue(Result.success(null)))
                .addOnFailureListener(e -> live.setValue(Result.error(e)));

        return live;
    }

    /* ===================== Xoá danh mục ===================== */
    @Override
    public LiveData<Result<Void>> delete(String id) {
        MutableLiveData<Result<Void>> live = new MutableLiveData<>(Result.loading());

        db.collection("categories").document(id)
                .delete()
                .addOnSuccessListener(v -> live.setValue(Result.success(null)))
                .addOnFailureListener(e -> live.setValue(Result.error(e)));

        return live;
    }

    /* ===================== Mapping helper ===================== */
    private Category toCategory(DocumentSnapshot d) {
        if (d == null) return null;

        Category c = d.toObject(Category.class);
        if (c == null) c = new Category();
        c.setId(d.getId());

        // Bảo đảm timestamp không null
        Timestamp created = d.getTimestamp("createdAt");
        Timestamp updated = d.getTimestamp("updatedAt");
        if (created != null) c.setCreatedAt(created);
        if (updated != null) c.setUpdatedAt(updated);

        return c;
    }
}
