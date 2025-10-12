package com.pro.milkteaapp.fragment;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.firebase.firestore.FirebaseFirestore;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.activity.MainActivity;
import com.pro.milkteaapp.models.CartItem;
import com.pro.milkteaapp.models.Products;
import com.pro.milkteaapp.utils.ImageUtils;
import com.pro.milkteaapp.utils.MoneyUtils;

public class ProductDetailFragment extends Fragment {

    public static final String ARG_PRODUCT_ID = "product_id";

    // Views
    private ImageView milkTeaImage;
    private TextView nameTextView, priceTextView, descriptionTextView, totalPriceTextView, quantityTextView;
    private RadioGroup sizeRadioGroup;
    private RadioButton radioMedium;
    private MaterialCheckBox toppingCheckBox;
    private MaterialButton addToCartButton;
    private View decrementButton, incrementButton;
    private Toolbar toolbar;
    private CollapsingToolbarLayout collapsingToolbar;

    private Products product;
    private int quantity = 1;

    // Tuỳ chọn
    private String selectedSize = "Vừa";      // mặc định
    private String selectedTopping = "Không"; // mặc định

    public static ProductDetailFragment newInstance(@NonNull String productId) {
        Bundle args = new Bundle();
        args.putString(ARG_PRODUCT_ID, productId);
        ProductDetailFragment f = new ProductDetailFragment();
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_product_detail, container, false);
    }

    @SuppressLint("StringFormatInvalid")
    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);
        bindViews(v);
        setupToolbar();

        radioMedium.setChecked(true);
        quantityTextView.setText(String.valueOf(quantity));

        setupOptionListeners();
        setupQuantityListeners();

        addToCartButton.setOnClickListener(view -> {
            if (product == null) return;

            selectedTopping = toppingCheckBox.isChecked() ? "Trân châu trắng" : "Không";

            // Gộp số lượng theo (productId + size + topping)
            int existed = findExistingCartIndex(product.getId(), selectedSize, selectedTopping);
            if (existed >= 0) {
                CartFragment.cartItems.get(existed).increaseQuantity(quantity);
            } else {
                CartFragment.cartItems.add(new CartItem(product, quantity, selectedSize, selectedTopping));
            }

            Toast.makeText(requireContext(),
                    getString(R.string.added_to_cart_format, quantity, product.getName()),
                    Toast.LENGTH_SHORT).show();

            // Hiện BottomSheet: "Chọn thêm" / "Đến giỏ hàng"
            showPostAddBottomSheet();
        });

        // Nạp dữ liệu theo productId
        String id = (getArguments() != null) ? getArguments().getString(ARG_PRODUCT_ID) : null;
        if (TextUtils.isEmpty(id)) {
            Toast.makeText(requireContext(), R.string.data_not_found, Toast.LENGTH_SHORT).show();
            requireActivity().getOnBackPressedDispatcher().onBackPressed();
            return;
        }
        fetchProductById(id);
    }

    private void bindViews(@NonNull View v) {
        milkTeaImage       = v.findViewById(R.id.milkTeaImage);
        nameTextView       = v.findViewById(R.id.nameTextView);
        priceTextView      = v.findViewById(R.id.priceTextView);
        descriptionTextView= v.findViewById(R.id.descriptionTextView);
        totalPriceTextView = v.findViewById(R.id.totalPriceTextView);
        quantityTextView   = v.findViewById(R.id.quantityTextView);
        sizeRadioGroup     = v.findViewById(R.id.sizeRadioGroup);
        radioMedium        = v.findViewById(R.id.radioMedium);
        toppingCheckBox    = v.findViewById(R.id.toppingCheckBox);
        addToCartButton    = v.findViewById(R.id.addToCartButton);
        decrementButton    = v.findViewById(R.id.decrementButton);
        incrementButton    = v.findViewById(R.id.incrementButton);
        toolbar            = v.findViewById(R.id.toolbar);
        collapsingToolbar  = v.findViewById(R.id.collapsing_toolbar);
    }

    private void setupToolbar() {
        if (toolbar != null) {
            toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
            toolbar.setNavigationOnClickListener(view -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        }
    }

    private void setupOptionListeners() {
        sizeRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radioSmall)         selectedSize = "Nhỏ";
            else if (checkedId == R.id.radioMedium)   selectedSize = "Vừa";
            else if (checkedId == R.id.radioLarge)    selectedSize = "Lớn";
            updateTotalPrice();
        });

        toppingCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            selectedTopping = isChecked ? "Trân châu trắng" : "Không";
            updateTotalPrice();
        });
    }

    private void setupQuantityListeners() {
        decrementButton.setOnClickListener(view -> {
            if (quantity > 1) {
                quantity--;
                quantityTextView.setText(String.valueOf(quantity));
                updateTotalPrice();
            }
        });

        incrementButton.setOnClickListener(view -> {
            if (quantity < 999) {
                quantity++;
                quantityTextView.setText(String.valueOf(quantity));
                updateTotalPrice();
            }
        });
    }

    private void fetchProductById(@NonNull String id) {
        FirebaseFirestore.getInstance()
                .collection("products")
                .document(id)
                .get()
                .addOnSuccessListener(doc -> {
                    product = doc.toObject(Products.class);
                    if (product == null) {
                        Toast.makeText(requireContext(), R.string.data_not_found, Toast.LENGTH_SHORT).show();
                        requireActivity().getOnBackPressedDispatcher().onBackPressed();
                        return;
                    }
                    if (TextUtils.isEmpty(product.getId())) product.setId(doc.getId());
                    bindProduct(product);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(requireContext(), R.string.data_not_found, Toast.LENGTH_SHORT).show();
                    requireActivity().getOnBackPressedDispatcher().onBackPressed();
                });
    }

    private void bindProduct(@NonNull Products p) {
        nameTextView.setText(nn(p.getName(), getString(R.string.unknown_item)));
        priceTextView.setText(MoneyUtils.formatVnd(p.getPrice()));
        descriptionTextView.setText(nn(p.getDescription(), ""));

        if (collapsingToolbar != null) {
            collapsingToolbar.setTitle(nn(p.getName(), getString(R.string.product_detail)));
        }

        int resId = ImageUtils.getImageResId(requireContext(), nn(p.getImageUrl(), ""));
        if (resId == 0) resId = R.drawable.ic_milk_tea; // fallback
        milkTeaImage.setImageResource(resId);
        milkTeaImage.setContentDescription(p.getName());

        updateTotalPrice();
    }

    private void updateTotalPrice() {
        if (product == null) return;
        double base = product.getPrice();

        // Size: Nhỏ 0%, Vừa +10%, Lớn +20%
        double sizePrice = base;
        if ("Vừa".equals(selectedSize))  sizePrice = base * 1.10;
        else if ("Lớn".equals(selectedSize)) sizePrice = base * 1.20;

        // Topping +5.000đ
        if (toppingCheckBox.isChecked()) sizePrice += 5000;

        double total = sizePrice * quantity;
        totalPriceTextView.setText(MoneyUtils.formatVnd(total));
    }

    private int findExistingCartIndex(String productId, String size, String topping) {
        if (productId == null) return -1;
        String pid = productId.trim();
        String s   = size == null ? "" : size.trim();
        String t   = topping == null ? "" : topping.trim();

        for (int i = 0; i < CartFragment.cartItems.size(); i++) {
            CartItem it = CartFragment.cartItems.get(i);
            if (it.getMilkTea() == null) continue;
            String itId = it.getMilkTea().getId();
            String itS  = it.getSize();
            String itT  = it.getTopping();
            boolean same =
                    pid.equals(itId == null ? "" : itId.trim()) &&
                            s.equals(itS == null ? "" : itS.trim())     &&
                            t.equals(itT == null ? "" : itT.trim());
            if (same) return i;
        }
        return -1;
    }

    private String nn(String s, String def) {
        return TextUtils.isEmpty(s) ? def : s;
    }

    /** BottomSheet: “Chọn thêm” / “Đến giỏ hàng” */
    private void showPostAddBottomSheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        View content = getLayoutInflater().inflate(R.layout.sheet_post_add_cart, null, false);
        sheet.setContentView(content);

        TextView tvMessage = content.findViewById(R.id.tvMessage);
        MaterialButton btnContinue = content.findViewById(R.id.btnContinue);
        MaterialButton btnGoCart   = content.findViewById(R.id.btnGoCart);

        if (product != null) {
            String msg = getString(R.string.added_to_cart_question_with_name, product.getName());
            tvMessage.setText(msg);
        }

        btnContinue.setOnClickListener(v -> {
            sheet.dismiss();
            // Đóng chi tiết → quay về màn trước (Home/List)
            requireActivity().getSupportFragmentManager().popBackStack();
        });

        btnGoCart.setOnClickListener(v -> {
            sheet.dismiss();
            if (requireActivity() instanceof MainActivity) {
                requireActivity().getSupportFragmentManager().popBackStack();
                ((MainActivity) requireActivity()).selectBottomNav(R.id.navigation_cart);
            } else {
                startActivity(new android.content.Intent(requireContext(), MainActivity.class)
                        .putExtra(MainActivity.EXTRA_TARGET_FRAGMENT, "cart")
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                                | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP));
                requireActivity().finish();
            }
        });

        sheet.show();
    }
}
