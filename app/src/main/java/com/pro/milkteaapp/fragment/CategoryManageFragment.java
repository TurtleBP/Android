package com.pro.milkteaapp.fragment;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.*;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.*;

import com.pro.milkteaapp.R;
import com.pro.milkteaapp.adapters.CategoryAdapter;
import com.pro.milkteaapp.handler.AddActionHandler;
import com.pro.milkteaapp.models.Category;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CategoryManageFragment extends Fragment implements AddActionHandler,
        CategoryAdapter.OnCategoryActionListener {

    private FirebaseFirestore db;
    private ListenerRegistration reg;
    private CategoryAdapter adapter;

    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefresh;
    private androidx.recyclerview.widget.RecyclerView rv;
    private android.widget.LinearLayout emptyState;
    private TextInputEditText edtSearch;

    private final List<Category> full = new ArrayList<>();

    public CategoryManageFragment(){}

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_admin_category, container, false);

        db = FirebaseFirestore.getInstance();
        swipeRefresh = v.findViewById(R.id.swipeRefresh);
        rv = v.findViewById(R.id.rvCategories);
        emptyState = v.findViewById(R.id.emptyState);
        edtSearch = v.findViewById(R.id.edtSearch);
        android.view.View btnAddFirst = v.findViewById(R.id.btnAddFirst);
        com.google.android.material.floatingactionbutton.FloatingActionButton fabAdd = v.findViewById(R.id.fabAdd);

        adapter = new CategoryAdapter(this);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setHasFixedSize(true);
        rv.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(this::reload);
        if (btnAddFirst != null) btnAddFirst.setOnClickListener(_v -> onAddAction());
        if (fabAdd != null) fabAdd.setOnClickListener(_v -> onAddAction());

        if (edtSearch != null) {
            edtSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void afterTextChanged(Editable s) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filter(s == null ? "" : s.toString());
                }
            });
        }

        subscribe();
        return v;
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        if (reg != null) { reg.remove(); reg = null; }
    }

    private void subscribe() {
        setLoading(true);
        if (reg != null) { reg.remove(); reg = null; }
        reg = db.collection("categories")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, err) -> {
                    setLoading(false);
                    if (err != null) { toast("Lỗi tải: " + err.getMessage()); return; }
                    if (snap == null) { toast("Không có dữ liệu"); return; }

                    full.clear();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Category c = d.toObject(Category.class);
                        if (c != null) {
                            if (TextUtils.isEmpty(c.getId())) c.setId(d.getId());
                            if (c.getName() == null) c.setName("");
                            full.add(c);
                        }
                    }
                    filter(edtSearch == null ? "" : String.valueOf(edtSearch.getText()));
                });
    }

    private void reload() {
        swipeRefresh.setRefreshing(true);
        if (reg != null) { reg.remove(); reg = null; }
        subscribe();
        swipeRefresh.setRefreshing(false);
    }

    private void filter(String q) {
        String s = (q == null ? "" : q.trim().toLowerCase(Locale.ROOT));
        List<Category> result = new ArrayList<>();
        if (s.isEmpty()) {
            result.addAll(full);
        } else {
            for (Category c : full) {
                if ((c.getName() != null && c.getName().toLowerCase(Locale.ROOT).contains(s))) {
                    result.add(c);
                }
            }
        }
        adapter.submit(result);
        boolean empty = result.isEmpty();
        if (emptyState != null) emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (rv != null) rv.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void setLoading(boolean b) {
        if (swipeRefresh != null) swipeRefresh.setRefreshing(b);
    }

    private void toast(String m){ if (getContext()!=null) Toast.makeText(getContext(), m, Toast.LENGTH_SHORT).show(); }

    // ===== AddActionHandler =====
    @Override
    public void onAddAction() {
        showAddEditDialog(null);
    }

    // ===== OnCategoryActionListener =====
    @Override
    public void onEdit(Category c) { showAddEditDialog(c); }

    @Override
    public void onDelete(Category c) {
        if (c == null || TextUtils.isEmpty(c.getId())) return;

        // Kiểm tra ràng buộc: có product đang dùng category này không?
        db.collection("products")
                .whereEqualTo("category", c.getName())
                .limit(1)
                .get()
                .addOnSuccessListener(qs -> {
                    if (!qs.isEmpty()) {
                        new AlertDialog.Builder(requireContext())
                                .setTitle("Không thể xoá")
                                .setMessage("Đang có sản phẩm thuộc category \"" + c.getName() + "\". Hãy chuyển sản phẩm sang category khác hoặc đặt category này Inactive.")
                                .setPositiveButton("OK", null)
                                .show();
                    } else {
                        new AlertDialog.Builder(requireContext())
                                .setTitle("Xoá category")
                                .setMessage("Bạn chắc chắn muốn xoá \"" + c.getName() + "\"?")
                                .setPositiveButton("Xoá", (d,w) -> doDelete(c))
                                .setNegativeButton("Hủy", null)
                                .show();
                    }
                })
                .addOnFailureListener(e -> toast("Lỗi kiểm tra: " + e.getMessage()));
    }

    private void doDelete(Category c) {
        setLoading(true);
        db.collection("categories").document(c.getId())
                .delete()
                .addOnSuccessListener(v -> { setLoading(false); toast("Đã xoá"); })
                .addOnFailureListener(e -> { setLoading(false); toast("Lỗi xoá: " + e.getMessage()); });
    }

    private void showAddEditDialog(@Nullable Category editing) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout._dialog_category, null);
        TextInputEditText edtName = dialogView.findViewById(R.id.edtName);
        MaterialCheckBox cbActive = dialogView.findViewById(R.id.cbActive);
        if (editing != null) {
            edtName.setText(editing.getName());
            cbActive.setChecked(editing.isActive());
        } else {
            cbActive.setChecked(true);
        }

        AlertDialog dlg = new AlertDialog.Builder(requireContext())
                .setTitle(editing == null ? "Thêm category" : "Sửa category")
                .setView(dialogView)
                .setCancelable(false)
                .create();

        dialogView.findViewById(R.id.btnCancel).setOnClickListener(v -> dlg.dismiss());
        dialogView.findViewById(R.id.btnSave).setOnClickListener(v -> {
            String name = String.valueOf(edtName.getText()).trim();
            boolean active = cbActive.isChecked();

            if (TextUtils.isEmpty(name)) {
                edtName.setError("Không được để trống");
                return;
            }

            // Kiểm tra trùng tên (case-insensitive)
            db.collection("categories")
                    .whereEqualTo("name", name) // bạn có thể normalize về lowercase và lưu thêm field nameLower để tối ưu
                    .get()
                    .addOnSuccessListener(qs -> {
                        boolean exists = false;
                        for (DocumentSnapshot d : qs.getDocuments()) {
                            if (editing == null || !d.getId().equals(editing.getId())) {
                                exists = true; break;
                            }
                        }
                        if (exists) {
                            edtName.setError("Tên category đã tồn tại");
                            return;
                        }
                        if (editing == null) addCategory(name, active, dlg);
                        else updateCategory(editing.getId(), name, active, dlg);
                    })
                    .addOnFailureListener(e -> toast("Lỗi kiểm tra: " + e.getMessage()));
        });

        dlg.show();
    }

    private void addCategory(String name, boolean active, AlertDialog dlg) {
        setLoading(true);
        DocumentReference doc = db.collection("categories").document();
        Timestamp now = Timestamp.now();
        Category c = new Category(doc.getId(), name, active, now, now);

        doc.set(c)
                .addOnSuccessListener(v -> { setLoading(false); toast("Đã thêm category"); dlg.dismiss(); })
                .addOnFailureListener(e -> { setLoading(false); toast("Lỗi thêm: " + e.getMessage()); });
    }

    private void updateCategory(String id, String name, boolean active, AlertDialog dlg) {
        setLoading(true);
        db.collection("categories").document(id)
                .update("name", name, "active", active, "updatedAt", Timestamp.now())
                .addOnSuccessListener(v -> { setLoading(false); toast("Đã cập nhật"); dlg.dismiss(); })
                .addOnFailureListener(e -> { setLoading(false); toast("Lỗi cập nhật: " + e.getMessage()); });
    }
}
