package com.pro.milkteaapp.fragment.admin.management;

import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.*;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.adapter.admin.AdminToppingAdapter;
import com.pro.milkteaapp.databinding.DialogToppingEditorBinding;
import com.pro.milkteaapp.databinding.FragmentAdminToppingsBinding;
import com.pro.milkteaapp.handler.AddActionHandler;
import com.pro.milkteaapp.models.Topping;
import com.google.firebase.firestore.*;
import java.util.*;

public class ToppingManageFragment extends Fragment
        implements AdminToppingAdapter.Listener, AddActionHandler {

    private FragmentAdminToppingsBinding binding;
    private FirebaseFirestore db;
    private ListenerRegistration sub;
    private final ArrayList<Topping> data = new ArrayList<>();
    private AdminToppingAdapter adapter;
    private final ArrayList<String> allCategories = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAdminToppingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);
        db = FirebaseFirestore.getInstance();

        adapter = new AdminToppingAdapter(data, this);
        binding.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recycler.setAdapter(adapter);

        binding.swipe.setOnRefreshListener(this::reload);

        setLoading(true);
        loadCategoriesThenListenToppings();
    }

    // Gọi khi nhấn FAB trong ManagementFragment
    @Override
    public void onAddAction() {
        openCreateDialog();
    }

    private void setLoading(boolean b) {
        binding.progress.setVisibility(b ? View.VISIBLE : View.GONE);
    }

    private void setEmpty(boolean b) {
        binding.emptyState.setVisibility(b ? View.VISIBLE : View.GONE);
    }

    private void loadCategoriesThenListenToppings() {
        db.collection("categories")
                .whereEqualTo("active", true)
                .orderBy("name", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(qs -> {
                    allCategories.clear();
                    for (DocumentSnapshot d : qs.getDocuments()) {
                        String name = d.getString("name");
                        if (!TextUtils.isEmpty(name)) allCategories.add(name.trim());
                    }
                    listenToppings();
                })
                .addOnFailureListener(e -> listenToppings());
    }

    private void listenToppings() {
        if (sub != null) { sub.remove(); sub = null; }
        sub = db.collection("toppings")
                .orderBy("name", Query.Direction.ASCENDING)
                .addSnapshotListener((snap, e) -> {
                    binding.swipe.setRefreshing(false);
                    setLoading(false);
                    data.clear();
                    if (e != null) {
                        setEmpty(true);
                        adapter.notifyDataSetChanged();
                        Toast.makeText(requireContext(), "Lỗi tải toppings: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (snap != null && !snap.isEmpty()) {
                        for (QueryDocumentSnapshot d : snap) {
                            Topping t = d.toObject(Topping.class);
                            t.setId(d.getId());
                            data.add(t);
                        }
                        setEmpty(data.isEmpty());
                        adapter.notifyDataSetChanged();
                    } else {
                        setEmpty(true);
                        adapter.notifyDataSetChanged();
                    }
                });
    }

    private void reload() {
        loadCategoriesThenListenToppings();
    }

    // --- Adapter callbacks ---
    @Override
    public void onToggleActive(@NonNull Topping t, boolean active) {
        if (TextUtils.isEmpty(t.getId())) return;
        db.collection("toppings").document(t.getId())
                .update("active", active)
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(), "Lỗi cập nhật: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onEdit(@NonNull Topping t) { openEditDialog(t); }

    @Override
    public void onDelete(@NonNull Topping t) {
        if (TextUtils.isEmpty(t.getId())) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Xoá topping")
                .setMessage("Bạn chắc muốn xoá \"" + t.getName() + "\"?")
                .setPositiveButton("Xoá", (d, w) ->
                        db.collection("toppings").document(t.getId()).delete()
                                .addOnFailureListener(e ->
                                        Toast.makeText(requireContext(), "Lỗi xoá: " + e.getMessage(), Toast.LENGTH_SHORT).show()))
                .setNegativeButton("Huỷ", null)
                .show();
    }

    private void openCreateDialog() { openEditor(null); }
    private void openEditDialog(@NonNull Topping t) { openEditor(t); }

    // --- Dialog create/edit ---
    private void openEditor(@Nullable Topping t) {
        DialogToppingEditorBinding eb = DialogToppingEditorBinding.inflate(getLayoutInflater());
        if (t != null) {
            eb.edtName.setText(t.getName());
            eb.edtPrice.setText(String.valueOf(t.getPrice()));
            eb.switchActive.setChecked(t.getActive());
            if (t.getCategories() != null && !t.getCategories().isEmpty())
                eb.txtCategories.setText(TextUtils.join(", ", t.getCategories()));
            else eb.txtCategories.setText("Chưa chọn");
        } else {
            eb.switchActive.setChecked(true);
            eb.txtCategories.setText("Chưa chọn");
        }

        eb.btnPickCategories.setOnClickListener(v -> pickCategories(eb.txtCategories));

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(t == null ? "Thêm topping" : "Sửa topping")
                .setView(eb.getRoot())
                .setPositiveButton(t == null ? "Thêm" : "Lưu", null)
                .setNegativeButton("Huỷ", null)
                .create();

        dialog.setOnShowListener(dlg -> dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = safe(eb.edtName.getText());
            String priceStr = safe(eb.edtPrice.getText());
            boolean active = eb.switchActive.isChecked();

            if (TextUtils.isEmpty(name)) {
                eb.edtName.setError("Nhập tên topping");
                eb.edtName.requestFocus();
                return;
            }

            long price;
            try { price = Long.parseLong(priceStr.replace(".", "").replace(",", "").trim()); }
            catch (Exception ex) {
                eb.edtPrice.setError("Giá không hợp lệ");
                eb.edtPrice.requestFocus();
                return;
            }

            List<String> cats = parseCategories(eb.txtCategories.getText().toString());
            Map<String, Object> data = new HashMap<>();
            data.put("name", name);
            data.put("price", price);
            data.put("categories", cats);
            data.put("active", active);

            if (t == null)
                db.collection("toppings").add(data).addOnSuccessListener(v1 -> dialog.dismiss());
            else
                db.collection("toppings").document(t.getId()).set(data, SetOptions.merge())
                        .addOnSuccessListener(v1 -> dialog.dismiss());
        }));
        dialog.show();
    }

    private void pickCategories(@NonNull android.widget.TextView target) {
        final String[] items = allCategories.toArray(new String[0]);
        final boolean[] checked = new boolean[items.length];
        Set<String> pre = new HashSet<>(parseCategories(target.getText().toString()));
        for (int i = 0; i < items.length; i++) checked[i] = pre.contains(items[i]);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Chọn category áp dụng")
                .setMultiChoiceItems(items, checked, (dialog, which, isChecked) -> {})
                .setPositiveButton("Xong", (dialog, which) -> {
                    ArrayList<String> chosen = new ArrayList<>();
                    for (int i = 0; i < items.length; i++) if (checked[i]) chosen.add(items[i]);
                    target.setText(chosen.isEmpty() ? "Chưa chọn" : TextUtils.join(", ", chosen));
                })
                .setNegativeButton("Huỷ", null)
                .show();
    }

    private static String safe(CharSequence cs) { return cs == null ? "" : cs.toString().trim(); }

    private static List<String> parseCategories(String text) {
        if (TextUtils.isEmpty(text) || "Chưa chọn".equals(text)) return new ArrayList<>();
        String[] parts = text.split(",");
        ArrayList<String> list = new ArrayList<>();
        for (String p : parts) {
            String v = p.trim();
            if (!v.isEmpty()) list.add(v);
        }
        return list;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (sub != null) { sub.remove(); sub = null; }
        binding = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }
}
