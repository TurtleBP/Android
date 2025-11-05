package com.pro.milkteaapp.fragment.admin;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.activity.admin.AdminMainActivity;
import com.pro.milkteaapp.models.User;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AdminUsersFragment extends Fragment
        implements AdminMainActivity.ScrollToTop, AdminMainActivity.SupportsRefresh {

    // Views
    private SwipeRefreshLayout swipe;
    private RecyclerView recycler;
    private View loading, empty;
    private TextInputEditText searchEditText;

    // Data
    private final List<User> fullList = new ArrayList<>();
    private UsersAdapter adapter;

    // Firestore
    private FirebaseFirestore db;
    private ListenerRegistration listener;

    // Search state
    private String lastSearch = "";
    private long lastTypeTs = 0L;
    private static final long SEARCH_DEBOUNCE_MS = 250L;

    private boolean isViewAlive = false;

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

        adapter = new UsersAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.addItemDecoration(new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));
        recycler.setHasFixedSize(true);
        recycler.setAdapter(adapter);

        swipe.setOnRefreshListener(this::refresh);

        if (searchEditText != null) searchEditText.addTextChangedListener(textWatcher);

        showLoading(true);
        toggleEmpty(false);
        isViewAlive = true;
        return v;
    }

    @Override public void onStart() { super.onStart(); attachListener(); }

    @Override
    public void onStop() {
        super.onStop();
        detachListener();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isViewAlive = false;
        if (searchEditText != null) {
            searchEditText.removeCallbacks(searchRunnable);
            searchEditText.removeTextChangedListener(textWatcher);
        }
        recycler = null; swipe = null; loading = null; empty = null; searchEditText = null;
    }

    private void attachListener() {
        detachListener();
        showLoading(true);
        toggleEmpty(false);

        listener = db.collection("users")
                .orderBy("fullName", Query.Direction.ASCENDING)
                .addSnapshotListener((snap, err) -> {
                    if (!isAdded() || !isViewAlive) return;

                    showLoading(false);
                    if (swipe != null) swipe.setRefreshing(false);

                    if (err != null) {
                        adapter.submit(new ArrayList<>());
                        toggleEmpty(true);
                        toast("Lỗi tải dữ liệu: " + err.getMessage());
                        return;
                    }
                    if (snap == null) {
                        adapter.submit(new ArrayList<>());
                        toggleEmpty(true);
                        toast("Không có dữ liệu.");
                        return;
                    }

                    fullList.clear();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        String docId = d.getId();

                        // Map về User model (an toàn null)
                        User u = d.toObject(User.class);
                        if (u == null) u = new User();
                        if (u.getUid() == null || u.getUid().trim().isEmpty()) {
                            u.setUid(docId); // đảm bảo có uid
                        }

                        // fallback tên hiển thị cũ
                        if (u.getFullName() == null || u.getFullName().trim().isEmpty()) {
                            String legacy = d.getString("displayName");
                            if (legacy != null && !legacy.trim().isEmpty()) u.setFullName(legacy);
                        }

                        // role fallback từ isAdmin
                        String role = u.getRole();
                        if (role == null || role.trim().isEmpty()) {
                            Boolean isAdmin = d.getBoolean("isAdmin");
                            if (Boolean.TRUE.equals(isAdmin)) {
                                u.setRole("admin");
                            } else {
                                u.setRole("user");
                            }
                        }

                        // phone/address an toàn null
                        if (u.getPhone() == null) u.setPhone("");
                        if (u.getAddress() == null) u.setAddress("");
                        if (u.getEmail() == null) u.setEmail("");

                        fullList.add(u);
                    }

                    adapter.submit(new ArrayList<>(fullList));
                    toggleEmpty(fullList.isEmpty());
                    applyFilter(lastSearch);
                });
    }

    private void detachListener() {
        if (listener != null) { listener.remove(); listener = null; }
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
        List<User> filtered = new ArrayList<>();
        for (User u : fullList) {
            String name = safe(u.getFullName());
            String email = safe(u.getEmail());
            String role = safe(u.getRole());
            String phone = safe(u.getPhone());
            String address = safe(u.getAddress());
            if (name.contains(qq) || email.contains(qq) || role.contains(qq) || phone.contains(qq) || address.contains(qq)) {
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
    private void showEditDialog(User u) {
        if (!isAdded()) return;

        View content = LayoutInflater.from(getContext()).inflate(R.layout._dialog_edit_user, null, false);
        EditText etName    = content.findViewById(R.id.etName);
        EditText etEmail   = content.findViewById(R.id.etEmail);
        EditText etPhone   = content.findViewById(R.id.etPhone);
        EditText etAddress = content.findViewById(R.id.etAddress);
        Spinner spRole     = content.findViewById(R.id.spRole);

        // Prefill
        etName.setText(safeShow(u.getFullName()));
        etName.setSelection(etName.getText().length());
        etName.setFilters(new InputFilter[]{ new InputFilter.LengthFilter(60) });

        etEmail.setText(safeShow(u.getEmail()));
        etPhone.setText(safeShow(u.getPhone()));
        etAddress.setText(safeShow(u.getAddress()));

        List<String> roles = Arrays.asList("admin", "user", "manager", "staff");
        ArrayAdapter<String> roleAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, roles);
        spRole.setAdapter(roleAdapter);

        int idx = roles.indexOf(safeShow(u.getRole()).toLowerCase(Locale.ROOT));
        if (idx < 0) idx = roles.indexOf("user");
        spRole.setSelection(idx);

        new AlertDialog.Builder(getContext())
                .setTitle("Sửa người dùng")
                .setView(content)
                .setPositiveButton("Lưu", (d, which) -> {
                    String newName    = getTextOrEmpty(etName);
                    String newEmail   = getTextOrEmpty(etEmail);
                    String newPhone   = getTextOrEmpty(etPhone);
                    String newAddress = getTextOrEmpty(etAddress);
                    String newRole    = roles.get(spRole.getSelectedItemPosition());

                    if (TextUtils.isEmpty(newName)) {
                        toast("Tên không được để trống");
                        return;
                    }
                    if (!TextUtils.isEmpty(newEmail) && !android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
                        toast("Email không hợp lệ");
                        return;
                    }
                    updateUser(u.getUid(), newName, newEmail, newPhone, newAddress, newRole);
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void updateUser(String userId,
                            String newName,
                            String newEmail,
                            String newPhone,
                            String newAddress,
                            String newRole) {
        if (TextUtils.isEmpty(userId)) {
            toast("ID không hợp lệ");
            return;
        }
        showLoading(true);

        Map<String, Object> data = new HashMap<>();
        data.put("fullName", newName);
        data.put("email", newEmail);   // đổi ở Firestore, không đổi Firebase Auth
        data.put("phone", newPhone);
        data.put("address", newAddress);
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

    private void confirmDelete(User u) {
        if (!isAdded()) return;
        String nameOrEmail = !safeShow(u.getFullName()).isEmpty() ? u.getFullName() : safeShow(u.getEmail());
        new AlertDialog.Builder(getContext())
                .setTitle("Xoá người dùng")
                .setMessage("Bạn có chắc muốn xoá \"" + nameOrEmail + "\"?")
                .setPositiveButton("Xoá", (d, w) -> deleteUser(u.getUid()))
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void deleteUser(String userId) {
        if (TextUtils.isEmpty(userId)) {
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
    }

    private void toast(String m) {
        if (getContext() != null) Toast.makeText(getContext(), m, Toast.LENGTH_SHORT).show();
    }

    private String safe(String s) { return (s == null ? "" : s).trim().toLowerCase(Locale.ROOT); }
    private String safeShow(String s) { return s == null ? "" : s; }
    private String getTextOrEmpty(EditText et) { return et.getText() == null ? "" : et.getText().toString().trim(); }

    // ====== Search debounce ======
    private final Runnable searchRunnable = () -> {
        if (SystemClock.elapsedRealtime() - lastTypeTs < SEARCH_DEBOUNCE_MS - 5) return;
        applyFilter(lastSearch);
    };

    private final TextWatcher textWatcher = new TextWatcher() {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void afterTextChanged(Editable s) {}
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (searchEditText == null) return;
            lastTypeTs = SystemClock.elapsedRealtime();
            lastSearch = s == null ? "" : s.toString();
            searchEditText.removeCallbacks(searchRunnable);
            searchEditText.postDelayed(searchRunnable, SEARCH_DEBOUNCE_MS);
        }
    };

    // ===== Adapter =====
    private class UsersAdapter extends RecyclerView.Adapter<UserVH> {
        private final DiffUtil.ItemCallback<User> DIFF = new DiffUtil.ItemCallback<>() {
            @Override
            public boolean areItemsTheSame(@NonNull User oldItem, @NonNull User newItem) {
                String o = oldItem.getUid() == null ? "" : oldItem.getUid();
                String n = newItem.getUid() == null ? "" : newItem.getUid();
                return o.equals(n);
            }

            @Override
            public boolean areContentsTheSame(@NonNull User oldItem, @NonNull User newItem) {
                // so sánh các field hiển thị & chỉnh sửa
                return safeShow(oldItem.getFullName()).equals(safeShow(newItem.getFullName())) &&
                        safeShow(oldItem.getEmail()).equals(safeShow(newItem.getEmail())) &&
                        safeShow(oldItem.getRole()).equals(safeShow(newItem.getRole())) &&
                        safeShow(oldItem.getPhone()).equals(safeShow(newItem.getPhone())) &&
                        safeShow(oldItem.getAddress()).equals(safeShow(newItem.getAddress()));
            }
        };

        private final AsyncListDiffer<User> differ = new AsyncListDiffer<>(this, DIFF);

        UsersAdapter() { setHasStableIds(true); }

        void submit(List<User> newItems) { differ.submitList(newItems); }

        @Override public long getItemId(int position) {
            List<User> list = differ.getCurrentList();
            if (position < 0 || position >= list.size()) return RecyclerView.NO_ID;
            String id = list.get(position).getUid();
            return (id == null ? "" : id).hashCode();
        }

        @NonNull @Override
        public UserVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_user_row, parent, false);
            return new UserVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull UserVH h, int pos) {
            List<User> list = differ.getCurrentList();
            if (pos < 0 || pos >= list.size()) return;
            h.bind(list.get(pos));
        }

        @Override
        public int getItemCount() { return differ.getCurrentList().size(); }
    }

    private class UserVH extends RecyclerView.ViewHolder {
        private final TextView tvDisplayName, tvEmail, tvRole;
        private final TextView tvPhone, tvAddress;
        private final View btnEdit, btnDelete;

        public UserVH(@NonNull View itemView) {
            super(itemView);
            tvDisplayName = itemView.findViewById(R.id.tvDisplayName);
            tvEmail = itemView.findViewById(R.id.tvEmail);
            tvRole = itemView.findViewById(R.id.tvRole);
            tvPhone = itemView.findViewById(R.id.tvPhone);
            tvAddress = itemView.findViewById(R.id.tvAddress);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }

        void bind(User u) {
            final Context ctx = itemView.getContext();

            String displayName = safeShow(u.getFullName());
            if (displayName.isEmpty()) displayName = "(No name)";
            tvDisplayName.setText(displayName);

            String email = safeShow(u.getEmail());
            tvEmail.setText(email.isEmpty() ? "(no email)" : email);

            String phone = safeShow(u.getPhone());
            tvPhone.setText(phone.isEmpty() ? "(no phone)" : phone);

            String address = safeShow(u.getAddress());
            tvAddress.setText(address.isEmpty() ? "(no address)" : address);

            String role = safeShow(u.getRole()).trim().toLowerCase(Locale.ROOT);
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
