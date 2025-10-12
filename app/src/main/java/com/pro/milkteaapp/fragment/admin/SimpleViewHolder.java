package com.pro.milkteaapp.fragment.admin;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.pro.milkteaapp.R;

public class SimpleViewHolder extends RecyclerView.ViewHolder {
    private final TextView tvTitle;

    public SimpleViewHolder(@NonNull View itemView) {
        super(itemView);
        tvTitle = itemView.findViewById(R.id.tvTitle);
    }

    public void bind(String text) {
        tvTitle.setText(text);
    }
}
