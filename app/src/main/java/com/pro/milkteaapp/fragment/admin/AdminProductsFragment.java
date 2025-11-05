package com.pro.milkteaapp.fragment.admin;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.activity.admin.AdminMainActivity;
import com.pro.milkteaapp.activity.admin.ProductEditorActivity;
import com.pro.milkteaapp.adapter.admin.AdminProductAdapter;
import com.pro.milkteaapp.handler.AddActionHandler;
import com.pro.milkteaapp.models.Products;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fragment quản trị sản phẩm: KHÔNG dùng ViewModel/Repository, gọi Firestore trực tiếp.
 * Bản này KHÔNG inflate menu riêng để tránh trùng với menu của AdminMainActivity.
 */
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
        // KHÔNG attach menu provider ở đây nữa, để Activity lo menu

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
