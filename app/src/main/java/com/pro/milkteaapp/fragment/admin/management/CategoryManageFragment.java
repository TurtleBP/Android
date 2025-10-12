package com.pro.milkteaapp.fragment.admin.management;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.widget.TextView;

import com.pro.milkteaapp.handler.AddActionHandler;

public class CategoryManageFragment extends Fragment implements AddActionHandler {

    public CategoryManageFragment(){}

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // TODO: thay bằng layout thật (RecyclerView + Dialog CRUD)
        TextView tv = new TextView(requireContext());
        tv.setText("Quản lý Category");
        int p = (int) (16 * getResources().getDisplayMetrics().density);
        tv.setPadding(p,p,p,p);
        return tv;
    }

    @Override
    public void onAddAction() {
        // TODO: mở dialog thêm Category (dựa trên dialog_category.xml + Firestore)
        // showAddCategoryDialog();
    }
}
