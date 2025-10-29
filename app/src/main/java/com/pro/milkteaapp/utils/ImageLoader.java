package com.pro.milkteaapp.utils;

import android.content.Context;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;

public final class ImageLoader {

    private ImageLoader() {}

    /**
     * urlOrName:
     *  - "https://..." hoặc "http://..."  -> load URL
     *  - "drawable:ic_xxx" hoặc "ic_xxx"  -> load từ res/drawable/ic_xxx.*
     */
    public static void load(ImageView iv, @Nullable String urlOrName, @DrawableRes int placeholder) {
        Context ctx = iv.getContext();

        if (urlOrName != null) {
            String s = urlOrName.trim();

            // 1) URL
            if (s.startsWith("http://") || s.startsWith("https://")) {
                Glide.with(ctx)
                        .load(s)
                        .placeholder(placeholder)
                        .error(placeholder)
                        .into(iv);
                return;
            }

            // 2) drawable name
            if (s.startsWith("drawable:")) s = s.substring("drawable:".length()).trim();
            if (!s.isEmpty()) {
                int resId = ctx.getResources().getIdentifier(s, "drawable", ctx.getPackageName());
                if (resId != 0) {
                    Glide.with(ctx)
                            .load(resId)
                            .placeholder(placeholder)
                            .error(placeholder)
                            .into(iv);
                    return;
                }
            }
        }

        // 3) fallback
        Glide.with(ctx).load(placeholder).into(iv);
    }
}
