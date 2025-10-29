package com.pro.milkteaapp.fragment.pickersheet;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.models.Voucher;
import com.pro.milkteaapp.utils.MoneyUtils;
import com.pro.milkteaapp.utils.VoucherUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class VoucherListPickerSheet extends BottomSheetDialogFragment {

    // ===== Callback =====
    public interface Listener { void onPicked(@Nullable Voucher v); }
    private Listener listener;
    public void setListener(@NonNull Listener l){ this.listener = l; }

    // ===== Args =====
    private static final String ARG_SUBTOTAL = "subtotal";
    private static final String ARG_PAYMENT  = "payment";
    private static final String ARG_USERID   = "userId";

    /** Tạo instance kèm điều kiện lọc (subtotal, payment, userId). Có thể null nếu không cần. */
    public static VoucherListPickerSheet newInstance(long subtotal, @Nullable String payment, @Nullable String userId){
        VoucherListPickerSheet f = new VoucherListPickerSheet();
        Bundle b = new Bundle();
        b.putLong(ARG_SUBTOTAL, subtotal);
        if (payment != null) b.putString(ARG_PAYMENT, payment);
        if (userId != null) b.putString(ARG_USERID, userId);
        f.setArguments(b);
        return f;
    }

    // ===== UI =====
    private RecyclerView rv;
    private ChipGroup chipGroup;

    // ===== Data =====
    private final List<Voucher> all = new ArrayList<>();
    private final List<Voucher> shown = new ArrayList<>();
    private final Adapter adapter = new Adapter();

    // ===== Filters =====
    private long subtotal = 0L;
    @Nullable private String payment; // "COD" | "Momo" | "Thẻ" ...
    @Nullable private String userId;

    // ===== Firestore =====
    private ListenerRegistration reg;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle s) {
        View v = inflater.inflate(R.layout.bottomsheet_voucher_picker, container, false);
        rv = v.findViewById(R.id.rv);
        chipGroup = v.findViewById(R.id.chipGroupType);

        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);

        Bundle args = getArguments();
        if (args != null){
            subtotal = args.getLong(ARG_SUBTOTAL, 0L);
            payment  = args.getString(ARG_PAYMENT, null);
            userId   = args.getString(ARG_USERID, null);
        } else {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            if (auth.getCurrentUser()!=null) userId = auth.getCurrentUser().getUid();
        }

        // Lắng nghe thay đổi chip để lọc
        chipGroup.setOnCheckedStateChangeListener((g, ids)-> applyFilter());

        return v;
    }

    @Override public void onStart() {
        super.onStart();
        View view = getDialog().findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (view != null) {
            view.getLayoutParams().height = (int) (getResources().getDisplayMetrics().heightPixels * 0.66f);
            view.requestLayout();
        }
    }

    @Override public void onResume() {
        super.onResume();
        // Lắng nghe vouchers active
        reg = FirebaseFirestore.getInstance()
                .collection("vouchers")
                .whereEqualTo("active", true)
                .orderBy("code", Query.Direction.ASCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        Toast.makeText(requireContext(), "Lỗi tải voucher: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        return;
                    }
                    all.clear();
                    if (snap != null) {
                        List<Voucher> list = snap.toObjects(Voucher.class);
                        for (int i = 0; i < snap.size(); i++) {
                            Voucher v = list.get(i);
                            DocumentSnapshot d = snap.getDocuments().get(i);
                            v.setId(d.getId());
                        }
                        // Điều kiện chung: thời hạn, min order, kênh thanh toán
                        Date today0 = stripTime(new Date());
                        for (Voucher v : list) {
                            if (!VoucherUtils.isInRangeNow(v)) continue;
                            if (v.getEndAt() != null && v.getEndAt().toDate().before(today0)) continue;

                            if (payment != null && v.getAllowedChannels()!=null && !v.getAllowedChannels().isEmpty()){
                                boolean ok = false;
                                for (String ch : v.getAllowedChannels()) {
                                    if (payment.equalsIgnoreCase(ch)) { ok = true; break; }
                                }
                                if (!ok) continue;
                            }
                            all.add(v);
                        }
                    }
                    // Check hạn mức mỗi KH nếu có userId
                    if (userId != null) checkPerUserLimitThenFilter();
                    else applyFilter();
                });
    }

    @Override public void onPause() {
        super.onPause();
        if (reg != null) { reg.remove(); reg = null; }
    }

    /** Lọc theo số lượt dùng/khách nếu có perUserLimit */
    private void checkPerUserLimitThenFilter(){
        if(userId == null){ applyFilter(); return; }
        FirebaseFirestore.getInstance()
                .collection("users").document(userId)
                .collection("voucher_usages")
                .get()
                .addOnSuccessListener(qs -> {
                    Map<String,Long> used = new HashMap<>();
                    for(DocumentSnapshot d: qs.getDocuments()){
                        Long c = d.getLong("count");
                        used.put(d.getId(), (c!=null? c:0L));
                    }
                    Iterator<Voucher> it = all.iterator();
                    while (it.hasNext()){
                        Voucher v = it.next();
                        Long lim = (long) v.getPerUserLimit();
                        if(lim != null && lim > 0){
                            long cnt = used.getOrDefault(v.getId(), 0L);
                            if(cnt >= lim){
                                it.remove();
                            }
                        }
                    }
                    applyFilter();
                })
                .addOnFailureListener(e -> applyFilter());
    }

    /** Áp dụng lọc theo chip loại */
    private void applyFilter(){
        String type = null;
        int checked = chipGroup.getCheckedChipId();
        if(checked == R.id.chipOrderFixed) type = "ORDER_FIXED";
        else if(checked == R.id.chipOrderPercent) type = "ORDER_PERCENT";
        else if(checked == R.id.chipShippingFixed) type = "SHIPPING_FIXED";

        shown.clear();
        for(Voucher v : all){
            if(type==null || type.equals(v.getType())) shown.add(v);
        }
        adapter.notifyDataSetChanged();
    }

    // ===== Adapter hiển thị picker_voucher.xml =====
    class Adapter extends RecyclerView.Adapter<VH>{
        private final SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        @Override public int getItemCount(){ return shown.size(); }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int vt){
            View v = LayoutInflater.from(p.getContext()).inflate(R.layout.picker_voucher, p, false);
            return new VH(v);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int pos){
            Voucher v = shown.get(pos);

            // Mã
            h.tvCode.setText(nz(v.getCode()));

            // Ưu đãi
            h.tvBenefit.setText(buildBenefit(v));

            // Hạn sử dụng
            Timestamp end = v.getEndAt();
            h.tvExpiry.setText(end == null ? "Không giới hạn" : "Đến " + df.format(end.toDate()));

            // Min order
            long min = nzl(v.getMinOrder());
            h.tvMin.setText(min > 0 ? "Từ " + MoneyUtils.formatVnd(min) : "Không yêu cầu");

            // Kênh thanh toán (chips)
            bindChannels(h.chipsChannels, v.getAllowedChannels());

            // Giới hạn / KH
            long perUser = nzl(v.getPerUserLimit());
            h.tvLimit.setText(perUser > 0 ? ("Mỗi khách hàng " + perUser + " lần") : "Không giới hạn");

            // Nút chọn
            h.btnPick.setOnClickListener(x -> {
                if (listener != null) listener.onPicked(v);
                dismiss();
            });
        }

        private String buildBenefit(@NonNull Voucher v){
            String type = nz(v.getType());
            if ("ORDER_FIXED".equals(type)) {
                return "Giảm " + MoneyUtils.formatVnd(nzl(v.getAmount()));
            }
            if ("ORDER_PERCENT".equals(type)) {
                long percent = nzl(v.getPercent());
                long max = nzl(v.getMaxDiscount());
                String s = "Giảm " + percent + "% tổng đơn";
                if (max > 0) s += " (tối đa " + MoneyUtils.formatVnd(max) + ")";
                return s;
            }
            if ("SHIPPING_FIXED".equals(type)) {
                return "Giảm phí ship " + MoneyUtils.formatVnd(nzl(v.getAmount()));
            }
            return "Ưu đãi";
        }

        private void bindChannels(@NonNull ChipGroup group, @Nullable List<String> channels){
            group.removeAllViews();
            if (channels == null || channels.isEmpty()){
                Chip chip = new Chip(group.getContext(), null, com.google.android.material.R.attr.chipStyle);
                chip.setText("Tất cả");
                chip.setCheckable(false);
                group.addView(chip);
                return;
            }
            for (String ch : channels){
                Chip chip = new Chip(group.getContext(), null, com.google.android.material.R.attr.chipStyle);
                chip.setText(ch);
                chip.setCheckable(false);
                group.addView(chip);
            }
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvCode, tvBenefit, tvExpiry, tvMin, tvLimit;
        View btnPick;
        ChipGroup chipsChannels;

        VH(@NonNull View v){
            super(v);
            tvCode   = v.findViewById(R.id.tvCode);
            tvBenefit= v.findViewById(R.id.tvBenefit);
            tvExpiry = v.findViewById(R.id.tvExpiry);
            tvMin    = v.findViewById(R.id.tvMin);
            tvLimit  = v.findViewById(R.id.tvLimit);
            chipsChannels = v.findViewById(R.id.chipsChannels);
            btnPick  = v.findViewById(R.id.btnPick);
        }
    }

    // ===== Utils =====
    private static String nz(@Nullable String s){ return s == null ? "" : s; }
    private static long nzl(@Nullable Number n){ return n == null ? 0L : n.longValue(); }

    private static Date stripTime(Date d){
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }
}
