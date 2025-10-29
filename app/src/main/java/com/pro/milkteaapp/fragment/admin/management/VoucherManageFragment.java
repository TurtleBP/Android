package com.pro.milkteaapp.fragment.admin.management;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.adapter.VoucherAdapter;
import com.pro.milkteaapp.models.Voucher;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

public class VoucherManageFragment extends Fragment
        implements com.pro.milkteaapp.handler.AddActionHandler, VoucherAdapter.Listener {

    private FirebaseFirestore db;
    private VoucherAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_voucher_manage, container, false);
        db = FirebaseFirestore.getInstance();

        RecyclerView rv = v.findViewById(R.id.rv);
        View fabAdd = v.findViewById(R.id.fabAdd);

        adapter = new VoucherAdapter(this);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);

        if (fabAdd != null) {
            fabAdd.setOnClickListener(x -> showUpsertDialog(null, "ORDER_FIXED"));
        }

        db.collection("vouchers").orderBy("code", Query.Direction.ASCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    List<Voucher> list = new ArrayList<>();
                    if (snap != null) {
                        list = snap.toObjects(Voucher.class);
                        for (int i = 0; i < snap.size(); i++) {
                            list.get(i).setId(snap.getDocuments().get(i).getId());
                        }
                    }
                    adapter.submit(list);
                });

        return v;
    }

    @Override
    public void onAddAction() {
        showUpsertDialog(null, "ORDER_FIXED");
    }

    private void showUpsertDialog(@Nullable Voucher editing, @Nullable String presetType) {
        View form = LayoutInflater.from(requireContext()).inflate(R.layout.voucher_form_simple, null, false);

        AlertDialog d = new AlertDialog.Builder(requireContext())
                .setTitle(editing == null ? "Thêm voucher" : ("Sửa " + editing.getCode()))
                .setView(form)
                .setPositiveButton(editing == null ? "Tạo" : "Lưu", null)
                .setNegativeButton("Hủy", (dlg, w) -> dlg.dismiss())
                .create();

        d.setOnShowListener(dialog -> {
            TextInputLayout tilAmount = form.findViewById(R.id.tilAmount);
            TextInputLayout tilPercent = form.findViewById(R.id.tilPercent);
            TextInputLayout tilMaxDiscount = form.findViewById(R.id.tilMaxDiscount);

            TextInputEditText edtCode = form.findViewById(R.id.edtCode);
            TextInputEditText edtAmount = form.findViewById(R.id.edtAmount);
            TextInputEditText edtPercent = form.findViewById(R.id.edtPercent);
            TextInputEditText edtMax = form.findViewById(R.id.edtMaxDiscount);
            TextInputEditText edtMin = form.findViewById(R.id.edtMin);
            TextInputEditText edtEndDate = form.findViewById(R.id.edtEndDate);
            TextInputEditText edtLimit = form.findViewById(R.id.edtLimit);
            TextInputEditText edtDesc = form.findViewById(R.id.edtDesc);
            MaterialCheckBox chkCOD = form.findViewById(R.id.chkCOD);
            MaterialCheckBox chkMomo = form.findViewById(R.id.chkMomo);
            MaterialCheckBox chkCard = form.findViewById(R.id.chkCard);
            SwitchMaterial swActive = form.findViewById(R.id.swActive);

            View btnOrderFixed = form.findViewById(R.id.btnTypeOrderFixed);
            View btnOrderPercent = form.findViewById(R.id.btnTypeOrderPercent);
            View btnShipFixed = form.findViewById(R.id.btnTypeShipFixed);
            final String[] type = new String[]{"ORDER_FIXED"};

            if (editing != null) type[0] = editing.getType();
            else if (presetType != null) type[0] = presetType;

            Runnable applyTypeUi = () -> {
                boolean isOrderFixed = "ORDER_FIXED".equals(type[0]);
                boolean isOrderPercent = "ORDER_PERCENT".equals(type[0]);
                boolean isShipFixed = "SHIPPING_FIXED".equals(type[0]);

                tilAmount.setVisibility((isOrderFixed || isShipFixed) ? View.VISIBLE : View.GONE);
                tilPercent.setVisibility(isOrderPercent ? View.VISIBLE : View.GONE);
                tilMaxDiscount.setVisibility(isOrderPercent ? View.VISIBLE : View.GONE);

                if (!(isOrderFixed || isShipFixed)) {
                    if (edtAmount != null) edtAmount.setText("");
                }
                if (!isOrderPercent) {
                    if (edtPercent != null) edtPercent.setText("");
                    if (edtMax != null) edtMax.setText("");
                }

                btnOrderFixed.setEnabled(!isOrderFixed);
                btnOrderPercent.setEnabled(!isOrderPercent);
                btnShipFixed.setEnabled(!isShipFixed);
            };

            View.OnClickListener pick = v1 -> {
                int id = v1.getId();
                if (id == R.id.btnTypeOrderFixed) type[0] = "ORDER_FIXED";
                else if (id == R.id.btnTypeOrderPercent) type[0] = "ORDER_PERCENT";
                else type[0] = "SHIPPING_FIXED";
                applyTypeUi.run();
            };
            btnOrderFixed.setOnClickListener(pick);
            btnOrderPercent.setOnClickListener(pick);
            btnShipFixed.setOnClickListener(pick);

            // DatePicker: yyyy-MM-dd
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            edtEndDate.setOnClickListener(v12 -> {
                Calendar c = Calendar.getInstance();
                new DatePickerDialog(requireContext(), (dp, y, m, d1) -> {
                    String mm = (m + 1 < 10 ? "0" + (m + 1) : String.valueOf(m + 1));
                    String dd = (d1 < 10 ? "0" + d1 : String.valueOf(d1));
                    edtEndDate.setText(y + "-" + mm + "-" + dd);
                }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
            });

            if (editing != null) {
                edtCode.setText(editing.getCode());
                if ("ORDER_FIXED".equals(type[0]) || "SHIPPING_FIXED".equals(type[0])) {
                    edtAmount.setText(String.valueOf(editing.getAmount()));
                } else if ("ORDER_PERCENT".equals(type[0])) {
                    edtPercent.setText(String.valueOf(editing.getPercent()));
                    if (editing.getMaxDiscount() != null) edtMax.setText(String.valueOf(editing.getMaxDiscount()));
                }
                edtMin.setText(String.valueOf(editing.getMinOrder()));
                if (editing.getEndAt() != null) { // nếu model của bạn là getEndAt()
                    edtEndDate.setText(sdf.format(editing.getEndAt().toDate()));
                }
                edtLimit.setText(String.valueOf(editing.getPerUserLimit()));
                swActive.setChecked(editing.isActive());
                if (editing.getAllowedChannels() != null) {
                    chkCOD.setChecked(editing.getAllowedChannels().contains("COD"));
                    chkMomo.setChecked(editing.getAllowedChannels().contains("MOMO"));
                    chkCard.setChecked(editing.getAllowedChannels().contains("CARD"));
                }
                edtDesc.setText(editing.getDescription());
            } else {
                // seed
                if ("ORDER_FIXED".equals(type[0])) edtAmount.setText("15000");
                if ("ORDER_PERCENT".equals(type[0])) {
                    edtPercent.setText("15");
                    edtMax.setText("30000");
                }
                edtMin.setText("60000");
                edtEndDate.setText("2025-11-15");
                edtLimit.setText("1");
                chkCOD.setChecked(true);
                chkMomo.setChecked(true);
                chkCard.setChecked(true);
                swActive.setChecked(true);
            }

            applyTypeUi.run();

            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(clk -> {
                String code = safe(edtCode).toUpperCase(Locale.getDefault());
                if (code.isEmpty()) {
                    edtCode.setError("Bắt buộc");
                    return;
                }

                List<String> channels = new ArrayList<>();
                if (chkCOD.isChecked()) channels.add("COD");
                if (chkMomo.isChecked()) channels.add("MOMO");
                if (chkCard.isChecked()) channels.add("CARD");

                Map<String, Object> data = new HashMap<>();
                data.put("code", code);
                data.put("type", type[0]);

                long amount = parseLong(edtAmount);
                int percent = parseInt(edtPercent);
                long max = parseLong(edtMax);
                long min = parseLong(edtMin);
                int limit = (int) parseLong(edtLimit);
                if (limit <= 0) limit = 1;

                if ("ORDER_FIXED".equals(type[0]) || "SHIPPING_FIXED".equals(type[0])) {
                    if (amount <= 0) {
                        edtAmount.setError("Nhập số tiền > 0");
                        return;
                    }
                }
                if ("ORDER_PERCENT".equals(type[0])) {
                    if (percent <= 0) {
                        edtPercent.setError("Nhập % > 0");
                        return;
                    }
                    if (max < 0) {
                        edtMax.setError("Tối đa không âm");
                        return;
                    }
                }

                com.google.firebase.Timestamp startTs =
                        (editing == null || editing.getStartAt() == null)
                                ? com.google.firebase.Timestamp.now()
                                : editing.getStartAt();

                String endStr = safe(edtEndDate);
                com.google.firebase.Timestamp endTs = null;
                if (!endStr.isEmpty()) {
                    try {
                        SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                        Date date = sdf2.parse(endStr);
                        Calendar c = Calendar.getInstance();
                        c.setTime(date);
                        c.set(Calendar.HOUR_OF_DAY, 23);
                        c.set(Calendar.MINUTE, 59);
                        c.set(Calendar.SECOND, 59);
                        c.set(Calendar.MILLISECOND, 0);
                        endTs = new com.google.firebase.Timestamp(c.getTime());
                    } catch (Exception ex) {
                        edtEndDate.setError("Định dạng yyyy-MM-dd");
                        return;
                    }
                }

                data.put("amount", amount);
                data.put("percent", percent);
                data.put("maxDiscount", ("ORDER_PERCENT".equals(type[0]) && max > 0) ? max : null);
                data.put("minOrder", min);
                data.put("startAt", startTs);
                data.put("endAt", endTs);
                data.put("perUserLimit", limit);
                data.put("active", swActive.isChecked());
                data.put("allowedChannels", channels);
                data.put("description", safe(edtDesc));

                if (editing == null) {
                    db.collection("vouchers").add(data)
                            .addOnSuccessListener(ref -> d.dismiss())
                            .addOnFailureListener(er -> Toast.makeText(requireContext(), er.getMessage(), Toast.LENGTH_SHORT).show());
                } else {
                    db.collection("vouchers").document(editing.getId()).update(data)
                            .addOnSuccessListener(vv -> d.dismiss())
                            .addOnFailureListener(er -> Toast.makeText(requireContext(), er.getMessage(), Toast.LENGTH_SHORT).show());
                }
            });
        });

        d.show();
    }

    private static String safe(@Nullable TextInputEditText e) {
        return e == null || e.getText() == null ? "" : e.getText().toString().trim();
    }

    private static long parseLong(@Nullable TextInputEditText e) {
        try {
            return Long.parseLong(safe(e));
        } catch (Exception ignore) {
            return 0L;
        }
    }

    private static int parseInt(@Nullable TextInputEditText e) {
        try {
            return Integer.parseInt(safe(e));
        } catch (Exception ignore) {
            return 0;
        }
    }

    @Override public void onEdit(@NonNull Voucher v) { showUpsertDialog(v, null); }

    @Override
    public void onDelete(@NonNull Voucher v) {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setMessage("Xóa " + v.getCode() + "?")
                .setPositiveButton("Xóa", (dlg, w) -> db.collection("vouchers").document(v.getId()).delete())
                .setNegativeButton("Hủy", null).show();
    }

    @Override
    public void onToggle(@NonNull Voucher v, boolean active) {
        db.collection("vouchers").document(v.getId()).update("active", active);
    }
}
