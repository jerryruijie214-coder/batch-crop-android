package com.trae.batchcrop.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 后台批量裁剪：把同一套归一化裁剪区域套用到每一张图片。
 */
public final class BatchCropper {

    public interface Listener {
        void onProgress(int done, int total, String currentName);

        void onComplete(Result result);
    }

    public static class Result {
        public final ArrayList<Uri> outputUris = new ArrayList<>();
        public int generated;
        public int failed;
        public int processedImages;
    }

    private static final int MAX_DECODE_DIM = 4096;

    private BatchCropper() {
    }

    public static void run(Context context, List<Uri> sources, List<RectF> regions, Listener listener) {
        Context appContext = context.getApplicationContext();
        Handler mainHandler = new Handler(Looper.getMainLooper());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            Result result = new Result();
            int total = sources.size();
            for (int i = 0; i < total; i++) {
                Uri uri = sources.get(i);
                String baseName = ImageUtils.queryBaseName(appContext, uri);
                String mimeType = ImageUtils.queryMimeType(appContext, uri);
                int before = result.outputUris.size();
                Bitmap source = null;
                try {
                    source = ImageUtils.decodeSampled(appContext, uri, MAX_DECODE_DIM);
                    int width = source.getWidth();
                    int height = source.getHeight();
                    for (int r = 0; r < regions.size(); r++) {
                        RectF region = regions.get(r);
                        int left = Math.round(region.left * width);
                        int top = Math.round(region.top * height);
                        int right = Math.round(region.right * width);
                        int bottom = Math.round(region.bottom * height);
                        left = Math.max(0, Math.min(width - 1, left));
                        top = Math.max(0, Math.min(height - 1, top));
                        right = Math.max(left + 1, Math.min(width, right));
                        bottom = Math.max(top + 1, Math.min(height, bottom));

                        Bitmap part = Bitmap.createBitmap(source, left, top, right - left, bottom - top);
                        try {
                            Uri output = ImageUtils.saveToGallery(appContext, part,
                                    baseName + "_" + (r + 1), mimeType);
                            result.outputUris.add(output);
                            result.generated++;
                        } catch (Exception e) {
                            result.failed++;
                        } finally {
                            if (part != source) {
                                part.recycle();
                            }
                        }
                    }
                } catch (Exception | OutOfMemoryError e) {
                    result.failed++;
                } finally {
                    if (source != null && !source.isRecycled()) {
                        source.recycle();
                    }
                }
                if (result.outputUris.size() > before) {
                    result.processedImages++;
                }
                int done = i + 1;
                final String name = baseName;
                mainHandler.post(() -> listener.onProgress(done, total, name));
            }
            mainHandler.post(() -> listener.onComplete(result));
        });
        executor.shutdown();
    }
}
