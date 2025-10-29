package com.pro.milkteaapp.service;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.pro.milkteaapp.models.Products;
import com.pro.milkteaapp.repository.ProductRepository;
import com.pro.milkteaapp.utils.Result;

import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Repository Firestore cho products: null-safe + coerce kiểu số */
public class ProductRepositoryImpl implements ProductRepository {

    private static final String TAG = "ProductRepo";
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    @Override
    public LiveData<Result<List<Products>>> listenAll() {
        MutableLiveData<Result<List<Products>>> live = new MutableLiveData<>(Result.loading());

        db.collection("products")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        live.setValue(Result.error(e));
                        return;
                    }
                    List<Products> out = new ArrayList<>();
                    if (snap != null) {
                        for (DocumentSnapshot d : snap.getDocuments()) {
                            Products p = mapDocToProduct(d);
                            if (p != null) out.add(p);
                        }
                    }
                    live.setValue(Result.success(out));
                });

        return live;
    }

    @Override
    public LiveData<Result<Products>> getById(String id) {
        MutableLiveData<Result<Products>> live = new MutableLiveData<>(Result.loading());

        db.collection("products").document(id)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc == null || !doc.exists()) {
                        live.setValue(Result.error(new IllegalStateException("Không tìm thấy sản phẩm")));
                        return;
                    }

                    // DEBUG: log các field thực tế
                    Log.d(TAG, "getById docId=" + doc.getId() + " data=" + doc.getData());

                    Products p = mapDocToProduct(doc);
                    live.setValue(Result.success(p));
                })
                .addOnFailureListener(err -> live.setValue(Result.error(err)));

        return live;
    }

    @Override
    public LiveData<Result<Void>> add(Map<String, Object> data) {
        MutableLiveData<Result<Void>> live = new MutableLiveData<>(Result.loading());

        Map<String, Object> payload = new HashMap<>(data);
        payload.remove("id"); // tránh lưu id trong doc

        Object nameObj = payload.get("name");
        if (nameObj instanceof String) {
            payload.put("searchableName", ((String) nameObj).toLowerCase());
        }

        payload.put("createdAt", FieldValue.serverTimestamp());
        payload.put("updatedAt", FieldValue.serverTimestamp());

        db.collection("products")
                .add(payload)
                .addOnSuccessListener(ref -> live.setValue(Result.success(null)))
                .addOnFailureListener(err -> live.setValue(Result.error(err)));

        return live;
    }

    @Override
    public LiveData<Result<Void>> update(String id, Map<String, Object> data) {
        MutableLiveData<Result<Void>> live = new MutableLiveData<>(Result.loading());

        Map<String, Object> payload = new HashMap<>(data);
        payload.remove("id");

        Object nameObj = payload.get("name");
        if (nameObj instanceof String && !payload.containsKey("searchableName")) {
            payload.put("searchableName", ((String) nameObj).toLowerCase());
        }
        payload.put("updatedAt", FieldValue.serverTimestamp());

        db.collection("products").document(id)
                .update(payload)
                .addOnSuccessListener(v -> live.setValue(Result.success(null)))
                .addOnFailureListener(err -> live.setValue(Result.error(err)));

        return live;
    }

    @Override
    public LiveData<Result<Void>> delete(String id) {
        MutableLiveData<Result<Void>> live = new MutableLiveData<>(Result.loading());

        db.collection("products").document(id)
                .delete()
                .addOnSuccessListener(v -> live.setValue(Result.success(null)))
                .addOnFailureListener(err -> live.setValue(Result.error(err)));

        return live;
    }

    /* ====================== Helper: Map Document → Products ====================== */

    private Products mapDocToProduct(DocumentSnapshot d) {
        if (d == null) return null;

        // Bắt đầu từ toObject để tận dụng mapping sẵn có
        Products p = d.toObject(Products.class);
        if (p == null) p = new Products();

        // Luôn ưu tiên docId
        p.setId(d.getId());

        // ---- String fields (alias & null-safe) ----
        if (p.getName() == null)        p.setName(s(d.getString("name")));
        if (p.getCategory() == null)    p.setCategory(s(firstNonEmpty(d.getString("category"),
                d.getString("categoryName"))));
        if (p.getImageUrl() == null)    p.setImageUrl(s(firstNonEmpty(d.getString("imageUrl"),
                d.getString("imgUrl"),
                d.getString("image"))));
        if (p.getDescription() == null) p.setDescription(s(d.getString("description")));
        if (p.getStatus() == null)      p.setStatus(s(d.getString("status")));
        if (p.getSearchableName() == null) {
            String nm = (p.getName() != null) ? p.getName() : d.getString("name");
            p.setSearchableName(s(nm).toLowerCase());
        }
        if (p.getCreatedByUid() == null)p.setCreatedByUid(s(d.getString("createdByUid")));

        // ---- Numeric fields (coerce Long/Double/String) ----
        // price
        if (p.getPrice() == null) {
            Double price = coerceDouble(d.get("price"));
            if (price != null) p.setPrice(price);
        }
        // stock
        if (p.getStock() == null) {
            Integer stock = coerceInt(d.get("stock"));
            if (stock != null) p.setStock(stock);
        }
        // soldCount
        if (p.getSoldCount() == null) {
            Integer sold = coerceInt(d.get("soldCount"));
            if (sold != null) p.setSoldCount(sold);
        }

        // ---- Dates (dùng Date thay vì Object để khớp model) ----
        Timestamp tc = d.getTimestamp("createdAt");
        if (tc != null) p.setCreatedAt(tc.toDate());

        Timestamp tu = d.getTimestamp("updatedAt");
        if (tu != null) p.setUpdatedAt(tu.toDate());

        return p;
    }

    private static String s(String v) { return v == null ? "" : v; }

    private static String firstNonEmpty(String... ss) {
        if (ss == null) return null;
        for (String x : ss) if (x != null && !x.trim().isEmpty()) return x;
        return null;
    }

    private static Double coerceDouble(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try {
            String s = v.toString().replaceAll("[^\\d.\\-]", "");
            if (s.isEmpty()) return null;
            return Double.parseDouble(s);
        } catch (Exception ignore) { return null; }
    }

    private static Integer coerceInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        try {
            String s = v.toString().replaceAll("[^\\d\\-]", "");
            if (s.isEmpty()) return null;
            return Integer.parseInt(s);
        } catch (Exception ignore) { return null; }
    }
}
