package com.pro.milkteaapp.utils;

import android.annotation.SuppressLint;
import android.content.Context;

import com.pro.milkteaapp.R;
import java.util.HashMap;
import java.util.Map;

public class ImageUtils {

    private static final Map<String, Integer> imageMap = new HashMap<>();

    static {
        imageMap.put("tea_blacktea", R.drawable.tea_blacktea);
        imageMap.put("milktea_backsugar", R.drawable.milktea_brownsugar);
        imageMap.put("milktea_matcha", R.drawable.milktea_matcha);
        imageMap.put("milktea_taro", R.drawable.milktea_taro);
        imageMap.put("milktea_olong", R.drawable.milktea_olong);
        imageMap.put("milktea_greenthai", R.drawable.milktea_greenthai);
        imageMap.put("milktea_strawberry", R.drawable.milktea_strawberry);
        imageMap.put("milktea_chocolate", R.drawable.milktea_chocolate);
        imageMap.put("milktea_chese", R.drawable.milktea_cheese);
        imageMap.put("milktea_caramel", R.drawable.milktea_caramel);
        imageMap.put("milktea_blueberry", R.drawable.milktea_blueberry);
        imageMap.put("milktea_mango", R.drawable.milktea_mango);
        // Thêm mapping cho tên ảnh mới từ Firebase nếu cần
    }
    @SuppressLint("DiscouragedApi")
    public static int getImageResId(Context context, String imageName) {
        if (imageName == null || imageName.isEmpty()) return 0;
        return context.getResources().getIdentifier(imageName, "drawable", context.getPackageName());
    }
}