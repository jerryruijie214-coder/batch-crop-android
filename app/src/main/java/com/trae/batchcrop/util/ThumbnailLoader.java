package com.trae.batchcrop.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Size;
import android.widget.ImageView;

import com.trae.batchcrop.R;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 简易缩略图加载器，避免列表滚动时阻塞主线程。
 */
public final class ThumbnailLoader {

    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ThumbnailLoader() {
    }

    public static void load(Context context, Uri uri, int sizePx, ImageView imageView) {
        imageView.setTag(R.id.tag_thumb_uri, uri);
        imageView.setImageDrawable(null);
        Context appContext = context.getApplicationContext();
        POOL.execute(() -> {
            Bitmap bitmap = decode(appContext, uri, sizePx);
            MAIN.post(() -> {
                if (uri.equals(imageView.getTag(R.id.tag_thumb_uri)) && bitmap != null) {
                    imageView.setImageBitmap(bitmap);
                }
            });
        });
    }

    private static Bitmap decode(Context context, Uri uri, int sizePx) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return context.getContentResolver().loadThumbnail(uri, new Size(sizePx, sizePx), null);
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 4;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(in, null, opts);
            }
        } catch (Exception e) {
            return null;
        }
    }
}
