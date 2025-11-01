package com.pro.milkteaapp.fragment.admin.management;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.adapter.CategoryAdapter;
import com.pro.milkteaapp.handler.AddActionHandler;
import com.pro.milkteaapp.models.Category;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Quản lý danh mục cho admin – bản không dùng ViewModel.
 * Truy cập Firestore trực tiếp.
 */
public class CategoryManageFragment extends Fragment implements
        AddActionHandler,
        CategoryAdapter.OnCategoryActionListener {

    // Views
    private androidx.recyclerview.widget.RecyclerView recyclerView;
    private LinearProgressIndicator progressBar;
    private SwipeRefreshLayout swipeRefresh;
    private View emptyState;
    private FloatingActionButton fabAdd;
    private TextInputEditText edtSearch;
    private Button btnAddFirst;

    // Firebase
    private FirebaseFirestore db;
    private CollectionReference colCategories;
    private ListenerRegistration reg; // để remove khi onDestroyView

    // Adapter + data
    private CategoryAdapter adapter;
    private final List<Category> list = new ArrayList<>();

    public CategoryManageFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_admin_category, container, false);

        // init firebase
        db = FirebaseFirestore.getInstance();
        colCategories = db.collection("categories");

        // bind views
        recyclerView = v.findViewById(R.id.recyclerViewCategories);
        progressBar  = v.findViewById(R.id.progressBar);
        swipeRefresh = v.findViewById(R.id.swipeRefresh);
        emptyState   = v.findViewById(R.id.emptyState);
        fabAdd       = v.findViewById(R.id.fabAdd);
        edtSearch    = v.findViewById(R.id.edtSearch);
        btnAddFirst  = v.findViewById(R.id.btnAddFirst);

        // adapter (admin mode = true)
        adapter = new CategoryAdapter(this, true);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setHasFixedSize(true);
        recyclerView.setAdapter(adapter);

        // btn add
        if (fabAdd != null) fabAdd.setOnClickListener(_v -> onAddAction());
        if (btnAddFirst != null) btnAddFirst.setOnClickListener(_v -> onAddAction());

        // swipe refresh: vì mình nghe realtime nên chỉ tắt refresh
        if (swipeRefresh != null) {
            swipeRefresh.setOnRefreshListener(() -> swipeRefresh.setRefreshing(false));
        }

        // search
        if (edtSearch != null) {
            edtSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void afterTextChanged(Editable s) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    applyFilter(s == null ? "" : s.toString().trim().toLowerCase(Locale.ROOT));
                }
            });
        }

        listenCategoriesRealtime();
        return v;
    }

    /**
     * Nghe realtime tất cả danh mục (giống vm.listenAll())
     * Sort theo createdAt DESC, nếu doc cũ không có createdAt thì vẫn add vô
     */
    private void listenCategoriesRealtime() {
        showLoading(true);
        reg = colCategories
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (!isAdded()) return;

                    if (e != null) {
                        showLoading(false);
                        toast("Lỗi tải danh mục: " + e.getMessage());
                        return;
                    }
                    list.clear();
                    if (snap != null) {
                        for (DocumentSnapshot d : snap.getDocuments()) {
                            Category c = d.toObject(Category.class);
                            if (c == null) c = new Category();
                            // đảm bảo id
                            c.setId(d.getId());
                            list.add(c);
                        }
                    }
                    showLoading(false);

                    // áp dụng filter nếu đang search
                    String q = edtSearch != null && edtSearch.getText() != null
                            ? edtSearch.getText().toString().trim().toLowerCase(Locale.ROOT)
                            : "";
                    if (q.isEmpty()) {
                        adapter.submit(list);
                    } else {
                        applyFilter(q);
                    }
                    updateEmptyState();
                });
    }

    private void applyFilter(String q) {
        if (q == null) q = "";
        q = q.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            adapter.submit(list);
            updateEmptyState();
            return;
        }
        List<Category> filtered = new ArrayList<>();
        for (Category it : list) {
            String name = it.getName() == null ? "" : it.getName();
            if (name.toLowerCase(Locale.ROOT).contains(q)) {
                filtered.add(it);
            }
        }
        adapter.submit(filtered);
        updateEmptyState();
    }

    private void showLoading(boolean show) {
        if (progressBar != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void updateEmptyState() {
        boolean isEmpty = adapter.getItemCount() == 0;
        if (emptyState != null) emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        if (recyclerView != null) recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    /* ================= AddActionHandler ================= */
    @Override
    public void onAddAction() {
        showCategoryDialog(null);
    }

    /* ================= Dialog thêm/sửa danh mục ================= */
    private void showCategoryDialog(@Nullable Category category) {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout._dialog_category, null);

        TextInputEditText edtName     = dialogView.findViewById(R.id.edtName);
        TextInputEditText edtImageUrl = dialogView.findViewById(R.id.edtImageUrl);
        ImageView imgPreview          = dialogView.findViewById(R.id.imgPreview);
        MaterialCheckBox cbActive     = dialogView.findViewById(R.id.cbActive);
        Button btnSave                = dialogView.findViewById(R.id.btnSave);
        Button btnCancel              = dialogView.findViewById(R.id.btnCancel);

        // nếu sửa
        if (category != null) {
            if (edtName != null) edtName.setText(category.getName());
            if (edtImageUrl != null) edtImageUrl.setText(category.getImageUrl());
            if (cbActive != null) cbActive.setChecked(category.isActive());

            Glide.with(requireContext())
                    .load(category.getImageUrl())
                    .placeholder(R.drawable.milktea)
                    .error(R.drawable.milktea)
                    .into(imgPreview);
        }

        // preview ảnh
        if (edtImageUrl != null) {
            edtImageUrl.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void afterTextChanged(Editable s) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String url = s == null ? "" : s.toString().trim();
                    if (!url.isEmpty()) {
                        Glide.with(requireContext()).load(url)
                                .placeholder(R.drawable.milktea)
                                .error(R.drawable.milktea)
                                .into(imgPreview);
                    } else {
                        imgPreview.setImageResource(R.drawable.milktea);
                    }
                }
            });
        }

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setCancelable(false)
                .create();

        if (btnCancel != null) btnCancel.setOnClickListener(v -> dialog.dismiss());

        if (btnSave != null) {
            AlertDialog finalDialog = dialog;
            btnSave.setOnClickListener(v -> {
                String name = edtName != null && edtName.getText() != null
                        ? edtName.getText().toString().trim()
                        : "";
                String imageUrl = edtImageUrl != null && edtImageUrl.getText() != null
                        ? edtImageUrl.getText().toString().trim()
                        : "";
                boolean active = cbActive != null && cbActive.isChecked();

                if (name.isEmpty()) {
                    toast("Vui lòng nhập tên danh mục");
                    return;
                }
                if (imageUrl.isEmpty()) imageUrl = null;

                Map<String, Object> data = new HashMap<>();
                data.put("name", name);
                data.put("imageUrl", imageUrl);
                data.put("active", active);
                data.put("searchableName", name.toLowerCase(Locale.ROOT));

                if (category == null) {
                    // thêm mới
                    addCategory(data, finalDialog);
                } else {
                    // cập nhật
                    updateCategory(category.getId(), data, finalDialog);
                }
            });
        }

        dialog.show();
    }

    /* ================= CRUD trực tiếp Firestore ================= */

    private void addCategory(Map<String, Object> data, AlertDialog dialog) {
        // thêm các field hệ thống giống repo làm
        data.put("createdAt", FieldValue.serverTimestamp());
        colCategories.add(data)
                .addOnSuccessListener(doc -> {
                    toast("Đã thêm danh mục");
                    dialog.dismiss();
                })
                .addOnFailureListener(e ->
                        toast("Lỗi thêm: " + e.getMessage())
                );
    }

    private void updateCategory(String id, Map<String, Object> data, AlertDialog dialog) {
        colCategories.document(id)
                .update(data)
                .addOnSuccessListener(unused -> {
                    toast("Đã cập nhật danh mục");
                    dialog.dismiss();
                })
                .addOnFailureListener(e ->
                        toast("Lỗi cập nhật: " + e.getMessage())
                );
    }

    private void deleteCategory(String id) {
        colCategories.document(id)
                .delete()
                .addOnSuccessListener(unused -> toast("Đã xóa danh mục"))
                .addOnFailureListener(e -> toast("Lỗi xóa: " + e.getMessage()));
    }

    /* ================= callback từ adapter ================= */

    @Override
    public void onEdit(Category category) {
        showCategoryDialog(category);
    }

    @Override
    public void onDelete(Category category) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Xóa danh mục")
                .setMessage("Bạn có chắc muốn xóa \"" + (category.getName() == null ? "" : category.getName()) + "\" không?")
                .setPositiveButton("Xóa", (d, w) -> deleteCategory(category.getId()))
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void toast(String msg) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (reg != null) {
            reg.remove();
            reg = null;
        }
    }
}
