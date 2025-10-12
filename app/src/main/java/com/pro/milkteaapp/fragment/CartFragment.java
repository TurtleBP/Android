package com.pro.milkteaapp.fragment;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.activity.MainActivity;
import com.pro.milkteaapp.adapters.CartAdapter;
import com.pro.milkteaapp.databinding.FragmentCartBinding;
import com.pro.milkteaapp.models.CartItem;
import com.pro.milkteaapp.utils.MoneyUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CartFragment - phiên bản hoàn chỉnh:
 * - Hiển thị danh sách giỏ hàng
 * - Tính tổng tiền
 * - Đặt hàng: tạo order PENDING lên Firestore và điều hướng về Activities -> Pending
 */
public class CartFragment extends Fragment implements CartAdapter.OnItemActionClickListener {

    private static final String TAG = "CartFragment";

    private FragmentCartBinding binding;
    private CartAdapter adapter;

    public static List<CartItem> cartItems = new ArrayList<>();

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCartBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated called");

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        setupRecyclerView();
        setupClickListeners();
        setupBackPressedHandler();
        updateTotal();

        debugViewHierarchy();
    }

    private void setupRecyclerView() {
        if (getContext() == null) return;
        binding.cartRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new CartAdapter(getContext(), cartItems, this);
        binding.cartRecyclerView.setAdapter(adapter);
        updateTotal();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void setupClickListeners() {
        binding.checkoutButton.setOnClickListener(v -> {
            if (getContext() == null) return;

            if (cartItems.isEmpty()) {
                Toast.makeText(getContext(), R.string.empty_cart, Toast.LENGTH_SHORT).show();
                return;
            }

            FirebaseUser user = auth.getCurrentUser();
            if (user == null) {
                Toast.makeText(getContext(), "Vui lòng đăng nhập trước khi đặt hàng", Toast.LENGTH_SHORT).show();
                return;
            }

            binding.checkoutButton.setEnabled(false);

            Map<String, Object> orderPayload = buildOrderPayload(user.getUid(), cartItems);

            db.collection("orders")
                    .add(orderPayload)
                    .addOnSuccessListener(docRef -> {
                        cartItems.clear();
                        if (adapter != null) adapter.notifyDataSetChanged();
                        updateTotal();

                        Toast.makeText(getContext(),
                                getString(R.string.order_placed_successfully),
                                Toast.LENGTH_SHORT).show();

                        Intent i = new Intent(requireContext(), MainActivity.class);
                        i.putExtra(MainActivity.EXTRA_TARGET_FRAGMENT, "activities:pending");
                        startActivity(i);
                    })
                    .addOnFailureListener(e -> Toast.makeText(getContext(),
                            "Lỗi đặt hàng: " + e.getMessage(),
                            Toast.LENGTH_LONG).show())
                    .addOnCompleteListener(task -> {
                        if (binding != null) binding.checkoutButton.setEnabled(true);
                    });
        });

        // ⬇️ Toolbar back: dùng cùng 1 hàm xử lý
        binding.toolbar.setNavigationOnClickListener(v -> handleBack());
    }

    private void setupBackPressedHandler() {
        // ⬇️ KHÔNG gọi dispatcher trong callback để tránh đệ quy
        // ⬇️ NEW: giữ callback để có thể điều khiển nếu cần
        OnBackPressedCallback backCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBack();
            }
        };
        requireActivity().getOnBackPressedDispatcher()
                .addCallback(getViewLifecycleOwner(), backCallback);
    }

    // ⬇️ Tất cả xử lý back dồn về đây — TUYỆT ĐỐI không gọi dispatcher ở đây
    private void handleBack() {
        if (getActivity() == null) return;

        // Pop back stack nếu còn
        if (getParentFragmentManager().popBackStackImmediate()) return;

        // Không còn fragment phía sau:
        // - Nếu app bạn có bottom nav và muốn quay về Home:
        if (getActivity() instanceof MainActivity) {
            // Ví dụ: quay về tab Home (tuỳ bạn hiện thực selectBottomNav)
            try {
                ((MainActivity) getActivity()).selectBottomNav(R.id.navigation_home);
                return;
            } catch (Throwable ignored) {}
        }

        // - Hoặc đơn giản là đóng Activity hiện tại
        getActivity().finish();
    }

    private Map<String, Object> buildOrderPayload(String userId, List<CartItem> items) {
        List<Map<String, Object>> itemMaps = new ArrayList<>();
        for (CartItem ci : items) {
            Map<String, Object> m = new HashMap<>();
            m.put("productId",  ci.getProductId());
            m.put("name",       ci.getName());
            m.put("price",      ci.getPrice());
            m.put("qty",        ci.getQuantity());
            m.put("imageUrl",   ci.getImageUrl());
            itemMaps.add(m);
        }

        Map<String, Object> order = new HashMap<>();
        order.put("userId", userId);
        order.put("status", "PENDING");
        order.put("items", itemMaps);
        order.put("total", calculateTotal());
        order.put("createdAt", FieldValue.serverTimestamp());
        return order;
    }

    private void debugViewHierarchy() {
        if (binding == null) {
            Log.e(TAG, "Binding is null");
            return;
        }
        Log.d(TAG, "=== DEBUG ===");
        Log.d(TAG, "Total TextView: " + binding.totalTextView);
        Log.d(TAG, "Current text: '" + binding.totalTextView.getText() + "'");
        Log.d(TAG, "=== END DEBUG ===");
    }

    private void updateTotal() {
        if (binding == null || getContext() == null) {
            Log.e(TAG, "Binding or Context is null");
            return;
        }
        double total = calculateTotal();
        String formattedTotal = MoneyUtils.formatVnd(total);
        Log.d(TAG, "Updating total: " + formattedTotal);
        binding.totalTextView.setText(formattedTotal);
        Log.d(TAG, "Text set to: " + formattedTotal);
        binding.checkoutButton.setEnabled(!cartItems.isEmpty());
    }

    private double calculateTotal() {
        double total = 0;
        for (CartItem item : cartItems) {
            total += item.getTotalPrice();
        }
        return total;
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume called");
        if (adapter != null) adapter.notifyDataSetChanged();
        updateTotal();
    }

    @Override
    public void onItemRemoved(int position) {
        if (getActivity() == null || isDetached()) return;
        if (position < 0 || position >= cartItems.size()) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("Xác nhận xóa")
                .setMessage("Bạn có chắc chắn muốn xóa sản phẩm này khỏi giỏ hàng không?")
                .setPositiveButton("Xóa", (dialog, which) -> {
                    cartItems.remove(position);
                    if (adapter != null) adapter.notifyItemRemoved(position);
                    updateTotal();
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "Đã xóa sản phẩm", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    @Override
    public void onQuantityIncreased(int position) {
        if (position >= 0 && position < cartItems.size()) {
            CartItem item = cartItems.get(position);
            item.increaseQuantity();
            if (adapter != null) adapter.notifyItemChanged(position);
            updateTotal();
        }
    }

    @Override
    public void onQuantityDecreased(int position) {
        if (position >= 0 && position < cartItems.size()) {
            CartItem item = cartItems.get(position);
            if (item.getQuantity() > 1) {
                item.decreaseQuantity();
                if (adapter != null) adapter.notifyItemChanged(position);
                updateTotal();
            } else {
                onItemRemoved(position);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        Log.d(TAG, "onDestroyView called");
    }
}
