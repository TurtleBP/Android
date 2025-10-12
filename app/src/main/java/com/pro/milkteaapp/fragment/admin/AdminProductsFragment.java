package com.pro.milkteaapp.fragment.admin;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.activity.admin.AddProductActivity;
import com.pro.milkteaapp.activity.admin.AdminMainActivity;
import com.pro.milkteaapp.adapters.admin.AdminProductAdapter;
import com.pro.milkteaapp.handler.AddActionHandler;
import com.pro.milkteaapp.models.Products;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class AdminProductsFragment extends Fragment
        implements AdminProductAdapter.OnProductActionListener,
        AdminMainActivity.ScrollToTop,
        AdminMainActivity.SupportsRefresh,
        AddActionHandler {

    // Views
    private RecyclerView recyclerView;
    private LinearLayout emptyState;
    private LinearProgressIndicator progressBar;
    private TextInputEditText searchEditText;
    private SwipeRefreshLayout swipeRefresh;

    // Data & adapter
    private AdminProductAdapter adapter;
    private final List<Products> productList = new ArrayList<>();
    private String lastSearch = "";

    // Firestore
    private FirebaseFirestore db;
    private ListenerRegistration productListener;

    // State
    private boolean isAdminRole = false;

    // ===== Lifecycle =====
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = FirebaseFirestore.getInstance();
        isAdminRole = isAdmin(); // chốt role sớm
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_products, container, false);

        if (!isAdminRole) {
            Toast.makeText(requireContext(), "Bạn không có quyền truy cập (admin-only).", Toast.LENGTH_SHORT).show();
            if (getActivity() != null) getActivity().getOnBackPressedDispatcher().onBackPressed();
            return new LinearLayout(requireContext()); // tránh NPE
        }

        bindViews(view);
        setupRecyclerView();
        setupSearch();
        setupSwipeToRefresh();

        // Lần đầu load
        loadProducts(true);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        attachMenuProvider(); // thay setHasOptionsMenu
    }

    private void attachMenuProvider() {
        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menuInflater.inflate(R.menu.menu_admin_products, menu);
            }
            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                int id = item.getItemId();
                if (id == R.id.action_sort) {
                    showSortDialog();
                    return true;
                } else if (id == R.id.action_refresh) {
                    refresh();
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (productListener == null && isAdminRole) {
            loadProducts(false);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        detachListener();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        detachListener();
    }

    private void detachListener() {
        if (productListener != null) {
            productListener.remove();
            productListener = null;
        }
    }

    // ===== UI setup =====
    private void bindViews(View v) {
        recyclerView   = v.findViewById(R.id.productsRecyclerView);
        emptyState     = v.findViewById(R.id.emptyState);
        progressBar    = v.findViewById(R.id.progressBar);
        searchEditText = v.findViewById(R.id.searchEditText);
        swipeRefresh   = v.findViewById(R.id.swipeRefresh);

        Button btnAddFirstProduct = v.findViewById(R.id.btnAddFirstProduct);
        if (btnAddFirstProduct != null) btnAddFirstProduct.setOnClickListener(_v -> openAddProduct());
    }

    private void setupRecyclerView() {
        adapter = new AdminProductAdapter(productList, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setHasFixedSize(true);
        recyclerView.setAdapter(adapter);
    }

    private void setupSearch() {
        if (searchEditText == null) return;
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void afterTextChanged(Editable s) { }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                lastSearch = s == null ? "" : s.toString();
                filterProducts(lastSearch);
            }
        });
    }

    private void setupSwipeToRefresh() {
        if (swipeRefresh == null) return;
        swipeRefresh.setOnRefreshListener(this::refresh);
    }

    // ===== Data =====
    private void loadProducts(boolean firstLoad) {
        detachListener();
        showLoading(true);

        productListener = db.collection("products")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    showLoading(false);
                    if (swipeRefresh != null && swipeRefresh.isRefreshing()) {
                        swipeRefresh.setRefreshing(false);
                    }

                    if (error != null) {
                        showToast("Lỗi tải dữ liệu: " + error.getMessage());
                        return;
                    }
                    if (value == null) {
                        showToast("Không có dữ liệu.");
                        return;
                    }

                    productList.clear();
                    for (DocumentSnapshot doc : value.getDocuments()) {
                        Products p = doc.toObject(Products.class);
                        if (p != null) {
                            if (p.getName() == null)     p.setName("");
                            if (p.getCategory() == null) p.setCategory("");
                            if (p.getId() == null)       p.setId(doc.getId());
                            productList.add(p);
                        }
                    }

                    if (lastSearch != null && !lastSearch.trim().isEmpty()) {
                        filterProducts(lastSearch);
                    } else {
                        adapter.updateList(productList);
                        checkEmptyState();
                    }
                });
    }

    private void filterProducts(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            adapter.updateList(productList);
            checkEmptyState();
            return;
        }
        List<Products> filtered = new ArrayList<>();
        for (Products product : productList) {
            String name = safeStr(product.getName());
            String category = safeStr(product.getCategory());
            if (name.toLowerCase(Locale.ROOT).contains(q) ||
                    category.toLowerCase(Locale.ROOT).contains(q)) {
                filtered.add(product);
            }
        }
        adapter.updateList(filtered);
        checkEmptyState();
    }

    private void checkEmptyState() {
        boolean isEmpty = adapter.getItemCount() == 0;
        if (emptyState != null) emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        if (recyclerView != null) recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void showLoading(boolean show) {
        if (progressBar == null) return;
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    // ===== Actions =====
    private void openAddProduct() {
        if (!isAdminRole) {
            showToast("Chức năng này chỉ dành cho admin.");
            return;
        }
        startActivity(new Intent(requireContext(), AddProductActivity.class));
    }

    /** FAB ở tab "Sản phẩm" trong ManagementFragment sẽ gọi vào đây */
    public void onAddProduct() {
        openAddProduct();
    }

    /** Triển khai interface chung cho FAB */
    @Override
    public void onAddAction() {
        openAddProduct();
    }

    @Override
    public void onEditProduct(Products product) {
        if (product == null) return;
        Intent intent = new Intent(requireContext(), com.pro.milkteaapp.activity.admin.EditProductActivity.class);
        intent.putExtra("productId", product.getId());
        intent.putExtra("name", product.getName());
        intent.putExtra("category", product.getCategory());
        intent.putExtra("imageUrl", product.getImageUrl());
        intent.putExtra("description", product.getDescription());
        intent.putExtra("price", product.getPrice());
        startActivity(intent);
    }

    @Override
    public void onDeleteProduct(Products product) {
        if (getContext() == null || product == null) return;
        new AlertDialog.Builder(getContext())
                .setTitle("Xóa sản phẩm")
                .setMessage("Bạn có chắc muốn xóa '" + safeStr(product.getName()) + "'?")
                .setPositiveButton("Xóa", (dialog, which) -> deleteProduct(product))
                .setNegativeButton("Hủy", null)
                .show();
    }

    @Override
    public void onEditStock(Products product) {
        showEditStockDialog(product);
    }

    private void deleteProduct(Products product) {
        String id = product.getId();
        if (id == null || id.trim().isEmpty()) {
            showToast("Không xác định được ID sản phẩm.");
            return;
        }
        showLoading(true);
        db.collection("products").document(id)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    showToast("Đã xóa sản phẩm");
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    showToast("Lỗi xóa sản phẩm: " + e.getMessage());
                });
    }

    private void showEditStockDialog(Products product) {
        if (getContext() == null || product == null) return;

        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_edit_stock, null);
        androidx.appcompat.app.AlertDialog dialog =
                new androidx.appcompat.app.AlertDialog.Builder(getContext())
                        .setView(dialogView)
                        .create();

        android.widget.TextView productName = dialogView.findViewById(R.id.productName);
        android.widget.TextView stockQuantity = dialogView.findViewById(R.id.stockQuantity);
        android.widget.EditText editStock = dialogView.findViewById(R.id.editStock);
        Button btnDecrease = dialogView.findViewById(R.id.btnDecrease);
        Button btnIncrease = dialogView.findViewById(R.id.btnIncrease);
        Button btnCancel = dialogView.findViewById(R.id.btnCancel);
        Button btnSave = dialogView.findViewById(R.id.btnSave);

        int current = Math.max(0, product.getStock());
        productName.setText(safeStr(product.getName()));
        stockQuantity.setText(String.valueOf(current));
        editStock.setText(String.valueOf(current));

        btnDecrease.setOnClickListener(v -> {
            int cur = parseIntSafe(stockQuantity.getText().toString(), 0);
            if (cur > 0) cur--;
            stockQuantity.setText(String.valueOf(cur));
            editStock.setText(String.valueOf(cur));
        });

        btnIncrease.setOnClickListener(v -> {
            int cur = parseIntSafe(stockQuantity.getText().toString(), 0);
            cur++;
            stockQuantity.setText(String.valueOf(cur));
            editStock.setText(String.valueOf(cur));
        });

        editStock.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String t = s == null ? "" : s.toString().trim();
                if (!t.isEmpty()) stockQuantity.setText(t);
            }
        });

        btnSave.setOnClickListener(v -> {
            String stockStr = editStock.getText() == null ? "" : editStock.getText().toString().trim();
            if (stockStr.isEmpty()) {
                showToast("Số lượng không hợp lệ.");
                return;
            }
            int newStock = parseIntSafe(stockStr, current);
            updateProductStock(safeStr(product.getId()), newStock);
            dialog.dismiss();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void updateProductStock(String productId, int newStock) {
        if (productId == null || productId.trim().isEmpty()) {
            showToast("Không xác định được ID sản phẩm.");
            return;
        }
        showLoading(true);
        db.collection("products").document(productId)
                .update("stock", newStock)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    showToast("Đã cập nhật số lượng tồn kho");
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    showToast("Lỗi cập nhật: " + e.getMessage());
                });
    }

    // ===== Hỗ trợ =====
    private String safeStr(String s) { return s == null ? "" : s; }
    private void showToast(String message) {
        if (getContext() != null) Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }
    private boolean isAdmin() {
        SharedPreferences prefs = requireContext().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        String role = prefs.getString("role", "user");
        return role.trim().equalsIgnoreCase("admin");
    }
    private int parseIntSafe(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return fallback; }
    }

    // ===== ScrollToTop / Refresh =====
    @Override
    public void scrollToTop() {
        if (recyclerView != null) recyclerView.smoothScrollToPosition(0);
    }

    @Override
    public void refresh() {
        if (swipeRefresh != null && !swipeRefresh.isRefreshing()) {
            swipeRefresh.setRefreshing(true);
        }
        loadProducts(false);
    }
    // ===== Sort menu actions =====
    private void showSortDialog() {
        // Các tuỳ chọn sắp xếp
        final String[] sortOptions = {
                "Mới nhất",
                "Cũ nhất",
                "Giá tăng dần",
                "Giá giảm dần",
                "Tên A-Z",
                "Tồn kho ít nhất"
        };

        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("Sắp xếp sản phẩm")
                .setItems(sortOptions, (dialog, which) -> sortProducts(which))
                .show();
    }

    /**
     * sortType:
     * 0: Mới nhất (giữ nguyên vì query DESC theo createdAt)
     * 1: Cũ nhất
     * 2: Giá tăng dần
     * 3: Giá giảm dần
     * 4: Tên A-Z
     * 5: Tồn kho ít nhất
     */
    private void sortProducts(int sortType) {
        List<Products> current = new ArrayList<>(adapter.getCurrentList());

        switch (sortType) {
            case 0: // Mới nhất: giữ nguyên
                break;
            case 1: // Cũ nhất
                Collections.reverse(current);
                break;
            case 2: // Giá tăng dần
                current.sort(Comparator.comparingDouble(p -> p.getPrice())); // double primitive
                break;
            case 3: // Giá giảm dần
                current.sort((p1, p2) -> Double.compare(p2.getPrice(), p1.getPrice())); // double primitive
                break;
            case 4: // Tên A-Z
                current.sort((p1, p2) -> safeStr(p1.getName()).compareToIgnoreCase(safeStr(p2.getName())));
                break;
            case 5: // Tồn kho ít nhất
                current.sort(Comparator.comparingInt(Products::getStock)); // nếu getStock() là int
                break;
        }

        adapter.updateList(current);
        checkEmptyState();
    }
}
