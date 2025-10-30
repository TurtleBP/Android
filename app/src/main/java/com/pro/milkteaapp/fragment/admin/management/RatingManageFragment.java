package com.pro.milkteaapp.fragment.admin.management;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.*;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.*;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.adapter.admin.AdminRatingsAdapter;
import com.pro.milkteaapp.databinding.FragmentAdminRatingsBinding;

import java.util.*;

public class RatingManageFragment extends Fragment {

    private FragmentAdminRatingsBinding b;
    private FirebaseFirestore db;
    private ListenerRegistration reg;
    private AdminRatingsAdapter adapter;

    // cache user để đỡ gọi Firestore lặp lại
    private final Map<String, UserBrief> userCache = new HashMap<>();

    public RatingManageFragment(){}

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentAdminRatingsBinding.inflate(inflater, container, false);

        db = FirebaseFirestore.getInstance();

        b.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new AdminRatingsAdapter(this::onDeleteClicked);
        b.recycler.setAdapter(adapter);

        b.swipeRefresh.setOnRefreshListener(this::reload);

        showLoading(true);
        listenData(); // realtime
        return b.getRoot();
    }

    private void reload() {
        if (reg != null) { reg.remove(); reg = null; }
        listenData();
    }

    private void listenData() {
        Query q = db.collection("orders")
                .whereGreaterThan("rating", 0)
                .orderBy("ratedAt", Query.Direction.DESCENDING)
                .limit(200);

        reg = q.addSnapshotListener((snap, e) -> {
            if (!isAdded() || b == null) return;

            b.swipeRefresh.setRefreshing(false);
            showLoading(false);

            if (e != null) {
                showEmpty(true);
                Toast.makeText(requireContext(), "Lỗi tải: " + String.valueOf(e), Toast.LENGTH_LONG).show();
                return;
            }
            if (snap == null || snap.size() == 0) {
                adapter.submitList(Collections.emptyList());
                showEmpty(true);
                return;
            }

            List<DocumentSnapshot> docs = snap.getDocuments();
            List<AdminRatingsAdapter.Item> items = new ArrayList<>(docs.size());

            for (DocumentSnapshot d : docs) {
                String orderId = d.getId();
                Double rating  = toDouble(d.get("rating"));
                if (rating == null || rating <= 0) continue;

                // ƯU TIÊN dùng denormalized fields
                String nameDenorm   = asStr(d.get("userName"));
                String avatarDenorm = asStr(d.get("userAvatar"));

                // Lấy userId: ưu tiên field "userId", nếu trống thì fallback "ratedBy"
                String userId  = firstNonEmpty(asStr(d.get("userId")), asStr(d.get("ratedBy")));
                String comment = asStr(d.get("review"));
                Timestamp ts   = d.getTimestamp("ratedAt");
                long when      = ts == null ? 0L : ts.toDate().getTime();

                items.add(new AdminRatingsAdapter.Item(
                        orderId,
                        userId,
                        // nếu đã có tên denorm thì dùng ngay, không cần enrich
                        firstNonEmpty(nameDenorm, "Người dùng"),
                        avatarDenorm,
                        rating,
                        comment,
                        when
                ));
            }

            adapter.submitList(items);
            showEmpty(items.isEmpty());

            // Enrich cho những mục còn thiếu tên/avatar
            b.recycler.post(() -> {
                for (int i = 0; i < items.size(); i++) {
                    final int pos = i;
                    AdminRatingsAdapter.Item it = items.get(pos);

                    // nếu đã có tên denorm thì bỏ qua
                    if (!TextUtils.isEmpty(it.userName) && !"Người dùng".equals(it.userName)) continue;

                    if (TextUtils.isEmpty(it.userId)) continue;

                    UserBrief cached = userCache.get(it.userId);
                    if (cached != null) {
                        applyUserBrief(pos, cached);
                        continue;
                    }

                    db.collection("users").document(it.userId).get()
                            .addOnSuccessListener(u -> {
                                UserBrief br = new UserBrief(
                                        firstNonEmpty(
                                                u.getString("displayName"),
                                                u.getString("fullName"),
                                                u.getString("name"),
                                                emailPrefix(u.getString("email")),
                                                "Người dùng"
                                        ),
                                        firstNonEmpty(u.getString("avatar"), u.getString("photoUrl"))
                                );
                                userCache.put(it.userId, br);
                                applyUserBrief(pos, br);
                            });
                }
            });
        });
    }

    private void applyUserBrief(int position, @NonNull UserBrief br) {
        if (!isAdded() || b == null) return;
        List<AdminRatingsAdapter.Item> cur = adapter.getCurrentList();
        if (position < 0 || position >= cur.size()) return;

        AdminRatingsAdapter.Item old = cur.get(position);
        AdminRatingsAdapter.Item upd = new AdminRatingsAdapter.Item(
                old.orderId, old.userId, br.name, br.avatar, old.stars, old.comment, old.ratedAtMs
        );

        List<AdminRatingsAdapter.Item> clone = new ArrayList<>(cur);
        clone.set(position, upd);
        adapter.submitList(clone);
    }

    private void onDeleteClicked(@NonNull AdminRatingsAdapter.Item it) {
        if (!isAdded() || b == null) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("Xoá đánh giá?")
                .setMessage("Xoá rating của đơn #" + it.orderId + " (không xoá đơn hàng).")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) -> doDeleteRating(it.orderId))
                .show();
    }

    /** Xoá rating: reset các trường đánh giá; giữ nguyên userId/ratedBy để history vẫn còn quy chiếu */
    private void doDeleteRating(@NonNull String orderId) {
        showLoading(true);
        Map<String, Object> upd = new HashMap<>();
        upd.put("rating", 0);
        upd.put("review", FieldValue.delete());
        upd.put("ratedAt", FieldValue.delete());
        // KHÔNG xóa userId / ratedBy / userName / userAvatar để tiện debug & audit

        db.collection("orders").document(orderId)
                .update(upd)
                .addOnSuccessListener(v -> {
                    showLoading(false);
                    Toast.makeText(requireContext(), "Đã xoá đánh giá của đơn #" + orderId, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(err -> {
                    showLoading(false);
                    Toast.makeText(requireContext(), "Lỗi xoá: " + String.valueOf(err), Toast.LENGTH_LONG).show();
                });
    }

    private void showLoading(boolean show) {
        b.progress.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) b.empty.setVisibility(View.GONE);
    }

    private void showEmpty(boolean empty) {
        b.empty.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onDestroyView() {
        if (reg != null) { reg.remove(); reg = null; }
        b = null;
        super.onDestroyView();
    }

    // ===== Helpers =====
    private static Double toDouble(Object o) {
        if (o instanceof Double) return (Double) o;
        if (o instanceof Long)   return ((Long) o).doubleValue();
        if (o instanceof Integer)return ((Integer) o).doubleValue();
        return null;
    }
    private static String asStr(Object o) { return o == null ? null : String.valueOf(o); }

    private static String firstNonEmpty(String... arr) {
        if (arr == null) return null;
        for (String s : arr) if (s != null && !s.trim().isEmpty()) return s.trim();
        return null;
    }
    private static String emailPrefix(String email) {
        if (email == null) return null;
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    private static class UserBrief {
        final String name;
        final String avatar; // URL hoặc tên drawable
        UserBrief(String name, String avatar) { this.name = name; this.avatar = avatar; }
    }
    @Override
    public void onResume() {
        super.onResume();
        reload();
    }
}
