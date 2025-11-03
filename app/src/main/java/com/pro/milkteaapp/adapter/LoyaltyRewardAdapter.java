package com.pro.milkteaapp.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.pro.milkteaapp.R;
import com.pro.milkteaapp.models.LoyaltyReward;

import java.util.List;

public class LoyaltyRewardAdapter extends RecyclerView.Adapter<LoyaltyRewardAdapter.VH> {

    public interface RedeemListener {
        void onRedeem(LoyaltyReward reward);
    }

    private final List<LoyaltyReward> data;
    private final RedeemListener listener;

    public LoyaltyRewardAdapter(List<LoyaltyReward> data, RedeemListener listener) {
        this.data = data;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_loyalty_reward, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        LoyaltyReward r = data.get(position);
        h.tvName.setText(r.getName());
        h.tvDesc.setText(r.getDescription());
        h.tvCost.setText(String.format("%d điểm", r.getCostPoints()));
        h.btnRedeem.setOnClickListener(v -> {
            if (listener != null) listener.onRedeem(r);
        });
    }

    @Override
    public int getItemCount() {
        return data == null ? 0 : data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvDesc, tvCost;
        Button btnRedeem;
        public VH(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvRewardName);
            tvDesc = itemView.findViewById(R.id.tvRewardDesc);
            tvCost = itemView.findViewById(R.id.tvRewardCost);
            btnRedeem = itemView.findViewById(R.id.btnRedeem);
        }
    }
}
