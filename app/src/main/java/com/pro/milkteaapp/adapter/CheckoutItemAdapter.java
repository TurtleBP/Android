package com.pro.milkteaapp.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.pro.milkteaapp.databinding.ItemCheckoutRowBinding;
import com.pro.milkteaapp.models.CartItem;
import com.pro.milkteaapp.models.Products;
import com.pro.milkteaapp.models.SelectedTopping;
import com.pro.milkteaapp.utils.ImageUtils;
import com.pro.milkteaapp.utils.MoneyUtils;

import java.util.List;
import java.util.Locale;

public class CheckoutItemAdapter extends RecyclerView.Adapter<CheckoutItemAdapter.VH> {

    private final Context ctx;
    private final List<CartItem> items;

    public CheckoutItemAdapter(Context ctx, List<CartItem> items) {
        this.ctx = ctx;
        this.items = items;
        setHasStableIds(true);
    }

    // ID ổn định: productId|size|sugar|ice|note|toppingIds...
    @Override
    public long getItemId(int position) {
        CartItem ci = items.get(position);
        StringBuilder key = new StringBuilder();
        key.append(ci.getProductId()).append("|")
                .append(ci.getSize()).append("|")
                .append(ci.getSugar()).append("|")
                .append(ci.getIce()).append("|")
                .append(ci.getNote()).append("|");
        List<SelectedTopping> ts = ci.getToppings();
        if (ts != null) for (SelectedTopping t : ts) key.append(t.id).append(",");
        return key.toString().replace("null","_").hashCode();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemCheckoutRowBinding.inflate(LayoutInflater.from(ctx), parent, false));
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        CartItem ci = items.get(position);
        Products p = ci.getMilkTea();

        // Tên
        h.b.tvName.setText(p.getName());

        // Hàng 1: Size • Đường • Đá
        String size = ci.getSize();
        String sugar = ci.getSugar();
        String ice = ci.getIce();

        StringBuilder line1 = new StringBuilder();
        if (!TextUtils.isEmpty(size)) {
            line1.append(String.format(Locale.getDefault(), "Size %s", size));
        }
        if (!TextUtils.isEmpty(sugar)) {
            if (line1.length() > 0) line1.append(" • ");
            line1.append(sugar);
        }
        if (!TextUtils.isEmpty(ice)) {
            if (line1.length() > 0) line1.append(" • ");
            line1.append(ice);
        }
        h.b.tvLine1.setText(line1.toString());
        h.b.tvLine1.setVisibility(line1.length() > 0 ? View.VISIBLE : View.GONE);

        // Hàng 2: Topping (riêng 1 hàng, tự wrap)
        String toppingLabel = ci.getToppingsLabel(); // ví dụ: "Trân châu trắng, Flan"
        if (!TextUtils.isEmpty(toppingLabel)) {
            h.b.tvToppings.setText("Topping: " + toppingLabel);
            h.b.tvToppings.setVisibility(View.VISIBLE);
        } else {
            h.b.tvToppings.setVisibility(View.GONE);
        }

        // Ghi chú
        String note = ci.getNote();
        if (!TextUtils.isEmpty(note)) {
            h.b.tvNote.setText("Ghi chú: " + note);
            h.b.tvNote.setVisibility(View.VISIBLE);
        } else {
            h.b.tvNote.setVisibility(View.GONE);
        }

        // Đơn giá × SL và Thành tiền
        long unit = ci.getUnitPrice(); // đã gồm size + toppings
        int qty = ci.getQuantity();
        h.b.tvUnitQty.setText(String.format(Locale.getDefault(), "%s × %d", MoneyUtils.formatVnd((double) unit), qty));
        h.b.tvLineTotal.setText(MoneyUtils.formatVnd((double) ci.getTotalPrice()));

        // Ảnh (drawable name)
        String imageName = p.getImageUrl();
        int resId = ImageUtils.getImageResId(ctx, imageName);
        h.b.img.setImageResource(resId != 0 ? resId : com.pro.milkteaapp.R.mipmap.ic_launcher);
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final ItemCheckoutRowBinding b;
        VH(ItemCheckoutRowBinding b) { super(b.getRoot()); this.b = b; }
    }
}
