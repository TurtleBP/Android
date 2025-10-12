package com.pro.milkteaapp.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.models.Address;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * AddressAdapter – phiên bản hoàn thiện
  * YÊU CẦU LAYOUT item_address.xml có các id:
 *  - @id/tvNamePhone
 *  - @id/tvAddressLines
 *  - @id/btnEdit
 *  - @id/btnDelete
 *  - @id/btnSetDefault
 */
public class AddressAdapter extends RecyclerView.Adapter<AddressAdapter.VH> {

    // Listener KHÔNG phải functional interface -> truyền bằng anonymous class từ Activity/Fragment
    public interface Listener {
        void onEdit(Address a);
        void onDelete(Address a);
        void onSetDefault(Address a);
        void onSelect(Address a); // nếu dùng để chọn địa chỉ khi checkout
    }

    private final Listener listener;
    private List<Address> data = new ArrayList<>();

    public AddressAdapter(@NonNull Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    /** Cập nhật dữ liệu bằng DiffUtil */
    public void submit(@NonNull List<Address> newData) {
        final List<Address> old = this.data;
        final List<Address> now = new ArrayList<>(newData);

        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return old.size(); }
            @Override public int getNewListSize() { return now.size(); }

            @Override public boolean areItemsTheSame(int o, int n) {
                String a = old.get(o).getId();
                String b = now.get(n).getId();
                if (a != null && b != null) return a.equals(b);
                // fallback (ít dùng): so theo name+phone
                return Objects.equals(s(old.get(o).getFullName()), s(now.get(n).getFullName())) &&
                        Objects.equals(s(old.get(o).getPhone()),    s(now.get(n).getPhone()));
            }

            @Override public boolean areContentsTheSame(int o, int n) {
                Address A = old.get(o), B = now.get(n);
                return Objects.equals(s(A.getFullName()), s(B.getFullName())) &&
                        Objects.equals(s(A.getPhone()),    s(B.getPhone())) &&
                        Objects.equals(s(A.getLine1()),    s(B.getLine1())) &&
                        Objects.equals(s(A.getLine2()),    s(B.getLine2())) &&
                        Objects.equals(s(A.getCity()),     s(B.getCity())) &&
                        Objects.equals(s(A.getDistrict()), s(B.getDistrict())) &&
                        Objects.equals(s(A.getNote()),     s(B.getNote())) &&
                        A.isDefault() == B.isDefault();
            }

            @Nullable
            @Override public Object getChangePayload(int o, int n) {
                Address A = old.get(o), B = now.get(n);
                // Nếu chỉ đổi cờ default => payload tối ưu hoá bind
                if (A.isDefault() != B.isDefault()) {
                    return "payload:isDefault";
                }
                return null; // default: rebind full
            }
        });

        this.data = now;
        diff.dispatchUpdatesTo(this);
    }

    @Override public long getItemId(int position) {
        String id = data.get(position).getId();
        return (id == null) ? RecyclerView.NO_ID : (id.hashCode() & 0xffffffffL);
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_address, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(data.get(position));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && payloads.contains("payload:isDefault")) {
            holder.renderDefaultBadge(data.get(position).isDefault());
        } else {
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    @Override public int getItemCount() { return data.size(); }

    // ================== ViewHolder ==================
    class VH extends RecyclerView.ViewHolder {
        TextView tvNamePhone, tvAddressLines;
        MaterialButton btnEdit, btnDelete, btnSetDefault;

        VH(@NonNull View itemView) {
            super(itemView);
            tvNamePhone    = itemView.findViewById(R.id.tvNamePhone);
            tvAddressLines = itemView.findViewById(R.id.tvAddressLines);
            btnEdit        = itemView.findViewById(R.id.btnEdit);
            btnDelete      = itemView.findViewById(R.id.btnDelete);
            btnSetDefault  = itemView.findViewById(R.id.btnSetDefault);
        }

        void bind(@NonNull Address a) {
            String fullName = s(a.getFullName());
            String phone    = s(a.getPhone());
            tvNamePhone.setText(
                    (fullName.isEmpty() ? itemView.getContext().getString(R.string.unknown) : fullName)
                            + (phone.isEmpty() ? "" : " • " + phone)
            );

            StringBuilder sb = new StringBuilder();
            if (!s(a.getLine1()).isEmpty()) sb.append(s(a.getLine1()));
            if (!s(a.getLine2()).isEmpty()) sb.append(", ").append(s(a.getLine2()));
            if (!s(a.getDistrict()).isEmpty()) sb.append(", ").append(s(a.getDistrict()));
            if (!s(a.getCity()).isEmpty()) sb.append(", ").append(s(a.getCity()));
            tvAddressLines.setText(sb.length() == 0 ? "—" : sb.toString());

            renderDefaultBadge(a.isDefault());

            itemView.setOnClickListener(v -> { if (listener != null) listener.onSelect(a); });
            btnEdit.setOnClickListener(v -> { if (listener != null) listener.onEdit(a); });
            btnDelete.setOnClickListener(v -> { if (listener != null) listener.onDelete(a); });
            btnSetDefault.setOnClickListener(v -> { if (listener != null) listener.onSetDefault(a); });

            // Nếu đã là mặc định thì disable nút đặt mặc định (tuỳ UX)
            btnSetDefault.setEnabled(!a.isDefault());
        }

        void renderDefaultBadge(boolean isDefault) {
            // Thể hiện cờ mặc định bằng cách thêm nhãn vào cuối dòng địa chỉ
            CharSequence cur = tvAddressLines.getText();
            String base = cur == null ? "" : cur.toString().replace("  •  " + itemView.getContext().getString(R.string.default_address), "");
            if (isDefault) {
                tvAddressLines.setText(base + "  •  " + itemView.getContext().getString(R.string.default_address));
            } else {
                tvAddressLines.setText(base);
            }
            btnSetDefault.setEnabled(!isDefault);
        }
    }

    private static String s(String x) { return x == null ? "" : x.trim(); }
}
