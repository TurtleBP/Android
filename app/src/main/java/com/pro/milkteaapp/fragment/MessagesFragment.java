package com.pro.milkteaapp.fragment;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.SessionManager;
import com.pro.milkteaapp.adapter.MessagesAdapter;
import com.pro.milkteaapp.databinding.FragmentMessagesBinding;
import com.pro.milkteaapp.fragment.bottomsheet.RateOrderBottomSheet;
import com.pro.milkteaapp.models.MessageModel;

import java.util.ArrayList;
import java.util.List;

public class MessagesFragment extends Fragment {

    private FragmentMessagesBinding binding;
    private MessagesAdapter adapter;
    private ListenerRegistration registration;

    public MessagesFragment() { super(R.layout.fragment_messages); }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        binding = FragmentMessagesBinding.bind(view);

        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new MessagesAdapter(this::onMessageClick);
        binding.recyclerView.setAdapter(adapter);

        binding.swipeRefresh.setOnRefreshListener(this::reloadOnce);
        startListening();
    }

    private void startListening() {
        showLoading(true);
        String uid = getUnifiedUidOrNotify();
        if (uid == null) return;

        Query q = FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("inbox")
                .orderBy("createdAt", Query.Direction.DESCENDING);

        if (registration != null) { registration.remove(); registration = null; }
        registration = q.addSnapshotListener((snap, err) -> {
            if (binding == null) return;
            binding.swipeRefresh.setRefreshing(false);
            showLoading(false);
            if (err != null) {
                Toast.makeText(getContext(), err.getMessage(), Toast.LENGTH_LONG).show();
                return;
            }

            List<MessageModel> data = new ArrayList<>();
            if (snap != null) {
                for (DocumentSnapshot d : snap.getDocuments()) {
                    MessageModel m = d.toObject(MessageModel.class);
                    if (m == null) m = new MessageModel();
                    m.setId(d.getId());
                    data.add(m);
                }
            }
            adapter.submit(data);
            toggleEmpty(data.isEmpty());
        });
    }

    private void reloadOnce() {
        String uid = getUnifiedUidOrNotify();
        if (uid == null) return;

        FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("inbox")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnCompleteListener(t -> {
                    if (binding == null) return;
                    binding.swipeRefresh.setRefreshing(false);
                    if (!t.isSuccessful()) {
                        Exception e = t.getException();
                        if (e != null) Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                        return;
                    }
                    List<MessageModel> data = new ArrayList<>();
                    for (DocumentSnapshot d : t.getResult().getDocuments()) {
                        MessageModel m = d.toObject(MessageModel.class);
                        if (m == null) m = new MessageModel();
                        m.setId(d.getId());
                        data.add(m);
                    }
                    adapter.submit(data);
                    toggleEmpty(data.isEmpty());
                });
    }

    private void onMessageClick(@NonNull MessageModel m) {
        String uid = getUnifiedUidOrNotify();
        if (uid != null) {
            FirebaseFirestore.getInstance()
                    .collection("users").document(uid)
                    .collection("inbox").document(m.getId())
                    .update("read", true, "readAt", Timestamp.now());
        }
        if ("order_done".equals(m.getType()) && m.getOrderId() != null) {
            RateOrderBottomSheet.newInstance(m.getOrderId())
                    .show(getParentFragmentManager(), "rate_order");
        }
    }

    private void toggleEmpty(boolean empty) {
        if (binding == null) return;
        binding.recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private void showLoading(boolean show) {
        if (binding == null) return;
        binding.progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            binding.recyclerView.setVisibility(View.GONE);
            binding.emptyView.setVisibility(View.GONE);
        }
    }

    /**
     * Lấy userId đồng nhất:
     * - Ưu tiên SessionManager.getUid() (USRxxxxx)
     * - Fallback FirebaseAuth UID nếu chưa có (để không vỡ luồng cũ)
     */
    @Nullable
    private String getUnifiedUidOrNotify() {
        // Ưu tiên custom userId (USRxxxxx)
        String customId = null;
        try {
            customId = new SessionManager(requireContext()).getUid();
        } catch (Throwable ignored) {}

        if (customId != null && !customId.trim().isEmpty()) {
            return customId.trim();
        }

        // Fallback sang Firebase UID (không khuyến nghị, nhưng an toàn)
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Toast.makeText(getContext(), "Bạn chưa đăng nhập", Toast.LENGTH_LONG).show();
            return null;
        }
        return FirebaseAuth.getInstance().getCurrentUser().getUid();
    }

    @Override
    public void onDestroyView() {
        if (registration != null) { registration.remove(); registration = null; }
        binding = null;
        super.onDestroyView();
    }
}
