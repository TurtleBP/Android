package com.pro.milkteaapp.fragment.admin;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.*;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.*;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.*;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.activity.admin.AdminMainActivity;

import java.util.*;

public class AdminUsersFragment extends Fragment
        implements AdminMainActivity.ScrollToTop, AdminMainActivity.SupportsRefresh {

    // Views
    private SwipeRefreshLayout swipe;
    private RecyclerView recycler;
    private View loading, empty;
    private TextInputEditText searchEditText;

    // Data
    private final List<UserRow> fullList = new ArrayList<>();
    private final UsersAdapter adapter = new UsersAdapter();

    // Firestore
    private FirebaseFirestore db;
    private ListenerRegistration listener;

    // Search state
    private String lastSearch = "";
    private long lastTypeTs = 0L;
    private static final long SEARCH_DEBOUNCE_MS = 250L;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = FirebaseFirestore.getInstance();
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.activity_admin_users, container, false);

        swipe = v.findViewById(R.id.swipeRefresh);
        recycler = v.findViewById(R.id.recycler);
        loading = v.findViewById(R.id.loadingState);
        empty = v.findViewById(R.id.emptyState);
        searchEditText = v.findViewById(R.id.searchEditText);

        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.addItemDecoration(new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));
        recycler.setHasFixedSize(true);
        recycler.setAdapter(adapter);

        swipe.setOnRefreshListener(this::refresh);

        // Search + debounce
        if (searchEditText != null) {
            searchEditText.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void afterTextChanged(Editable s) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    lastTypeTs = SystemClock.elapsedRealtime();
                    lastSearch = s == null ? "" : s.toString();
                    searchEditText.removeCallbacks(searchRunnable);
                    searchEditText.postDelayed(searchRunnable, SEARCH_DEBOUNCE_MS);
                }
            });
        }

        showLoading(true);
        toggleEmpty(false);
        return v;
    }

    private final Runnable searchRunnable = () -> {
        if (SystemClock.elapsedRealtime() - lastTypeTs < SEARCH_DEBOUNCE_MS - 5) return;
        applyFilter(lastSearch);
    };

    @Override public void onStart() { super.onStart(); attachListener(); }
    @Override public void onStop()  { super.onStop();  detachListener(); }

    private void attachListener() {
        detachListener();
        showLoading(true);
        toggleEmpty(false);

        listener = db.collection("users")
                .orderBy("fullName", Query.Direction.ASCENDING)
                .addSnapshotListener((snap, err) -> {
                    showLoading(false);
                    if (swipe != null) swipe.setRefreshing(false);

                    if (err != null) {
                        adapter.submit(Collections.emptyList());
                        toggleEmpty(true);
                        toast("Lỗi tải dữ liệu: " + err.getMessage());
                        return;
                    }
                    if (snap == null) {
                        adapter.submit(Collections.emptyList());
                        toggleEmpty(true);
                        toast("Không có dữ liệu.");
                        return;
                    }

                    fullList.clear();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        String id = d.getId();

                        // Tên hiển thị: ưu tiên fullName -> displayName (fallback cũ)
                        String displayName = safeStr(d.getString("fullName"));
                        if (displayName.isEmpty()) displayName = safeStr(d.getString("displayName"));

                        String email = safeStr(d.getString("email"));

                        // Vai trò: role -> isAdmin -> "user"
                        String role = safeStr(d.getString("role"));
                        if (role.isEmpty()) {
                            Boolean isAdmin = d.getBoolean("isAdmin");
                            if (Boolean.TRUE.equals(isAdmin)) role = "admin";
                        }
                        if (role.isEmpty()) role = "user";

                        // NEW: phone & address
                        String phone   = safeStr(d.getString("phone"));
                        String address = safeStr(d.getString("address"));

                        // >>> Sửa lỗi: truyền đủ 6 tham số <<<
                        fullList.add(new UserRow(id, displayName, email, role, phone, address));
                    }

                    adapter.submit(new ArrayList<>(fullList));
                    toggleEmpty(fullList.isEmpty());
                    applyFilter(lastSearch);
                });
    }

    private void detachListener() {
        if (listener != null) {
            listener.remove();
            listener = null;
        }
    }

    @Override
    public void refresh() {
        if (swipe != null) swipe.setRefreshing(true);
        attachListener();
    }

    private void applyFilter(String q) {
        if (q == null) q = "";
        final String qq = q.trim().toLowerCase(Locale.ROOT);
        if (qq.isEmpty()) {
            adapter.submit(new ArrayList<>(fullList));
            toggleEmpty(fullList.isEmpty());
            return;
        }
        List<UserRow> filtered = new ArrayList<>();
        for (UserRow u : fullList) {
            if (u.displayName.toLowerCase(Locale.ROOT).contains(qq)
                    || u.email.toLowerCase(Locale.ROOT).contains(qq)
                    || (u.role != null && u.role.toLowerCase(Locale.ROOT).contains(qq))
                    || (u.phone != null && u.phone.toLowerCase(Locale.ROOT).contains(qq))       // lọc theo phone
                    || (u.address != null && u.address.toLowerCase(Locale.ROOT).contains(qq))   // lọc theo address
            ) {
                filtered.add(u);
            }
        }
        adapter.submit(filtered);
        toggleEmpty(filtered.isEmpty());
    }

    private void toggleEmpty(boolean showEmpty) {
        if (loading != null) loading.setVisibility(View.GONE);
        if (empty != null) empty.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        if (recycler != null) recycler.setVisibility(showEmpty ? View.GONE : View.VISIBLE);
    }

    private void showLoading(boolean show) {
        if (loading != null) loading.setVisibility(show ? View.VISIBLE : View.GONE);
        if (recycler != null) recycler.setVisibility(show ? View.GONE : View.VISIBLE);
        if (empty != null) empty.setVisibility(View.GONE);
    }

    @Override public void scrollToTop() {
        if (recycler != null) recycler.smoothScrollToPosition(0);
    }

    // ===== Edit dialog =====
    private void showEditDialog(UserRow u) {
        if (!isAdded()) return;

        View content = LayoutInflater.from(getContext()).inflate(R.layout._dialog_edit_user, null, false);
        EditText etName = content.findViewById(R.id.etName);
        Spinner spRole = content.findViewById(R.id.spRole);

        etName.setText(u.displayName);
        etName.setSelection(etName.getText().length());
        etName.setFilters(new InputFilter[]{ new InputFilter.LengthFilter(60) });

        List<String> roles = Arrays.asList("admin", "user", "manager", "staff");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, roles);
        spRole.setAdapter(adapter);

        int idx = roles.indexOf(u.role == null ? "user" : u.role.toLowerCase(Locale.ROOT));
        if (idx < 0) idx = roles.indexOf("user");
        spRole.setSelection(idx);

        new AlertDialog.Builder(getContext())
                .setTitle("Sửa người dùng")
                .setView(content)
                .setPositiveButton("Lưu", (d, which) -> {
                    String newName = etName.getText() == null ? "" : etName.getText().toString().trim();
                    String newRole = roles.get(spRole.getSelectedItemPosition());
                    if (newName.isEmpty()) {
                        toast("Tên không được để trống");
                        return;
                    }
                    updateUser(u.id, newName, newRole);
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void updateUser(String userId, String newName, String newRole) {
        if (userId == null || userId.isEmpty()) {
            toast("ID không hợp lệ");
            return;
        }
        showLoading(true);
        Map<String, Object> data = new HashMap<>();
        data.put("fullName", newName);
        data.put("role", newRole);

        db.collection("users").document(userId)
                .update(data)
                .addOnSuccessListener(v -> {
                    showLoading(false);
                    toast("Đã cập nhật người dùng");
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    toast("Cập nhật thất bại: " + e.getMessage());
                });
    }

    private void confirmDelete(UserRow u) {
        if (!isAdded()) return;
        new AlertDialog.Builder(getContext())
                .setTitle("Xoá người dùng")
                .setMessage("Bạn có chắc muốn xoá \"" + (u.displayName.isEmpty() ? u.email : u.displayName) + "\"?")
                .setPositiveButton("Xoá", (d, w) -> deleteUser(u.id))
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void deleteUser(String userId) {
        if (userId == null || userId.isEmpty()) {
            toast("ID không hợp lệ");
            return;
        }
        showLoading(true);
        db.collection("users").document(userId)
                .delete()
                .addOnSuccessListener(v -> {
                    showLoading(false);
                    toast("Đã xoá người dùng");
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    toast("Xoá thất bại: " + e.getMessage());
                });

        // Lưu ý: Xoá Firestore không xoá tài khoản Authentication.
        // Cần Admin SDK/server/Cloud Functions để xoá user auth.
    }

    private void toast(String m) {
        if (getContext() != null) Toast.makeText(getContext(), m, Toast.LENGTH_SHORT).show();
    }

    private String safeStr(String s) { return s == null ? "" : s; }

    // DTO
    private static class UserRow {
        final String id;
        final String displayName;
        final String email;
        final String role;
        final String phone;    // NEW
        final String address;  // NEW

        UserRow(String id, String displayName, String email, String role, String phone, String address) {
            this.id = id;
            this.displayName = displayName;
            this.email = email;
            this.role = role;
            this.phone = phone;
            this.address = address;
        }
    }

    // Adapter & ViewHolder
    private class UsersAdapter extends RecyclerView.Adapter<UserVH> {
        private final List<UserRow> items = new ArrayList<>();

        @SuppressLint("NotifyDataSetChanged")
        void submit(List<UserRow> newItems) {
            items.clear();
            if (newItems != null) items.addAll(newItems);
            notifyDataSetChanged();
        }

        @NonNull @Override
        public UserVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_user_row, parent, false);
            return new UserVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull UserVH h, int pos) {
            h.bind(items.get(pos));
        }

        @Override
        public int getItemCount() { return items.size(); }
    }

    private class UserVH extends RecyclerView.ViewHolder {
        private final TextView tvDisplayName, tvEmail, tvRole;
        private final TextView tvPhone, tvAddress; // NEW
        private final View btnEdit, btnDelete;

        public UserVH(@NonNull View itemView) {
            super(itemView);
            tvDisplayName = itemView.findViewById(R.id.tvDisplayName);
            tvEmail = itemView.findViewById(R.id.tvEmail);
            tvRole = itemView.findViewById(R.id.tvRole);
            tvPhone = itemView.findViewById(R.id.tvPhone);       // NEW
            tvAddress = itemView.findViewById(R.id.tvAddress);   // NEW
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }

        void bind(UserRow u) {
            final Context ctx = itemView.getContext();

            tvDisplayName.setText(u.displayName.isEmpty() ? "(No name)" : u.displayName);
            tvEmail.setText(u.email.isEmpty() ? "(no email)" : u.email);

            // NEW
            tvPhone.setText(u.phone == null || u.phone.isEmpty() ? "(no phone)" : u.phone);
            tvAddress.setText(u.address == null || u.address.isEmpty() ? "(no address)" : u.address);

            String role = (u.role == null ? "" : u.role).trim().toLowerCase(Locale.ROOT);
            if (role.isEmpty()) role = "user";
            tvRole.setText(role);

            if ("admin".equals(role)) {
                tvRole.setTextColor(ContextCompat.getColor(ctx, R.color.roleAdmin));
                tvRole.setBackground(ContextCompat.getDrawable(ctx, R.drawable.bg_role_admin));
            } else if ("user".equals(role)) {
                tvRole.setTextColor(ContextCompat.getColor(ctx, R.color.roleUser));
                tvRole.setBackground(ContextCompat.getDrawable(ctx, R.drawable.bg_role_user));
            } else {
                tvRole.setTextColor(ContextCompat.getColor(ctx, R.color.roleOther));
                tvRole.setBackground(ContextCompat.getDrawable(ctx, R.drawable.bg_role_other));
            }

            int hPad = dp(ctx, 8), vPad = dp(ctx, 4);
            tvRole.setPadding(hPad, vPad, hPad, vPad);

            btnEdit.setOnClickListener(v -> showEditDialog(u));
            btnDelete.setOnClickListener(v -> confirmDelete(u));
        }

        private int dp(Context ctx, int dp) {
            float d = ctx.getResources().getDisplayMetrics().density;
            return Math.round(dp * d);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        // inflater.inflate(R.menu.menu_admin_users, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        // if (item.getItemId() == R.id.action_refresh_users) { refresh(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
