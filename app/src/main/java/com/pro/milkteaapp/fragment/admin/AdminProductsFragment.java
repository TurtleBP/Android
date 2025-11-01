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

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.activity.admin.AdminMainActivity;
import com.pro.milkteaapp.activity.admin.ProductEditorActivity;
import com.pro.milkteaapp.adapter.admin.AdminProductAdapter;
import com.pro.milkteaapp.handler.AddActionHandler;
import com.pro.milkteaapp.models.Products;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Fragment quản trị sản phẩm: KHÔNG dùng ViewModel/Repository, gọi Firestore trực tiếp. */
public class AdminProductsFragment extends Fragment
        implements AdminProductAdapter.OnProductActionListener,
        AdminMainActivity.ScrollToTop,
        AdminMainActivity.SupportsRefresh,
        AddActionHandler {

    // UI
    private RecyclerView recyclerView;
    private LinearLayout emptyState;
    private LinearProgressIndicator progressBar;
    private TextInputEditText searchEditText;
    private SwipeRefreshLayout swipeRefresh;

    // Dữ liệu & adapter
    private final List<Products> productList = new ArrayList<>();
    private AdminProductAdapter adapter;
    private String lastSearch = "";

    // Quyền
    private boolean isAdminRole = false;

    // Firestore
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private ListenerRegistration realtimeReg;

    // Cờ để biết view đã được inflate chưa
    private boolean viewInited = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isAdminRole = isAdmin();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_products, container, false);

        if (!isAdminRole) {
            Toast.makeText(requireContext(), "Bạn không có quyền truy cập (admin-only).", Toast.LENGTH_SHORT).show();
            if (getActivity() != null) {
                getActivity().getOnBackPressedDispatcher().onBackPressed();
            }
            return new LinearLayout(requireContext());
        }

        bindViews(view);
        setupRecyclerView();
        setupSearch();
        setupSwipeToRefresh();
        attachMenuProvider();

        // Tải một lần ban đầu
        loadOnce();

        viewInited = true;

        return view;
    }

    private void bindViews(View v) {
        recyclerView    = v.findViewById(R.id.productsRecyclerView);
        emptyState      = v.findViewById(R.id.emptyState);
        progressBar     = v.findViewById(R.id.progressBar);
        searchEditText  = v.findViewById(R.id.searchEditText);
        swipeRefresh    = v.findViewById(R.id.swipeRefresh);

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
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
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

    private void attachMenuProvider() {
        requireActivity().addMenuProvider(new MenuProvider() {
            @Override public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
                inflater.inflate(R.menu.menu_admin_products, menu);
            }
            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                if (item.getItemId() == R.id.action_sort) {
                    showSortDialog();
                    return true;
                }
                if (item.getItemId() == R.id.action_refresh) {
                    refresh();
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    // ====== Vòng đời: bật/tắt realtime ======
    @Override public void onStart() {
        super.onStart();
        startRealtimeIfReady();
    }

    @Override public void onStop() {
        super.onStop();
        stopRealtime();
    }

    private void startRealtimeIfReady() {
        if (!isAdminRole) return;
        if (!viewInited) return;
        startRealtime();
    }

    private void startRealtime() {
        stopRealtime();
        showLoading(true);
        realtimeReg = db.collection("products")
                .addSnapshotListener(realtimeListener);
    }

    private void stopRealtime() {
        if (realtimeReg != null) {
            realtimeReg.remove();
            realtimeReg = null;
        }
    }

    private final EventListener<QuerySnapshot> realtimeListener = (snap, e) -> {
        if (swipeRefresh != null && swipeRefresh.isRefreshing()) swipeRefresh.setRefreshing(false);
        if (e != null) {
            showLoading(false);
            toast("Lỗi realtime: " + e.getMessage());
            return;
        }
        productList.clear();
        if (snap != null) {
            for (DocumentSnapshot d : snap.getDocuments()) {
                Products p = d.toObject(Products.class);
                if (p != null) {
                    if (p.getId() == null || p.getId().trim().isEmpty()) {
                        p.setId(d.getId());
                    }
                    productList.add(p);
                }
            }
        }
        if (adapter != null) {
            filterProducts(lastSearch);
        }
        showLoading(false);
    };

    // ====== Tải một lần (pull) ======
    private void loadOnce() {
        if (!isAdded()) return;
        showLoading(true);
        db.collection("products").get()
                .addOnSuccessListener(snap -> {
                    productList.clear();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Products p = d.toObject(Products.class);
                        if (p != null) {
                            if (p.getId() == null || p.getId().trim().isEmpty()) {
                                p.setId(d.getId());
                            }
                            productList.add(p);
                        }
                    }
                    if (adapter != null) {
                        filterProducts(lastSearch);
                    }
                })
                .addOnFailureListener(e -> toast("Lỗi tải dữ liệu: " + e.getMessage()))
                .addOnCompleteListener(t -> showLoading(false));
    }

    // ====== CRUD trực tiếp Firestore ======
    private void deleteProductById(@NonNull String id) {
        showLoading(true);
        db.collection("products").document(id)
                .delete()
                .addOnSuccessListener(r -> toast("Đã xóa sản phẩm."))
                .addOnFailureListener(e -> toast("Lỗi xóa: " + e.getMessage()))
                .addOnCompleteListener(t -> showLoading(false));
    }

    private void updateProductStock(@NonNull String productId, int newStock) {
        showLoading(true);
        db.collection("products").document(productId)
                .update("stock", newStock, "updatedAt", FieldValue.serverTimestamp())
                .addOnSuccessListener(r -> toast("Đã cập nhật tồn kho"))
                .addOnFailureListener(e -> toast("Lỗi cập nhật tồn kho: " + e.getMessage()))
                .addOnCompleteListener(t -> showLoading(false));
    }

    // ====== UI helpers ======
    private void filterProducts(String query) {
        if (adapter == null) return;
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            adapter.updateList(productList);
            checkEmptyState();
            return;
        }
        List<Products> filtered = new ArrayList<>();
        for (Products p : productList) {
            String name = safe(p.getName()).toLowerCase(Locale.ROOT);
            String cat  = safe(p.getCategory()).toLowerCase(Locale.ROOT);
            if (name.contains(q) || cat.contains(q)) {
                filtered.add(p);
            }
        }
        adapter.updateList(filtered);
        checkEmptyState();
    }

    private void checkEmptyState() {
        if (adapter == null) return;
        boolean isEmpty = adapter.getItemCount() == 0;
        if (emptyState != null) emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        if (recyclerView != null) recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void showLoading(boolean show) {
        if (progressBar != null) {
            progressBar.setIndeterminate(true);
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void showSortDialog() {
        final String[] options = {
                "Mới nhất", "Cũ nhất", "Giá tăng dần", "Giá giảm dần", "Tên A-Z", "Tồn kho ít nhất"
        };
        new AlertDialog.Builder(requireContext())
                .setTitle("Sắp xếp sản phẩm")
                .setItems(options, (d, which) -> {
                    if (adapter == null) return;
                    List<Products> current = new ArrayList<>(adapter.getCurrentList());

                    Comparator<Products> byPriceAsc =
                            Comparator.comparing(Products::getPrice, Comparator.nullsLast(Double::compare));
                    Comparator<Products> byPriceDesc = byPriceAsc.reversed();
                    Comparator<Products> byNameAsc =
                            Comparator.comparing(p -> safe(p.getName()).toLowerCase(Locale.ROOT));
                    Comparator<Products> byStockLeast =
                            Comparator.comparing(Products::getStock, Comparator.nullsLast(Integer::compare));

                    switch (which) {
                        case 0: /* Mới nhất → giữ nguyên */ break;
                        case 1: Collections.reverse(current); break;
                        case 2: current.sort(byPriceAsc); break;
                        case 3: current.sort(byPriceDesc); break;
                        case 4: current.sort(byNameAsc); break;
                        case 5: current.sort(byStockLeast); break;
                    }
                    adapter.updateList(current);
                    checkEmptyState();
                })
                .show();
    }

    // ====== Callbacks từ Activity/Adapter ======
    private void openAddProduct() {
        startActivity(new Intent(requireContext(), ProductEditorActivity.class));
    }

    @Override public void onAddAction() { openAddProduct(); }

    @Override
    public void onEditProduct(Products product) {
        Intent it = new Intent(requireContext(), ProductEditorActivity.class);
        it.putExtra(ProductEditorActivity.EXTRA_PRODUCT_ID, product.getId());
        startActivity(it);
    }

    @Override
    public void onDeleteProduct(Products product) {
        if (product == null || safe(product.getId()).isEmpty()) {
            toast("Không xác định được ID sản phẩm.");
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("Xóa sản phẩm")
                .setMessage("Bạn có chắc muốn xóa \"" + safe(product.getName()) + "\"?")
                .setPositiveButton("Xóa", (d, w) -> deleteProductById(product.getId()))
                .setNegativeButton("Hủy", null)
                .show();
    }

    @Override
    public void onEditStock(Products product) {
        showEditStockDialog(product);
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

        int current = Math.max(0, product.getStock() == null ? 0 : product.getStock());
        productName.setText(safe(product.getName()));
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
                toast("Số lượng không hợp lệ.");
                return;
            }
            int newStock = parseIntSafe(stockStr, current);
            String pid = safe(product.getId());
            if (pid.isEmpty()) {
                toast("Không xác định được ID sản phẩm.");
                return;
            }
            updateProductStock(pid, newStock);
            dialog.dismiss();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    @Override public void scrollToTop() {
        if (recyclerView != null) recyclerView.smoothScrollToPosition(0);
    }

    @Override public void refresh() {
        if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
        loadOnce();
    }

    private boolean isAdmin() {
        SharedPreferences p = requireContext().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        return "admin".equalsIgnoreCase(p.getString("role", "user"));
    }
    private void toast(String msg) { Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show(); }
    private String safe(String s) { return s == null ? "" : s; }
    private int parseIntSafe(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return fallback; }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopRealtime();
        recyclerView = null;
        searchEditText = null;
        swipeRefresh = null;
        emptyState = null;
        progressBar = null;
        viewInited = false;
    }
}
