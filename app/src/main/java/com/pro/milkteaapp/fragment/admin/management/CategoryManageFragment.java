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
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.adapter.CategoryAdapter;
import com.pro.milkteaapp.handler.AddActionHandler;
import com.pro.milkteaapp.models.Category;
import com.pro.milkteaapp.viewmodel.AdminCategoriesViewModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class CategoryManageFragment extends Fragment implements AddActionHandler,
        CategoryAdapter.OnCategoryActionListener {

    // Views
    private RecyclerView recyclerView;
    private LinearProgressIndicator progressBar;
    private SwipeRefreshLayout swipeRefresh;
    private View emptyState;
    private FloatingActionButton fabAdd;
    private TextInputEditText edtSearch;
    private Button btnAddFirst;

    // Adapter & VM
    private CategoryAdapter adapter;
    private AdminCategoriesViewModel vm;

    // Data
    private final List<Category> list = new ArrayList<>();

    public CategoryManageFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_admin_category, container, false);

        // Bind views
        recyclerView = v.findViewById(R.id.recyclerViewCategories);
        progressBar  = v.findViewById(R.id.progressBar);
        swipeRefresh = v.findViewById(R.id.swipeRefresh);
        emptyState   = v.findViewById(R.id.emptyState);
        fabAdd       = v.findViewById(R.id.fabAdd);
        edtSearch    = v.findViewById(R.id.edtSearch);
        btnAddFirst  = v.findViewById(R.id.btnAddFirst);

        // Recycler + Adapter (admin mode = true)
        adapter = new CategoryAdapter(this, true);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setHasFixedSize(true);
        recyclerView.setAdapter(adapter);

        // VM
        vm = new ViewModelProvider(this, new AdminCategoriesViewModel.Factory())
                .get(AdminCategoriesViewModel.class);

        // Listeners
        if (fabAdd != null) fabAdd.setOnClickListener(_v -> onAddAction());
        if (btnAddFirst != null) btnAddFirst.setOnClickListener(_v -> onAddAction());
        if (swipeRefresh != null) {
            swipeRefresh.setOnRefreshListener(() -> {
                // listenAll là realtime nên chỉ cần dừng refresh
                swipeRefresh.setRefreshing(false);
            });
        }
        if (edtSearch != null) {
            edtSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void afterTextChanged(Editable s) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String q = s == null ? "" : s.toString().trim().toLowerCase(Locale.ROOT);
                    List<Category> filtered = new ArrayList<>();
                    for (Category it : list) {
                        String name = it.getName() == null ? "" : it.getName();
                        if (name.toLowerCase(Locale.ROOT).contains(q)) filtered.add(it);
                    }
                    adapter.submit(filtered);
                    updateEmptyState();
                }
            });
        }

        observeCategories();
        return v;
    }

    /** ================= Quan sát dữ liệu từ Firestore ================= */
    private void observeCategories() {
        showLoading(true);
        vm.listenAll().observe(getViewLifecycleOwner(), result -> {
            switch (result.status) {
                case LOADING:
                    showLoading(true);
                    break;
                case SUCCESS:
                    showLoading(false);
                    list.clear();
                    if (result.data != null) list.addAll(result.data);
                    // Áp dụng filter hiện tại (nếu có)
                    String q = edtSearch != null && edtSearch.getText() != null
                            ? edtSearch.getText().toString().trim().toLowerCase(Locale.ROOT)
                            : "";
                    if (q.isEmpty()) {
                        adapter.submit(list);
                    } else {
                        List<Category> filtered = new ArrayList<>();
                        for (Category it : list) {
                            String name = it.getName() == null ? "" : it.getName();
                            if (name.toLowerCase(Locale.ROOT).contains(q)) filtered.add(it);
                        }
                        adapter.submit(filtered);
                    }
                    updateEmptyState();
                    break;
                case ERROR:
                    showLoading(false);
                    toast("Lỗi tải danh mục: " + (result.error != null ? result.error.getMessage() : "unknown"));
                    break;
            }
        });
    }

    private void showLoading(boolean show) {
        if (progressBar != null)
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void updateEmptyState() {
        boolean isEmpty = adapter.getItemCount() == 0;
        if (emptyState != null) emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        if (recyclerView != null) recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    /** ================= Sự kiện thêm danh mục ================= */
    @Override
    public void onAddAction() {
        showCategoryDialog(null);
    }

    /** ================= Dialog thêm/sửa danh mục ================= */
    private void showCategoryDialog(@Nullable Category category) {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout._dialog_category, null);

        TextInputEditText edtName     = dialogView.findViewById(R.id.edtName);
        TextInputEditText edtImageUrl = dialogView.findViewById(R.id.edtImageUrl);
        ImageView imgPreview          = dialogView.findViewById(R.id.imgPreview);
        MaterialCheckBox cbActive     = dialogView.findViewById(R.id.cbActive);
        Button btnSave                = dialogView.findViewById(R.id.btnSave);
        Button btnCancel              = dialogView.findViewById(R.id.btnCancel);

        // Fill dữ liệu khi sửa
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

        // Preview ảnh khi người dùng nhập URL
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
            btnSave.setOnClickListener(v -> {
                String name = edtName != null && edtName.getText() != null
                        ? edtName.getText().toString().trim() : "";
                String imageUrl = edtImageUrl != null && edtImageUrl.getText() != null
                        ? edtImageUrl.getText().toString().trim() : "";
                boolean active = cbActive != null && cbActive.isChecked();

                if (name.isEmpty()) {
                    toast("Vui lòng nhập tên danh mục");
                    return;
                }

                // Chuẩn hoá URL rỗng -> null (document sạch hơn)
                if (imageUrl.isEmpty()) imageUrl = null;

                Map<String, Object> data = new HashMap<>();
                data.put("name", name);
                data.put("imageUrl", imageUrl);
                data.put("active", active);
                data.put("searchableName", name.toLowerCase(Locale.ROOT));

                if (category == null) {
                    // Thêm mới
                    vm.add(data).observe(getViewLifecycleOwner(), r -> {
                        switch (r.status) {
                            case SUCCESS:
                                toast("Đã thêm danh mục");
                                dialog.dismiss();
                                break;
                            case ERROR:
                                toast("Lỗi thêm: " + (r.error != null ? r.error.getMessage() : "unknown"));
                                break;
                        }
                    });
                } else {
                    // Cập nhật
                    vm.update(category.getId(), data).observe(getViewLifecycleOwner(), r -> {
                        switch (r.status) {
                            case SUCCESS:
                                toast("Đã cập nhật danh mục");
                                dialog.dismiss();
                                break;
                            case ERROR:
                                toast("Lỗi cập nhật: " + (r.error != null ? r.error.getMessage() : "unknown"));
                                break;
                        }
                    });
                }
            });
        }

        dialog.show();
    }

    /** ================= Sửa / Xóa ================= */
    @Override
    public void onEdit(Category category) {
        showCategoryDialog(category);
    }

    @Override
    public void onDelete(Category category) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Xóa danh mục")
                .setMessage("Bạn có chắc muốn xóa \"" + (category.getName() == null ? "" : category.getName()) + "\" không?")
                .setPositiveButton("Xóa", (d, w) -> {
                    vm.delete(category.getId()).observe(getViewLifecycleOwner(), r -> {
                        switch (r.status) {
                            case SUCCESS:
                                toast("Đã xóa danh mục");
                                break;
                            case ERROR:
                                toast("Lỗi xóa: " + (r.error != null ? r.error.getMessage() : "unknown"));
                                break;
                        }
                    });
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void toast(String msg) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }
}
