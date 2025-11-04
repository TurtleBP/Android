package com.pro.milkteaapp.fragment.bottomsheet;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.*;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.adapter.ToppingSelectAdapter;
import com.pro.milkteaapp.models.Products;
import com.pro.milkteaapp.models.SelectedTopping;
import com.pro.milkteaapp.models.Topping;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Locale;

public class ProductDetailBottomSheet extends BottomSheetDialogFragment {

    private static final String TAG = "ProductDetailSheet";
    private static final String ARG_PRODUCT   = "arg_product";
    private static final String ARG_IMAGE_URL = "ARG_IMAGE_URL";

    public interface Listener {
        void onAddToCart(Products product, int qty, String size, String sugar, String ice, String note, java.util.List<SelectedTopping> toppings);
    }

    private Listener listener;
    private Products product;

    private ImageView milkTeaImage;
    private TextView nameTextView, priceTextView, descriptionTextView, quantityTextView, totalPriceTextView;
    private RadioGroup sizeRadioGroup;
    private MaterialButton decrementButton, incrementButton, addToCartButton;

    private RadioGroup sugarRadioGroup;
    private RadioGroup iceRadioGroup;
    private EditText edtNote;

    private RecyclerView recyclerToppings;
    private TextView toppingsEmpty;

    private final ArrayList<SelectedTopping> selected = new ArrayList<>();
    private final ArrayList<Topping> available = new ArrayList<>();
    private ToppingSelectAdapter toppingAdapter;

    private int quantity = 1;

    public static ProductDetailBottomSheet newInstance(@NonNull Products p) {
        Bundle b = new Bundle();
        b.putSerializable(ARG_PRODUCT, p);
        String url = !TextUtils.isEmpty(p.getImageUrl()) ? p.getImageUrl() : null;
        if (!TextUtils.isEmpty(url)) b.putString(ARG_IMAGE_URL, url);
        ProductDetailBottomSheet f = new ProductDetailBottomSheet();
        f.setArguments(b);
        return f;
    }

    public void setListener(Listener l) { this.listener = l; }

    @Override public int getTheme() { return R.style.AppBottomSheetDialogTheme; }

    @Override
    public void onStart() {
        super.onStart();
        BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
        if (dialog != null) {
            View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
                behavior.setFitToContents(true);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        }
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,@Nullable ViewGroup container,@Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.bottomsheet_product_detail, container, false);
        milkTeaImage        = v.findViewById(R.id.milkTeaImage);
        nameTextView        = v.findViewById(R.id.nameTextView);
        priceTextView       = v.findViewById(R.id.priceTextView);
        descriptionTextView = v.findViewById(R.id.descriptionTextView);
        quantityTextView    = v.findViewById(R.id.quantityTextView);
        totalPriceTextView  = v.findViewById(R.id.totalPriceTextView);
        sizeRadioGroup      = v.findViewById(R.id.sizeRadioGroup);
        decrementButton     = v.findViewById(R.id.decrementButton);
        incrementButton     = v.findViewById(R.id.incrementButton);
        addToCartButton     = v.findViewById(R.id.addToCartButton);
        recyclerToppings    = v.findViewById(R.id.recyclerToppings);
        toppingsEmpty       = v.findViewById(R.id.toppingsEmpty);

        sugarRadioGroup = v.findViewById(R.id.sugarRadioGroup);
        iceRadioGroup   = v.findViewById(R.id.iceRadioGroup);
        edtNote         = v.findViewById(R.id.edtNote);
        return v;
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        // Product
        if (getArguments() != null) {
            Object o = getArguments().getSerializable(ARG_PRODUCT);
            if (o instanceof Products) product = (Products) o;
        }
        if (product == null) { dismiss(); return; }

        // Bind text
        nameTextView.setText(product.getName());
        priceTextView.setText(formatVND(safePrice(product.getPrice())));
        if (!TextUtils.isEmpty(product.getDescription())) {
            descriptionTextView.setText(product.getDescription());
            descriptionTextView.setVisibility(View.VISIBLE);
        } else descriptionTextView.setVisibility(View.GONE);

        // Image
        String raw = getArguments() != null ? getArguments().getString(ARG_IMAGE_URL, null) : null;
        if (TextUtils.isEmpty(raw)) raw = product.getImageUrl();
        if (!TextUtils.isEmpty(raw)) {
            if (isHttpUrl(raw)) {
                Glide.with(this).load(raw).placeholder(R.drawable.ic_milk_tea).error(R.drawable.ic_bank).into(milkTeaImage);
            } else {
                int resId = resolveDrawableRes(requireContext(), raw);
                milkTeaImage.setImageResource(resId != 0 ? resId : R.drawable.ic_milk_tea);
            }
        } else milkTeaImage.setImageResource(R.drawable.ic_milk_tea);

        // Size mặc định Medium
        sizeRadioGroup.check(R.id.radioSmall);
        quantityTextView.setText(String.valueOf(quantity));

        // Adapter topping
        toppingAdapter = new ToppingSelectAdapter((t, ch) -> {
            if (ch) {
                // tránh trùng
                boolean exists = false;
                for (SelectedTopping st : selected) {
                    if (TextUtils.equals(st.id, t.getId())) { exists = true; break; }
                }
                if (!exists) selected.add(new SelectedTopping(t.getId(), t.getName(), t.getPrice()));
            } else {
                for (int i = 0; i < selected.size(); i++) {
                    if (TextUtils.equals(selected.get(i).id, t.getId())) { selected.remove(i); break; }
                }
            }
            updateTotal();
        });
        recyclerToppings.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerToppings.setAdapter(toppingAdapter);

