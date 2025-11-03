package com.pro.milkteaapp.repository;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.*;
import com.pro.milkteaapp.SessionManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Quản lý đọc/ghi hồ sơ user trong Firestore.
 * Hỗ trợ: session UID (USRxxxxx) → email → fallback UID Firebase.
 */
public class UserProfileRepository {

    private final FirebaseAuth auth;
    private final FirebaseFirestore db;
    private final SessionManager session;

    public UserProfileRepository(@NonNull SessionManager session) {
        this.auth = FirebaseAuth.getInstance();
        this.db   = FirebaseFirestore.getInstance();
        this.session = session;
    }

    /**
     * Lấy DocumentReference đúng với user hiện tại.
     * - ưu tiên session uid (USRxxxxx)
     * - sau đó query theo email
     * - cuối cùng dùng firebase uid
     */
    public Task<DocumentReference> getCurrentUserDocRef() {
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) {
            return Tasks.forException(new IllegalStateException("Not logged in"));
        }

        // 1. từ session
        String sid = session.getUid();
        if (!TextUtils.isEmpty(sid) && sid.startsWith("USR")) {
            return Tasks.forResult(db.collection("users").document(sid));
        }

        // 2. từ email
        String email = u.getEmail();
        if (!TextUtils.isEmpty(email)) {
            return db.collection("users")
                    .whereEqualTo("email", email)
                    .limit(1)
                    .get()
                    .continueWith(task -> {
                        QuerySnapshot qs = task.getResult();
                        if (qs != null && !qs.isEmpty()) {
                            DocumentSnapshot doc = qs.getDocuments().get(0);
                            // lưu lại để lần sau dùng
                            session.setUid(doc.getId());
                            return doc.getReference();
                        }
                        // không có thì fallback
                        return db.collection("users").document(u.getUid());
                    });
        }

        // 3. fallback
        return Tasks.forResult(db.collection("users").document(u.getUid()));
    }

    /** Load dữ liệu user */
    public Task<DocumentSnapshot> loadProfile() {
        return getCurrentUserDocRef().continueWithTask(refTask -> {
            DocumentReference ref = refTask.getResult();
            return ref.get();
        });
    }

    /** Lưu các field văn bản */
    public Task<Void> saveProfileFields(@NonNull Map<String, Object> data) {
        return getCurrentUserDocRef().continueWithTask(refTask -> {
            DocumentReference ref = refTask.getResult();
            return ref.set(data, SetOptions.merge());
        });
    }

    /** Lưu avatar */
    public Task<Void> saveAvatar(@NonNull String avatarName) {
        Map<String, Object> upd = new HashMap<>();
        upd.put("avatar", avatarName);
        return saveProfileFields(upd).addOnSuccessListener(unused -> session.setAvatar(avatarName));
    }
}
