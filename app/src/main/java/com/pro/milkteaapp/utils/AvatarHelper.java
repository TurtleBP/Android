package com.pro.milkteaapp.utils;

import android.content.Context;
import android.text.TextUtils;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;

public class AvatarHelper {

    public static void load(@NonNull Context ctx,
                            @NonNull ImageView img,
                            String urlOrName,
                            @DrawableRes int placeholder) {
        if (!TextUtils.isEmpty(urlOrName)) {
            String s = urlOrName.trim();
            if (s.startsWith("http://") || s.startsWith("https://")) {
                Glide.with(ctx)
                        .load(s)
                        .placeholder(placeholder)
                        .error(placeholder)
                        .into(img);
                return;
            }
            if (s.startsWith("drawable:")) s = s.substring("drawable:".length()).trim();
            int resId = ctx.getResources().getIdentifier(s, "drawable", ctx.getPackageName());
            if (resId != 0) {
                Glide.with(ctx)
                        .load(resId)
                        .placeholder(placeholder)
                        .error(placeholder)
                        .into(img);
                return;
            }
        }
        Glide.with(ctx).load(placeholder).into(img);
    }
}