        loadToppingsForCategory(safe(product.getCategory()));

        // Listeners
        decrementButton.setOnClickListener(v1 -> {
            if (quantity > 1) quantity--;
            quantityTextView.setText(String.valueOf(quantity));
            updateTotal();
        });
        incrementButton.setOnClickListener(v12 -> {
            quantity++;
            quantityTextView.setText(String.valueOf(quantity));
            updateTotal();
        });
        sizeRadioGroup.setOnCheckedChangeListener((g, id) -> updateTotal());
        sugarRadioGroup.setOnCheckedChangeListener((g, id) -> updateTotal()); // THÊM
        iceRadioGroup.setOnCheckedChangeListener((g, id) -> updateTotal());   // THÊM

        addToCartButton.setOnClickListener(v13 -> {
            if (listener != null && product != null) {

                String size = selectedSize();
                String sugar = selectedSugar(sugarRadioGroup);
                String ice = selectedIce(iceRadioGroup);
                String note = edtNote.getText() != null ? edtNote.getText().toString().trim() : "";

                listener.onAddToCart(product, quantity, size,
                        sugar, ice, note, new ArrayList<>(selected));
            }
            dismiss();
        });

        updateTotal();
    }

    /** Load topping theo category + active=true; nếu sản phẩm không có category thì hiển thị tất cả active */
    private void loadToppingsForCategory(@NonNull String category) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Query q = db.collection("toppings").whereEqualTo("active", true);
        if (!TextUtils.isEmpty(category)) {
            q = q.whereArrayContains("categories", category);
        }
        q = q.orderBy("name");

        q.get().addOnSuccessListener(snap -> {
            available.clear();
            if (snap != null) {
                for (QueryDocumentSnapshot d : snap) {
                    Topping t = d.toObject(Topping.class);
                    t.setId(d.getId());
                    available.add(t);
                }
            }
            toppingsEmpty.setVisibility(available.isEmpty() ? View.VISIBLE : View.GONE);
            recyclerToppings.setVisibility(available.isEmpty() ? View.GONE : View.VISIBLE);
            toppingAdapter.submitList(available, selected);
            updateTotal();
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Load toppings failed: " + e.getMessage(), e);
            toppingsEmpty.setVisibility(View.VISIBLE);
            recyclerToppings.setVisibility(View.GONE);
        });
    }

    private void updateTotal() {
        long unit = safePrice(product != null ? product.getPrice() : null);

        // Phụ thu theo size (điều chỉnh theo business của bạn)
        int checked = sizeRadioGroup.getCheckedRadioButtonId();
        if (checked == R.id.radioSmall)       unit += 0;
        else if (checked == R.id.radioMedium)  unit += 3000; // nếu Medium là mặc định = 0, sửa dòng này về 0
        else if (checked == R.id.radioLarge)   unit += 6000;

        // Cộng topping
        long top = 0;
        for (SelectedTopping st : selected) top += Math.max(0, st.price);
        unit += top;

        long total = unit * Math.max(1, quantity);
        totalPriceTextView.setText(formatVND(total));
    }

    private String selectedSize() {
        int checked = sizeRadioGroup.getCheckedRadioButtonId();
        if (checked == R.id.radioSmall)  return "Nhỏ";
        if (checked == R.id.radioLarge)  return "Lớn";
        return "Vừa";
    }

    private String selectedSugar(RadioGroup group) {
        int id = group.getCheckedRadioButtonId();
        if (id == R.id.radioSugar0) return "0% đường";
        if (id == R.id.radioSugar50) return "50% đường";
        if (id == R.id.radioSugar120) return "120% đường";
        return "100% đường"; // Default
    }

    private String selectedIce(RadioGroup group) {
        int id = group.getCheckedRadioButtonId();
        if (id == R.id.radioIceSeparate) return "Đá riêng";
        if (id == R.id.radioIce0) return "0% đá";
        if (id == R.id.radioIce50) return "50% đá";
        return "100% đá"; // Default
    }

    // Helpers
    private static String  safe(String v){ return v == null ? "" : v; }
    private static long    safePrice(Double p){ return p == null ? 0L : p.longValue(); }
    private static String  formatVND(long v) { NumberFormat nf = NumberFormat.getInstance(new Locale("vi","VN")); return nf.format(v) + "đ"; }
    private static boolean isHttpUrl(String s) { return s != null && (s.startsWith("http://") || s.startsWith("https://")); }
    @SuppressLint("DiscouragedApi")
    private static int resolveDrawableRes(@NonNull android.content.Context ctx, @NonNull String name) {
        String n = name.trim();
        if (n.startsWith("drawable/")) n = n.substring("drawable/".length());
        if (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp")) {
            int dot = n.lastIndexOf('.'); if (dot > 0) n = n.substring(0, dot);
        }
        n = n.replace(' ', '_').toLowerCase(Locale.ROOT);
        return ctx.getResources().getIdentifier(n, "drawable", ctx.getPackageName());
    }
}
