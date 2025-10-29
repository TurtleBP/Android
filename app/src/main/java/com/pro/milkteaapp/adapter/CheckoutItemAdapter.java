package com.pro.milkteaapp.adapter;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
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

    // ID ổn định: productId|size|toppingIds...
    @Override
    public long getItemId(int position) {
        CartItem ci = items.get(position);
        StringBuilder key = new StringBuilder();
        key.append(ci.getProductId()).append("|").append(ci.getSize()).append("|");
        List<SelectedTopping> ts = ci.getToppings();
        if (ts != null) for (SelectedTopping t : ts) key.append(t.id).append(",");
        return key.toString().replace("null","_").hashCode();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemCheckoutRowBinding.inflate(LayoutInflater.from(ctx), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        CartItem ci = items.get(position);
        Products p = ci.getMilkTea();

        h.b.tvName.setText(p.getName());

        // Meta: Size • Topping: a, b, c
        String size = ci.getSize();
        String toppingLabel = ci.getToppingsLabel(); // "Không" hoặc danh sách
        String meta = TextUtils.isEmpty(size) ? "" : String.format(Locale.getDefault(), "Size %s", size);
        if (!TextUtils.isEmpty(toppingLabel) && !"Không".equals(toppingLabel)) {
            meta = TextUtils.isEmpty(meta) ? ("Topping: " + toppingLabel) : (meta + " • Topping: " + toppingLabel);
        }
        h.b.tvMeta.setText(meta);

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
