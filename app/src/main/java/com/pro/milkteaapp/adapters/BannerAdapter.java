package com.pro.milkteaapp.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.pro.milkteaapp.R;

public class BannerAdapter extends RecyclerView.Adapter<BannerAdapter.VH> {
    private final int[] drawableIds;
    public BannerAdapter(int[] drawableIds) { this.drawableIds = drawableIds; }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_banner, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        h.img.setImageResource(drawableIds[position]);
    }

    @Override
    public int getItemCount() { return drawableIds == null ? 0 : drawableIds.length; }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView img;
        VH(@NonNull View itemView) {
            super(itemView);
            img = itemView.findViewById(R.id.imgBanner);
        }
    }
}
